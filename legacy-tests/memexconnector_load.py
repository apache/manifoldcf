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
import sqatools
from wintools import sqa_domain_info
from sqatools import LicenseMakerClient
import TestDocs

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

# Set up SSL mode
def setup_ssl_mode( file_name ):
    ConnectorHelpers.invoke_root_script( [ "/usr/bin/memexconnector_control", "installcert", file_name ] )

# Tear down SSL mode
def teardown_ssl_mode( ):
    ConnectorHelpers.invoke_root_script( [ "/usr/bin/memexconnector_control", "removecert" ] )

# Method to load the contents of a text file into a string in memory
def load_file( file_name ):
        f = open(file_name, "r")
        try:
                return f.read()
        finally:
                f.close()

# Method to add a record to the memex repository, and return a record number
def add_record(servername, port, user, password, virtual_server, entity_name, field_dict):
        """Add a record to the repository"""
        fields = []
        for field in field_dict.keys():
                value = field_dict[field]
                fields = fields + [ field, value ]
        return ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.memex.AddRecord", 
                                    argument_list=[servername,port,user,password,virtual_server,entity_name] + fields,
                                    additional_classpath="memex-testing-package/metacarta-memexconnector-test.jar")

# Method to locate a document based on field data
def locate_record(servername, port, user, password, virtual_server, entity_name, field_name, field_value):
        """ Locate a record in the repository """
        rval = ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.memex.LookupRecord", 
                                    argument_list=[servername,port,user,password,virtual_server,entity_name,field_name,field_value],
                                    additional_classpath="memex-testing-package/metacarta-memexconnector-test.jar" )
        if rval == "":
                rval = None
        return rval

# Method to wait whatever time is needed after changing memex documents
# for them to be noted as changed.
def wait_for_memex(servername, port, user, password):
        """Nothing needed"""
        pass

# Server name to talk to
memexServerName = None
# Server port to talk to
memexServerPort = "9001"
# User
memexUser = "system"
# Password
memexPassword = "test"
# Memex virtual server
memexVirtualServer = "London Underground"
# Memex entity name
memexEntityName = "RR"
memexEntityDisplayName = "Report"
# Memex key field name
memexRecordKeyField = "opnameno"
# Memex data field name
memexDataField = "text"

def build_memex_url( id, port=None, server=None ):
        """Build the url from pieces"""
        server_part = server
        if server_part == None:
                server_part = memexServerName
        if port != None:
                server_part = "%s:%s" % (server_part,port)
        return "%s/search.jsp?urn=%s" % (server_part,id)

# Crawl user credentials
username = "testingest"
password = "testingest"

