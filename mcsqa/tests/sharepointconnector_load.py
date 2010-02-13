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

# Method to add a document to the sharepoint repository
def add_document(protocol, servername, port, location, user, password, domain, sharepointurl, filepath, metadata=""):
    """Add a document to the repository"""
    listparams = [ ConnectorHelpers.process_argument(protocol),
            ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(location),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(domain),
            ConnectorHelpers.process_argument(sharepointurl),
            ConnectorHelpers.process_argument(filepath),
            ConnectorHelpers.process_argument(metadata) ]
    return ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.sharepoint.AddDoc", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="sharepoint-testing-package/metacarta-sharepointconnector-test.jar" )

# Method to remove a document from the sharepoint repository
def remove_document(protocol, servername, port, location, user, password, domain, sharepointurl):
    """Remove a document from the repository"""
    listparams = [ ConnectorHelpers.process_argument(protocol),
            ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(location),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(domain),
            ConnectorHelpers.process_argument(sharepointurl) ]
    try:
            ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.sharepoint.RemoveDoc", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="sharepoint-testing-package/metacarta-sharepointconnector-test.jar" )
    except Exception, e:
        print "Warning: Error deleting document: %s" % str(e)

# Method to see if a document exists already in the sharepoint repository
def lookup_document(protocol, servername, port, location, user, password, domain, sharepointurl):
    """ Attempt to grab the document, and let caller know if success or
        failure """
    listparams = [ ConnectorHelpers.process_argument(protocol),
            ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(location),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(domain),
            ConnectorHelpers.process_argument(sharepointurl) ]
    response = ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.sharepoint.LookupDoc", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="sharepoint-testing-package/metacarta-sharepointconnector-test.jar" )
    return response != ""

# Method to update a document in the sharepoint repository
def version_document(protocol, servername, port, location, user, password, domain, sharepointurl, filepath, metadata=""):
    """Create a new version of an existing document"""
    add_document(protocol,servername,port,location,user,password,domain,sharepointurl,filepath,metadata)

# Method to wait whatever time is needed after changing sharepoint documents
# for them to be noted as changed.
def wait_for_sharepoint(protocol, servername, port, location, user, password, domain):
    """Nothing needed"""
    pass

# Protocol
sharepointProtocol = "http"
# Server name to talk to
sharepointServerName = "10.32.76.27"
# Server port to talk to
sharepointServerPort = "80"
# User
sharepointUser = "Administrator"
# Password
sharepointPassword = "password"
# Domain
sharepointDomain = "QA-AD-76.METACARTA.COM"


def make_sharepoint_url(folder_path):
    if int(sharepointServerPort) == 80:
        return "%s/%s" % (sharepointServerName,folder_path)
    else:
        return "%s:%s/%s" % (sharepointServerName,sharepointServerPort,folder_path)

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
        LicenseMakerClient.revoke_license
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

# Main
if __name__ == '__main__':

    print "Precleaning!"

    preclean( print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    # Look for the last document
    filesExist = lookup_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, "LargeDocs/lf009-9-9-9")
    if filesExist == False:
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
                        id = "LargeDocs/"+llname
                        add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                                  sharepointDomain, id, filename)
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
        id = "LargeDocs/" + llname
        doc_id_array.append( id )
        level0 += 1

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["sharepointConnector"], detect_gdms=True)

    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )

    # PHASE 1: Ingestion


    print "Load Test."

    # In case there is clock skew, sleep a minute
    wait_for_sharepoint(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain)

    ConnectorHelpers.define_repositoryconnection( "SharepointConnection",
                                 "Sharepoint Connection",
                                 "com.metacarta.crawler.connectors.sharepoint.SharePointRepository",
                                 configparams = [ "serverProtocol="+sharepointProtocol,
                                                  "serverName="+sharepointServerName,
                                                  "serverPort="+sharepointServerPort,
                                                  "userName="+sharepointDomain+"\\"+sharepointUser,
                                                  "password="+sharepointPassword ] )

    # Define job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint site="" lib="LargeDocs"><include type="file" match="*"/><include type="directory" match="*"/></startpoint><security value="off"/></specification>'
    job_id = ConnectorHelpers.define_job( "Sharepoint test job",
                             "SharepointConnection",
                             doc_spec_xml )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    # Wait until it's really working, then cycle the service, to be sure it is shutting down cleanly
    time.sleep(120)
    ConnectorHelpers.restart_agents( )

    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_exists_check( [ "divestment url:"+sharepointProtocol+"://" + make_sharepoint_url(doc_id_array[0]) ], None, ConnectorHelpers.regexp_encode(make_sharepoint_url( doc_id_array[0] )) )
    ConnectorHelpers.search_exists_check( [ "visitors url:"+sharepointProtocol+"://" + make_sharepoint_url(doc_id_array[2]) ], None, ConnectorHelpers.regexp_encode(make_sharepoint_url( doc_id_array[2] )) )

    # Success: done
    print "Done load test."

    # PHASE 5: Delete Job

    print "Job Delete Test."
    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    # Make sure the documents all went away
    ConnectorHelpers.search_nonexists_check( [ "divestment url:"+sharepointProtocol+"://" + make_sharepoint_url(doc_id_array[0]) ], None, ConnectorHelpers.regexp_encode(make_sharepoint_url( doc_id_array[0] )) )
    ConnectorHelpers.search_nonexists_check( [ "visitors url:"+sharepointProtocol+"://" + make_sharepoint_url(doc_id_array[2]) ], None, ConnectorHelpers.regexp_encode(make_sharepoint_url( doc_id_array[2] )) )

    ConnectorHelpers.delete_repositoryconnection( "SharepointConnection" )

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "SharepointConnector load tests PASSED"
