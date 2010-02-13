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
import sqatools
from wintools import sqa_domain_info
from sqatools import LicenseMakerClient
import TestDocs

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion



# Method to add a document to the livelink repository
def add_document(servername, port, user, password, llpath, llname, filename):
    """Add a document to the repository"""
    listparams = [ ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(llpath),
            ConnectorHelpers.process_argument(llname),
            ConnectorHelpers.process_argument(filename) ]
    return ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.livelink.AddDoc", 
                                    argument_list=listparams,
                                    additional_classpath="livelink-testing-package/metacarta-livelinkconnector-test.jar" )

# Method to lookup a document in the livelink repository
def lookup_document(servername, port, user, password, llpath):
    """Add a document to the repository"""
    listparams = [ ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(llpath) ]
    return ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.livelink.LookupDoc", 
                                    argument_list=listparams,
                                    additional_classpath="livelink-testing-package/metacarta-livelinkconnector-test.jar" )

# Method to remove a document from the livelink repository
def remove_document(servername, port, user, password, llpath):
    """Remove a document from the repository"""
    listparams = [ ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(llpath) ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.livelink.RemoveDoc", 
                                    argument_list=listparams,
                                    additional_classpath="livelink-testing-package/metacarta-livelinkconnector-test.jar" )

# Method to update a document in the livelink repository
def version_document(servername, port, user, password, llpath, filename):
    """Create a new version of an existing document"""
    listparams = [ ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(llpath),
            ConnectorHelpers.process_argument(filename) ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.livelink.VersionDoc", 
                                    argument_list=listparams,
                                    additional_classpath="livelink-testing-package/metacarta-livelinkconnector-test.jar" )

def add_document_right(servername, port, user, password, llpath, username, domain):
    """Adds the right to see & view contents of the specified path, for the given username"""
    listparams = [ ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(llpath),
            ConnectorHelpers.process_argument(username),
            ConnectorHelpers.process_argument(domain) ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.livelink.AddDocRights", 
                                    argument_list=listparams,
                                    additional_classpath="livelink-testing-package/metacarta-livelinkconnector-test.jar" )

# Method to wait whatever time is needed after changing livelink documents
# for them to be noted as changed.
def wait_for_livelink(servername, port, user, password):
    """Nothing needed"""
    pass

# Server name to talk to
llServerName = None
# Server port to talk to
llServerPort = "2099"
# User
llUser = "admin"
# Password
llPassword = "livelink"

fullURLSuffix = "&objAction=download"

def build_livelink_url( id ):
    """Build the url from pieces"""
    # No protocol base url
    veryBaseURL = llServerName + "/livelink/livelink.exe"
    # Base URL
    baseURL = "http://"+veryBaseURL
    # Full URL
    fullURL = veryBaseURL + "?func=ll&objID="
    return fullURL + id + fullURLSuffix

# Crawl user credentials
username = "testingest"
password = "testingest"

