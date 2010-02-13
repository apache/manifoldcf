#!/usr/bin/python

# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import os
import sys
import errno
import time
import datetime
import subprocess
import socket
import signal
import re
import sqatools
from sqatools import docs, sqautils
import urllib
import urllib2
import TestDocs
import SQAhelpers
import traceback
import VirtualBrowser
import pycurl
from sqatools import LicenseMakerClient
from sqatools import appliance

sys.path.append("/usr/lib/metacarta")

# These methods are helper methods that abstract from the various testing scripts

# Set ingestion proxy timeout
def set_proxy_timeout(timeout):
    """ Set the ingestion proxy timeout.
    """
    appliance.ingestion_proxy_adjust_timeout(timeout)
    # Restart leafblower
    stop_leafblower()
    start_leafblower()

# Restore ingestion proxy timeout to default
def restore_default_proxy_timeout():
    """ Restore the proxy timeout to default value """
    appliance.ingestion_proxy_adjust_timeout()
    # Restart leafblower
    stop_leafblower()
    start_leafblower()

# Global flag pertaining to which kind of adtools we will use
use_legacy_adtools = False
# Global adtools object, which will be overridden by various imports when initialized properly
#adtools = None

# Synchronize ad mode.  This detects the current setting of legacy mode, based on the appliance environment
def synchronize_legacy_mode( ):
    """ Detect which mode is currently in place.  This is necessary in order for preclean to work properly. """
    global use_legacy_adtools
    if os.readlink("/etc/alternatives/active_directory_control") == "/usr/lib/metacarta/active_directory_tool":
        # Legacy mode
        use_legacy_adtools = True
    elif os.readlink("/etc/alternatives/active_directory_control") == "/usr/lib/metacarta/active_directory_control":
        # MD mode
        use_legacy_adtools = False
    else:
        raise Exception("Could not synchronize legacy mode")

# Select 'legacy' ad mode
def select_legacy_mode(use_legacy_tools=True):
    """ Change the mode based on where we currently are """
    global use_legacy_adtools
    # Call out to active directory tool to set or clear legacy mode
    if use_legacy_tools != use_legacy_adtools:
        if use_legacy_tools:
            invoke_root_script( [ "/usr/bin/active_directory_control", "downgrade" ] )
        else:
            invoke_root_script( [ "/usr/bin/active_directory_control", "upgrade-ad" ] )
        use_legacy_adtools = use_legacy_tools

# Cancel legacy mode (restore to legacy mode = off)
def cancel_legacy_mode():
    """ Cancel legacy mode, but only if it is set """
    if use_legacy_adtools:
        select_legacy_mode(use_legacy_tools=False)

def initialize_adtools():
    """ Import the right adtools version, and return it """
    if use_legacy_adtools:
        from wintools import adtools as adtools
    else:
        from wintools import adtools_md as adtools
    return adtools

# Records of when certain operations started
time_records = {}

def start_timing( time_key ):
    """ Record the start of a given activity """
    global time_records
    if time_records.has_key( time_key ):
        raise Exception("Two starts with the same key! %s" % time_key)
    time_records[time_key] = time.time()

def end_timing( time_key, limit=None ):
    """ Record the end of a given activity, and error out if it exceeds the limit """
    global time_records
    if not time_records.has_key( time_key ):
        raise Exception("End without start! %s" % time_key)
    start_time = time_records[time_key]
    end_time = time.time()
    print "Test activity %s took %f seconds." % (time_key,end_time-start_time)
    del time_records[time_key]
    if limit != None and end_time-start_time > limit:
        raise Exception("Time for activity %s took too long; actual %f, limit %f" % (time_key,end_time-start_time,limit))

# Do an http fetch using curl
class CallbackClass:
    def __init__(self):
        self.contents = ''

    def body_callback(self, buf):
        self.contents = self.contents + buf

def invoke_curl( url_string, user_name=None, password=None ):
    """ Use curl to request data from a url via http """
    t = CallbackClass()
    c = pycurl.Curl()
    try:
        c.setopt(c.URL, url_string)
        c.setopt(c.WRITEFUNCTION, t.body_callback)
        if user_name != None and password != None:
            c.setopt(c.USERPWD, "%s:%s" % (user_name,password))
        c.perform()
        errcode = c.getinfo(pycurl.HTTP_CODE)
        if errcode != 200:
            raise Exception("HTTP error %s" % str(errcode))
    finally:
        c.close()
    return t.contents

# Set the clock forward enough to screw up thread sleeps
def set_clock_forward():
    """ Set clock forward 1 day (for testing thread sleep) """
    invoke_root_script(["/etc/init.d/ntp","stop"])
    t = datetime.datetime.today()
    day, month, year = t.day + 1, t.month, t.year
    if day > 28:
        day, month = 1, month + 1
    if month > 12:
        month, year = month - 12, year + 1
    invoke_root_script(["date","%02d%02d%02d%02d%02d" % (month, day, t.hour, t.minute, year)])

# Set clock back to normal
def restore_clock():
    """ Restore clock to the correct time, as served by ntp """
    # reset clock
    invoke_root_script(["/usr/sbin/ntpdate","time.metacarta.com"])
    # restart ntp server
    invoke_root_script(["/etc/init.d/ntp","start"])

# Count temporary files in temporary area that have a specified prefix
def count_temporary_files( prefix_string ):
    """ Look in /var/tmp for files that begin with prefix_string, and return a count """
    file_list = invoke_root_script( [ "ls", "-1", "/var/tmp" ] )
    file_array = file_list.splitlines()
    tfilecount = 0;
    for filename in file_array:
        if filename.startswith( prefix_string ):
            tfilecount += 1
    return tfilecount

# Kick off a maintenance operation
def run_maintenance( ):
    """ Run postgresql maintenance operation, and check for success.
    """
    output = invoke_root_script( [ "/usr/lib/metacarta/postgres-maintenance" ] )
    lines = output.splitlines()
    saw_vacuum = False
    for line in lines:
        line_pos = line.find("VACUUM")
        if line_pos != None and line_pos != -1:
            saw_vacuum = True
        line_pos = line.find("Postgresql maintenance completed")
        if line_pos != None and line_pos != -1:
            return
    if saw_vacuum == False:
        raise Exception( "Maintenance did not perform a VACUUM; got %s" % output )
    raise Exception( "Maintenance operation did not complete successfully; output %s" % output)

# Find the metacarta-agents process id
def find_daemon_pid( ):
    """ Find the pid of the daemon process, and return None if it can't be found. """
    process_list = invoke_root_script( [ "ps", "-eo", "pid,command", "-w", "-w" ] )
    # Break process_list into lines
    process_array = process_list.splitlines()
    # Go through each line
    pid = None
    for line in process_array:
        line_pos = line.find("com.metacarta.agents.AgentRun")
        if line_pos != None and line_pos != -1:
            line_fields = line.split()
            pid = int(line_fields[ 0 ])
            break
    return pid

# Find the daemon invocation command
def confirm_daemon_switch( switch_string ):
    """ Find a given switch in the daemon invocation line. """
    process_list = invoke_root_script( [ "ps", "-eo", "command", "-w", "-w" ] )
    # Break process_list into lines
    process_array = process_list.splitlines()
    # Go through each line
    command = None
    for line in process_array:
        line_pos = line.find("com.metacarta.agents.AgentRun")
        if line_pos != None and line_pos != -1:
            command = line
            break
    if command.find(switch_string) == -1:
        raise Exception("Expected command clause '%s', did not see it" % switch_string)

# Find the number of matching lsof lines for a regexp against the daemon process
def count_lsof_daemon_lines( match_regexp ):
    """ Count the number of lines from lsof for the AgentRun process which match the
        provided regexp.
    """
    regexp_pattern = re.compile( match_regexp, 0 )

    # Find the pid, if any
    pid = find_daemon_pid( )
    if pid == None:
        raise Exception("Daemon process is not running")

    # Now, invoke lsof -p
    lsof_list = invoke_root_script( [ "lsof", "-p", str(pid) ] )
    lsof_array = lsof_list.splitlines()
    matching_count = 0
    for line in lsof_array:
        if regexp_pattern.search( line ) != None:
            matching_count += 1
    return matching_count

# Find the amount of resident memory the AgentRun process is consuming, in bytes
def calculate_daemon_memory( ):
    """ This involves a ps command to get the mem usage of the AgentRun process """
    process_list = invoke_root_script( [ "ps", "-eo", "vsize,rss,command", "-w", "-w" ] )
    # Break process_list into lines
    process_array = process_list.splitlines()
    # Go through each line
    for line in process_array:
        line_pos = line.find("com.metacarta.agents.AgentRun")
        if line_pos != None and line_pos != -1:
            line_fields = line.split()
            return ( int(line_fields[ 0 ]), int(line_fields[ 1 ]) )

    return (0,0)

# Change CF logging setup; does NOT restart tomcat and metacarta-agents
def configure_cf( desired_options ):
    """ Modifies the agents.conf file to set up desired logging behavior.
        The desired_log_options argument is a dictionary of keys and values corresponding
        to what should get set in the log.  NOTE WELL: Only entries that already
        exist in the agents.conf file will be modified!!  Does not restart tomcat or agents
        processes.
    """
    fh = open( "/etc/metacarta/agents.conf", "r" )
    try:
        outputfh = open( "/etc/metacarta/agents.conf.new", "w" )
        try:
            for line in fh.readlines():
                if line.lstrip().startswith("#"):
                    outputfh.write(line)
                else:
                    equals_index = line.find("=")
                    if equals_index == -1:
                        outputfh.write(line)
                    elif desired_options.has_key(line[0:equals_index]):
                        new_value = desired_options[ line[0:equals_index] ]
                        comment_index = line.find("#",equals_index+1)
                        if comment_index == -1:
                            comment_index = len(line.rstrip())
                        outputfh.write(line[0:equals_index+1] + new_value + line[comment_index:len(line)])
                    else:
                        outputfh.write(line)
        finally:
            outputfh.close()
    finally:
        fh.close()
    os.remove( "/etc/metacarta/agents.conf" )
    os.rename( "/etc/metacarta/agents.conf.new", "/etc/metacarta/agents.conf" )
    clear_logs()

# Clear the metacarta error log
def get_metacarta_log_pos(log_name="/var/log/metacarta/error.log"):
    """ Find the position in the metacarta error log, so we can figure out where to read from. """

    #tag_string = "------ Connectorframework basic test start marker %f ------" % time.time()

    val = os.stat( log_name )
    return (val.st_ino,val.st_size)

# Read the metacarta logs, looking for lines that match a regexp
def read_metacarta_log( reg_exp, position, log_name="/var/log/metacarta/error.log" ):
    regexp_pattern = re.compile( reg_exp, 0 )
    inode,offset = position
    val = os.stat( log_name )
    if val.st_ino == inode:
        return read_matching_lines_position( log_name, offset, regexp_pattern )
    else:
        val = os.stat( "%s.1" % log_name)
        if val.st_ino != inode:
            raise Exception("Log rolled but I can't figure out when")
        return read_matching_lines_position( "%s.1" % log_name, offset, regexp_pattern ) + read_matching_lines_position( log_name, 0, regexp_pattern )

