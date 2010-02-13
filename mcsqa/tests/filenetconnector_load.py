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

# Filenet constants
filenet_serverprotocol = "http"
filenet_serverhostname = "w2k3-fln-76-2.qa-ad-76.metacarta.com"
filenet_serverport = 9080
filenet_serverlocation = "wsi/FNCEWS40DIME"
filenet_urlprotocol = "http"
filenet_urlhostname = filenet_serverhostname
filenet_urlport = 9080
filenet_urllocation = "Workplace/Browse.jsp"
filenet_domain = "mcfn"
filenet_username = "Administrator"
filenet_password = "password"
filenet_objectstore = "MCOS"
filenet_documentclass = "Document"

# Calculate the filenet WSIURL
def build_wsi_uri(server_protocol=filenet_serverprotocol, server_hostname=filenet_serverhostname,
        server_port=filenet_serverport, server_location=filenet_serverlocation):
    uri = server_protocol + "://" + server_hostname
    if int(server_port) != 80 and server_protocol == "http":
        uri = uri + ":" + str(server_port)
    elif int(server_port) != 443 and server_protocol == "https":
        uri = uri + ":" + str(server_port)
    if server_location != None:
        uri = uri + "/" + server_location
    return uri

# Calculate the filenet search url, without the protocol part
def build_search_url_no_protocol(fnid, doc_class=filenet_documentclass,
    url_protocol=filenet_urlprotocol, url_hostname=filenet_urlhostname, url_port=filenet_urlport,
    url_location=filenet_urllocation, url_objectstore=filenet_objectstore):
    uri = url_hostname
    if int(url_port) != 80 and url_protocol == "http":
        uri = uri + ":" + str(url_port)
    elif int(url_port) != 443 and url_protocol == "https":
        uri = uri + ":" + str(url_port)
    if url_location != None:
        uri = uri + "/" + url_location
    uri = uri + "/getContent?objectStoreName=" + url_objectstore + "&id=" + fnid + "&element=0&objectType="+doc_class
    return uri

# Calculate the filenet search url, including the protocol
def build_search_url(fnid, doc_class=filenet_documentclass,
    url_protocol=filenet_urlprotocol, url_hostname=filenet_urlhostname, url_port=filenet_urlport,
    url_location=filenet_urllocation, url_objectstore=filenet_objectstore):
    return url_protocol + "://" + build_search_url_no_protocol(fnid,doc_class,url_protocol,url_hostname,url_port,url_location,url_objectstore)

# Invoke filenet-classpath java code
def execute_filenet_java( classname, argument_list=[], input=None ):
    """ Invoke filenet classpath java code """
    command_arguments = [ "filenet-testing-package/executejava", classname ] + argument_list
    return ConnectorHelpers.invoke_script(command_arguments,input=input)

# Method to add a document to filenet
def add_document(fntitle, file_name, wsiurl=build_wsi_uri(), fnuser=filenet_username, fnpassword=filenet_password,
        fndomain=filenet_domain, fnobjectstore=filenet_objectstore, fnfolder=None, docclass=filenet_documentclass):
    """Add a document to the repository"""
    listparams = [ ConnectorHelpers.process_argument(wsiurl),
            ConnectorHelpers.process_argument(fnuser),
            ConnectorHelpers.process_argument(fnpassword),
            ConnectorHelpers.process_argument(fndomain),
            ConnectorHelpers.process_argument(fnobjectstore),
            ConnectorHelpers.process_argument(fnfolder),
            ConnectorHelpers.process_argument(fntitle),
            ConnectorHelpers.process_argument(docclass),
            ConnectorHelpers.process_argument(file_name) ]
    return execute_filenet_java( "com.metacarta.crawler.connectors.filenet.AddDoc", argument_list=listparams )

# Method to lookup the id of a document in filenet
def lookup_document(fntitle, wsiurl=build_wsi_uri(), fnuser=filenet_username, fnpassword=filenet_password,
        fndomain=filenet_domain, fnobjectstore=filenet_objectstore, docclass=filenet_documentclass):
    """Lookup a document in filenet"""
    listparams = [ ConnectorHelpers.process_argument(wsiurl),
            ConnectorHelpers.process_argument(fnuser),
            ConnectorHelpers.process_argument(fnpassword),
            ConnectorHelpers.process_argument(fndomain),
            ConnectorHelpers.process_argument(fnobjectstore),
            ConnectorHelpers.process_argument(fntitle),
            ConnectorHelpers.process_argument(docclass) ]
    return execute_filenet_java( "com.metacarta.crawler.connectors.filenet.LookupDoc", argument_list=listparams )

# Method to remove a document from filenet
def remove_document(fntitle, wsiurl=build_wsi_uri(), fnuser=filenet_username, fnpassword=filenet_password,
        fndomain=filenet_domain, fnobjectstore=filenet_objectstore, docclass=filenet_documentclass):
    """Remove a document from filenet"""
    fnid = lookup_document(fntitle,wsiurl,fnuser,fnpassword,fndomain,fnobjectstore,docclass)
    if fnid != None and fnid != "":
        listparams = [ ConnectorHelpers.process_argument(wsiurl),
                ConnectorHelpers.process_argument(fnuser),
                ConnectorHelpers.process_argument(fnpassword),
                ConnectorHelpers.process_argument(fndomain),
                ConnectorHelpers.process_argument(fnobjectstore),
                ConnectorHelpers.process_argument(fnid) ]
        execute_filenet_java( "com.metacarta.crawler.connectors.filenet.RemoveDoc", argument_list=listparams )

