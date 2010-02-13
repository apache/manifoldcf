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

# Copy a folder to a (new) area
def copy_folder( source, target ):
    ConnectorHelpers.invoke_root_script( [ "mkdir", "-p", target ] )
    ConnectorHelpers.invoke_root_script( [ "cp", "-r", source, target ] )

# Remove a folder
def delete_folder( target ):
    ConnectorHelpers.invoke_root_script( [ "rm", "-rf", target ] )

# Method to initialize docbroker reference
def initialize(server):
    """ Initialize DFC on the appliance """
    listparams = [ "/usr/bin/metacarta-setupdocumentum",
            ConnectorHelpers.process_argument(server) ]
    return ConnectorHelpers.invoke_root_script( listparams )

# Create a documentum repository connection via the UI
def define_documentum_repository_connection_ui( username,
                                        password,
                                        connection_name,
                                        connection_description,
                                        dctm_docbase,
                                        dctm_username,
                                        dctm_password,
                                        dctm_domain,
                                        dctm_webtop_url,
                                        authority_name=None ):
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
    connectortypefield.select_value( "com.metacarta.crawler.connectors.DCTM.DCTM" )
    if authority_name != None:
        form.find_selectbox("authorityname").select_value( authority_name )
    # Click the "Continue" button
    continue_button = window.find_button("Continue to next page")
    continue_button.click( )

    # "Docbase" tab
    window = vb.find_window("")
    link = window.find_link("Docbase tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    # Set docbase-specific stuff
    form.find_textarea("docbasename").set_value( dctm_docbase )
    form.find_textarea("docbaseusername").set_value( dctm_username )
    form.find_textarea("docbasepassword").set_value( dctm_password )
    form.find_textarea("docbasedomain").set_value( dctm_domain )

    # "Webtop" tab
    link = window.find_link("Webtop tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    form.find_textarea("webtopbaseurl").set_value(dctm_webtop_url)

    # Now, save this page
    save_button = window.find_button("Save this connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")

# Create a documentum authority connection via the UI
def define_documentum_authority_connection_ui( username,
                                        password,
                                        connection_name,
                                        connection_description,
                                        dctm_docbase,
                                        dctm_username,
                                        dctm_password,
                                        dctm_domain ):
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for repository connection management and click it
    window = vb.find_window("")
    link = window.find_link("List authorities")
    link.click( )

    # Click "add a connection"
    window = vb.find_window("")
    link = window.find_link("Add new connection")
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
    connectortypefield.select_value( "com.metacarta.crawler.authorities.DCTM.AuthorityConnector" )
    # Click the "Continue" button
    continue_button = window.find_button("Continue to next page")
    continue_button.click( )

    # "Docbase" tab
    window = vb.find_window("")
    link = window.find_link("Docbase tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    # Set docbase-specific stuff
    form.find_textarea("docbasename").set_value( dctm_docbase )
    form.find_textarea("docbaseusername").set_value( dctm_username )
    form.find_textarea("docbasepassword").set_value( dctm_password )
    form.find_textarea("docbasedomain").set_value( dctm_domain )

    # Now, save this page
    save_button = window.find_button("Save this authority connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")

# Define a standard livelink job using the UI
def define_documentum_job_ui( username,
                password,
                job_name,
                connection_name,
                folders,
                doctypes=[ { "name" : "dm_document", "allmetadata" : False, "metadatalist" : [ ] } ],
                contenttypes=None,
                maxcontentlength=None,
                security_enabled=False,
                path_value_attribute=None,
                mappings=None,
                collection_name=None,
                type="specified",
                startmethod="windowbegin",
                recrawlinterval=0 ):
    """connection_name is the name of the documentum connection.  folders is an array of
       folder path arrays.  doctypes is an array of dictionaries, each with the following
       possible members:
           name = the name of the document type to include
           allmetadata = True if all metadata for the document type should be included
           metadatalist = an array of metadata names that should be included for the
                document type
       contenttypes is the array of content types to exclude, or None if ALL indexable content
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
    form.find_selectbox("outputname").select_value( "GTS" )
    # Click the "Continue" button
    window.find_button("Continue to next screen").click( )
    window = vb.find_window("")
    form = window.find_form("editjob")

    # "Collections" tab
    link = window.find_link("Collections tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # textarea for setting collection
    if collection_name != None:
        form.find_textarea("gts_collectionname").set_value( collection_name )

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
        form.find_textarea("recrawlinterval").set_value( str(recrawlinterval * 1000 * 60) )

    # "Paths" tab
    link = window.find_link("Paths tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # Now, set up paths and matches
    for pathelement in folders:
        # Get everything ready to click the "Add" button
        for selectionelement in pathelement:
            form.find_selectbox("pathaddon").select_value(selectionelement)
            window.find_button("Add to path").click()
            window = vb.find_window("")
            form = window.find_form("editjob")
        # Now, click the "Add" button
        window.find_button("Add path").click()
        window = vb.find_window("")
        form = window.find_form("editjob")

    # "Document Types" tab
    link = window.find_link("Document Types tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    for doctype in doctypes:
        doctypename = doctype["name"]
        doctypeallmetadata = doctype["allmetadata"]
        doctypefields = doctype["metadatalist"]
        form.find_checkbox("specfiletype",doctypename).select()
        if doctypeallmetadata == True:
            form.find_checkbox("specfileallattrs_"+doctypename,"true").select()
        else:
            for field in doctypefields:
                form.find_selectbox("specfileattrs_"+doctypename).select_value(field)

    # "Content Types" tab
    link = window.find_link("Content Types tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if contenttypes != None:
        for contenttype in contenttypes:
            form.find_checkbox("specmimetype",contenttype).deselect()

    # "Content Length" tab
    link = window.find_link("Content Length tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if maxcontentlength != None:
        form.find_textarea("specmaxdoclength").set_value(str(maxcontentlength))

    # "Security" tab
    link = window.find_link("Security tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # Set up security
    if security_enabled:
        form.find_radiobutton("specsecurity","on").select()
    else:
        form.find_radiobutton("specsecurity","off").select()

    # "Path Metadata" tab
    link = window.find_link("Path Metadata tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # Set up path value attribute
    if path_value_attribute != None:
        form.find_textarea("specpathnameattribute").set_value( path_value_attribute )
    # Set up mappings
    if mappings != None:
        for mappingelement in mappings:
            match, replace = mappingelement
            form.find_textarea("specmatch").set_value(match)
            form.find_textarea("specreplace").set_value(replace)
            window.find_button("Add to mappings").click()
            window = vb.find_window("")
            form = window.find_form("editjob")

    # Finally, submit the form
    window.find_button("Save this job").click( )
    window = vb.find_window("")
    jobid = window.find_match("<!--jobid=(.*)-->",1)
    return jobid

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

# Method to check for packaging problems for a given package
def check_package( package_name ):
    """ This method calls debsums -c to look for unanticipated changes in files.
        It is similar to the code in the check_system_health plugin that does this
        task also. """
    listparams = [ "/usr/bin/debsums", "-c", ConnectorHelpers.process_argument(package_name) ]
    rval = ConnectorHelpers.invoke_root_script( listparams )
    if rval != "":
        raise Exception("Package %s seems to have changed, specifically these files: %s" % ( package_name, rval ) )

# Documentum constants
documentum_docbase = "Metacarta1"
documentum_username = "dmadmin"
documentum_password = "password"
documentum_domain = ""

# No protocol base url
veryBaseURL = "dctmsrvr.qa-ad-76.metacarta.com:8080/webtop/"
# Base URL
baseURL = "http://"+veryBaseURL
# Full URL
fullURL = veryBaseURL + "component/drl?versionLabel=CURRENT&objectId="

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
    delete_docs = [ ("/TestDocs","f001.txt"), ("/TestDocs","f002.txt"), ("/TestDOcs","f003.txt"), ("/TestDocs","f004.txt"),
                ("/TestDocs","f005.txt"), ("/TestDocs","f006.txt"), ("/TestDocs","f007.txt"), ("/TestDocs","f008.txt"),
                ("/TestDocs","f009.txt"), ("/TestDocs","f010.txt"), ("/TestDocs/userafolder","f003.txt"),
                ("/TestDocs/userafolder","f004.txt"), ("/TestDocs/userbfolder","f005.txt"),
                ("/TestDocs/userbfolder","f006.txt"), ("/TestDocs/usercfolder","f007.txt"), ("/TestDocs/newfolder","f008.txt") ]

    for folder,file in delete_docs:
        try:
            remove_document(documentum_docbase,documentum_domain,documentum_username,documentum_password,folder,file)
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

    ad_win_host = ad_domain_info.ie_client_fqdn

    # PHASE 6: Ingestion with AD on and security enabled

    # Set up system to use ad
    ConnectorHelpers.configure_ad( ad_domain_info )

    # Enable CF security again
    ConnectorHelpers.enable_connector_framework( )

    # Create users that correspond to the already-existing Documentum users
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_documentumusera", "usera" )
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_documentumuserb", "userb" )
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_documentumuserc", "userc" )

    # Add the current set of docs to the repository, under the right folders.  This will cause the right
    # security to be inherited.

    # These documents are available to everyone...
    id1 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f001.txt", "/root/crawlarea/testfiles/f001.txt")
    id2 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f002.txt", "/root/crawlarea/testfiles/f002.txt")

    # TestDocs/userafolder will be visible by documentumusera only
    id3 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs/userafolder", "f003.txt", "/root/crawlarea/testfiles/f003.txt")
    id4 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs/userafolder", "f004.txt", "/root/crawlarea/testfiles/f004.txt")

    # TestDocs/userbfolder will be visible by documentumuserb only
    id5 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs/userbfolder", "f005.txt", "/root/crawlarea/testfiles/f005.txt")
    id6 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs/userbfolder", "f006.txt", "/root/crawlarea/testfiles/f006.txt")

    # TestDocs/usercfolder will be visible by documentumuserc only
    id7 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs/usercfolder", "f007.txt", "/root/crawlarea/testfiles/f007.txt")

    # TestDocs/newfolder will be visible by both documentumusera and documentumuserb, but not documentumuserc
    id8 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs/newfolder", "f008.txt", "/root/crawlarea/testfiles/newfolder/f008.txt")

    # After something is changed, we always have to wait
    wait_for_documentum(documentum_docbase, documentum_domain, documentum_username, documentum_password)

    # Define authority connection
    define_documentum_authority_connection_ui( username,
                                        password,
                                        "DocumentumAuthority",
                                        "Documentum Authority",
                                        documentum_docbase,
                                        documentum_username,
                                        documentum_password,
                                        documentum_domain )

    # Define repository connection
    define_documentum_repository_connection_ui( username,
                                        password,
                                        "DocumentumConnection",
                                        "Documentum Connection",
                                        documentum_docbase,
                                        documentum_username,
                                        documentum_password,
                                        documentum_domain,
                                        baseURL,
                                        authority_name="DocumentumAuthority")

    # Define job
    job_id = define_documentum_job_ui( username,
                password,
                "Documentum test job",
                "DocumentumConnection",
                [ [ "TestDocs" ] ],
                [ { "name" : "dm_document", "allmetadata" : False, "metadatalist" : [ "r_creator_name" ] } ],
                security_enabled=True )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Check for proper visibility of all documents
    # usera
    ConnectorHelpers.search_check( [ "reference" ], None, [ fullURL +id1 ], username="p_documentumusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ fullURL +id3 ], username="p_documentumusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="p_documentumusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="p_documentumusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="p_documentumusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ fullURL +id8 ], username="p_documentumusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ fullURL +id2 ], username="p_documentumusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ fullURL +id4 ], username="p_documentumusera", password="usera", win_host=ad_win_host )

    # userb
    ConnectorHelpers.search_check( [ "reference" ], None, [ fullURL +id1 ], username="p_documentumuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="p_documentumuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ fullURL +id5 ], username="p_documentumuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ fullURL +id6 ], username="p_documentumuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="p_documentumuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ fullURL +id8 ], username="p_documentumuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ fullURL +id2 ], username="p_documentumuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="p_documentumuserb", password="userb", win_host=ad_win_host )

    # userc
    ConnectorHelpers.search_check( [ "reference" ], None, [ fullURL +id1 ], username="p_documentumuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="p_documentumuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="p_documentumuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="p_documentumuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ fullURL +id7 ], username="p_documentumuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ ], username="p_documentumuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ fullURL +id2 ], username="p_documentumuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="p_documentumuserc", password="userc", win_host=ad_win_host )

    # Search using metadata too
    ConnectorHelpers.search_check( [ "good", "metadata:r_creator_name="+documentum_username ], None, [ fullURL+id2 ], username="p_documentumuserc", password="userc", win_host=ad_win_host )

    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    # Get rid of the connections
    ConnectorHelpers.delete_repository_connection_ui( username, password, "DocumentumConnection" )
    ConnectorHelpers.delete_authority_connection_ui( username, password, "DocumentumAuthority" )

    # Clean up documents
    delete_docs = [ ("/TestDocs","f001.txt"), ("/TestDocs","f002.txt"), ("/TestDocs/userafolder","f003.txt"),
                ("/TestDocs/userafolder","f004.txt"), ("/TestDocs/userbfolder","f005.txt"),
                ("/TestDocs/userbfolder","f006.txt"), ("/TestDocs/usercfolder","f007.txt"), ("/TestDocs/newfolder","f008.txt") ]
    for folder,file in delete_docs:
        remove_document(documentum_docbase,documentum_domain,documentum_username,documentum_password,folder,file)

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

    print "Initializing to point to server"
    initialize( "dctmsrvr.qa-ad-76.metacarta.com" )

    print "Precleaning!"
    preclean( ad_domain_info, perform_legacy_pass, print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up file area."
    copy_folder("/root/testfiles","/root/crawlarea")

    # Enable CF security
    ConnectorHelpers.enable_connector_framework( )

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["documentumConnector"], detect_gdms=True)

    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )

    # Check the error handling for bad docbase name
    print "Checking for proper error for bad docbase name."
    
    # Create the connection
    define_documentum_repository_connection_ui( username,
                                        password,
                                        "DocumentumConnection",
                                        "Documentum Connection",
                                        "mumblefrats",
                                        documentum_username,
                                        documentum_password,
                                        documentum_domain,
                                        baseURL )

    # View the connection to make sure the right error is returned
    ConnectorHelpers.view_repository_connection_ui( username, password, "DocumentumConnection", match_string="DM_DOCBROKER_E_NO_SERVERS_FOR_DOCBASE" )
    
    # Delete the connection
    ConnectorHelpers.delete_repository_connection_ui( username, password, "DocumentumConnection" )
    
    # Check the error handling for invalid credentials
    print "Checking for proper error for invalid credentials."

    # Create the connection
    define_documentum_repository_connection_ui( username,
                                        password,
                                        "DocumentumConnection",
                                        "Documentum Connection",
                                        documentum_docbase,
                                        documentum_username,
                                        "mumblefrats",
                                        documentum_domain,
                                        baseURL )

    # View the connection to make sure the right error is returned
    ConnectorHelpers.view_repository_connection_ui( username, password, "DocumentumConnection", match_string="DM_SESSION_E_AUTH_FAIL" )
    
    # Delete the connection
    ConnectorHelpers.delete_repository_connection_ui( username, password, "DocumentumConnection" )

    
    # PHASE 1: Ingestion
    print "Ingestion Test."

    # Add the docs to the repository
    id1 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f001.txt", "/root/crawlarea/testfiles/f001.txt")
    id2 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f002.txt", "/root/crawlarea/testfiles/f002.txt")
    id3 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f003.txt", "/root/crawlarea/testfiles/f003.txt")
    id4 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f004.txt", "/root/crawlarea/testfiles/f004.txt")
    id5 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f005.txt", "/root/crawlarea/testfiles/f005.txt")
    id6 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f006.txt", "/root/crawlarea/testfiles/f006.txt")
    id7 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f007.txt", "/root/crawlarea/testfiles/f007.txt")
    id8 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f008.txt", "/root/crawlarea/testfiles/newfolder/f008.txt")
    # In case there is clock skew, sleep a minute
    wait_for_documentum(documentum_docbase, documentum_domain, documentum_username, documentum_password)

    # Define repository connection
    define_documentum_repository_connection_ui( username,
                                        password,
                                        "DocumentumConnection",
                                        "Documentum Connection",
                                        documentum_docbase,
                                        documentum_username,
                                        documentum_password,
                                        documentum_domain,
                                        baseURL )

    #ConnectorHelpers.define_repositoryconnection( "DocumentumConnection",
    #                            "Documentum Connection",
    #                            "com.metacarta.crawler.connectors.DCTM.DCTM",
    #                            configparams = [ "docbasename="+documentum_docbase,
    #                                             "docbaseusername="+documentum_username,
    #                                             "docbasepassword="+documentum_password,
    #                                             "domain="+documentum_domain,
    #                                             "webtopbaseurl="+baseURL ] )

    print "Looking for proper error for illegal mapping regular expression"
    
    job_id = define_documentum_job_ui( username,
                password,
                "Documentum test job",
                "DocumentumConnection",
                [ [ "TestDocs" ] ],
                [ { "name" : "dm_document", "allmetadata" : True, "metadatalist" : [ ] } ],
                path_value_attribute="doc_path",
                mappings=[ ( "\\E$", "$(0)") ] )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Check they aborted
    result_list = ConnectorHelpers.list_job_statuses_api( )
    if len(result_list) != 1:
        raise Exception("Expected one job, found %d" % len(result_list))
    for result in result_list:
        if result["status"] != "error":
            raise Exception("Expected job to be error, found status %s instead" % result["status"])
    
    # Clean up
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    print "Running ingestion job"
    
    # Define job
    job_id = define_documentum_job_ui( username,
                password,
                "Documentum test job",
                "DocumentumConnection",
                [ [ "TestDocs" ] ],
                [ { "name" : "dm_document", "allmetadata" : True, "metadatalist" : [ ] } ],
                path_value_attribute="doc_path" )

    #doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><docbaselocation path="/TestDocs"/><security value="off"/><objecttype token="dm_document"/></specification>'
    #job_id = ConnectorHelpers.define_job( "Documentum test job",
    #                         "DocumentumConnection",
    #                         doc_spec_xml )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_check( [ "reference" ], None, [ fullURL+id1 ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ fullURL+id2 ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ fullURL+id3 ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ fullURL+id4 ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ fullURL+id5 ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ fullURL+id6 ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ fullURL+id7 ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ fullURL+id8 ] )

    # Search using metadata too
    ConnectorHelpers.search_check( [ "reference", "metadata:doc_path=/TestDocs" ], None, [ fullURL+id1 ] )
    ConnectorHelpers.search_check( [ "good", "metadata:doc_path=/TestDocs", "metadata:r_creator_name="+documentum_username ], None, [ fullURL+id2 ] )

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
    # Modify the documents
    add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs","f002.txt", "/root/crawlarea/testfiles/f002a.txt")
    add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs","f004.txt", "/root/crawlarea/testfiles/f004a.txt")
    # Sleep, in case there's clock skew
    wait_for_documentum(documentum_docbase, documentum_domain, documentum_username, documentum_password)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Look for state of index being right
    ConnectorHelpers.search_check( [ "reference" ], None, [ fullURL + id1 ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ fullURL + id3 ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ fullURL + id5 ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ fullURL + id6 ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ fullURL + id7 ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ fullURL + id8 ] )
    ConnectorHelpers.search_check( [ "platypus" ], None, [ fullURL + id2 ] )
    ConnectorHelpers.search_check( [ "establishments" ], None, [ fullURL + id4 ] )

    print "Done Document Change Test."

    # PHASE 3: Document Delete Detection

    print "Document Delete Test."
    remove_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f003.txt" )
    remove_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f005.txt" )
    # Sleep, in case of clock skew
    wait_for_documentum(documentum_docbase, documentum_domain, documentum_username, documentum_password)

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
    id9 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f009.txt", "/root/crawlarea/testfiles/f009.txt")
    id10 = add_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f010.txt", "/root/crawlarea/testfiles/f010.txt")
    wait_for_documentum(documentum_docbase, documentum_domain, documentum_username, documentum_password)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    ConnectorHelpers.search_check( [ "albemarle" ], None, [ fullURL + id9 ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [ fullURL + id10 ] )
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

    ConnectorHelpers.delete_repository_connection_ui( username, password, "DocumentumConnection" )
    #ConnectorHelpers.delete_repositoryconnection( "DocumentumConnection" )

    # Delete the documents we put into Documentum that are still there
    delete_docs = [ "f001.txt", "f002.txt", "f004.txt", "f006.txt", "f007.txt",
                    "f008.txt", "f009.txt", "f010.txt" ]
    for file in delete_docs:
        remove_document(documentum_docbase,documentum_domain,documentum_username,documentum_password,"/TestDocs",file)

    # Do the ad-specific part of the test
    run_ad_test_part( ad_domain_info )
    if perform_legacy_pass:
        ConnectorHelpers.select_legacy_mode()
        run_ad_test_part( ad_domain_info )
        ConnectorHelpers.cancel_legacy_mode()

    # Phase 7: Make sure that the documentum authorities are all running in parallel, and don't seem serialized in any way
    define_documentum_authority_connection_ui( username,
                                        password,
                                        "DocumentumAuthority1",
                                        "Documentum Authority1",
                                        documentum_docbase,
                                        documentum_username,
                                        documentum_password,
                                        documentum_domain )
    define_documentum_authority_connection_ui( username,
                                        password,
                                        "DocumentumAuthority2",
                                        "Documentum Authority 2",
                                        documentum_docbase,
                                        documentum_username,
                                        documentum_password,
                                        documentum_domain )
    define_documentum_authority_connection_ui( username,
                                        password,
                                        "DocumentumAuthority3",
                                        "Documentum Authority 3",
                                        documentum_docbase,
                                        documentum_username,
                                        documentum_password,
                                        documentum_domain )
    define_documentum_authority_connection_ui( username,
                                        password,
                                        "DocumentumAuthority4",
                                        "Documentum Authority 4",
                                        documentum_docbase,
                                        documentum_username,
                                        documentum_password,
                                        documentum_domain )
    define_documentum_authority_connection_ui( username,
                                        password,
                                        "DocumentumAuthority5",
                                        "Documentum Authority 5",
                                        documentum_docbase,
                                        documentum_username,
                                        documentum_password,
                                        documentum_domain )

    ConnectorHelpers.shutdown_tomcat()
    ConnectorHelpers.shutdown_agents()

    # Configure CF
    ConnectorHelpers.configure_cf({"com.metacarta.authorityconnectors":"DEBUG"})
    # Clear the logs and restart tomcat
    ConnectorHelpers.clear_logs()

    ConnectorHelpers.start_agents()
    ConnectorHelpers.start_tomcat()

    # Sleep a while to let tomcat start
    time.sleep(60)

    # We want to see all five documentum queries at the same time.  We will repeat the test for n times, or
    # until we see this happen once.
    trial_count = 100
    trial_counter = 0
    while trial_counter < trial_count:
        # Execute a curl request
        ConnectorHelpers.invoke_curl("http://localhost:8180/authorityservice/UserACLs?username=dmadmin")
        # Examine the logs - look for five concurrent queries
        log_lines = ConnectorHelpers.read_log( "DEBUG.*DCTM:" )
        query_count = 0
        regexp_pattern = re.compile( "About to execute query", 0 )
        for line in log_lines:
            if regexp_pattern.search(line) != None:
                query_count += 1
                if query_count == 5:
                    break
            else:
                query_count = 0

        if query_count == 5:
            break
        trial_counter += 1

    if trial_counter == trial_count:
        raise Exception("Could not find any matching example of full parallelism in %d trials - failure" % trial_count)

    # Reconfigure to turn off logging
    ConnectorHelpers.shutdown_tomcat()
    ConnectorHelpers.shutdown_agents()

    # Configure CF
    ConnectorHelpers.configure_cf({"com.metacarta.authorityconnectors":"WARN"})
    # Clear the logs and restart tomcat
    ConnectorHelpers.clear_logs()

    ConnectorHelpers.start_agents()
    ConnectorHelpers.start_tomcat()

    time.sleep(60)

    # Remove documentum authorities
    ConnectorHelpers.delete_authority_connection_ui( username, password, "DocumentumAuthority5" )
    ConnectorHelpers.delete_authority_connection_ui( username, password, "DocumentumAuthority4" )
    ConnectorHelpers.delete_authority_connection_ui( username, password, "DocumentumAuthority3" )
    ConnectorHelpers.delete_authority_connection_ui( username, password, "DocumentumAuthority2" )
    ConnectorHelpers.delete_authority_connection_ui( username, password, "DocumentumAuthority1" )

    # Clean up crawl user
    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    # Clean up temporary folder
    delete_folder("/root/crawlarea")

    # Check packages that should not change but have noted problems in the past; test for RT#11244
    for package in [ "metacarta-documentumconnector", "documentum-installer", "documentum-installer-dev" ]:
        check_package( package )

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "Basic DocumentumConnector tests PASSED"
