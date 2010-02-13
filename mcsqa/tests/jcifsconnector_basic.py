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

# Copy a folder to a (new) area
def copy_folder( source, target ):
    ConnectorHelpers.invoke_root_script( [ "mkdir", "-p", target ] )
    ConnectorHelpers.invoke_root_script( [ "cp", "-r", source, target ] )

# Remove a folder
def delete_folder( target ):
    ConnectorHelpers.invoke_root_script( [ "rm", "-rf", target ] )

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

def make_jcifs_url(target_path):
    return "file://%s/%s/%s" % (jcifsServerName, jcifsShare, target_path)

def make_jcifs_search_url(target_path):
    return "%s/%s/%s" % (jcifsServerName, jcifsShare, target_path)

def make_jcifs_dfs_url(target_path):
    return "file://%s/%s/%s" % (jcifsServerName, jcifsDfsShare+"/"+jcifsDfsLink, target_path)

def make_jcifs_dfs_search_url(target_path):
    return "%s/%s/%s" % (jcifsServerName, jcifsDfsShare+"/"+jcifsDfsLink, target_path)

def make_jcifs_dfs_domain_url(target_path):
    return "file://%s/%s/%s" % (jcifsDomain, jcifsDfsShare+"/"+jcifsDfsLink, target_path)

def make_jcifs_dfs_domain_search_url(target_path):
    return "%s/%s/%s" % (jcifsDomain, jcifsDfsShare+"/"+jcifsDfsLink, target_path)

def make_jcifs_dfs_onedeep_url(target_path):
    return "file://%s/%s/%s" % (jcifsServerName, jcifsDfsOneDeepShare+"/"+jcifsDfsOneDeepLink, target_path)

def make_jcifs_dfs_onedeep_search_url(target_path):
    return "%s/%s/%s" % (jcifsServerName, jcifsDfsOneDeepShare+"/"+jcifsDfsOneDeepLink, target_path)

# Document identifiers
id1 = "TestDocs/f001.txt"
id2 = "TestDocs/f002.txt"
id3 = "TestDocs/f003.txt"
id4 = "TestDocs/f004.txt"
id5 = "TestDocs/f005.txt"
id6 = "TestDocs/f006.txt"
id7 = "TestDocs/f007.txt"
id8 = "TestDocs/newfolder/f008.txt"
id9 =  "TestDocs/f009.txt"
id10 =  "TestDocs/f010.txt"

id3a =  "TestDocs/userafolder/f003.txt"
id4a =  "TestDocs/userafolder/f004.txt"
id5a =  "TestDocs/userbfolder/f005.txt"
id6a =  "TestDocs/userbfolder/f006.txt"
id7a =  "TestDocs/usercfolder/f007.txt"

id_fingerprint_1 = "TestDocs/test1.doc"
id_fingerprint_2 = "TestDocs/excel2003.xls"
id_fingerprint_3 = "TestDocs/powerpoint2003.ppt"
id_fingerprint_4 = "TestDocs/word2003.doc"
id_fingerprint_5 = "TestDocs/excel2007.xls"
id_fingerprint_6 = "TestDocs/powerpoint2007.ppt"
id_fingerprint_7 = "TestDocs/word2007.doc"

# For the two-connections test, the document names are generated, but the base names are as follows:
idbase_connection1 = "TestDocs1/base"
idbase_connection2 = "TestDocs2/base"
idrange =  range(1,100)

# Samba server info for "share security" test
samba_share_server = "ena.metacarta.com"
samba_share_domain = "ENA"
samba_share_username = "samba"
samba_share_password = "metacarta"

# Samba server info for samba-hosted DFS test
samba_dfs_server = "uonumakomagadake.metacarta.com"
samba_dfs_domain = "UONUMAKOMAGADAKE"
samba_dfs_username = "qashare"
samba_dfs_password = "password"

# Login credentials
username = "testingest"
password = "testingest"


