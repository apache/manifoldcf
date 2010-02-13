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

import os
import sys
import time
import socket
import subprocess
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

# Deploy the contents of the web service installer, and run the specified batch file
def deploy_and_run_bat( ambassador, batch_file_name ):
    """ Copy the contents of the sharepoint mcpermissions web service installer to the target, and run the specified batch file """
    # First, create a temporary folder and unpack the .zip file
    os.mkdir("unzipdir")
    try:
        # Call unzip to do the unzip
        rval = subprocess.call(["unzip","-d","unzipdir","/usr/share/metacarta/MetaCartaSharePointWebServiceInstaller.zip"])
        if rval != 0:
            raise Exception("Couldn't unpack web service installer; error %d" % rval)

        temp_dir = ambassador.remote_temp_dir

        # Use ambassador to deploy and run the batch file in this directory.  The python script needs to invoke cmd.exe in order to make this happen...
        for file_name in ["MetaCarta.SharePoint.MCPermissionsService.wsp","setup.bat","upgrade.bat","remove.bat"]:
            output = ambassador.put_file("unzipdir/%s" % file_name, "%s\\%s" % (temp_dir,file_name))
            if output:
                raise Exception("Copying file %s to target failed: %s" % (file_name,output))

        # Now, run the batch file in question under cmd.exe
        output = ambassador.run("cmd.exe /C \"cd %s && %s\"" % (temp_dir,batch_file_name))
        if output.find("completed successfully") == -1:
            raise Exception("Error while executing %s from remote directory %s: %s" % (batch_file_name,temp_dir,output))

    finally:
        rval = subprocess.call(["rm","-rf","unzipdir"])
        if rval != 0:
            raise Exception("Couldn't clean up unzipdir; error %d" % rval)

# Deploy MCPermissions web service on the specified ambassador
def deploy_mcpermissions_service( ambassador ):
    """ Deploy the MCPermissions service using the supplied .bat files """
    deploy_and_run_bat(ambassador,"setup.bat")

# Remove MCPermissions web service using the specified ambassador
def remove_mcpermissions_service( ambassador ):
    """ Uninstall the MCPermissions service using the supplied .bat files """
    deploy_and_run_bat(ambassador,"remove.bat")