# Read lines matching a regexp from a file starting at a position
def read_matching_lines_position( file_name, position, regexp_pattern=None ):
    try:
        fh = open( file_name, "r" )
        fh.seek(position,0)
        try:
            rlines = [ ]
            for line in fh.readlines():
                if regexp_pattern.search( line ) != None:
                    rlines.append( line )
            return rlines
        finally:
            fh.close()
    except:
        # No log
        return [ ]

# Clear logs
def clear_logs():
    # Clear out the existing logs; we don't want to confuse matters with old stuff
    invoke_root_script( [ "rm", "-f", "/var/log/metacarta/java-agents/agents.log" ] )

# Read the log, searching for a particular regular expression, and return all lines
# that have that expression, in order.
def read_log( reg_exp ):
    regexp_pattern = re.compile( reg_exp, 0 )
    return read_matching_lines_position( "/var/log/metacarta/java-agents/agents.log", 0, regexp_pattern )

# Shutdown tomcat
def shutdown_tomcat():
    invoke_root_script( [ "/etc/init.d/tomcat5.5", "stop" ] )

# Shutdown agents
def shutdown_agents(timeout=40):
    start_time = time.time()
    output = invoke_root_script( [ "/etc/init.d/metacarta-agents", "stop" ] )
    end_time = time.time()
    if output.find("kill") != -1:
        raise Exception("metacarta-agents did not shut down cleanly!")
    if end_time - start_time > timeout:
        raise Exception("Shutdown of metacarta-agents took more than %f seconds" % timeout)

# Call this method to start tomcat
def start_tomcat():
    invoke_root_script( [ "/etc/init.d/tomcat5.5", "start" ] )

# Call this method to start agents
def start_agents():
    invoke_root_script( [ "/etc/init.d/metacarta-agents", "start" ] )

# Call this method to restart tomcat
def restart_tomcat():
    invoke_root_script( [ "/etc/init.d/tomcat5.5", "restart" ] )

# Call this method to restart agents
def restart_agents():
    start_time = time.time()
    output = invoke_root_script( [ "/etc/init.d/metacarta-agents", "restart" ] )
    end_time = time.time()
    if output.find("kill") != -1:
        raise Exception("metacarta-agents did not shut down cleanly!")
    # 2850's get so maxed out that 60 wasn't enough
    if end_time - start_time > 120:
        raise Exception("Restart of metacarta-agents took more than one minute")

# Call this method to shut down ingestion
def stop_leafblower():
    from sqatools import LeafblowerHacks
    LeafblowerHacks.stop_leafblower()

# Call this method to start ingestion
def start_leafblower():
    from sqatools import LeafblowerHacks
    LeafblowerHacks.start_leafblower();

# Call this method to set NTLMv1 mode for Share Connector
def set_shareconnector_ntlmv1_mode():
    invoke_root_script( [ "/usr/bin/shareconnector_control", "set", "ntlmv1" ] )

# Call this method to restore default (NTLMv2) mode for Share Connector
def set_shareconnector_default_mode():
    invoke_root_script( [ "/usr/bin/shareconnector_control", "set", "ntlmv2" ] )

# Get the status of the ntlm version switch for Share Connector
def get_shareconnector_mode():
    return invoke_root_script( [ "/usr/bin/shareconnector_control", "status" ] )

# This method deregisters a connector
def deregister_connector( class_name ):
    """Deregisters a connector; used in tests that see what happens when a connector
       has been uninstalled, where connections and jobs may remain."""
    invoke_script( [ "/usr/lib/metacarta/crawler-unregisterconnector", class_name ] )

# This method reregisters a connector
def register_connector( class_name, description ):
    """Registers or re-registers a connector; used in tests that see what happens when a connector
       has been uninstalled, and is reinstalled."""
    invoke_script( [ "/usr/lib/metacarta/crawler-registerconnector", class_name, description ] )

# This method deregisters an authority
def deregister_authorityconnector( class_name ):
    """Deregisters an authority connector; used in tests that see what happens when an authority
       has been uninstalled, where connections may remain."""
    invoke_script( [ "/usr/lib/metacarta/crawler-unregisterauthority", class_name ] )

# This method reregisters an authority connector
def register_authorityconnector( class_name, description ):
    """Registers or re-registers an authority connector; used in tests that see what happens when an authority
       has been uninstalled, and is reinstalled."""
    invoke_script( [ "/usr/lib/metacarta/crawler-registerauthority", class_name, description ] )

# This method checks what the authority webapp returns
def ask_authority_webapp( user_name ):
    """Ask the authority webapp to see what it says.  Returns the entire response."""
    try:
        f = urllib2.urlopen("http://localhost:8180/authorityservice/UserACLs?username=%s" % urllib.quote(user_name))
    except urllib2.HTTPError,e:
        return (str(e),None)
    return (None,f.read())

# Dump configuration to a file
def export_configuration( filename ):
    """ Export configuration to the specified file """
    invoke_root_script( [ "/usr/lib/metacarta/backup-crawler-configuration", filename ] )

# Restore configuration from a file
def import_configuration( filename ):
    """ Import configuration from the specified file """
    invoke_root_script( [ "/usr/lib/metacarta/restore-crawler-configuration", filename ] )

# Call this method to prepare for the test.  Turns off maintenance script, etc.
def setup_connector_environment( ):
    """Set up the connector environment for tests - disable maintenance, other hooks here."""
    disable_maintenance()

def disable_maintenance( ):
    """ Disable maintenance script, then wait if it is running until it stops.
    """
    invoke_root_script( [ "mv",
                          "/etc/cron.d/metacarta-postgres-maintenance-crontab",
                          "local-metacarta-postgres-maintenance-crontab-copy" ] )

    while True:
        try:
            os.stat( "/var/run/metacarta/postgres-maintenance-in-progress" )
            time.sleep(10)
        except Exception, e:
            return

# Call this method to clean up an installed machine.  Restores maintenance
# script, etc.
def teardown_connector_environment( ):
    """restore normal connector configuration - turn maintenance back on, etc."""
    enable_maintenance()

def enable_maintenance( ):
    try:
        os.stat( "/etc/cron.d/metacarta-postgres-maintenance-crontab" )
    except Exception, e:
        invoke_root_script( [ "mv",
                              "local-metacarta-postgres-maintenance-crontab-copy",
                              "/etc/cron.d/metacarta-postgres-maintenance-crontab" ] )

    invoke_root_script( [ "rm", "-f",
                          "local-metacarta-postgres-maintenance-crontab-copy" ] )

# This method MUST be called in order to use the connector framework.
# This also has to be added to the documentation because without it search will not work.
def enable_connector_framework( ):
    pass

# Set maximum document-size limit
def set_max_document_size( size_value ):
    fd = open( "/etc/metacarta/ingest_reject_size", "w" )
    try:
        fd.write( str(size_value) )
        fd.write( "\n" )
    finally:
        fd.close( )

# Clear maximum document-size limit
def clear_max_document_size( ):
    try:
        os.remove( "/etc/metacarta/ingest_reject_size" )
    except:
        pass

# Create a serial file, for client certificate creation
def create_serial_file( serial_file_name ):
    fd = open( serial_file_name, "w" )
    try:
        fd.write( "01\n" )
    finally:
        fd.close( )

# Build a signed, duck-specific client certificate using an existing certificate authority.
# The certificate authority is specified by a public key and a private key, and a password.
def create_client_certificate( ca_public_key_file, ca_private_key_file, serial_file, client_cert_file, password ):
    """ This code uses ssl_control and openssl to build a certificate request, and sign the certificate,
        respectively. """
    invoke_root_script( [ "ssl_control",
                          "create-cert-req",
                          "client.req" ], "US\nMassachusetts\nCambridge\nMetaCarta\nEngineering/QA\n\n\n\n" )
    # Now invoke openssl
    invoke_root_script( [ "openssl",
                          "x509",
                          "-req",
                          "-in",
                          "client.req",
                          "-CA",
                          ca_public_key_file,
                          "-CAkey",
                          ca_private_key_file,
                          "-CAserial",
                          serial_file,
                          "-out",
                          client_cert_file ], password + "\n" )

# Clean out everything
def reset_all( ):
    # Clear out what's been ingested before
    from sqatools import LeafblowerHacks
    LeafblowerHacks.total_purge()
    # Get rid of all jobs, etc.
    invoke_crawler_command( "com.metacarta.crawler.ResetAll" )
    invoke_crawler_command( "com.metacarta.authorities.ResetAll")

# Define the standard output connection
def define_gts_outputconnection( ):
    """ Define a standard GTS output connection """
    define_outputconnection( "GTS", "GTS", "com.metacarta.agents.output.gts.GTSConnector", configparams=[ "Ingestion URI=http://localhost:7031/HTTPIngest" ] )

# Delete the standard output connection
def delete_gts_outputconnection( ):
    """ Delete the standard GTS output connection """
    delete_outputconnection( "GTS" )
    
# Define an output connection
def define_outputconnection( connectionname, connectiondescription, connectionclass,
                       poolmax="10", configparams=[] ):
    """Define a connection"""
    listparams = [ process_argument(connectionname),
                   process_argument(connectiondescription),
                   connectionclass,
                   str(poolmax) ]
    for item in configparams:
        listparams.append(item)
    invoke_crawler_command( "com.metacarta.crawler.DefineOutputConnection", argument_list=listparams )

# Define a repository connection
def define_repositoryconnection( connectionname, connectiondescription, connectionclass, authorityname="",
                       poolmax="10", configparams=[] ):
    """Define a connection"""
    listparams = [ process_argument(connectionname),
                   process_argument(connectiondescription),
                   connectionclass,
                   process_argument(authorityname),
                   str(poolmax) ]
    for item in configparams:
        listparams.append(item)
    invoke_crawler_command( "com.metacarta.crawler.DefineRepositoryConnection", argument_list=listparams )

# Define an authority connection
def define_authorityconnection( connectionname, connectiondescription, connectionclass,
                       poolmax="10", configparams=[] ):
    """Define a connection"""
    listparams = [ process_argument(connectionname),
                   process_argument(connectiondescription),
                   connectionclass,
                   poolmax ]
    for item in configparams:
        process_argument(listparams.append(item))
    invoke_crawler_command( "com.metacarta.authorities.DefineAuthorityConnection", argument_list=listparams )


# Define a job
def define_job( jobdescription, connectionname, xml, output_connection="GTS", output_xml="", type="specified", startmethod="windowbegin",
                recrawlinterval=0 ):
    """Define a job"""
    return invoke_crawler_command( "com.metacarta.crawler.DefineJob", argument_list=[ process_argument(jobdescription),
                            process_argument(connectionname),
                            process_argument(output_connection),
                            type,
                            startmethod,
                            "accurate",
                            "%d" % (recrawlinterval * 1000*60),
                            "",
                            "",
                            "5",
                            "",
                            process_argument(xml),
                            process_argument(output_xml) ] )

