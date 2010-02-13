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

import sys
import time
import ConnectorHelpers
import JcifsConnectorHelpers
import MetaHelpers
import sqatools
from wintools import sqa_domain_info
from wintools import filetools
from wintools import ambassador_client
from sqatools import LicenseMakerClient
import TestDocs
import VirtualBrowser
from threading import Thread

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

class run_strace(Thread):
    def __init__ (self, response):
        Thread.__init__(self)
        self.response = response
        self.setDaemon(True)

    def run(self):
        try:
            args = [ "/bin/sh", "-c", "strace -e trace=network,file -f -o /common/agents-strace.out -p $(ps -C java -o pid,cmd | awk '/AgentRun/ {print $1}')" ]
            ConnectorHelpers.invoke_root_script(args)
        except Exception, e:
            self.response.append( str(e) )

# Copy a folder to a (new) area
def copy_folder( source, target ):
    ConnectorHelpers.invoke_root_script( [ "mkdir", "-p", target ] )
    ConnectorHelpers.invoke_root_script( [ "cp", "-r", source, target ] )

# Remove a folder
def delete_folder( target ):
    ConnectorHelpers.invoke_root_script( [ "rm", "-rf", target ] )

# Server name to talk to
jcifsServerName = "w2k3-shp-76-1.QA-AD-76.METACARTA.COM"
# Domain
jcifsDomain = "QA-AD-76.METACARTA.COM"
# User
jcifsSimpleUser = "Administrator"
jcifsUser = jcifsSimpleUser + "@QA-AD-76.METACARTA.COM"
# Password
jcifsPassword = "password"
# Share name
jcifsShare = "qashare"
# DFS share name
jcifsDfsShare = "DFSRoot"
# DFS link
jcifsDfsLink = "DFSLink"
# DFS target (which is where we actually write stuff that is picked up via DFS)
jcifsDfsTarget = "DFSTarget"

# For the two-connections test, the document names are generated, but the base names are as follows:
idbase_connection1 = "SpinTestDocs1/base"
idbase_connection2 = "SpinTestDocs2/base"
idbase_connection3 = "SpinTestDocs/base"
idrange =  range(1,100)

# Login credentials
username = "testingest"
password = "testingest"


def preclean( ad_domain_info, print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    try:
        ConnectorHelpers.reset_all()
    except Exception, e:
        if print_errors:
            print "Error resetting all jobs"
            print e

    # Remove test documents first
    for folder in [ "/root/crawlarea" ]:
        try:
            delete_folder( folder )
        except Exception, e:
            if print_errors:
                print "Error removing %s" % folder
                print e

    # Clean up more documents we might have dumped onto the CIFS server
    for docinstance in idrange:
        doc_name_1 = "%s_%d.txt" % (idbase_connection1, docinstance)
        doc_name_2 = "%s_%d.txt" % (idbase_connection2, docinstance)
        doc_name_3 = "%s_%d.txt" % (idbase_connection3, docinstance)
        try:
            JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare + "/" + doc_name_1)
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % doc_name_1
                print e
        try:
            JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare + "/" + doc_name_2)
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % doc_name_2
                print e
        try:
            JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare + "/" + doc_name_3)
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % doc_name_3
                print e
        try:
            JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget + "/" + doc_name_3)
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % doc_name_3
                print e

    try:
        ConnectorHelpers.delete_crawler_user( username )
    except Exception, e:
        if print_errors:
            print "Error deleting crawl user"
            print e

    try:
        LicenseMakerClient.revoke_license()
    except Exception, e:
        if print_errors:
            print "Error cleaning up old license"
            print e

    try:
        ConnectorHelpers.teardown_connector_environment( )
    except Exception, e:
        if print_errors:
            print "Error cleaning up debs"
            print e

