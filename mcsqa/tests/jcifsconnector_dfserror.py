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
import socket
import ConnectorHelpers
import JcifsConnectorHelpers
import sqatools
from sqatools import LicenseMakerClient
from sqatools import sqautils
import VirtualBrowser

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

# Values to use for this test.  Values that are "None" will be filled in during test initialization.

jcifs_server_host = "w2k3-shp-76-1.qa-ad-76.metacarta.com"
# Domain
jcifsDomain = "qa-ad-76.metacarta.com"
# User
jcifsUser = "Administrator"
# Password
jcifsPassword = "password"
# Root
jcifsRoot = "SharedRoot"
# A known folder in the root
jcifsFolder = "TestFolder"

# Login credentials
username = "testingest"
password = "testingest"

def disable_server_access( server_host ):
    """ Use iptables to shut off the ability of appliance to talk to port 445 on server """
    ConnectorHelpers.invoke_root_script( ["iptables", "-A", "INPUT", "-s", socket.gethostbyname(server_host), "-p", "tcp", "--sport", "445", "-j", "DROP" ] )

def enable_server_access( ):
    ConnectorHelpers.invoke_root_script( ["iptables", "-D", "INPUT", "1" ] )


def preclean( print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    try:
        enable_server_access()
    except Exception, e:
        if print_errors:
            print "Error enabling server access"
            print e

    try:
        ConnectorHelpers.reset_all()
    except Exception, e:
        if print_errors:
            print "Error resetting all jobs"
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

    # Do the non-AD-specific setup and teardown ONLY ONCE!

    print "Precleaning!"

    preclean( print_errors=False )

    
    print "Setting up environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["shareConnector"], detect_gdms=True)

    print "Setting up ingestion user."
    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    print "Running test."

    # Set up repository connection.  We'll use the same connection for the entire test
    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection",
                                        "JCifs Connection",
                                        jcifsDomain,
                                        jcifsDomain,
                                        jcifsUser,
                                        jcifsPassword )

    print "Testing access to domain-based folder with communication enabled."

    min_check_time = 100000;
    max_check_time = 0;
    for iteration in range(10):
        print "Non-blocked access attempt %d" % iteration
        # Must reset tomcat each time
        ConnectorHelpers.restart_tomcat()
        sqautils.wait_for_service("tomcat")
        time.sleep(2)
        # Now, time how long it takes to create a job that requires dfs lookup
        start_time = time.time()
        job_id = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                        password,
                        "JCifs test job",
                        "JCifsConnection",
                        [ ( [ jcifsRoot, jcifsFolder ], [ ] ) ] )
        elapsed_time = time.time() - start_time
        if elapsed_time < min_check_time:
            min_check_time = elapsed_time
        if elapsed_time > max_check_time:
            max_check_time = elapsed_time

        # Delete the job
        ConnectorHelpers.delete_job( job_id )
        ConnectorHelpers.wait_job_deleted( job_id )

    print "For unbroken access, the min time was %f seconds, and the max time was %f seconds" % (min_check_time,max_check_time)

    print "Testing access to domain-based folder with communication disabled."

    # Now, disable communication to the primary, and rely on the replicated server
    disable_server_access(jcifs_server_host)

    # Repeat the run.  We want to see about 50% of the tests take longer than the max of the last run.  There should be no failures.
    bigger_count = 0
    run_size = 20
    for iteration in range(run_size):
        print "Blocked access attempt %d" % iteration
        # Must reset tomcat each time
        ConnectorHelpers.restart_tomcat()
        sqautils.wait_for_service("tomcat")
        time.sleep(2)
        # Now, time how long it takes to create a job that requires dfs lookup
        start_time = time.time()
        job_id = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                        password,
                        "JCifs test job",
                        "JCifsConnection",
                        [ ( [ jcifsRoot, jcifsFolder ], [ ] ) ] )
        elapsed_time = time.time() - start_time
        if elapsed_time > max_check_time:
            bigger_count += 1

        # Delete the job
        ConnectorHelpers.delete_job( job_id )
        ConnectorHelpers.wait_job_deleted( job_id )

    print "Saw %d accesses that exceeded the maximum time from the non-blocked run (%d iterations)." % (bigger_count,run_size)

    if bigger_count*100.0/run_size < 20:
        raise Exception("Expected at least 20%% of accesses to go via error path; saw only %d%%" % bigger_count)
    if bigger_count*100.0/run_size > 80:
        raise Exception("Expected at most 80%% of accesses to go via error path; saw %d%%" % bigger_count)

    print "Done with test; cleaning up"

    enable_server_access()

    # Get rid of test crawler user
    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "DFS Error JCifsConnector test PASSED"
