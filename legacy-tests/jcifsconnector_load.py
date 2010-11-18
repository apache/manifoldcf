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
import sqatools
from wintools import sqa_domain_info
from wintools import filetools
from wintools import ambassador_client
from sqatools import LicenseMakerClient
import TestDocs

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

SHARE_ROOT='C:\\'

def disable_receive_port_445(server_host):
    """ Use iptables to shut off the ability of appliance to talk to port 445 on server """
    ConnectorHelpers.invoke_root_script( ["iptables", "-A", "INPUT", "-s", socket.gethostbyname(server_host), "-p", "tcp", "--sport", "445", "-j", "DROP" ] )

def enable_receive_port_445():
    """ Reverse iptables changes to re-enable appliance to talk to port 445 on server """
    ConnectorHelpers.invoke_root_script( ["iptables", "-D", "INPUT", "1" ] )

def path_to_diskpath(targetpath):
    return SHARE_ROOT+targetpath.replace("/","\\")

# Method to create a directory
def create_directory(jcifs_servername, jcifs_user, jcifs_password, targetpath):
    amb = ambassador_client.AmbassadorClient(jcifs_servername+":8000", jcifs_user, jcifs_password)
    targetpath = path_to_diskpath(targetpath)
    python_filename = "/tmp/create_dir.py"
    python_file = open(python_filename, 'w')
    python_file.write('import os\n\n')
    python_file.write('os.makedirs("%s")\n' % targetpath.replace("\\","\\\\"))
    python_file.close()

    output = amb.run_python_script(python_filename,
                                                  supporting_files=[])
    if output:
        raise Exception("Unable to create directory %s:\n%s" % (targetpath,
                                                                    output))
    return output

# Method to add a document to a jcifs share
def add_document(jcifs_servername, jcifs_user, jcifs_password, targetpath, sourcepath):
    """Add a document to the share.  This is a runt method that does not worry about permissions, because once the files are created
       there is no further modification and the acls don't matter. """
    amb = ambassador_client.AmbassadorClient(jcifs_servername+":8000", jcifs_user, jcifs_password)
    targetpath = path_to_diskpath(targetpath)
    permissions = [ ("+", ConnectorHelpers.get_everyone_sid()) ]
    fd = open(sourcepath, "r")
    try:
        lines = fd.readlines()
        newlines = []
        for line in lines:
            newlines.append( line.strip() )
        string = " ".join(newlines)
        # add an extra backslash before each backslash, because they will get
        # interpreted as escape characters otherwise
        file_name = "\\\\".join(targetpath.split("\\"))

        # create a python script to run on the windows machine that
        # will create the file with an appropriate acl
        python_filename = "/tmp/create_file2.py"
        python_file = open(python_filename, 'w')
        python_file.write('fd = open("%s", "w")\n' % file_name)
        python_file.write('fd.write("%s")\n' % filetools.python_escape(string))
        python_file.write('fd.close()\n')
        python_file.close()

        output = amb.run_python_script(python_filename,
                                                supporting_files=[])
        if output:
            raise Exception("Unable to create file %s:\n%s" % (file_name,
                                                                    output))
    finally:
        fd.close()

# Method to remove a document from a jcifs share
def remove_document(jcifs_servername, jcifs_user, jcifs_password, targetpath):
    """Remove a document from the share"""
    #listparams = [ "/usr/lib/metacarta/jcifs-removedoc",
    #       ConnectorHelpers.process_argument(jcifs_servername),
    #       ConnectorHelpers.process_argument(jcifs_user),
    #       ConnectorHelpers.process_argument(jcifs_password),
    #       ConnectorHelpers.process_argument(targetpath) ]
    #try:
    #    ConnectorHelpers.invoke_script( listparams )
    #except Exception, e:
    #    print "Warning: Error deleting document: %s" % str(e)
    print "Erasing %s" % targetpath

    amb = ambassador_client.AmbassadorClient(jcifs_servername+":8000", jcifs_user, jcifs_password)
    targetpath = path_to_diskpath(targetpath)

    try:
        amb.run('erase "%s"' % targetpath)
    except Exception, e:
        print "Warning: Error deleting document: %s" % str(e)

# Method to check for a document on the target
def check_for_document(jcifs_servername, jcifs_user, jcifs_password, targetpath):
    """ Look for existing file on the target """
    amb = ambassador_client.AmbassadorClient(jcifs_servername+":8000", jcifs_user, jcifs_password)
    targetpath = path_to_diskpath(targetpath)
    # add an extra backslash before each backslash, because they will get
    # interpreted as escape characters otherwise
    file_name = "\\\\".join(targetpath.split("\\"))

    # create a python script to run on the windows machine that
    # will create the file with an appropriate acl
    python_filename = "/tmp/lookup_file2.py"
    python_file = open(python_filename, 'w')
    python_file.write('import sys\n')
    python_file.write('try:\n')
    python_file.write(' fd = open("%s", "r")\n' % file_name)
    python_file.write(' found = "YES"\n')
    python_file.write(' fd.close()\n')
    python_file.write('except IOError,e:\n')
    python_file.write(' found = "NO"\n')
    python_file.write('sys.stderr.write(found)\n')
    python_file.close()

    output = amb.run_python_script(python_filename,
                                              supporting_files=[])
    if output != None and output.find("YES") != -1:
        return True
    return False

# Method to wait whatever time is needed after changing jcifs documents
# for them to be noted as changed.
def wait_for_jcifs(jcifs_servername, jcifs_user, jcifs_password):
    """Nothing needed"""
    pass

# Server name to talk to
jcifsServerName = "w2k3-shp-76-1.QA-AD-76.METACARTA.COM"
# Domain
jcifsDomain = "QA-AD-76.METACARTA.COM"
# User
jcifsUser = "Administrator@QA-AD-76.METACARTA.COM"
# Password
jcifsPassword = "password"
# Share name
jcifsShare = "qashare"

