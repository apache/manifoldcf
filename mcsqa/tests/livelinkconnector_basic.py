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
from wintools import ambassador_client
from sqatools import LicenseMakerClient
import TestDocs
import VirtualBrowser
from threading import Thread
import urllib2

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

class run_livelink_test_server_thread(Thread):
    def __init__ (self, ambassador, response):
        Thread.__init__(self)
        self.response = response
        self.ambassador = ambassador
        self.setDaemon(True)

    def run(self):
        try:
            # Start the livelink test server via ambassador on the livelink machine
            # Warning: This will hang until shut down!
            output = self.ambassador.run_python_script("livelink_test_server.py",
                    supporting_files=[ ])
            # Output isn't necessarily bad; the web server dumps all logs to standard out
            if output:
                print output
        except Exception, e:
            self.response.append( str(e) )

# Start livelink test server on the Windows client machine, and wait for it to come up
def start_livelink_test_server( livelink_server, username, password, response ):
    """ Start the livelink test server on the livelink repository machine.  It will then stay up until shut down.
    """
    ambassador = ambassador_client.AmbassadorClient(livelink_server+":8000", username, password)
    thread = run_livelink_test_server_thread(ambassador,response)
    thread.start()
    # Keep polling until the server comes up
    while True:
        if not thread.isAlive():
                # Didn't come up!
            thread.join()
            raise Exception("Could not start livelink test server: %s" % response)
        try:
            urllib2.urlopen("http://%s:8002/stuff/Livelink.exe" % livelink_server)
            print "Livelink test server successfully started up"
            return thread
        except:
            # back around again
            pass
        time.sleep(5)

# Shutdown livelink test server on the Windows client machine, if it is running.
def shutdown_livelink_test_server( livelink_server, thread=None, response=None ):
    """ Shut down the livelink test server on the livelink repository machine.  This will also unlock the
        ambassador, which will be blocked until the shutdown is performed.
    """
    try:
        urllib2.urlopen("http://%s:8002" % livelink_server)

    except:
        # Must already have been shut down; continue
        return

    print "Livelink test server successfully shut down"
    # If there's a thread we know about, let it exit, and report any errors
    if thread:
        thread.join()
        if response:
            if len(response) > 0:
                raise Exception("Livelink test server had problems: %s" % response)


# Copy a folder to a (new) area
def copy_folder( source, target ):
    ConnectorHelpers.invoke_root_script( [ "mkdir", "-p", target ] )
    ConnectorHelpers.invoke_root_script( [ "cp", "-r", source, target ] )

# Remove a folder
def delete_folder( target ):
    ConnectorHelpers.invoke_root_script( [ "rm", "-rf", target ] )

