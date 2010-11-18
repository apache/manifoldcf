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
import ConnectorHelpers
import sqatools.appliance
from wintools import sqa_domain_info
from wintools import filetools
from wintools import ambassador_client
from sqatools import LicenseMakerClient
import VirtualBrowser

# Create a web repository connection via the UI
def define_web_repository_connection_ui( username,
                                        password,
                                        connection_name,
                                        connection_description,
                                        email_address,
                                        robots_value="all",
                                        max_repository_connections=None,
                                        throttles=None,
                                        limits=None,
                                        page_access_credentials=[],
                                        session_access_credentials=[],
                                        certificates=[] ) :

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
    connectortypefield.select_value( "com.metacarta.crawler.connectors.webcrawler.WebcrawlerConnector" )
    # Click the "Continue" button
    continue_button = window.find_button("Continue to next page")
    continue_button.click( )
    window = vb.find_window("")

    # "Throttling" tab
    link = window.find_link("Throttling tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    if max_repository_connections != None:
        form.find_textarea("maxconnections").set_value( str(max_repository_connections) )
    if throttles != None:
        for throttle in throttles:
            regexp,description,rate = throttle
            # Add a throttle with the specified parameters
            regexpfield = form.find_textarea("throttle")
            descfield = form.find_textarea("throttledesc")
            valuefield = form.find_textarea("throttlevalue")
            regexpfield.set_value( regexp )
            if description != None:
                descfield.set_value( description )
            valuefield.set_value( rate )
            add_button = window.find_button("Add throttle")
            add_button.click()
            window = vb.find_window("")
            form = window.find_form("editconnection")

    # "Email" tab
    link = window.find_link("Email tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    # Set web-specific stuff
    form = window.find_form("editconnection")
    form.find_textarea("email").set_value( email_address )

    # "Robots" tab
    link = window.find_link("Robots tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    # WEB robots selection
    if robots_value != None:
        form.find_selectbox("robotsusage").select_value(robots_value)

    # "Bandwidth" tab
    link = window.find_link("Bandwidth tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    # Do the throttles, if any
    if limits != None:
        for throttle in limits:
            # Each throttle consists of a dictionary with the following fields: "regexp", "insensitive", "connections", "kbpersecond", "fetchesperminute".
            if throttle.has_key("regexp"):
                form.find_textarea("regexp_bandwidth").set_value(throttle["regexp"])
            else:
                form.find_textarea("regexp_bandwidth").set_value("()")
            if throttle.has_key("insensitive"):
                insensitive = (throttle["insensitive"] == True)
            else:
                insensitive = False
            if insensitive == True:
                form.find_checkbox("insensitive_bandwidth").check()
            if throttle.has_key("connections"):
                form.find_textarea("connections_bandwidth").set_value(str(throttle["connections"]))
            if throttle.has_key("kbpersecond"):
                form.find_textarea("rate_bandwidth").set_value(str(throttle["kbpersecond"]))
            if throttle.has_key("fetchesperminute"):
                form.find_textarea("fetches_bandwidth").set_value(str(throttle["fetchesperminute"]))
            # Click the "add" button
            window.find_button("Add bin regular expression").click()
            window = vb.find_window("")
            form = window.find_form("editconnection")

    # Access credentials tab
    link = window.find_link("Access Credentials tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    # Do the individual page credentials, if any
    for credential in page_access_credentials:
        # Each credential is a dictionary with the following fields: "regexp", "type", "domain, "username", "password".  The understood types are
        # "basic" and "ntlm".
        if credential.has_key("regexp"):
            form.find_textarea("regexp_acredential").set_value(credential["regexp"])
        if credential.has_key("type"):
            form.find_radiobutton("type_acredential",credential["type"]).select()
        if credential.has_key("domain"):
            form.find_textarea("domain_acredential").set_value(credential["domain"])
        if credential.has_key("username"):
            form.find_textarea("username_acredential").set_value(credential["username"])
        if credential.has_key("password"):
            form.find_textarea("password_acredential").set_value(credential["password"])
        window.find_button("Add page authentication url regular expression").click()
        window = vb.find_window("")
        form = window.find_form("editconnection")
    # Do the individual session credentials, if any
    session_access_credential_index = 0
    for credential in session_access_credentials:
        # Credential is a dictionary with fields: "regexp", "loginpages".
        # "loginpages" contains an array of login page descriptions, see below.
        if credential.has_key("regexp"):
            form.find_textarea("scredential_regexp").set_value(credential["regexp"])
        window.find_button("Add session authentication url regular expression").click()
        window = vb.find_window("")
        form = window.find_form("editconnection")
        prefix = "scredential_%d" % session_access_credential_index
        if credential.has_key("loginpages"):
            loginpages = credential["loginpages"]
            login_page_index = 0
            for loginpage in loginpages:
                # Each loginpage is a dictionary consisting of the fields "regexp", "pagetype", "matchexpr", and optionally "parameters".
                # The understood page types are: "form", "link", and "redirection".

                # We have to add the login pages to the row we just added, which has a certain index (that must be used to locate the right form elements)
                if loginpage.has_key("regexp"):
                    form.find_textarea("%s_loginpageregexp" % prefix).set_value(loginpage["regexp"])
                if loginpage.has_key("pagetype"):
                    form.find_radiobutton("%s_loginpagetype" % prefix, loginpage["pagetype"]).select()
                if loginpage.has_key("matchexpr"):
                    form.find_textarea("%s_loginpagematchregexp" % prefix).set_value(loginpage["matchexpr"])
                # Click the 'add' button
                window.find_button("Add login page to credential #%d" % (session_access_credential_index+1)).click()
                window = vb.find_window("")
                form = window.find_form("editconnection")
                login_prefix = "%s_%d" % (prefix,login_page_index)
                if loginpage.has_key("parameters"):
                    parameters = loginpage["parameters"]
                    for parameter in parameters:
                        # Each parameter consists of a dictionary with these fields: "nameregexp", "value", "password"
                        if parameter.has_key("nameregexp"):
                            form.find_textarea("%s_loginparamname" % login_prefix).set_value(parameter["nameregexp"])
                        if parameter.has_key("value"):
                            form.find_textarea("%s_loginparamvalue" % login_prefix).set_value(parameter["value"])
                        if parameter.has_key("password"):
                            form.find_textarea("%s_loginparampassword" % login_prefix).set_value(parameter["password"])
                        # Click the "add" button
                        window.find_button("Add parameter to login page #%d for credential #%d" % (login_page_index+1,session_access_credential_index+1)).click()
                        window = vb.find_window("")
                        form = window.find_form("editconnection")
                login_page_index += 1
        session_access_credential_index += 1
        
    # Certificates tab
    link = window.find_link("Certificates tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    # Set the certificates
    for certificate in certificates:
        # Each certificate is a dictionary with the fields: "regexp", and "certificate".  The "certificate" field contains a file name.
        if certificate.has_key("regexp"):
            form.find_textarea("regexp_trust").set_value(certificate["regexp"])
        if certificate.has_key("certificate"):
            form.find_filebrowser("certificate_trust").setfile(certificate["certificate"],"application/octet-stream")
        window.find_button("Add url regular expression for truststore").click()
        window = vb.find_window("")
        form = window.find_form("editconnection")

    # Now, save this page
    save_button = window.find_button("Save this connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")

# Define a standard web job using the UI
def define_web_job_ui( username,
                password,
                job_name,
                connection_name,
                URLs,
                inclusions=None,
                exclusions=None,
                user_metadata=None,
                canonicalization_rules=None,
                collection_name=None,
                type="specified",
                startmethod="windowbegin",
                recrawlinterval=0 ):
    """connection_name is the name of the web connection.  URLs is the array of seed urls.
       Legal values for type are: "specified" or "continuous"
       Legal values for start method are: "windowbegin", "windowinside", or "disable".
       user_metadata is an array of tuples, each tuple having the form:
         ( name, value )
       canonicalization_rules is an array of tuples, each tuple having the form:
         ( regexp, description, allow_reordering, java_session_removal, asp_session_removal, php_session_removal, bv_session_removal)
       ... where the regexp and description fields are strings, and the other fields are None, or "yes", or "no".
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

    # "Seeds" tab
    link = window.find_link("Seeds tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # Now, set up seed urls
    url_string = ""
    for url in URLs:
        # Append each url to the string with a newline separator
        url_string = url_string + url + "\n"
    form.find_textarea("seeds").set_value(url_string)

    # "Inclusions" tab
    link = window.find_link("Inclusions tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if inclusions != None:
        inclusion_string = ""
        for inclusion in inclusions:
            inclusion_string = inclusion_string + inclusion + "\n"
        form.find_textarea("inclusions").set_value(inclusion_string)

    # "Exclusions" tab
    link = window.find_link("Exclusions tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if exclusions != None:
        exclusion_string = ""
        for exclusion in exclusions:
            exclusion_string = exclusion_string + exclusion + "\n"
        form.find_textarea("exclusions").set_value(exclusion_string)

    # "Metadata" tab
    link = window.find_link("Metadata tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if user_metadata != None:
        for element in user_metadata:
            name, value = element
            form.find_textarea("specmetaname").set_value(str(name))
            form.find_textarea("specmetavalue").set_value(str(value))
            window.find_button("Add metadata").click()
            window = vb.find_window("")
            form = window.find_form("editjob")

    # "Canonicalization" tab
    link = window.find_link("Canonicalization tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if canonicalization_rules != None:
        # Loop through the tuples and add them one at a time
        for rule in canonicalization_rules:
            regexp, description, allow_reorder, remove_java, remove_asp, remove_php, remove_bv = rule
            form.find_textarea("urlregexp").set_value(regexp)
            if description != None:
                form.find_textarea("urlregexpdesc").set_value(description)
            if allow_reorder != None:
                if allow_reorder == "yes":
                    form.find_checkbox("urlregexpreorder", "yes").select()
                else:
                    form.find_checkbox("urlregexpreorder", "yes").deselect()
            if remove_java != None:
                if remove_java == "yes":
                    form.find_checkbox("urlregexpjava", "yes").select()
                else:
                    form.find_checkbox("urlregexpjava", "yes").deselect()
            if remove_asp != None:
                if remove_asp == "yes":
                    form.find_checkbox("urlregexpasp", "yes").select()
                else:
                    form.find_checkbox("urlregexpasp", "yes").deselect()
            if remove_php != None:
                if remove_php == "yes":
                    form.find_checkbox("urlregexpphp", "yes").select()
                else:
                    form.find_checkbox("urlregexpphp", "yes").deselect()
            if remove_bv != None:
                if remove_bv == "yes":
                    form.find_checkbox("urlregexpbv", "yes").select()
                else:
                    form.find_checkbox("urlregexpbv", "yes").deselect()
            # Click the "Add" button
            window.find_button("Add url regexp").click()
            window = vb.find_window("")
            form = window.find_form("editjob")

    # Finally, submit the form
    window.find_button("Save this job").click( )
    window = vb.find_window("")
    jobid = window.find_match("<!--jobid=(.*)-->",1)
    return jobid

# Method to add a document to a jcifs share
def add_document(server_servername, server_user, server_password, targetpath, sourcepath, map=None):
    """Add a document to the share"""
    """ The code below does not work, because we get an access violation creating the file.  Not sure
        why... """
    #listparams = [ "/usr/lib/metacarta/jcifs-adddoc",
    #       ConnectorHelpers.process_argument(server_servername),
    #       ConnectorHelpers.process_argument(server_user),
    #       ConnectorHelpers.process_argument(server_password),
    #       ConnectorHelpers.process_argument(targetpath),
    #       ConnectorHelpers.process_argument(sourcepath) ]
    #return ConnectorHelpers.invoke_script( listparams )
    amb = ambassador_client.AmbassadorClient(server_servername+":8000", server_user, server_password)
    targetpath = "C:\\"+targetpath.replace("/","\\")
    permissions = [ ("+", ConnectorHelpers.get_everyone_sid()) ]
    fd = open(sourcepath, "r")
    try:
        lines = fd.readlines()
        newlines = []
        for line in lines:
            if map != None:
                    # For each key in the map, do substitution
                for key in map.keys():
                    apart = line.split(key)
                    line = map[key].join(apart)
            newlines.append( line.strip() )
        string = "\n".join(newlines)
        filetools.create_windows_file(targetpath, permissions, string, amb)
        return targetpath
    finally:
        fd.close()

# Method to remove a document from a jcifs share
def remove_document(server_servername, server_user, server_password, targetpath):
    """Remove a document from the server"""
    #listparams = [ "/usr/lib/metacarta/jcifs-removedoc",
    #       ConnectorHelpers.process_argument(server_servername),
    #       ConnectorHelpers.process_argument(server_user),
    #       ConnectorHelpers.process_argument(server_password),
    #       ConnectorHelpers.process_argument(targetpath) ]
    #try:
    #    ConnectorHelpers.invoke_script( listparams )
    #except Exception, e:
    #    print "Warning: Error deleting document: %s" % str(e)
    print "Erasing %s" % targetpath

    amb = ambassador_client.AmbassadorClient(server_servername+":8000", server_user, server_password)
    targetpath = "C:\\"+targetpath.replace("/","\\")

    try:
        amb.run('erase "%s"' % targetpath)
    except Exception, e:
        print "Warning: Error deleting document: %s" % str(e)

# Method to update a document in the jcifs repository
def version_document(server_servername, server_user, server_password, targetpath, sourcepath):
    """Create a new version of an existing document"""
    amb = ambassador_client.AmbassadorClient(server_servername+":8000", server_user, server_password)
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
