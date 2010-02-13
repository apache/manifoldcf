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
import VirtualBrowser
import ConnectorHelpers
from sqatools import LicenseMakerClient
from sqatools import appliance
from threading import Thread

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

# Copy a folder to a (new) area
def copy_folder( source, target ):
    appliance.spcall( [ "mkdir", "-p", target ] )
    appliance.spcall( [ "cp", "-r", source, target ] )


# Remove a folder
def delete_folder( target ):
    appliance.spcall( [ "rm", "-rf", target ] )

# Look for maintenance message displayed in UI
def check_for_maintenance_message_ui( username, password ):
    """ See if maintenance message shows up
    """

    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("Manage jobs")
    link.click( )

    # Grab the new window
    window = vb.find_window("")
    # Use the built-in function to look for a match
    try:
        window.find_match( "unavailable due to maintenance operations" )
        return True
    except:
        return False


# Crawl user credentials
username = "testingest"
password = "testingest"

def preclean( print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    try:
        ConnectorHelpers.start_leafblower()
    except Exception, e:
        if print_errors:
            print "Error restarting ingestion"
            print e

    try:
        ConnectorHelpers.reset_all()
    except Exception, e:
        if print_errors:
            print "Error resetting all jobs"
            print e

    # Remove test documents first
    for folder in [ "/common/crawlarea" ]:
        try:
            delete_folder( folder )
        except Exception, e:
            if print_errors:
                print "Error removing %s" % folder
                print e

    try:
        LicenseMakerClient.revoke_license
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

class run_maintenance_thread(Thread):
    def __init__ (self, response):
        Thread.__init__(self)
        self.response = response
        self.setDaemon(True)

    def run(self):
        try:
            ConnectorHelpers.run_maintenance()
        except Exception, e:
            self.response.append( str(e) )

# Main
if __name__ == '__main__':

    print "Precleaning!"

    preclean( print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    print "Setting up file area."
    # We need at least 10,000 documents.  We'll get this by
    # having 10 documents under 4 levels of a hierarchy
    level0 = 0
    while level0 < 10:
        level1 = 0
        while level1 < 10:
            level2 = 0
            while level2 < 10:
                level3 = 0
                while level3 < 10:
                    pathname = "/common/crawlarea/%d/%d/%d/%d" % (level0,level1,level2,level3)
                    copy_folder("/root/largefiles",pathname)
                    level3 += 1
                level2 += 1
            level1 += 1
        level0 += 1


    # PHASE 1: Ingestion

    print "Ingestion Load Test."

    ConnectorHelpers.clear_logs()
    
    # Define repository connection
    ConnectorHelpers.define_repositoryconnection( "FileSystem",
                                 "FileSystem Connection",
                                 "com.metacarta.crawler.connectors.filesystem.FileConnector" )

    # Define job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="/common/crawlarea"><include match="*.htm" type="file"/><include match="*" type="directory"/></startpoint></specification>'
    job_id = ConnectorHelpers.define_job( "Test job",
                            "FileSystem",
                            doc_spec_xml )

    # Run the job
    ConnectorHelpers.start_job( job_id )
    # Framework abort test!  Abort the job and see how long it takes for it to actually stop doing stuff
    # First, wait 2 minutes for the job to get really rolling
    time.sleep(120)
    # Now, abort it
    ConnectorHelpers.abort_job( job_id )
    # Wait to see how long it actually takes to abort the job
    the_time = time.time()
    ConnectorHelpers.wait_job_complete( job_id )
    elapsed_time = time.time() - the_time;
    print "It took %f seconds to abort the job" % elapsed_time
    if elapsed_time > 120.0:
        raise Exception( "Took too long for job to abort: %f seconds" % elapsed_time )

    # This time, run the job to completion
    ConnectorHelpers.start_job( job_id )
    # Pause test!  Pause the job and see how long it takes for it to actually stop doing stuff
    # First, wait 3 minutes for the job to get really rolling.  We can't pause the job until it is finished starting up.
    time.sleep(180)
    # Now, pause it
    ConnectorHelpers.pause_job( job_id )
    # Wait to see how long it actually takes to pause the job
    the_time = time.time()
    ConnectorHelpers.wait_job_paused( job_id )
    elapsed_time = time.time() - the_time;
    print "It took %f seconds to pause the job" % elapsed_time
    if elapsed_time > 120.0:
        raise Exception( "Took too long for job to pause: %f seconds" % elapsed_time )
    # Resume job
    ConnectorHelpers.resume_job( job_id )

    # Next, shut down ingestion for a time, to make sure the job recovers and properly completes afterwards
    ConnectorHelpers.stop_leafblower()
    # Two minutes is enough time to cause serious havoc (if it's going to occur...)
    time.sleep(120)
    # Start it back up again
    ConnectorHelpers.start_leafblower()

    # When approximately the largest number of unprocessed documents are on the queue, we stop and start
    # metacarta-agents twice in quick succession.  We're looking for a spurious FATAL error in the log.
    time.sleep(120)
    ConnectorHelpers.restart_agents()
    ConnectorHelpers.restart_agents()
    # Check the log for a FATAL error
    results = ConnectorHelpers.read_log( "FATAL:" )
    if len(results) > 0:
        raise Exception("Saw FATAL error in log after quick-succession restarts")

    # Now, let job complete
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( timeout=5000 )

    # See if we can find all of the documents we just ingested
    level0 = 0
    while level0 < 10:
        level1 = 0
        while level1 < 10:
            level2 = 0
            while level2 < 10:
                level3 = 0;
                while level3 < 10:
                    ConnectorHelpers.search_exists_check( [ "divestment url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/000.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/000\\.htm" )
                    ConnectorHelpers.search_exists_check( [ "wildlife url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/001.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/001\\.htm" )
                    ConnectorHelpers.search_exists_check( [ "visitors url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/002.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/002\\.htm" )
                    ConnectorHelpers.search_exists_check( [ "fishery url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/003.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/003\\.htm" )
                    ConnectorHelpers.search_exists_check( [ "concession url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/004.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/004\\.htm" )
                    ConnectorHelpers.search_exists_check( [ "helicopter url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/005.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/005\\.htm" )
                    ConnectorHelpers.search_exists_check( [ "moratorium url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/006.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/006\\.htm" )
                    ConnectorHelpers.search_exists_check( [ "diversified url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/007.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/007\\.htm" )
                    ConnectorHelpers.search_exists_check( [ "renegotiated url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/008.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/008\\.htm" )
                    ConnectorHelpers.search_exists_check( [ "wilderness url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/009.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/009\\.htm" )
                    level3 += 1
                level2 += 1
            level1 += 1
        level0 += 1

    # Success: done
    print "Done ingestion load test."

    print "Performing maintenance/search test"

    # Fire up maintenance operation in a separate thread
    response_string = ""
    mt = run_maintenance_thread(response_string)
    mt.start()

    # Perform searches every .25 second until maintenance operation is complete
    search_count = 0
    fail_count = 0
    good_count = 0
    saw_maintenance_message = False
    while True:
        # If maintenance complete, break
        if mt.isAlive() == False:
            break;
        try:
            responses = ConnectorHelpers.invoke_curl("http://localhost:8180/authorityservice/UserACLs?username=foo")
            good_count += 1
        except Exception,e:
            fail_count += 1
        # Now, check the UI also to be sure we show maintenance message at some point...
        if not saw_maintenance_message:
            saw_maintenance_message = check_for_maintenance_message_ui( username, password )
        time.sleep( 0.25 )
    mt.join()
    if fail_count > 0:
        raise Exception("A response indicated a time when tomcat was not available")
    if good_count <= 1:
        raise Exception("Test invalid; unable to perform sufficient searches while postgresql maintenance ongoing")
    if not saw_maintenance_message:
        raise Exception("Did not see maintenance message any time during maintenance!")
    if len(response_string) > 0:
        raise Exception("Maintenance script had an error: %s" % response_string)
    print "Done maintenance/search test"

    # PHASE 1.5: Look for what happens on a radically changed crawl, to be sure we properly handle the cleanup of old docs
    # Note well - this will extend the time of the test somewhat, because only one thread does document cleanup at termination time
    new_doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="/common/crawlarea"><include match="*.skip" type="file"/><include match="*" type="directory"/></startpoint></specification>'
    ConnectorHelpers.change_job_doc_spec( job_id, new_doc_spec_xml )
    # Run the job again; the job should scan directories, but find no files and almost immediately enter the "terminating" state, while it cleans up the stuff ingested before
    ConnectorHelpers.start_job( job_id )
    while True:
        results = ConnectorHelpers.list_job_statuses_api( )
        if len(results) != 1:
            raise Exception("Unexpected number of jobs have a status!  Expected: 1, found: %d" % len(results))
        job_result = results[0]
        job_status = job_result["status"]
        if job_status == "terminating":
            break
        if job_status != "starting up" and job_status != "running":
            raise Exception("Unexpected job status: %s" % job_status)
        time.sleep(1)

    # Wait for cleanup to finish
    ConnectorHelpers.wait_job_complete( job_id )

    # PHASE 2: Cleanup test

    print "Cleanup Load Test."

    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    
    # If broken, the following sequence of activity will cause the job to be stuck in "cleaning up" forever.
    time.sleep(60)
    ConnectorHelpers.stop_leafblower()
    time.sleep(120)
    ConnectorHelpers.start_leafblower()
    
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"
    # Make sure the documents all went away
    level0 = 0
    while level0 < 10:
        level1 = 0
        while level1 < 10:
            level2 = 0
            while level2 < 10:
                level3 = 0;
                while level3 < 10:
                    ConnectorHelpers.search_nonexists_check( [ "divestment url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/000.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/000\\.htm" )
                    ConnectorHelpers.search_nonexists_check( [ "wildlife url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/001.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/001\\.htm" )
                    ConnectorHelpers.search_nonexists_check( [ "visitors url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/002.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/002\\.htm" )
                    ConnectorHelpers.search_nonexists_check( [ "fishery url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/003.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/003\\.htm" )
                    ConnectorHelpers.search_nonexists_check( [ "concession url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/004.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/004\\.htm" )
                    ConnectorHelpers.search_nonexists_check( [ "helicopter url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/005.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/005\\.htm" )
                    ConnectorHelpers.search_nonexists_check( [ "moratorium url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/006.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/006\\.htm" )
                    ConnectorHelpers.search_nonexists_check( [ "diversified url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/007.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/007\\.htm" )
                    ConnectorHelpers.search_nonexists_check( [ "renegotiated url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/008.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/008\\.htm" )
                    ConnectorHelpers.search_nonexists_check( [ "wilderness url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/009.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/009\\.htm" )
                    level3 += 1
                level2 += 1
            level1 += 1
        level0 += 1

    print "Done Cleanup Load Test."

    ConnectorHelpers.delete_repositoryconnection( "FileSystem" )

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    delete_folder("/common/crawlarea")
    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "Load ConnectorFramework tests PASSED"
