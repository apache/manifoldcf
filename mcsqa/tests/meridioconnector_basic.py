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
import VirtualBrowser

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

# Copy a folder to a (new) area
def copy_folder( source, target ):
    ConnectorHelpers.invoke_root_script( [ "mkdir", "-p", target ] )
    ConnectorHelpers.invoke_root_script( [ "cp", "-r", source, target ] )

# Remove a folder
def delete_folder( target ):
    ConnectorHelpers.invoke_root_script( [ "rm", "-rf", target ] )

# Create a meridio repository connection via the UI
def define_meridio_repository_connection_ui( username,
                                        password,
                                        connection_name,
                                        connection_description,
                                        connection_authority,
                                        doc_protocol,
                                        doc_server,
                                        doc_port,
                                        doc_location,
                                        rec_protocol,
                                        rec_server,
                                        rec_port,
                                        rec_location,
                                        web_protocol,
                                        web_server,
                                        web_port,
                                        web_location,
                                        meridio_user_name,
                                        meridio_password,
                                        meridio_domain ) :

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
    connectortypefield.select_value( "com.metacarta.crawler.connectors.meridio.MeridioConnector" )
    if connection_authority != None:
        form.find_selectbox("authorityname").select_value(connection_authority)
    # Click the "Continue" button
    continue_button = window.find_button("Continue to next page")
    continue_button.click( )
    window = vb.find_window("")

    # "Document Server" tab
    link = window.find_link("Document Server tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    form.find_selectbox("dmwsServerProtocol").select_value( doc_protocol )
    form.find_textarea("dmwsServerName").set_value( doc_server )
    form.find_textarea("dmwsServerPort").set_value( doc_port )
    form.find_textarea("dmwsLocation").set_value( doc_location )

    # "Records Server" tab
    link = window.find_link("Records Server tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    form.find_selectbox("rmwsServerProtocol").select_value( rec_protocol )
    form.find_textarea("rmwsServerName").set_value( rec_server )
    form.find_textarea("rmwsServerPort").set_value( rec_port )
    form.find_textarea("rmwsLocation").set_value( rec_location )

    # "Web Client" tab
    link = window.find_link("Web Client tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    form.find_selectbox("webClientProtocol").select_value( web_protocol )
    form.find_textarea("webClientServerName").set_value( web_server )
    form.find_textarea("webClientServerPort").set_value( web_port )
    form.find_textarea("webClientDocDownloadLocation").set_value( web_location )

    # "Credentials" tab
    link = window.find_link("Credentials tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    form.find_textarea("userName").set_value( meridio_domain + "\\" + meridio_user_name )
    form.find_textarea("password").set_value( meridio_password )

    # Now, save this page
    save_button = window.find_button("Save this connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")

# Create a meridio authority connection via the UI
def define_meridio_authority_connection_ui( username,
                                        password,
                                        connection_name,
                                        connection_description,
                                        doc_protocol,
                                        doc_server,
                                        doc_port,
                                        doc_location,
                                        rec_protocol,
                                        rec_server,
                                        rec_port,
                                        rec_location,
                                        mc_protocol,
                                        mc_server,
                                        mc_port,
                                        mc_location,
                                        meridio_user_name,
                                        meridio_password,
                                        meridio_domain ) :

    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for authority connection management and click it
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
    connectortypefield.select_value( "com.metacarta.crawler.connectors.meridio.MeridioAuthority" )
    # Click the "Continue" button
    continue_button = window.find_button("Continue to next page")
    continue_button.click( )
    window = vb.find_window("")

    # "Document Server" tab
    link = window.find_link("Document Server tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    form.find_selectbox("dmwsServerProtocol").select_value( doc_protocol )
    form.find_textarea("dmwsServerName").set_value( doc_server )
    form.find_textarea("dmwsServerPort").set_value( doc_port )
    form.find_textarea("dmwsLocation").set_value( doc_location )

    # "Records Server" tab
    link = window.find_link("Records Server tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    form.find_selectbox("rmwsServerProtocol").select_value( rec_protocol )
    form.find_textarea("rmwsServerName").set_value( rec_server )
    form.find_textarea("rmwsServerPort").set_value( rec_port )
    form.find_textarea("rmwsLocation").set_value( rec_location )

    # "MetaCarta Service Server" tab
    link = window.find_link("MetaCarta Service Server tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    form.find_selectbox("metacartawsServerProtocol").select_value( mc_protocol )
    form.find_textarea("metacartawsServerName").set_value( mc_server )
    form.find_textarea("metacartawsServerPort").set_value( mc_port )
    if mc_location != None:
        form.find_textarea("metacartawsLocation").set_value( mc_location )

    # "Credentials" tab
    link = window.find_link("Credentials tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    form.find_textarea("userName").set_value( meridio_domain + "\\" + meridio_user_name )
    form.find_textarea("password").set_value( meridio_password )

    # Now, save this page
    save_button = window.find_button("Save this authority connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")

# Define a standard meridio job using the UI
def define_meridio_job_ui( username,
                password,
                job_name,
                connection_name,
                search_paths,
                content_types,
                categories,
                datatypes,
                security_enabled=False,
                path_value_attribute=None,
                mappings=None,
                all_metadata=None,
                metadata_fields=None,
                collection_name=None,
                type="specified",
                startmethod="windowbegin",
                recrawlinterval=0 ):
    """connection_name is the name of the meridio connection.  search_paths is an array of path arrays,
       each describing a valid search path.  content_types is an array of content types TO EXCLUDE, or None to
       select all content types.  categories is an array of desired category names.  datatypes is either
       "DOCUMENTS", "RECORDS", or "DOCUMENTS_AND_RECORDS".
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
    # Start setting stuff in the form
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

    # "Search Paths" tab
    link = window.find_link("Search Paths tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # For each path, walk through the path components to get to the end, and then click the add button
    for path in search_paths:
        for component in path:
            form.find_selectbox("specpath").select_value_regexp(";%s$" % ConnectorHelpers.regexp_encode(component))
            window.find_button("Add to path").click()
            window = vb.find_window("")
            form = window.find_form("editjob")
        window.find_button("Add path").click()
        window = vb.find_window("")
        form = window.find_form("editjob")

    # "Content Types" tab
    link = window.find_link("Content Types tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if content_types != None:
        for content_type in content_types:
            form.find_checkbox("specmimetypes",content_type).deselect()

    # "Categories" tab
    link = window.find_link("Categories tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    for category in categories:
        form.find_checkbox("speccategories",category).select()

    # "Data Types" tab
    link = window.find_link("Data Types tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    form.find_radiobutton("specsearchon",datatypes).select()

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

    # "Metadata" tab
    link = window.find_link("Metadata tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # All metadata?
    if all_metadata != None and all_metadata == True:
        form.find_radiobutton("allmetadata","true").select()
    elif metadata_fields != None:
        form.find_radiobutton("allmetadata","false").select()
        for metadata_field in metadata_fields:
            form.find_checkbox("specproperties",metadata_field).select()
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
    time.sleep(60)
    pass

# Server name to talk to
meridioServerName = None
# Domain
meridioDomain = None
# Version
meridioVersion = None

# Protocol
meridioProtocol = "http"
# Server port to talk to
meridioServerPort = ""
# User
meridioUser = "meridioServiceUser"
# Password
meridioPassword = "password"
# Document management location
documentLocation = "/DMWS/MeridioDMWS.asmx";
# Record management location
recordLocation = "/RMWS/MeridioRMWS.asmx";
# Metacarta service location
mcLocation = "/MetaCartaWebService/MetaCarta.asmx";
# Web client location
webClientLocation = "/meridio/browse/downloadcontent.aspx";

# These are calculated globals
meridioDocURL = None
meridioRecURL = None
meridioDomainUser = None


# This makes a URL of the kind that will be ingested
def make_meridio_url(document_id):
    # Note: TestDocs results have protocol stripped off.
    if meridioServerPort == "":
        return "%s%s?launchMode=1&launchAs=0&documentId=%s" % (meridioServerName,webClientLocation,document_id)
    else:
        return "%s:%s/%s?launchMode=1&launchAs=0&documentId=%s" % (meridioServerName,meridioServerPort,webClientLocation,document_id)

# Document titles and identifiers
title1 = ("/Testing/TestDocs","f001")
title2 = ("/Testing/TestDocs","f002")
title3 = ("/Testing/TestDocs","f003")
title4 = ("/Testing/TestDocs","f004")
title5 = ("/Testing/TestDocs","f005")
title6 = ("/Testing/TestDocs","f006")
title7 = ("/Testing/TestDocs","f007")
title8 = ("/Testing/TestDocs-newfolder","f008")
title9 = ("/Testing/TestDocs","f009")
title10 = ("/Testing/TestDocs","f010")

title3a =  ("/Testing/TestDocs-userafolder","f003")
title4a =  ("/Testing/TestDocs-userafolder","f004")
title5a =  ("/Testing/TestDocs-userbfolder","f005")
title6a =  ("/Testing/TestDocs-userbfolder","f006")
title7a =  ("/Testing/TestDocs-usercfolder","f007")
title8a =  ("/Testing/TestDocs-newfolder","f008")

id1 = None
id2 = None
id3 = None
id4 = None
id5 = None
id6 = None
id7 = None
id8 = None
id9 = None
id10 = None

id3a = None
id4a = None
id5a = None
id6a = None
id7a = None
id8a = None

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

    # Lookup the id's of test documents and delete them
    for docstuff in [title1,title2,title3,title4,title5,title6,title7,title8,title9,title10,title3a,title4a,title5a,title6a,title7a,title8a]:
        (docpath,doctitle) = docstuff
        try:
            docid = lookup_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,docpath,doctitle)
            if docid != "":
                remove_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,docid)
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s/%s" % (docpath,doctitle)
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
    """ Do the ad part of the test.  This may be done twice, if a legacy test has been requested. """

    ad_win_host = ad_domain_info.ie_client_fqdn

    # PHASE 6: Security test with AD and ACLs
    # This particular test involves putting documents into Sharepoint and setting up specific user rights.
    # The search is done by specific AD users, which should return documents that have only the
    # right permissions.

    # The domain controller is already set up ad with the following users:
    # meridiousera
    # meridiouserb
    # meridiouserc
    # These users have already been added to meridio as users that can read documents.

    # Set up system to use ad
    ConnectorHelpers.configure_ad( ad_domain_info )

    # Add test documents into folders where security is enabled
    id1 = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f001.txt","f001")
    add_acl_to_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id1,"meridiousera")
    add_acl_to_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id1,"meridiouserb")
    add_acl_to_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id1,"meridiouserc")
    id2 = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f002.txt","f002")
    add_acl_to_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id2,"meridiousera")
    add_acl_to_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id2,"meridiouserb")
    add_acl_to_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id2,"meridiouserc")
    # Documents that meridiousera should be able to see, only
    id3a = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs-userafolder","/root/crawlarea/testfiles/","f003.txt","f003")
    add_acl_to_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id3a,"meridiousera")
    id4a = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs-userafolder","/root/crawlarea/testfiles/","f004.txt","f004")
    add_acl_to_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id4a,"meridiousera")
    # Documents that meridiouserb should be able to see, only
    id5a = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs-userbfolder","/root/crawlarea/testfiles/","f005.txt","f005")
    add_acl_to_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id5a,"meridiouserb")
    id6a = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs-userbfolder","/root/crawlarea/testfiles/","f006.txt","f006")
    add_acl_to_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id6a,"meridiouserb")
    # Documents that meridiouserc should be able to see, only
    id7a = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs-usercfolder","/root/crawlarea/testfiles/","f007.txt","f007")
    add_acl_to_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id7a,"meridiouserc")
    # Documents that both meridiousera and meridiouserb should be able to see, only
    id8a = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs-newfolder","/root/crawlarea/testfiles/newfolder/","f008.txt","f008")
    add_acl_to_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id8a,"meridiousera")
    add_acl_to_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id8a,"meridiouserb")

    # In case there is clock skew, sleep a minute
    wait_for_meridio(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword)

    # Define authority connection
    define_meridio_authority_connection_ui( username,
                                        password,
                                        "MeridioConnection",
                                        "Meridio Connection",
                                        meridioProtocol,
                                        meridioServerName,
                                        meridioServerPort,
                                        documentLocation,
                                        meridioProtocol,
                                        meridioServerName,
                                        meridioServerPort,
                                        recordLocation,
                                        meridioProtocol,
                                        meridioServerName,
                                        meridioServerPort,
                                        mcLocation,
                                        meridioUser,
                                        meridioPassword,
                                        meridioDomain )

    # Define repository connection, using the authority we just defined
    define_meridio_repository_connection_ui( username,
                                        password,
                                        "MeridioConnection",
                                        "Meridio Connection",
                                        "MeridioConnection",
                                        meridioProtocol,
                                        meridioServerName,
                                        meridioServerPort,
                                        documentLocation,
                                        meridioProtocol,
                                        meridioServerName,
                                        meridioServerPort,
                                        recordLocation,
                                        meridioProtocol,
                                        meridioServerName,
                                        meridioServerPort,
                                        webClientLocation,
                                        meridioUser,
                                        meridioPassword,
                                        meridioDomain )

    # Define the job
    job_id = define_meridio_job_ui( username,
                        password,
                        "Meridio test job",
                        "MeridioConnection",
                        [ [ "Testing","TestDocs" ],
                          [ "Testing","TestDocs-userafolder" ],
                          [ "Testing","TestDocs-userbfolder" ],
                          [ "Testing","TestDocs-usercfolder" ],
                          [ "Testing","TestDocs-newfolder" ] ],
                        [ ],
                        [ "MetacartaSearchable" ],
                        "DOCUMENTS_AND_RECORDS",
                        security_enabled=True )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # meridiousera
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_meridio_url( id1 ) ], username="meridiousera", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_meridio_url( id3a ) ], username="meridiousera", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="meridiousera", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="meridiousera", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="meridiousera", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ make_meridio_url( id8a ) ], username="meridiousera", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_meridio_url( id2 ) ], username="meridiousera", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_meridio_url( id4a ) ], username="meridiousera", password="user", win_host=ad_win_host )

    # userb
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_meridio_url( id1 ) ], username="meridiouserb", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="meridiouserb", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_meridio_url( id5a ) ], username="meridiouserb", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_meridio_url( id6a ) ], username="meridiouserb", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="meridiouserb", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ make_meridio_url( id8a ) ], username="meridiouserb", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_meridio_url( id2 ) ], username="meridiouserb", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="meridiouserb", password="user", win_host=ad_win_host )

    # userc
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_meridio_url( id1 ) ], username="meridiouserc", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="meridiouserc", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="meridiouserc", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="meridiouserc", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_meridio_url( id7a ) ], username="meridiouserc", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ ], username="meridiouserc", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_meridio_url( id2 ) ], username="meridiouserc", password="user", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="meridiouserc", password="user", win_host=ad_win_host )

    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    # Delete the repository connection
    ConnectorHelpers.delete_repository_connection_ui( username, password, "MeridioConnection" )

    # Delete the authority connection
    ConnectorHelpers.delete_authority_connection_ui( username, password, "MeridioConnection" )

    # Clean up the documents we dumped into the folders on meridio
    for docid in [id1,id2,id3a,id4a,id5a,id6a,id7a,id8a]:
        remove_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,docid)

    # Disable ad
    ConnectorHelpers.turn_off_ad( ad_domain_info )

# Main
if __name__ == '__main__':

    # AD parameters
    ad_group = "76"

    # This means 'use the default'
    version = None

    if len(sys.argv) > 1:
        ad_group = sys.argv[1]
    if len(sys.argv) > 2:
        version = sys.argv[2]
    perform_legacy_pass = False
    if len(sys.argv) > 3 and sys.argv[3] == "legacy":
        perform_legacy_pass = True

    ad_domain_info = sqa_domain_info.SQADomainInfo( ad_group )

    meridioDomain = ad_domain_info.dns_domain.upper()
    meridioVersion = version

    if version == None:
        server_version = "4_3"
    else:
        server_version = version.replace(".","_")

    meridioServerName = getattr( ad_domain_info, "meridio_server_v" + server_version + "_fqdn" ).split(".")[0]


    # Calculate the globals
    # These are the calculated URLs for talking with Meridio
    if len(meridioServerPort) > 0:
        meridioDocURL = "%s://%s:%s%s" % (meridioProtocol,meridioServerName,meridioServerPort,documentLocation)
    else:
        meridioDocURL = "%s://%s%s" % (meridioProtocol,meridioServerName,documentLocation)

    if len(meridioServerPort) > 0:
        meridioRecURL = "%s://%s:%s%s" % (meridioProtocol,meridioServerName,meridioServerPort,recordLocation)
    else:
        meridioRecURL = "%s://%s%s" % (meridioProtocol,meridioServerName,recordLocation)

    meridioDomainUser = "%s\%s" % (meridioDomain,meridioUser)

    print "Precleaning!"

    preclean( ad_domain_info, perform_legacy_pass, print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up file area."
    copy_folder("/root/testfiles","/root/crawlarea")

    # Enable CF security
    ConnectorHelpers.enable_connector_framework( )


    print "Initializing test documents."
    # These documents are not yet part of the set that gets moved over, but they may be in the future
    o = open( "/root/crawlarea/testfiles/f002a.txt", "w" )
    o.write("Now this document is at 50N 75E, and the keyword is platypus")
    o.close()
    o = open( "/root/crawlarea/testfiles/f004a.txt", "w" )
    o.write("No longer about drinking establishments at 23N 15W")
    o.close()
    o = open( "/root/crawlarea/testfiles/f009.txt", "w" )
    o.write("Now this document is at 50N 75E, and the keyword is albemarle")
    o.close()
    o = open( "/root/crawlarea/testfiles/f010.txt", "w" )
    o.write("No longer about golfcarts at 23N 15W")
    o.close()

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["meridioConnector"], detect_gdms=True)

    # Set up the ingestion user.

    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )

    # PHASE 1: Ingestion


    print "Ingestion Test."

    # Add some docs to the repository
    id1 = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f001.txt","f001")
    id2 = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f002.txt","f002")
    id3 = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f003.txt","f003")
    id4 = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f004.txt","f004")
    id5 = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f005.txt","f005")
    id6 = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f006.txt","f006")
    id7 = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f007.txt","f007")
    id8 = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs-newfolder","/root/crawlarea/testfiles/newfolder/","f008.txt","f008")

    # In case there is clock skew, sleep a minute
    wait_for_meridio(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword)

    # Restart tomcat to clear any pooled connections
    ConnectorHelpers.restart_tomcat()
    time.sleep(10)

    # Define repository connection
    define_meridio_repository_connection_ui( username,
                                        password,
                                        "MeridioConnection",
                                        "Meridio Connection",
                                        None,
                                        meridioProtocol,
                                        meridioServerName,
                                        meridioServerPort,
                                        documentLocation,
                                        meridioProtocol,
                                        meridioServerName,
                                        meridioServerPort,
                                        recordLocation,
                                        meridioProtocol,
                                        meridioServerName,
                                        meridioServerPort,
                                        webClientLocation,
                                        meridioUser,
                                        meridioPassword,
                                        meridioDomain )

    # Verify that this connection is sound
    ConnectorHelpers.view_repository_connection_ui( username, password, "MeridioConnection" )
    
    # Now, define a second connection that has incorrect signon parameters, and make sure we get the expected error.  This tests whether
    # connection pooling is working properly.
    define_meridio_repository_connection_ui( username,
                                        password,
                                        "MeridioConnection2",
                                        "Meridio Connection 2",
                                        None,
                                        meridioProtocol,
                                        meridioServerName,
                                        meridioServerPort,
                                        documentLocation,
                                        meridioProtocol,
                                        meridioServerName,
                                        meridioServerPort,
                                        recordLocation,
                                        meridioProtocol,
                                        meridioServerName,
                                        meridioServerPort,
                                        webClientLocation,
                                        meridioUser,
                                        "badpassword",
                                        meridioDomain )
    
    ConnectorHelpers.view_repository_connection_ui( username, password, "MeridioConnection2", match_string="401" )

    # Delete unused connection
    ConnectorHelpers.delete_repository_connection_ui( username, password, "MeridioConnection2" )

    # Define job
    job_id = define_meridio_job_ui( username,
                        password,
                        "Meridio test job",
                        "MeridioConnection",
                        [ [ "Testing","TestDocs" ],
                          [ "Testing","TestDocs-newfolder" ] ],
                        [ ],
                        [ "MetacartaSearchable" ],
                        "DOCUMENTS_AND_RECORDS",
                        path_value_attribute="doc_path",
                        all_metadata=True )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_meridio_url(id1) ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_meridio_url(id2) ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_meridio_url(id3) ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_meridio_url(id4) ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_meridio_url(id5) ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_meridio_url(id6) ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_meridio_url(id7) ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ make_meridio_url(id8) ] )

    # Do a path metadata search
    # HMM.  Right now, this doesn't work, because no documents seem to have path metadata!  Find out if this should be true or not.
    # 3/13/2008: Sent email to Stuart about whether this should work; haven't heard back yet
    #ConnectorHelpers.search_check( [ "reference", "metadata:doc_path=%s" % id1 ], None, [ make_meridio_url(id1) ] )

    # Do other kinds of metadata searching, to be sure metadata got ingested
    ConnectorHelpers.search_check( [ "reference", "metadata:Title=f001" ], None, [ make_meridio_url(id1) ] )

    # Success: done
    print "Done ingestion test."


    # PHASE 2: Document Change Detection

    print "Document Change Test."
    # Modify the documents
    update_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id2,"/root/crawlarea/testfiles/","f002a.txt")
    update_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id4,"/root/crawlarea/testfiles/","f004a.txt")

    # Sleep, in case there's clock skew
    wait_for_meridio(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Look for state of index being right
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_meridio_url(id1) ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_meridio_url(id3) ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_meridio_url(id5) ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_meridio_url(id6) ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_meridio_url(id7) ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ make_meridio_url(id8) ] )
    ConnectorHelpers.search_check( [ "platypus" ], None, [ make_meridio_url(id2) ] )
    ConnectorHelpers.search_check( [ "establishments" ], None, [ make_meridio_url(id4) ] )

    print "Done Document Change Test."

    # PHASE 3: Document Delete Detection

    print "Document Delete Test."
    remove_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id3 )
    remove_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,id5 )
    # Sleep, in case of clock skew
    wait_for_meridio(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ ] )
    print "Done Document Delete Test."

    # PHASE 4: Document Addition Detection

    print "Document Add Test."
    id9 = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f009.txt","f009")
    id10 = add_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f010.txt","f010")
    wait_for_meridio(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    ConnectorHelpers.search_check( [ "albemarle" ], None, [ make_meridio_url(id9) ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [ make_meridio_url(id10) ] )
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

    ConnectorHelpers.delete_repository_connection_ui( username, password, "MeridioConnection" )

    # Clean up the documents we dumped into the folders on meridio
    for docid in [id1,id2,id3,id4,id5,id6,id7,id8,id9,id10]:
        remove_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,docid)

    # Do the part of the test that requires ad
    run_ad_test_part( ad_domain_info )
    if perform_legacy_pass:
        ConnectorHelpers.select_legacy_mode()
        run_ad_test_part( ad_domain_info )
        ConnectorHelpers.cancel_legacy_mode()


    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    # Clean up temporary folder
    delete_folder("/root/crawlarea")

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "Basic MeridioConnector tests PASSED"