def preclean( ad_domain_info, perform_legacy_pass, print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    # Because the duck might have been renumbered after a test was run, and
    # because this test is sensitive to domain DNS changes, we need to restart
    # tomcat and metacarta-agents.  This may be addressed in future in other
    # ways, but for now this is a reasonable way to get going again.
    ConnectorHelpers.restart_agents()
    ConnectorHelpers.restart_tomcat()

    # First order of business: synchronize legacy mode, so our state matches the duck state
    ConnectorHelpers.synchronize_legacy_mode( )

    try:
        ConnectorHelpers.reset_all()
    except Exception, e:
        if print_errors:
            print "Error resetting all jobs"
            print e

    # Remove test documents first
    for folder in [ "/root/crawlarea" ]:
        try:
            delete_folder( folder )
        except Exception, e:
            if print_errors:
                print "Error removing %s" % folder
                print e

    # Remove test documents first
    # Clean up the documents we dumped into the folders on CIFS server
    for docid in [id1,id2,id3,id4,id5,id6,id7,id8,id9,id10,id3a,id4a,id5a,id6a,id7a,
        id_fingerprint_1,id_fingerprint_2,id_fingerprint_3,id_fingerprint_4,id_fingerprint_5,id_fingerprint_6,id_fingerprint_7]:
        try:
            JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+docid )
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % docid
                print e

    # Clean up the documents we dumped into the dfs folders on CIFS server
    for docid in [id1,id2,id3,id4,id5,id6,id7,id8,id9,id10]:
        try:
            JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+docid )
        except Exception, e:
            if print_errors:
                print "Error deleting test dfs document %s" % docid
                print e
        try:
            JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+docid)
        except Exception, e:
            if print_errors:
                print "Error deleting test dfs document %s" % docid
                print e

    # Clean up more documents we might have dumped onto the CIFS server
    for docinstance in idrange:
        doc_name_1 = "%s_%d.txt" % (idbase_connection1, docinstance)
        doc_name_2 = "%s_%d.txt" % (idbase_connection2, docinstance)
        try:
            JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare + "/" + doc_name_1)
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % doc_name_1
                print e
        try:
            JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare + "/" + doc_name_2)
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % doc_name_2
                print e

    try:
        ConnectorHelpers.delete_crawler_user( username )
    except Exception, e:
        if print_errors:
            print "Error deleting crawl user"
            print e

    # Next, disassociate users from share folders
    for fileid in [ "userafolder", "userbfolder", "usercfolder", "newfolder" ]:
        try:
            JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/"+fileid, [ad_domain_info.realm_admin])
        except Exception, e:
            if print_errors:
                print "Error resetting folder permission"
                print e

    # Disable ad
    try:
        ConnectorHelpers.turn_off_ad( ad_domain_info )
    except Exception, e:
        if print_errors:
            print "Error disabling AD"
            print e

    try:
        ConnectorHelpers.set_shareconnector_default_mode()
    except Exception, e:
        if print_errors:
            print "Error setting NTLM mode to default"
            print e

    #TODO: Is this a duplicate of the above, or did username change?
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

    if perform_legacy_pass:
        try:
            ConnectorHelpers.select_legacy_mode(use_legacy_tools=False)
        except Exception, e:
            if print_errors:
                print "Error turning off legacy AD mode"
                print e