# Start a job
def start_job( job_id ):
    """Start a job"""
    return invoke_crawler_command( "com.metacarta.crawler.StartJob", argument_list=[ process_argument(job_id) ] )

# Wait for job to be complete
def wait_job_complete( job_id ):
    """Wait for job to finish"""
    return invoke_crawler_command( "com.metacarta.crawler.WaitForJobInactive", argument_list=[ process_argument(job_id) ] )

# Wait for job to be deleted
def wait_job_deleted( job_id ):
    """Wait for job to be successfully removed"""
    return invoke_crawler_command( "com.metacarta.crawler.WaitForJobDeleted", argument_list=[ process_argument(job_id) ] )

# Delete a job
def delete_job( job_id ):
    """Delete an existing job"""
    return invoke_crawler_command( "com.metacarta.crawler.DeleteJob", argument_list=[ process_argument(job_id) ] )

# Abort a job
def abort_job( job_id ):
    """Abort a running job"""
    return invoke_crawler_command( "com.metacarta.crawler.AbortJob", argument_list=[ process_argument(job_id) ] )

# Pause a job
def pause_job( job_id ):
    """ Pause a running job """
    return invoke_crawler_command( "com.metacarta.crawler.PauseJob", argument_list=[ process_argument(job_id) ] )

# Resume a job
def resume_job( job_id ):
    """ Resume a running job """
    return invoke_crawler_command( "com.metacarta.crawler.RestartJob", argument_list=[ process_argument(job_id) ] )

# Wait for job to pause
def wait_job_paused( job_id ):
    """ Wait for a job to pause """
    return invoke_crawler_command( "com.metacarta.crawler.WaitJobPaused", argument_list=[ process_argument(job_id) ] )

# Delete an output connection
def delete_outputconnection( connection_name ):
    """Delete an output connection"""
    return invoke_crawler_command( "com.metacarta.crawler.DeleteOutputConnection", argument_list=[ process_argument(connection_name) ] )

# Delete a repository connection
def delete_repositoryconnection( connection_name ):
    """Delete a repository connection"""
    return invoke_crawler_command( "com.metacarta.crawler.DeleteRepositoryConnection", argument_list=[ process_argument(connection_name) ] )

# Delete an authority connection
def delete_authorityconnection( connection_name ):
    """Delete an authority connection"""
    return invoke_crawler_command( "com.metacarta.authorities.DeleteAuthorityConnection", argument_list=[ process_argument(connection_name) ] )

def set_scheduled_time( jobid, minutesFromNow, intervalMinutes=None ):
    """Add a schedule time for a job"""
    # What we do is get the current time, add the seconds, and then call the schedule set script
    currentTime = time.time()
    triggerTime = currentTime + minutesFromNow * 60
    triggerStruct = time.localtime(triggerTime)
    interval = ""
    if (intervalMinutes != None):
        interval = "%d" % intervalMinutes
    months = [ "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december" ]
    hours = [ "12am", "1am", "2am", "3am", "4am", "5am", "6am", "7am", "8am", "9am", "10am", "11am", "12pm",
              "1pm", "2pm", "3pm", "4pm", "5pm", "6pm", "7pm", "8pm", "9pm", "10pm", "11pm" ]
    return invoke_crawler_command( "com.metacarta.crawler.AddScheduledTime", argument_list=[ jobid,
                            interval,
                            "monday,tuesday,wednesday,thursday,friday,saturday,sunday",
                            "%d" % triggerStruct.tm_mday,
                            months[triggerStruct.tm_mon - 1],
                            "%d" % triggerStruct.tm_year,
                            hours[triggerStruct.tm_hour],
                            "%d" % triggerStruct.tm_min ] )

def change_job_doc_spec( jobid, docspec ):
    """Change the job document specification"""
    return invoke_crawler_command( "com.metacarta.crawler.ChangeJobDocSpec", argument_list=[ jobid,
                            docspec ] )

def change_auth_spec( authname, spec ):
    """Change the authority specification"""
    return invoke_crawler_command( "com.metacarta.authorities.ChangeAuthSpec", argument_list=[ authname,
                            spec ] )

def get_everyone_sid( ):
    """ Get the sid of 'everyone' """
    return initialize_adtools().EveryoneSID

def get_ad_user_sid( user ):
    """ Get the sid of an ad user """
    return initialize_adtools().get_sid( user )

def conditionally_add_ad_user( ad_domain_info, user, password ):
    """ Create a user if one doesn't already exist.
        The appliance must be joined to the domain before this will work.
    """
    local_ad_handle = initialize_adtools()
    try:
        local_ad_handle.query_entity_in_ldap( user )
    except local_ad_handle.ADToolsException, e:
        add_ad_user( ad_domain_info, user, password )
    # if the users are going to hang around we need to ensure that their passwords don't expire
    # this is unconditional due to a point in time where they were expiring and we want tests
    # to force this setting
    local_ad_handle.set_no_password_expire( ad_domain_info.domain_controller_ambassador, ad_domain_info.ad_domain, user)

def add_ad_user( ad_domain_info, user, password ):
    """Create a user in the specified AD domain controller"""
    initialize_adtools().create_user( user, password, ad_domain_info.domain_controller_ambassador, ad_domain_info.ad_domain, confirm_user=False )

def delete_ad_user( ad_domain_info, user ):
    """Delete a user"""
    initialize_adtools().delete_user(user, ad_domain_info.domain_controller_ambassador)

def configure_ad( ad_domain_info, join_multidomain = False ):
    """Configure appliance to use AD authentication"""
    adtools = initialize_adtools()

    # if multi-domain join requested then join all the child domains too
    # Disable precheck of admin user; this is because this feature is broken in ntlmv2 domains
    if join_multidomain:
        adtools.ad_md_setup_join( ad_domain_info, False, precheck_admin_user=False )
    else:
        adtools.join_ad_with_defaults( ad_domain_info.realm_admin_password, admin_user=ad_domain_info.realm_admin.split("@")[0], precheck_admin_user=False )
    # Added so the 76 domain works
    adtools.add_to_group(SQAhelpers.reverse_resolve().split('.')[0],
                         adtools.CompatibilityGroup,
                         ad_domain_info.domain_controller_ambassador)
    # Set auth_control again to specify connector framework
    invoke_root_script( [ "auth_control",
                          "authorization",
                          "search-web-ui,secure-soap-search",
                          "--add",
                          "connector_framework" ] )

def turn_off_ad( ad_domain_info, leave_multidomain = False):
    """Disable ad authentication"""
    # Disable precheck of admin user; this is because this feature is broken in ntlmv2 domains
    if leave_multidomain:
        initialize_adtools().leave_ad( realm_admin_password=ad_domain_info.realm_admin_password, disable_machine_acct=False, delete_machine_acct=True, already_left=0, precheck_admin_user=False )
    else:
        initialize_adtools().leave_ad( realm_admin_password=ad_domain_info.realm_admin_password, admin_user=ad_domain_info.realm_admin.split("@")[0], precheck_admin_user=False )

def create_crawler_user( user, password ):
    """Create a user specifically for crawler UI, that the UI will be able to use"""
    if user == None:
        return
    invoke_root_script( [ "basic_auth_control", "add", "ingest_users", user+":"+password ] )

def delete_crawler_user( user ):
    """Delete the crawler user"""
    if user == None:
        return
    invoke_root_script( [ "basic_auth_control", "remove", "ingest_users", user ] )

def add_basic_auth_user( user, password ):
    """Set up a basic auth user and password"""
    invoke_root_script( [ "basic_auth_control", "add", "my_ui_users", user+":"+password ], "yes\n" )

def delete_basic_auth_user( user ):
    """Delete basic auth user"""
    invoke_root_script( [ "basic_auth_control", "remove", "my_ui_users", user ], "yes\n" )

def configure_basic_auth( ):
    """Reset the system so that basic auth is the authenticator, and wait for apache to come back up."""
    invoke_root_script( [ "basic_auth_control", "createdb", "my_ui_users" ] )
    invoke_root_script( [ "auth_control", "auth", "search-web-ui,soap-search,secure-soap-search",
                          "basic_auth", "my_ui_users" ] )
    invoke_root_script( [ "auth_control",
                          "authorization",
                          "search-web-ui,soap-search,secure-soap-search",
                          "--add",
                          "connector_framework" ] )
    wait_for_apache( )

def turn_off_basic_auth( ):
    """Reset the system so basic auth is off"""
    invoke_root_script( [ "auth_control", "auth", "secure-soap-search",
                          "basic_auth", "ui_users" ] )
    invoke_root_script(["auth_control", "auth", "search-web-ui,soap-search", "none"])
    wait_for_apache( )
    invoke_root_script( [ "basic_auth_control", "destroydb", "my_ui_users", "--force"], "yes\n" )

def invoke_crawler_command( classname, argument_list=[], input=None, additional_switches=[], additional_classpath=None ):
    """ Invoke a crawler command, including the metacarta-pullagent-test.jar in addition to the full java-environment classpath
        and definitions.  Always run as tomcat user. """
    if additional_classpath:
        additional_classpath = "crawler-testing-package/metacarta-pullagent-test.jar:%s" % additional_classpath
    else:
        additional_classpath = "crawler-testing-package/metacarta-pullagent-test.jar"
        
    command_arguments = [ "crawler-testing-package/executejava", "-classpath", additional_classpath ] + additional_switches+ [ classname ] + argument_list
    return invoke_script(command_arguments,input=input)
    
def invoke_script( argumentlist, input=None, stdin_encoding=None, stdout_encoding="utf-8", stderr_encoding=None ):
    # for some reason the argument list can have null entries, map them to ""
    for i in range(0,len(argumentlist)):
        if argumentlist[i] == None:
            print "Warning: None argument in invoke_script, printing stack trace"
            traceback.print_stack()
            argumentlist[i] = ""   
            
    programname = argumentlist[0]
    
    fullargumentlist = [ "sudo", "-u", "tomcat55" ] + argumentlist
    print " ".join([quote_escape(i) for i in fullargumentlist])
    
    program = subprocess.Popen(fullargumentlist,
                               stdin=subprocess.PIPE,
                               stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE)
    if input != None:
        if stdin_encoding != None:
            input = input.encode(stdin_encoding)
        else:
            input = input.encode()
    (outputtext, errortext) = program.communicate(input)
    retcode = program.wait()
    if stderr_encoding != None:
        errortext = errortext.decode(stderr_encoding)
    else:
        errortext = errortext.decode()
    if stdout_encoding != None:
        outputtext = outputtext.decode(stdout_encoding)
    else:
        outputtext = outputtext.decode()
    if retcode != 0:
        raise Exception((u"Error response from %s: %d, message = [" % (programname,retcode)) + errortext + u"]")
    # Right at the moment, the testing infrastructure only accepts 7-bit binary values, so we have no choice but to do what we can to bash
    # the output to 7 bits
    print (u"Program %s output = '" % (programname) + outputtext + u"'").encode("utf-8")
    return outputtext

