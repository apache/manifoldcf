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

# Main
if __name__ == '__main__':

    print "Precleaning!"
    preclean( print_errors=False )

    print "Setting up environment."
    ConnectorHelpers.setup_connector_environment()

    print "Adding crawl user."
    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    print "Setting up file area."
    # We need enough documents to allow us to estimate fetch rate accurately; I
    # think 3x1,000 documents will do nicely, since it also fits within the "max fetch rate report" max window size of 5,000.
    level0 = 0
    while level0 < 10:
        level1 = 0
        while level1 < 10:
            pathname = "/common/crawlarea/BOTH/%d/%d" % (level0,level1)
            copy_folder("/root/largefiles",pathname)
            level1 += 1
        level0 += 1
    level0 = 0
    while level0 < 10:
        level1 = 0
        while level1 < 10:
            pathname = "/common/crawlarea/SLOW/%d/%d" % (level0,level1)
            copy_folder("/root/largefiles",pathname)
            level1 += 1
        level0 += 1
    level0 = 0
    while level0 < 10:
        level1 = 0
        while level1 < 10:
            pathname = "/common/crawlarea/FAST/%d/%d" % (level0,level1)
            copy_folder("/root/largefiles",pathname)
            level1 += 1
        level0 += 1


    # Set up the connection with two different throttles for bins "FAST" and "SLOW"

    print "Dissimilar throttle test."

    # Define repository connection.  The throttle rates are in documents per minute.  We have to stay clear of the probable upper fetch
    # rate of about 100 docs/sec, or 6000 docs per minute, but we can certainly aspire to make the test run as quickly as possible otherwise.
    ConnectorHelpers.define_filesystem_repository_connection_ui( username, password,
        "FileSystem", "FileSystem Connection",
        max_connections=100,
        throttles=[("FAST","All documents in the FAST bin",1000), ("SLOW","All documents in the SLOW bin",100)] )

    # Define job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="/common/crawlarea"><include match="*.htm" type="file"/><include match="*" type="directory"/></startpoint></specification>'
    job_id = ConnectorHelpers.define_job( "Test job",
                            "FileSystem",
                            doc_spec_xml )

    # Run the job to completion
    start_time = time.time()

    ConnectorHelpers.start_job( job_id )
    # Now, let job complete
    ConnectorHelpers.wait_job_complete( job_id )

    # Now, check whether we exceeded any of the throttle rates for the different classes.
    # If we did, it may mean that throttling is not working correctly for
    # documents with multiple bins.  I'm leaving the window size at 5 minutes, even though the overall crawl is expected to take only
    # 10, because we do want to assess whether a burst might take place.
    rate_report = ConnectorHelpers.run_max_activity_history_report_ui( username, password, "FileSystem",
        [ "read document" ],
        entity_regexp="/SLOW/",
        entity_bin_regexp="()" )
    if len(rate_report) != 1:
        raise Exception("Expected max activity report to have exactly one row, instead saw %d" % len(rate_report))
    max_rate = float(rate_report[0]["Highest Activity Rate [per min]"])
    if max_rate > 110.0:
        raise Exception("Max activity rate for SLOW docs should not have exceeded one-sigma value of 110.0 docs per minute; instead saw %f" % max_rate)

    rate_report = ConnectorHelpers.run_max_activity_history_report_ui( username, password, "FileSystem",
        [ "read document" ],
        entity_regexp="/BOTH/",
        entity_bin_regexp="()" )
    if len(rate_report) != 1:
        raise Exception("Expected max activity report to have exactly one row, instead saw %d" % len(rate_report))
    max_rate = float(rate_report[0]["Highest Activity Rate [per min]"])
    if max_rate > 110.0:
        raise Exception("Max activity rate for BOTH docs should not have exceeded one-sigma value of 110.0 docs per minute; instead saw %f" % max_rate)

    rate_report = ConnectorHelpers.run_max_activity_history_report_ui( username, password, "FileSystem",
        [ "read document" ],
        entity_regexp="/FAST/",
        entity_bin_regexp="()" )
    if len(rate_report) != 1:
        raise Exception("Expected max activity report to have exactly one row, instead saw %d" % len(rate_report))
    max_rate = float(rate_report[0]["Highest Activity Rate [per min]"])
    if max_rate > 1100.0:
        raise Exception("Max activity rate for FAST docs should not have exceeded one-sigma value of 1100.0 docs per minute; instead saw %f" % max_rate)

    # Next, use the simple report generator to give us an idea of the overall fetch rate for each document class.
    # The BOTH and the SLOW rates should be similar.
    slow_history = ConnectorHelpers.run_simple_history_report_ui( username, password, "FileSystem",
        [ "read document" ],
        entity_regexp="/SLOW/" )
    fast_history = ConnectorHelpers.run_simple_history_report_ui( username, password, "FileSystem",
        [ "read document" ],
        entity_regexp="/FAST/" )
    both_history = ConnectorHelpers.run_simple_history_report_ui( username, password, "FileSystem",
        [ "read document" ],
        entity_regexp="/BOTH/" )
    if len(slow_history) == 0:
        raise Exception("There should be documents in the history with /SLOW/ in the file path!")
    if len(fast_history) == 0:
        raise Exception("There should be documents in the history with /FAST/ in the file path!")
    if len(both_history) == 0:
        raise Exception("There should be documents in the history with /BOTH/ in the file path!")
    # Use the last time (which is the first row of the result) for each of these to establish a crawl interval
    slow_end_time = ConnectorHelpers.parse_date_time(slow_history[0]["Start Time"])
    fast_end_time = ConnectorHelpers.parse_date_time(fast_history[0]["Start Time"])
    both_end_time = ConnectorHelpers.parse_date_time(both_history[0]["Start Time"])
    # Calculate a read rate (in docs per second).
    # Even though we put 1000 documents into each category, really there are 1211 because we have to count the directories too...
    slow_rate = 1211.0 / (slow_end_time - start_time)
    fast_rate = 1211.0 / (fast_end_time - start_time)
    both_rate = 1211.0 / (both_end_time - start_time)

    print "The approximate rates are: %f (SLOW), %f (FAST), %f (BOTH)" % ((slow_rate * 60.0),(fast_rate * 60.0),(both_rate * 60.0))

    # The document priorities should be calculated to avoid overweighting the "FAST" pool at the expense of the "SLOW" or "BOTH" pools.
    # It is therefore part of this test that the rates we've determined will hold approximately the correct ratios to one another, even if the
    # whole system "runs behind".  Therefore, FAST should be approximately 10x SLOW and 10x BOTH.
    if slow_rate * (10.0 * 0.65) > fast_rate or slow_rate * (10.0 * 1.35) < fast_rate:
        raise Exception("SLOW documents : FAST documents ratio was out of range: %f" % (fast_rate/slow_rate))
    if both_rate * (10.0 * 0.65) > fast_rate or both_rate * (10.0 * 1.35) < fast_rate:
        raise Exception("BOTH documents : FAST documents ratio was out of range: %f" % (fast_rate/both_rate))
    if slow_rate * 0.65 > both_rate or slow_rate * 1.35 < both_rate:
        raise Exception("SLOW documents : BOTH documents ratio was out of range: %f" % (both_rate/slow_rate))

    print "Cleanup Dissimilar Throttle Test."

    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    ConnectorHelpers.delete_filesystem_repository_connection_ui( username, password, "FileSystem" )

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )
    delete_folder("/common/crawlarea")
    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "Scheduling/Throttling ConnectorFramework tests PASSED"
