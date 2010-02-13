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
from threading import Thread
from sqatools import LicenseMakerClient
import TestDocs

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

# Method to initialize docbroker reference
def initialize(server):
    """ Initialize DFC on the appliance """
    listparams = [ "/usr/bin/metacarta-setupdocumentum",
            ConnectorHelpers.process_argument(server) ]
    return ConnectorHelpers.invoke_root_script( listparams )

# Invoke documentum-classpath java code
def execute_documentum_java( classname, argument_list=[], input=None ):
    """ Invoke documentum classpath java code """
    command_arguments = [ "documentum-testing-package/executejava", classname ] + argument_list
    return ConnectorHelpers.invoke_script(command_arguments,input=input)

# Method to add a document to the documentum docbase
def add_document(docbase, domain, user, password, location, file, path):
    """Add a document to the docbase"""
    listparams = [ ConnectorHelpers.process_argument(docbase),
            ConnectorHelpers.process_argument(domain),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(location),
            ConnectorHelpers.process_argument(file),
            ConnectorHelpers.process_argument(path) ]
    return execute_documentum_java( "com.metacarta.crawler.connectors.DCTM.AddDoc", argument_list=listparams )

# Method to lookup the id of a document in the documentum docbase
def lookup_document(docbase, domain, user, password, location, file):
    """ Look up a document and return its ID. """
    listparams = [ ConnectorHelpers.process_argument(docbase),
            ConnectorHelpers.process_argument(domain),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(location),
            ConnectorHelpers.process_argument(file) ]
    return execute_documentum_java( "com.metacarta.crawler.connectors.DCTM.FindDoc", argument_list=listparams )

# Method to remove a document from the documentum docbase
def remove_document(docbase, domain, user, password, location, file):
    """Remove a document from the docbase"""
    listparams = [ ConnectorHelpers.process_argument(docbase),
            ConnectorHelpers.process_argument(domain),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(location),
            ConnectorHelpers.process_argument(file) ]
    execute_documentum_java( "com.metacarta.crawler.connectors.DCTM.RemoveDoc", argument_list=listparams )

# Method to update a document in the documentum docbase
def version_document(docbase, domain, user, password, location, file):
    """Create a new version of an existing document"""
    listparams = [ ConnectorHelpers.process_argument(docbase),
            ConnectorHelpers.process_argument(domain),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(location),
            ConnectorHelpers.process_argument(file) ]
    execute_documentum_java( "com.metacarta.crawler.connectors.DCTM.VersionDoc", argument_list=listparams )

# Method to outwait the time skew between connector framework box
# and documentum docbase
def wait_for_documentum(docbase, domain, user, password):
    """This will eventually create a temporary document, get its time, and wait until then"""
    time.sleep(120)

# Documentum constants
documentum_docbase = "Metacarta1"
documentum_username = "dmadmin"
documentum_password = "password"
documentum_domain = ""
documentum_location = "/LargeDocs"

# No protocol base url
veryBaseURL = "dctmsrvr.qa-ad-76.metacarta.com:8080/webtop/"
# Base URL
baseURL = "http://"+veryBaseURL
# Full URL
full_url = veryBaseURL + "component/drl?versionLabel=CURRENT&objectId="

# Crawl user credentials
username = "testingest"
password = "testingest"