def invoke_root_script( argumentlist, input=None, allow_errors=False ):
    programname = argumentlist[0]
    # Set up the escaped argument list, which is a single string with each
    # argument included in an escaped, quoted manner
    accumulator = [ ]
    user = sqautils.check_username_configured()
    if user != "root":
        accumulator.append(quote_escape("sudo"))
        accumulator.append(" ")
    for item in argumentlist:
        accumulator.append(quote_escape(item))
        accumulator.append(" ")
    actualcommand = "".join(accumulator)
    print actualcommand
    program = subprocess.Popen(argumentlist,
                               stdin=subprocess.PIPE,
                               stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE)
    if input != None:
        input = input.encode()
    (outputtext, errortext) = program.communicate(input)
    retcode = program.wait()
    outputtext = outputtext.decode()
    errortext = errortext.decode()
    if allow_errors == False and retcode != 0:
        raise Exception((u"Error response from %s: %d, message = [" % (programname,retcode)) + errortext + u"]")
    # Right at the moment, the testing infrastructure only accepts 7-bit binary values, so we have no choice but to do what we can to bash
    # the output to 7 bits
    print (u"Program %s output = '" % (programname) + outputtext + u"'").encode("utf-8")
    return outputtext

def process_argument( mystring ):
    if mystring == None:
        return ''
    return mystring

def quote_escape( mystring ):
    if mystring == None:
        return '""'
    outputresult = [ '"' ]
    for i in range(len(mystring)):
        character = mystring[i]
        # In order to add memex documents into the repository, had '*' in here too, but that led to fields having '\*' in them instead of '*'.
        if character == '"' or character == '\\' or character == '$':
            outputresult.append('\\')
        outputresult.append(character)
    outputresult.append( '"' )
    return ''.join(outputresult)

# Waits until apache is up -- stolen from active_directory_tool
def wait_for_apache( ):
    """Wait for apache to respond to port 80"""
    http_port = 80
    for x in range(0,100):
        try:
            try:
                signal.alarm(15)
                s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                s.connect(("localhost", http_port))
            finally:
                signal.alarm(0)
        except IOError, e:
            if e.errno == errno.EINTR:
                raise Exception("timeout talking to port %s" % http_port)
            else:
                raise
        except socket.error, e:
            if re.search("Connection refused", str(e)):
                time.sleep(1)
                continue
            else:
                raise
        return
    raise Exception("Unable to connect to port %d.  Check apache error log." % \
            http_port)

def regexp_encode( thestring  ):
    """Encode a string so that regexp will match it exactly"""
    rval = ""
    for thechar in thestring:
        if thechar == '.':
            rval += "\."
        elif thechar == '?':
            rval += "\?"
        elif thechar == '\\':
            rval += "\\\\"
        else:
            rval += thechar
    return rval

def search_exists_check( keywords, collectionname, example, win_host=None, username=None, password=None ):
    """Check for existence in search results of the single example supplied"""

    # The SHN and HN may get restarted out from under our query.  So
    # this should loop a few times to find what we are looking for.
    # This means the test may take 4 seconds longer to fail, and other
    # timing related issues may not be caught.
    for counter in range(4):
        matches = TestDocs.search_documents_user(keywords,
                                             collection=collectionname,
                                             method="soapheadnode",
                                             username=username,
                                             password=password,
                                             win_host=win_host)

        # matches are url-encoded, full file names (including /root)
        # Filenames in docs are therefore url encoded here
        quotedoc = example #urllib.quote(example,safe='')
        cn = re.compile(example)
        for doc in matches:
            # Use re matching
            mo = cn.match( doc )
            if mo != None:
                return
        time.sleep(1)

    raise Exception("Expected document %s not returned when searching" % quotedoc)

def search_nonexists_check( keywords, collectionname, example, win_host=None, username=None, password=None ):
    """Check for nonexistence in search results of the single example supplied"""

    # The SHN and HN may get restarted out from under our query.  This
    # means the test may get success when the underlying query fails,
    # so there's not much point in doing it.  We'll do it anyway
    # though, for the day may come when it will be meaningful to ask
    # this question.

    # We could also query a few times here to gain more confidence
    # that the number of results returned is 0.

    matches = TestDocs.search_documents_user(keywords,
                                             collection=collectionname,
                                             method="soapheadnode",
                                             username=username,
                                             password=password,
                                             win_host=win_host)

    # matches are url-encoded, full file names (including /root)
    # Filenames in docs are therefore url encoded here
    quotedoc = example #urllib.quote(example,safe='')
    cn = re.compile(example)
    for doc in matches:
        # Use re matching
        mo = cn.match( doc )
        if mo != None:
            raise Exception("Unexpected document %s was returned when searching: Actual results =[%s]" % (quotedoc,"".join(matches)))

def search_check( keywords, collectionname, docs, win_host=None, username=None, password=None ):
    """Make sure all the specified docs are in the set returned for a
    search against the specified keywords"""

    # Reingested documents can cause duplicate documents because of race on new slice start
    # we need to retry once if we've discovered a document that should not be there.
    retry_unexpected = True

    # Keep looping until we find what we are looking for.
    # This will tend to cause false successes in the case of an empty set, and slower failures
    # if the test actually fails.
    for counter in range(4):
        matches = TestDocs.search_documents_user(keywords,
                                             collection=collectionname,
                                             method="soapheadnode",
                                             username=username,
                                             password=password,
                                             win_host=win_host)

        for founddoc in matches:
            if founddoc not in docs:
                if retry_unexpected:
                    print "Retrying search because unexpected document %s was present." % (founddoc)
                    break
                raise Exception("Unexpected document %s returned when searching; results =[%s]" % (founddoc,"".join(matches)))
        else:
            # If we got all the way through the loop, there were no unexpected docs to retry
            retry_unexpected = False

        if retry_unexpected:
            # We only want to retry once, so clear the flag that triggers retries, sleep a couple
            # seconds to give the old slice time to shut down and then continue on to the next
            # search attempt.
            retry_unexpected = False
            time.sleep(2.0)
            continue

        # matches are url-encoded, full file names (including /root)
        # Filenames in docs are therefore url encoded here
        failed = False
        for doc in docs:
            quotedoc = doc #urllib.quote(doc,safe='')
            if quotedoc not in matches:
                failed = True
                break
        if failed == False:
            return
        time.sleep(1)

    raise Exception("Expected document %s not returned when searching; actual results =[%s]" % (quotedoc,"".join(matches)))

def wait_for_ingest( timeout=3600 ):
    docs.wait_for_ingestion(timeout=timeout)