def preclean( print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    try:
        ConnectorHelpers.start_agents( )
    except Exception, e:
        if print_errors:
            print "Error starting metacarta-agents"
            print e

    try:
        ConnectorHelpers.reset_all()
    except Exception, e:
        if print_errors:
            print "Error resetting all jobs"
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

    llServerName = "livelinksrvr.qa-ad-76.metacarta.com"
    if len(sys.argv) > 1:
        llServerName = sys.argv[1]

    print "Precleaning!"

    preclean( print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    # Look for the last document
    id = lookup_document(llServerName, llServerPort, llUser, llPassword, "LargeDocs/lf009-9-9-9")
    if id == None or id == "null":
        # Presume that we have to set up the server
        print "Initializing test documents."
        # We need at least 10,000 documents.  We'll get this by
        # having 10 documents each with 1000 different names at the same level in the hierarchy.
        # We do it this way because we have no programmatic way of creating folders at this time.
        doc_id_array = []
        level0 = 0
        while level0 < 10:
            level1 = 0
            while level1 < 10:
                level2 = 0
                while level2 < 10:
                    level3 = 0
                    while level3 < 10:
                        filename = "/root/largefiles/00%d.htm" % level0
                        llname = "lf00%d-%d-%d-%d" % (level0,level1,level2,level3)
                        id = add_document(llServerName, llServerPort, llUser, llPassword, "LargeDocs", llname, filename)
                        if level1 == 0 and level2 == 0 and level3 == 0:
                            doc_id_array.append( id )
                        level3 += 1
                    level2 += 1
                level1 += 1
            level0 += 1
        print "Done creating test documents on server."

    # The documents are already on the LLserver box, under the LargeDocs folder.  We
    # just need exemplars of each one, so we can search for them.

    # We need at least 10,000 documents.  We'll get this by
    # having 10 documents each with 1000 different names at the same level in the hierarchy.
    # We do it this way because we have no programmatic way of creating folders at this time.
    doc_id_array = []
    level0 = 0
    while level0 < 10:
        llname = "lf00%d-%d-%d-%d" % (level0,0,0,0)
        id = lookup_document(llServerName, llServerPort, llUser, llPassword, "LargeDocs/" + llname)
        if id == None:
            raise Exception("Couldn't find %s in repository" % ("LargeDocs/"+llname) )
        doc_id_array.append( id )
        level0 += 1

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["livelinkConnector"], detect_gdms=True)


    # Set up the ingestion user.
    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # PHASE 1: Ingestion


    print "Load Test."

    # In case there is clock skew, sleep a minute
    wait_for_livelink(llServerName, llServerPort, llUser, llPassword)

    ConnectorHelpers.define_repositoryconnection( "LivelinkConnection",
                                 "Livelink Connection",
                                 "com.metacarta.crawler.connectors.livelink.LivelinkConnector",
                                 configparams = [ "CGI path=/livelink/livelink.exe",
                                                  "View CGI path=",
                                                  "Server name="+llServerName,
                                                  "Server port="+llServerPort,
                                                  "Server user name="+llUser,
                                                  "Server password="+llPassword,
                                                  "NTLM domain=" ] )

    # Define job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="LargeDocs"/><include filespec="*.htm"/><security value="off"/></specification>'
    job_id = ConnectorHelpers.define_job( "Livelink test job",
                             "LivelinkConnection",
                             doc_spec_xml )

    # Run the job
    ConnectorHelpers.start_job( job_id )
    # Livelink abort test!  Abort the job and see how long it takes for it to actually stop doing stuff
    # First, wait 1 minute for the job to get really rolling
    time.sleep(60)
    # Now, abort it
    ConnectorHelpers.abort_job( job_id )
    # Wait to see how long it actually takes to abort the job
    the_time = time.time()
    ConnectorHelpers.wait_job_complete( job_id )
    elapsed_time = time.time() - the_time;
    print "It took %f seconds to abort the job" % elapsed_time
    if elapsed_time > 120.0:
        raise Exception( "Took too long for job to abort: %f seconds" % elapsed_time )

    # Now, start it again and run the job to completion this time
    ConnectorHelpers.start_job( job_id )
    # Wait until it's really working, then cycle the service, to be sure it is shutting down cleanly
    time.sleep(120)
    ConnectorHelpers.restart_agents( )

    # Stop agents service when a job is running, delicense the connector, start it again, make sure it comes up
    print "Delicensing livelink connector test."

    time.sleep(120)
    ConnectorHelpers.shutdown_agents( )

    # Remove license
    sqatools.appliance.install_license(extra_services=[ ], detect_gdms=True)

    # Start metacarta-agents
    ConnectorHelpers.start_agents( )
    # If the bug is present, metacarta-agents will come up only briefly and then kill itself.  This should be obvious from c_s_h.
    time.sleep(60)

    # If it makes it this far, the job should have aborted due to the bad license.
    job_statuses = ConnectorHelpers.list_job_statuses_api( )
    if len(job_statuses) != 1:
        raise Exception("Expected one job status, instead found %d" % len(job_statuses))
    if job_statuses[0]["status"] != "error":
        raise Exception("Expected job status to be 'error', instead found '%s'" % job_statuses[0]["status"])

    # Restore license
    sqatools.appliance.install_license(extra_services=["livelinkConnector"], detect_gdms=True)
    # Start job again
    ConnectorHelpers.start_job( job_id )

    # Now, wait for job to complete
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_exists_check( [ "divestment url:http://" + build_livelink_url(doc_id_array[0]) ], None, ConnectorHelpers.regexp_encode(build_livelink_url( doc_id_array[0] )) )
    ConnectorHelpers.search_exists_check( [ "visitors url:http://" + build_livelink_url(doc_id_array[2]) ], None, ConnectorHelpers.regexp_encode(build_livelink_url( doc_id_array[2] )) )

    # Success: done
    print "Done load test."

    # PHASE 5: Delete Job

    print "Job Delete Test."
    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    # Make sure the documents all went away
    ConnectorHelpers.search_nonexists_check( [ "divestment url:http://" + build_livelink_url(doc_id_array[0]) ], None, ConnectorHelpers.regexp_encode(build_livelink_url( doc_id_array[0] )) )
    ConnectorHelpers.search_nonexists_check( [ "visitors url:http://" + build_livelink_url(doc_id_array[2]) ], None, ConnectorHelpers.regexp_encode(build_livelink_url( doc_id_array[2] )) )

    print "Done Job Delete Test."

    ConnectorHelpers.delete_repositoryconnection( "LivelinkConnection" )

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "LivelinkConnector load tests PASSED"
