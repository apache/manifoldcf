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

# $Id$

import sys
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

SHARE_SERVER = "u14e.metacarta.com"
SHARE_USER   = "qashare"
SHARE_PSWD   = "password"

cfui_username = "fred"
cfui_password = "fred"

REPO_CONNECTION = "unicode_jcifscrawl"

# Invoke the API method for getting the server name from the connection
def get_jcifs_server_name_api( connection_name ):
    """ Invoke the API method for getting the server name from the connection
    """
    results = ConnectorHelpers.invoke_script(["/usr/lib/metacarta/jcifs-getconnectioninfo",connection_name])
    return ConnectorHelpers.process_api_result(results,["server"])

def main():
    """TODO"""
    ConnectorHelpers.setup_connector_environment()
    try:
        ConnectorHelpers.create_crawler_user(cfui_username, cfui_password)

        print "Setting up license."
        sqatools.appliance.install_license(extra_services=["shareConnector"], detect_gdms=True)

        try:
            ConnectorHelpers.reset_all()
        except Exception, e:
            if print_errors:
                print "Error resetting all jobs"
                print e

        ConnectorHelpers.define_gts_outputconnection( )
        
        # Define basic server-based repository connection
        JcifsConnectorHelpers.define_jcifs_repository_connection_ui(cfui_username,
                                            cfui_password,
                                            REPO_CONNECTION,
                                            "Connection for Unicode jCIFS crawl",
                                            SHARE_SERVER,
                                            None,
                                            SHARE_USER,
                                            SHARE_PSWD)

        # Try out the API method
        results = get_jcifs_server_name_api(REPO_CONNECTION)
        if len(results) != 1:
            raise Exception("Expected one jcifs connection, found %d" % len(results))
        if results[0]["server"] != SHARE_SERVER:
            raise Exception("Expected server name to be '%s', instead was '%s'" % (SHARE_SERVER, results[0]["server"]))


        collection_list=["andrew", "arabic", "basic", "bath", "invalid"]
        
        for colname in collection_list:

            # Define server-based job
            job_id = JcifsConnectorHelpers.define_jcifs_job_ui(cfui_username,
                                cfui_password,
                                job_name="i18n URLs",
                                connection_name=REPO_CONNECTION,
                                crawlpaths=[
                                    (["qashare", "i18n", colname], [("include", "file", "*"), ("include", "directory", "*")]),
                                
                                ],
                                collection_name=colname,
                                security_enabled=False,
                                share_security_enabled=False,
                                path_value_attribute="doc_path")
    
            ConnectorHelpers.start_job(job_id)
    
            ConnectorHelpers.wait_job_complete(job_id)
    
            # Wait until ingest has caught up
            ConnectorHelpers.wait_for_ingest()
    
            #ConnectorHelpers.search_check([ "reference" ], None, [ make_jcifs_search_url(id1), make_jcifs_dfs_search_url(id1), make_jcifs_dfs_domain_search_url(id1), make_jcifs_dfs_onedeep_search_url(id1) ])
            
            if "clean" in sys.argv:
                ConnectorHelpers.delete_job(job_id)
                ConnectorHelpers.wait_job_deleted(job_id)
        
        
        if "clean" in sys.argv:
            # Clean up domain-based crawl pieces
            ConnectorHelpers.delete_gts_outputconnection( )

            ConnectorHelpers.delete_repository_connection_ui(cfui_username, cfui_password, REPO_CONNECTION)

            ConnectorHelpers.delete_crawler_user(cfui_username)

            LicenseMakerClient.revoke_license()

    finally:
        ConnectorHelpers.teardown_connector_environment()
    
if __name__ == "__main__":
    main()
