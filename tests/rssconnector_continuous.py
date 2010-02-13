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

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

# Create a sharepoint repository connection via the UI
def define_rss_repository_connection_ui( username,
                                        password,
                                        connection_name,
                                        connection_description,
                                        email_address,
                                        robots_value="all",
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
        form.find_textarea("bandwidth").set_value(max_kbytes_per_second_per_server)
    if max_connections_per_server != None:
        form.find_textarea("connections").set_value(max_connections_per_server)
    if max_fetches_per_minute_per_server != None:
        form.find_textarea("fetches").set_value(max_fetches_per_minute_per_server)

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
                feed_connect_timeout_seconds=None,
                default_feed_refetch_time_minutes=None,
                min_feed_refetch_time_minutes=None,
                dechromed_mode=None,
                chromed_mode=None,
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
        form.find_textarea("collection").set_value( collection_name )

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

    # "Dechromed Content" tab
    link = window.find_link("Dechromed Content tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if dechromed_mode != None:
        form.find_radiobutton("dechromedmode",dechromed_mode).select()
    if chromed_mode != None:
        form.find_radiobutton("chromedmode",chromed_mode).select()

    # Finally, submit the form
    window.find_button("Save this job").click( )
    window = vb.find_window("")
    jobid = window.find_match("<!--jobid=(.*)-->",1)
    return jobid

def change_rss_job_ui( username,
                password,
                jobid,
                job_name=None,
                URLs=None,
                mappings=None,
                feed_connect_timeout_seconds=None,
                default_feed_refetch_time_minutes=None,
                min_feed_refetch_time_minutes=None,
                dechromed_mode=None,
                chromed_mode=None,
                collection_name=None,
                type=None,
                startmethod=None,
                recrawlinterval=None,
                expireinterval=None ):
    """connection_name is the name of the rss connection.  URLs is the array of seed urls.
       Mappings are an array of tuples of the form: (regexp, mapping_string)
       Legal values for type are: "specified" or "continuous"
       Legal values for start method are: "windowbegin", "windowinside", or "disable".
       Legal values for dechromed_mode are None, "content", or "description".
       Legal values for chromed_mode are None, "use", or "skip".
    """

    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("List jobs")
    if link == None:
        raise Exception("Can't find list link for jobs");
    link.click( )

    # Grab the new window
    window = vb.find_window("")
    # Find the delete link
    editlink = window.find_link("Edit job "+jobid)
    if editlink == None:
        raise Exception("Can't find edit link for job %s" % jobid)
    editlink.click( )

    # Grab the edit window
    window = vb.find_window("")
    # Start setting stuff in the form
    form = window.find_form("editjob")

    # "Name" tab
    # textarea for setting description
    if job_name != None:
        form.find_textarea("description").set_value( job_name )

    # textarea for setting collection
    if collection_name != None:
        form.find_textarea("collection").set_value( collection_name )

    # "Connection" tab
    link = window.find_link("Connection tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # start method
    if startmethod != None:
        if startmethod == "windowbegin":
            startmethod_value = 0
        elif startmethod == "windowinside":
            startmethod_value = 1
        elif startmethod == "disable":
            startmethod_value = 2
        else:
            raise Exception("Illegal start method value: '%s'" % startmethod )
        form.find_selectbox("startmethod").select_value( str(startmethod_value) )

    # "Scheduling" tab
    link = window.find_link("Scheduling tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")

    # type
    if type != None:
        if type == "specified":
            type_value = 1
        elif type == "continuous":
            type_value = 0
        else:
            raise Exception("Illegal type value: '%s'" % type )
        form.find_selectbox("scheduletype").select_value( str(type_value) )

    # Recrawl interval
    if recrawlinterval!=None:
        form.find_textarea("recrawlinterval").set_value( str(recrawlinterval) )

    # Expire interval
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
    if URLs != None:
        url_string = ""
        for url in URLs:
            # Append each url to the string with a newline separator
            url_string = url_string + url + "\n"
        form.find_textarea("rssurls").set_value(url_string)

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

    # "Dechromed Content" tab
    link = window.find_link("Dechromed Content tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if dechromed_mode != None:
        form.find_radiobutton("dechromedmode",dechromed_mode).select()
    if chromed_mode != None:
        form.find_radiobutton("chromedmode",chromed_mode).select()

    # Finally, submit the form
    window.find_button("Save this job").click( )
    window = vb.find_window("")
    find_jobid = window.find_match("<!--jobid=(.*)-->",1)
    if find_jobid != jobid:
        raise Exception("Job update failed!")

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

# Server name to talk to
rssServerName = None
# Domain
rssDomain = None
# Server port to talk to
rssServerPort = "81"
# User
rssUser = "Administrator"
# Password
rssPassword = "password"

# Names of feeds
feed_names = [ "feed15.xml" ]

# Names of documents that match 'latah'
latah_documents = [ "latah_2657.htm",
                        "latah_2658.htm",
                        "latah_2659.htm" ]

# Transferable documents
unmappable_transferable_documents = latah_documents
mappable_transferable_documents = feed_names
transferable_documents = unmappable_transferable_documents + mappable_transferable_documents

def make_rss_url(folder_path, location=""):
    if int(rssServerPort) == 80:
        return "%s%s/%s" % (rssServerName,location,folder_path)
    else:
        return "%s:%s%s/%s" % (rssServerName,rssServerPort,location,folder_path)


# Crawl user credentials
username = "testingest"
password = "testingest"

def preclean( ad_domain_info, print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    try:
        ConnectorHelpers.reset_all()
    except Exception, e:
        if print_errors:
            print "Error resetting all jobs"
            print e

    # Clean up the documents we dumped into the folders on the server
    for document in transferable_documents:
        try:
            remove_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\"+document )
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % document
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

# Main
if __name__ == '__main__':

    # AD parameters
    ad_group = "76"

    if len(sys.argv) > 1:
        ad_group = sys.argv[1]

    ad_domain_info = sqa_domain_info.SQADomainInfo( ad_group )
    rssDomain = ad_domain_info.dns_domain.upper()
    rssServerName = getattr(ad_domain_info,"rss_server_fqdn");

    print "Precleaning!"

    preclean( ad_domain_info, print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["rssConnector"], detect_gdms=True)

    # Set up the ingestion user.

    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )

    # Set up a continuous crawl so that we can see how it behaves

    print "Continuous crawl test."

    # Add all docs to the repository
    map = { "%server%" : rssServerName }

    for document in mappable_transferable_documents:
        add_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\"+document, "/root/rssfeeds/"+document, map=map)

    for document in unmappable_transferable_documents:
        add_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\"+document, "/root/rssfeeds/"+document)

    feed_list = []
    for feed_name in feed_names:
        feed_list.append( "http://"+make_rss_url(feed_name) )


    # Define repository connection
    define_rss_repository_connection_ui( username,
                                        password,
                                        "RSSConnection",
                                        "RSS Connection",
                                        "kwright@metacarta.com" )

    # Define job.
    # The strategy for this test is to crawl for a while, and make sure all the documents are visible, then change the feed list,
    # and wait until the documents should expire.
    job_id = define_rss_job_ui( username,
                        password,
                        "RSSJob",
                        "RSSConnection",
                        feed_list,
                        default_feed_refetch_time_minutes=1,
                        min_feed_refetch_time_minutes=1,
                        type="continuous",
                        recrawlinterval="",
                        expireinterval=2 )

    # Start the job.
    print "Starting continuous job..."

    ConnectorHelpers.start_job( job_id )

    # Wait 5 minutes.  This is to allow several cycles of feed fetching and document rescanning.
    print "Waiting for 5 minutes..."
    time.sleep(300.0)

    # Make sure index has caught up
    print "Checking for ingestion..."
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    url_list = []
    for document in latah_documents:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "latah" ], None, url_list )

    print "Changing job seeds..."

    # Now, modify the job and remove the one feed we gave it
    change_rss_job_ui( username,
                password,
                job_id,
                URLs=[] )

    # Wait 5 more minutes
    print "Waiting 5 more minutes..."
    time.sleep(300.0)

    # Make sure documents disappeared
    ConnectorHelpers.wait_for_ingest( )
    ConnectorHelpers.search_check( [ "latah" ], None, [ ] )

    print "Cleaning up..."
    # There is no need to abort the job; it should have stopped on its own because there were no longer any documents it was covering.
    # Delete job
    ConnectorHelpers.delete_job_ui( username, password, job_id )
    ConnectorHelpers.wait_job_deleted( job_id )
    # Remove connection
    ConnectorHelpers.delete_repository_connection_ui( username, password, "RSSConnection" )

    # Clean up the documents we dumped into the folders on server
    for document in transferable_documents:
        remove_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\"+document )

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "Continuous RSSConnector tests PASSED"