def preclean( print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    try:
        ConnectorHelpers.start_agents( )
    except Exception, e:
        if print_errors:
            print "Error starting metacarta-agents"
            print e

    try:
        ConnectorHelpers.reset_all()
    except Exception, e:
        if print_errors:
            print "Error resetting all jobs"
            print e

    # Turn off memex client SSL
    try:
        teardown_ssl_mode( )
    except Exception, e:
            if print_errors:
                print "Error tearing down SSL mode"
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

    memexServerName = "memex.metacarta.com"
    if len(sys.argv) > 1:
        llServerName = sys.argv[1]

    print "Precleaning!"

    preclean( print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["memexConnector"], detect_gdms=True)

    # Set up the ingestion user.
    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )

    print "Setting up ssl"
    setup_ssl_mode( "memex-client-cert/cacert.pem" )

    created_new_docs = False
    # Look for the last document
    id = locate_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, memexRecordKeyField, "LargeDocs/lf009-9-9-9")
    if id == None:
      created_new_docs = True
      # Presume that we have to set up the server
      print "Initializing test documents."
      # We need at least 10,000 records.  We'll get this by
      # having 10 documents each with 1000 different names at the same level in the hierarchy.
      # We do it this way because we have no programmatic way of creating folders at this time.
      doc_id_array = []
      level0 = 0
      while level0 < 10:
        level1 = 0
        while level1 < 10:
          level2 = 0
          while level2 < 10:
            level3 = 0
            while level3 < 10:
              filename = "/root/largefiles/00%d.htm" % level0
              llname = "lf00%d-%d-%d-%d" % (level0,level1,level2,level3)
              id = locate_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, memexRecordKeyField, "LargeDocs/%s" % llname)
              if id == None:
                id = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"LargeDocs/%s" % llname, memexDataField:load_file(filename)})
              if level1 == 0 and level2 == 0 and level3 == 0:
                doc_id_array.append( id )
              level3 += 1
            level2 += 1
          level1 += 1
        level0 += 1
      print "Done creating test documents on server."

    if created_new_docs:
        # In case there is clock skew, sleep a minute
        wait_for_memex(memexServerName, memexServerPort, memexUser, memexPassword)

    # The documents are already on the memex server box.  We
    # just need exemplars of each one, so we can search for them.
  
    doc_id_array = []
    level0 = 0
    while level0 < 10:
      llname = "lf00%d-%d-%d-%d" % (level0,0,0,0)
      id = locate_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, memexRecordKeyField, "LargeDocs/%s" % llname)
      if id == None:
        raise Exception("Couldn't find %s in repository" % ("LargeDocs/%s"%llname) )
      doc_id_array.append( id )
      level0 += 1


    # PHASE 1: Ingestion 
    print "Load Test."


    ConnectorHelpers.define_repositoryconnection( "MemexConnection",
                                 "Memex Connection",
                                 "com.metacarta.crawler.connectors.memex.MemexConnector",
                                 poolmax=3,
                                 configparams = [ "Memex server name=%s" % memexServerName,
                                                        "Memex server port=%s" % memexServerPort,
                                                        "User ID=%s" % memexUser,
                                                        "Password=%s" % memexPassword,
                                                        "Web server protocol=http",
                                                        "Web server name=",
                                                        "Web server port=",
                                                        "Server time zone=GMT",
                                                        "Character encoding=windows-1252" ] )

    # Define job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><specificationrule virtualserver="%s" entity="%s" description="%s"/><entity name="%s" description="%s"><primaryfield name="%s"/></entity><security value="off"/></specification>' % (memexVirtualServer, memexEntityName, memexEntityDisplayName, memexEntityName, memexEntityDisplayName, memexDataField)
    job_id = ConnectorHelpers.define_job( "Memex test job",
                             "MemexConnection",
                             doc_spec_xml )

    # Run the job
    ConnectorHelpers.start_job( job_id )
    # Memex abort test!  Abort the job and see how long it takes for it to actually stop doing stuff
    # First, wait 1 minute for the job to get really rolling
    time.sleep(60)
    # Now, abort it
    ConnectorHelpers.abort_job( job_id )
    # Wait to see how long it actually takes to abort the job
    the_time = time.time()
    ConnectorHelpers.wait_job_complete( job_id )
    elapsed_time = time.time() - the_time;
    print "It took %f seconds to abort the job" % elapsed_time
    if elapsed_time > 120.0:
        raise Exception( "Took too long for job to abort: %f seconds" % elapsed_time )

    # Now, start it again and run the job to completion this time
    ConnectorHelpers.start_job( job_id )
    # Wait until it's really working, then cycle the service, to be sure it is shutting down cleanly
    time.sleep(120)
    ConnectorHelpers.restart_agents( )
    
    # Now, wait for job to complete
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )
            
    # See if we can find the documents we just ingested
    ConnectorHelpers.search_exists_check( [ "divestment url:http://" + build_memex_url(doc_id_array[0]) ], None, ConnectorHelpers.regexp_encode(build_memex_url( doc_id_array[0] )) )
    ConnectorHelpers.search_exists_check( [ "visitors url:http://" + build_memex_url(doc_id_array[2]) ], None, ConnectorHelpers.regexp_encode(build_memex_url( doc_id_array[2] )) )

    # Success: done
    print "Done load test."

    # PHASE 5: Delete Job

    print "Job Delete Test."
    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    # Make sure the documents all went away
    ConnectorHelpers.search_nonexists_check( [ "divestment url:http://" + build_memex_url(doc_id_array[0]) ], None, ConnectorHelpers.regexp_encode(build_memex_url( doc_id_array[0] )) )
    ConnectorHelpers.search_nonexists_check( [ "visitors url:http://" + build_memex_url(doc_id_array[2]) ], None, ConnectorHelpers.regexp_encode(build_memex_url( doc_id_array[2] )) )

    print "Done Job Delete Test."

    ConnectorHelpers.delete_repositoryconnection( "MemexConnection" )

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    LicenseMakerClient.revoke_license

    teardown_ssl_mode( )
    
    ConnectorHelpers.teardown_connector_environment( )

    print "MemexConnector load tests PASSED"
