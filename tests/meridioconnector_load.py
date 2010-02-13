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

# Method to add a document to the meridio repository
def add_document(docurl, recurl, domainuser, password, folder, filepath, filename, filetitle, category="MetacartaSearchable"):
    """Add a document to the repository"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(folder),
            ConnectorHelpers.process_argument(filepath),
            ConnectorHelpers.process_argument(filename),
            ConnectorHelpers.process_argument(filetitle),
            ConnectorHelpers.process_argument(category) ]
    stringval = ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.AddDoc", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )
    stringarry = stringval.split("\n")
    # Take the last line
    return stringarry[len(stringarry)-1]

# Method to add multiple documents to the repository
def add_documents_multiple(docurl, recurl, domainuser, password, folder, basefilepath, basetitle, levelcount, levelmax, category="MetacartaSearchable"):
    """Add a document to the repository"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(folder),
            ConnectorHelpers.process_argument(basefilepath),
            ConnectorHelpers.process_argument(basetitle),
            str(levelcount),
            str(levelmax),
            ConnectorHelpers.process_argument(category) ]
    stringval = ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.AddDocMultiple", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )
    stringarry = stringval.split("\n")

# Method to add a record to the meridio repository
def add_record(docurl, recurl, domainuser, password, folder, filepath, filename, filetitle, category="MetacartaSearchable"):
    """Add a document to the repository"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(folder),
            ConnectorHelpers.process_argument(filepath),
            ConnectorHelpers.process_argument(filename),
            ConnectorHelpers.process_argument(filetitle),
            ConnectorHelpers.process_argument(category) ]
    stringval = ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.AddRec", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )
    stringarry = stringval.split("\n")
    # Take the last line
    return stringarry[len(stringarry)-1]

# Method to add multiple documents to the repository
def add_records_multiple(docurl, recurl, domainuser, password, folder, basefilepath, basetitle, levelcount, levelmax, category="MetacartaSearchable"):
    """Add a document to the repository"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(folder),
            ConnectorHelpers.process_argument(basefilepath),
            ConnectorHelpers.process_argument(basetitle),
            str(levelcount),
            str(levelmax),
            ConnectorHelpers.process_argument(category) ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.AddRecMultiple", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )

# Method to remove a document from the meridio repository
def remove_document(docurl, recurl, domainuser, password, docid):
    """Remove a document from the repository"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(docid) ]
    try:
        ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.DeleteDoc", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )
    except Exception, e:
        print "Warning: Error deleting document: %s" % str(e)

# Method to remove a record from the meridio repository
def remove_record(docurl, recurl, domainuser, password, docid):
    """Remove a document from the repository"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(docid) ]
    try:
        ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.DeleteRec", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )
    except Exception, e:
        print "Warning: Error deleting record: %s" % str(e)

# Method to lookup a document in the meridio repository
def lookup_document(docurl, recurl, domainuser, password, folder, title):
    """Lookup a document in the repository"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(folder),
            ConnectorHelpers.process_argument(title) ]
    stringval = ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.LookupDoc", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )
    stringarry = stringval.split("\n")
    # Take the last line
    return stringarry[len(stringarry)-1]

# Method to lookup a record in the meridio repository
def lookup_record(docurl, recurl, domainuser, password, folder, title):
    """Lookup a record in the repository"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(folder),
            ConnectorHelpers.process_argument(title) ]
    stringval = ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.LookupRec", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )
    stringarry = stringval.split("\n")
    # Take the last line
    return stringarry[len(stringarry)-1]

