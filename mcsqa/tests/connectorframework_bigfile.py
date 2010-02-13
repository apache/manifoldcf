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


class ZPF_save(ZPF_Filter.ZPF_filter):
    def handle_metadata(self, zpfin, zpfout, str, vals, mvals):
        self.url = vals.group("url")
        # we don't really need creation time
        return 1

    def handle_doc(self, zpfin, zpfout, doclen, flags):
        """ Takes the input and output strings, a length, and flags that we can ignore """

        # Since the document is quite large, we have to stream it
        file_name = convert_url_to_filename(self.url)
        directory_path = "%s/%s" % (self.directory,get_path_from_filename(file_name))
        # Make sure path exists
        try:
            os.makedirs(directory_path)
        except OSError,e:
            pass

        file_name = "%s/%s.htm" % (directory_path,file_name)

        # Are these binary?  No harm in presuming that...
        fhandle = open(file_name,"wb")
        try:
            while True:
                if doclen <= 65536:
                    break
                doc_contents = zpfin.read(65536)
                fhandle.write(doc_contents)
                doclen -= len(doc_contents)
            if doclen > 0:
                doc_contents = zpfin.read(doclen)
                fhandle.write(doc_contents)
        finally:
            fhandle.close()

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
        ConnectorHelpers.reset_all()
    except Exception, e:
        if print_errors:
            print "Error resetting all jobs"
            print e

    try:
        ConnectorHelpers.restore_default_proxy_timeout()
    except Exception, e:
        if print_errors:
            print "Error setting proxy timeout to default"
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

    print "Setting up file area."

    # The documents will be piped to us as a zpf over stdin; use techniques borrowed from bulk_zpf_ingestion.py accordingly
    os.mkdir("/common/crawlarea")
    save_zpf_pipe_files()

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Creating crawler user."
    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # PHASE 1: Ingestion

    print "Big file test."

    ConnectorHelpers.set_proxy_timeout(3600)

    # Define repository connection
    ConnectorHelpers.define_filesystem_repository_connection_ui( username, password, "FileSystem",
                                 "FileSystem Connection",
                                 max_connections=100 )

    # Define job
    job_id = ConnectorHelpers.define_filesystem_job_ui( username, password,
                        "Test job",
                        "FileSystem",
                        [ ("/common/crawlarea", [( "include", "file", "*.htm" ), ( "include", "directory", "*" )]) ],
                        hop_mode="neverdelete" )

    # Record the start time
    the_start_time = time.time()

    # Run the job
    ConnectorHelpers.start_job( job_id )

    # Now, let job complete
    ConnectorHelpers.wait_job_complete( job_id )

    # Check to be sure 1 document got ingested
    results = ConnectorHelpers.run_simple_history_report_api( "FileSystem",
        ["document ingest (GTS)"],
        start_time=ConnectorHelpers.build_api_time(the_start_time) )

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

    ConnectorHelpers.delete_repositoryconnection( "FileSystem" )

    # Restore default timeout
    ConnectorHelpers.restore_default_proxy_timeout()

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    delete_folder("/common/crawlarea")
    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "Big File ConnectorFramework test PASSED"