# Create a sharepoint repository connection via the UI
def define_sharepoint_repository_connection_ui( username,
                                        password,
                                        connection_name,
                                        connection_description,
                                        server_version,
                                        server_protocol,
                                        server_name,
                                        server_port,
                                        server_location,
                                        sharepointuser_name,
                                        sharepointuser_password,
                                        sharepoint_domain,
                                        server_certificate_file=None ) :

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
    window.check_no_match("Sharepoint")
    namefield = form.find_textarea("connname")
    descriptionfield = form.find_textarea("description")
    namefield.set_value( connection_name )
    descriptionfield.set_value( connection_description )

    # "Type" tab
    link = window.find_link("Type tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("Sharepoint")
    form = window.find_form("editconnection")
    connectortypefield = form.find_selectbox("classname")
    connectortypefield.select_value( "com.metacarta.crawler.connectors.sharepoint.SharePointRepository" )
    # Click the "Continue" button
    continue_button = window.find_button("Continue to next page")
    continue_button.click( )
    window = vb.find_window("")
    window.check_no_match("Sharepoint")

    # "Server" tab
    link = window.find_link("Server tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("Sharepoint")
    form = window.find_form("editconnection")
    # Set sharepoint-specific stuff
    form = window.find_form("editconnection")
    if server_version != None:
        form.find_selectbox("serverVersion").select_value( server_version )
    if server_protocol != None:
        form.find_selectbox("serverProtocol").select_value( server_protocol )
    form.find_textarea("serverName").set_value( server_name )
    if server_port != None:
        form.find_textarea("serverPort").set_value( server_port )
    if sharepoint_domain != None and sharepointuser_name != None:
        form.find_textarea("userName").set_value( sharepoint_domain + "\\" + sharepointuser_name )
    if sharepointuser_password != None:
        form.find_textarea("password").set_value( sharepointuser_password )
    if server_location != None:
        form.find_textarea("serverLocation").set_value( server_location )
    if server_certificate_file != None:
        # Set the certificate
        form.find_filebrowser("shpcertificate").setfile(server_certificate_file,"application/octet-stream")
        add_button = window.find_button("Add cert")
        add_button.click( )
        window = vb.find_window("")
        form = window.find_form("editconnection")

    # Now, save this page
    save_button = window.find_button("Save this connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    window.check_no_match("Sharepoint")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")

# Define a standard sharepoint job using the UI
def define_sharepoint_job_ui( username,
                password,
                job_name,
                connection_name,
                sitelibs,
                security_enabled=False,
                path_value_attribute=None,
                mappings=None,
                metadatarules=None,
                collection_name=None,
                type="specified",
                startmethod="windowbegin",
                recrawlinterval=0 ):
    """connection_name is the name of the sharepoint connection.  sitelibs is an array of
       rule tuples, each of which is: a subsite path array, an optional lib name, a text path array,
       an optional rule type ("site", "library", or "file"), and an action ("include" or "exclude").
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
    window.check_no_match("Sharepoint")
    # textarea for setting description
    form.find_textarea("description").set_value( job_name )

    # "Connection" tab
    link = window.find_link("Connection tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("Sharepoint")
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
    window.check_no_match("Sharepoint")
    form = window.find_form("editjob")

    # "Collections" tab
    link = window.find_link("Collections tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("Sharepoint")
    form = window.find_form("editjob")
    # textarea for setting collection
    if collection_name != None:
        form.find_textarea("gts_collectionname").set_value( collection_name )

    # "Scheduling" tab
    link = window.find_link("Scheduling tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("Sharepoint")
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
    window.check_no_match("Sharepoint")
    form = window.find_form("editjob")
    # Now, set up paths and matches
    for pathelement in sitelibs:
        sitepath, libname, textpath, type, inclusion = pathelement
        # First, select the subsite and lib
        # Get everything ready to click the "Add" button
        if sitepath != None:
            for selectionelement in sitepath:
                form.find_selectbox("specsite").select_value(selectionelement)
                window.find_button("Add Site to Rule Path").click()
                window = vb.find_window("")
                window.check_no_match("Sharepoint")
                form = window.find_form("editjob")
        # Select the library, if specified
        if libname != None:
            form.find_selectbox("speclibrary").select_value(libname)
            window.find_button("Add Library to Rule Path").click()
            window = vb.find_window("")
            window.check_no_match("Sharepoint")
            form = window.find_form("editjob")
        # Do text matches, if specified
        if textpath != None:
            for textelement in textpath:
                form.find_textarea("specmatch").set_value(textelement)
                window.find_button("Add Text to Rule Path").click()
                window = vb.find_window("")
                window.check_no_match("Sharepoint")
                form = window.find_form("editjob")
        if type != None:
            form.find_selectbox("spectype").set_value(type)
        form.find_selectbox("specflavor").select_value(inclusion)
        window.find_button("Add rule").click()
        window = vb.find_window("")
        window.check_no_match("Sharepoint")
        form = window.find_form("editjob")

    # "Security" tab
    link = window.find_link("Security tab")
    link.click()
    window = vb.find_window("")
    window.check_no_match("Sharepoint")
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
    window.check_no_match("Sharepoint")
    form = window.find_form("editjob")
    # Metadata rules
    if metadatarules != None:
        for metadataelement in metadatarules:
            sitepath, libname, textpath, inclusion, includeall, fieldlist = metadataelement
            # First, select the subsite and lib
            # Get everything ready to click the "Add" button
            if sitepath != None:
                for selectionelement in sitepath:
                    form.find_selectbox("metasite").select_value(selectionelement)
                    window.find_button("Add Site to Metadata Rule Path").click()
                    window = vb.find_window("")
                    window.check_no_match("Sharepoint")
                    form = window.find_form("editjob")
            # Select the library, if specified
            if libname != None:
                form.find_selectbox("metalibrary").select_value(libname)
                window.find_button("Add Library to Metadata Rule Path").click()
                window = vb.find_window("")
                window.check_no_match("Sharepoint")
                form = window.find_form("editjob")
            # Do text matches, if specified
            if textpath != None:
                for textelement in textpath:
                    form.find_textarea("metamatch").set_value(textelement)
                    window.find_button("Add Text to Metadata Rule Path").click()
                    window = vb.find_window("")
                    window.check_no_match("Sharepoint")
                    form = window.find_form("editjob")
            form.find_selectbox("metaflavor").select_value(inclusion)
            if includeall != None and includeall == True:
                form.find_checkbox("metaall","true").select()
            elif fieldlist != None:
                for field in fieldlist:
                    form.find_selectbox("metafields").select_value(field)
            window.find_button("Add rule").click()
            window = vb.find_window("")
            window.check_no_match("Sharepoint")
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
            window.check_no_match("Sharepoint")
            form = window.find_form("editjob")

    # Finally, submit the form
    window.find_button("Save this job").click( )
    window = vb.find_window("")
    window.check_no_match("Sharepoint")
    jobid = window.find_match("<!--jobid=(.*)-->",1)
    return jobid


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

# Method to update a document in the sharepoint repository
def version_document(protocol, servername, port, location, user, password, domain, sharepointurl, filepath, metadata=""):
    """Create a new version of an existing document"""
    add_document(protocol,servername,port,location,user,password,domain,sharepointurl,filepath,metadata)

# Method to wait whatever time is needed after changing sharepoint documents
# for them to be noted as changed.
def wait_for_sharepoint(protocol, servername, port, location, user, password, domain):
    """Nothing needed"""
    pass

# Method to add a user to a site in sharepoint
def add_user_to_site(protocol, servername, port, location, user, password, domain, siteurl, newuser_alias, newuser_display_name, newuser_email, newuser_group):
    """Add a user to a sharepoint site"""
    listparams = [ ConnectorHelpers.process_argument(protocol),
            ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(location),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(domain),
            ConnectorHelpers.process_argument(siteurl),
            ConnectorHelpers.process_argument(newuser_alias),
            ConnectorHelpers.process_argument(newuser_display_name),
            ConnectorHelpers.process_argument(newuser_email),
            ConnectorHelpers.process_argument(newuser_group) ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.sharepoint.AddUserToSite", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="sharepoint-testing-package/metacarta-sharepointconnector-test.jar" )

# Method to remove a user from a site in sharepoint
def remove_user_from_site(protocol, servername, port, location, user, password, domain, siteurl, user_alias):
    """Remove a user from a sharepoint site"""
    listparams = [ ConnectorHelpers.process_argument(protocol),
            ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(location),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(domain),
            ConnectorHelpers.process_argument(siteurl),
            ConnectorHelpers.process_argument(user_alias) ]
    try:
            ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.sharepoint.RemoveUserFromSite", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="sharepoint-testing-package/metacarta-sharepointconnector-test.jar" )
    except Exception, e:
        print "Warning: Error removing user from site: %s" % str(e)

# Method to add a user to a library in sharepoint
#<sharepoint_path> <lib_name> <new_user_name>
def add_user_to_library(protocol, servername, port, location, user, password, domain, siteurl, lib_path, user_alias):
    """Add a user to a sharepoint library"""
    listparams = [ ConnectorHelpers.process_argument(protocol),
            ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(location),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(domain),
            ConnectorHelpers.process_argument(siteurl),
            ConnectorHelpers.process_argument(lib_path),
            ConnectorHelpers.process_argument(user_alias) ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.sharepoint.AddUserToLibrary", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="sharepoint-testing-package/metacarta-sharepointconnector-test.jar" )

# Method to remove a user from a library in sharepoint
def remove_user_from_library(protocol, servername, port, location, user, password, domain, siteurl, lib_path, user_alias):
    """Remove a user from a sharepoint library"""
    listparams = [ ConnectorHelpers.process_argument(protocol),
            ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(location),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(domain),
            ConnectorHelpers.process_argument(siteurl),
            ConnectorHelpers.process_argument(lib_path),
            ConnectorHelpers.process_argument(user_alias) ]
    try:
            ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.sharepoint.RemoveUserFromLibrary", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="sharepoint-testing-package/metacarta-sharepointconnector-test.jar" )
    except Exception, e:
        print "Warning: Error removing user from library: %s" % str(e)

# Disable the ability of the appliance to connect to port 80 on the server
def disable_port_80(server_host):
    """ Use iptables to shut off the ability of appliance to talk to port 80 on server """
    ConnectorHelpers.invoke_root_script( ["iptables", "-A", "OUTPUT", "-d", socket.gethostbyname(server_host), "-p", "tcp", "-m", "multiport", "--destination-ports", "80", "-j", "DROP" ] )

# Re-enable the ability of the AD authority to connect to LDAP
def enable_port_80():
    """ Reverse iptables changes to re-enable appliance to talk to port 80 on server """
    ConnectorHelpers.invoke_root_script( ["iptables", "-D", "OUTPUT", "1" ] )

# Version
sharepointVersion = None
# Server name to talk to
sharepointServerName = None
# Domain
sharepointDomain = None
# Protocol
sharepointProtocol = "http"
# Server port to talk to
sharepointServerPort = "80"
# User
sharepointUser = None
# Password
sharepointPassword = None
# Limited user
sharepointLimitedUser = "limited_crawl_user"
# Limited passward
sharepointLimitedPassword = "password"


def make_sharepoint_url(folder_path, location=""):
    if int(sharepointServerPort) == 80:
        return "%s%s/%s" % (sharepointServerName,location,folder_path)
    else:
        return "%s:%s%s/%s" % (sharepointServerName,sharepointServerPort,location,folder_path)

# Document identifiers; use screwy characters where possible (encoded here)
id1 = "TestDocs/f001%28%29"
id1_unpacked = "TestDocs/f001()"
id2 = "TestDocs/f002%2B"
# We can't use [ and ] because something in the "add_document" webservices
# codepath peels this off and interprets it as a username/password.  So that
# will remain untested for now.
id3 = "TestDocs/f003"
id4 = "TestDocs/f004"
id5 = "TestDocs/f005"
id6 = "TestDocs/f006"
id7 = "TestDocs/f007"
id8 = "TestDocs/newfolder/f008"
id9 = "TestDocs/f009"
id10 = "TestDocs/f010"

id3a =  "TestDocsUserafolder/f003"
id4a =  "TestDocsUserafolder/f004"
id5a =  "TestDocsUserbfolder/f005"
id6a =  "TestDocsUserbfolder/f006"
id7a =  "TestDocsUsercfolder/f007"
id8a =  "TestDocsNewfolder/f008"

id11 = "TestDocsManaged/f011"
id12 = "subsite/TestDocsSubsite/f012"
id13 = "sub%20site%20with%20space/Documents%20With%20Space/f013"

# Crawl user credentials
username = "testingest"
password = "testingest"

def preclean( ad_domain_info, sharepoint_ambassador, server_version, perform_legacy_pass, print_errors=True ):
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

    # Clean up MCPermissions service on sharepoint server
    if server_version == "3_0":
        try:
            remove_mcpermissions_service( sharepoint_ambassador )
        except Exception,e:
            if print_errors:
                print "Error cleaning up MCPermissions"
                print e

    # Remove block on port 80
    try:
        enable_port_80()
    except Exception,e:
        if print_errors:
            print "Error cleaning up iptables"
            print e

    # Remove test documents first
    for folder in [ "/root/crawlarea" ]:
        try:
            delete_folder( folder )
        except Exception, e:
            if print_errors:
                print "Error removing %s" % folder
                print e

    # Clean up the documents we dumped into the folders on sharepoint server
    for docid in [id1,id2,id3,id4,id5,id6,id7,id8,id9,id10,id12,id13,id3a,id4a,id5a,id6a,id7a,id8a]:
        try:
            remove_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, docid )
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % docid
                print e

    try:
        remove_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "/sites/meta%20carta", sharepointUser, sharepointPassword,
                        sharepointDomain, id11)
    except Exception, e:
        if print_errors:
            print "Error deleting test document %s" % id11
            print e

    # Next, disassociate users from libraries
    for library,user in [("TestDocsUserafolder","p_sharepointusera"),("TestDocsUserbfolder","p_sharepointuserb"),("TestDocsUsercfolder","p_sharepointuserc"),
                         ("TestDocsNewfolder","p_sharepointusera"),("TestDocsNewfolder","p_sharepointuserb")]:
        try:
            remove_user_from_library(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, "", library, sharepointDomain + "\\" + user)
        except Exception, e:
            if print_errors:
                print "Error removing %s from %s" % (user,library)
                print e

    # Dissociate users from site
    for user in ["p_sharepointusera","p_sharepointuserb","p_sharepointuserc"]:
        try:
            remove_user_from_site(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, "", sharepointDomain + "\\" + user)
        except Exception, e:
            if print_errors:
                print "Error removing %s from site" % user
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

# This part of the test may be run twice, if we are trying to test legacy ad mode as well as the standard multidomain mode
def run_ad_test_part( ad_domain_info ):
    """ Run the ad-specific part of the test.  For Sharepoint, that's pretty much the whole thing, but I've separated it out to allow
        for expensive setup/teardown if that arises.
    """
    ad_win_host = ad_domain_info.ie_client_fqdn

    print "Joining domain."
    # Set up system to use ad.  The appliance MUST be joined to the domain in order for Sharepoint 2007 to work, because you can't use the fqdn for
    # some of the web services calls (because of apparent Sharepoint bugs)
    ConnectorHelpers.configure_ad( ad_domain_info )

    # PHASE 0: See if SSL works

    print "SSL Test."

    define_sharepoint_repository_connection_ui( username,
                                        password,
                                        "SharePointConnection",
                                        "SharePoint Connection",
                                        sharepointVersion,
                                        "https",
                                        sharepointServerName,
                                        None,
                                        None,
                                        sharepointUser,
                                        sharepointPassword,
                                        sharepointDomain,
                                        server_certificate_file="livelinksrvr/bigiiscax509.cer" )

    # View the connection!
    disable_port_80( sharepointServerName )

    ConnectorHelpers.view_repository_connection_ui( username, password, "SharePointConnection" )

    enable_port_80( )

    ConnectorHelpers.delete_repository_connection_ui( username, password, "SharePointConnection" )

    # PHASE 1: Ingestion

    print "Ingestion Test."

    # Add some docs to the repository
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id1, "/root/crawlarea/testfiles/f001.txt")
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id2, "/root/crawlarea/testfiles/f002.txt")
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id3, "/root/crawlarea/testfiles/f003.txt")
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id4, "/root/crawlarea/testfiles/f004.txt")
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id5, "/root/crawlarea/testfiles/f005.txt")
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id6, "/root/crawlarea/testfiles/f006.txt")
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id7, "/root/crawlarea/testfiles/f007.txt")
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain,id8,"/root/crawlarea/testfiles/newfolder/f008.txt")

    # In case there is clock skew, sleep a minute
    wait_for_sharepoint(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain)


    print "Ingestion with limited permissions..."

    # Define repository connection
    define_sharepoint_repository_connection_ui( username,
                                        password,
                                        "SharePointConnection",
                                        "SharePoint Connection",
                                        sharepointVersion,
                                        sharepointProtocol,
                                        sharepointServerName,
                                        sharepointServerPort,
                                        "",
                                        sharepointLimitedUser,
                                        sharepointLimitedPassword,
                                        sharepointDomain )

    print "Checking whether connector properly detects bad regular expression"
    
    job_id = define_sharepoint_job_ui( username,
                        password,
                        "SharePoint test job",
                        "SharePointConnection",
                        [ ( None, "TestDocs", [ "*" ], None, "include" ) ] ,
                        path_value_attribute="foo",
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
    job_id = define_sharepoint_job_ui( username,
                        password,
                        "SharePoint test job",
                        "SharePointConnection",
                        [ ( None, "TestDocs", [ "*" ], None, "include" ) ] ,
                        security_enabled=True )


    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Nothing should have actually been ingested, because we could not read the library or document permissions, but the job should complete nonetheless.
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

    # Success! Remove the job and the connection
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    ConnectorHelpers.delete_repository_connection_ui( username, password, "SharePointConnection" )

    print "Ingestion with full permissions..."

    # Restart tomcat to make sure nothing is cached
    ConnectorHelpers.restart_tomcat()
    time.sleep(10)
    
    # Define repository connection
    define_sharepoint_repository_connection_ui( username,
                                        password,
                                        "SharePointConnection",
                                        "SharePoint Connection",
                                        sharepointVersion,
                                        sharepointProtocol,
                                        sharepointServerName,
                                        sharepointServerPort,
                                        "",
                                        sharepointUser,
                                        sharepointPassword,
                                        sharepointDomain )

    # This should work; view it to confirm
    ConnectorHelpers.view_repository_connection_ui( username, password, "SharePointConnection" )

    # Now, create a different connection with an incorrect password.  This tests connection pooling.
    define_sharepoint_repository_connection_ui( username,
                                        password,
                                        "SharePointConnection2",
                                        "SharePoint Connection 2",
                                        sharepointVersion,
                                        sharepointProtocol,
                                        sharepointServerName,
                                        sharepointServerPort,
                                        "",
                                        sharepointUser,
                                        "badpassword",
                                        sharepointDomain )

    # This should NOT work; view it to confirm
    ConnectorHelpers.view_repository_connection_ui( username, password, "SharePointConnection2", match_string="401" )

    # Delete unused connection
    ConnectorHelpers.delete_repository_connection_ui( username, password, "SharePointConnection2" )

    # Define job
    job_id = define_sharepoint_job_ui( username,
                        password,
                        "SharePoint test job",
                        "SharePointConnection",
                        [ ( None, "TestDocs", [ "*" ], None, "include" ) ],
                        path_value_attribute = "doc_path",
                        metadatarules = [ ( [ ], None, ["*"], "include", True, None) ] )


    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_sharepoint_url(id1) ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_sharepoint_url(id2) ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_sharepoint_url(id3) ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_sharepoint_url(id4) ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_sharepoint_url(id5) ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_sharepoint_url(id6) ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_sharepoint_url(id7) ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ make_sharepoint_url(id8) ] )

    # Search for metadata too
    id1_path = "/"+id1_unpacked
    ConnectorHelpers.search_check( [ "reference", "metadata:doc_path=%s" % id1_path ], None, [ make_sharepoint_url(id1) ] )
    try:
        ConnectorHelpers.search_check( [ "reference", "metadata:Author=%s\\%s" % (sharepointDomain.split(".")[0],ad_domain_info.realm_admin.split("@")[0].lower()) ], None, [ make_sharepoint_url(id1) ] )
    except Exception,e:
        # The qa-ad-90 sharepoint doesn't put the domain in the author, not sure why the difference ...
        ConnectorHelpers.search_check( [ "reference", "metadata:Author=%s" % (ad_domain_info.realm_admin.split("@")[0].lower()) ], None, [ make_sharepoint_url(id1) ] )

    # Success: done
    print "Done ingestion test."


    # PHASE 2: Document Change Detection

    print "Document Change Test."
    # Modify the documents
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                sharepointDomain, id2, "/root/crawlarea/testfiles/f002a.txt")
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                sharepointDomain, id4, "/root/crawlarea/testfiles/f004a.txt")
    # Sleep, in case there's clock skew
    wait_for_sharepoint(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                sharepointDomain)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Look for state of index being right
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_sharepoint_url(id1) ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_sharepoint_url(id3) ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_sharepoint_url(id5) ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_sharepoint_url(id6) ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_sharepoint_url(id7) ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ make_sharepoint_url(id8) ] )
    ConnectorHelpers.search_check( [ "platypus" ], None, [ make_sharepoint_url(id2) ] )
    ConnectorHelpers.search_check( [ "establishments" ], None, [ make_sharepoint_url(id4) ] )

    print "Done Document Change Test."

    # PHASE 3: Document Delete Detection

    print "Document Delete Test."
    remove_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                sharepointDomain, id3 )
    remove_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                sharepointDomain, id5 )
    # Sleep, in case of clock skew
    wait_for_sharepoint(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                sharepointDomain)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ ] )
    print "Done Document Delete Test."

    # PHASE 4: Document Addition Detection

    print "Document Add Test."
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                sharepointDomain, id9, "/root/crawlarea/testfiles/f009.txt")
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                sharepointDomain, id10, "/root/crawlarea/testfiles/f010.txt")
    wait_for_sharepoint(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                sharepointDomain)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    ConnectorHelpers.search_check( [ "albemarle" ], None, [ make_sharepoint_url(id9) ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [ make_sharepoint_url(id10) ] )
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

    ConnectorHelpers.delete_repository_connection_ui( username, password, "SharePointConnection" )

    # Clean up the documents we dumped into the folders on sharepoint
    for docid in [id1,id2,id3,id4,id5,id6,id7,id8,id9,id10]:
        remove_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, docid )

    # PHASE 5.2: Test if subsites work

    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id12, "/root/crawlarea/testfiles/f001.txt")
    wait_for_sharepoint(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                sharepointDomain)

    # Define repository connection
    define_sharepoint_repository_connection_ui( username,
                                        password,
                                        "SharePointConnection",
                                        "SharePoint Connection",
                                        sharepointVersion,
                                        sharepointProtocol,
                                        sharepointServerName,
                                        sharepointServerPort,
                                        "",
                                        sharepointUser,
                                        sharepointPassword,
                                        sharepointDomain )

    # Define job
    job_id = define_sharepoint_job_ui( username,
                        password,
                        "SharePoint test job",
                        "SharePointConnection",
                        [ ( ["subsite"], "TestDocsSubsite", [ "*" ], None, "include" ) ] )


    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_sharepoint_url(id12) ] )

    print "Job Delete Test."
    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )

    remove_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id12 )


    # PHASE 5.3: Test if subsites and libs with spaces in them work

    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id13, "/root/testfiles/f001.txt")
    wait_for_sharepoint(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                sharepointDomain)

    # Define repository connection
    define_sharepoint_repository_connection_ui( username,
                                        password,
                                        "SharePointConnection",
                                        "SharePoint Connection",
                                        sharepointVersion,
                                        sharepointProtocol,
                                        sharepointServerName,
                                        sharepointServerPort,
                                        "",
                                        sharepointUser,
                                        sharepointPassword,
                                        sharepointDomain )

    # Define job
    job_id = define_sharepoint_job_ui( username,
                        password,
                        "SharePoint test job",
                        "SharePointConnection",
                        [ ( ["sub site with space"], "Documents With Space", [ "*" ], None, "include" ) ] )


    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_sharepoint_url(id13) ] )

    print "Job Delete Test."
    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )

    remove_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id13 )

    # PHASE 5.5: Test if managed paths work - including managed paths with encoded characters

    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "/sites/meta%20carta", sharepointUser, sharepointPassword,
                        sharepointDomain, id11, "/root/crawlarea/testfiles/f001.txt")
    wait_for_sharepoint(sharepointProtocol, sharepointServerName, sharepointServerPort, "/sites/meta%20carta", sharepointUser, sharepointPassword,
                sharepointDomain)

    # Define repository connection
    # Changed case of root site path.
    define_sharepoint_repository_connection_ui( username,
                                        password,
                                        "SharePointConnection",
                                        "SharePoint Connection",
                                        sharepointVersion,
                                        sharepointProtocol,
                                        sharepointServerName,
                                        sharepointServerPort,
                                        "/sites/Meta%20carta",
                                        sharepointUser,
                                        sharepointPassword,
                                        sharepointDomain )

    # Define job
    job_id = define_sharepoint_job_ui( username,
                        password,
                        "SharePoint test job",
                        "SharePointConnection",
                        [ ( None, "TestDocsManaged", [ "*" ], None, "include" ) ] )


    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_sharepoint_url(id11,"/sites/Meta%20carta") ] )

    print "Job Delete Test."
    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )

    remove_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "/sites/meta%20carta", sharepointUser, sharepointPassword,
                        sharepointDomain, id11 )

    # PHASE 6: Security test with AD and ACLs
    # This particular test involves putting documents into Sharepoint and setting up specific user rights.
    # The search is done by specific AD users, which should return documents that have only the
    # right permissions.

    # Now, set up ad with the following users:
    # spusera
    # spuserb
    # spuserc
    # These users have to be added to the sharepoint site and libraries after they are created.

    # Set up system to use ad
    # I've moved this above because the Sharepoint 2007 functionality does not work at the moment unless the appliance is joined to the domain.
    #ConnectorHelpers.configure_ad( ad_domain_info )

    for user in ["p_sharepointusera","p_sharepointuserb","p_sharepointuserc"]:
        ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, user, user )

    # Now, add these users to the root sharepoint site
    for user,description,group in [("p_sharepointusera","User A","UserAGroup"),("p_sharepointuserb","User B","UserBGroup"),("p_sharepointuserc","User C","UserCGroup")]:
        add_user_to_site(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, "", sharepointDomain + "\\" + user, description, user + "@metacarta.com", group)

    # Add these users to the various libraries that need them
    for folder,user in [("TestDocsUserafolder","p_sharepointusera"),("TestDocsUserbfolder","p_sharepointuserb"),("TestDocsUsercfolder","p_sharepointuserc"),
                        ("TestDocsNewfolder","p_sharepointusera"),("TestDocsNewfolder","p_sharepointuserb")]:
        add_user_to_library(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, "", folder, sharepointDomain + "\\" + user)

    # Add the current set of docs to the repository, and twiddle their security
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id1, "/root/crawlarea/testfiles/f001.txt")
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id2, "/root/crawlarea/testfiles/f002.txt")
    # TestDocs.userafolder will be visible by spusera only
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id3a, "/root/crawlarea/testfiles/f003.txt")
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id4a, "/root/crawlarea/testfiles/f004.txt")
    # TestDocs.userbfolder will be visible by spuserb only
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id5a, "/root/crawlarea/testfiles/f005.txt")
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id6a, "/root/crawlarea/testfiles/f006.txt")
    # TestDocs.usercfolder will be visible by spuserc only
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id7a, "/root/crawlarea/testfiles/f007.txt")
    # TestDocs.newfolder will be visible by both spusera and spuserb, but not spuserc
    add_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, id8a, "/root/crawlarea/testfiles/newfolder/f008.txt")


    # Define repository connection
    ConnectorHelpers.define_repositoryconnection( "SharePointConnection",
                                 "SharePoint Connection",
                                 "com.metacarta.crawler.connectors.sharepoint.SharePointRepository",
                                 configparams = [ "serverProtocol="+sharepointProtocol,
                                                  "serverName="+sharepointServerName,
                                                  "serverPort="+sharepointServerPort,
                                                  "serverLocation=",
                                                  "userName="+sharepointDomain+"\\"+sharepointUser,
                                                  "password="+sharepointPassword ] )

    # PHASE 6: Ingest documents with the specified connectors.
    # There will be several separate kinds of security as well.

    # Define a single job that scans multiple libraries, each with their own security
    job_id = define_sharepoint_job_ui( username,
                        password,
                        "SharePoint test job",
                        "SharePointConnection",
                        [ ( None, "TestDocs", [ "*" ], None, "include" ),
                          ( None, "TestDocsUserafolder", [ "*" ], None, "include" ),
                          ( None, "TestDocsUserbfolder", [ "*" ], None, "include" ),
                          ( None, "TestDocsUsercfolder", [ "*" ], None, "include" ),
                          ( None, "TestDocsNewfolder", [ "*" ], None, "include" ) ],
                        security_enabled=True )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Check for proper visibility of all documents
    # usera
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_sharepoint_url(id1) ], username="p_sharepointusera", password="p_sharepointusera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_sharepoint_url(id3a) ], username="p_sharepointusera", password="p_sharepointusera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="p_sharepointusera", password="p_sharepointusera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="p_sharepointusera", password="p_sharepointusera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="p_sharepointusera", password="p_sharepointusera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ make_sharepoint_url(id8a) ], username="p_sharepointusera", password="p_sharepointusera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_sharepoint_url(id2) ], username="p_sharepointusera", password="p_sharepointusera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_sharepoint_url(id4a) ], username="p_sharepointusera", password="p_sharepointusera", win_host=ad_win_host )

    # userb
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_sharepoint_url(id1) ], username="p_sharepointuserb", password="p_sharepointuserb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="p_sharepointuserb", password="p_sharepointuserb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_sharepoint_url(id5a) ], username="p_sharepointuserb", password="p_sharepointuserb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_sharepoint_url(id6a) ], username="p_sharepointuserb", password="p_sharepointuserb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="p_sharepointuserb", password="p_sharepointuserb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ make_sharepoint_url(id8a) ], username="p_sharepointuserb", password="p_sharepointuserb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_sharepoint_url(id2) ], username="p_sharepointuserb", password="p_sharepointuserb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="p_sharepointuserb", password="p_sharepointuserb", win_host=ad_win_host )

    # userc
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_sharepoint_url(id1) ], username="p_sharepointuserc", password="p_sharepointuserc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="p_sharepointuserc", password="p_sharepointuserc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="p_sharepointuserc", password="p_sharepointuserc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="p_sharepointuserc", password="p_sharepointuserc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_sharepoint_url(id7a) ], username="p_sharepointuserc", password="p_sharepointuserc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ ], username="p_sharepointuserc", password="p_sharepointuserc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_sharepoint_url(id2) ], username="p_sharepointuserc", password="p_sharepointuserc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="p_sharepointuserc", password="p_sharepointuserc", win_host=ad_win_host )


    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    ConnectorHelpers.delete_repositoryconnection( "SharePointConnection" )

    # Clean up everything in the environment that may have happened during this block (e.g. after ad was enabled)
    # Remove test documents first
    # Clean up the documents we dumped into the folders on sharepoint server
    for docid in [id1,id2,id3a,id4a,id5a,id6a,id7a,id8a]:
        remove_document(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, docid )

    # Next, disassociate users from libraries
    for folder,user in [("TestDocsUserafolder","p_sharepointusera"),("TestDocsUserbfolder","p_sharepointuserb"),("TestDocsUsercfolder","p_sharepointuserc"),
                        ("TestDocsNewfolder","p_sharepointusera"),("TestDocsNewfolder","p_sharepointuserb")]:
        remove_user_from_library(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                        sharepointDomain, "", folder, sharepointDomain + "\\" + user)

    # Dissociate users from site
    for user in ["p_sharepointusera","p_sharepointuserb","p_sharepointuserc"]:
        remove_user_from_site(sharepointProtocol, sharepointServerName, sharepointServerPort, "", sharepointUser, sharepointPassword,
                    sharepointDomain, "", sharepointDomain + "\\" + user)

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

    sharepointDomain = ad_domain_info.dns_domain.upper()
    sharepointVersion = version

    if version == None:
        server_version = "2_0"
    else:
        server_version = version.replace(".","_")

    sharepoint_domain_variable = "sharepoint_server_v" + server_version
    sharepointServerName = getattr( ad_domain_info, sharepoint_domain_variable + "_fqdn" ).split(".")[0]
    sharepointAmbassador = getattr( ad_domain_info, sharepoint_domain_variable + "_ambassador" )

    # User
    sharepointUser = ad_domain_info.realm_admin.split("@")[0]
    # Password
    sharepointPassword = ad_domain_info.realm_admin_password

    print "Precleaning!"

    preclean( ad_domain_info, sharepointAmbassador, server_version, perform_legacy_pass, print_errors=False )

    if server_version == "3_0":
        print "Deploying SharePoint permissions service"
        deploy_mcpermissions_service( sharepointAmbassador )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up file area."
    copy_folder("/root/testfiles","/root/crawlarea")

    print "Enabling security."
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
    sqatools.appliance.install_license(extra_services=["sharepointConnector"], detect_gdms=True)

    # Set up the ingestion user.

    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # Do the ad-dependent part of the test.
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

    if server_version == "3_0":
        print "Removing SharePoint permissions service"
        remove_mcpermissions_service( sharepointAmbassador )


    print "Basic SharePointConnector tests PASSED"
