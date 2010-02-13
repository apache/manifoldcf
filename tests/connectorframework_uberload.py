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
import random
import optparse

import ConnectorHelpers
from sqatools import LicenseMakerClient
from sqatools import appliance
from threading import Thread

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

# Copy a folder to a (new) area
def copy_folder( source, target ):
    os.makedirs(target)
    appliance.spcall( [ "cp", "-r", source, target ] )


# Remove a folder
def delete_folder( target ):
    appliance.spcall( [ "rm", "-rf", target ] )

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

    # Remove test documents first
    for folder in [ "/common/crawlarea" ]:
        try:
            delete_folder( folder )
        except Exception, e:
            if print_errors:
                print "Error removing %s" % folder
                print e

    try:
        LicenseMakerClient.revoke_license()
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

def run_postjob_search(search_fn=ConnectorHelpers.search_exists_check):
    level0 = 0
    while level0 < 10:
        level1 = 0
        while level1 < 10:
            level2 = 0
            while level2 < 10:
                level3 = 0;
                while level3 < 10:
                    search_fn( [ "divestment url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/000.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/000\\.htm" )
                    search_fn( [ "wildlife url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/001.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/001\\.htm" )
                    search_fn( [ "visitors url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/002.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/002\\.htm" )
                    search_fn( [ "fishery url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/003.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/003\\.htm" )
                    search_fn( [ "concession url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/004.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/004\\.htm" )
                    search_fn( [ "helicopter url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/005.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/005\\.htm" )
                    search_fn( [ "moratorium url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/006.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/006\\.htm" )
                    search_fn( [ "diversified url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/007.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/007\\.htm" )
                    search_fn( [ "renegotiated url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/008.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/008\\.htm" )
                    search_fn( [ "wilderness url:file:/common/crawlarea/%d/%d/%d/%d/largefiles/009.htm" % (level0,level1,level2,level3) ], None, "common/crawlarea/[0-9]/[0-9]/[0-9]/[0-9]/largefiles/009\\.htm" )
                    level3 += 1
                level2 += 1
            level1 += 1
        level0 += 1

def run_job( job_id, do_search=True ):
    start_time = time.time()
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    end_time = time.time()

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    if do_search:
        run_postjob_search()

    return end_time - start_time

def touch_random_files( file_count ):
    file_number = 0
    while file_number < file_count:
        level1 = int(random.random() * 10.0)
        level2 = int(random.random() * 10.0)
        level3 = int(random.random() * 10.0)
        level4 = int(random.random() * 10.0)
        level5 = int(random.random() * 10.0)

        ConnectorHelpers.invoke_root_script( [ "touch", "/common/crawlarea/%d/%d/%d/%d/largefiles/00%d.htm" % (level1, level2, level3, level4, level5) ] )
        file_number += 1

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

def do_maintenance():
    print "Performing database maintenance."
    response_string = ""
    mt = run_maintenance_thread(response_string)
    mt.start()

    # Perform searches every .25 second until maintenance operation is complete
    search_count = 0
    fail_count = 0
    good_count = 0
    while True:
        # If maintenance complete, break
        if mt.isAlive() == False:
            break;
        try:
            responses = ConnectorHelpers.invoke_curl("http://localhost:8180/authorityservice/UserACLs?username=foo")
            good_count += 1
        except Exception,e:
            fail_count += 1
        time.sleep( 0.25 )
    mt.join()
    if fail_count > 0:
        raise Exception("A response indicated a time when tomcat was not available")
    if good_count <= 1:
        raise Exception("Test invalid; unable to perform sufficient searches while postgresql maintenance ongoing")
    if len(response_string) > 0:
        raise Exception("Maintenance script had an error: %s" % response_string)
    print "Maintenance complete."

def main():
    parser = optparse.OptionParser()

    # This number of crawls should take approximately 4 days
    # (It was 100, turns out that 30 seems to demonstrate the issue on a 2950).
    parser.add_option("-b", "--before-crawls", action="store", type="int", dest="before_maintenance_count", default=30)
    parser.add_option("-a", "--after-crawls", action="store", type="int", dest="after_maintenance_count", default=5)
    parser.add_option("-n", "--no-search", action="store_true", dest="no_search", default=False)

    (options, argv) = parser.parse_args(sys.argv)

    print "Precleaning!"

    preclean( print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()
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


    print "Setting up connection and job."

    # Define repository connection
    ConnectorHelpers.define_repositoryconnection( "FileSystem",
                                 "FileSystem Connection",
                                 "com.metacarta.crawler.connectors.filesystem.FileConnector" )

    # Define job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="/common/crawlarea"><include match="*.htm" type="file"/><include match="*" type="directory"/></startpoint></specification>'
    job_id = ConnectorHelpers.define_job( "Test job",
                            "FileSystem",
                            doc_spec_xml )

    # This test performs repeated cycles of crawling over an extended period of time.
    # Each cycle is expected to take longer and longer, until the maintenance phase
    # comes along.  At that point, performance is expected to recover to within a few
    # percent of the initial performance of the system.

    # To make the test a valid one, we must therefore perform a maintenance
    # cycle on the currently clean database.

    print "Performing initial maintenance run."

    ConnectorHelpers.run_maintenance()

    # Each run consists of 100,000 documents, some of which are marked changed,
    # and others of which are NOT changed.

    # The first run is "special" because all the documents will need to be ingested.
    # Timing this one doesn't make much sense.  We'll time all the subsequent runs
    # instead.

    # Run the job to completion the first time.
    print "Performing initial crawl."
    timing = run_job( job_id, do_search=not options.no_search )
    print "Initial crawl took %f seconds." % timing

    random_touch_count = 10000

    # Lowest and highest times...
    lowest_time = 1000000.0
    highest_time = 0
    average_time_accumulator = 0.0

    current_crawl_number = 0
    while current_crawl_number < options.before_maintenance_count:
        # touch 1000 random files in the set
        touch_random_files( random_touch_count )
        # Run the crawl again
        print "Performing crawl number %d..." % current_crawl_number
        time_value = run_job( job_id, do_search=not options.no_search )
        print "Crawl number %d done in %f seconds." % (current_crawl_number,time_value)
        current_crawl_number += 1
        average_time_accumulator += time_value
        if time_value > highest_time:
            highest_time = time_value
        if time_value < lowest_time:
            lowest_time = time_value

    average_time = average_time_accumulator / options.before_maintenance_count
    
    print "After %d crawls, the best crawl time was %f, the worst was %f, and the average was %f." % (current_crawl_number,lowest_time,highest_time,average_time)

    do_maintenance()

    after_lowest_time = 1000000.0
    after_highest_time = 0.0
    after_average_time_accumulator = 0.0

    current_crawl_number = 0
    while current_crawl_number < options.after_maintenance_count:
        # touch 1000 random files in the set
        touch_random_files( random_touch_count )
        # Run the crawl again
        print "Performing after-maintenance crawl number %d..." % current_crawl_number
        time_value = run_job( job_id, do_search=not options.no_search )
        print "After-maintenance crawl number %d done in %f seconds." % (current_crawl_number,time_value)
        current_crawl_number += 1
        after_average_time_accumulator += time_value
        if time_value > after_highest_time:
            after_highest_time = time_value
        if time_value < after_lowest_time:
            after_lowest_time = time_value

    after_average_time = after_average_time_accumulator / options.after_maintenance_count
    
    print "After %d post-maintenance crawls, lowest is %f, highest is %f, and average is %f." % (current_crawl_number,after_lowest_time,after_highest_time,after_average_time)
    
    # Postgresql 8.3.7 does not seem to degrade noticeably in performance from run to run, even without maintenance.
    # We therefore just make sure the post-maintenance average is about the same as the pre-maintenance average.
    if after_lowest_time >= highest_time:
        raise Exception("Run after maintenance yields timing that is worse than before maintenance!")
    if after_average_time > average_time * 1.10:
        raise Exception("Average post-maintenance time is more that 10% worse than pre-maintenance value!")


    # PHASE 2: Cleanup test

    print "Cleanup Load Test."

    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"
    # Make sure the documents all went away

    if not options.no_search:
        run_postjob_search(search_fn=ConnectorHelpers.search_nonexists_check)

    print "Done Cleanup Load Test."

    ConnectorHelpers.delete_repositoryconnection( "FileSystem" )

    ConnectorHelpers.delete_gts_outputconnection( )
    
    delete_folder("/common/crawlarea")
    LicenseMakerClient.revoke_license()

    ConnectorHelpers.teardown_connector_environment( )

    print "UberLoad ConnectorFramework tests PASSED"

# Main
if __name__ == '__main__':
    main()
