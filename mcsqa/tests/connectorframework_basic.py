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

import pg
import re
import os
import sys
import time
import socket
import shutil
import urllib2
import traceback
import ConnectorHelpers
import VirtualBrowser
from threading import Thread
import sqatools.LicenseMakerClient
from sqatools import sqautils

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

# Dump output of lsof into log
def dump_debug():
    print "Starting dump of lsof"
    ConnectorHelpers.invoke_root_script( [ "lsof" ] )
    print "Starting dump of ps -aux"
    ConnectorHelpers.invoke_root_script( [ "ps", "-aux" ] )
    print "Done dump"
    
# Copy a folder to a (new) area
def copy_folder( source, target ):
    ConnectorHelpers.invoke_root_script( [ "mkdir", "-p", target ] )
    ConnectorHelpers.invoke_root_script( [ "cp", "-r", source, target ] )

# Remove a folder
def delete_folder( target ):
    ConnectorHelpers.invoke_root_script( [ "rm", "-rf", target ] )

# Run the spinner for looking for database/external lock deadlocks
def run_lock_spinner( import_file_name ):
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.JobStartSpinner", argument_list=[ ConnectorHelpers.process_argument(import_file_name) ] )

# Extract a password from a conf file
def extract_password(file_name):
    fd = open(file_name,"r")
    try:
        for line in fd.readlines():
            index = line.find("com.metacarta.ingest.password=")
            if index == 0:
                # Strip off the newline at the end
                return line[len("com.metacarta.ingest.password="):len(line)-1]
    finally:
        fd.close()
    raise Exception("Password not found!")

class run_ingestion_test_server_thread(Thread):
    def __init__ (self, response):
        Thread.__init__(self)
        self.response = response
        self.setDaemon(True)

    def run(self):
        try:
            # Start the ingestion test server
            # Warning: This will hang until shut down!
            while True:
                output = ConnectorHelpers.invoke_root_script( ["nc", "-l", "localhost", "7031"] )
                # Because system health checker doesn't die when advertised, we *may* need to restart nc
                if output.find("MetaCarta-Verbose-Response:") == -1:
                    break

        except Exception, e:
            self.response += str(e)

# Disable the ability of the appliance to receive data from localhost port 7031
def startup_fake_ingestion_service(response):
    """ Use nc to simulate a busted ingestion system """
    # Start nc, by invoking in a different thread
    thread = run_ingestion_test_server_thread(response)
    thread.start()
    # Sleep until we think nc is listening
    time.sleep(20)
    print "Ingestion test server successfully started up"
    return thread

# Re-enable the ability of the appliance to receive data from localhost port 7031
def shutdown_fake_ingestion_service(thread=None, response=None):
    """ Undo changes from disable """
    # Send a shutdown signal.  If this is nc listening, it should cause it to exit but not respond.  If nc has already exited, it should timeout and get an exception.
    # Otherwise, there should be a real response
    try:
        socket.setdefaulttimeout(10)
        ConnectorHelpers.invoke_curl("http://localhost:7031/services/HTTPIngest/?STATUS")
        socket.setdefaulttimeout(1000000)
    except:
        # Must already have been shut down; continue
        pass

    socket.setdefaulttimeout(1000000)

    print "Ingest system fakeout successfully shut down"
    # If there's a thread we know about, let it exit, and report any errors
    if thread:
        thread.join()
        if response:
            if len(response) > 0:
                raise Exception("Ingestion fakeout server had problems: %s" % response)

# This class runs the second kind of ingestion fakeout we try, which returns
class run_non_timeout_ingestion_test_server_thread(Thread):
    def __init__ (self, response, mode):
        Thread.__init__(self)
        self.response = response
        self.mode = mode
        self.setDaemon(True)

    def run(self):
        try:
        # Start the ingestion test server
        # Warning: This will hang until shut down!
            ConnectorHelpers.invoke_root_script( ["python", "ingestion_fakeout_server.py", self.mode] )
        except Exception, e:
            self.response += str(e)

# Run the real (non-timeout) fakeout service
def startup_non_timeout_fake_ingestion_service(response,mode="500"):
    """ Use our own script to simulate a busted ingestion system that returns 500 on every request """
    # Start nc, by invoking in a different thread
    thread = run_non_timeout_ingestion_test_server_thread(response,mode)
    thread.start()

    # Loop until we think server is listening
    while True:
        error_seen = True
        try:
            ConnectorHelpers.invoke_curl("http://localhost:7031/checkalive")
            error_seen = False
        except:
            pass
        if error_seen:
            time.sleep(1)
        else:
            break

    print "Non-timeout ingestion test server successfully started up"
    return thread

# Re-enable the ability of the appliance to receive data from localhost port 7031
def shutdown_non_timeout_fake_ingestion_service(thread=None, response=None):
    """ Send shutdown signal, and wait for system to exit """
    # Send a shutdown signal.  If this is nc listening, it should cause it to exit but not respond.  If nc has already exited, it should timeout and get an exception.
    # Otherwise, there should be a real response
    try:
        socket.setdefaulttimeout(10)
        ConnectorHelpers.invoke_curl("http://localhost:7031/shutdown")
        socket.setdefaulttimeout(1000000)
    except:
        # Must already have been shut down; continue
        pass

    socket.setdefaulttimeout(1000000)

    print "Non-timeout ingest system fakeout successfully shut down"
    # If there's a thread we know about, let it exit, and report any errors
    if thread:
        thread.join()
        if response:
            if len(response) > 0:
                raise Exception("Non-timeout ingestion fakeout server had problems: %s" % response)


# Stop health checkers
def stop_health_checker():
    ConnectorHelpers.invoke_root_script( [ "/etc/init.d/system_health_monitor", "stop" ] )
    # Health monitor doesn't really stop in synch with the above, so wait a while to be sure
    time.sleep(60)

# Start health checkers
def start_health_checker():
    ConnectorHelpers.invoke_root_script( [ "/etc/init.d/system_health_monitor", "start" ] )


# Edit file system repository connection via the UI (for BPA spinner test)
def resave_filesystem_repository_connection_ui( username, password, connection_name ):
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
    link = window.find_link("Edit "+connection_name)
    link.click( )

    # Find the "save" button
    window = vb.find_window("")
    link = window.find_button("Save this connection").click();

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Edited connection doesn't match")

# Crawl user credentials
username = "testingest"
password = "testingest"

# A document template we can use to verify that that works
document_template_text = '<template>\n' + \
        '<filter tagger_name="geo">\n' + \
        '<end_regex>-{10}</end_regex>\n' + \
        '</filter>\n' + \
        '</template>\n'

def count_java_heap_dumps( ):
    """ Count the number of heap dumps in the /common/metacarta/java-heap-dumps directory """
    results = ConnectorHelpers.invoke_root_script( [ "ls","-1","/common/metacarta/java-heap-dumps" ] )
    return len(results.splitlines())
    
def start_database( ):
    """ Start the database """
    ConnectorHelpers.invoke_root_script(["/etc/init.d/postgresql-8.3", "start"])
    time.sleep(15)

def stop_database( ):
    """ Stop the database """
    ConnectorHelpers.invoke_root_script(["/etc/init.d/postgresql-8.3", "stop"])

def system_health_check( ):
    """ Return True if the authority status comes out as a one line error, or
        False if it comes out as "skipping", or an exception if anything else.
    """
    text = ConnectorHelpers.invoke_root_script(["/usr/bin/check_system_health"], allow_errors=True)
    for line in text.splitlines():
        if line.find("Authority check already in progress") != -1:
            return False
        if line.find("Exception checking on authorities") != -1:
            if line.find("[Error getting connection]") != -1:
                return True
    raise Exception("Expected check_system_health to return a single-line status for authority checks, instead saw %s" % text)

signatures = [ "Schema upgrade in progress",
        "Schema incorrect for table",
        "Extra field for table",
        "Field definition incorrect for table",
        "Indexes incorrect for table",
        "Index definition incorrect for table",
        "Unexpected index definition for table",
        "Index definition for table" ]

def gather_schema_errors( ):
    """ Return a list of errors that seem to be schema-related """
    rval = []
    text = ConnectorHelpers.invoke_root_script(["/usr/bin/check_system_health"], allow_errors=True)
    for line in text.splitlines():
        # Look for the pertinent signatures in this line
        for signature in signatures:
            if line.find(signature) != -1:
                rval += [ line ]
                break
    return rval