# Run the ad part of the test
def run_ad_test_part( ad_domain_info ):
    """ This part of the test may be run more than once, if legacy mode is to be tested too.  Therefore I've broken this part into
        a separate method.
    """

    ad_win_host = ad_domain_info.ie_client_fqdn

    # Set up system to use ad
    ConnectorHelpers.configure_ad( ad_domain_info )

    # PHASE 1: Ingestion

    # Add some docs to the repository
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id1, "/root/crawlarea/testfiles/f001.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id2, "/root/crawlarea/testfiles/f002.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id3, "/root/crawlarea/testfiles/f003.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id4, "/root/crawlarea/testfiles/f004.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id5, "/root/crawlarea/testfiles/f005.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id6, "/root/crawlarea/testfiles/f006.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id7, "/root/crawlarea/testfiles/f007.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id8,"/root/crawlarea/testfiles/newfolder/f008.txt")

    # Add the dfs docs to the repository
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+id1, "/root/crawlarea/testfiles/f001.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+id2, "/root/crawlarea/testfiles/f002.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+id3, "/root/crawlarea/testfiles/f003.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+id4, "/root/crawlarea/testfiles/f004.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+id5, "/root/crawlarea/testfiles/f005.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+id6, "/root/crawlarea/testfiles/f006.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+id7, "/root/crawlarea/testfiles/f007.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+id8,"/root/crawlarea/testfiles/newfolder/f008.txt")

    # Add the one-level-deep dfs docs to the repository
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+id1, "/root/crawlarea/testfiles/f001.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+id2, "/root/crawlarea/testfiles/f002.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+id3, "/root/crawlarea/testfiles/f003.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+id4, "/root/crawlarea/testfiles/f004.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+id5, "/root/crawlarea/testfiles/f005.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+id6, "/root/crawlarea/testfiles/f006.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+id7, "/root/crawlarea/testfiles/f007.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+id8,"/root/crawlarea/testfiles/newfolder/f008.txt")

    # In case there is clock skew, sleep a minute
    JcifsConnectorHelpers.wait_for_jcifs(jcifsServerName, jcifsUser, jcifsPassword)

    ConnectorHelpers.start_timing("ad_test_part")

    print "Ingestion Test."

    # Define basic server-based repository connection
    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection",
                                        "JCifs Connection",
                                        jcifsServerName,
                                        None,
                                        jcifsUser,
                                        jcifsPassword )

    print "Running API method for obtaining connection information"
    
    # Try out the API method
    results = get_jcifs_server_name_api( "JCifsConnection" )
    if len(results) != 1:
        raise Exception("Expected one jcifs connection, found %d" % len(results))
    if results[0]["server"] != jcifsServerName:
        raise Exception("Expected server name to be '%s', instead was '%s'" % (jcifsServerName,results[0]["server"]))

    print "Defining second repository connection"
    
    # Define basic domain-based repository connection
    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection2",
                                        "JCifs Connection 2",
                                        jcifsDomain,
                                        jcifsDomain,
                                        jcifsSimpleUser,
                                        jcifsPassword )

    print "Checking proper handling of illegal regular expressions by connector"

    job_id = JcifsConnectorHelpers.define_jcifs_job_ui( username, password,
                "JCifs test job",
                "JCifsConnection",
                [ ( [ jcifsShare, "TestDocs" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ) ],
                path_value_attribute="foo",
                mappings=[ ( "\\E$", "$(0)") ] )

    job_id_2 = JcifsConnectorHelpers.define_jcifs_job_ui( username, password,
                "JCifs test job",
                "JCifsConnection",
                [ ( [ jcifsShare, "TestDocs" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ) ],
                filename_mappings=[ ( "\\E$", "$(0)") ] )

    job_id_3 = JcifsConnectorHelpers.define_jcifs_job_ui( username, password,
                "JCifs test job",
                "JCifsConnection",
                [ ( [ jcifsShare, "TestDocs" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ) ],
                url_mappings=[ ( "\\E$", "$(0)") ] )

    # Run the jobs to completion.  They should all error out.
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.start_job( job_id_2 )
    ConnectorHelpers.start_job( job_id_3 )
    
    ConnectorHelpers.wait_job_complete( job_id )
    ConnectorHelpers.wait_job_complete( job_id_2 )
    ConnectorHelpers.wait_job_complete( job_id_3 )

    # Make sure they all aborted with an error.
    result_list = ConnectorHelpers.list_job_statuses_api( )
    if len(result_list) != 3:
        raise Exception("Expected three jobs, found %d" % len(result_list))
    for result in result_list:
        if result["status"] != "error":
            raise Exception("Expected job to be error, found status %s instead" % result["status"])

    # Clean up
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.delete_job( job_id_2 )
    ConnectorHelpers.delete_job( job_id_3 )

    ConnectorHelpers.wait_job_deleted( job_id )
    ConnectorHelpers.wait_job_deleted( job_id_2 )
    ConnectorHelpers.wait_job_deleted( job_id_3 )

    print "Running ingestion jobs"
    
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

    # Run the domain-based job to completion
    ConnectorHelpers.start_job( job_id_2 )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )

    ConnectorHelpers.wait_job_complete( job_id_2 )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_jcifs_search_url(id1), make_jcifs_dfs_search_url(id1), make_jcifs_dfs_domain_search_url(id1), make_jcifs_dfs_onedeep_search_url(id1) ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_jcifs_search_url(id2), make_jcifs_dfs_search_url(id2), make_jcifs_dfs_domain_search_url(id2), make_jcifs_dfs_onedeep_search_url(id2) ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_jcifs_search_url(id3), make_jcifs_dfs_search_url(id3) , make_jcifs_dfs_domain_search_url(id3), make_jcifs_dfs_onedeep_search_url(id3) ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_jcifs_search_url(id4), make_jcifs_dfs_search_url(id4), make_jcifs_dfs_domain_search_url(id4), make_jcifs_dfs_onedeep_search_url(id4) ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_jcifs_search_url(id5), make_jcifs_dfs_search_url(id5), make_jcifs_dfs_domain_search_url(id5), make_jcifs_dfs_onedeep_search_url(id5) ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_jcifs_search_url(id6), make_jcifs_dfs_search_url(id6), make_jcifs_dfs_domain_search_url(id6), make_jcifs_dfs_onedeep_search_url(id6) ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_jcifs_search_url(id7), make_jcifs_dfs_search_url(id7), make_jcifs_dfs_domain_search_url(id7), make_jcifs_dfs_onedeep_search_url(id7) ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ make_jcifs_search_url(id8), make_jcifs_dfs_search_url(id8), make_jcifs_dfs_domain_search_url(id8), make_jcifs_dfs_onedeep_search_url(id8) ] )

    # Do a metadata search too
    criteria = "/"+jcifsShare+"/"+id1
    print "doc_path criteria is: '%s'" % criteria
    ConnectorHelpers.search_check( [ "reference", "metadata:doc_path=%s" % criteria ], None, [ make_jcifs_search_url(id1) ] )

    # Success: done
    print "Done ingestion test."

    # Clean up domain-based crawl pieces
    ConnectorHelpers.delete_job( job_id_2 )
    ConnectorHelpers.wait_job_deleted( job_id_2 )
    ConnectorHelpers.delete_repository_connection_ui( username, password, "JCifsConnection2" )

    # PHASE 2: Document Change Detection

    print "Document Change Test."
    # Modify the documents
    JcifsConnectorHelpers.version_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id2, "/root/crawlarea/testfiles/f002a.txt")
    JcifsConnectorHelpers.version_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id4, "/root/crawlarea/testfiles/f004a.txt")
    JcifsConnectorHelpers.version_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+id2, "/root/crawlarea/testfiles/f002a.txt")
    JcifsConnectorHelpers.version_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+id4, "/root/crawlarea/testfiles/f004a.txt")
    JcifsConnectorHelpers.version_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+id2, "/root/crawlarea/testfiles/f002a.txt")
    JcifsConnectorHelpers.version_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+id4, "/root/crawlarea/testfiles/f004a.txt")
    # Sleep, in case there's clock skew
    JcifsConnectorHelpers.wait_for_jcifs(jcifsServerName, jcifsUser, jcifsPassword)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Look for state of index being right
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_jcifs_search_url(id1), make_jcifs_dfs_search_url(id1), make_jcifs_dfs_onedeep_search_url(id1) ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_jcifs_search_url(id3), make_jcifs_dfs_search_url(id3), make_jcifs_dfs_onedeep_search_url(id3) ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_jcifs_search_url(id5), make_jcifs_dfs_search_url(id5), make_jcifs_dfs_onedeep_search_url(id5) ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_jcifs_search_url(id6), make_jcifs_dfs_search_url(id6), make_jcifs_dfs_onedeep_search_url(id6) ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_jcifs_search_url(id7), make_jcifs_dfs_search_url(id7), make_jcifs_dfs_onedeep_search_url(id7) ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ make_jcifs_search_url(id8), make_jcifs_dfs_search_url(id8), make_jcifs_dfs_onedeep_search_url(id8) ] )
    ConnectorHelpers.search_check( [ "platypus" ], None, [ make_jcifs_search_url(id2), make_jcifs_dfs_search_url(id2), make_jcifs_dfs_onedeep_search_url(id2) ] )
    ConnectorHelpers.search_check( [ "establishments" ], None, [ make_jcifs_search_url(id4), make_jcifs_dfs_search_url(id4), make_jcifs_dfs_onedeep_search_url(id4) ] )

    print "Done Document Change Test."

    # PHASE 3: Document Delete Detection

    print "Document Delete Test."
    JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id3 )
    JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id5 )
    JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+id3 )
    JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+id5 )
    JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+id3 )
    JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+id5 )

    # Sleep, in case of clock skew
    JcifsConnectorHelpers.wait_for_jcifs(jcifsServerName, jcifsUser, jcifsPassword)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ ] )
    print "Done Document Delete Test."

    # PHASE 4: Document Addition Detection

    print "Document Add Test."
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id9, "/root/crawlarea/testfiles/f009.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id10, "/root/crawlarea/testfiles/f010.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+id9, "/root/crawlarea/testfiles/f009.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+id10, "/root/crawlarea/testfiles/f010.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+id9, "/root/crawlarea/testfiles/f009.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+id10, "/root/crawlarea/testfiles/f010.txt")

    JcifsConnectorHelpers.wait_for_jcifs(jcifsServerName, jcifsUser, jcifsPassword)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    ConnectorHelpers.search_check( [ "albemarle" ], None, [ make_jcifs_search_url(id9), make_jcifs_dfs_search_url(id9), make_jcifs_dfs_onedeep_search_url(id9) ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [ make_jcifs_search_url(id10), make_jcifs_dfs_search_url(id10), make_jcifs_dfs_onedeep_search_url(id10) ] )
    print "Done Document Add Test."

    # PHASE 5: Delete Job

    print "Job Delete Test."
    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    # Make sure the documents all went away
    ConnectorHelpers.search_check( [ "reference" ], None, [] )
    ConnectorHelpers.search_check( [ "good" ], None, [] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [] )
    ConnectorHelpers.search_check( [ "pub" ], None, [] )
    ConnectorHelpers.search_check( [ "city" ], None, [] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [] )
    ConnectorHelpers.search_check( [ "albemarle" ], None, [] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [] )
    print "Done Job Delete Test."

    print "Testing file name and URL mapping"
    
    # Create a new job that has both realistic file name and URI mapping enabled.  Make sure we get the right answers for all documents.
    job_id = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                        password,
                        "JCifs test job",
                        "JCifsConnection",
                        [ ( [ jcifsShare, "TestDocs" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ),
                          ( [ latin1share ], [ ("include", "file", "*"), ("include", "directory", "*") ] ) ],
                        filename_mappings=[ ( "(.*)txt$", "$(1)text" ) ],
                        url_mappings=[ ( "[^/]*/(.*)", "http://someserver.metacarta.com/$(1u)" ) ] )

    # Run the job
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Sample a couple to be sure we got the ingestion URI's right
    ConnectorHelpers.search_check( [ "reference" ], None, [ "someserver.metacarta.com/QASHARE/TESTDOCS/F001.TEXT" ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ "someserver.metacarta.com/QASHARE/TESTDOCS/F006.TEXT" ] )
    ConnectorHelpers.search_check( [ "mumblefrats" ], None, [ "someserver.metacarta.com/%C3%98YVIND/SOMEDOC.TEXT" ] )
    
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    print "Removing repository connection"
    
    ConnectorHelpers.delete_repository_connection_ui( username, password, "JCifsConnection" )

    # Clean up the documents we dumped into the folders on jcifs
    for docid in [id1,id2,id3,id4,id5,id6,id7,id8,id9,id10]:
        JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+docid )

    # Clean up the documents we dumped into the dfs folders on jcifs
    for docid in [id1,id2,id3,id4,id5,id6,id7,id8,id9,id10]:
        JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsTarget+"/"+docid )
        JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsDfsOneDeepTarget+"/"+docid )

    # PHASE 6: Security test with AD and ACLs
    # This test relies on the ability to set permissions on folders via JCifs, which is
    # not something I currently know how to do.  I've left the core of the test in place, however,
    # so that if the knowledge becomes available I can proceed.


    # Now, set up ad with the following users:
    # usera
    # userb
    # userc

    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_jcifsusera", "usera" )
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_jcifsuserb", "userb" )
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_jcifsuserc", "userc" )

    # Now, add these users to the appropriate directories
    JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/userafolder", [ ad_domain_info.realm_admin,"p_jcifsusera@"+jcifsDomain ])
    JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/userbfolder", [ ad_domain_info.realm_admin,"p_jcifsuserb@"+jcifsDomain ])
    JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/usercfolder", [ ad_domain_info.realm_admin,"p_jcifsuserc@"+jcifsDomain ])
    JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/newfolder", [ad_domain_info.realm_admin,"p_jcifsusera@"+jcifsDomain, "p_jcifsuserb@"+jcifsDomain])

    # Add the current set of docs to the repository, and twiddle their security
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id1, "/root/crawlarea/testfiles/f001.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id2, "/root/crawlarea/testfiles/f002.txt")
    # TestDocs.userafolder will be visible by usera only
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id3a, "/root/crawlarea/testfiles/f003.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id4a, "/root/crawlarea/testfiles/f004.txt")
    JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id3a, [ ad_domain_info.realm_admin,"p_jcifsusera@"+jcifsDomain ])
    JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id4a, [ad_domain_info.realm_admin,"p_jcifsusera@"+jcifsDomain ])
    # TestDocs.userbfolder will be visible by userb only
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id5a, "/root/crawlarea/testfiles/f005.txt")
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id6a, "/root/crawlarea/testfiles/f006.txt")
    JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id5a, [ ad_domain_info.realm_admin,"p_jcifsuserb@"+jcifsDomain ])
    JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id6a, [ ad_domain_info.realm_admin,"p_jcifsuserb@"+jcifsDomain ])
    # TestDocs.usercfolder will be visible by userc only
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id7a, "/root/crawlarea/testfiles/f007.txt")
    JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id7a, [ ad_domain_info.realm_admin,"p_jcifsuserc@"+jcifsDomain ])
    # TestDocs.newfolder will be visible by both usera and userb, but not userc
    JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id8, "/root/crawlarea/testfiles/newfolder/f008.txt")
    JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id8, [ad_domain_info.realm_admin,"p_jcifsusera@"+jcifsDomain, "p_jcifsuserb@"+jcifsDomain])

    # Define repository connection
    ConnectorHelpers.define_repositoryconnection( "JCifsConnection",
                                 "JCifs Connection",
                                 "com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector",
                                 configparams = [ "Server="+jcifsServerName,
                                                  "User Name="+jcifsUser,
                                                  "Password="+jcifsPassword ] )

    # PHASE 6: Ingest documents with the specified connectors.
    # There will be several separate kinds of security as well.

    # Define jobs
    job_id = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                                password,
                                "JCifs test job",
                                "JCifsConnection",
                                [ ( [ jcifsShare, "TestDocs" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ) ],
                                security_enabled=True )

    #doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="qashare/TestDocs"><include type="file" filespec="*"/><include type="directory" filespec="*"/></startpoint><security value="on"/></specification>'
    #job_id = ConnectorHelpers.define_job( "JCifs test job",
    #                         "JCifsConnection",
    #                         doc_spec_xml )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Check for proper visibility of all documents
    # usera
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_jcifs_search_url(id1) ], username="p_jcifsusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_jcifs_search_url(id3a) ], username="p_jcifsusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="p_jcifsusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="p_jcifsusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="p_jcifsusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ make_jcifs_search_url(id8) ], username="p_jcifsusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_jcifs_search_url(id2) ], username="p_jcifsusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_jcifs_search_url(id4a) ], username="p_jcifsusera", password="usera", win_host=ad_win_host )

    # userb
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_jcifs_search_url(id1) ], username="p_jcifsuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="p_jcifsuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_jcifs_search_url(id5a) ], username="p_jcifsuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_jcifs_search_url(id6a) ], username="p_jcifsuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="p_jcifsuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ make_jcifs_search_url(id8) ], username="p_jcifsuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_jcifs_search_url(id2) ], username="p_jcifsuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="p_jcifsuserb", password="userb", win_host=ad_win_host )

    # userc
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_jcifs_search_url(id1) ], username="p_jcifsuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="p_jcifsuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="p_jcifsuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="p_jcifsuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_jcifs_search_url(id7a) ], username="p_jcifsuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ ], username="p_jcifsuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_jcifs_search_url(id2) ], username="p_jcifsuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="p_jcifsuserc", password="userc", win_host=ad_win_host )


    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    ConnectorHelpers.delete_repositoryconnection( "JCifsConnection" )

    # The last test series involves crawling as usera, rather than as
    # Administrator.  This will allow us to see if documents for which
    # there is no access permission are properly skipped during the crawl.
    # Define repository connection
    ConnectorHelpers.define_repositoryconnection( "JCifsConnection",
                                                  "JCifs Connection",
                                                  "com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector",
                                                  configparams = [ "Server="+jcifsServerName,
                                                                   "User Name="+"p_jcifsusera@"+jcifsDomain,
                                                                   "Password="+"usera" ] )

    # Define jobs
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="qashare/TestDocs"><include type="file" filespec="*"/><include type="directory" filespec="*"/></startpoint><security value="on"/></specification>'
    job_id = ConnectorHelpers.define_job( "JCifs test job",
                                          "JCifsConnection",
                                          doc_spec_xml )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # For now, we don't need to check anything to be sure it got processed,
    # because the goal of the test is just to insure that unauthorized
    # documents don't cause CF to retry indefinitely.

    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    # Clean up repository connection
    ConnectorHelpers.delete_repositoryconnection( "JCifsConnection" )

    ConnectorHelpers.end_timing("ad_test_part", limit=850)

    # Clean up everything in the environment that may have happened during this block (e.g. after ad was enabled)
    # Remove test documents first
    # Clean up the documents we dumped into the folders on jcifs server
    for docid in [id1,id2,id3,id4,id5,id6,id7,id8,id3a,id4a,id5a,id6a,id7a]:
        JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+docid )

    # Next, disassociate users from share folders
    JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/userafolder", [ad_domain_info.realm_admin])
    JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/userbfolder", [ad_domain_info.realm_admin])
    JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/usercfolder", [ad_domain_info.realm_admin])
    JcifsConnectorHelpers.set_target_permission(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/newfolder", [ad_domain_info.realm_admin])

    # Disable ad
    ConnectorHelpers.turn_off_ad( ad_domain_info )


# Main
if __name__ == '__main__':

    # AD parameters
    ad_group = "76"
    share_machine_handle = "jcifs_server"
    if len(sys.argv) > 1:
        ad_group = sys.argv[1]
    if len(sys.argv) > 2:
        share_machine_handle = sys.argv[2]
    perform_legacy_pass = False
    if len(sys.argv) > 3 and sys.argv[3] == "legacy":
        perform_legacy_pass = True

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

    preclean( ad_domain_info, perform_legacy_pass, print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up file area."
    copy_folder("/root/testfiles","/root/crawlarea")

    # Enable CF security
    ConnectorHelpers.enable_connector_framework( )


    print "Initializing test documents."
    # These documents are not yet part of the set that gets moved over, but they may be in the future
    o = open( "/root/crawlarea/testfiles/f002a.txt", "w" )
    o.write("Now this document is at 50N 75E, and the keyword is platypus")
    o.close()
    o = open( "/root/crawlarea/testfiles/f004a.txt", "w" )
    o.write("No longer about drinking establishments at 23N 15W")
    o.close()
    o = open( "/root/crawlarea/testfiles/f009.txt", "w" )
    o.write("Now this document is at 50N 75E, and the keyword is albemarle")
    o.close()
    o = open( "/root/crawlarea/testfiles/f010.txt", "w" )
    o.write("No longer about golfcarts at 23N 15W")
    o.close()

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["shareConnector"], detect_gdms=True)


    # Set up the ingestion user.

    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # Phase -0.1: Check that the daemon has the right switches (fix for 32897)
    ConnectorHelpers.confirm_daemon_switch(" -Djcifs.smb.client.soTimeout=150000 ")
    ConnectorHelpers.confirm_daemon_switch(" -Djcifs.smb.client.responseTimeout=120000 ")
    
    # Phase 0: Check that share security seems to work
    print "Checking whether Samba share security authenticates."

    ConnectorHelpers.set_shareconnector_ntlmv1_mode()

    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection",
                                        "JCifs Connection",
                                        samba_share_server,
                                        samba_share_domain,
                                        samba_share_username,
                                        samba_share_password )

    ConnectorHelpers.view_repository_connection_ui( username, password, "JCifsConnection" )

    ConnectorHelpers.delete_repository_connection_ui( username, password, "JCifsConnection" )

    # Restore default mode
    ConnectorHelpers.set_shareconnector_default_mode()

    # Phase 0.1: Check that samba-based dfs seems to work
    print "Checking whether samba-hosted DFS works."

    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection",
                                        "JCifs Connection",
                                        samba_dfs_server,
                                        samba_dfs_domain,
                                        samba_dfs_username,
                                        samba_dfs_password )

    ConnectorHelpers.view_repository_connection_ui( username, password, "JCifsConnection" )

    # Attempt to create a job that has a path "dfs/dfslink/LargeDir"; if this succeeds, we know that jcifs can successfully
    # deal with DFS on samba
    job_id = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                        password,
                        "JCifs test job",
                        "JCifsConnection",
                        [ ( [ "dfs", "dfslink", "LargeDir" ], [ ] ) ] )

    # Delete the job
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    # Phase 0.2: Check that server names cannot include "/" characters
    print "Checking that server names cannot include / characters"
    
    try:
        JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection",
                                        "JCifs Connection",
                                        "w2k3-shp-76-1.qa-ad-76.metacarta.com/foo",
                                        "qa-ad-76.metacarta.com",
                                        "Administrator",
                                        "password" )
        exception_seen = False
    except:
        # this is expected
        exception_seen = True

    if not exception_seen:
        raise Exception("Server name allows '/' character, and shouldn't!")

    # Delete the connection
    ConnectorHelpers.delete_repository_connection_ui( username, password, "JCifsConnection" )



    # The ad part of this test may need to run in both legacy and non-legacy modes
    run_ad_test_part( ad_domain_info )
    if perform_legacy_pass:
        # Set up legacy mode
        ConnectorHelpers.select_legacy_mode()
        # Run the ad part of the test again
        run_ad_test_part( ad_domain_info )
        # Cancel legacy mode
        ConnectorHelpers.cancel_legacy_mode()

    # This part of the test does not run with ad enabled

    # Phase 7: Use multiple connections to tweak Connector Framework code that had a bug in it handling this case.
    # The test must successfully complete without null pointer exceptions in the log.
    # The separate jobs must start close enough together in time, and have enough documents in them, so that there's a high likelihood that documents from both jobs will be
    # queued for processing at the same time.  Detecting this is therefore probabalistic and not deterministic - so the strategy will be to execute n trials of the same task in hopes
    # picking up the problem.

    # First, create n documents in each of the two test areas
    for docinstance in idrange:
        doc_name_1 = "%s_%d.txt" % (idbase_connection1, docinstance)
        doc_name_2 = "%s_%d.txt" % (idbase_connection2, docinstance)
        # Suck the data onto the shares
        filename = "/root/crawlarea/testfiles/f00%d.txt" % ((docinstance % 4) + 1)
        JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+doc_name_1, filename)
        JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+doc_name_2, filename)

    # Clear the logs
    ConnectorHelpers.clear_logs()
    # Restart the services so new logs will be used
    ConnectorHelpers.restart_tomcat()
    ConnectorHelpers.restart_agents()

    ConnectorHelpers.start_timing("multithread_test")

    # Next, create two repository connections
    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection1",
                                        "JCifs Connection # 1",
                                        jcifsServerName,
                                        None,
                                        jcifsUser,
                                        jcifsPassword )
    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection2",
                                        "JCifs Connection # 2",
                                        jcifsServerName,
                                        None,
                                        jcifsUser,
                                        jcifsPassword )


    # Now, loop 10 times to be sure we have sufficient chance of catching the problem.
    for loopcount in range(10):
        # Create two jobs
        job_id_1 = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                        password,
                        "JCifs test job # 1",
                        "JCifsConnection1",
                        [ ( [ jcifsShare, "TestDocs1" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ) ] )

        job_id_2 = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                        password,
                        "JCifs test job # 2",
                        "JCifsConnection2",
                        [ ( [ jcifsShare, "TestDocs2" ], [ ("include", "file", "*"), ("include", "directory", "*") ] ) ] )

        # Run the jobs...
        ConnectorHelpers.start_job( job_id_1 )
        ConnectorHelpers.start_job( job_id_2 )

        # Wait for them to finish.  If the bug is there this may wait indefinitely.
        ConnectorHelpers.wait_job_complete( job_id_1 )
        ConnectorHelpers.wait_job_complete( job_id_2 )

        # Check for NPE's in log
        results = ConnectorHelpers.read_log( "NullPointerException" )
        if len(results) > 0:
            raise Exception("Concurrent jobs detected null pointer exception!  Check the agents.log file.")

        ConnectorHelpers.delete_job( job_id_1 )
        ConnectorHelpers.delete_job( job_id_2 )
        ConnectorHelpers.wait_job_deleted( job_id_1 )
        ConnectorHelpers.wait_job_deleted( job_id_2 )

    # Clean up repository connections
    ConnectorHelpers.delete_repositoryconnection( "JCifsConnection2" )
    ConnectorHelpers.delete_repositoryconnection( "JCifsConnection1" )

    ConnectorHelpers.end_timing("multithread_test", limit=900)

    # Now, delete the documents created for this test
    for docinstance in idrange:
        doc_name_1 = "%s_%d.txt" % (idbase_connection1, docinstance)
        doc_name_2 = "%s_%d.txt" % (idbase_connection2, docinstance)
        JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare + "/" + doc_name_1)
        JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare + "/" + doc_name_2)

    # PHASE 8: Attempt fingerprinting of some problem documents
    print "Fingerprinting test"

    # Add fingerprinting docs to the repository
    for id,filename in [ (id_fingerprint_1, "/root/fingerprinter/test1.doc"),
                              (id_fingerprint_2, "/root/fingerprinter/excel2003.xls"),
                              (id_fingerprint_3, "/root/fingerprinter/powerpoint2003.ppt"),
                              (id_fingerprint_4, "/root/fingerprinter/word2003.doc"),
                              (id_fingerprint_5, "/root/fingerprinter/excel2007.xls"),
                              (id_fingerprint_6, "/root/fingerprinter/powerpoint2007.ppt"),
                              (id_fingerprint_7, "/root/fingerprinter/word2007.doc") ]:
        JcifsConnectorHelpers.add_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+id, filename, mode="binary")

    # In case there is clock skew, sleep a minute
    JcifsConnectorHelpers.wait_for_jcifs(jcifsServerName, jcifsUser, jcifsPassword)

    ConnectorHelpers.start_timing("fingerprint_test")

    # Define basic server-based repository connection
    JcifsConnectorHelpers.define_jcifs_repository_connection_ui( username,
                                        password,
                                        "JCifsConnection",
                                        "JCifs Connection",
                                        jcifsServerName,
                                        None,
                                        jcifsUser,
                                        jcifsPassword )

    # Create a job that uses fingerprinting
    job_id = JcifsConnectorHelpers.define_jcifs_job_ui( username,
                password,
                "JCifs test job",
                "JCifsConnection",
                [ ( [ jcifsShare, "TestDocs" ], [ ("include", "indexable-file", "*"), ("include", "directory", "*") ] ) ] )

    # Run the job...
    ConnectorHelpers.start_job( job_id )
    # If the indexer chokes on any of these files, the job will never complete
    ConnectorHelpers.wait_job_complete( job_id )

    search_url_list = []
    for doc_id in [ id_fingerprint_2, id_fingerprint_3, id_fingerprint_4, id_fingerprint_5, id_fingerprint_6, id_fingerprint_7 ]:
        search_url_list += [ make_jcifs_search_url(doc_id) ]

    # Wait for ingestion to complete and check that the proper documents are searchable
    ConnectorHelpers.wait_for_ingest( )

    ConnectorHelpers.search_check( [ "document" ], None, search_url_list )

    # Delete the job
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    # Delete repository connection
    ConnectorHelpers.delete_repositoryconnection( "JCifsConnection" )

    ConnectorHelpers.end_timing("fingerprint_test", limit=180)

    # Remove fingerprinting docs from the repository
    for id in [ id_fingerprint_1, id_fingerprint_2, id_fingerprint_3, id_fingerprint_4, id_fingerprint_5, id_fingerprint_6, id_fingerprint_7 ]:
        JcifsConnectorHelpers.remove_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare + "/" +id)

    # Get rid of test crawler user
    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    # Clean up temporary folder
    delete_folder("/root/crawlarea")

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "Basic JCifsConnector tests PASSED"
