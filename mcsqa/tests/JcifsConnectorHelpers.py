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

import ConnectorHelpers
import VirtualBrowser
import sqatools
from wintools import sqa_domain_info
from wintools import filetools
from wintools import ambassador_client
from sqatools import LicenseMakerClient

# Create a jcifs repository connection via the UI
def define_jcifs_repository_connection_ui( username,
                                        password,
                                        connection_name,
                                        connection_description,
                                        jcifs_server,
                                        jcifs_domain,
                                        jcifs_username,
                                        jcifs_password ) :

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
    connectortypefield.select_value( "com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector" )
    # Click the "Continue" button
    continue_button = window.find_button("Continue to next page")
    continue_button.click( )
    window = vb.find_window("")

    # "Server" tab
    link = window.find_link("Server tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    # Set jcifs-specific stuff
    form.find_textarea("server").set_value( jcifs_server )
    if jcifs_domain != None:
        form.find_textarea("domain").set_value( jcifs_domain )
    form.find_textarea("username").set_value( jcifs_username )
    form.find_textarea("password").set_value( jcifs_password )

    # Now, save this page
    save_button = window.find_button("Save this connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")


# Define a standard jcifs job using the UI
def define_jcifs_job_ui( username,
                password,
                job_name,
                connection_name,
                crawlpaths,
                security_enabled=False,
                share_security_enabled=False,
                path_value_attribute=None,
                mappings=None,
                filename_mappings=None,
                url_mappings=None,
                collection_name=None,
                type="specified",
                startmethod="windowbegin",
                recrawlinterval=0 ):
    """connection_name is the name of the jcifs connection.  crawlpaths is an array of
       tuples, each of which is: a path array, and an array of includes/excludes.
       Each include/exclude is a tuple where the first value is either "include" or "exclude",
       the second value is the fingerprint-filedir flag, and is "", "file", "indexable-file", "unindexable-file",
       or "directory", and the third is the match specification.
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

    # "Paths" tab
    link = window.find_link("Paths tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # Now, set up paths and matches
    pathindex = 0
    for pathelement in crawlpaths:
        pathlist, includes_excludes = pathelement
        # First, select the path
        # Get everything ready to click the "Add" button
        if pathlist != None:
            for selectionelement in pathlist:
                form.find_selectbox("pathaddon").select_value(selectionelement)
                window.find_button("Add to path").click()
                window = vb.find_window("")
                form = window.find_form("editjob")
        # Click the "Add path" button
        window.find_button("Add path").click()
        window = vb.find_window("")
        form = window.find_form("editjob")
        # Set up includes/excludes
        for includespecelement in includes_excludes:
            method, fingerprint_filedir, match = includespecelement
            form.find_selectbox("specfl_"+str(pathindex)).select_value(method)
            form.find_selectbox("spectin_"+str(pathindex)).select_value(fingerprint_filedir)
            form.find_textarea("specfile_"+str(pathindex)).set_value(match)
            window.find_button("Add new match for path #"+str(pathindex)).click()
            window = vb.find_window("")
            form = window.find_form("editjob")
        # Go to the next path index
        pathindex += 1

    # "Security" tab
    link = window.find_link("Security tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if security_enabled:
        form.find_radiobutton("specsecurity","on").select()
    else:
        form.find_radiobutton("specsecurity","off").select()
    if share_security_enabled:
        form.find_radiobutton("specsharesecurity","on").select()
    else:
        form.find_radiobutton("specsharesecurity","off").select()

    # Metadata tab
    link = window.find_link("Metadata tab")
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

    # Filename mapping tab
    link = window.find_link("File Mapping tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # Set up mappings
    if filename_mappings != None:
        for mappingelement in filename_mappings:
            match, replace = mappingelement
            form.find_textarea("specfmapmatch").set_value(match)
            form.find_textarea("specfmapreplace").set_value(replace)
            window.find_button("Add to file mappings").click()
            window = vb.find_window("")
            form = window.find_form("editjob")

    # URL mapping tab
    link = window.find_link("URL Mapping tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # Set up mappings
    if url_mappings != None:
        for mappingelement in url_mappings:
            match, replace = mappingelement
            form.find_textarea("specumapmatch").set_value(match)
            form.find_textarea("specumapreplace").set_value(replace)
            window.find_button("Add to URL mappings").click()
            window = vb.find_window("")
            form = window.find_form("editjob")

    # Finally, submit the form
    window.find_button("Save this job").click( )
    window = vb.find_window("")
    jobid = window.find_match("<!--jobid=(.*)-->",1)
    return jobid


# Method to add a document to a jcifs share
def add_document(jcifs_servername, jcifs_user, jcifs_password, targetpath, sourcepath, mode="character"):
    """Add a document to the share"""
    """ The code below does not work, because we get an access violation creating the file.  Not sure
        why... """
    #listparams = [ "/usr/lib/metacarta/jcifs-adddoc",
    #       ConnectorHelpers.process_argument(jcifs_servername),
    #       ConnectorHelpers.process_argument(jcifs_user),
    #       ConnectorHelpers.process_argument(jcifs_password),
    #       ConnectorHelpers.process_argument(targetpath),
    #       ConnectorHelpers.process_argument(sourcepath) ]
    #return ConnectorHelpers.invoke_script( listparams )
    assert mode == "character" or mode =="binary"

    amb = ambassador_client.AmbassadorClient(jcifs_servername+":8000", jcifs_user, jcifs_password)
    targetpath = "C:\\"+targetpath.replace("/","\\")
    permissions = [ ("+", ConnectorHelpers.get_everyone_sid()) ]
    if mode == "character":
        fd = open(sourcepath, "r")
        try:
            lines = fd.readlines()
            newlines = []
            for line in lines:
                newlines.append( line.strip() )
            string = " ".join(newlines)
            filetools.create_windows_file(targetpath, permissions, string, amb, mode="character")
            return targetpath
        finally:
            fd.close()
    else:
        fd = open(sourcepath, "rb")
        try:
            data = fd.read()
            filetools.create_windows_file(targetpath, permissions, data, amb, mode="binary")
            return targetpath
        finally:
            fd.close()

# Method to remove a document from a jcifs share
def remove_document(jcifs_servername, jcifs_user, jcifs_password, targetpath):
    """Remove a document from the share"""
    #listparams = [ "/usr/lib/metacarta/jcifs-removedoc",
    #       ConnectorHelpers.process_argument(jcifs_servername),
    #       ConnectorHelpers.process_argument(jcifs_user),
    #       ConnectorHelpers.process_argument(jcifs_password),
    #       ConnectorHelpers.process_argument(targetpath) ]
    #try:
    #    ConnectorHelpers.invoke_script( listparams )
    #except Exception, e:
    #    print "Warning: Error deleting document: %s" % str(e)
    print "Erasing %s" % targetpath

    amb = ambassador_client.AmbassadorClient(jcifs_servername+":8000", jcifs_user, jcifs_password)
    targetpath = "C:\\"+targetpath.replace("/","\\")

    try:
        amb.run('erase "%s"' % targetpath)
    except Exception, e:
        print "Warning: Error deleting document: %s" % str(e)

# Method to update a document in the jcifs repository
def version_document(jcifs_servername, jcifs_user, jcifs_password, targetpath, sourcepath):
    """Create a new version of an existing document"""
    amb = ambassador_client.AmbassadorClient(jcifs_servername+":8000", jcifs_user, jcifs_password)
    targetpath = "C:\\"+targetpath.replace("/","\\")
    try:
        amb.run('erase "%s"' % targetpath)
    except Exception, e:
        print "Warning: Error deleting document: %s" % str(e)

    permissions = [ ("+", ConnectorHelpers.get_everyone_sid()) ]
    fd = open(sourcepath, "r")
    try:
        lines = fd.readlines()
        newlines = []
        for line in lines:
            newlines.append( line.strip() )
        string = " ".join(newlines)
        filetools.create_windows_file(targetpath, permissions, string, amb)
    finally:
        fd.close()

# Method to wait whatever time is needed after changing jcifs documents
# for them to be noted as changed.
def wait_for_jcifs(jcifs_servername, jcifs_user, jcifs_password):
    """Nothing needed"""
    pass

# Method for setting permissions on a folder or file
def set_target_permission(jcifs_servername, jcifs_user, jcifs_password, targetpath, usernames):
    amb = ambassador_client.AmbassadorClient(jcifs_servername+":8000", jcifs_user, jcifs_password)
    targetpath = "C:\\" + targetpath.replace("/","\\")
    permissions = []
    for username in usernames:
        permissions.append( ("+",ConnectorHelpers.get_ad_user_sid(username),0x7FFF0000) )
    filetools.change_windows_file_acl(targetpath, permissions, amb)