# Create a outofmemory repository connection via the UI
def define_outofmemory_repository_connection_ui( username, password, connection_name, connection_description,
                                                                        throttles=None,
                                                                        max_connections=None,
                                                                        failure_mode=None):
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
    connectortypefield.select_value( "com.metacarta.crawler.connectors.outofmemory.OutOfMemoryConnector" )
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

    # "Failure Mode" tab
    link = window.find_link("Failure Mode tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    if failure_mode != None:
        form.find_selectbox("failuremode").select_value(failure_mode)

    # Now, save this page
    save_button = window.find_button("Save this connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")


# Define a standard job using the UI
def define_outofmemory_job_ui( username,
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
    # We should be able to use the filesystem connector job creation UI here
    return ConnectorHelpers.define_filesystem_job_ui(username,password,job_name,connection_name,
        startpoints_and_matches,collection_name=collection_name,document_template=document_template,
        hop_filters=hop_filters,hop_mode=hop_mode,type=type,startmethod=startmethod,recrawlinterval=recrawlinterval)

def preclean( print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    # Restore ingestion system
    try:
        shutdown_fake_ingestion_service()
    except Exception, e:
        if print_errors:
            print "Error restoring ingestion system"
            print e

    try:
        shutdown_non_timeout_fake_ingestion_service()
    except Exception, e:
        if print_errors:
            print "Error restoring ingestion system"
            print e

    try:
        ConnectorHelpers.start_leafblower()
    except Exception, e:
        if print_errors:
            print "Error starting leafblower"
            print e

    try:
        start_health_checker()
    except Exception, e:
        if print_errors:
            print "Error starting health checker"
            print e

    # Set clock back to actual time, if needed
    try:
        ConnectorHelpers.restore_clock()
    except Exception, e:
        if print_errors:
            print "Error restoring clock"
            print e

    # Start database if it is stopped
    try:
        start_database( )
    except Exception, e:
        if print_errors:
            print "Error starting database"
            print e

    # Restore schema if it has been altered
    print "Restoring schema."
    db = pg.DB( "metacarta", "localhost", 5432, None, None, "metacarta", "atracatem" )
    try:
        # First, get hold of the column definitions for intrinsiclink
        schema_query = "SELECT pg_attribute.attname AS field_col," + \
                "CASE pg_type.typname WHEN 'int2' THEN 'smallint' WHEN 'int4' THEN 'int'" + \
                        " WHEN 'int8' THEN 'bigint' WHEN 'varchar' THEN 'varchar(' || pg_attribute.atttypmod-4 || ')'" + \
                        " WHEN 'float8' THEN 'double'" + \
                        " WHEN 'text' THEN 'longtext'" + \
                        " WHEN 'bpchar' THEN 'char(' || pg_attribute.atttypmod-4 || ')'" + \
                        " ELSE pg_type.typname END AS type_col," + \
                "CASE WHEN pg_attribute.attnotnull THEN 'no' ELSE 'yes' END AS null_col," + \
                "CASE pg_type.typname WHEN 'varchar' THEN substring(pg_attrdef.adsrc from '^(.*).*$') ELSE pg_attrdef.adsrc END AS Default " + \
                "FROM pg_class INNER JOIN pg_attribute ON (pg_class.oid=pg_attribute.attrelid) INNER JOIN pg_type ON (pg_attribute.atttypid=pg_type.oid) " + \
                        "LEFT JOIN pg_attrdef ON (pg_class.oid=pg_attrdef.adrelid AND pg_attribute.attnum=pg_attrdef.adnum) " + \
                "WHERE pg_class.relname='%s' AND pg_attribute.attnum>=1 AND NOT pg_attribute.attisdropped " + \
                "ORDER BY pg_attribute.attnum"
        schema_results = db.query(schema_query % "intrinsiclink").dictresult()
        seen_isnew = False
        for row in schema_results:
            field_name = row["field_col"]
            if field_name == "isnew":
                seen_isnew = True
            elif field_name == "wasnew":
                # Delete this column!
                db.query("ALTER TABLE intrinsiclink DROP COLUMN wasnew")
        if seen_isnew == False:
            # Create isnew column
            db.query("ALTER TABLE intrinsiclink ADD COLUMN isnew CHAR(1) NULL")

        index_query = "SELECT pg_catalog.pg_get_indexdef(i.indexrelid, 0, true) AS indexdef FROM pg_catalog.pg_class c, pg_catalog.pg_class c2, pg_catalog.pg_index i " + \
                "WHERE c.relname = '%s' AND c.oid = i.indrelid AND i.indexrelid = c2.oid"
        index_results = db.query(index_query % "intrinsiclink").dictresult()
        seen_dropindex = False
        for definition in index_results:
            indexdef = definition["indexdef"]
            if indexdef.find("(jobid, childidhash, isnew)") != -1:
                seen_dropindex = True
            elif indexdef.find("(isnew)") != -1 and indexdef.find("temporaryindex") != -1:
                # Drop this index
                db.query("DROP INDEX temporaryindex")
        if seen_dropindex == False:
            # Recreate missing index
            db.query("CREATE INDEX i123 ON intrinsiclink (jobid,childidhash,isnew)")
    finally:
        db.close()
    print "Done restoring schema"

    # Start agents if it is down
    try:
        ConnectorHelpers.start_agents()
    except Exception, e:
        if print_errors:
            print "Error starting agents service"
            print e

    try:
        ConnectorHelpers.reset_all()
    except Exception, e:
        if print_errors:
            print "Error resetting all jobs"
            print e

    # Remove saved crawl configuration files, if any
    for file in [ "test_crawl_1.conf", "test_crawl_2.conf", "test_crawl_3.conf" ]:
        try:
            os.unlink( file )
        except Exception, e:
            if print_errors:
                print "Error removing %s" % file
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
        sqatools.LicenseMakerClient.revoke_license()
    except Exception, e:
        if print_errors:
            print "Error revoking license"
            print e

    try:
        ConnectorHelpers.delete_crawler_user( username )
    except Exception, e:
        if print_errors:
            print "Error removing crawler user"
            print e

    try:
        ConnectorHelpers.teardown_connector_environment( )
    except Exception, e:
        if print_errors:
            print "Error cleaning up debs"
            print e

    try:
        # Since one of the tests deregisters the filesystem connector, reregister it here to be sure it exists.
        ConnectorHelpers.register_connector("com.metacarta.crawler.connectors.filesystem.FileConnector", "FilesystemConnector")
    except Exception, e:
        if print_errors:
            print "Error reregistering file system connector"
            print e

# Main
if __name__ == '__main__':

    print "Precleaning!"

    preclean( print_errors=False )

    print "Clearing metacarta logs"
    log_pos = ConnectorHelpers.get_metacarta_log_pos( )
    agents_log_pos = ConnectorHelpers.get_metacarta_log_pos( log_name="/var/log/metacarta/java-agents/agents.log" )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up file area."
    copy_folder("/root/testfiles","/root/crawlarea")
    copy_folder("/root/testfiles2","/root/crawlarea")

    ConnectorHelpers.create_crawler_user( username, password )

    # PHASE 0: Checking whether reset-crawler script seems to work
    
    print "Trying reset-crawler command..."
    
    ConnectorHelpers.invoke_root_script( [ "/usr/lib/metacarta/reset-crawler" ] )
    sqautils.wait_for_service("tomcat")
    saw_exception = False
    try:
        ConnectorHelpers.invoke_script( [ "/usr/lib/metacarta/reset-crawler" ] )
    except:
        saw_exception = True
    if saw_exception == False:
        raise Exception("Running /usr/lib/metacarta/reset-crawler as non-root should have failed but didn't!")

    # PHASE 0.1: See if the security on the database is OK
    try:
        ConnectorHelpers.invoke_root_script( [ "/usr/bin/psql", "--port", "5432", "-U", "metacarta", "-c", "\"SELECT * FROM jobs;\"" ], input="incorrect\n" )
        succeeded = True
    except:
        succeeded = False

    if succeeded:
        raise Exception("Was able to talk with psql on port 5432 with incorrect password!")


    print "Checking schema checker."

    # PHASE 0.2: Try out check_system_health after mucking with the schema
    ConnectorHelpers.shutdown_agents( )

    schema_errors = gather_schema_errors( )
    # Initially there should be no schema errors
    if len(schema_errors) != 0:
        raise Exception("Unexpected schema errors detected! %s" % schema_errors)

    # For all the schema alterations, be sure to do it on a table that is only used by metacarta-agents!
    # I've chosen intrinsiclink for this purpose.  Its normal schema is:
    #Column    |          Type          | Modifiers
    #--------------+------------------------+-----------
    #isnew        | character(1)           |
    #linktype     | character varying(255) |
    #childidhash  | character varying(40)  |
    #parentidhash | character varying(40)  | not null
    #jobid        | bigint                 | not null
    #Indexes:
    #"i1237996140680" UNIQUE, btree (jobid, linktype, parentidhash, childidhash)
    #"i1237996140678" btree (jobid, childidhash, isnew)
    #"i1237996140679" btree (jobid, parentidhash)
    #Foreign-key constraints:
    #"intrinsiclink_jobid_fkey" FOREIGN KEY (jobid) REFERENCES jobs(id) ON DELETE RESTRICT
    # It *should* be empty, after a successful preclean

    db = pg.DB( "metacarta", "localhost", 5432, None, None, "metacarta", "atracatem" )
    try:

        # Alter table to have an additional unexpected column
        db.query("ALTER TABLE intrinsiclink ADD COLUMN foobar VARCHAR(20) NOT NULL")
        schema_errors = gather_schema_errors( )
        if len(schema_errors) != 1:
            raise Exception("After adding a column, expected 1 schema error, instead saw %d: %s" % (len(schema_errors),str(schema_errors)))
        db.query("ALTER TABLE intrinsiclink DROP COLUMN foobar")

        # Alter table to have a column substitution
        db.query("ALTER TABLE intrinsiclink ADD COLUMN wasnew CHAR(1) NULL")
        db.query("ALTER TABLE intrinsiclink DROP COLUMN isnew")
        # Not only does this cause the loss of a column, but it also causes the loss of an index.  So we see 2 errors...
        schema_errors = gather_schema_errors( )
        if len(schema_errors) != 2:
            raise Exception("After substituting a column, expected 2 schema errors, instead saw %d: %s" % (len(schema_errors),str(schema_errors)))
        db.query("ALTER TABLE intrinsiclink ADD COLUMN isnew CHAR(1) NULL")
        db.query("ALTER TABLE intrinsiclink DROP COLUMN wasnew")

        # At this point, we will have still lost the index on the isnew column, so we can test an index delete here
        schema_errors = gather_schema_errors( )
        if len(schema_errors) != 1:
            raise Exception("After deleting an index, expected 1 schema error, instead saw %d: %s" % (len(schema_errors),str(schema_errors)))

        # Add an incorrect index
        db.query("CREATE INDEX temporaryindex ON intrinsiclink (isnew)")

        schema_errors = gather_schema_errors( )
        if len(schema_errors) != 1:
            raise Exception("After index substitution, expected 1 schema error, instead saw %d: %s" % (len(schema_errors),str(schema_errors)))

        # Add in the correct index again
        db.query("CREATE INDEX i123 ON intrinsiclink (jobid,childidhash,isnew)")

        # Still should get 1 schema error because we have one extra index
        schema_errors = gather_schema_errors( )
        if len(schema_errors) != 1:
            raise Exception("With addition index, expected 1 schema error, instead saw %d: %s" % (len(schema_errors),str(schema_errors)))

        # Drop the bad index
        db.query("DROP INDEX temporaryindex")

        # Now, schema test should be OK
        schema_errors = gather_schema_errors( )
        if len(schema_errors) != 0:
            raise Exception("It looks like the test is screwed up; schema test failed after restoration")

    finally:
        db.close()

    ConnectorHelpers.start_agents( )

    # Create a standard GTS output connection
    ConnectorHelpers.define_gts_outputconnection( )
    
    print "Dump and restore empty configuration"
    
    # Check what happens when we dump and restore an empty configuration (21045)
    job_list = ConnectorHelpers.list_jobs_api( )
    if len(job_list) != 0:
        raise Exception("Expecting zero jobs, instead found %d" % len(job_list))
    ConnectorHelpers.export_configuration( "test_crawl_1.conf" )
    # There should be no connector-related configuration to blow away at this point!
    #ConnectorHelpers.reset_all( )
    # Restore the configuration
    ConnectorHelpers.import_configuration( "test_crawl_1.conf" )
    # Check that there are still zero jobs
    job_list = ConnectorHelpers.list_jobs_api( )
    if len(job_list) != 0:
        raise Exception("Expecting zero jobs, instead found %d" % len(job_list))

    print "Dump and restore configuration that has lots of jobs, with funky characters too"

    ConnectorHelpers.define_filesystem_repository_connection_ui( username, password, "OneDocumentTest", "One Document Test" )
    job_name_list = {}
    job_count = 50
    for job_index in range(job_count):
        # I never intend to actually crawl this, so it can be utterly screwy and that's OK
        job_name = u"One Document Test Job \u00d8 %d" % job_index
        job_id = ConnectorHelpers.define_filesystem_job_ui( username,
                                   password,
                                   job_name,
                                   "OneDocumentTest",
                                   [ ( "/root/crawlarea", [ ( "include", "file", "f007.txt" ), ( "include", "directory", "*" ) ] ) ] )
        job_name_list[job_name] = job_name
    ConnectorHelpers.export_configuration( "test_crawl_3.conf" )
    job_list = ConnectorHelpers.list_jobs_api( )
    if len(job_list) != job_count:
        raise Exception("Expecting %d jobs, instead found %d" % (job_count,len(job_list)))
    # Blow away the config
    ConnectorHelpers.reset_all( )
    # Restore the configuration
    ConnectorHelpers.import_configuration( "test_crawl_3.conf" )
    # Check that there are still 50 jobs
    job_list = ConnectorHelpers.list_jobs_api( )
    if len(job_list) != job_count:
        raise Exception("Expecting %d jobs, instead found %d" % (job_count,len(job_list)))
    # Now, check to see that these jobs adhere to specifications
    for job_record in job_list:
        job_id = job_record["identifier"]
        job_name = job_record["description"]
        if not job_name_list.has_key(job_name):
            raise Exception(u"One of the restored jobs does not have a recognized name!  %s" % job_name)
        ConnectorHelpers.delete_job(job_id)
    # Test performance hack; get all the deletes started and then wait for the deletes to all complete.
    for job_record in job_list:
        job_id = job_record["identifier"]
        ConnectorHelpers.wait_job_deleted(job_id)
        
    ConnectorHelpers.delete_repositoryconnection("OneDocumentTest")
    
    print "Verify that the help link exists and seems correct."
    
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username, password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for help
    window = vb.find_window("")
    link = window.find_link("Help")
    if link == None:
        raise Exception("Could not find help link in UI navigation")
    if link.url != "/documentation/ConnectorGuide.pdf":
        raise Exception("The help link was wrong: Saw '%s'" % link.url)

    print "Run two jobs with identical documents at the same time, and make sure we can restart metacarta-agents during this process."
    
    ConnectorHelpers.define_filesystem_repository_connection_ui( username, password, "FileSystem", "FileSystem Connection",throttles=[("",None,"20000")] )

    # Define job
    job_id_1 = ConnectorHelpers.define_filesystem_job_ui( username,
                                   password,
                                   "Test job 1",
                                   "FileSystem",
                                   [ ( "/root/crawlarea", [ ( "include", "file", "*.txt" ), ( "include", "directory", "*" ) ] ) ] )
    job_id_2 = ConnectorHelpers.define_filesystem_job_ui( username,
                                   password,
                                   "Test job 2",
                                   "FileSystem",
                                   [ ( "/root/crawlarea", [ ( "include", "file", "*.txt" ), ( "include", "directory", "*" ) ] ) ] )

    # Run the job to completion
    ConnectorHelpers.start_job_ui( username, password, job_id_1 )
    ConnectorHelpers.start_job_ui( username, password, job_id_2 )

    # Immediately restart metacarta-agents.  If failure, agents won't actually ever come back up, so test will timeout.
    ConnectorHelpers.restart_agents()
    
    ConnectorHelpers.wait_job_complete( job_id_1 )
    ConnectorHelpers.wait_job_complete( job_id_2 )

    # Get job status.  Sometimes we get this far but the status is messed up (job is done but there's an active document still)
    result = ConnectorHelpers.list_job_statuses_api()
    if len(result) != 2:
        raise Exception("Expected two jobs, found %d" % len(result))
    if result[0]["status"] != "done":
        raise Exception("Expected job status to be 'done', instead found '%s'" % result[0]["status"])
    if result[1]["status"] != "done":
        raise Exception("Expected job status to be 'done', instead found '%s'" % result[1]["status"])
    if result[0]["outstanding"] != str(0):
        raise Exception("Expected active documents to be 0, instead found '%s'" % result[0]["outstanding"])
    if result[1]["outstanding"] != str(0):
        raise Exception("Expected active documents to be 0, instead found '%s'" % result[1]["outstanding"])
    
    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_check( [ "reference" ], None, [ "root/crawlarea/testfiles/f001.txt" ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ "root/crawlarea/testfiles/f002.txt" ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ "root/crawlarea/testfiles/f003.txt" ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ "root/crawlarea/testfiles/f004.txt" ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ "root/crawlarea/testfiles/f005.txt" ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ "root/crawlarea/testfiles/f006.txt" ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ "root/crawlarea/testfiles/f007.txt" ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ "root/crawlarea/testfiles/newfolder/f008.txt" ] )

    # Clean up both jobs simultaneously too.
    ConnectorHelpers.delete_job_ui( username, password, job_id_1 )
    ConnectorHelpers.delete_job_ui( username, password, job_id_2 )

    ConnectorHelpers.wait_job_deleted( job_id_1 )
    ConnectorHelpers.wait_job_deleted( job_id_2 )
    
    ConnectorHelpers.delete_repository_connection_ui( username, password, "FileSystem" )

    print "Force the crawler to run out of memory, and see that it shuts down."

    # Shutdown health checker first; otherwise we can get locks stuck, and this would be messy to clean up.
    stop_health_checker()
    # Wait long enough so we can be sure there are no outstanding connector-related health activities going on.
    time.sleep(30)
    
    old_heap_dumps = count_java_heap_dumps()
    
    define_outofmemory_repository_connection_ui( username, password, "OutOfMemoryTest", "Out of Memory Test" )
    job_id = define_outofmemory_job_ui( username,
                                   password,
                                   "Out of Memory Test Job",
                                   "OutOfMemoryTest",
                                   [ ( "/root/crawlarea", [ ( "include", "file", "*" ), ( "include", "directory", "*" ) ] ) ] )

    ConnectorHelpers.start_job_ui( username, password, job_id )
    # Give it some time to shut itself down
    time.sleep(60)
    # Verify that metacarta-agents is indeed gone
    if ConnectorHelpers.find_daemon_pid( ) != None:
        raise Exception("metacarta-agents should have aborted, but it's still running!")
    # Clean up locks enough so that we can't die trying to abort the job.
    ConnectorHelpers.shutdown_tomcat( )
    ConnectorHelpers.invoke_script(["/usr/lib/metacarta/core-lockclean"])
    # Now, abort the job.  This must happen before reset-crawler, because otherwise we might just run out of memory again.
    ConnectorHelpers.abort_job( job_id )
    # It stopped.  Now, we have to reset locks because the oom may have messed them up.  This will start services back up too.
    ConnectorHelpers.invoke_root_script(["/usr/lib/metacarta/reset-crawler"])
    # Start health checker
    start_health_checker()
    # The job should now abort properly
    ConnectorHelpers.wait_job_complete( job_id )

    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )
    ConnectorHelpers.delete_repository_connection_ui( username, password, "OutOfMemoryTest" )
    
    new_heap_dumps = count_java_heap_dumps()
    if new_heap_dumps != old_heap_dumps + 1:
        raise Exception("Expected there to be %d heap dumps, instead found %d" % (old_heap_dumps+1,new_heap_dumps))

    print "Check that a delayed seeding phase does not permit job to abort until done."

    define_outofmemory_repository_connection_ui( username, password, "OutOfMemoryTest", "Out of Memory Test", failure_mode="seedingdelay" )
    job_id = define_outofmemory_job_ui( username,
                                   password,
                                   "Out of Memory Test Job",
                                   "OutOfMemoryTest",
                                   [ ( "/root/crawlarea", [ ( "include", "file", "*" ), ( "include", "directory", "*" ) ] ) ] )

    ConnectorHelpers.start_job_ui( username, password, job_id )
    # When the above is clicked, the UI immediately gives feedback that the job is starting.  Unfortunately you cannot tell the difference in the UI between the "READYFORSTARTUP" state and the
    # "STARTINGUP" state.  Aborts function differently in each state.
    # To sidestep this issue, we wait for 20 seconds after we see the 'starting up' in the UI, in order to be pretty certain the job has entered the "STARTINGUP" state.
    # Once truly in the "STARTINGUP" state, the job is guaranteed to stay in that state for at least 2 minutes.
    while True:
        start_began_time = time.time()
        job_state = ConnectorHelpers.get_job_status_ui( username, password, job_id )
        if job_state == "Starting up":
            time.sleep(20)
            break
        if job_state == "Running":
            raise Exception("Test problem: saw 'running' state without seeing 'starting up' phase")
        time.sleep(10)

    # Now, abort the job
    ConnectorHelpers.abort_job( job_id )
    # The job should NOT abort right away!!  Indeed, we should see the job stay in the "aborting" state for about 120-30 seconds.  Since this is approximate, we'll wait
    # only 60 seconds before checking the job state; it'd better not stop aborting by then!
    time.sleep(60-(time.time()-start_began_time))
    job_state = ConnectorHelpers.get_job_status_ui( username, password, job_id )
    if job_state != "Aborting":
        raise Exception("Expected job to stay in the Aborting state for an extended period of time, when interrupted during startup phase")

    ConnectorHelpers.wait_job_complete( job_id )

    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )
    ConnectorHelpers.delete_repository_connection_ui( username, password, "OutOfMemoryTest" )

    print "Seeing whether broken pipe ingestion errors get handled correctly"
    
    # We need a file that's at least large enough to cause packet transmission without a flush.  The test documents are all too small by themselves - so build a big one....
    f_out = open("/root/crawlarea/bigfile.txt","w")
    try:
        for iteration in range(10000):
            f_in = open("/root/crawlarea/testfiles/f001.txt","r")
            try:
                for line in f_in.readlines():
                    f_out.write(line)
            finally:
                f_in.close()
    finally:
        f_out.close()

    define_outofmemory_repository_connection_ui( username, password, "OutOfMemoryTest", "Out of Memory Test", failure_mode="ingestiondelay" )
    job_id = define_outofmemory_job_ui( username,
                                   password,
                                   "Out of Memory Test Job",
                                   "OutOfMemoryTest",
                                   [ ( "/root/crawlarea", [ ( "include", "file", "*" ), ( "include", "directory", "*" ) ] ) ] )

    ConnectorHelpers.start_job_ui( username, password, job_id )
    # If the code is working properly, broken pipe errors will be treated as 400's.
    # If not, the job will retry documents indefinitely, and the test will fail for that reason.
    ConnectorHelpers.wait_job_complete( job_id )

    # Get rid of big file
    os.unlink("/root/crawlarea/bigfile.txt")

    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )
    ConnectorHelpers.delete_repository_connection_ui( username, password, "OutOfMemoryTest" )

    print "Ingest using broken ingestion system."

    # Set up an ingestion of exactly one document
    ConnectorHelpers.define_filesystem_repository_connection_ui( username, password, "OneDocumentTest", "One Document Test" )
    job_id = ConnectorHelpers.define_filesystem_job_ui( username,
                                   password,
                                   "One Document Test Job",
                                   "OneDocumentTest",
                                   [ ( "/root/crawlarea", [ ( "include", "file", "f007.txt" ), ( "include", "directory", "*" ) ] ) ] )

    stop_health_checker()
    ConnectorHelpers.stop_leafblower()
    # Set up dummy ingest listener
    response = ""
    this_thread = startup_fake_ingestion_service(response)

    print "Looking at debug info after nc-based fake ingestion service started"
    dump_debug()

    # Start the job.  The job would normally run for many hours, because we would need to wait for the retries to give up, so I'm going to abort it after some short period of time.
    ConnectorHelpers.start_job_ui( username, password, job_id )
    time.sleep(60)
    ConnectorHelpers.abort_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # The connector framework should have logged a -2 error for the first ingest activity!
    results = ConnectorHelpers.run_simple_history_report_api( "OneDocumentTest", [ "document ingest (GTS)" ] )
    # There should have been one or more ingestion attempts
    if len(results) == 0:
        raise Exception("No ingestion attempts were reported!  Expected at least one.")

    # Check to be sure that at least one received a -2 error
    saw_proper_error = False
    for result in results:
        if int(result["result_code"]) == -2:
            saw_proper_error = True
            break

    if not saw_proper_error:
        raise Exception("Did not see expected -2 error in ingest history results")

    shutdown_fake_ingestion_service(this_thread,response)

    print "Looking at debug info after nc-based fake ingestion service stopped"
    dump_debug()

    # Next, try an ingestion service that just returns 500 errors
    response = ""
    this_thread = startup_non_timeout_fake_ingestion_service(response)

    print "Looking at debug info after homegrown fake ingestion service started"
    dump_debug()

    # Start the job, wait for a time, then abort it.  This should run for a long time, and generate a few warnings in the log, which we'll check for later.
    ConnectorHelpers.start_job_ui( username, password, job_id )
    time.sleep(60)
    ConnectorHelpers.abort_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    shutdown_non_timeout_fake_ingestion_service(this_thread,response)

    print "Looking at debug info after homegrown fake ingestion service stopped"
    dump_debug()

    # Check for at least one error in the log of form:
    # "Error 500 from ingestion request; ingestion will be retried again later"
    lines = ConnectorHelpers.read_metacarta_log( "Error 500 from ingestion request; ingestion will be retried again later", agents_log_pos, log_name="/var/log/metacarta/java-agents/agents.log" )
    if len(lines) == 0:
        raise Exception("Did not see expected ingestion request retry message in log!")

    # Delete the job, without anything listening on 7031.  This will cause a number of deleted documents to be queued.
    ConnectorHelpers.delete_job( job_id )
    for iteration in range(5):
        # Wait a little while so they *do* get queued,
        time.sleep(10)
        # Now, stop metacarta-agents.  This should replicate bug 29943.
        ConnectorHelpers.shutdown_agents()
        # Look for inconsistencies in the database
        # We should never see rows in intrinsiclink that refer to non-existent jobs
        db = pg.DB( "metacarta", "localhost", 5432, None, None, "metacarta", "atracatem" )
        try:
            bad_results = db.query("select count(*) as mycount from intrinsiclink t0 where not exists(select 'x' from jobs t1 where t0.jobid=t1.id)").dictresult()
            found_count = None
            for result in bad_results:
                found_count = int(result["mycount"])
            if found_count != 0:
                raise Exception("Detected schema inconsistency!  Job is gone, but %d rows in intrinsiclink table refer to it." % found_count)
        finally:
            db.close()

        # No schema inconsistency: restart agents
        ConnectorHelpers.start_agents()
        # Startup time is at least 45 seconds, because the secure random number generator is invoked during startup if httpposter is invoked.
        time.sleep(45)


    # No problems detected: restart leafblower, and let job finish deleting
    ConnectorHelpers.start_leafblower()
    start_health_checker()

    ConnectorHelpers.wait_job_deleted( job_id )

    # Check once again that there are no dangling intrinsiclink rows!!
    db = pg.DB( "metacarta", "localhost", 5432, None, None, "metacarta", "atracatem" )
    try:
        bad_results = db.query("select count(*) as mycount from intrinsiclink t0 where not exists(select 'x' from jobs t1 where t0.jobid=t1.id)").dictresult()
        found_count = None
        for result in bad_results:
            found_count = int(result["mycount"])
        if found_count != 0:
            raise Exception("Detected schema inconsistency!  Job is gone, but %d rows in intrinsiclink table refer to it." % found_count)
    finally:
        db.close()

    ConnectorHelpers.delete_repository_connection_ui( username, password, "OneDocumentTest" )

    print "Non-typical connection name test."

    # PHASE 0.9: Try creating and removing some odd connection names.  This is not definitive, as we must rely on the correctness of the virtual browser to get
    # the correct results...

    # ConnectorHelpers.define_repositoryconnection( "FileSystem",
    #                            "FileSystem Connection",
    #                            "com.metacarta.crawler.connectors.filesystem.FileConnector" )
    # Do via the UI, with one stupid throttle (to test that part of the UI)
    ConnectorHelpers.define_filesystem_repository_connection_ui( username, password, "Test%2BTest", "Odd Connection" )
    ConnectorHelpers.delete_repository_connection_ui( username, password, "Test%2BTest" )

    # PHASE 1: Ingestion

    print "Ingestion Test."

    # Define repository connection
    # ConnectorHelpers.define_repositoryconnection( "FileSystem",
    #                            "FileSystem Connection",
    #                            "com.metacarta.crawler.connectors.filesystem.FileConnector" )
    # Do via the UI, with one stupid throttle (to test that part of the UI)
    ConnectorHelpers.define_filesystem_repository_connection_ui( username, password, "FileSystem", "FileSystem Connection",throttles=[("",None,"20000")] )

    # Spinner test to make sure we aren't leaking file descriptors from tomcat for BPA callout.
    # 3 handles will be leaked each iteration, if broken, out of a max number of 1024.
    for counter in range(1,1024):
        resave_filesystem_repository_connection_ui( username, password, "FileSystem" )

    # Define job
    # doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="/root/crawlarea"><include match="*.txt" type="file"/><include match="*" type="directory"/></startpoint></specification>'
    # job_id = ConnectorHelpers.define_job( "Test job",
    #                     "FileSystem",
    #                     doc_spec_xml )
    job_id = ConnectorHelpers.define_filesystem_job_ui( username,
                                   password,
                                   "Test job",
                                   "FileSystem",
                                   [ ( "/root/crawlarea", [ ( "include", "file", "*.txt" ), ( "include", "directory", "*" ) ] ) ] )

    # Run the job to completion
    # ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.start_job_ui( username, password, job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_check( [ "reference" ], None, [ "root/crawlarea/testfiles/f001.txt" ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ "root/crawlarea/testfiles/f002.txt" ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ "root/crawlarea/testfiles/f003.txt" ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ "root/crawlarea/testfiles/f004.txt" ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ "root/crawlarea/testfiles/f005.txt" ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ "root/crawlarea/testfiles/f006.txt" ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ "root/crawlarea/testfiles/f007.txt" ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ "root/crawlarea/testfiles/newfolder/f008.txt" ] )

    # Check job definition and job status via API
    result = ConnectorHelpers.list_jobs_api()
    if len(result) != 1:
        raise Exception("Expected one job, found %d" % len(result))
    if result[0]["identifier"] != job_id:
        raise Exception("Expected job identifier to be %s, instead found %s" % (job_id,result[0]["identifier"]))
    if result[0]["description"] != "Test job":
        raise Exception("Expected job description to be 'Test job', instead found '%s'" % result[0]["description"])
    if result[0]["connection"] != "FileSystem":
        raise Exception("Expected job connection to be 'FileSystem', instead found '%s'" % result[0]["connection"])

    result = ConnectorHelpers.list_job_statuses_api()
    if len(result) != 1:
        raise Exception("Expected one job, found %d" % len(result))
    if result[0]["identifier"] != job_id:
        raise Exception("Expected job identifier to be %s, instead found %s" % (job_id,result[0]["identifier"]))
    if result[0]["description"] != "Test job":
        raise Exception("Expected job description to be 'Test job', instead found '%s'" % result[0]["description"])
    if result[0]["status"] != "done":
        raise Exception("Expected job status to be 'done', instead found '%s'" % result[0]["status"])

    # Success: done
    print "Done ingestion test."


    # PHASE 2: Document Change Detection

    print "Document Change Test."
    o = open( "/root/crawlarea/testfiles/f002.txt", "w" )
    o.write("Now this document is at 50N 75E, and the keyword is platypus")
    o.close()
    o = open( "/root/crawlarea/testfiles/f004.txt", "w" )
    o.write("No longer about drinking establishments at 23N 15W")
    o.close()

    # Added 7/21/2008: Set clock forward 18 months, and wait long enough so that all current Thread.sleep()'s (if present)
    # will wake up, and go back to sleep.
    ConnectorHelpers.set_clock_forward()
    time.sleep(60)
    # Restore the clock, because we should not be ACTIVELY doing anything
    # with the daemon while the clock is wrong.
    ConnectorHelpers.restore_clock()

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Look for state of index being right
    ConnectorHelpers.search_check( [ "reference" ], None, [ "root/crawlarea/testfiles/f001.txt" ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ "root/crawlarea/testfiles/f003.txt" ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ "root/crawlarea/testfiles/f005.txt" ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ "root/crawlarea/testfiles/f006.txt" ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ "root/crawlarea/testfiles/f007.txt" ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ "root/crawlarea/testfiles/newfolder/f008.txt" ] )
    ConnectorHelpers.search_check( [ "platypus" ], None, [ "root/crawlarea/testfiles/f002.txt" ] )
    ConnectorHelpers.search_check( [ "establishments" ], None, [ "root/crawlarea/testfiles/f004.txt" ] )

    print "Done Document Change Test."

    # PHASE 3: Document Delete Detection

    print "Document Delete Test."
    os.remove( "/root/crawlarea/testfiles/f003.txt" )
    os.remove( "/root/crawlarea/testfiles/f005.txt" )
    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ ] )
    print "Done Document Delete Test."

    # PHASE 4: Document Addition Detection

    print "Document Add Test."
    o = open( "/root/crawlarea/testfiles/f009.txt", "w" )
    o.write("Now this document is at 50N 75E, and the keyword is albemarle")
    o.close()
    o = open( "/root/crawlarea/testfiles/f010.txt", "w" )
    o.write("No longer about golfcarts at 23N 15W")
    o.close()
    o = open( "/root/crawlarea/testfiles/f011.txt", "w" )
    o.write("------------\n")
    o.write("No sodapop should show up for 12N 72W")
    o.close()

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    ConnectorHelpers.search_check( [ "albemarle" ], None, [ "root/crawlarea/testfiles/f009.txt" ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [ "root/crawlarea/testfiles/f010.txt" ] )
    ConnectorHelpers.search_check( [ "sodapop" ], None, [ "root/crawlarea/testfiles/f011.txt" ] )
    print "Done Document Add Test."

    # PHASE 4.5: Run all the reports via the API and check the results for being sensible
    simple_result = ConnectorHelpers.run_simple_history_report_api( "FileSystem",
        [ "job start", "job end" ] )
    if len(simple_result) != 8:
        raise Exception("Expected 8 job start/job end events, found %d" % len(simple_result))
    max_bandwidth_result = ConnectorHelpers.run_max_bandwidth_history_report_api( "FileSystem",
        [ "document ingest (GTS)" ], entity_bin_regexp="()" )
    if len(max_bandwidth_result) != 1:
        raise Exception("Expected 1 result row from bandwidth report, found %d" % len(max_bandwidth_result))
    max_activity_result = ConnectorHelpers.run_max_activity_history_report_api( "FileSystem",
        [ "document ingest (GTS)" ], entity_bin_regexp="()" )
    if len(max_activity_result) != 1:
        raise Exception("Expected 1 result row from activity report, found %d" % len(max_activity_result))
    result_report = ConnectorHelpers.run_result_histogram_history_report_api( "FileSystem",
        [ "document ingest (GTS)" ], entity_bin_regexp="()", result_bin_regexp="()" )
    if len(result_report) != 1:
        raise Exception("Expected 1 result row from result histogram report, found %d" % len(result_report))
    document_status = ConnectorHelpers.run_document_status_api( "FileSystem",
        [ job_id ] )
    expected_queue_length = 15
    if len(document_status) != expected_queue_length:
        raise Exception("Expected %d documents in queue, found %d" % (expected_queue_length,len(document_status)))
    queue_status = ConnectorHelpers.run_queue_status_api( "FileSystem",
        [ job_id ], bucket_regexp="()" )
    if len(queue_status) != 1:
        raise Exception("Expected 1 result row from queue status report, found %d" % len(queue_status))
    if int(queue_status[0]["inactive_count"]) != expected_queue_length:
        raise Exception("Expected %d inactive queued documents, found %d" % (expected_queue_length,int(queue_status[0]["inactive_count"])))

    # PHASE 5: Delete Job

    print "Job Delete Test."
    # ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.delete_job_ui( username, password, job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"
    # Make sure the documents all went away
    ConnectorHelpers.search_check( [ "reference" ], None, [] )
    ConnectorHelpers.search_check( [ "good" ], None, [] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [] )
    ConnectorHelpers.search_check( [ "pub" ], None, [] )
    ConnectorHelpers.search_check( [ "city" ], None, [] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [] )
    ConnectorHelpers.search_check( [ "albemarle" ], None, [] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [] )
    ConnectorHelpers.search_check( [ "sodapop" ], None, [] )
    print "Done Job Delete Test."

    # PHASE 6: Scheduled Ingestion
    print "Scheduled Ingestion Test."
    # Define job again
    # doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="/root/crawlarea"><include match="*.txt" type="file"/><include match="*" type="directory"/></startpoint></specification>'
    job_id = ConnectorHelpers.define_filesystem_job_ui( username,
                                                        password,
                                                        "Test job",
                                                        "FileSystem",
                                                        [ ( "/root/crawlarea", [ ( "include", "file", "*.txt" ), ( "include", "directory", "*" ) ] ) ] )

    # Set the schedule.  One minute is too short; might miss the window.
    # We need to be sure we hit the window.  We can estimate sanity without having the test fail in obscure ways by calculating the time interval ourselves.
    min_scheduled_time_begin = time.time() + 120.0
    # For the max time, give an additional minute's slop, because various CF threads don't run all the time
    max_scheduled_time_begin = min_scheduled_time_begin + 60.0 + 60.0
    # Sets the time for the test to run to the current time, plus 2 minutes.  Since the current time might be (say) 3 m 59 s, and the execute time would then be
    # 6 m, the actual interval may well be as little as 2 minutes.
    ConnectorHelpers.set_scheduled_time( job_id, 3 )

    # Dump the configuration
    ConnectorHelpers.export_configuration( "test_crawl_2.conf" )
    # Blow away all connector-related stuff
    ConnectorHelpers.reset_all( )
    # Restore the configuration
    ConnectorHelpers.import_configuration( "test_crawl_2.conf" )
    # Check to be sure we didn't miss the window!
    if time.time() >= min_scheduled_time_begin:
        raise Exception("Test invalid: Test setup exceeded limits, so scheduling won't fire")
        
    # Everything should be back and work, as if we hadn't blown everything away and restored it.  The only thing we must do is find the job_id, since it has changed.
    job_id = ConnectorHelpers.find_job_by_name_ui( username, password, "Test job", "FileSystem" )

    # Sleep until we are sure it should have fired
    sleep_amt = max_scheduled_time_begin - time.time()
    if sleep_amt > 0:
        time.sleep(sleep_amt)

    # Wait for job inactive
    ConnectorHelpers.wait_job_complete( job_id )
    # Make sure we can find our stuff
    ConnectorHelpers.wait_for_ingest( )
    # Look for state of index being right
    ConnectorHelpers.search_check( [ "reference" ], None, [ "root/crawlarea/testfiles/f001.txt" ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ "root/crawlarea/testfiles/f006.txt" ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ "root/crawlarea/testfiles/f007.txt" ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ "root/crawlarea/testfiles/newfolder/f008.txt" ] )
    ConnectorHelpers.search_check( [ "platypus" ], None, [ "root/crawlarea/testfiles/f002.txt" ] )
    ConnectorHelpers.search_check( [ "establishments" ], None, [ "root/crawlarea/testfiles/f004.txt" ] )
    ConnectorHelpers.search_check( [ "albemarle" ], None, [ "root/crawlarea/testfiles/f009.txt" ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [ "root/crawlarea/testfiles/f010.txt" ] )

    # Try out the API call for getting the schedule
    result = ConnectorHelpers.get_job_schedule_api( job_id )
    if len(result) != 1:
        raise Exception("Expected one schedule record, instead found %d" % len(result))
    result[0]["daysofweek"]
    result[0]["years"]
    result[0]["months"]
    result[0]["days"]
    result[0]["hours"]
    result[0]["minutes"]
    result[0]["timezone"]
    result[0]["duration"]

    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    # PHASE 7: Time Window Ingestion
    print "Time Window Ingestion Test."
    # This test requires enough documents to keep crawler busy for >1 minute,
    # which I don't have yet - so skip for now. MHL
    print "Done Time Window Ingestion Test."

    # PHASE 7.1: Check that pathological situations in scheduler don't mess us up.

    #stop metacarta-agents
    ConnectorHelpers.shutdown_agents()
    
    #create the job
    #doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="/root/crawlarea"><include match="*.txt" type="file"/><include match="*" type="directory"/></startpoint></specification>'
    job_id = ConnectorHelpers.define_filesystem_job_ui( username,
                                          password,
                                          "Test job",
                                          "FileSystem",
                                          [ ( "/root/crawlarea", [ ( "include", "file", "*.txt" ), ( "include", "directory", "*" ) ] ) ] )

    #set up the job schedule: run in Jan,Feb,Mar,April,May,June,July,Aug,Sept,Oct,Nov,Dec, but don't set any other info
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.AddScheduledTime", argument_list=[ job_id,
                            "",
                            "",
                            "",
                            "january,february,march,april,may,june,july,august,september,october,november,december",
                            "",
                            "",
                            "" ] )
    
    #screw with the 'last job run' timestamp in the database, to set it to a
    #  known magic value, e.g 12:00AM June 29, 2009 GMT: 1246233600000
    # This time is carefully picked because it must be greater than the 28th of the month, and yet after we've advanced to midnight
    # we must still be in the same month; the next advance will thus be to go by days towards the first of the next month, which is
    # what would fail.
    db = pg.DB( "metacarta", "localhost", 5432, None, None, "metacarta", "atracatem" )
    try:
        db.query("update jobs set lastchecktime=1246233600000 where id=%s" % job_id)
    finally:
        db.close()

    #start metacarta-agents
    ConnectorHelpers.start_agents()
    
    #wait until we're sure scheduler has had a chance to look at the record in question
    time.sleep(30)
    
    #try to shut down metacarta-agents; it should succeed if fixed; otherwise it will time out.
    ConnectorHelpers.shutdown_agents()
   
    # Now, clean up job
    ConnectorHelpers.start_agents()
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )
   
   
    # PHASE 8: Crawl from seeds
    print "Crawl From Seeds Test."
    # define sample job with two sets of seeds
    #doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="/root/crawlarea"><include match="*.txt" type="file"/><include match="*" type="directory"/></startpoint><startpoint path="/root/crawlarea2"><include match="*.txt" type="file"/><include match="*" type="directory"/></startpoint></specification>'
    job_id = ConnectorHelpers.define_filesystem_job_ui( username, password, "Test job",
                             "FileSystem",
                             [ ( "/root/crawlarea", [ ( "include", "file", "*.txt" ), ( "include", "directory", "*" ) ] ), ( "/root/crawlarea2", [ ( "include", "file", "*.txt" ), ( "include", "directory", "*" ) ] ) ] )

    # Now, crawl
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )
    # Verify correctness of ingestion
    ConnectorHelpers.search_check( [ "reference" ], None, [ "root/crawlarea/testfiles/f001.txt" ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ "root/crawlarea/testfiles/f006.txt" ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ "root/crawlarea/testfiles/f007.txt" ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ "root/crawlarea/testfiles/newfolder/f008.txt" ] )
    ConnectorHelpers.search_check( [ "platypus" ], None, [ "root/crawlarea/testfiles/f002.txt" ] )
    ConnectorHelpers.search_check( [ "establishments" ], None, [ "root/crawlarea/testfiles/f004.txt" ] )
    ConnectorHelpers.search_check( [ "albemarle" ], None, [ "root/crawlarea/testfiles/f009.txt" ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [ "root/crawlarea/testfiles/f010.txt" ] )
    ConnectorHelpers.search_check( [ "humid" ], None, [ "root/crawlarea/testfiles2/f002.txt" ] )
    ConnectorHelpers.search_check( [ "document" ], None, [ "root/crawlarea/testfiles2/f001.txt", "root/crawlarea/testfiles/f002.txt", "root/crawlarea/testfiles/f009.txt"  ] )

    # Modify document specification to remove testfiles2 area
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="/root/crawlarea/testfiles"><include match="*.txt" type="file"/><include match="*" type="directory"/></startpoint></specification>'
    ConnectorHelpers.change_job_doc_spec( job_id, doc_spec_xml )
    # Rerun
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )
    # Verify correctness of ingestion
    ConnectorHelpers.search_check( [ "reference" ], None, [ "root/crawlarea/testfiles/f001.txt" ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ "root/crawlarea/testfiles/f006.txt" ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ "root/crawlarea/testfiles/f007.txt" ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ "root/crawlarea/testfiles/newfolder/f008.txt" ] )
    ConnectorHelpers.search_check( [ "platypus" ], None, [ "root/crawlarea/testfiles/f002.txt" ] )
    ConnectorHelpers.search_check( [ "establishments" ], None, [ "root/crawlarea/testfiles/f004.txt" ] )
    ConnectorHelpers.search_check( [ "albemarle" ], None, [ "root/crawlarea/testfiles/f009.txt" ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [ "root/crawlarea/testfiles/f010.txt" ] )
    ConnectorHelpers.search_check( [ "humid" ], None, [] )
    ConnectorHelpers.search_check( [ "document" ], None, [ "root/crawlarea/testfiles/f002.txt", "root/crawlarea/testfiles/f009.txt" ] )

    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )
    print "Done Crawl From Seeds Test."

    # PHASE 9: Used to be "crawl everything crawled before", but it's no longer meaningful, since the
    # it's the connector that determines how the crawler behaves now.

    # PHASE 10: Adaptive crawling test
    print "Adaptive Crawl Test."
    # define sample job with two sets of seeds
    #doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="/root/crawlarea"><include match="*.txt" type="file"/><include match="*" type="directory"/></startpoint><startpoint path="/root/crawlarea2"><include match="*.txt" type="file"/><include match="*" type="directory"/></startpoint></specification>'
    job_id = ConnectorHelpers.define_filesystem_job_ui( username, password, "Test job",
                             "FileSystem",
                             [ ( "/root/crawlarea", [ ( "include", "file", "*.txt" ), ( "include", "directory", "*" ) ] ), ( "/root/crawlarea2", [ ( "include", "file", "*.txt" ), ( "include", "directory", "*" ) ] ) ],
                             type="continuous",
                             recrawlinterval=2 )

    # Now, crawl
    ConnectorHelpers.start_job( job_id )
    # Job will not end, so we simply need to wait one minute.
    time.sleep(1 * 60)
    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )
    # Verify correctness of ingestion
    ConnectorHelpers.search_check( [ "reference" ], None, [ "root/crawlarea/testfiles/f001.txt" ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ "root/crawlarea/testfiles/f006.txt" ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ "root/crawlarea/testfiles/f007.txt" ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ "root/crawlarea/testfiles/newfolder/f008.txt" ] )
    ConnectorHelpers.search_check( [ "platypus" ], None, [ "root/crawlarea/testfiles/f002.txt" ] )
    ConnectorHelpers.search_check( [ "establishments" ], None, [ "root/crawlarea/testfiles/f004.txt" ] )
    ConnectorHelpers.search_check( [ "albemarle" ], None, [ "root/crawlarea/testfiles/f009.txt" ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [ "root/crawlarea/testfiles/f010.txt" ] )
    ConnectorHelpers.search_check( [ "humid" ], None, [ "root/crawlarea/testfiles2/f002.txt" ] )
    ConnectorHelpers.search_check( [ "document" ], None, [ "root/crawlarea/testfiles2/f001.txt", "root/crawlarea/testfiles/f002.txt", "root/crawlarea/testfiles/f009.txt" ] )

    # Now, change a document and see if the recrawl happens
    o = open( "/root/crawlarea/testfiles/f002.txt", "w" )
    o.write("Now this document is at 49N 75E, and the keyword is castle")
    o.close()
    # Simply wait to see if the reingest occurs (it should after about 1 min)
    time.sleep(2 * 60)
    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )
    # Verify correctness of ingestion
    ConnectorHelpers.search_check( [ "reference" ], None, [ "root/crawlarea/testfiles/f001.txt" ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ "root/crawlarea/testfiles/f006.txt" ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ "root/crawlarea/testfiles/f007.txt" ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ "root/crawlarea/testfiles/newfolder/f008.txt" ] )
    ConnectorHelpers.search_check( [ "platypus" ], None, [] )
    ConnectorHelpers.search_check( [ "castle" ], None, [ "root/crawlarea/testfiles/f002.txt" ] )
    ConnectorHelpers.search_check( [ "establishments" ], None, [ "root/crawlarea/testfiles/f004.txt" ] )
    ConnectorHelpers.search_check( [ "albemarle" ], None, [ "root/crawlarea/testfiles/f009.txt" ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [ "root/crawlarea/testfiles/f010.txt" ] )
    ConnectorHelpers.search_check( [ "humid" ], None, [ "root/crawlarea/testfiles2/f002.txt" ] )
    ConnectorHelpers.search_check( [ "document" ], None, [ "root/crawlarea/testfiles2/f001.txt", "root/crawlarea/testfiles/f002.txt", "root/crawlarea/testfiles/f009.txt" ] )

    ConnectorHelpers.abort_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    # PHASE 11: Ingest into collection test
    print "Collection Ingestion Test (also with unregistered connector)."

    job_id = ConnectorHelpers.define_filesystem_job_ui( username,
                                   password,
                                   "Test job",
                                   "FileSystem",
                                   [ ( "/root/crawlarea", [ ( "include", "file", "*.txt" ), ( "include", "directory", "*" ) ] ) ],
                                   collection_name="Zena",
                                   document_template=document_template_text )

    # Unregister the file system connector, and restart the services.  This should not prevent us from starting the job!  But when the job starts it should do
    # nothing until we reregister the connector.
    ConnectorHelpers.deregister_connector("com.metacarta.crawler.connectors.filesystem.FileConnector")
    # Recycle the services to be sure there's no already-created handles around
    ConnectorHelpers.restart_tomcat()
    ConnectorHelpers.restart_agents()
    time.sleep(60)

    # Run the job to completion
    # ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.start_job_ui( username, password, job_id )
    # Wait a while.  The job should start, but go nowhere.
    time.sleep(60)
    # Check the status in the UI, by fetching the job status page
    jobstatus = ConnectorHelpers.get_job_status_ui( username, password, job_id )
    if jobstatus != "Starting up":
        raise Exception("Expected to see 'Starting up' status, but saw '%s'" % jobstatus)
    # OK, reregister the connector now.  This should cause the job to wake up and actually start.  We have to be fast, though, to unregister exactly at that point.
    ConnectorHelpers.register_connector("com.metacarta.crawler.connectors.filesystem.FileConnector", "FilesystemConnector")
    # Wait for the job to leave the "Starting up" state; shut down everything the moment that happens
    it_started = False
    for retry in range(30):
        jobstatus = ConnectorHelpers.get_job_status_ui( username, password, job_id )
        if jobstatus == "Running":
            it_started = True
            break
        if jobstatus != "Starting up":
            raise Exception( "Expecting job to start, but wound up with status '%s' instead" % jobstatus )
        time.sleep(1)
    if it_started == False:
        raise Exception( "Job did not start as expected when connector was reregistered" )
    # Stop agents, and deregister connector again
    ConnectorHelpers.deregister_connector("com.metacarta.crawler.connectors.filesystem.FileConnector")
    # We should have immediately entered the "Running, no connector" state
    jobstatus = ConnectorHelpers.get_job_status_ui( username, password, job_id )
    if jobstatus != "Running, no connector":
        raise Exception("Expected to see 'Running, no connector' status, but saw '%s'" % jobstatus)

    # Pause the job
    ConnectorHelpers.pause_job_ui( username, password, job_id )
    # Check the status to see if we indeed paused.
    jobstatus = ConnectorHelpers.get_job_status_ui( username, password, job_id )
    if jobstatus != "Paused":
        raise Exception("Expected to see 'Paused' status, but saw '%s'" % jobstatus)

    # Resume the job
    ConnectorHelpers.resume_job_ui( username, password, job_id )
    # Check the status to see if we indeed paused.
    jobstatus = ConnectorHelpers.get_job_status_ui( username, password, job_id )
    if jobstatus != "Running, no connector":
        raise Exception("Expected to see 'Running, no connector' status, but saw '%s'" % jobstatus)

    # OK, reregister the connector once again now.  This should cause the job to wake up and finish.
    ConnectorHelpers.register_connector("com.metacarta.crawler.connectors.filesystem.FileConnector", "FilesystemConnector")

    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested, except for the one that should have been eliminated by the document template
    ConnectorHelpers.search_check( [ "reference" ], "Zena", [ "root/crawlarea/testfiles/f001.txt" ] )
    ConnectorHelpers.search_check( [ "interesting" ], "Zena", [ "root/crawlarea/testfiles/f006.txt" ] )
    ConnectorHelpers.search_check( [ "smelly" ], "Zena", [ "root/crawlarea/testfiles/f007.txt" ] )
    ConnectorHelpers.search_check( [ "restaurants" ], "Zena", [ "root/crawlarea/testfiles/newfolder/f008.txt" ] )
    ConnectorHelpers.search_check( [ "castle" ], "Zena", [ "root/crawlarea/testfiles/f002.txt" ] )
    ConnectorHelpers.search_check( [ "establishments" ], "Zena", [ "root/crawlarea/testfiles/f004.txt" ] )
    ConnectorHelpers.search_check( [ "albemarle" ], "Zena", [ "root/crawlarea/testfiles/f009.txt" ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], "Zena", [ "root/crawlarea/testfiles/f010.txt" ] )
    ConnectorHelpers.search_check( [ "sodapop" ], "Zena", [ ] )
    ConnectorHelpers.search_check( [ "humid" ], "Zena", [ "root/crawlarea/testfiles2/f002.txt" ] )
    ConnectorHelpers.search_check( [ "document" ], "Zena", [ "root/crawlarea/testfiles2/f001.txt", "root/crawlarea/testfiles/f002.txt", "root/crawlarea/testfiles/f009.txt" ] )

    # Create a second job that ingests the same documents using a different collection (part of the test for 24171)
    job_id_2 = ConnectorHelpers.define_filesystem_job_ui( username,
                                   password,
                                   "Test job 2",
                                   "FileSystem",
                                   [ ( "/root/crawlarea", [ ( "include", "file", "*.txt" ), ( "include", "directory", "*" ) ] ) ],
                                   collection_name="Boomer",
                                   document_template=document_template_text )

    # Run this job to completion.  It should replace all the collections with the new one...
    ConnectorHelpers.start_job( job_id_2 )
    ConnectorHelpers.wait_job_complete( job_id_2 )
    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Confirm that all the documents have been reingested, but into the new collection.
    ConnectorHelpers.search_check( [ "reference" ], "Boomer", [ "root/crawlarea/testfiles/f001.txt" ] )
    ConnectorHelpers.search_check( [ "interesting" ], "Boomer", [ "root/crawlarea/testfiles/f006.txt" ] )
    ConnectorHelpers.search_check( [ "smelly" ], "Boomer", [ "root/crawlarea/testfiles/f007.txt" ] )
    ConnectorHelpers.search_check( [ "restaurants" ], "Boomer", [ "root/crawlarea/testfiles/newfolder/f008.txt" ] )
    ConnectorHelpers.search_check( [ "castle" ], "Boomer", [ "root/crawlarea/testfiles/f002.txt" ] )
    ConnectorHelpers.search_check( [ "establishments" ], "Boomer", [ "root/crawlarea/testfiles/f004.txt" ] )
    ConnectorHelpers.search_check( [ "albemarle" ], "Boomer", [ "root/crawlarea/testfiles/f009.txt" ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], "Boomer", [ "root/crawlarea/testfiles/f010.txt" ] )
    ConnectorHelpers.search_check( [ "sodapop" ], "Boomer", [ ] )
    ConnectorHelpers.search_check( [ "humid" ], "Boomer", [ "root/crawlarea/testfiles2/f002.txt" ] )
    ConnectorHelpers.search_check( [ "document" ], "Boomer", [ "root/crawlarea/testfiles2/f001.txt", "root/crawlarea/testfiles/f002.txt", "root/crawlarea/testfiles/f009.txt" ] )

    # Now, delete the second job.  This should *not* delete the documents, because they are shared, but the "Boomer" collection
    # should still have documents in it.
    ConnectorHelpers.delete_job( job_id_2 )
    ConnectorHelpers.wait_job_deleted( job_id_2 )

    ConnectorHelpers.search_check( [ "reference" ], "Boomer", [ "root/crawlarea/testfiles/f001.txt" ] )
    ConnectorHelpers.search_check( [ "interesting" ], "Boomer", [ "root/crawlarea/testfiles/f006.txt" ] )
    ConnectorHelpers.search_check( [ "smelly" ], "Boomer", [ "root/crawlarea/testfiles/f007.txt" ] )
    ConnectorHelpers.search_check( [ "restaurants" ], "Boomer", [ "root/crawlarea/testfiles/newfolder/f008.txt" ] )
    ConnectorHelpers.search_check( [ "castle" ], "Boomer", [ "root/crawlarea/testfiles/f002.txt" ] )
    ConnectorHelpers.search_check( [ "establishments" ], "Boomer", [ "root/crawlarea/testfiles/f004.txt" ] )
    ConnectorHelpers.search_check( [ "albemarle" ], "Boomer", [ "root/crawlarea/testfiles/f009.txt" ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], "Boomer", [ "root/crawlarea/testfiles/f010.txt" ] )
    ConnectorHelpers.search_check( [ "sodapop" ], "Boomer", [ ] )
    ConnectorHelpers.search_check( [ "humid" ], "Boomer", [ "root/crawlarea/testfiles2/f002.txt" ] )
    ConnectorHelpers.search_check( [ "document" ], "Boomer", [ "root/crawlarea/testfiles2/f001.txt", "root/crawlarea/testfiles/f002.txt", "root/crawlarea/testfiles/f009.txt" ] )

    # Rerun the *first* job.  This should detect the fact that the collection needs to change, and the documents should thus be updated.
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # All should have reverted to how it was prior to job_2  being introduced!
    ConnectorHelpers.search_check( [ "reference" ], "Zena", [ "root/crawlarea/testfiles/f001.txt" ] )
    ConnectorHelpers.search_check( [ "interesting" ], "Zena", [ "root/crawlarea/testfiles/f006.txt" ] )
    ConnectorHelpers.search_check( [ "smelly" ], "Zena", [ "root/crawlarea/testfiles/f007.txt" ] )
    ConnectorHelpers.search_check( [ "restaurants" ], "Zena", [ "root/crawlarea/testfiles/newfolder/f008.txt" ] )
    ConnectorHelpers.search_check( [ "castle" ], "Zena", [ "root/crawlarea/testfiles/f002.txt" ] )
    ConnectorHelpers.search_check( [ "establishments" ], "Zena", [ "root/crawlarea/testfiles/f004.txt" ] )
    ConnectorHelpers.search_check( [ "albemarle" ], "Zena", [ "root/crawlarea/testfiles/f009.txt" ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], "Zena", [ "root/crawlarea/testfiles/f010.txt" ] )
    ConnectorHelpers.search_check( [ "sodapop" ], "Zena", [ ] )
    ConnectorHelpers.search_check( [ "humid" ], "Zena", [ "root/crawlarea/testfiles2/f002.txt" ] )
    ConnectorHelpers.search_check( [ "document" ], "Zena", [ "root/crawlarea/testfiles2/f001.txt", "root/crawlarea/testfiles/f002.txt", "root/crawlarea/testfiles/f009.txt" ] )

    # For added fun, make sure we can delete a job when the connector has been removed!
    ConnectorHelpers.deregister_connector("com.metacarta.crawler.connectors.filesystem.FileConnector")
    # Recycle the services to be sure there's no already-created handles around
    ConnectorHelpers.restart_tomcat()
    ConnectorHelpers.restart_agents()
    time.sleep(60)

    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    # Job cleanup should leave nothing around
    ConnectorHelpers.search_check( [ "reference" ], "Zena", [  ] )
    ConnectorHelpers.search_check( [ "interesting" ], "Zena", [  ] )
    ConnectorHelpers.search_check( [ "smelly" ], "Zena", [  ] )
    ConnectorHelpers.search_check( [ "restaurants" ], "Zena", [  ] )
    ConnectorHelpers.search_check( [ "castle" ], "Zena", [  ] )
    ConnectorHelpers.search_check( [ "establishments" ], "Zena", [  ] )
    ConnectorHelpers.search_check( [ "albemarle" ], "Zena", [  ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], "Zena", [  ] )
    ConnectorHelpers.search_check( [ "humid" ], "Zena", [  ] )
    ConnectorHelpers.search_check( [ "document" ], "Zena", [  ] )

    print "Done Collection Ingestion Test."

    # ConnectorHelpers.delete_repositoryconnection( "FileSystem" )
    ConnectorHelpers.delete_filesystem_repository_connection_ui( username, password, "FileSystem" )

    # OK, reregister the connector now.  This should have no other affect than getting the system back to normal.
    ConnectorHelpers.register_connector("com.metacarta.crawler.connectors.filesystem.FileConnector", "FilesystemConnector")

    # Phase 12: Throttling and report generation test

    # With this test, we will establish a fetch-rate throttle, and then crawl with it to verify that the crawler obeys the average fetch rate restrictions.
    # For this to work, the file system connector must also provide a "fetch" activity that we can run reports against.
    ConnectorHelpers.define_filesystem_repository_connection_ui( username, password, "FileSystem", "FileSystem Connection",throttles=[("","Limit fetch rate to two per minute","2")] )

    # Create and run the job.  The job should take about 5 minutes to run, given the throttle settings.
    job_id = ConnectorHelpers.define_filesystem_job_ui( username,
                                   password,
                                   "Test job",
                                   "FileSystem",
                                   [ ( "/root/crawlarea", [ ( "include", "file", "*.txt" ), ( "include", "directory", "*" ) ] ) ] )

    # Run the job to completion
    ConnectorHelpers.start_job_ui( username, password, job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Now, delete the job
    ConnectorHelpers.delete_job_ui( username, password, job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    # Run some history reports from the UI.  These reports should confirm an average read rate of 2 documents per minute, and should contain all expected events.
    simple_results = ConnectorHelpers.run_simple_history_report_ui( username, password, "FileSystem", [ "read document" ] )
    if len(simple_results) != 15:
        raise Exception("Expecting 15 simple report result rows; got %d" % len(simple_results))

    max_activity_results = ConnectorHelpers.run_max_activity_history_report_ui( username, password, "FileSystem", [ "read document" ], entity_bin_regexp="()" )
    if len(max_activity_results) != 1:
        raise Exception("Expecting 1 row in max activity report; got %d" % len(max_activity_results))
    rate_column = float(max_activity_results[0]["Highest Activity Rate [per min]"])
    if rate_column > 3.0:
        raise Exception("Maximum fetch rate exceeded the 1-sigma limit of 3.0 documents per minute; got %f" % rate_column)

    max_bandwidth_results = ConnectorHelpers.run_max_bandwidth_history_report_ui( username, password, "FileSystem", [ "read document" ], entity_bin_regexp="()" )
    if len(max_bandwidth_results) != 1:
        raise Exception("Expecting 1 row in max bandwidth report; got %d" % len(max_bandwidth_results))

    result_histogram = ConnectorHelpers.run_result_histogram_history_report_ui( username, password, "FileSystem", [ "read document" ], entity_bin_regexp="()", result_bin_regexp="(.*)" )

    if len(result_histogram) != 1:
        raise Exception("Expecting 1 row from result histogram; got %d" % len(result_histogram))
    if result_histogram[0]["Result Class"] != "ok":
        raise Exception("Expected only 'ok' results, got '%s'" % result_histogram[0]["Result Class"])
    if result_histogram[0]["Event Count"] != "15":
        raise Exception("Expected EventCount to be 15, was %s" % result_histogram[0]["Event Count"])

    # We need to make sure that a report screen where connection has been chosen is still happy after the connection goes away.
    # Open up a virtual browser window accordingly...
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )
    vb.load_main_window( "http://localhost/crawler/index.jsp" )
    window = vb.find_window("")
    link = window.find_link("Simple history")
    link.click( )
    window = vb.find_window("")
    form = window.find_form("report")
    form.find_selectbox("reportconnection").select_value( "FileSystem" )
    window.find_button("Continue").click( )
    window = vb.find_window("")
    form = window.find_form("report")
    activities_select = form.find_selectbox("reportactivities")
    activities_select.multi_select_value( "read document" )
    # Fire off the query
    window.find_button("Execute this query").click( )
    # Make sure we could fire off the query again if we wanted
    window = vb.find_window("")
    form = window.find_form("report")
    go_button = window.find_button("Execute this query")

    # Remove the connection.
    ConnectorHelpers.delete_filesystem_repository_connection_ui( username, password, "FileSystem" )

    # Make sure when we press "Go", something reasonable happens
    go_button.click()
    window = vb.find_window("")
    # Report form should be present
    form = window.find_form("report")
    # Go button should be gone, but we should have a "continue" button back...
    window.find_button("Continue")

    print "Done Report Tests."

    print "Spin to detect deadlock condition"
    
    # This uses the crawl configuration saved during the scheduling test.  We really don't care much about it except the queries that will be fired
    # off, so it's fine to rerun this as long as we use ResetAll to clean up whatever configuration garbage is around at the end.
    run_lock_spinner( "test_crawl_2.conf" )
    ConnectorHelpers.reset_all()
    ConnectorHelpers.define_gts_outputconnection( )
    
    # Next phase: Run postgresql maintenance script

    print "Running maintenance script"
    ConnectorHelpers.run_maintenance()
    print "Done with maintenance script test"

    print "Testing for error page script injection"
    response = ConnectorHelpers.invoke_curl( "http://localhost/crawler/error.jsp?text=%3Cscript%3Ealert(%27test%27)%3C/script%3E&target=%27%3E%3Cscript%3Ealert(%27test%27)%3C/script%3E", user_name=username, password=password )
    if response.find("<script>alert('test')</script>") != -1:
        raise Exception("Script injection seems to have taken place into error.jsp!  Response = %s" % response)
    print "Done with error page injection test"

    # Delete standard GTS output
    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )
    
    delete_folder("/root/crawlarea")
    sqatools.LicenseMakerClient.revoke_license()
    ConnectorHelpers.teardown_connector_environment( )

    # Last check: Be sure that there are no errors in the metacarta log due to postgresql connections being dropped
    print "Checking log for postgresql EOF errors"
    lines = ConnectorHelpers.read_metacarta_log( "unexpected EOF on client connection", log_pos )
    if len(lines) > 0:
        raise Exception("Found %d EOF errors in postgresql log output!" % len(lines) )


    print "Testing check-system-health when database is down"

    stop_database()

    # Kick off check_system_health until we don't see the 'skipping' message about authorities
    while True:
        if system_health_check( ):
            break

    start_database()
    ConnectorHelpers.start_agents( )

    print "Basic ConnectorFramework tests PASSED"