# Create a file system repository connection via the UI
def define_filesystem_repository_connection_ui( username, password, connection_name, connection_description,
                                                                        throttles=None,
                                                                        max_connections=None ):
    """ The throttles argument is an array of tuples.  Each tuple represents a throttle and is of the form (regexp,description,avg-fetch-rate).
    """
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for repository connection management and click it
    window = vb.find_window("")
    link = window.find_link("List repository connections")
    link.click( )

    # Click "add a connection"
    window = vb.find_window("")
    link = window.find_link("Add a connection")
    link.click( )

    # Find the right form elements and set them
    window = vb.find_window("")
    form = window.find_form("editconnection")

    # "Name" tab
    namefield = form.find_textarea("connname")
    descriptionfield = form.find_textarea("description")
    namefield.set_value( connection_name )
    descriptionfield.set_value( connection_description )

    # "Type" tab
    link = window.find_link("Type tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    connectortypefield = form.find_selectbox("classname")
    connectortypefield.select_value( "com.metacarta.crawler.connectors.filesystem.FileConnector" )
    # Click the "Continue" button
    continue_button = window.find_button("Continue to next page")
    continue_button.click( )
    window = vb.find_window("")

    # "Throttling" tab
    link = window.find_link("Throttling tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")

    if throttles != None:
        for throttle in throttles:
            regexp,description,rate = throttle
            # Add a throttle with the specified parameters
            regexpfield = form.find_textarea("throttle")
            descfield = form.find_textarea("throttledesc")
            valuefield = form.find_textarea("throttlevalue")
            regexpfield.set_value( regexp )
            if description != None:
                descfield.set_value( description )
            valuefield.set_value( str(rate) )
            add_button = window.find_button("Add throttle")
            add_button.click()
            window = vb.find_window("")
            form = window.find_form("editconnection")

    if max_connections != None:
        form.find_textarea("maxconnections").set_value( str(max_connections) )

    # Now, save this page
    save_button = window.find_button("Save this connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")

# Delete a file system repository connection via the UI
def delete_filesystem_repository_connection_ui( username, password, connection_name ):
    delete_repository_connection_ui( username, password, connection_name )

# Define a standard job using the UI
def define_filesystem_job_ui( username,
                password,
                job_name,
                connection_name,
                startpoints_and_matches,
                collection_name=None,
                document_template=None,
                hop_filters=None,
                hop_mode=None,
                type="specified",
                startmethod="windowbegin",
                recrawlinterval=0 ):
    """connection_name is the name of the filesystem connection.  startpoints_and_matches
       is an array, each element of which is a tuple.  The tuple consists of the start point
       path, and an array of match specifications.  Each match specification is a tuple
       consisting of a string (either "include" or "exclude"), a type (either "file" or "directory"),
       and a match value (such as "*.txt").
       Legal values for type are: "specified" or "continuous"
       Legal values for start method are: "windowbegin", "windowinside", or "disable".
       Hop filters are an array of tuples, each one ( filter_name, filter_value ).
       Hop mode has the legal values "accurate", "nodelete", or "neverdelete".
    """
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("List jobs")
    link.click( )

    # Grab the new window
    window = vb.find_window("")
    # Add a job
    link = window.find_link("Add a job")
    link.click( )

    # Grab the edit window
    window = vb.find_window("")
    form = window.find_form("editjob")

    # "Name" tab
    # textarea for setting description
    form.find_textarea("description").set_value( job_name )

    # "Connection" tab
    link = window.find_link("Connection tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # start method
    if startmethod == "windowbegin":
        startmethod_value = 0
    elif startmethod == "windowinside":
        startmethod_value = 1
    elif startmethod == "disable":
        startmethod_value = 2
    else:
        raise Exception("Illegal start method value: '%s'" % startmethod )
    form.find_selectbox("startmethod").select_value( str(startmethod_value) )
    # connection name
    form.find_selectbox("connectionname").select_value( connection_name )
    # output connection name
    form.find_selectbox("outputname").select_value( "GTS" )
    # Click the "Continue" button
    window.find_button("Continue to next screen").click( )
    window = vb.find_window("")
    form = window.find_form("editjob")

    # "Scheduling" tab
    link = window.find_link("Scheduling tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # type
    if type == "specified":
        type_value = 1
    elif type == "continuous":
        type_value = 0
    else:
        raise Exception("Illegal type value: '%s'" % type )
    form.find_selectbox("scheduletype").select_value( str(type_value) )
    # Recrawl interval
    if type == "continuous":
        form.find_textarea("recrawlinterval").set_value( str(recrawlinterval) )

    # "Hop Filters" tab
    link = window.find_link("Hop Filters tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if hop_filters != None:
        for filterelement in hop_filters:
            filter_name, filter_value = filterelement
            if filter_value == None:
                filter_value = ""
            form.find_textarea("hopmax_"+filter_name).set_value(str(filter_value))
    if hop_mode != None:
        if hop_mode == "accurate":
            mode = 0
        elif hop_mode == "nodelete":
            mode = 1
        elif hop_mode == "neverdelete":
            mode = 2
        else:
            raise Exception("Illegal mode %s" % hop_mode)
        form.find_radiobutton("hopcountmode",str(mode)).select()

    # "Collections" tab
    link = window.find_link("Collections tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # textarea for setting collection
    if collection_name != None:
        form.find_textarea("gts_collectionname").set_value( collection_name )
        
    # "Template" tab
    link = window.find_link("Template tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # textarea for setting document template
    if document_template != None:
        form.find_textarea("gts_documenttemplate").set_value( document_template )

    # "Paths" tab
    link = window.find_link("Paths tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # Now, set up paths and matches
    path_index = 0
    for pathelement in startpoints_and_matches:
        path, matches = pathelement
        # Add the path
        form.find_textarea("specpath").set_value( path )
        # Click the "Add" button
        window.find_button("Add new path").click( )
        window = vb.find_window("")
        form = window.find_form("editjob")
        # Now, go through the matches
        for match in matches:
            include_exclude, match_type, match_value = match
            assert include_exclude == "include" or include_exclude == "exclude"
            assert match_type == "file" or match_type == "directory"
            form.find_selectbox("specflavor_%d" % path_index).select_value(include_exclude)
            form.find_selectbox("spectype_%d" % path_index).select_value(match_type)
            form.find_textarea("specmatch_%d" % path_index).set_value(match_value)
            window.find_button("Add new match for path #%d" % path_index).click( )
            window = vb.find_window("")
            form = window.find_form("editjob")
        path_index += 1

    # Finally, submit the form
    window.find_button("Save this job").click( )
    window = vb.find_window("")
    jobid = window.find_match("<!--jobid=(.*)-->",1)
    return jobid

# Delete a job using the UI
def delete_job_ui( username,
                password,
                jobid ):
    """Delete the specified job using the UI.  No attempt
       is made to insure that the job actually can be deleted.  If it is running
       then this operation will fail.
    """

    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("List jobs")
    if link == None:
        raise Exception("Can't find list link for jobs");
    link.click( )

    # Grab the new window
    window = vb.find_window("")
    # Find the delete link
    deletelink = window.find_link("Delete job "+jobid)
    if deletelink == None:
        raise Exception("Can't find delete link for job %s" % jobid)
    deletelink.click( )

    # Grab the new window, and confirm that the job is gone
    window = vb.find_window("")
    window.check_no_match("View job "+jobid)

# Start a job using the UI
def start_job_ui( username,
                password,
                jobid ):
    """ Start a job, using the UI to do it.  This does not confirm
        that the job has started, due to the difficulty of getting
        the timing right, but it does kick the job off at least.
    """
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("Manage jobs")
    link.click( )

    # Grab the new window
    window = vb.find_window("")
    # Find the link that would start the job
    link = window.find_link("Start job "+jobid)
    # Click it!
    link.click( )

# View repository connection via the UI (and check for 'working' status)
def view_repository_connection_ui( username, password, connection_name, match_string="Connection working" ):
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for repository connection management and click it
    window = vb.find_window("")
    link = window.find_link("List repository connections")
    link.click( )

    # Now, find the delete link for this connection
    window = vb.find_window("")
    link = window.find_link("View "+connection_name)
    link.click( )

    # Verify that the connection was OK
    window = vb.find_window("")
    window.find_match(match_string)

# Delete a repository connection via the UI
def delete_repository_connection_ui( username, password, connection_name ):
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for repository connection management and click it
    window = vb.find_window("")
    link = window.find_link("List repository connections")
    link.click( )

    # Now, find the delete link for this connection
    window = vb.find_window("")
    link = window.find_link("Delete "+connection_name)
    link.click( )

    # Verify that the connection was deleted
    window = vb.find_window("")
    # simply make sure it's not an error screen
    window.find_match("List of Repository Connections")

# Delete an authority connection via the UI
def delete_authority_connection_ui( username, password, connection_name ):
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for repository connection management and click it
    window = vb.find_window("")
    link = window.find_link("List authorities")
    link.click( )

    # Now, find the delete link for this connection
    window = vb.find_window("")
    link = window.find_link("Delete "+connection_name)
    link.click( )

    # Verify that the connection was deleted
    window = vb.find_window("")
    # simply make sure it's not an error screen
    window.find_match("List of Authority Connections")


# Pause a job using the UI
def pause_job_ui( username,
                password,
                jobid ):
    """ Pause a job, using the UI to do it.  This does not confirm
        that the job has paused, due to the difficulty of getting
        the timing right, but it does pause the job off at least.
    """
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("Manage jobs")
    link.click( )

    # Grab the new window
    window = vb.find_window("")
    # Find the link that would start the job
    link = window.find_link("Pause job "+jobid)
    # Click it!
    link.click( )

# Resume a job using the UI
def resume_job_ui( username,
                password,
                jobid ):
    """ Resume a job, using the UI to do it.  This does not confirm
        that the job has resumed, due to the difficulty of getting
        the timing right.
    """
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("Manage jobs")
    link.click( )

    # Grab the new window
    window = vb.find_window("")
    # Find the link that would start the job
    link = window.find_link("Resume job "+jobid)
    # Click it!
    link.click( )

# Get a job's status string from the UI
def get_job_status_ui( username, password, jobid ):
    """ Find a job's status in the UI given it's ID.
    """

    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("Manage jobs")
    link.click( )

    # Grab the new window
    window = vb.find_window("")
    # Use the built-in function to look for a match
    return window.find_match( "<td class=\"columncell\"><!--jobid=%s-->([^<]*)</td><td class=\"columncell\">([^<]*)</td>" % jobid, group=2)

def find_job_by_name_ui( username, password, jobname, connectionname ):
    """ Look for a job matching the provided description. """

    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("List jobs")
    link.click( )
    window = vb.find_window("")
    window.find_match_no_newlines("<tr[^>]*>.*<td[^>]*>%s</td[^>]*>.*<td[^>]*>%s</td[^>]*>.*</tr[^>]*>" % (jobname,connectionname))

    link = window.find_link("Manage jobs")
    link.click( )

    # Get the text and look for something that matches what we are looking for.
    # This is complicated by the fact that python's regexp processing doesn't like newlines, so eliminate those first.
    window = vb.find_window("")
    return window.find_match_no_newlines("<!--jobid=([0123456789]*)-->%s<" % (jobname), group=1)

def find_connection_by_name_ui( username, password, connectionname ):
    """ Look for a connection matching the provided description. """

    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for repository connection management and click it
    window = vb.find_window("")
    link = window.find_link("List repository connections")
    link.click( )

    # Look for a view link for the correct connection name
    window = vb.find_window("")
    window.find_link("View %s" % connectionname)

def find_status_by_job_name_ui( username, password, jobname, jobstatus, totalqueue, activequeue, remainingqueue ):
    """ Look for a matching jobstatus. """

    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for repository connection management and click it
    window = vb.find_window("")
    link = window.find_link("Manage jobs")
    link.click( )

    # Get the text and look for something that matches what we are looking for.
    # This is complicated by the fact that python's regexp processing doesn't like newlines, so eliminate those first.
    window = vb.find_window("")
    return window.find_match_no_newlines("<tr[^>]*>.*<td[^>]*><!--jobid=([0123456789]*)-->%s</td[^>]*>.*<td[^>]*>%s</td[^>]*>.*<td[^>]*>%s</td[^>]*>.*<td[^>]*>%s</td[^>]*>.*<td[^>]*>%s</td[^>]*>.*</tr[^>]*>" % (jobname,jobstatus,totalqueue,activequeue,remainingqueue), group=1)

# Parse a date of the form 03-25-2008
def parse_date( date_value ):
    date_fields = date_value.split("-")
    return (int(date_fields[2]),int(date_fields[0]),int(date_fields[1]))

# Parse a time of the form 10:11:20.523
def parse_time( time_value ):
    time_fields = time_value.split(":")
    return (int(time_fields[0]), int(time_fields[1]), float(time_fields[2]))

# Parse a time value from a report into seconds since epoch.
# Time values look something like this: 03-25-2008 10:11:20.523
# Note there is no timezone, so the value must be parsed in terms of the appliance's
# default timezone.
def parse_date_time( time_value ):
    date_and_time = time_value.split(" ")
    year,mon,mday = parse_date(date_and_time[0])
    hour,min,sec = parse_time(date_and_time[1])
    return time.mktime((year,mon,mday,hour,min,int(sec),-1,-1,-1))

# Helper method: Decode html found in result data.  This removes nobr's, a tags, and converts br's to newlines
def decode_html( raw_data ):
    replace_data = ""
    start_pos = 0
    while True:
        new_pos = raw_data.find("<a",start_pos)
        if new_pos == -1:
            replace_data = replace_data + raw_data[start_pos:len(raw_data)]
            break
        replace_data = replace_data + raw_data[start_pos:new_pos]
        end_pos = raw_data.find(">",new_pos)
        if end_pos == -1:
            raise Exception("Can't find end of '<a' tag")
        start_pos = end_pos+1
    replace_data = replace_data.replace("<nobr>","")
    replace_data = replace_data.replace("</nobr>","")
    replace_data = replace_data.replace("<nobr/>","")
    replace_data = replace_data.replace("<br/>","\n")
    replace_data = replace_data.replace("</a>","")

    return replace_data.strip()

# Helper method to parse a single table row.  Returns data as an array.
def process_table_row( data, start_position ):
    row_end = data.find("</tr>",start_position)
    if row_end == -1:
        raise Exception( "Couldn't find end of row in '%s' at position %d" % (data,start_position) )
    current_position = start_position
    rval = []
    while True:
        new_position = data.find("<td",current_position)
        if new_position == -1 or new_position > row_end:
            return rval
        new_position = data.find(">",new_position)
        if new_position == -1:
            raise Exception("Missing td endbracket")
        new_position = new_position + 1
        end_position = data.find("</td>",new_position)
        if end_position == -1:
            raise Exception("Missing </td> tag")
        if end_position > row_end:
            raise Exception("Found </tr> before </td>")
        raw_data = data[new_position:end_position]
        current_position = end_position + 4
        rval.append( decode_html(raw_data) )
    return rval

# Helper method to parse a single result row.  Returns data as a dictionary, with one element for each <td> cell
def process_data_row( data, headers, start_position ):
    row_data = process_table_row( data, start_position )
    if len(row_data) != len(headers):
        raise Exception( "Row data does not agree with header data in '%s' at position %d" % (data,start_position))
    rval = {}
    for index in range(0,len(row_data)):
        row_value = row_data[index]
        header_value = headers[index]
        rval[header_value] = row_value
    return rval

# Helper method to parse an entire resultset.  Returns result as an array of dictionaries.
def parse_tableresult( data ):
    # We look for tr's of class "headerrow" followed by "evendatarow" and "odddatarow"
    current_pos = data.find('<tr class="headerrow">')
    if current_pos == -1:
        raise Exception( "Missing report header row in data '%s'" % data )
    # Parse the header row into header data
    headers = process_table_row( data, current_pos )
    rval = []
    while True:
        # Find evendatarow
        new_pos = data.find("<tr class=\"evendatarow\">",current_pos)
        if new_pos == -1:
            break
        rval.append( process_data_row( data, headers, new_pos ) )
        current_pos = new_pos
        new_pos = data.find("<tr class=\"odddatarow\">",current_pos)
        if new_pos == -1:
            break
        rval.append( process_data_row( data, headers, new_pos ) )
        current_pos = new_pos

    return rval

# Sanity check a report page
def report_sanity_check( window, more):
    """ Look at the previous/next buttons to make sure they grey out when they are supposed to, etc. """
    # First, we should not be able to find the "Previous page" button.
    found_it = False
    try:
        window.find_button("Previous page")
        found_it = True
    except:
        pass
    if found_it:
        raise Exception("Previous page button showed up when it shouldn't have")
    # The "next" button should not show up either, because the max results should contain anything we try to do with the test
    found_it = False
    try:
        window.find_button("Next page")
        found_it = True
    except:
        pass
    if found_it and not more:
        raise Exception("Next page button showed up when it shouldn't have")
    if not found_it and more:
        raise Exception("Next page button didn't show up but it should have")

    range = window.find_match("<td class=\"description\"><nobr>Rows:</nobr></td><td class=\"value\">([0-9]*-([0-9]*|END))</td>", 1)

    values = range.split("-")
    if values[0] != "0":
        raise Exception("Row range should have begun at zero - instead saw %s" % values[0])
    if values[1] != "END" and not more:
        raise Exception("Row range should have ended with END - instead saw %s" % values[1])

# Format activity list in a manner acceptable to the API methods
def format_activity_list(activities_list):
    """ Format a list of activities in a manner acceptable to the API scripts """
    return ",".join(activities_list)

# Format time value in a manner acceptable to the API methods
def format_time(ms_since_epoch):
    """ Format a time in ms since epoch in a manner acceptable to the API scripts """
    if ms_since_epoch != None and len(str(ms_since_epoch)) > 0:
        return str(ms_since_epoch)
    return ""

# Format window size in minutes for the API methods
def format_window_size(minutes):
    """ Format window size in minutes """
    if minutes != None and len(str(minutes)) > 0:
        return str(minutes)
    return "5"

# Split a comma-delimited line into multiple columns, unescaping in the process
def split_api_result_line(result_line):
    """ Split a comma-delimited line into multiple columns, unescaping in the process
    """
    return_value = []
    current_value = ""
    index = 0
    for index in range(len(result_line)):
        result_char = result_line[index]
        if result_char == '\\':
            index += 1
            result_char = result_line[index]
            current_value += result_char
        elif result_char == ',':
            index += 1
            return_value += [ current_value ]
            current_value = ""
        else:
            current_value += result_char
    return_value += [ current_value ]
    return return_value

# Digest report API call result and an array of dictionaries corresponding to the results returned
def process_api_result(result, column_list):
    """ Digest API result and make an array of dictionaries out of it """
    return_value = []
    for result_line in result.splitlines():
        value_array = split_api_result_line(result_line)
        dict = {}
        for index in range(len(column_list)):
            column_name = column_list[index]
            value = value_array[index]
            dict[column_name] = value.strip()
        return_value += [ dict ]
    return return_value

# List jobs using the API and return the results.
def list_jobs_api( ):
    """ List jobs using the API """
    result = invoke_script( ["/usr/lib/metacarta/crawler-listjobs"] )
    return process_api_result(result,["identifier",
                        "description",
                        "connection",
                        "startmode",
                        "runmode",
                        "hopcountmode",
                        "priority",
                        "rescaninterval",
                        "expirationinterval",
                        "reseedinterval",
                        "documenttemplate"])

# List job statuses using the API and return the results
def list_job_statuses_api( ):
    """ List job statuses using the API and return the results """
    result = invoke_script( ["/usr/lib/metacarta/crawler-listjobstatuses"] )
    return process_api_result(result,["identifier",
                        "description",
                        "status",
                        "inqueue",
                        "outstanding",
                        "processed",
                        "starttime",
                        "endtime",
                        "errortext"])

# Get job collections using the API and return the results
def get_job_collections_api( job_id ):
    """ Get job collections using the API and return the results
    """
    result = invoke_script( ["/usr/lib/metacarta/crawler-getjobcollections", job_id] )
    return process_api_result(result,["collection"])

# Get job schedule using the API and return the results
def get_job_schedule_api( job_id ):
    """ Get job schedule using the API and return the results """
    result = invoke_script( ["/usr/lib/metacarta/crawler-getjobschedule", job_id] )
    return process_api_result(result,["daysofweek",
                        "years",
                        "months",
                        "days",
                        "hours",
                        "minutes",
                        "timezone",
                        "duration"])

# Run a simple history report using the API and return the results.
def run_simple_history_report_api( connection_name,
        activities_list,
        start_time=None, end_time=None,
        entity_regexp=None, result_regexp=None,
        start_result_row=0, max_result_count=10000 ):
    """ Run a simple history report.  Return an array of dictionaries, each dictionary having fields that correspond to the data
        from the report.  For arguments, None indicates the default (start time
        is one hour ago, end time is now).
    """
    result = invoke_script( ["/usr/lib/metacarta/crawler-runsimplehistory",
                                connection_name,
                                format_activity_list(activities_list),
                                format_time(start_time),
                                format_time(end_time),
                                entity_regexp,
                                result_regexp,
                                "",
                                str(start_result_row),
                                str(max_result_count) ] )
    # Decode the result
    return process_api_result(result,["identifier","activity","start_time","elapsed_time","result_code","result_desc","byte_count"])

# Run a simple history report from the UI and return the results.
def run_simple_history_report_ui( username, password, connection_name,
        activities_list,
        start_time=None, end_time=None,
        entity_regexp=None, result_regexp=None,
        max_result_count=10000, more=False ):
    """ Run a simple history report.  Return an array of dictionaries, each dictionary having fields that correspond to the header data
        from the report.  For arguments, None indicates the default (start time
        is one hour ago, end time is now).  Inside the tuples, any None in any one place indicates the current time for the
        whole tuple, so (None,None,None,None,None) would be one way of indicating that.
    """

    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("Simple history")
    link.click( )

    # Select the connection
    # Grab the new window
    window = vb.find_window("")
    form = window.find_form("report")
    form.find_selectbox("reportconnection").select_value( connection_name )
    # Submit
    window.find_button("Continue").click( )

    # Set up all the other report parameters
    # Select the activities
    window = vb.find_window("")
    form = window.find_form("report")
    activities_select = form.find_selectbox("reportactivities")
    for activity in activities_list:
        activities_select.multi_select_value( activity )
    # Entity match
    if entity_regexp != None:
        form.find_textarea("reportentitymatch").set_value( entity_regexp )
    if result_regexp != None:
        form.find_textarea("reportresultcodematch").set_value( result_regexp )
    # Select the start time and end time
    if start_time != None:
        start_hour, start_minute, start_day, start_month, start_year = start_time
        if start_hour != None:
            form.find_selectbox("reportstarthour").select_value( str(start_hour) )
        else:
            form.find_selectbox("reportstarthour").select_value( "" )
        if start_minute != None:
            form.find_selectbox("reportstartminute").select_value( str(start_minute) )
        else:
            form.find_selectbox("reportstartminute").select_value( "" )
        if start_day != None:
            form.find_selectbox("reportstartday").select_value( str(start_day) )
        else:
            form.find_selectbox("reportstartday").select_value( "" )
        if start_month != None:
            form.find_selectbox("reportstartmonth").select_value( str(start_month) )
        else:
            form.find_selectbox("reportstartmonth").select_value( "" )
        if start_year != None:
            form.find_selectbox("reportstartyear").select_value( str(start_year) )
        else:
            form.find_selectbox("reportstartyear").select_value( "" )
    if end_time != None:
        end_hour, end_minute, end_day, end_month, end_year = end_time
        if end_hour != None:
            form.find_selectbox("reportendhour").select_value( str(end_hour) )
        else:
            form.find_selectbox("reportendhour").select_value( "" )
        if end_minute != None:
            form.find_selectbox("reportendminute").select_value( str(end_minute) )
        else:
            form.find_selectbox("reportendminute").select_value( "" )
        if end_day != None:
            form.find_selectbox("reportendday").select_value( str(end_day) )
        else:
            form.find_selectbox("reportendday").select_value( "" )
        if end_month != None:
            form.find_selectbox("reportendmonth").select_value( str(end_month) )
        else:
            form.find_selectbox("reportendmonth").select_value( "" )
        if end_year != None:
            form.find_selectbox("reportendyear").select_value( str(end_year) )
        else:
            form.find_selectbox("reportendyear").select_value( "" )
    form.find_textarea("rowcount").set_value(str(max_result_count))

    # Fire off the query
    window.find_button("Execute this query").click( )

    # Get the window contents, and scrape out the report data
    window = vb.find_window("")
    # Make sure everything about the response is consistent
    report_sanity_check(window,more)
    return parse_tableresult( window.get_data() )

# Run a max activity history report using the API and return the results.
def run_max_activity_history_report_api( connection_name,
        activities_list,
        start_time=None, end_time=None,
        entity_regexp=None, result_regexp=None,
        start_result_row=0, max_result_count=10000,
        entity_bin_regexp=None, window_size_minutes=None ):
    """ Run a max activity history report.  Return an array of dictionaries, each dictionary having fields that correspond to the data
        from the report.  For arguments, None indicates the default (start time
        is one hour ago, end time is now).
    """
    result = invoke_script( ["/usr/lib/metacarta/crawler-runmaxactivityhistory",
                                connection_name,
                                format_activity_list(activities_list),
                                format_time(start_time),
                                format_time(end_time),
                                entity_regexp,
                                result_regexp,
                                "",
                                entity_bin_regexp,
                                format_window_size(window_size_minutes),
                                str(start_result_row),
                                str(max_result_count) ] )
    # Decode the result
    return process_api_result(result,["identifier_bucket","starttime_ms","endtime_ms","activity_count"])

# Run a max activity  history report from the UI and return the results.
def run_max_activity_history_report_ui( username, password, connection_name,
        activities_list,
        start_time=None, end_time=None,
        entity_regexp=None, result_regexp=None,
        max_result_count=10000, more=False,
        entity_bin_regexp=None, window_size_minutes=None ):
    """ Run a max activity history report.  Return an array of dictionaries, each dictionary having fields that correspond to the header data
        from the report.  For arguments, None indicates the default (start time
        is one hour ago, end time is now).  Inside the tuples, any None in any one place indicates the current time for the
        whole tuple, so (None,None,None,None,None) would be one way of indicating that.
    """

    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("Maximum activity")
    link.click( )

    # Select the connection
    # Grab the new window
    window = vb.find_window("")
    form = window.find_form("report")
    form.find_selectbox("reportconnection").select_value( connection_name )
    # Submit
    window.find_button("Continue").click( )

    # Set up all the other report parameters
    # Select the activities
    window = vb.find_window("")
    form = window.find_form("report")
    activities_select = form.find_selectbox("reportactivities")
    for activity in activities_list:
        activities_select.multi_select_value( activity )
    # Entity match
    if entity_regexp != None:
        form.find_textarea("reportentitymatch").set_value( entity_regexp )
    if result_regexp != None:
        form.find_textarea("reportresultcodematch").set_value( result_regexp )
    # Select the start time and end time
    if start_time != None:
        start_hour, start_minute, start_day, start_month, start_year = start_time
        if start_hour != None:
            form.find_selectbox("reportstarthour").select_value( str(start_hour) )
        else:
            form.find_selectbox("reportstarthour").select_value( "" )
        if start_minute != None:
            form.find_selectbox("reportstartminute").select_value( str(start_minute) )
        else:
            form.find_selectbox("reportstartminute").select_value( "" )
        if start_day != None:
            form.find_selectbox("reportstartday").select_value( str(start_day) )
        else:
            form.find_selectbox("reportstartday").select_value( "" )
        if start_month != None:
            form.find_selectbox("reportstartmonth").select_value( str(start_month) )
        else:
            form.find_selectbox("reportstartmonth").select_value( "" )
        if start_year != None:
            form.find_selectbox("reportstartyear").select_value( str(start_year) )
        else:
            form.find_selectbox("reportstartyear").select_value( "" )
    if end_time != None:
        end_hour, end_minute, end_day, end_month, end_year = end_time
        if end_hour != None:
            form.find_selectbox("reportendhour").select_value( str(end_hour) )
        else:
            form.find_selectbox("reportendhour").select_value( "" )
        if end_minute != None:
            form.find_selectbox("reportendminute").select_value( str(end_minute) )
        else:
            form.find_selectbox("reportendminute").select_value( "" )
        if end_day != None:
            form.find_selectbox("reportendday").select_value( str(end_day) )
        else:
            form.find_selectbox("reportendday").select_value( "" )
        if end_month != None:
            form.find_selectbox("reportendmonth").select_value( str(end_month) )
        else:
            form.find_selectbox("reportendmonth").select_value( "" )
        if end_year != None:
            form.find_selectbox("reportendyear").select_value( str(end_year) )
        else:
            form.find_selectbox("reportendyear").select_value( "" )

    if entity_bin_regexp != None:
        form.find_textarea("reportbucketdesc").set_value( entity_bin_regexp )
    if window_size_minutes != None:
        form.find_textarea("reportinterval").set_value( str(window_size_minutes) )

    form.find_textarea("rowcount").set_value(str(max_result_count))

    # Fire off the query
    window.find_button("Execute this query").click( )

    # Get the window contents, and scrape out the report data
    window = vb.find_window("")
    # Make sure everything about the response is consistent
    report_sanity_check(window,more)
    # Parse the result
    return parse_tableresult( window.get_data() )

# Run a max bandwidth history report using the API and return the results.
def run_max_bandwidth_history_report_api( connection_name,
        activities_list,
        start_time=None, end_time=None,
        entity_regexp=None, result_regexp=None,
        start_result_row=0, max_result_count=10000,
        entity_bin_regexp=None, window_size_minutes=None ):
    """ Run a max bandwidth report.  Return an array of dictionaries, each dictionary having fields that correspond to the data
        from the report.  For arguments, None indicates the default (start time
        is one hour ago, end time is now).
    """
    result = invoke_script( ["/usr/lib/metacarta/crawler-runmaxbandwidthhistory",
                                connection_name,
                                format_activity_list(activities_list),
                                format_time(start_time),
                                format_time(end_time),
                                entity_regexp,
                                result_regexp,
                                "",
                                entity_bin_regexp,
                                format_window_size(window_size_minutes),
                                str(start_result_row),
                                str(max_result_count) ] )
    # Decode the result
    return process_api_result(result,["identifier_bucket","starttime_ms","endtime_ms","byte_count"])

# Run a max bandwidth  history report from the UI and return the results.
def run_max_bandwidth_history_report_ui( username, password, connection_name,
        activities_list,
        start_time=None, end_time=None,
        entity_regexp=None, result_regexp=None,
        max_result_count=10000, more=False,
        entity_bin_regexp=None, window_size_minutes=None ):
    """ Run a max bandwidth report.  Return an array of dictionaries, each dictionary having fields that correspond to the header data
        from the report.  For arguments, None indicates the default (start time
        is one hour ago, end time is now).  Inside the tuples, any None in any one place indicates the current time for the
        whole tuple, so (None,None,None,None,None) would be one way of indicating that.
    """

    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("Maximum bandwidth")
    link.click( )

    # Select the connection
    # Grab the new window
    window = vb.find_window("")
    form = window.find_form("report")
    form.find_selectbox("reportconnection").select_value( connection_name )
    # Submit
    window.find_button("Continue").click( )

    # Set up all the other report parameters
    # Select the activities
    window = vb.find_window("")
    form = window.find_form("report")
    activities_select = form.find_selectbox("reportactivities")
    for activity in activities_list:
        activities_select.multi_select_value( activity )
    # Entity match
    if entity_regexp != None:
        form.find_textarea("reportentitymatch").set_value( entity_regexp )
    if result_regexp != None:
        form.find_textarea("reportresultcodematch").set_value( result_regexp )
    # Select the start time and end time
    if start_time != None:
        start_hour, start_minute, start_day, start_month, start_year = start_time
        if start_hour != None:
            form.find_selectbox("reportstarthour").select_value( str(start_hour) )
        else:
            form.find_selectbox("reportstarthour").select_value( "" )
        if start_minute != None:
            form.find_selectbox("reportstartminute").select_value( str(start_minute) )
        else:
            form.find_selectbox("reportstartminute").select_value( "" )
        if start_day != None:
            form.find_selectbox("reportstartday").select_value( str(start_day) )
        else:
            form.find_selectbox("reportstartday").select_value( "" )
        if start_month != None:
            form.find_selectbox("reportstartmonth").select_value( str(start_month) )
        else:
            form.find_selectbox("reportstartmonth").select_value( "" )
        if start_year != None:
            form.find_selectbox("reportstartyear").select_value( str(start_year) )
        else:
            form.find_selectbox("reportstartyear").select_value( "" )
    if end_time != None:
        end_hour, end_minute, end_day, end_month, end_year = end_time
        if end_hour != None:
            form.find_selectbox("reportendhour").select_value( str(end_hour) )
        else:
            form.find_selectbox("reportendhour").select_value( "" )
        if end_minute != None:
            form.find_selectbox("reportendminute").select_value( str(end_minute) )
        else:
            form.find_selectbox("reportendminute").select_value( "" )
        if end_day != None:
            form.find_selectbox("reportendday").select_value( str(end_day) )
        else:
            form.find_selectbox("reportendday").select_value( "" )
        if end_month != None:
            form.find_selectbox("reportendmonth").select_value( str(end_month) )
        else:
            form.find_selectbox("reportendmonth").select_value( "" )
        if end_year != None:
            form.find_selectbox("reportendyear").select_value( str(end_year) )
        else:
            form.find_selectbox("reportendyear").select_value( "" )

    if entity_bin_regexp != None:
        form.find_textarea("reportbucketdesc").set_value( entity_bin_regexp )
    if window_size_minutes != None:
        form.find_textarea("reportinterval").set_value( str(window_size_minutes) )

    form.find_textarea("rowcount").set_value(str(max_result_count))

    # Fire off the query
    window.find_button("Execute this query").click( )

    # Get the window contents, and scrape out the report data
    window = vb.find_window("")
    # Make sure everything about the response is consistent
    report_sanity_check(window,more)
    # Parse the result
    return parse_tableresult( window.get_data() )

# Run a result histogram history report using the API and return the results.
def run_result_histogram_history_report_api( connection_name,
        activities_list,
        start_time=None, end_time=None,
        entity_regexp=None, result_regexp=None,
        start_result_row=0, max_result_count=10000,
        entity_bin_regexp=None, result_bin_regexp=None ):
    """ Run a result histogram report.  Return an array of dictionaries, each dictionary having fields that correspond to the data
        from the report.  For arguments, None indicates the default (start time
        is one hour ago, end time is now).
    """
    result = invoke_script( ["/usr/lib/metacarta/crawler-runresulthistory",
                                connection_name,
                                format_activity_list(activities_list),
                                format_time(start_time),
                                format_time(end_time),
                                entity_regexp,
                                result_regexp,
                                "",
                                entity_bin_regexp,
                                result_bin_regexp,
                                str(start_result_row),
                                str(max_result_count) ] )
    # Decode the result
    return process_api_result(result,["identifier_bucket","resultcode_bucket","event_count"])

# Run a result histogram  history report from the UI and return the results.
def run_result_histogram_history_report_ui( username, password, connection_name,
        activities_list,
        start_time=None, end_time=None,
        entity_regexp=None, result_regexp=None,
        max_result_count=10000, more=False,
        entity_bin_regexp=None, result_bin_regexp=None ):
    """ Run a result histogram report.  Return an array of dictionaries, each dictionary having fields that correspond to the header data
        from the report.  For arguments, None indicates the default (start time
        is one hour ago, end time is now).  Inside the tuples, any None in any one place indicates the current time for the
        whole tuple, so (None,None,None,None,None) would be one way of indicating that.
    """

    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("Result histogram")
    link.click( )

    # Select the connection
    # Grab the new window
    window = vb.find_window("")
    form = window.find_form("report")
    form.find_selectbox("reportconnection").select_value( connection_name )
    # Submit
    window.find_button("Continue").click( )

    # Set up all the other report parameters
    # Select the activities
    window = vb.find_window("")
    form = window.find_form("report")
    activities_select = form.find_selectbox("reportactivities")
    for activity in activities_list:
        activities_select.multi_select_value( activity )
    # Entity match
    if entity_regexp != None:
        form.find_textarea("reportentitymatch").set_value( entity_regexp )
    if result_regexp != None:
        form.find_textarea("reportresultcodematch").set_value( result_regexp )
    # Select the start time and end time
    if start_time != None:
        start_hour, start_minute, start_day, start_month, start_year = start_time
        if start_hour != None:
            form.find_selectbox("reportstarthour").select_value( str(start_hour) )
        else:
            form.find_selectbox("reportstarthour").select_value( "" )
        if start_minute != None:
            form.find_selectbox("reportstartminute").select_value( str(start_minute) )
        else:
            form.find_selectbox("reportstartminute").select_value( "" )
        if start_day != None:
            form.find_selectbox("reportstartday").select_value( str(start_day) )
        else:
            form.find_selectbox("reportstartday").select_value( "" )
        if start_month != None:
            form.find_selectbox("reportstartmonth").select_value( str(start_month) )
        else:
            form.find_selectbox("reportstartmonth").select_value( "" )
        if start_year != None:
            form.find_selectbox("reportstartyear").select_value( str(start_year) )
        else:
            form.find_selectbox("reportstartyear").select_value( "" )
    if end_time != None:
        end_hour, end_minute, end_day, end_month, end_year = end_time
        if end_hour != None:
            form.find_selectbox("reportendhour").select_value( str(end_hour) )
        else:
            form.find_selectbox("reportendhour").select_value( "" )
        if end_minute != None:
            form.find_selectbox("reportendminute").select_value( str(end_minute) )
        else:
            form.find_selectbox("reportendminute").select_value( "" )
        if end_day != None:
            form.find_selectbox("reportendday").select_value( str(end_day) )
        else:
            form.find_selectbox("reportendday").select_value( "" )
        if end_month != None:
            form.find_selectbox("reportendmonth").select_value( str(end_month) )
        else:
            form.find_selectbox("reportendmonth").select_value( "" )
        if end_year != None:
            form.find_selectbox("reportendyear").select_value( str(end_year) )
        else:
            form.find_selectbox("reportendyear").select_value( "" )

    if entity_bin_regexp != None:
        form.find_textarea("reportbucketdesc").set_value( entity_bin_regexp )
    if result_bin_regexp != None:
        form.find_textarea("reportresultdesc").set_value( result_bin_regexp )

    form.find_textarea("rowcount").set_value(str(max_result_count))

    # Fire off the query
    window.find_button("Execute this query").click( )

    # Get the window contents, and scrape out the report data
    window = vb.find_window("")
    # Make sure everything about the response is consistent
    report_sanity_check(window,more)
    # Parse the result
    return parse_tableresult( window.get_data() )

document_state_dictionary = { "never_been_processed" : 0, "processed_at_least_once" : 1 }
document_status_dictionary = { "no_longer_active" : 0, "in_progress" : 1,
                                             "being_expired" : 2, "being_deleted" : 3,
                                             "available_for_processing" : 4, "available_for_expiration" : 5,
                                             "not_yet_processable" : 6, "not_yet_expirable" : 7,
                                             "waiting_forever" : 8 }

# Format the job id list
def format_job_list(job_list):
    """ Format a list of jobs in a manner acceptable to API methods """
    return ",".join(job_list)

# Format a time offset in minutes
def format_offset_minutes(time_offset_minutes):
    """ Format a time offset given in minutes as milliseconds """
    if time_offset_minutes == None:
        return "0"
    return str(int(float(time_offset_minutes) * 60000L))

api_state_map = { "never_been_processed" : "neverprocessed", "processed_at_least_once" : "previouslyprocessed" }

# Format a state list
def format_state_list(document_states):
    """ Convert standard state strings into API state strings """
    state_array = []
    for state in document_states:
        state_array += [ api_state_map[state] ]
    return ",".join(state_array)

api_status_map = { "no_longer_active" : "inactive", "in_progress" : "processing",
                        "being_expired" : "expiring", "being_deleted" : "deleting",
                        "available_for_processing" : "readyforprocessing", "available_for_expiration" : "readyforexpiration",
                        "not_yet_processable" : "waitingforprocessing", "not_yet_expirable" : "waitingforexpiration",
                        "waiting_forever" : "waitingforever" }

# Format a status list
def format_status_list(document_statuses):
    """ Convert standard status strings into API status strings """
    status_array = []
    for status in document_statuses:
        status_array += [ api_status_map[status] ]
    return ",".join(status_array)

# Run a document status report using the API and return the results.
def run_document_status_api( connection_name,
        job_list,
        time_offset_minutes=None,
        document_states=[ "never_been_processed", "processed_at_least_once" ],
        document_statuses=[ "no_longer_active", "in_progress", "being_expired", "being_deleted",
                                      "available_for_processing", "available_for_expiration",
                                      "not_yet_processable", "not_yet_expirable", "waiting_forever" ],
        identifier_regexp=None,
        start_result_row=0, max_result_count=10000 ):
    """ Run a document queue status report.  Return an array of dictionaries, each dictionary having fields that correspond to data
        from the report.  For arguments, document_statuses is an array of strings, whose legal values are:
        "no_longer_active", "in_progress", "being_expired", "being_deleted", "available_for_processing",
        "available_for_expiration", "not_yet_processable", "not_yet_expirable", "waiting_forever".  document_states is an array of strings,
        whose legal values are: "never_been_processed", and "processed_at_least_once".
    """
    result = invoke_script( ["/usr/lib/metacarta/crawler-rundocumentstatus",
                                connection_name,
                                format_job_list(job_list),
                                format_offset_minutes(time_offset_minutes),
                                format_state_list(document_states),
                                format_status_list(document_statuses),
                                identifier_regexp,
                                "",
                                str(start_result_row),
                                str(max_result_count) ] )
    # Decode the result
    return process_api_result(result,["doc_identifier","job_description","document_state","document_status","when_scheduled","action_to_take","remaining_retrycount","retrylimit_time"] )


# Run a document status report from the UI and return the results.
def run_document_status_ui( username, password, connection_name,
        job_list,
        time_offset_minutes=None,
        document_states=[ "never_been_processed", "processed_at_least_once" ],
        document_statuses=[ "no_longer_active", "in_progress", "being_expired", "being_deleted",
                                      "available_for_processing", "available_for_expiration",
                                      "not_yet_processable", "not_yet_expirable", "waiting_forever" ],
        identifier_regexp=None,
        max_result_count=10000, more=False ):
    """ Run a document queue status report.  Return an array of dictionaries, each dictionary having fields that correspond to the header data
        from the report.  For arguments, document_statuses is an array of strings, whose legal values are:
        "no_longer_active", "in_progress", "being_expired", "being_deleted", "available_for_processing",
        "available_for_expiration", "not_yet_processable", "not_yet_expirable", "waiting_forever".  document_states is an array of strings,
        whose legal values are: "never_been_processed", and "processed_at_least_once".
    """

    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("Document status")
    link.click( )

    # Select the connection
    # Grab the new window
    window = vb.find_window("")
    form = window.find_form("report")
    form.find_selectbox("statusconnection").select_value( connection_name )
    # Submit
    window.find_button("Continue").click( )

    # Set up all the other report parameters
    window = vb.find_window("")
    form = window.find_form("report")

    # Select the jobs
    if job_list != None and len(job_list) > 0:
        job_select = form.find_selectbox("statusjobs")
        for job in job_list:
            job_select.multi_select_value( job )
    else:
        raise Exception("There must be some jobs!")

    # Submit
    window.find_button("Continue").click( )
    window = vb.find_window("")
    form = window.find_form("report")

    # Set the time offset, if any
    if time_offset_minutes != None:
        form.find_textarea( "status_schedule_offset" ).set_value( str(time_offset_minutes) )

    # Select the states
    if document_states != None:
        state_select = form.find_selectbox("statusdocumentstates")
        for document_state in document_states:
            value = document_state_dictionary[ document_state ]
            state_select.multi_select_value( str(value) )

    # Select the statuses
    if document_statuses != None:
        status_select = form.find_selectbox("statusdocumentstatuses")
        for document_status in document_statuses:
            value = document_status_dictionary[ document_status ]
            status_select.multi_select_value( str(value) )

    # Entity match
    if identifier_regexp != None:
        form.find_textarea("statusidentifiermatch").set_value( identifier_regexp )

    form.find_textarea("rowcount").set_value(str(max_result_count))

    # Fire off the query
    window.find_button("Execute this query").click( )

    # Get the window contents, and scrape out the report data
    window = vb.find_window("")
    # Make sure everything about the response is consistent
    report_sanity_check(window,more)
    # Parse the result
    return parse_tableresult( window.get_data() )

# Run a queue status report using the API and return the results.
def run_queue_status_api( connection_name,
        job_list,
        time_offset_minutes=None,
        document_states=[ "never_been_processed", "processed_at_least_once" ],
        document_statuses=[ "no_longer_active", "in_progress", "being_expired", "being_deleted",
                                      "available_for_processing", "available_for_expiration",
                                      "not_yet_processable", "not_yet_expirable", "waiting_forever" ],
        identifier_regexp=None,
        start_result_row=0, max_result_count=10000,
        bucket_regexp=None ):
    """ Run a queue status report.  Return an array of dictionaries, each dictionary having fields that correspond to data
        from the report.  For arguments, document_statuses is an array of strings, whose legal values are:
        "no_longer_active", "in_progress", "being_expired", "being_deleted", "available_for_processing",
        "available_for_expiration", "not_yet_processable", "not_yet_expirable".  document_states is an array of strings,
        whose legal values are: "never_been_processed", and "processed_at_least_once".
    """
    result = invoke_script( ["/usr/lib/metacarta/crawler-runqueuestatus",
                                connection_name,
                                format_job_list(job_list),
                                format_offset_minutes(time_offset_minutes),
                                format_state_list(document_states),
                                format_status_list(document_statuses),
                                identifier_regexp,
                                "",
                                bucket_regexp,
                                str(start_result_row),
                                str(max_result_count) ] )
    # Decode the result
    return process_api_result(result,["id_bucket","inactive_count","processing_count","expiring_count","deleting_count",
        "process_ready_count","expire_ready_count","process_waiting_count","expire_waiting_count","waiting_forever_count" ] )

# Build a time value for a report given a time in seconds since epoch, using the current timezone
def build_report_time(seconds_since_epoch):
    """ Build a report time structure (hours, minutes, days, month, year) from a value of seconds since epoch. """
    time_struct = time.localtime(seconds_since_epoch)
    hours = time_struct.tm_hour
    minutes = time_struct.tm_min
    days = time_struct.tm_mday - 1
    month = time_struct.tm_mon - 1
    year = time_struct.tm_year
    return (hours, minutes, days, month, year)

# Build a time value for an report given a time in seconds since epoch
def build_api_time(seconds_since_epoch):
    """ Build a report time string (ms since epoch) from a value of seconds since epoch. """
    return str(int(seconds_since_epoch * 1000))

# Miscellaneous file system test helpers.  Dave put these here because he wanted his new tests to be able to use them; they really are pretty test-specific though.

# Copy a folder to a (new) area
def copy_folder( source, target ):
    invoke_root_script( [ "mkdir", "-p", target ] )
    invoke_root_script( [ "cp", "-r", source, target ] )

# Remove a folder
def delete_folder( target ):
    invoke_root_script( [ "rm", "-rf", target ] )

def preclean( username, print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    try:
        reset_all()
    except Exception, e:
        if print_errors:
            print "Error resetting all jobs"
            print e

    # Remove test documents first
    for folder in [ "/root/crawlarea", "/root/crawlarea2" ]:
        try:
            delete_folder( folder )
        except Exception, e:
            if print_errors:
                print "Error removing %s" % folder
                print e

    try:
        delete_crawler_user( username )
    except Exception, e:
        if print_errors:
            print "Error removing crawler user"
            print e

    try:
        teardown_connector_environment( )
    except Exception, e:
        if print_errors:
            print "Error cleaning up debs"
            print e

    try:
        # Since one of the tests deregisters the filesystem connector, reregister it here to be sure it exists.
        register_connector("com.metacarta.crawler.connectors.filesystem.FileConnector", "FilesystemConnector")
    except Exception, e:
        if print_errors:
            print "Error reregistering file system connector"
            print e