# Method to update a document in the filenet repository
def version_document(fnid, fntitle, file_name, wsiurl=build_wsi_uri(), fnuser=filenet_username, fnpassword=filenet_password,
                fndomain=filenet_domain, fnobjectstore=filenet_objectstore):
    """Create a new version of an existing document"""
    listparams = [ ConnectorHelpers.process_argument(wsiurl),
            ConnectorHelpers.process_argument(fnuser),
            ConnectorHelpers.process_argument(fnpassword),
            ConnectorHelpers.process_argument(fndomain),
            ConnectorHelpers.process_argument(fnobjectstore),
            ConnectorHelpers.process_argument(fnid),
            ConnectorHelpers.process_argument(fntitle),
            ConnectorHelpers.process_argument(file_name) ]
    return execute_filenet_java( "com.metacarta.crawler.connectors.filenet.VersionDoc", argument_list=listparams )

# Method to outwait the time skew between connector framework box
# and filenet
def wait_for_filenet(wsiurl=build_wsi_uri(), fnuser=filenet_username, fnpassword=filenet_password,
        fndomain=filenet_domain, fnobjectstore=filenet_objectstore):
    """This will eventually create a temporary document, get its time, and wait until then"""
    time.sleep(60)

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

# Crawl user credentials
username = "testingest"
password = "testingest"

# Main
if __name__ == '__main__':

    print "Precleaning!"
    preclean( print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Creating UI user"

    # Look for the last document
    id = lookup_document("LargeDocs/lf009-9-9-9")
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
                        filenet_name = "LargeDocs/lf00%d-%d-%d-%d" % (level0,level1,level2,level3)
                        id = lookup_document(filenet_name)
                        if id == None or id == "":
                            id = add_document(filenet_name, path)
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
        filenet_name = "LargeDocs/lf00%d-%d-%d-%d" % (level0,0,0,0)
        id = lookup_document(filenet_name)
        if id == None:
            raise Exception("Couldn't find %s in repository" % (filenet_name) )
        doc_id_array.append( id )
        level0 += 1

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["filenetConnector"], detect_gdms=True)

    # Set up the ingestion user.
    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # PHASE 1: Ingestion


    print "Load Test."

    # In case there is clock skew, sleep a minute
    wait_for_filenet()

    ConnectorHelpers.define_repositoryconnection( "FilenetConnection",
                                 "Filenet Connection",
                                 "com.metacarta.crawler.connectors.filenet.FilenetConnector",
                                 configparams = [ "Password="+filenet_password,
                                                        "Server port="+str(filenet_serverport),
                                                        "Document URL hostname="+filenet_urlhostname,
                                                        "User ID="+filenet_username,
                                                        "Document URL location="+filenet_urllocation,
                                                        "Server hostname="+filenet_serverhostname,
                                                        "Filenet domain="+filenet_domain,
                                                        "Server WebServices location="+filenet_serverlocation,
                                                        "Server protocol="+filenet_serverprotocol,
                                                        "Document URL port="+str(filenet_serverport),
                                                        "Object store="+filenet_objectstore,
                                                        "Document URL protocol="+filenet_urlprotocol ] )

    # Define job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification>'+ \
                        ('<documentclass value="%s">'  % filenet_documentclass) + \
                        '<match fieldname="DocumentTitle" matchtype="LIKE" value="LargeDocs/%"/>' + \
                        '</documentclass><security value="off"/></specification>'
    job_id = ConnectorHelpers.define_job( "Filenet test job",
                             "FilenetConnection",
                             doc_spec_xml )
    # Run the job to completion
    ConnectorHelpers.start_job( job_id )

    # Fire up a parallel thread that attempts to shut down metacarta-agents, and times how long that takes
    response_string = ""
    mt = run_restart_thread(response_string)
    mt.start()
    mt.join()
    if len(response_string) > 0:
        raise Exception("Start/stop of metacarta-agents during load test failed: %s" % response_string)

    # Now, also figure out just how long it takes for the job to make it to "Running" status
    time_start = time.time()
    while True:
        # Wait 30 seconds
        time.sleep(30)
        # Check on the state of the job
        jobstatus = ConnectorHelpers.get_job_status_ui( username, password, job_id )
        if jobstatus != "Starting up":
            break
    time_end = time.time()
    # Should take less than a minute really, not > 30 minutes.
    if time_end-time_start > 120.0:
        raise Exception("Took more than 2 minutes to leave 'starting up' state (%f seconds)" % (time_end-time_start))

    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_exists_check( [ "divestment url:http://" + build_search_url_no_protocol(doc_id_array[0]) ], None, ConnectorHelpers.regexp_encode(build_search_url_no_protocol(doc_id_array[0])) )
    ConnectorHelpers.search_exists_check( [ "visitors url:http://" + build_search_url_no_protocol(doc_id_array[2]) ], None, ConnectorHelpers.regexp_encode(build_search_url_no_protocol(doc_id_array[2])) )

    # Success: done
    print "Done load test."

    # PHASE 5: Delete Job

    print "Job Delete Test."
    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    # Make sure the documents all went away
    ConnectorHelpers.search_nonexists_check( [ "divestment url:http://" + build_search_url_no_protocol(doc_id_array[0]) ], None, ConnectorHelpers.regexp_encode(build_search_url_no_protocol(doc_id_array[0])) )
    ConnectorHelpers.search_nonexists_check( [ "visitors url:http://" + build_search_url_no_protocol(doc_id_array[2]) ], None, ConnectorHelpers.regexp_encode(build_search_url_no_protocol(doc_id_array[2])) )

    print "Done Job Delete Test."

    ConnectorHelpers.delete_repositoryconnection( "FilenetConnection" )

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "FilenetConnector load tests PASSED"
