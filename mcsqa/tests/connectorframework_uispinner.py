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
import os
import sys
import time
import traceback
import ConnectorHelpers
from sqatools import LicenseMakerClient
import VirtualBrowser

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

# Create a file system repository connection via the UI
def define_filesystem_repository_connection_ui( username, password, connection_name, connection_description,
                                                                        throttles=None ):
    """ The throttles argument is an array of tuples.  Each tuple represents a throttle and is of the form (regexp,description,avg-fetch-rate).
    """
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
    connectortypefield.select_value( "com.metacarta.crawler.connectors.filesystem.FileConnector" )
    # Click the "Continue" button
    continue_button = window.find_button("Continue to next page")
    continue_button.click( )
    window = vb.find_window("")

    # "Throttling" tab
    link = window.find_link("Throttling tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")

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

    # Now, save this page
    save_button = window.find_button("Save this connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")

# Edit file system repository connection via the UI (for BPA spinner test)
def resave_filesystem_repository_connection_ui( username, password, connection_name ):
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for repository connection management and click it
    window = vb.find_window("")
    link = window.find_link("List repository connections")
    link.click( )

    # Now, find the delete link for this connection
    window = vb.find_window("")
    link = window.find_link("Edit "+connection_name)
    link.click( )

    # Find the "save" button
    window = vb.find_window("")
    link = window.find_button("Save this connection").click();

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Edited connection doesn't match")

# Delete a file system repository connection via the UI
def delete_filesystem_repository_connection_ui( username, password, connection_name ):
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for repository connection management and click it
    window = vb.find_window("")
    link = window.find_link("List repository connections")
    link.click( )

    # Now, find the delete link for this connection
    window = vb.find_window("")
    link = window.find_link("Delete "+connection_name)
    link.click( )

    # Verify that the connection was deleted
    window = vb.find_window("")
    # simply make sure it's not an error screen
    window.find_match("List of Repository Connections")

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
        license.revoke_license()
    except Exception, e:
        if print_errors:
            print "Error revoking license"
            print e

    try:
        ConnectorHelpers.delete_crawler_user( username )
    except Exception, e:
        if print_errors:
            print "Error removing crawler user"
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

    ConnectorHelpers.preclean( username, print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up license."
    license = LicenseMakerClient.LicenseParams(MetaCartaVersion.major,
                                               MetaCartaVersion.minor,
                                               MetaCartaVersion.patch)
    license.enable_search_feature( "webSearch" )
    license.enable_search_feature( "soapSearch" )
    license.set_flag('energyExtractor', 'false')
    license.set_flag('geoTaggerCountryTagger' , 'false')
    license.set_flag('geotagger','true')
    license.set_flag('multiAppliance','false')
    license.set_flag('multitagger','true')
    license.set_flag('timetagger','false')
    license.set_flag('usStreetAddressGDM','false')
    license.write_license()

    ConnectorHelpers.create_crawler_user( username, password )

    # Create standard output connection
    ConnectorHelpers.define_gts_outputconnection( )
    
    # Spin test

    print "Creating repository connection."

    # Define repository connection
    # ConnectorHelpers.define_repositoryconnection( "FileSystem",
    #                            "FileSystem Connection",
    #                            "com.metacarta.crawler.connectors.filesystem.FileConnector" )
    # Do via the UI, with one stupid throttle (to test that part of the UI)
    define_filesystem_repository_connection_ui( username, password, "FileSystem", "FileSystem Connection",throttles=[("",None,"20000")] )

    print "Spinning...."

    # Spinner test to make sure we aren't leaking file descriptors from tomcat for BPA callout.
    # 3 handles will be leaked each iteration, if broken, out of a max number of 1024.
    for counter in range(1,10240):
        print "Iteration %d..." % counter
        resave_filesystem_repository_connection_ui( username, password, "FileSystem" )

    print "Cleaning up."

    delete_filesystem_repository_connection_ui( username, password, "FileSystem" )

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )
    
    license.revoke_license()
    ConnectorHelpers.teardown_connector_environment( )

    print "UI Spinner ConnectorFramework test PASSED"