# Main
if __name__ == '__main__':

    # AD parameters
    ad_group = "76"
    if len(sys.argv) > 1:
        ad_group = sys.argv[1]

    ad_domain_info = sqa_domain_info.SQADomainInfo( ad_group )

    print "Precleaning!"

    preclean( ad_domain_info, print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up file area."
    copy_folder("/root/testfiles","/root/crawlarea")

    # Enable CF security
    ConnectorHelpers.enable_connector_framework( )

    print "Setting up license."
    license = LicenseMakerClient.LicenseParams(MetaCartaVersion.major,
                                               MetaCartaVersion.minor,
                                               MetaCartaVersion.patch)
    license.enable_search_feature( "webSearch" )
    license.enable_search_feature( "soapSearch" )
    license.enable_feature( "shareConnector" )
    license.set_flag('energyExtractor', 'false')
    license.set_flag('geoTaggerCountryTagger' , 'false')
    license.set_flag('geotagger','true')
    license.set_flag('multiAppliance','false')
    license.set_flag('multitagger','true')
    license.set_flag('timetagger','false')
    license.set_flag('usStreetAddressGDM','false')
    license.write_license()

    # Set up the ingestion user.

    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # PHASE 1: Create DFS documents on server, and spin to start up parallel jobs


    print "Initializing server documents."

    # First, create n documents in each of the two test areas
    for docinstance in idrange:
        doc_name_1 = "%s_%d.txt" % (idbase_connection1, docinstance)
        doc_name_2 = "%s_%d.txt" % (idbase_connection2, docinstance)
        doc_name_3 = "%s_%d.txt" % (idbase_connection3, docinstance)
        # Suck the data onto the shares
        filename = "/root/crawlarea/testfiles/f00%d.txt" % ((docinstance % 4) + 1)
        JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+doc_name_1, filename)
        JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+doc_name_2, filename)
        JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+doc_name_3, filename)
        JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+doc_name_3, filename)

    # In case there is clock skew, sleep a minute
    JcifsConnectorHelpers.wait_for_jcifs(jcifsServerName, jcifsUser, jcifsPassword)

    ConnectorHelpers.start_timing("spin test")

    # Define basic server-based repository connection
    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection1",
                                        "JCifs Connection # 1",
                                        jcifsServerName,
                                        None,
                                        jcifsUser,
                                        jcifsPassword )

    # Define basic domain-based repository connection
    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection2",
                                        "JCifs Connection # 2",
                                        jcifsDomain,
                                        jcifsDomain,
                                        jcifsSimpleUser,
                                        jcifsPassword )

    # Next, create two repository connections
    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection3",
                                        "JCifs Connection # 3",
                                        jcifsServerName,
                                        None,
                                        jcifsUser,
                                        jcifsPassword )

    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection4",
                                        "JCifs Connection # 4",
                                        jcifsServerName,
                                        None,
                                        jcifsUser,
                                        jcifsPassword )

    spin_capture = False
    ConnectorHelpers.shutdown_tomcat()
    ConnectorHelpers.shutdown_agents()

    # This is the spin part of the test.
    for repeatcount in range(100):
        # Clear the logs
        ConnectorHelpers.clear_logs()
        # Restart the services so new logs will be used
        ConnectorHelpers.start_tomcat()
        time.sleep(15)
        ConnectorHelpers.start_agents()

        response_string = ""
        mt = None
        if spin_capture == True:
            mt = run_strace(response_string)
            mt.start()

        # Define server-based job
        job_id_1 = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                            password,
                            "JCifs test job 1",
                            "JCifsConnection1",
                            [ ( [ jcifsShare, "SpinTestDocs" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ) ] )

        # Define domain-based job
        job_id_2 = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                            password,
                            "JCifs test job 2",
                            "JCifsConnection2",
                            [ ( [ jcifsDfsShare, jcifsDfsLink, "SpinTestDocs" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ) ] )

        job_id_3 = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                            password,
                            "JCifs test job 3",
                            "JCifsConnection3",
                            [ ( [ jcifsShare, "SpinTestDocs1" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ) ] )

        job_id_4 = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                            password,
                            "JCifs test job 4",
                            "JCifsConnection4",
                            [ ( [ jcifsShare, "SpinTestDocs2" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ) ] )

        # These are the documents in DFS that are located without being domain-based
        job_id_5 = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                            password,
                            "JCifs test job 5",
                            "JCifsConnection1",
                            [ ( [ jcifsDfsShare, jcifsDfsLink, "SpinTestDocs" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ) ] )

        # Run the jobs to completion
        ConnectorHelpers.start_job( job_id_1 )
        ConnectorHelpers.start_job( job_id_2 )
        ConnectorHelpers.start_job( job_id_3 )
        ConnectorHelpers.start_job( job_id_4 )
        ConnectorHelpers.start_job( job_id_5 )

        ConnectorHelpers.wait_job_complete( job_id_1 )
        ConnectorHelpers.wait_job_complete( job_id_2 )
        ConnectorHelpers.wait_job_complete( job_id_3 )
        ConnectorHelpers.wait_job_complete( job_id_4 )
        ConnectorHelpers.wait_job_complete( job_id_5 )

        # Check for NPE's in log
        results = ConnectorHelpers.read_log( "NullPointerException" )
        if len(results) > 0:
            raise Exception("Concurrent jobs detected null pointer exception!  Check the agents.log file.")

        # Don't wait for ingest to catch up; just kill the job right away
        #ConnectorHelpers.wait_for_ingest( )
        ConnectorHelpers.delete_job( job_id_1 )
        ConnectorHelpers.delete_job( job_id_2 )
        ConnectorHelpers.delete_job( job_id_3 )
        ConnectorHelpers.delete_job( job_id_4 )
        ConnectorHelpers.delete_job( job_id_5 )

        ConnectorHelpers.wait_job_deleted( job_id_1 )
        ConnectorHelpers.wait_job_deleted( job_id_2 )
        ConnectorHelpers.wait_job_deleted( job_id_3 )
        ConnectorHelpers.wait_job_deleted( job_id_4 )
        ConnectorHelpers.wait_job_deleted( job_id_5 )

        # Success: done this cycle
        print "Done cycle %d." % repeatcount

        ConnectorHelpers.shutdown_tomcat()
        ConnectorHelpers.shutdown_agents()

        if spin_capture == True:
            while True:
                # If strace complete, break
                if mt.isAlive() == False:
                    break;
            mt.join()

    # Clean up crawl pieces
    ConnectorHelpers.start_tomcat()
    time.sleep(15)
    ConnectorHelpers.start_agents()

    ConnectorHelpers.delete_repository_connection_ui( username, password, "JCifsConnection4" )
    ConnectorHelpers.delete_repository_connection_ui( username, password, "JCifsConnection3" )
    ConnectorHelpers.delete_repository_connection_ui( username, password, "JCifsConnection2" )
    ConnectorHelpers.delete_repository_connection_ui( username, password, "JCifsConnection1" )

    # Adjust timeout for the hardware.  These values came originally from the qmt, before the times there were adjusted.
    time_limit = 12100
    hardware_type = MetaHelpers.get_hardware_type()
    if hardware_type.find("2850") != -1:
        time_limit = 16500
    ConnectorHelpers.end_timing("spin test",limit=time_limit)

    # Now, delete the documents created for this test
    for docinstance in idrange:
        doc_name_1 = "%s_%d.txt" % (idbase_connection1, docinstance)
        doc_name_2 = "%s_%d.txt" % (idbase_connection2, docinstance)
        doc_name_3 = "%s_%d.txt" % (idbase_connection3, docinstance)
        JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare + "/" + doc_name_1)
        JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare + "/" + doc_name_2)
        JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare + "/" + doc_name_3)
        JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget + "/" + doc_name_3)

    # Get rid of test crawler user
    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    # Clean up temporary folder
    delete_folder("/root/crawlarea")

    LicenseMakerClient.revoke_license()

    ConnectorHelpers.teardown_connector_environment( )

    print "JCifsConnector startup spinner tests PASSED"
