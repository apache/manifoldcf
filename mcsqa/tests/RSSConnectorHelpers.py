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
from wintools import filetools
from wintools import ambassador_client
from sqatools import LicenseMakerClient
import TestDocs
import VirtualBrowser

# Create a sharepoint repository connection via the UI
def define_rss_repository_connection_ui( username,
                                        password,
                                        connection_name,
                                        connection_description,
                                        email_address,
                                        robots_value="all",
                                        max_repository_connections=None,
                                        throttles=None,
                                        max_kbytes_per_second_per_server=None,
                                        max_connections_per_server=None,
                                        max_fetches_per_minute_per_server=None,
                                        proxy_host=None,
                                        proxy_port=None,
                                        proxy_domain=None,
                                        proxy_username=None,
                                        proxy_password=None ) :

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
    connectortypefield.select_value( "com.metacarta.crawler.connectors.rss.RSSConnector" )
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
    # Set rss-specific stuff
    form = window.find_form("editconnection")
    form.find_textarea("email").set_value( email_address )

    # "Robots" tab
    link = window.find_link("Robots tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    # RSS robots selection
    if robots_value != None:
        form.find_selectbox("robotsusage").select_value(robots_value)

    # "Bandwidth" tab
    link = window.find_link("Bandwidth tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    # RSS bandwidth fields
    if max_kbytes_per_second_per_server != None:
        form.find_textarea("bandwidth").set_value(str(max_kbytes_per_second_per_server))
    if max_connections_per_server != None:
        form.find_textarea("connections").set_value(str(max_connections_per_server))
    if max_fetches_per_minute_per_server != None:
        form.find_textarea("fetches").set_value(str(max_fetches_per_minute_per_server))

    # "Proxy" tab
    link = window.find_link("Proxy tab")
    link.click();
    window = vb.find_window("")
    form = window.find_form("editconnection")
    if proxy_host != None:
        form.find_textarea("proxyhost").set_value( proxy_host )
    if proxy_port != None:
        form.find_textarea("proxyport").set_value( str(proxy_port) )
    if proxy_domain != None:
        form.find_textarea("proxyauthdomain").set_value( proxy_domain )
    if proxy_username != None:
        form.find_textarea("proxyauthusername").set_value( proxy_username )
    if proxy_password != None:
        form.find_textarea("proxyauthpassword").set_value( proxy_password )

    # Now, save this page
    save_button = window.find_button("Save this connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")

# Define a standard rss job using the UI
def define_rss_job_ui( username,
                password,
                job_name,
                connection_name,
                URLs,
                mappings=None,
                user_metadata=None,
                feed_connect_timeout_seconds=None,
                default_feed_refetch_time_minutes=None,
                min_feed_refetch_time_minutes=None,
                bad_feed_refetch_time_minutes=None,
                dechromed_mode=None,
                chromed_mode=None,
                canonicalization_rules=None,
                forced_acls=None,
                collection_name=None,
                type="specified",
                startmethod="windowbegin",
                recrawlinterval=0,
                expireinterval=None ):
    """connection_name is the name of the rss connection.  URLs is the array of seed urls.
       Mappings are an array of tuples of the form: (regexp, mapping_string)
       Legal values for type are: "specified" or "continuous"
       Legal values for start method are: "windowbegin", "windowinside", or "disable".
       Legal values for dechromed_mode are None, "content", or "description".
       Legal values for chromed_mode are None, "use", or "skip".
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
        if recrawlinterval!=None:
            form.find_textarea("recrawlinterval").set_value( str(recrawlinterval) )
        if expireinterval!=None:
            form.find_textarea("expirationinterval").set_value( str(expireinterval) )

    # Do mappings first, to check the validity of the other tabs (test for proper fix to 17271)
    if mappings != None:
        link = window.find_link("Mappings tab")
        link.click()
        window = vb.find_window("")
        form = window.find_form("editjob")
        for regexp, value in mappings:
            form.find_textarea("rssmatch").set_value( regexp )
            form.find_textarea("rssmap").set_value( value )
            window.find_button("Add regexp").click()
            # Reload
            window = vb.find_window("")
            form = window.find_form("editjob")

    # "URLs" tab
    link = window.find_link("URLs tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # Now, set up seed urls
    url_string = ""
    for url in URLs:
        # Append each url to the string with a newline separator
        url_string = url_string + url + "\n"
    form.find_textarea("rssurls").set_value(url_string)

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
            
    # "Time Values" tab
    link = window.find_link("Time Values tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if feed_connect_timeout_seconds!=None:
        form.find_textarea("feedtimeout").set_value(str(feed_connect_timeout_seconds))
    if default_feed_refetch_time_minutes!=None:
        form.find_textarea("feedrefetch").set_value(str(default_feed_refetch_time_minutes))
    if min_feed_refetch_time_minutes!=None:
        form.find_textarea("minfeedrefetch").set_value(str(min_feed_refetch_time_minutes))
    if bad_feed_refetch_time_minutes!=None:
        form.find_textarea("badfeedrefetch").set_value(str(bad_feed_refetch_time_minutes))

    # "Dechromed Content" tab
    link = window.find_link("Dechromed Content tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if dechromed_mode != None:
        form.find_radiobutton("dechromedmode",dechromed_mode).select()
    if chromed_mode != None:
        form.find_radiobutton("chromedmode",chromed_mode).select()

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

    # "Security" tab
    link = window.find_link("Security tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if forced_acls != None:
        for access_token in forced_acls:
            # Set the textbox
            form.find_textarea("spectoken").set_value(access_token)
            window.find_button("Add access token").click()
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
