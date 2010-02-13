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
import os
import sys
import time
import sha
import traceback
import ConnectorHelpers
from sqatools import LicenseMakerClient

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

# Copy a folder to a (new) area
def copy_folder( source, target ):
    ConnectorHelpers.invoke_root_script( [ "mkdir", "-p", target ] )
    ConnectorHelpers.invoke_root_script( [ "cp", "-r", source, target ] )

# Remove a folder
def delete_folder( target ):
    ConnectorHelpers.invoke_root_script( [ "rm", "-rf", target ] )

def preclean( print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    # This test shuts down tomcat and agents, so make sure tomcat and agents are in fact running
    ConnectorHelpers.start_tomcat()
    ConnectorHelpers.start_agents()

    try:
        ConnectorHelpers.reset_all()
    except Exception, e:
        if print_errors:
            print "Error resetting all jobs"
            print e

    # Reset the configuration
    ConnectorHelpers.configure_cf( { "com.metacarta.perf":"WARN" } )
    ConnectorHelpers.restart_tomcat()
    ConnectorHelpers.restart_agents()

    # Remove test documents first
    for folder in [ "/root/crawlarea" ]:
        try:
            delete_folder( folder )
        except Exception, e:
            if print_errors:
                print "Error removing %s" % folder
                print e

    try:
        license.revoke_license()
    except Exception, e:
        if print_errors:
            print "Error revoking license"
            print e

    try:
        ConnectorHelpers.teardown_connector_environment( )
    except Exception, e:
        if print_errors:
            print "Error cleaning up debs"
            print e

# Calculate the sha-1 hash value for a document identifier
def sha1( doc_id ):
    m = sha.new(doc_id)
    return m.hexdigest().upper()

def check_log():
    log_lines = ConnectorHelpers.read_log( "find documents | Queuing" )
    count = None
    for line in log_lines:
        if line.find("find documents") != -1:
            count = None
        elif line.find("(up to ") != -1:
            index = line.find("(up to ")
            index = index + 7;
            end_index = line.find(" ",index)
            value = int(line[index:end_index])
            if count == None:
                count = value
            elif value > count:
                raise Exception("Found a line where the max count of %d exceeded remaining count of %d" % (value,count) )
        elif line.find("Queuing ") != -1:
            index = line.find("Queuing ")
            index = index + 8
            end_index = line.find(" ", index)
            value = int(line[index:end_index])
            if value > count:
                raise Exception("Found a line where queue count of %d exceeded remaining count of %d" % (value,count) )
            else:
                count = count - value


# Main
if __name__ == '__main__':

    print "Precleaning!"

    preclean( print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()
    ConnectorHelpers.define_gts_outputconnection( )
    
    print "Setting up file area."
    copy_folder("/root/testfiles","/root/crawlarea")

    print "Setting up license."
    license = LicenseMakerClient.LicenseParams(MetaCartaVersion.major,
                                               MetaCartaVersion.minor,
                                               MetaCartaVersion.patch)
    license.enable_search_feature( "webSearch" )
    license.enable_search_feature( "soapSearch" )
    license.set_flag('energyExtractor', 'false')
    license.set_flag('geoTaggerCountryTagger' , 'false')
    license.set_flag('geotagger','true')
    license.set_flag('multiAppliance','false')
    license.set_flag('multitagger','true')
    license.set_flag('timetagger','false')
    license.set_flag('usStreetAddressGDM','false')
    license.write_license()

    # PHASE 1: Test stuffing behavior given expected data

    # Shut down services, so we can mess with the database
    ConnectorHelpers.shutdown_tomcat()
    ConnectorHelpers.shutdown_agents()

    # Open a connection to the database
    connection = pg.connect( "metacarta", "localhost", -1, None, None, "metacarta", "atracatem" )
    try:
        # Populate the jobs and jobqueue tables appropriately
        configxml = "<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><configuration></configuration>"
        connection_name = "FileConnector"
        connection.query("INSERT INTO repoconnections (classname,configxml,description,connectionname,maxcount)"+
                " VALUES ('com.metacarta.crawler.connectors.filesystem.FileConnector','%s','File System Connector','%s',10)" % (configxml,connection_name) )
        job_id = 12345678
        jobxml = "<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><specification><startpoint path=\\\"/root/crawlarea/testfiles\\\"><include match=\\\"*\\\" type=\\\"file\\\"/><include match=\\\"*\\\" type=\\\"directory\\\"/></startpoint></specification>"
        outputxml = "<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><specification></specification>"
        current_time = int(time.time() * 1000)
        connection.query("INSERT INTO jobs (id,description,docspec,outputspec,reseedinterval,type,starttime,lastchecktime,status,lasttime,connectionname,outputname,priority,startmethod,hopcountmode,intervaltime) VALUES ("+
                "%d,'Test job','%s','%s',3600000,'S',%d,%d,'A',%d,'%s','GTS',5,'D','A',3600000)" % (job_id,jobxml,outputxml,current_time,current_time,current_time,connection_name) )
        # Now, insert select jobqueue rows, each with a different document priority, so that we are guaranteed multiple queries will be required to fetch them all
        priority_reset_time = current_time + 360000     # An hour from now
        doc_id_1_value = 12345679
        doc_id_1 = "/root/crawlarea/testfiles/f001.txt"
        connection.query("INSERT INTO jobqueue (id,jobid,dochash,docid,docpriority,priorityset,status,checktime) values (%d,%d,'%s','%s',%f,%d,'P',0)" %
                (doc_id_1_value,job_id,sha1(doc_id_1),doc_id_1,0.01,priority_reset_time) )
        doc_id_2_value = 12345680
        doc_id_2 = "/root/crawlarea/testfiles/f002.txt"
        connection.query("INSERT INTO jobqueue (id,jobid,dochash,docid,docpriority,priorityset,status,checktime) values (%d,%d,'%s','%s',%f,%d,'P',0)" %
                (doc_id_2_value,job_id,sha1(doc_id_2),doc_id_2,0.26,priority_reset_time) )
        doc_id_3_value = 12345681
        doc_id_3 = "/root/crawlarea/testfiles/f003.txt"
        connection.query("INSERT INTO jobqueue (id,jobid,dochash,docid,docpriority,priorityset,status,checktime) values (%d,%d,'%s','%s',%f,%d,'P',0)" %
                (doc_id_3_value,job_id,sha1(doc_id_3),doc_id_3,0.76,priority_reset_time) )
        doc_id_4_value = 12345682
        doc_id_4 = "/root/crawlarea/testfiles/f004.txt"
        connection.query("INSERT INTO jobqueue (id,jobid,dochash,docid,docpriority,priorityset,status,checktime) values (%d,%d,'%s','%s',%f,%d,'P',0)" %
                (doc_id_4_value,job_id,sha1(doc_id_4),doc_id_4,0.90,priority_reset_time) )
        doc_id_5_value = 12345683
        doc_id_5 = "/root/crawlarea/testfiles/f005.txt"
        connection.query("INSERT INTO jobqueue (id,jobid,dochash,docid,docpriority,priorityset,status,checktime) values (%d,%d,'%s','%s',%f,%d,'P',0)" %
                (doc_id_5_value,job_id,sha1(doc_id_5),doc_id_5,0.95,priority_reset_time) )

    finally:
        connection.close()

    # Log perf stuff
    ConnectorHelpers.configure_cf( { "com.metacarta.perf":"DEBUG" } )

    # Restart services
    ConnectorHelpers.restart_agents()
    ConnectorHelpers.restart_tomcat()

    # The job we created should complete.  Wait for this.
    ConnectorHelpers.wait_job_complete( str(job_id) )

    # Examine the log, and make sure all seems right
    check_log()

    # All done.  Clean up!
    # Reset the configuration
    ConnectorHelpers.configure_cf( { "com.metacarta.perf":"WARN" } )

    ConnectorHelpers.restart_tomcat()
    ConnectorHelpers.restart_agents()

    # Delete the job and the connection.
    ConnectorHelpers.delete_job( str(job_id) )
    ConnectorHelpers.wait_job_deleted( str(job_id) )
    ConnectorHelpers.delete_repositoryconnection(connection_name)

    # PHASE 2.  Do the same thing with throttling on.

    # Shut down services, so we can mess with the database
    ConnectorHelpers.shutdown_tomcat()
    ConnectorHelpers.shutdown_agents()

    # Open a connection to the database
    connection = pg.connect( "metacarta", "localhost", -1, None, None, "metacarta", "atracatem" )
    try:
        # Populate the jobs and jobqueue tables appropriately
        configxml = "<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><configuration></configuration>"
        connection_name = "FileConnector"
        connection.query("INSERT INTO repoconnections (classname,configxml,description,connectionname,maxcount)"+
                " VALUES ('com.metacarta.crawler.connectors.filesystem.FileConnector','%s','File System Connector','%s',10)" % (configxml,connection_name) )
        connection.query("INSERT INTO throttlespec (ownername,description,throttle,match) VALUES ('%s','%s',%f,'%s')" %
                (connection_name,"Test throttle",100.0,".*") )
        job_id = 12345678
        jobxml = "<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><specification><startpoint path=\\\"/root/crawlarea/testfiles\\\"><include match=\\\"*\\\" type=\\\"file\\\"/><include match=\\\"*\\\" type=\\\"directory\\\"/></startpoint></specification>"
        outputxml = "<?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?><specification></specification>"
        current_time = int(time.time() * 1000)
        connection.query("INSERT INTO jobs (id,description,docspec,outputspec,reseedinterval,type,starttime,lastchecktime,status,lasttime,connectionname,outputname,priority,startmethod,hopcountmode,intervaltime) VALUES ("+
                "%d,'Test job','%s','%s',3600000,'S',%d,%d,'A',%d,'%s','GTS',5,'D','A',3600000)" % (job_id,jobxml,outputxml,current_time,current_time,current_time,connection_name) )
        # Now, insert select jobqueue rows, each with a different document priority, so that we are guaranteed multiple queries will be required to fetch them all
        priority_reset_time = current_time + 360000     # An hour from now
        doc_id_1_value = 12345679
        doc_id_1 = "/root/crawlarea/testfiles/f001.txt"
        connection.query("INSERT INTO jobqueue (id,jobid,dochash,docid,docpriority,priorityset,status,checktime) values (%d,%d,'%s','%s',%f,%d,'P',0)" %
                (doc_id_1_value,job_id,sha1(doc_id_1),doc_id_1,0.01,priority_reset_time) )
        doc_id_2_value = 12345680
        doc_id_2 = "/root/crawlarea/testfiles/f002.txt"
        connection.query("INSERT INTO jobqueue (id,jobid,dochash,docid,docpriority,priorityset,status,checktime) values (%d,%d,'%s','%s',%f,%d,'P',0)" %
                (doc_id_2_value,job_id,sha1(doc_id_2),doc_id_2,0.26,priority_reset_time) )
        doc_id_3_value = 12345681
        doc_id_3 = "/root/crawlarea/testfiles/f003.txt"
        connection.query("INSERT INTO jobqueue (id,jobid,dochash,docid,docpriority,priorityset,status,checktime) values (%d,%d,'%s','%s',%f,%d,'P',0)" %
                (doc_id_3_value,job_id,sha1(doc_id_3),doc_id_3,0.76,priority_reset_time) )
        doc_id_4_value = 12345682
        doc_id_4 = "/root/crawlarea/testfiles/f004.txt"
        connection.query("INSERT INTO jobqueue (id,jobid,dochash,docid,docpriority,priorityset,status,checktime) values (%d,%d,'%s','%s',%f,%d,'P',0)" %
                (doc_id_4_value,job_id,sha1(doc_id_4),doc_id_4,0.90,priority_reset_time) )
        doc_id_5_value = 12345683
        doc_id_5 = "/root/crawlarea/testfiles/f005.txt"
        connection.query("INSERT INTO jobqueue (id,jobid,dochash,docid,docpriority,priorityset,status,checktime) values (%d,%d,'%s','%s',%f,%d,'P',0)" %
                (doc_id_5_value,job_id,sha1(doc_id_5),doc_id_5,0.95,priority_reset_time) )

    finally:
        connection.close()

    # Log perf stuff
    ConnectorHelpers.configure_cf( { "com.metacarta.perf":"DEBUG" } )

    # Restart services
    ConnectorHelpers.restart_agents()
    ConnectorHelpers.restart_tomcat()

    # The job we created should complete.  Wait for this.
    ConnectorHelpers.wait_job_complete( str(job_id) )

    # Examine the log, and make sure all seems right
    check_log()

    # All done.  Clean up!
    # Reset the configuration
    ConnectorHelpers.configure_cf( { "com.metacarta.perf":"WARN" } )

    ConnectorHelpers.restart_tomcat()
    ConnectorHelpers.restart_agents()

    # Delete the job and the connection.
    ConnectorHelpers.delete_job( str(job_id) )
    ConnectorHelpers.wait_job_deleted( str(job_id) )
    ConnectorHelpers.delete_repositoryconnection(connection_name)

    delete_folder("/root/crawlarea")
    
    ConnectorHelpers.delete_gts_outputconnection( )
    license.revoke_license()
    ConnectorHelpers.teardown_connector_environment( )

    print "Stuffer ConnectorFramework tests PASSED"