# Create a livelink repository connection via the UI
def define_livelink_repository_connection_ui( username,
                                        password,
                                        connection_name,
                                        connection_description,
                                        server_name,
                                        server_port,
                                        lluser_name,
                                        lluser_password,
                                        authority_name=None,
                                        fetch_protocol="http",
                                        fetch_port=None,
                                        fetch_cgipath=None,
                                        fetch_certificate_file=None,
                                        fetch_ntlm_domain=None,
                                        fetch_ntlm_username=None,
                                        fetch_ntlm_password=None,
                                        view_protocol=None,
                                        view_port=None,
                                        view_cgipath=None,
                                        view_server=None ):
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
    connectortypefield.select_value( "com.metacarta.crawler.connectors.livelink.LivelinkConnector" )
    if authority_name != None:
        form.find_selectbox("authorityname").select_value( authority_name )
    # Click the "Continue" button
    continue_button = window.find_button("Continue to next page")
    continue_button.click( )

    # "Server" tab
    window = vb.find_window("")
    link = window.find_link("Server tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    # Set livelink-specific stuff
    form.find_textarea("servername").set_value( server_name )
    form.find_textarea("serverport").set_value( server_port )
    form.find_textarea("serverusername").set_value( lluser_name )
    form.find_textarea("serverpassword").set_value( lluser_password )

    # "Document Access" tab
    link = window.find_link("Document Access tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    form.find_selectbox("ingestprotocol").select_value( fetch_protocol )
    if fetch_port != None:
        form.find_textarea("ingestport").set_value( str(fetch_port) )
    if fetch_cgipath != None:
        form.find_textarea("ingestcgipath").set_value( fetch_cgipath )
    if fetch_certificate_file != None:
        # Set the certificate
        form.find_filebrowser("llcertificate").setfile(fetch_certificate_file,"application/octet-stream")
        add_button = window.find_button("Add cert")
        add_button.click( )
        window = vb.find_window("")
        form = window.find_form("editconnection")
    if fetch_ntlm_domain != None:
        form.find_textarea("ntlmdomain").set_value( fetch_ntlm_domain )
    if fetch_ntlm_username != None:
        form.find_textarea("ntlmusername").set_value( fetch_ntlm_username )
    if fetch_ntlm_password != None:
        form.find_textarea("ntlmpassword").set_value( fetch_ntlm_password )

    # "Document View" tab
    link = window.find_link("Document View tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    if view_protocol != None:
        form.find_selectbox("viewprotocol").select_value( view_protocol )
    if view_port != None:
        form.find_textarea("viewport").set_value( str(view_port) )
    if view_cgipath != None:
        form.find_textarea("viewcgipath").set_value( view_cgipath )
    if view_server != None:
        form.find_textarea("viewservername").set_value( view_server )

    # Now, save this page
    save_button = window.find_button("Save this connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")

# Define a standard livelink job using the UI
def define_livelink_job_ui( username,
                password,
                job_name,
                connection_name,
                folders,
                includes_excludes,
                security_enabled=False,
                path_value_attribute=None,
                mappings=None,
                all_metadata=None,
                metadata_spec=None,
                collection_name=None,
                type="specified",
                startmethod="windowbegin",
                recrawlinterval=0 ):
    """connection_name is the name of the livelink connection.  folders is an array of
       folder path arrays.  includes_excludes is an array of tuples, the first value of which
       is either "include" or "exclude", and the second being the file specification.
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

    # "Filters" tab
    link = window.find_link("Filters tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # Set up includes/excludes
    for includespecelement in includes_excludes:
        method, match = includespecelement
        form.find_selectbox("specfiletype").select_value(method)
        form.find_textarea("specfile").set_value(match)
        window.find_button("Add file specification").click()
        window = vb.find_window("")
        form = window.find_form("editjob")

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
    # Select "all metadata" if so specified
    if all_metadata != None and all_metadata == True:
        form.find_radiobutton("specallmetadata","true").select()
    elif metadata_spec != None:
        for metadata_item in metadata_spec:
            workspace, folder_array, category, all_attributes, attribute_fields = metadata_item
            # Select the workspace
            form.find_selectbox("metadataaddon").set_value(workspace)
            form.find_button("Add to metadata path").click()
            window = vb.find_window("")
            form = window.find_form("editjob")
            # Select the path
            for path_item in folder_array:
                form.find_selectbox("metadataaddon").set_value(path_item)
                form.find_button("Add to metadata path").click()
                window = vb.find_window("")
                form = window.find_form("editjob")
            # Select the category
            form.find_selectbox("categoryaddon").set_value(category)
            form.find_button("Add category").click()
            window = vb.find_window("")
            form = window.find_form("editjob")
            # Either select "all attributes", or pick the ones we want
            if all_attributes != None and all_attributes == True:
                form.find_checkbox("attributeall","true").select()
            elif attribute_fields != None:
                for attribute_field in attribute_fields:
                    form.find_selectbox("attributeselect").set_value(attribute_field)
            # Add the metadata item to the set
            form.find_button("Add metadata item").click()
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

def set_metadata_value(servername, port, user, password, llpath, catpath, attrname, attrvalue):
    """ Sets a metadata value """
    listparams = [ ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(llpath),
            ConnectorHelpers.process_argument(catpath),
            ConnectorHelpers.process_argument(attrname),
            ConnectorHelpers.process_argument(attrvalue) ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.livelink.SetMetadataValue", 
                                    argument_list=listparams,
                                    additional_classpath="livelink-testing-package/metacarta-livelinkconnector-test.jar" )

# Method to wait whatever time is needed after changing livelink documents
# for them to be noted as changed.
def wait_for_livelink(servername, port, user, password):
    """Nothing needed"""
    pass

# Invoke the API method for getting the server name from the connection
def get_livelink_server_name_api( connection_name ):
    """ Invoke the API method for getting the server name from the connection
    """
    results = ConnectorHelpers.invoke_script(["/usr/lib/metacarta/livelink-getconnectioninfo",connection_name])
    return ConnectorHelpers.process_api_result(results,["livelink_server"])

# Server name to talk to
llServerName = None
# Server port to talk to
llServerPort = "2099"
# User
llUser = "admin"
# Password
llPassword = "livelink"

# Server location
url_suffix = "/livelink/livelink.exe?func=ll&objID=%s&objAction=download"

def build_livelink_url( id, port=None, server=None ):
    """Build the url from pieces"""
    server_part = server
    if server_part == None:
        server_part = llServerName
    if port != None:
        server_part = server_part + ":" + str(port)
    return server_part + url_suffix % id

# Crawl user credentials
username = "testingest"
password = "testingest"

def preclean( ad_domain_info, livelink_server_name, perform_legacy_pass, print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    # First order of business: synchronize legacy mode, so our state matches the duck state
    ConnectorHelpers.synchronize_legacy_mode( )

    try:
        shutdown_livelink_test_server( livelink_server_name )
    except Exception,e:
        if print_errors:
            print "Error shutting down livelink test server"
            print e

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
    for docname in [ "TestDocs/f001", "TestDocs/f002", "TestDocs/f003", "TestDocs/f004", "TestDocs/f005",
                     "TestDocs/f006", "TestDocs/f007", "TestDocs/f007", "TestDocs/newfolder/f008",
                     "TestDocs/f009", "TestDocs/f010", "TestDocs/CompoundDocument/f011",
                     "TestDocs/userafolder/f003", "TestDocs/userafolder/f004",
                     "TestDocs/userbfolder/f005", "TestDocs/userbfolder/f006", "TestDocs/usercfolder/f007" ]:
        try:
            remove_document(llServerName, llServerPort, llUser, llPassword, docname )
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % docname
                print e

    # Delete users

    # Clean up basic auth users
    for user in [ "llusera", "lluserb", "lluserc" ]:
        try:
            ConnectorHelpers.delete_basic_auth_user(user)
        except Exception, e:
            if print_errors:
                print "Error deleting basic user %s" % user
                print e

    # Turn off basic auth
    try:
        ConnectorHelpers.turn_off_basic_auth( )
    except Exception, e:
        if print_errors:
            print "Error disabling basic auth"
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

    try:
        # Since the test unregisters the livelink authority at one point, we better make sure it is there on the preclean.
        ConnectorHelpers.register_authorityconnector("com.metacarta.crawler.connectors.livelink.LivelinkAuthority","LivelinkAuthority")
    except Exception, e:
        if print_errors:
            print "Error registering livelink authority"
            print e

    if perform_legacy_pass:
        try:
            ConnectorHelpers.select_legacy_mode(use_legacy_tools=False)
        except Exception, e:
            if print_errors:
                print "Error turning off legacy AD mode"
                print e

def run_ad_test_part( ad_domain_info ):
    """ Perform the ad part of the test.  This may be called twice, if legacy mode testing is requested """

    ad_win_host = ad_domain_info.ie_client_fqdn

    # PHASE 7: Security test with AD and ACLs
    # This particular test involves putting documents into Livelink and setting up specific user rights.
    # In addition to creating a job, an authority connection must also be created.
    # Then, the search is done by specific AD users, which should return documents that have only the
    # right permissions.

    # Now, set up ad with the following users:
    # p_livelinkusera
    # p_livelinkuserb
    # p_livelinkuserc
    # These correspond to users already present in the livelink instance, except for the "p_livelink" prefix.

    # Set up system to use ad
    ConnectorHelpers.configure_ad( ad_domain_info )

    # Enable CF security again
    ConnectorHelpers.enable_connector_framework( )

    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_livelinkusera", "usera" )
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_livelinkuserb", "userb" )
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_livelinkuserc", "userc" )

    # Add the current set of docs to the repository, and twiddle their security
    id1 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f001", "/root/crawlarea/testfiles/f001.txt")
    id2 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f002", "/root/crawlarea/testfiles/f002.txt")
    # TestDocs.userafolder will be visible by llusera only
    id3 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/userafolder", "f003", "/root/crawlarea/testfiles/f003.txt")
    add_document_right(llServerName, llServerPort, llUser, llPassword, "TestDocs/userafolder/f003", "usera", "")
    id4 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/userafolder", "f004", "/root/crawlarea/testfiles/f004.txt")
    add_document_right(llServerName, llServerPort, llUser, llPassword, "TestDocs/userafolder/f004", "usera", "")
    # TestDocs.userbfolder will be visible by userb only
    id5 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/userbfolder", "f005", "/root/crawlarea/testfiles/f005.txt")
    add_document_right(llServerName, llServerPort, llUser, llPassword, "TestDocs/userbfolder/f005", "userb", "")
    id6 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/userbfolder", "f006", "/root/crawlarea/testfiles/f006.txt")
    add_document_right(llServerName, llServerPort, llUser, llPassword, "TestDocs/userbfolder/f006", "userb", "")
    # TestDocs.usercfolder will be visible by userc only
    id7 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/usercfolder", "f007", "/root/crawlarea/testfiles/f007.txt")
    add_document_right(llServerName, llServerPort, llUser, llPassword, "TestDocs/usercfolder/f007", "userc", "")
    # TestDocs.newfolder will be visible by both llusera and userb, but not userc
    id8 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/newfolder", "f008","/root/crawlarea/testfiles/newfolder/f008.txt")
    add_document_right(llServerName, llServerPort, llUser, llPassword, "TestDocs/newfolder/f008", "usera", "")
    add_document_right(llServerName, llServerPort, llUser, llPassword, "TestDocs/newfolder/f008", "userb", "")

    # Define authority connection
    ConnectorHelpers.define_authorityconnection( "LivelinkConnection",
                                 "Livelink Connection",
                                 "com.metacarta.crawler.connectors.livelink.LivelinkAuthority",
                                 configparams = [ "Server name="+llServerName,
                                                  "Server port="+llServerPort,
                                                  "Server user name="+llUser,
                                                  "Server password="+llPassword,
                                                  "User name regexp=",
                                                  "Livelink user spec=",
                                                  "Livelink user name map=^p_livelink(.*)\\\\@([A-Z|a-z|0-9|_|-]*)\\\\.(.*)\\$=\\$(1l)" ] )
                    # The old-style name maps were:
                    # "User name regexp=(.*)\\@([A-Z|a-z|0-9|_|-]*)\\.(.*)"
                    # "Livelink user spec=1l"



    # Define repository connection
    ConnectorHelpers.define_repositoryconnection( "LivelinkConnection",
                                 "Livelink Connection",
                                 "com.metacarta.crawler.connectors.livelink.LivelinkConnector",
                                 configparams = [ "CGI path=/livelink/livelink.exe",
                                                  "View CGI path=",
                                                  "Server name="+llServerName,
                                                  "Server port="+llServerPort,
                                                  "Server user name="+llUser,
                                                  "Server password="+llPassword,
                                                  "NTLM domain=" ],
                                 authorityname = "LivelinkConnection" )

    # PHASE 6: Ingest documents with the specified connectors.
    # There will be several separate kinds of security as well.

    # Define job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="TestDocs"/><include filespec="*.txt"/><security value="on"/></specification>'
    job_id = ConnectorHelpers.define_job( "Livelink test job",
                             "LivelinkConnection",
                             doc_spec_xml )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Check for proper visibility of all documents
    # usera
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( id1 ) ], username="p_livelinkusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_livelink_url( id3 ) ], username="p_livelinkusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="p_livelinkusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="p_livelinkusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="p_livelinkusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ build_livelink_url( id8 ) ], username="p_livelinkusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_livelink_url( id2 ) ], username="p_livelinkusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_livelink_url( id4 ) ], username="p_livelinkusera", password="usera", win_host=ad_win_host )

    # userb
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( id1 ) ], username="p_livelinkuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="p_livelinkuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_livelink_url( id5 ) ], username="p_livelinkuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_livelink_url( id6 ) ], username="p_livelinkuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="p_livelinkuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ build_livelink_url( id8 ) ], username="p_livelinkuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_livelink_url( id2 ) ], username="p_livelinkuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="p_livelinkuserb", password="userb", win_host=ad_win_host )

    # userc
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( id1 ) ], username="p_livelinkuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="p_livelinkuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="p_livelinkuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="p_livelinkuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_livelink_url( id7 ) ], username="p_livelinkuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ ], username="p_livelinkuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_livelink_url( id2 ) ], username="p_livelinkuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="p_livelinkuserc", password="userc", win_host=ad_win_host )


    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    ConnectorHelpers.delete_repositoryconnection( "LivelinkConnection" )

    ConnectorHelpers.delete_authorityconnection( "LivelinkConnection" )

    # Clean up the documents we dumped into the folders on livelink
    for docname in ["TestDocs/f001", "TestDocs/f002", "TestDocs/userafolder/f003", "TestDocs/userafolder/f004",
                    "TestDocs/userbfolder/f005", "TestDocs/userbfolder/f006", "TestDocs/usercfolder/f007",
                    "TestDocs/newfolder/f008" ]:
        remove_document(llServerName, llServerPort, llUser, llPassword, docname )

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

    llServerSpec = "livelink_server_v9_2"
    # Ambassador username
    llServerUsername = "Administrator"
    # Ambassador password
    llServerPassword = "password"

    if len(sys.argv) > 2:
        llServerSpec = sys.argv[2]

    llServerName = getattr( ad_domain_info, llServerSpec + "_fqdn" )

    print "Precleaning!"

    preclean( ad_domain_info, llServerName, perform_legacy_pass, print_errors=False )

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
    sqatools.appliance.install_license(extra_services=["livelinkConnector"], detect_gdms=True)


    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # PHASE 0: Check that cgi paths that don't start with / are rejected

    print "Check that UI rejects CGI paths that don't start with /"

    oops = False
    try:
        define_livelink_repository_connection_ui( username,
                                        password,
                                        "LivelinkConnection",
                                        "Livelink Connection",
                                        llServerName,
                                        llServerPort,
                                        llUser,
                                        llPassword,
                                        fetch_protocol="http",
                                        fetch_cgipath="stuff/Livelink.exe" )
        oops = True
    except Exception, e:
        pass
    if oops:
        raise Exception("Expected error when we use bad cgi path, instead succeeded!")
        
    print "Failed to define illegal repository connection, as expected."

    # PHASE 0.1: Check non-standard cgipath

    print "Check that changed ingest path works."


    define_livelink_repository_connection_ui( username,
                                        password,
                                        "LivelinkConnection",
                                        "Livelink Connection",
                                        llServerName,
                                        llServerPort,
                                        llUser,
                                        llPassword,
                                        fetch_protocol="http",
                                        fetch_cgipath="/stuff/Livelink.exe",
                                        fetch_port=8002 )

    print "Run API method to get livelink connection information"
    
    # Try out the API method
    results = get_livelink_server_name_api( "LivelinkConnection" )
    if len(results) != 1:
        raise Exception("Expected one livelink connection, found %d" % len(results))
    if results[0]["livelink_server"] != llServerName:
        raise Exception("Expected server name to be '%s', instead was '%s'" % (llServerName,results[0]["livelink_server"]))

    # We have to fake out the web server here because we were unable to make our Livelink instance redirect the way Shell's did in the field.
    response_string = ""
    thread = start_livelink_test_server( llServerName, llServerUsername, llServerPassword, response_string )

    # Make sure connection is happy
    ConnectorHelpers.view_repository_connection_ui( username, password, "LivelinkConnection" )

    # Shut down livelink test server
    shutdown_livelink_test_server( llServerName, thread=thread, response=response_string )

    ConnectorHelpers.delete_repositoryconnection( "LivelinkConnection" )

    # PHASE 0.1: Try pointing at a non-existent server

    print "Non-existent server test."

    define_livelink_repository_connection_ui( username,
                                        password,
                                        "LivelinkConnection",
                                        "Livelink Connection",
                                        "unknown_server",
                                        llServerPort,
                                        llUser,
                                        llPassword,
                                        fetch_protocol="http",
                                        fetch_cgipath="/stuff/Livelink.exe",
                                        fetch_port=8002 )

    # Check that the connection status is correct
    ConnectorHelpers.view_repository_connection_ui( username, password, "LivelinkConnection", match_string="cannot be resolved" )

    ConnectorHelpers.delete_repositoryconnection( "LivelinkConnection" )

    # PHASE 1: Ingestion

    print "Ingestion Test."

    # Add some docs to the repository
    id1 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f001", "/root/crawlarea/testfiles/f001.txt")
    id2 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f002", "/root/crawlarea/testfiles/f002.txt")
    id3 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f003", "/root/crawlarea/testfiles/f003.txt")
    id4 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f004", "/root/crawlarea/testfiles/f004.txt")
    id5 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f005", "/root/crawlarea/testfiles/f005.txt")
    id6 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f006", "/root/crawlarea/testfiles/f006.txt")
    id7 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f007", "/root/crawlarea/testfiles/f007.txt")
    id8 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/newfolder", "f008","/root/crawlarea/testfiles/newfolder/f008.txt")
    id11 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/CompoundDocument", "f011", "/root/crawlarea/testfiles/f006.txt")

    # Set some metadata
    set_metadata_value(llServerName, llServerPort, llUser, llPassword, "TestDocs/f001",
                "TestMetadata", "SimpleText", "some_metadata_value" )

    # In case there is clock skew, sleep a minute
    wait_for_livelink(llServerName, llServerPort, llUser, llPassword)

    # Define repository connection.
    # This phase of the test will use SSL - the only part that does.
    define_livelink_repository_connection_ui( username,
                                        password,
                                        "LivelinkConnection",
                                        "Livelink Connection",
                                        llServerName,
                                        llServerPort,
                                        llUser,
                                        llPassword,
                                        fetch_protocol="https",
                                        fetch_port=444,
                                        fetch_certificate_file="livelinksrvr/bigiiscax509.cer" )

    # Make sure connection is happy
    ConnectorHelpers.view_repository_connection_ui( username, password, "LivelinkConnection" )

    print "Making sure illegal mappings are detected by connector"
    
    job_id = define_livelink_job_ui( username,
                        password,
                        "Livelink test job",
                        "LivelinkConnection",
                        [ [ "TestDocs" ] ],
                        [ ( "include", "*.txt" ) ],
                        path_value_attribute="pathvalue",
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
    job_id = define_livelink_job_ui( username,
                        password,
                        "Livelink test job",
                        "LivelinkConnection",
                        [ [ "TestDocs" ] ],
                        [ ( "include", "*.txt" ) ],
                        path_value_attribute="pathvalue",
                        mappings=[ [ "^Enterprise/TestDocs/(.*)$", "$(1)" ] ],
                        all_metadata=True )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( id1,port=444 ) ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_livelink_url( id2,port=444 ) ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_livelink_url( id3,port=444 ) ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_livelink_url( id4,port=444 ) ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_livelink_url( id5,port=444 ) ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_livelink_url( id6,port=444 ), build_livelink_url( id11,port=444 ) ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_livelink_url( id7,port=444 ) ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ build_livelink_url( id8,port=444 ) ] )

    # Check if the path metadata attributes also got ingested ok, by searching on them directly
    ConnectorHelpers.search_check( [ "metadata:pathvalue=f001" ], None, [ build_livelink_url( id1,port=444 ) ] )
    ConnectorHelpers.search_check( [ "metadata:pathvalue=f002" ], None, [ build_livelink_url( id2,port=444 ) ] )
    ConnectorHelpers.search_check( [ "metadata:pathvalue=f003" ], None, [ build_livelink_url( id3,port=444 ) ] )
    ConnectorHelpers.search_check( [ "metadata:pathvalue=f004" ], None, [ build_livelink_url( id4,port=444 ) ] )
    ConnectorHelpers.search_check( [ "metadata:pathvalue=f005" ], None, [ build_livelink_url( id5,port=444 ) ] )
    ConnectorHelpers.search_check( [ "metadata:pathvalue=f006" ], None, [ build_livelink_url( id6,port=444 ) ] )
    ConnectorHelpers.search_check( [ "metadata:pathvalue=f007" ], None, [ build_livelink_url( id7,port=444 ) ] )

    # Check if other metadata is found
    ConnectorHelpers.search_check( [ "reference", "metadata:TestMetadata:SimpleText=some_metadata_value" ], None, [ build_livelink_url( id1,port=444 ) ] )

    # Success: done
    print "Done ingestion test."


    # PHASE 2: Document Change Detection

    print "Document Change Test."
    # Modify the documents
    add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f002", "/root/crawlarea/testfiles/f002a.txt")
    add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f004", "/root/crawlarea/testfiles/f004a.txt")
    # Sleep, in case there's clock skew
    wait_for_livelink(llServerName, llServerPort, llUser, llPassword)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Look for state of index being right
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( id1,port=444 ) ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_livelink_url( id3,port=444 ) ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_livelink_url( id5,port=444 ) ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_livelink_url( id6,port=444 ), build_livelink_url( id11,port=444 ) ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_livelink_url( id7,port=444 ) ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ build_livelink_url( id8,port=444 ) ] )
    ConnectorHelpers.search_check( [ "platypus" ], None, [ build_livelink_url( id2,port=444 ) ] )
    ConnectorHelpers.search_check( [ "establishments" ], None, [ build_livelink_url( id4,port=444 ) ] )

    print "Done Document Change Test."

    # PHASE 3: Document Delete Detection

    print "Document Delete Test."
    remove_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/f003" )
    remove_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/f005" )
    # Sleep, in case of clock skew
    wait_for_livelink(llServerName, llServerPort, llUser, llPassword)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ ] )
    print "Done Document Delete Test."

    # PHASE 4: Document Addition Detection

    print "Document Add Test."
    id9 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f009", "/root/crawlarea/testfiles/f009.txt")
    id10 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f010", "/root/crawlarea/testfiles/f010.txt")
    wait_for_livelink(llServerName, llServerPort, llUser, llPassword)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    ConnectorHelpers.search_check( [ "albemarle" ], None, [ build_livelink_url( id9,port=444 ) ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [ build_livelink_url( id10,port=444 ) ] )
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


    ConnectorHelpers.delete_repository_connection_ui( username, password, "LivelinkConnection" )
    # ConnectorHelpers.delete_repositoryconnection( "LivelinkConnection" )

    # Clean up the documents we dumped into the folders on livelink
    for docname in [ "TestDocs/f001", "TestDocs/f002", "TestDocs/f003", "TestDocs/f004", "TestDocs/f005",
                     "TestDocs/f006", "TestDocs/f007", "TestDocs/f007", "TestDocs/newfolder/f008",
                     "TestDocs/f009", "TestDocs/f010", "TestDocs/CompoundDocument/f011" ]:
        remove_document(llServerName, llServerPort, llUser, llPassword, docname )

    # PHASE 6: Security test with ACLs
    # This particular test involves putting documents into Livelink and setting up specific user rights.
    # In addition to creating a job, an authority connection must also be created.
    # Then, the search is done by specific AD users, which should return documents that have only the
    # right permissions.

    # NOTE WELL:  This test uses basic auth to supply the user.  This is because (a) basic auth is not
    # as strongly tested as kerberos/AD is, and (b) the authentication paradigm is orthogonal to what's
    # being tested here, and (c) it's simpler to set up.

    # For single-domain AD mapping, this would be what we'd use:
    # "User name regexp=(.*)\\@([A-Z|a-z|0-9|_|-]*)\\.(.*)"
    # "Livelink user spec=1l"

    # Now, use basic_auth_control to set up the following users on the appliance:
    # llusera
    # lluserb
    # lluserc
    # These correspond to users already present in the livelink instance.

    # Set up system to use basic auth
    ConnectorHelpers.configure_basic_auth( )

    # Enable CF security again
    ConnectorHelpers.enable_connector_framework( )

    ConnectorHelpers.add_basic_auth_user( "llusera", "llusera" )
    ConnectorHelpers.add_basic_auth_user( "lluserb", "lluserb" )
    ConnectorHelpers.add_basic_auth_user( "lluserc", "lluserc" )

    # Add the current set of docs to the repository, and twiddle their security
    id1 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f001", "/root/crawlarea/testfiles/f001.txt")
    id2 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f002", "/root/crawlarea/testfiles/f002.txt")
    # TestDocs/userafolder will be visible by llusera only
    id3 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/userafolder", "f003", "/root/crawlarea/testfiles/f003.txt")
    add_document_right(llServerName, llServerPort, llUser, llPassword, "TestDocs/userafolder/f003", "usera", "")
    id4 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/userafolder", "f004", "/root/crawlarea/testfiles/f004.txt")
    add_document_right(llServerName, llServerPort, llUser, llPassword, "TestDocs/userafolder/f004", "usera", "")
    # TestDocs.userbfolder will be visible by lluserb only
    id5 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/userbfolder", "f005", "/root/crawlarea/testfiles/f005.txt")
    add_document_right(llServerName, llServerPort, llUser, llPassword, "TestDocs/userbfolder/f005", "userb", "")
    id6 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/userbfolder", "f006", "/root/crawlarea/testfiles/f006.txt")
    add_document_right(llServerName, llServerPort, llUser, llPassword, "TestDocs/userbfolder/f006", "userb", "")
    # TestDocs.usercfolder will be visible by lluserc only
    id7 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/usercfolder", "f007", "/root/crawlarea/testfiles/f007.txt")
    add_document_right(llServerName, llServerPort, llUser, llPassword, "TestDocs/usercfolder/f007", "userc", "")
    # TestDocs.newfolder will be visible by both llusera and lluserb, but not userc
    id8 = add_document(llServerName, llServerPort, llUser, llPassword, "TestDocs/newfolder", "f008","/root/crawlarea/testfiles/newfolder/f008.txt")
    add_document_right(llServerName, llServerPort, llUser, llPassword, "TestDocs/newfolder/f008", "usera", "")
    add_document_right(llServerName, llServerPort, llUser, llPassword, "TestDocs/newfolder/f008", "userb", "")

    # Define authority connection
    ConnectorHelpers.define_authorityconnection( "LivelinkConnection",
                                 "Livelink Connection",
                                 "com.metacarta.crawler.connectors.livelink.LivelinkAuthority",
                                 configparams = [ "Server name="+llServerName,
                                                  "Server port="+llServerPort,
                                                  "Server user name="+llUser,
                                                  "Server password="+llPassword,
                                                  "User name regexp=ll(.*)",
                                                  "Livelink user spec=1l" ] )


    # Restart tomcat to be sure there's nothing cached inadvertantly
    ConnectorHelpers.restart_tomcat()
    time.sleep(10)
    
    # Define repository connection
    # For variety, this does not use SSL, but does use NTLM.
    fake_view_server = "blah.metacarta.com"
    define_livelink_repository_connection_ui( username,
                                        password,
                                        "LivelinkConnection",
                                        "Livelink Connection",
                                        llServerName,
                                        llServerPort,
                                        llUser,
                                        llPassword,
                                        "LivelinkConnection",
                                        fetch_port=83,
                                        view_server=fake_view_server,
                                        view_port=80,
                                        fetch_ntlm_domain=llServerName,
                                        fetch_ntlm_username="Administrator",
                                        fetch_ntlm_password="password" )

    # Make sure connection is happy
    ConnectorHelpers.view_repository_connection_ui( username, password, "LivelinkConnection" )

    # Now, create a connection that has a bad password; we should see an error
    define_livelink_repository_connection_ui( username,
                                        password,
                                        "LivelinkConnection2",
                                        "Livelink Connection 2",
                                        llServerName,
                                        llServerPort,
                                        llUser,
                                        llPassword,
                                        "LivelinkConnection",
                                        fetch_port=83,
                                        view_server=fake_view_server,
                                        view_port=80,
                                        fetch_ntlm_domain=llServerName,
                                        fetch_ntlm_username="Administrator",
                                        fetch_ntlm_password="badpassword" )

    ConnectorHelpers.view_repository_connection_ui( username, password, "LivelinkConnection2", match_string="401" )

    # Delete the second connection; we don't need that anymore.
    ConnectorHelpers.delete_repository_connection_ui( username, password, "LivelinkConnection2" )
    
    # PHASE 6: Ingest documents with the specified connectors.
    # There will be several separate kinds of security as well.

    # Define job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="TestDocs"/><include filespec="*.txt"/><security value="on"/></specification>'
    job_id = ConnectorHelpers.define_job( "Livelink test job",
                             "LivelinkConnection",
                             doc_spec_xml )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Check for proper visibility of all documents
    # llusera
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( id1, server=fake_view_server ) ], username="llusera", password="llusera" )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_livelink_url( id3, server=fake_view_server ) ], username="llusera", password="llusera" )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="llusera", password="llusera" )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="llusera", password="llusera" )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="llusera", password="llusera" )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ build_livelink_url( id8, server=fake_view_server ) ], username="llusera", password="llusera" )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_livelink_url( id2, server=fake_view_server ) ], username="llusera", password="llusera" )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_livelink_url( id4, server=fake_view_server ) ], username="llusera", password="llusera" )

    # lluserb
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( id1, server=fake_view_server ) ], username="lluserb", password="lluserb" )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="lluserb", password="lluserb" )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_livelink_url( id5, server=fake_view_server ) ], username="lluserb", password="lluserb" )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_livelink_url( id6, server=fake_view_server ) ], username="lluserb", password="lluserb" )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="lluserb", password="lluserb" )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ build_livelink_url( id8, server=fake_view_server ) ], username="lluserb", password="lluserb" )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_livelink_url( id2, server=fake_view_server ) ], username="lluserb", password="lluserb" )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="lluserb", password="lluserb" )

    # lluserc
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( id1, server=fake_view_server ) ], username="lluserc", password="lluserc" )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="lluserc", password="lluserc" )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="lluserc", password="lluserc" )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="lluserc", password="lluserc" )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_livelink_url( id7, server=fake_view_server ) ], username="lluserc", password="lluserc" )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ ], username="lluserc", password="lluserc" )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_livelink_url( id2, server=fake_view_server ) ], username="lluserc", password="lluserc" )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="lluserc", password="lluserc" )

    # As extra credit, check whether unregistering the livelink authority does the right thing.  This should cause us to get an error back in the
    # UI; however, that's harder to reliably check via TestDocs, so I've opted to use curl directly against the authorityservice webapp.

    # First, "deinstall" the authority.
    ConnectorHelpers.deregister_authorityconnector("com.metacarta.crawler.connectors.livelink.LivelinkAuthority")
    # Restart tomcat, so we don't use existing authority handles
    ConnectorHelpers.restart_tomcat()
    time.sleep(60)

    # Try to ask for tokens
    code,token_return = ConnectorHelpers.ask_authority_webapp("lluserc")
    if code == None or code.find("403") == -1:
        raise Exception("Unregistered authority should force return of 403; instead got '%s'" % token_return)
    ConnectorHelpers.register_authorityconnector("com.metacarta.crawler.connectors.livelink.LivelinkAuthority","LivelinkAuthority")
    # Check once again that we get a reasonable response
    code,token_return = ConnectorHelpers.ask_authority_webapp("lluserc")
    if code != None:
        raise Exception("Reregistered authority should allow proper token return; instead got '%s'" % token_return)


    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    ConnectorHelpers.delete_repository_connection_ui( username, password, "LivelinkConnection" )
    # ConnectorHelpers.delete_repositoryconnection( "LivelinkConnection" )

    ConnectorHelpers.delete_authorityconnection( "LivelinkConnection" )

    # Clean up the documents we dumped into the folders on livelink
    for docname in [ "TestDocs/f001", "TestDocs/f002", "TestDocs/userafolder/f003", "TestDocs/userafolder/f004",
                     "TestDocs/userbfolder/f005", "TestDocs/userbfolder/f006", "TestDocs/usercfolder/f007",
                     "TestDocs/newfolder/f008" ]:
        remove_document(llServerName, llServerPort, llUser, llPassword, docname )

    # Clean up basic auth users
    for username in [ "llusera", "lluserb", "lluserc" ]:
        ConnectorHelpers.delete_basic_auth_user(username)

    ConnectorHelpers.turn_off_basic_auth()

    # Run the ad part of the test
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

    print "Basic LivelinkConnector tests PASSED"
