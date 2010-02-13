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

import re
import sys
import time
import ConnectorHelpers
import sqatools
from wintools import sqa_domain_info
from sqatools import LicenseMakerClient
import VirtualBrowser
import Javascript

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


# Copy a folder to a (new) area
def copy_folder( source, target ):
    ConnectorHelpers.invoke_root_script( [ "mkdir", "-p", target ] )
    ConnectorHelpers.invoke_root_script( [ "cp", "-r", source, target ] )

# Remove a folder
def delete_folder( target ):
    ConnectorHelpers.invoke_root_script( [ "rm", "-rf", target ] )

# Create a documentum repository connection via the UI
def define_filenet_repository_connection_ui( username,
                                        password,
                                        connection_name,
                                        connection_description,
                                        fln_domain=filenet_domain,
                                        fln_objectstore=filenet_objectstore,
                                        fln_userid=filenet_username,
                                        fln_password=filenet_password,
                                        fln_serverprotocol=filenet_serverprotocol,
                                        fln_serverhostname=filenet_serverhostname,
                                        fln_serverport=filenet_serverport,
                                        fln_serverlocation=None,
                                        fln_urlprotocol=filenet_urlprotocol,
                                        fln_urlhostname=filenet_urlhostname,
                                        fln_urlport=filenet_urlport,
                                        fln_urllocation=None ):
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
    window.check_no_match("[^\\.]Filenet")
    namefield = form.find_textarea("connname")
    descriptionfield = form.find_textarea("description")
    namefield.set_value( connection_name )
    descriptionfield.set_value( connection_description )

    # "Type" tab
    link = window.find_link("Type tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
    form = window.find_form("editconnection")
    connectortypefield = form.find_selectbox("classname")
    connectortypefield.select_value( "com.metacarta.crawler.connectors.filenet.FilenetConnector" )
    # Click the "Continue" button
    continue_button = window.find_button("Continue to next page")
    continue_button.click( )

    # "Server" tab
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
    link = window.find_link("Server tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
    form = window.find_form("editconnection")
    # Set server-specific stuff
    form.find_selectbox("serverprotocol").select_value( fln_serverprotocol )
    form.find_textarea("serverhostname").set_value( fln_serverhostname )
    if fln_serverport != None:
        form.find_textarea("serverport").set_value( str(fln_serverport) )
    if fln_serverlocation != None:
        form.find_textarea("serverwsilocation").set_value( fln_serverlocation )

    # "Document URL" tab
    link = window.find_link("Document URL tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
    form = window.find_form("editconnection")
    form.find_selectbox("urlprotocol").select_value( fln_urlprotocol )
    form.find_textarea("urlhostname").set_value( fln_urlhostname )
    if fln_urlport != None:
        form.find_textarea("urlport").set_value( str(fln_urlport) )
    if fln_urllocation != None:
        form.find_textarea("urllocation").set_value( fln_urllocation )

    # "Object Store" tab
    link = window.find_link("Object Store tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
    form = window.find_form("editconnection")
    form.find_textarea("filenetdomain").set_value( fln_domain )
    form.find_textarea("objectstore").set_value( fln_objectstore )

    # "Credentials" tab
    link = window.find_link("Credentials tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
    form = window.find_form("editconnection")
    form.find_textarea("userid").set_value( fln_userid )
    form.find_textarea("password").set_value( fln_password )

    # Now, save this page
    save_button = window.find_button("Save this connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")

# Define a standard livelink job using the UI
def define_filenet_job_ui( username,
                password,
                job_name,
                connection_name,
                doctypes=[ { "name" : filenet_documentclass, "allmetadata" : False, "metadatalist" : [ ], "matchlist" : [ ( "DocumentTitle", "LIKE", "TestDocs/%" ) ] } ],
                mimetypes=None,
                security_enabled=False,
                collection_name=None,
                type="specified",
                startmethod="windowbegin",
                recrawlinterval=0 ):
    """connection_name is the name of the filenet connection.  doctypes is an array of dictionaries,
       each with the following possible members:
           name = the name of the document type to include
           allmetadata = True if all metadata for the document type should be included
           metadatalist = an array of metadata names that should be included for the
                document type
       mimetypes is the array of content types to exclude, or None if ALL indexable content
       types should be included.
       security_enabled is True if security should be enabled.
       Legal values for type are: "specified" or "continuous"
       Legal values for start method are: "windowbegin", "windowinside", or "disable".
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
    window.check_no_match("[^\\.]Filenet")
    # textarea for setting description
    form.find_textarea("description").set_value( job_name )

    # "Connection" tab
    link = window.find_link("Connection tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
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
    form.find_selectbox("outputname").select_value( "GTS" )
    # Click the "Continue" button
    window.find_button("Continue to next screen").click( )
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
    form = window.find_form("editjob")

    # "Collections" tab
    link = window.find_link("Collections tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
    form = window.find_form("editjob")
    # textarea for setting collection
    if collection_name != None:
        form.find_textarea("gts_collectionname").set_value( collection_name )

    # "Scheduling" tab
    link = window.find_link("Scheduling tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
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
        form.find_textarea("recrawlinterval").set_value( str(recrawlinterval * 1000 * 60) )

    # "Document Classes" tab
    link = window.find_link("Document Classes tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
    form = window.find_form("editjob")
    for doctype in doctypes:
        doctypename = doctype["name"]
        doctypeallmetadata = doctype["allmetadata"]
        doctypefields = doctype["metadatalist"]
        doctypematches = doctype["matchlist"]
        form.find_checkbox("documentclasses",doctypename).select()
        if doctypeallmetadata == True:
            form.find_checkbox("allmetadata_"+doctypename,"true").select()
        else:
            for field in doctypefields:
                form.find_selectbox("metadatafield_"+doctypename).multi_select_value(field)
        # Set up matches
        for match in doctypematches:
            (field,type,value) = match
            form.find_selectbox("matchfield_"+doctypename).select_value(field)
            form.find_selectbox("matchtype_"+doctypename).select_value(type)
            form.find_textarea("matchvalue_"+doctypename).set_value(str(value))
            window.find_button("Add match for "+doctypename).click( )
            window = vb.find_window("")
            window.check_no_match("[^\\.]Filenet")
            form = window.find_form("editjob")

    # "Mime Types" tab
    link = window.find_link("Mime Types tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
    form = window.find_form("editjob")
    if mimetypes != None:
        for mimetype in mimetypes:
            # Multiselect is a toggle, so this will turn off a value already set
            form.find_selectbox("mimetypes").multi_select_value(mimetype)

    # "Security" tab
    link = window.find_link("Security tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
    form = window.find_form("editjob")
    # Set up security
    if security_enabled:
        form.find_radiobutton("specsecurity","on").select()
    else:
        form.find_radiobutton("specsecurity","off").select()

    # Finally, submit the form
    window.find_button("Save this job").click( )
    window = vb.find_window("")
    window.check_no_match("[^\\.]Filenet")
    jobid = window.find_match("<!--jobid=(.*)-->",1)
    return jobid

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

# Method to remove a document from filenet
def remove_document(fntitle, wsiurl=build_wsi_uri(), fnuser=filenet_username, fnpassword=filenet_password,
        fndomain=filenet_domain, fnobjectstore=filenet_objectstore, docclass=filenet_documentclass):
    """Remove a document from filenet"""
    listparams = [ ConnectorHelpers.process_argument(wsiurl),
            ConnectorHelpers.process_argument(fnuser),
            ConnectorHelpers.process_argument(fnpassword),
            ConnectorHelpers.process_argument(fndomain),
            ConnectorHelpers.process_argument(fnobjectstore),
            ConnectorHelpers.process_argument(fntitle),
            ConnectorHelpers.process_argument(docclass) ]
    fnid = execute_filenet_java( "com.metacarta.crawler.connectors.filenet.LookupDoc", argument_list=listparams )
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

# Method to set a document's content access privs
def set_doc_access(fnid, access_list, wsiurl=build_wsi_uri(), fnuser=filenet_username, fnpassword=filenet_password,
                fndomain=filenet_domain, fnobjectstore=filenet_objectstore):
    """ Set document access privs according to access_list.  access_list contains an array of
        LDAP user names, or "#AUTHENTICATED_USER". """
    listparams = [ ConnectorHelpers.process_argument(wsiurl),
            ConnectorHelpers.process_argument(fnuser),
            ConnectorHelpers.process_argument(fnpassword),
            ConnectorHelpers.process_argument(fndomain),
            ConnectorHelpers.process_argument(fnobjectstore),
            ConnectorHelpers.process_argument(fnid),
            ConnectorHelpers.process_argument("Administrator") ]
    for access_item in access_list:
        listparams += [ access_item ]
    execute_filenet_java( "com.metacarta.crawler.connectors.filenet.SetDocPermission", argument_list=listparams )

# Method to outwait the time skew between connector framework box
# and filenet
def wait_for_filenet(wsiurl=build_wsi_uri(), fnuser=filenet_username, fnpassword=filenet_password,
        fndomain=filenet_domain, fnobjectstore=filenet_objectstore):
    """This will eventually create a temporary document, get its time, and wait until then"""
    time.sleep(60)


# Crawl user credentials
username = "testingest"
password = "testingest"

def preclean( ad_domain_info, perform_legacy_pass, print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    # First order of business: synchronize legacy mode, so our state matches the duck state
    ConnectorHelpers.synchronize_legacy_mode( )

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

    # Remove test documents first
    delete_docs = [ "TestDocs/f001", "TestDocs/f002", "TestDocs/f003", "TestDocs/f004",
                "TestDocs/f005", "TestDocs/f006", "TestDocs/f007", "TestDocs/f008",
                "TestDocs/f009", "TestDocs/f010", "TestDocs/userafolder/f003",
                "TestDocs/userafolder/f004", "TestDocs/userbfolder/f005",
                "TestDocs/userbfolder/f006", "TestDocs/usercfolder/f007",
                "TestDocs/newfolder/f008", "TestDocs/f002", "TestDocs/f004" ]

    for file in delete_docs:
        try:
            remove_document(file)
            pass
        except Exception, e:
            if print_errors:
                print "Error deleting document %s" % file
                print e

    # Disable ad
    try:
        ConnectorHelpers.turn_off_ad( ad_domain_info )
    except Exception, e:
        if print_errors:
            print "Error disabling AD"
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

    if perform_legacy_pass:
        try:
            ConnectorHelpers.select_legacy_mode(use_legacy_tools=False)
        except Exception, e:
            if print_errors:
                print "Error turning off legacy AD mode"
                print e

def run_ad_test_part( ad_domain_info ):
    """ Do the ad-specific part of the test.  This may be run twice if a legacy test is being requested. """

    # PHASE 6: Ingestion with AD on and security enabled

    ad_win_host = ad_domain_info.ie_client_fqdn

    # Set up system to use ad
    ConnectorHelpers.configure_ad( ad_domain_info )

    # Create ad users, if they don't already exist.  This method can ONLY be used after join!!
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_filenetusera", "usera" )
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_filenetuserb", "userb" )
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_filenetuserc", "userc" )

    # Enable CF security again
    ConnectorHelpers.enable_connector_framework( )

    # These documents are available to everyone...
    id1 = add_document("TestDocs/f001", "/root/crawlarea/testfiles/f001.txt")
    id2 = add_document("TestDocs/f002", "/root/crawlarea/testfiles/f002.txt")
    set_doc_access(id1,[ "p_filenetusera", "p_filenetuserb", "p_filenetuserc" ])
    set_doc_access(id2,[ "p_filenetusera", "p_filenetuserb", "p_filenetuserc" ])

    # TestDocs/userafolder/* will be visible by filenetusera only
    id3 = add_document("TestDocs/userafolder/f003", "/root/crawlarea/testfiles/f003.txt")
    id4 = add_document("TestDocs/userafolder/f004", "/root/crawlarea/testfiles/f004.txt")
    set_doc_access(id3,[ "p_filenetusera" ])
    set_doc_access(id4,[ "p_filenetusera" ])

    # TestDocs/userbfolder/* will be visible by filenetuserb only
    id5 = add_document("TestDocs/userbfolder/f005", "/root/crawlarea/testfiles/f005.txt")
    id6 = add_document("TestDocs/userbfolder/f006", "/root/crawlarea/testfiles/f006.txt")
    set_doc_access(id5,[ "p_filenetuserb" ])
    set_doc_access(id6,[ "p_filenetuserb" ])

    # TestDocs/usercfolder/* will be visible by filenetuserc only
    id7 = add_document("TestDocs/usercfolder/f007", "/root/crawlarea/testfiles/f007.txt")
    set_doc_access(id7,[ "p_filenetuserc" ])

    # TestDocs/newfolder/* will be visible by both filenetusera and filenetuserb, but not filenetuserc
    id8 = add_document("TestDocs/newfolder/f008", "/root/crawlarea/testfiles/newfolder/f008.txt")
    set_doc_access(id8,[ "p_filenetusera", "p_filenetuserb" ])

    # Define repository connection
    define_filenet_repository_connection_ui( username,
                                        password,
                                        "FileNetConnection",
                                        "FileNet Connection" )

    # Define job
    job_id = define_filenet_job_ui( username,
                password,
                "FileNet test job",
                "FileNetConnection",
                security_enabled=True )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Check for proper visibility of all documents
    # usera
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_search_url_no_protocol(id1) ], username="p_filenetusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_search_url_no_protocol(id3) ], username="p_filenetusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="p_filenetusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="p_filenetusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="p_filenetusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ build_search_url_no_protocol(id8) ], username="p_filenetusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_search_url_no_protocol(id2) ], username="p_filenetusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_search_url_no_protocol(id4) ], username="p_filenetusera", password="usera", win_host=ad_win_host )

    # userb
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_search_url_no_protocol(id1) ], username="p_filenetuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="p_filenetuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_search_url_no_protocol(id5) ], username="p_filenetuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_search_url_no_protocol(id6) ], username="p_filenetuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="p_filenetuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ build_search_url_no_protocol(id8) ], username="p_filenetuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_search_url_no_protocol(id2) ], username="p_filenetuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="p_filenetuserb", password="userb", win_host=ad_win_host )

    # userc
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_search_url_no_protocol(id1) ], username="p_filenetuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="p_filenetuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="p_filenetuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="p_filenetuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_search_url_no_protocol(id7) ], username="p_filenetuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ ], username="p_filenetuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_search_url_no_protocol(id2) ], username="p_filenetuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="p_filenetuserc", password="userc", win_host=ad_win_host )

    # Search using metadata too
    # MHL, when I get the rest to work

    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    # Get rid of the connections
    ConnectorHelpers.delete_repository_connection_ui( username, password, "FileNetConnection" )

    # Clean up documents
    delete_docs = [ "TestDocs/f001", "TestDocs/f002", "TestDocs/userafolder/f003",
                "TestDocs/userafolder/f004", "TestDocs/userbfolder/f005",
                "TestDocs/userbfolder/f006", "TestDocs/usercfolder/f007", "TestDocs/newfolder/f008" ]
    for file in delete_docs:
        remove_document(file)

    ConnectorHelpers.turn_off_ad( ad_domain_info )

# Main
if __name__ == '__main__':

    # AD parameters
    ad_group = "76"
    if len(sys.argv) > 1:
        ad_group = sys.argv[1]
    perform_legacy_pass = False
    if len(sys.argv) > 2 and sys.argv[2] == "legacy":
        perform_legacy_pass = True

    ad_domain_info = sqa_domain_info.SQADomainInfo( ad_group )

    print "Precleaning!"
    preclean( ad_domain_info, perform_legacy_pass, print_errors=False )


    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up file area."
    copy_folder("/root/testfiles","/root/crawlarea")

    # Enable CF security
    ConnectorHelpers.enable_connector_framework( )

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["filenetConnector"], detect_gdms=True)

    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # PHASE 1: Ingestion


    print "Ingestion Test."

    # Add the docs to the repository
    id1 = add_document("TestDocs/f001", "/root/crawlarea/testfiles/f001.txt")
    id2 = add_document("TestDocs/f002", "/root/crawlarea/testfiles/f002.txt")
    id3 = add_document("TestDocs/f003", "/root/crawlarea/testfiles/f003.txt")
    id4 = add_document("TestDocs/f004", "/root/crawlarea/testfiles/f004.txt")
    id5 = add_document("TestDocs/f005", "/root/crawlarea/testfiles/f005.txt")
    id6 = add_document("TestDocs/f006", "/root/crawlarea/testfiles/f006.txt")
    id7 = add_document("TestDocs/f007", "/root/crawlarea/testfiles/f007.txt")
    id8 = add_document("TestDocs/newfolder/f008", "/root/crawlarea/testfiles/newfolder/f008.txt")
    # In case there is clock skew, sleep a minute
    wait_for_filenet()

    # Define repository connection
    define_filenet_repository_connection_ui( username,
                                        password,
                                        "FileNetConnection",
                                        "FileNet Connection" )

    # Define job
    job_id = define_filenet_job_ui( username,
                password,
                "FileNet test job",
                "FileNetConnection" )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_search_url_no_protocol(id1) ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_search_url_no_protocol(id2) ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_search_url_no_protocol(id3) ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_search_url_no_protocol(id4) ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_search_url_no_protocol(id5) ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_search_url_no_protocol(id6) ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_search_url_no_protocol(id7) ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ build_search_url_no_protocol(id8) ] )

    # Metadata testing will be included here, later
    # MHL

    # Success: done
    print "Done ingestion test."


    # PHASE 2: Document Change Detection

    print "Document Change Test."
    o = open( "/root/crawlarea/testfiles/f002a.txt", "w" )
    o.write("Now this document is at 50N 75E, and the keyword is platypus")
    o.close()
    o = open( "/root/crawlarea/testfiles/f004a.txt", "w" )
    o.write("No longer about drinking establishments at 23N 15W")
    o.close()
    # Modify documents
    id2a = version_document(id2, "TestDocs/f002", "/root/crawlarea/testfiles/f002a.txt")
    id4a = version_document(id4, "TestDocs/f004", "/root/crawlarea/testfiles/f004a.txt")
    # Sleep, in case there's clock skew
    wait_for_filenet()

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Look for state of index being right
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_search_url_no_protocol(id1) ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_search_url_no_protocol(id3) ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_search_url_no_protocol(id5) ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_search_url_no_protocol(id6) ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_search_url_no_protocol(id7) ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ build_search_url_no_protocol(id8) ] )
    ConnectorHelpers.search_check( [ "platypus" ], None, [ build_search_url_no_protocol(id2a) ] )
    ConnectorHelpers.search_check( [ "establishments" ], None, [ build_search_url_no_protocol(id4a) ] )

    print "Done Document Change Test."

    # PHASE 3: Document Delete Detection

    print "Document Delete Test."
    remove_document("TestDocs/f003" )
    remove_document("TestDocs/f005" )
    # Sleep, in case of clock skew
    wait_for_filenet()

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
    id9 = add_document("TestDocs/f009", "/root/crawlarea/testfiles/f009.txt")
    id10 = add_document("TestDocs/f010", "/root/crawlarea/testfiles/f010.txt")
    wait_for_filenet()

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    ConnectorHelpers.search_check( [ "albemarle" ], None, [ build_search_url_no_protocol(id9) ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [ build_search_url_no_protocol(id10) ] )
    print "Done Document Add Test."

    # PHASE 5: Delete Job

    print "Job Delete Test."
    ConnectorHelpers.delete_job( job_id )
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
    print "Done Job Delete Test."

    ConnectorHelpers.delete_repository_connection_ui( username, password, "FileNetConnection" )
    #ConnectorHelpers.delete_repositoryconnection( "DocumentumConnection" )

    # Delete the documents we put into Documentum that are still there
    delete_docs = [ "TestDocs/f001", "TestDocs/f002", "TestDocs/f004", "TestDocs/f006", "TestDocs/f007",
                    "TestDocs/newfolder/f008", "TestDocs/f009", "TestDocs/f010", "TestDocs/f002", "TestDocs/f004" ]
    for file in delete_docs:
        remove_document(file)

    # Do the ad-specific part of the test
    run_ad_test_part( ad_domain_info )
    if perform_legacy_pass:
        ConnectorHelpers.select_legacy_mode()
        run_ad_test_part( ad_domain_info )
        ConnectorHelpers.cancel_legacy_mode()

    # Clean up crawl user
    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    # Clean up temporary folder
    delete_folder("/root/crawlarea")

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "Basic FileNetConnector tests PASSED"