def preclean( print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

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

class run_restart_thread(Thread):
    def __init__ (self, response):
        Thread.__init__(self)
        self.response = response
        self.setDaemon(True)

    def run(self):
        # Initial sleep: 10 seconds
        time.sleep(10)
        retry_count = 10
        for retry_number in range(retry_count):
            try:
                # Restart metacarta-agents, but do it with a time limit of 30 seconds.  If it ever takes longer than that, we have failed.
                current_time = time.time()
                ConnectorHelpers.restart_agents()
                new_time = time.time()
                if new_time-current_time > 30:
                    raise Exception("Restart #%d of metacarta-agents took longer than 30 seconds: %f" % (retry_number,new_time-current_time))
                time.sleep(60)

            except Exception, e:
                self.response.append( str(e) )

class run_maintenance_thread(Thread):
    def __init__ (self, response):
        Thread.__init__(self)
        self.response = response
        self.setDaemon(True)
    def run(self):
        try:
            ConnectorHelpers.run_maintenance()
        except Exception, e:
            self.response.append( str(e) )

# Main
if __name__ == '__main__':

    print "Initializing to point to server"
    initialize( "dctmsrvr.qa-ad-76.metacarta.com" )

    print "Precleaning!"
    preclean( print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    # Look for the last document
    id = lookup_document(documentum_docbase, documentum_domain, documentum_username, documentum_password,
                         documentum_location, "lf009-9-9-9")
    if id == None or id == "":
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
                        path = "/root/largefiles/00%d.htm" % level0
                        documentum_name = "lf00%d-%d-%d-%d" % (level0,level1,level2,level3)
                        id = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password,
                                   documentum_location, documentum_name, path)
                        if level1 == 0 and level2 == 0 and level3 == 0:
                            doc_id_array.append( id )
                        level3 += 1
                    level2 += 1
                level1 += 1
            level0 += 1
        print "Done creating test documents on server."

    # The documents are already on the Documentum box, under the LargeDocs folder.  We
    # just need exemplars of each one, so we can search for them.

    # We need at least 10,000 documents.  We'll get this by
    # having 10 documents each with 1000 different names at the same level in the hierarchy.
    # We do it this way because we have no programmatic way of creating folders at this time.
    doc_id_array = []
    level0 = 0
    while level0 < 10:
        documentum_name = "lf00%d-%d-%d-%d" % (level0,0,0,0)
        id = lookup_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, documentum_location, documentum_name)
        if id == None:
            raise Exception("Couldn't find %s in repository" % (documentum_name) )
        doc_id_array.append( id )
        level0 += 1

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["documentumConnector"], detect_gdms=True)

    # Set up the ingestion user.
    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )

    # PHASE 1: Ingestion


    print "Load Test."

    # In case there is clock skew, sleep a minute
    wait_for_documentum(documentum_docbase,documentum_domain,documentum_username,documentum_password)

    ConnectorHelpers.define_repositoryconnection( "DocumentumConnection",
                                 "Documentum Connection",
                                 "com.metacarta.crawler.connectors.DCTM.DCTM",
                                 configparams = [ "docbasename="+documentum_docbase,
                                                  "docbaseusername="+documentum_username,
                                                  "docbasepassword="+documentum_password,
                                                  "domain="+documentum_domain,
                                                  "webtopbaseurl="+baseURL ] )

    # Define job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><docbaselocation path="/LargeDocs"/><objecttype token="dm_document"/><security value="off"/></specification>'
    job_id = ConnectorHelpers.define_job( "Documentum test job",
                             "DocumentumConnection",
                             doc_spec_xml )

    print "Testing how long job abort takes while seeding underway"

    ConnectorHelpers.start_job( job_id )
    time.sleep(20)
    abort_time = time.time()
    ConnectorHelpers.abort_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    abort_interval = time.time() - abort_time
    if abort_interval > 90.0:
        raise Exception("Abort of documentum job during seeding process took more than 1.5 minutes! (%f seconds)" % abort_interval)
    
    print "Testing what happens when metacarta-agents is shut down multiple times during job run"

    ConnectorHelpers.start_job( job_id )
    # Fire up a parallel thread that attempts to shut down metacarta-agents, and times how long that takes
    response_string = ""
    mt = run_restart_thread(response_string)
    mt.start()

    ConnectorHelpers.wait_job_complete( job_id )

    mt.join()
    if len(response_string) > 0:
        raise Exception("Start/stop of metacarta-agents during load test failed: %s" % response_string)

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_exists_check( [ "divestment url:http://" + full_url + doc_id_array[0] ], None, ConnectorHelpers.regexp_encode(full_url + doc_id_array[0]) )
    ConnectorHelpers.search_exists_check( [ "visitors url:http://" + full_url + doc_id_array[2] ], None, ConnectorHelpers.regexp_encode(full_url + doc_id_array[2]) )

    # Success: done
    print "Done load test."

    print "Testing search while doing maintenance with an authority set up."

    # Enable CF security
    ConnectorHelpers.enable_connector_framework( )

    ConnectorHelpers.define_authorityconnection( "DCTMAuthority", "DCTMAuthority",
                                                 "com.metacarta.crawler.authorities.DCTM.AuthorityConnector",
                                                 configparams=[ "docbasename=%s" % documentum_docbase,
                                                                "docbaseusername=%s" % documentum_username,
                                                                "docbasepassword=%s" % documentum_password ] )

    # Fire up maintenance operation in a separate thread
    response_string = ""
    mt = run_maintenance_thread(response_string)
    mt.start()

    # Perform authority checks every .25 second until maintenance operation is complete.

    fail_count = 0
    good_count = 0
    while True:
        # If maintenance complete, break
        if mt.isAlive() == False:
            break;
        try:
            responses = ConnectorHelpers.invoke_curl("http://localhost:8180/authorityservice/UserACLs?username=dmadmin")
        except Exception, e:
            responses = ""
        response_lines = responses.splitlines()
        if len(response_lines) < 1:
            response_lines = [ "" ]
        find_pos = response_lines[0].find("AUTHORIZED:")
        if find_pos == None or find_pos == -1:
            fail_count += 1
        else:
            good_count += 1
        time.sleep( 0.25 )
    mt.join()
    if fail_count > 0:
        raise Exception("Bad response detected %d times from authority service; failure" % fail_count)
    if good_count <= 1:
        raise Exception("Test invalid; unable to perform sufficient searches while postgresql maintenance ongoing")
    if len(response_string) > 0:
        raise Exception("Maintenance script had an error: %s" % response_string)
    ConnectorHelpers.delete_authorityconnection( "DCTMAuthority" )
    print "Done maintenance/search/authority test."

    # PHASE 5: Delete Job

    print "Job Delete Test."
    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    # Make sure the documents all went away
    ConnectorHelpers.search_nonexists_check( [ "divestment url:http://" + full_url + doc_id_array[0] ], None, ConnectorHelpers.regexp_encode(full_url + doc_id_array[0]) )
    ConnectorHelpers.search_nonexists_check( [ "visitors url:http://" + full_url + doc_id_array[2] ], None, ConnectorHelpers.regexp_encode(full_url + doc_id_array[2]) )

    print "Done Job Delete Test."

    ConnectorHelpers.delete_repositoryconnection( "DocumentumConnection" )

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "DocumentumConnector load tests PASSED"
