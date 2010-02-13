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
import md5
import time
import ConnectorHelpers
from sqatools import LicenseMakerClient
from sqatools import appliance
import ZPF_Filter

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

def save_local_files():
    """ Generate files by replicating 10 test files 650,000 times """
    # We need 6,500,000 documents.  We'll get this by
    # having 10 documents under 6 levels of a hierarchy, and truncating when we reach the proper count
    filecount = 0
    max_file_count = 6500000
    level0 = 0
    while level0 < 10 and filecount < max_file_count:
        level1 = 0
        while level1 < 10 and filecount < max_file_count:
            level2 = 0
            while level2 < 10 and filecount < max_file_count:
                level3 = 0
                while level3 < 10 and filecount < max_file_count:
                    level4 = 0
                    while level4 < 10 and filecount < max_file_count:
                        level5 = 0
                        while level5 < 10 and filecount < max_file_count:
                            pathname = "/common/crawlarea/%d/%d/%d/%d/%d/%d" % (level0,level1,level2,level3,level4,level5)
                            copy_folder("/root/largefiles",pathname)
                            filecount += 10
                            level5 += 1
                        level4 += 1
                    level3 += 1
                level2 += 1
            level1 += 1
        level0 += 1

def convert_url_to_filename( url ):
    """ Convert a url to a hopefully-unique document name """
    # Because of length limitations, instead of character stuffing I had
    # to use a well-formed hash hex value
    return md5.new(url).hexdigest()

def get_path_from_filename( filename ):
    """ Get a two-level path from a filename """
    first_level = filename[len(filename)-2:len(filename)]
    second_level = filename[len(filename)-4:len(filename)-2]
    return "%s/%s" % (first_level,second_level)

def save_file(doc_contents, doc_directory, doc_url):
    """ Save a document that came in from a zpf.
        The document should be put into the specified directory, and
        the filename of the document must be based on the specified url.
    """
    file_name = convert_url_to_filename(doc_url)
    directory_path = "%s/%s" % (doc_directory,get_path_from_filename(file_name))
    # Make sure path exists
    try:
        os.makedirs(directory_path)
    except OSError,e:
        pass

    file_name = "%s/%s.htm" % (directory_path,file_name)

    # Are these binary?  No harm in presuming that...
    fhandle = open(file_name,"wb")
    try:
        fhandle.write(doc_contents)
    finally:
        fhandle.close()

class ZPF_save(ZPF_Filter.ZPF_filter):
    def handle_metadata(self, zpfin, zpfout, str, vals, mvals):
        self.url = vals.group("url")
        # we don't really need creation time
        return 1

    def handle_doc(self, zpfin, zpfout, doclen, flags):
        """ Takes the input and output strings, a length, and flags that we can ignore """

        doc = zpfin.read(doclen)
        if len(doc):
            save_file(doc, self.directory, self.url)

    def __init__(self, directory):
        self.directory = directory

def save_zpf_pipe_files():
    """ Generate files by receiving them over stdin as a zpf """
    # cheat a bit - gunzip will inherit our stdin, we just want stdout...
    zfile = os.popen("gunzip", "r")
    z = ZPF_save("/common/crawlarea")
    z.scan_zpf(zfile,None)

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

# Main
if __name__ == '__main__':

    # Look for a mode to indicate whether to populate our own data, or get it passed to us in a zpf
    mode = "local"
    if len(sys.argv) > 1:
        # Mode is "local" or "zpf-pipe"
        mode = sys.argv[1]

    print "Precleaning!"

    preclean( print_errors=False )

    print "Setting up file area."

    if mode == "local":
        save_local_files()
    elif mode == "zpf-pipe":
        # The documents will be piped to us as a zpf over stdin; use techniques borrowed from bulk_zpf_ingestion.py accordingly
        os.mkdir("/common/crawlarea")
        save_zpf_pipe_files()
    else:
        raise Exception("Illegal test operating mode: %s" % mode)

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Creating crawler user."
    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # Before we begin, do postgres maintenance to be sure we start in the same place each time
    ConnectorHelpers.run_maintenance()

    # PHASE 1: Ingestion

    print "Scale Test."

    # Define repository connection
    ConnectorHelpers.define_filesystem_repository_connection_ui( username, password,
                                "FileSystem",
                                "FileSystem Connection",
                                max_connections=100 )

    # Define job
    job_id = ConnectorHelpers.define_filesystem_job_ui( username, password,
                        "Test job",
                        "FileSystem",
                        [ ("/common/crawlarea", [( "include", "file", "*.htm" ), ( "include", "directory", "*" )]) ],
                        hop_mode="neverdelete" )

    # Run the job
    ConnectorHelpers.start_job( job_id )

    # The test requires about 14 days to run.  We need to do postgres maintenance during that
    # time to keep performance up to standards.  I've chosen to do it every 3 days...
    while True:
        abort = False
        interval_begin = time.time()
        while time.time() - interval_begin < 259200.0:
            time.sleep(600)
            status = ConnectorHelpers.get_job_status_ui( username, password, job_id )
            if status != "Starting up" and status != "Running":
                abort = True
                break
        if abort:
            break
        ConnectorHelpers.run_maintenance()

    # Now, let job complete
    ConnectorHelpers.wait_job_complete( job_id )

    # Success: done
    print "Done ingestion scale test."

    # PHASE 2: Cleanup test

    print "Cleanup scale Test."

    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    print "Done Cleanup Scale Test."

    ConnectorHelpers.delete_repositoryconnection( "FileSystem" )

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    delete_folder("/common/crawlarea")
    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "Scale ConnectorFramework test PASSED"
