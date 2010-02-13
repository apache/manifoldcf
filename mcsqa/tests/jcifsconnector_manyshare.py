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

# Method to create a pile of open, empty shares underneath the target path
def create_multiple_open_shares(jcifs_servername, jcifs_user, jcifs_password, targetpath="", count=2000):
    amb = ambassador_client.AmbassadorClient(jcifs_servername+":8000", jcifs_user, jcifs_password)
    # This is the set of shares that will be created and torn down as part of the
    # "large share list" test
    big_share_dict = {}
    for index in range(0,count):
        share_name = "myshare%d" % index
        big_share_dict[share_name] = { "files" : {} }
    filetools.create_shares(big_share_dict, amb)

# Method to remove a pile of open shares underneath a target path
def delete_multiple_open_shares(jcifs_servername, jcifs_user, jcifs_password, targetpath="", count=2000):
    amb = ambassador_client.AmbassadorClient(jcifs_servername+":8000", jcifs_user, jcifs_password)
    # This is the set of shares that will be created and torn down as part of the
    # "large share list" test
    big_share_dict = {}
    for index in range(0,count):
        share_name = "myshare%d" % index
        big_share_dict[share_name] = { "files" : {} }
    filetools.delete_shares(big_share_dict, amb)

# Invoke the API method for getting the server name from the connection
def get_jcifs_server_name_api( connection_name ):
    """ Invoke the API method for getting the server name from the connection
    """
    results = ConnectorHelpers.invoke_script(["/usr/lib/metacarta/jcifs-getconnectioninfo",connection_name])
    return ConnectorHelpers.process_api_result(results,["server"])

# Values to use for this test.  Values that are "None" will be filled in during test initialization.

# Server name to talk to
jcifsServerName = None
# Domain
jcifsDomain = None
# User
jcifsUser = None
# Base user
jcifsSimpleUser = "Administrator"
# Password
jcifsPassword = "password"
# Share name
jcifsShare = "qashare"
# DFS share name
jcifsDfsShare = None
# DFS link
jcifsDfsLink = "DFSLink"
# DFS share name, one level deep variant
jcifsDfsOneDeepShare = "DFSStandaloneRoot"
# DFS link, one level deep variant
jcifsDfsOneDeepLink = "StandaloneLink"
# DFS target (which is where we actually write stuff that is picked up via DFS)
jcifsDfsTarget = "DFSTarget"
# DFS target where the documents are one folder level down
jcifsDfsOneDeepTarget = "DFSStandaloneTarget/DFSStandaloneFolder"
# Latin-1 share name
latin1share = u"\u00d8yvind"

# Login credentials
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

    # Delete the massive numbers of shares we created to test large packet handling
    try:
        delete_multiple_open_shares(jcifsServerName, jcifsUser, jcifsPassword)
    except Exception, e:
        if print_errors:
            print "Error removing numbered shares"
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
    share_machine_handle = "jcifs_server"
    if len(sys.argv) > 1:
        ad_group = sys.argv[1]
    if len(sys.argv) > 2:
        share_machine_handle = sys.argv[2]

    ad_domain_info = sqa_domain_info.SQADomainInfo( ad_group )
    jcifsDomain = ad_domain_info.dns_domain.upper()

    # The fully qualified server name is the share machine plus the domain
    jcifsServerName = getattr( ad_domain_info, share_machine_handle + "_fqdn" )
    # The jcifs user is the simple user plus the domain
    jcifsUser = "%s@%s" % (jcifsSimpleUser,jcifsDomain)
    # The domain-based DFS share name is the same as the machine name
    jcifsDfsShare = jcifsServerName.split(".")[0]


    # Do the non-AD-specific setup and teardown ONLY ONCE!

    print "Precleaning!"

    preclean( ad_domain_info, print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    # Enable CF security
    ConnectorHelpers.enable_connector_framework( )

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["shareConnector"], detect_gdms=True)

    # Set up the ingestion user.
    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # Create lots of shares.  This will force the UI to use jcifs packet forms that need more than 65536 bytes.
    create_multiple_open_shares(jcifsServerName, jcifsUser, jcifsPassword)

    # This test basically defines a number of jobs while there are lots of shares, hoping to cause the conditions where packet overruns
    # used to take place in jcifs

    # Define basic server-based repository connection
    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection",
                                        "JCifs Connection",
                                        jcifsServerName,
                                        None,
                                        jcifsUser,
                                        jcifsPassword )

    # Define basic domain-based repository connection
    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection2",
                                        "JCifs Connection 2",
                                        jcifsDomain,
                                        jcifsDomain,
                                        jcifsSimpleUser,
                                        jcifsPassword )

    # Define server-based job (testing standard share and standalone DFS)
    job_id = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                        password,
                        "JCifs test job",
                        "JCifsConnection",
                        [ ( [ jcifsShare, "TestDocs" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ),
                          ( [ jcifsDfsShare, jcifsDfsLink, "TestDocs" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ),
                          ( [ jcifsDfsOneDeepShare, jcifsDfsOneDeepLink, "TestDocs" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ),
                          ( [ latin1share ], [ ("include", "file", "*"), ("include", "directory", "*") ] ) ],
                        path_value_attribute="doc_path" )

    # Define domain-based job
    job_id_2 = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                        password,
                        "JCifs test job 2",
                        "JCifsConnection2",
                        [ ( [ jcifsDfsShare, jcifsDfsLink, "TestDocs" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ) ],
                        path_value_attribute="doc_path" )

    # Clean up domain-based pieces
    ConnectorHelpers.delete_job( job_id_2 )
    ConnectorHelpers.wait_job_deleted( job_id_2 )
    ConnectorHelpers.delete_repository_connection_ui( username, password, "JCifsConnection2" )

    # Clean up non-domain-based pieces
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )
    ConnectorHelpers.delete_repository_connection_ui( username, password, "JCifsConnection" )

    # Get rid of the numerous testing shares we created
    delete_multiple_open_shares(jcifsServerName, jcifsUser, jcifsPassword)

    # Get rid of test crawler user
    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "Manyshare JCifsConnector test PASSED"