def make_jcifs_url(target_path):
    return "file://%s/%s/%s" % (jcifsServerName, jcifsShare, target_path)

def make_jcifs_search_url(target_path):
    return "%s/%s" % (jcifsServerName, target_path)

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

    try:
        ConnectorHelpers.delete_crawler_user( username )
    except Exception, e:
        if print_errors:
            print "Error deleting crawl user"
            print e

    # Turn on iptables
    try:
        enable_receive_port_445()
    except Exception, e:
        if print_errors:
            print "Error enabling receive of 445"
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

def main():
    # AD parameters
    ad_group = "76"
    if len(sys.argv) > 1:
        ad_group = sys.argv[1]

    ad_domain_info = sqa_domain_info.SQADomainInfo( ad_group )

    print "Precleaning!"

    preclean( ad_domain_info, print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()
    
    # Look for the last document
    if check_for_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare + "/LargeDocs/9/9/9/lf009") == False:
        # Presume that we have to set up the server
        print "Initializing test documents."
        # We need at least 10,000 documents.  We'll get this by
        # having 10 documents each with 1000 different names at the same level in the hierarchy.
        level0 = 0
        while level0 < 10:
            level1 = 0
            while level1 < 10:
                level2 = 0
                while level2 < 10:
                    level3 = 0
                    while level3 < 10:
                        filename = "/root/largefiles/00%d.htm" % level0
                        targetname = jcifsShare + "/LargeDocs/%d/%d/%d/lf00%d" % (level1,level2,level3,level0)
                        try:
                            create_directory(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare + "/LargeDocs/%d/%d/%d" % (level1,level2,level3))
                        except:
                            # do nothign
                            pass
                        add_document(jcifsServerName, jcifsUser, jcifsPassword, targetname, filename)
                        level3 += 1
                    level2 += 1
                level1 += 1
            level0 += 1
        print "Done creating test documents on server."

    # The documents are already on the LLserver box, under the LargeDocs folder.  We
    # just need exemplars of each one, so we can search for them.

    # We need at least 10,000 documents.  We'll get this by
    # having 10 documents each with 1000 different names at the same level in the hierarchy.
    # We do it this way because we have no programmatic way of creating folders at this time.
    doc_id_array = []
    level0 = 0
    while level0 < 10:
        targetname = jcifsShare + "/LargeDocs/%d/%d/%d/lf00%d" % (0,0,0,level0)
        doc_id_array.append( targetname )
        level0 += 1

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["shareConnector"], detect_gdms=True)

    # Set up the ingestion user.
    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # PHASE 1: Ingestion


    print "Load Test."

    # In case there is clock skew, sleep a minute
    wait_for_jcifs(jcifsServerName, jcifsUser, jcifsPassword)

    ConnectorHelpers.define_repositoryconnection( "JCifsConnection",
                                 "JCifs Connection",
                                 "com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector",
                                 configparams = [ "Server="+jcifsServerName,
                                                  "User Name="+jcifsUser,
                                                  "Password="+jcifsPassword ] )

    # Define job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="qashare/LargeDocs"><include type="file" filespec="*"/><include type="directory" filespec="*"/></startpoint><sharesecurity value="off"/><security value="off"/></specification>'
    job_id = ConnectorHelpers.define_job( "JCIFS test job",
                             "JCifsConnection",
                             doc_spec_xml )
    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    # Wait until it's really working, then cycle the service, to be sure it is shutting down cleanly
    time.sleep(120)
    
    print "Restarting java-agents..."
    
    ConnectorHelpers.restart_agents( )
    
    for trial in range(5):
        # Now, wait until it's cooking again, then throw up a firewall
        time.sleep(30)
        # Check for job status
        results = ConnectorHelpers.list_job_statuses_api()
        if len(results) != 1:
            raise Exception("Expected 1 job to have a status, instead found %d" % len(results))
        if results[0]["status"] == "done":
            break
        if results[0]["status"] != "running":
            raise Exception("Expected job to be running after firewall break, but it wasn't!  Status = %s" % results[0]["status"])

        print "Blocking responses from server..."
        disable_receive_port_445(jcifsServerName)
        # Wait 3 minutes
        time.sleep(180)
        
        print "Re-enabling responses from server..."
        enable_receive_port_445()

    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_exists_check( [ "divestment url:file://///" + make_jcifs_search_url(doc_id_array[0]) ], None, ConnectorHelpers.regexp_encode(make_jcifs_search_url( doc_id_array[0] )) )
    ConnectorHelpers.search_exists_check( [ "visitors url:file://///" + make_jcifs_search_url(doc_id_array[2]) ], None, ConnectorHelpers.regexp_encode(make_jcifs_search_url( doc_id_array[2] )) )

    # Success: done
    print "Done load test."

    # PHASE 5: Delete Job

    print "Job Delete Test."
    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    # Make sure the documents all went away
    ConnectorHelpers.search_nonexists_check( [ "divestment url:file://///" + make_jcifs_search_url(doc_id_array[0]) ], None, ConnectorHelpers.regexp_encode(make_jcifs_search_url( doc_id_array[0] )) )
    ConnectorHelpers.search_nonexists_check( [ "visitors url:file://///" + make_jcifs_search_url(doc_id_array[2]) ], None, ConnectorHelpers.regexp_encode(make_jcifs_search_url( doc_id_array[2] )) )

    print "Done Job Delete Test."

    ConnectorHelpers.delete_repositoryconnection( "JCifsConnection" )

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "JCifs load tests PASSED"

# Main
if __name__ == '__main__':
    main()
