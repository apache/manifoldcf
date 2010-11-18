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
import JcifsConnectorHelpers
import sqatools
from wintools import sqa_domain_info
from wintools import filetools
from wintools import ambassador_client
from sqatools import LicenseMakerClient
import TestDocs
import VirtualBrowser

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

# Samba server info
samba_share_server = "cadillac.metacarta.com"
samba_share_domain = "CADILLAC"
samba_share_username = "qashare"
samba_share_password = "password"
samba_share_name = "qashare"
samba_share_folder = "bigfile"

# Login credentials
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
        ConnectorHelpers.delete_crawler_user( username )
    except Exception, e:
        if print_errors:
            print "Error deleting crawl user"
            print e

    try:
        ConnectorHelpers.set_shareconnector_default_mode()
    except Exception, e:
        if print_errors:
            print "Error setting NTLM mode to default"
            print e

    try:
        ConnectorHelpers.restore_default_proxy_timeout()
    except Exception, e:
        if print_errors:
            print "Error setting proxy timeout to default"
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

    print "Precleaning!"

    preclean( print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    # Enable CF security
    ConnectorHelpers.enable_connector_framework( )

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["shareConnector"], detect_gdms=True)


    # Set up the ingestion user.

    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # Phase 0: Check that share security seems to work
    print "Ingesting large document using JCifs."

    ConnectorHelpers.set_proxy_timeout(3600)

    ConnectorHelpers.set_shareconnector_ntlmv1_mode()

    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection",
                                        "JCifs Connection",
                                        samba_share_server,
                                        samba_share_domain,
                                        samba_share_username,
                                        samba_share_password )

    # Record the start time
    the_start_time = time.time()

    # Set up job
    job_id = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                        password,
                        "JCifs Big File Job",
                        "JCifsConnection",
                        [ ( [ samba_share_name, samba_share_folder ], [ ] ) ] )

    # Run job
    ConnectorHelpers.start_job(job_id)
    # Wait for job to complete
    ConnectorHelpers.wait_job_complete(job_id)

    # Check to be sure 1 document got ingested
    results = ConnectorHelpers.run_simple_history_report_api( "JCifsConnection",
        ["document ingest (GTS)"],
        start_time=int(the_start_time * 1000) )

    if len(results) != 1:
        raise Exception("Expected to ingest one large document, instead saw %d ingestions (%s)" % (len(results),str(results)))

    if results[0]["result_code"] != str(200):
        raise Exception("Expected ingestion to return code 200, instead did not succeed and returned code %s" % str(results[0]["result_code"]))

    # Success: done
    print "Done big file ingestion test."

    # PHASE 2: Cleanup test

    print "Cleanup Big File Test."

    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    print "Done Cleanup Big File Test."

    ConnectorHelpers.delete_repository_connection_ui( username, password, "JCifsConnection" )

    # Restore default mode
    ConnectorHelpers.set_shareconnector_default_mode()

    # Restore default timeout
    ConnectorHelpers.restore_default_proxy_timeout()

    # Get rid of test crawler user
    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "BigFile JCifsConnector test PASSED"