# Method to update a document in the meridio repository
def update_document(docurl, recurl, domainuser, password, docid, folder, filename):
    """Create a new version of an existing document"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(docid),
            ConnectorHelpers.process_argument(folder),
            ConnectorHelpers.process_argument(filename),
            ConnectorHelpers.process_argument("Updated for testing") ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.UpdateDoc", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )

# Method to add a read acl to a document
def add_acl_to_document(docurl, recurl, domainuser, password, docid, read_username):
    """ Add read permission for the given user to the document """
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(docid),
            ConnectorHelpers.process_argument(read_username),
            ConnectorHelpers.process_argument("READ") ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.AddDocAcl", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )


# Method to wait whatever time is needed after changing meridio documents
# for them to be noted as changed.
def wait_for_meridio(docurl, recurl, domainuser, password):
    """Nothing needed"""
    pass

# Protocol
meridioProtocol = "http"
# Server name to talk to
meridioServerName = "w2k3-mer-76-1.qa-ad-76.metacarta.com"
# Server port to talk to
meridioServerPort = ""
# User
meridioUser = "meridioServiceUser"
# Password
meridioPassword = "password"
# Domain
meridioDomain = "QA-AD-76.METACARTA.COM"
# Document management location
documentLocation = "/DMWS/MeridioDMWS.asmx";
# Record management location
recordLocation = "/RMWS/MeridioRMWS.asmx";
# Web client location
webClientLocation = "/meridio/browse/downloadcontent.aspx";
# Document path for this test
docpath = "/Testing/LargeDocs"

# These are the calculated URLs for talking with Meridio
meridioDocURL = None
if len(meridioServerPort) > 0:
    meridioDocURL = "%s://%s:%s%s" % (meridioProtocol,meridioServerName,meridioServerPort,documentLocation)
else:
    meridioDocURL = "%s://%s%s" % (meridioProtocol,meridioServerName,documentLocation)

meridioRecURL = None
if len(meridioServerPort) > 0:
    meridioRecURL = "%s://%s:%s%s" % (meridioProtocol,meridioServerName,meridioServerPort,recordLocation)
else:
    meridioRecURL = "%s://%s%s" % (meridioProtocol,meridioServerName,recordLocation)

meridioDomainUser = "%s\%s" % (meridioDomain,meridioUser)

# This makes a URL of the kind that will be ingested
def make_meridio_url(document_id):
    # Note: TestDocs results have protocol stripped off.
    if meridioServerPort == "":
        return "%s%s?launchMode=1&launchAs=0&documentId=%s" % (meridioServerName,webClientLocation,document_id)
    else:
        return "%s:%s/%s?launchMode=1&launchAs=0&documentId=%s" % (meridioServerName,meridioServerPort,webClientLocation,document_id)

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


# Main
if __name__ == '__main__':

    print "Precleaning!"
    preclean( print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    # Look for the last document
    id = lookup_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,docpath,"lf009-9-9-9")
    if id == None or id == "":
        # Presume that we have to set up the server
        print "Initializing test documents."
        # We need at least 10,000 documents.  We'll get this by
        # having 10 documents each with 1000 different names at the same level in the hierarchy.
        # We do it this way because we have no programmatic way of creating folders at this time.
        add_documents_multiple(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,docpath,"/root/largefiles","lf",3,10)
        print "Done creating test documents on server."

    # The documents are already on the Meridio box, under the LargeDocs folder.  We
    # just need exemplars of each one, so we can search for them.

    # We need at least 10,000 documents.  We'll get this by
    # having 10 documents each with 1000 different names at the same level in the hierarchy.
    # We do it this way because we have no programmatic way of creating folders at this time.
    doc_id_array = []
    level0 = 0
    while level0 < 10:
        meridio_title = "lf00%d-%d-%d-%d" % (level0,0,0,0)
        id = lookup_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,docpath,meridio_title)
        if id == None:
            raise Exception("Couldn't find %s in repository" % (meridio_title) )
        doc_id_array.append( id )
        level0 += 1

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["meridioConnector"], detect_gdms=True)


    # Set up the ingestion user.
    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )

    # PHASE 1: Ingestion


    print "Load Test."

    # In case there is clock skew, sleep a minute
    wait_for_meridio(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword)

    ConnectorHelpers.define_repositoryconnection( "MeridioConnection",
                                 "Meridio Connection",
                                 "com.metacarta.crawler.connectors.meridio.MeridioConnector",
                                 configparams = [ "DMWSServerProtocol="+meridioProtocol,
                                                  "DMWSServerName="+meridioServerName,
                                                  "DMWSLocation=/DMWS/MeridioDMWS.asmx",
                                                  "RMWSServerProtocol="+meridioProtocol,
                                                  "RMWSServerName="+meridioServerName,
                                                  "RMWSLocation=/RMWS/MeridioRMWS.asmx",
                                                  "MeridioWebClientProtocol="+meridioProtocol,
                                                  "MeridioWebClientServerName="+meridioServerName,
                                                  "MeridioWebClientDocDownloadLocation=/meridio/browse/downloadcontent.aspx",
                                                  "UserName="+meridioDomain+"\\"+meridioUser,
                                                  "Password="+meridioPassword ] )

    # Define job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><SearchPath path="/Testing/LargeDocs"/><SearchCategory category="MetacartaSearchable"/><MIMEType type="text/html"/><security value="off"/></specification>'
    job_id = ConnectorHelpers.define_job( "Meridio test job",
                             "MeridioConnection",
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
    ConnectorHelpers.search_exists_check( [ "divestment url:http://" + make_meridio_url(doc_id_array[0]) ], None, ConnectorHelpers.regexp_encode(make_meridio_url(doc_id_array[0])) )
    ConnectorHelpers.search_exists_check( [ "visitors url:http://" + make_meridio_url(doc_id_array[2]) ], None, ConnectorHelpers.regexp_encode(make_meridio_url(doc_id_array[2])) )

    # Success: done
    print "Done load test."

    # PHASE 5: Delete Job

    print "Job Delete Test."
    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    # Make sure the documents all went away
    ConnectorHelpers.search_nonexists_check( [ "divestment url:http://" + make_meridio_url(doc_id_array[0]) ], None, ConnectorHelpers.regexp_encode(make_meridio_url(doc_id_array[0])) )
    ConnectorHelpers.search_nonexists_check( [ "visitors url:http://" + make_meridio_url(doc_id_array[2]) ], None, ConnectorHelpers.regexp_encode(make_meridio_url(doc_id_array[2])) )

    print "Done Job Delete Test."

    ConnectorHelpers.delete_repositoryconnection( "MeridioConnection" )

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "MeridioConnector load tests PASSED"
