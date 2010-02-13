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
import socket
import ConnectorHelpers
import sqatools
from wintools import sqa_domain_info
from sqatools import LicenseMakerClient
import TestDocs

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

def disable_receive_port(server_host, server_port):
    """ Use iptables to shut off the ability of appliance to talk to a port on server """
    ConnectorHelpers.invoke_root_script( ["iptables", "-A", "INPUT", "-s", socket.gethostbyname(server_host), "-p", "tcp", "--sport", str(server_port), "-j", "DROP" ] )

def enable_receive_port(server_host, server_port):
    """ Reverse iptables changes to re-enable appliance to talk to a port on server """
    ConnectorHelpers.invoke_root_script( ["iptables", "-D", "INPUT", "-s", socket.gethostbyname(server_host), "-p", "tcp", "--sport", str(server_port), "-j", "DROP" ] )

# Method to add a document to a jdbc table
def add_document( provider,
                  host,
                  databasename,
                  username,
                  password,
                  tablename,
                  idcolumn,
                  idvalue,
                  urlcolumn,
                  urlvalue,
                  versioncolumn,
                  versionvalue,
                  contentcolumn,
                  contentfilename ):
    """Add a document to a jdbc table"""
    listparams = [ ConnectorHelpers.process_argument(provider),
            ConnectorHelpers.process_argument(host),
            ConnectorHelpers.process_argument(databasename),
            ConnectorHelpers.process_argument(username),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(tablename),
            ConnectorHelpers.process_argument(idcolumn),
            ConnectorHelpers.process_argument(idvalue),
            ConnectorHelpers.process_argument(urlcolumn),
            ConnectorHelpers.process_argument(urlvalue),
            ConnectorHelpers.process_argument(versioncolumn),
            ConnectorHelpers.process_argument(versionvalue),
            ConnectorHelpers.process_argument(contentcolumn),
            ConnectorHelpers.process_argument(contentfilename) ]
    return ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.jdbc.AddDoc", 
                                    argument_list=listparams,
                                    additional_classpath="jdbc-testing-package/metacarta-jdbcconnector-test.jar" )

# Method to remove a document from a jdbc table
def remove_document( provider,
                     host,
                     databasename,
                     username,
                     password,
                     tablename,
                     idcolumn,
                     idvalue ):
    """Remove a document from the repository"""
    listparams = [ ConnectorHelpers.process_argument(provider),
            ConnectorHelpers.process_argument(host),
            ConnectorHelpers.process_argument(databasename),
            ConnectorHelpers.process_argument(username),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(tablename),
            ConnectorHelpers.process_argument(idcolumn),
            ConnectorHelpers.process_argument(idvalue) ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.jdbc.RemoveDoc", 
                                    argument_list=listparams,
                                    additional_classpath="jdbc-testing-package/metacarta-jdbcconnector-test.jar" )

# Method to lookup a document from a jdbc table
def lookup_document( provider,
                     host,
                     databasename,
                     username,
                     password,
                     tablename,
                     idcolumn,
                     idvalue ):
    """Remove a document from the repository"""
    listparams = [ ConnectorHelpers.process_argument(provider),
            ConnectorHelpers.process_argument(host),
            ConnectorHelpers.process_argument(databasename),
            ConnectorHelpers.process_argument(username),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(tablename),
            ConnectorHelpers.process_argument(idcolumn),
            ConnectorHelpers.process_argument(idvalue) ]
    value = ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.jdbc.LookupDoc", 
                                    argument_list=listparams,
                                    additional_classpath="jdbc-testing-package/metacarta-jdbcconnector-test.jar" )

    if value == "null":
        return False
    return True


# Method to update a document in a jdbc table
def version_document( provider,
                      host,
                      databasename,
                      username,
                      password,
                      tablename,
                      idcolumn,
                      idvalue,
                      versioncolumn,
                      versionvalue,
                      contentcolumn,
                      contentfilename ):
    """Create a new version of an existing document"""
    listparams = [ ConnectorHelpers.process_argument(provider),
            ConnectorHelpers.process_argument(host),
            ConnectorHelpers.process_argument(databasename),
            ConnectorHelpers.process_argument(username),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(tablename),
            ConnectorHelpers.process_argument(idcolumn),
            ConnectorHelpers.process_argument(idvalue),
            ConnectorHelpers.process_argument(versioncolumn),
            ConnectorHelpers.process_argument(versionvalue),
            ConnectorHelpers.process_argument(contentcolumn),
            ConnectorHelpers.process_argument(contentfilename) ]

    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.jdbc.VersionDoc", 
                                    argument_list=listparams,
                                    additional_classpath="jdbc-testing-package/metacarta-jdbcconnector-test.jar" )


# Method to wait whatever time is needed after changing jdbc documents
# for them to be noted as changed.
def wait_for_jdbc( provider,
                   host,
                   databasename,
                   username,
                   password ):
    """Nothing needed"""
    pass

# Database type
database_type = None
# Database hostname
database_hostname = None
# Database portnumber
database_port = None
# Database host and port
database_host = None
# Database name
database_name = None
# Database username
database_username = None
# Database password
database_password = None

# Database table
database_table = "largetest"
# ID column
database_idcolumn = "id"
# Version column
database_versioncolumn = "version"
# Data column
database_datacolumn = "data"
# URL column
database_urlcolumn = "url"

# Database urls are currently totally fake, since we don't bother to set up any database service that
# serves documents.
def make_url_protocol( idvalue ):
    return "http://" + make_url( idvalue )

def make_url( idvalue ):
    return database_host+"/"+idvalue

# Versions are based on file timestamps
def make_version( filename_value ):
    # Get the modify date as a string
    return str(os.stat(filename_value).st_mtime)

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

    # Turn on iptables
    try:
        enable_receive_port(database_hostname, database_port)
    except Exception, e:
        if print_errors:
            print "Error enabling receive of database port"
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

    if len(sys.argv) > 1:
        database_mfg = sys.argv[1]
    else:
        database_mfg = "oracle"

    database_username = { "oracle" : "metacarta", "mssql" : "sa" }[database_mfg]
    database_password = { "oracle" : "atracatem", "mssql" : "metacarta" }[database_mfg]
    database_type = { "oracle" : "oracle:thin:@", "mssql" : "jtds:sqlserver:" }[database_mfg]
    database_hostname = { "oracle" : "oraclesrvr.QA-AD-76.METACARTA.COM", "mssql" : "w2k3-shp-76-2.QA-AD-76.METACARTA.COM" }[database_mfg]
    database_port = { "oracle" : 1521, "mssql" : 4686 }[database_mfg]
    database_host = database_hostname + ":%d" % database_port
    database_name = "metacarta"
    
    print "Precleaning!"

    preclean( print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    # Look for the last document
    filesExist = lookup_document( database_type, database_host, database_name, database_username, database_password, database_table,
                                  database_idcolumn, "lf/009/9/9/9" )
    if filesExist == False:
        didntFind = False
        # Presume that we have to set up the server
        print "Initializing test documents."
        # We need at least 10,000 documents.  We'll get this by
        # having 10 documents each with 1000 different ids.
        level0 = 0
        while level0 < 10:
            level1 = 0
            while level1 < 10:
                level2 = 0
                while level2 < 10:
                    level3 = 0
                    while level3 < 10:
                        filename = "/root/largefiles/00%d.htm" % level0
                        id = "lf/00%d/%d/%d/%d" % (level0,level1,level2,level3)
                        if didntFind == False:
                            check_exists = lookup_document( database_type, database_host, database_name, database_username, database_password, database_table,
                                                            database_idcolumn, id )
                            if check_exists == False:
                                didntFind = True
                        else:
                            check_exists = False
                        if check_exists == False:
                            add_document( database_type, database_host, database_name, database_username, database_password, database_table,
                                          database_idcolumn, id, database_urlcolumn, make_url_protocol(id),
                                          database_versioncolumn, make_version(filename), database_datacolumn, filename )
                        level3 += 1
                    level2 += 1
                level1 += 1
            level0 += 1
        print "Done creating test documents on server."

    # The documents are already on the LLserver box, under the LargeDocs folder.  We
    # just need exemplars of each one, so we can search for them.

    # We need at least 10,000 documents.  We'll get this by
    # having 10 documents each with 1000 different names at the same level in the hierarchy.
    doc_id_array = []
    level0 = 0
    while level0 < 10:
        id = "lf/00%d/%d/%d/%d" % (level0,0,0,0)
        doc_id_array.append( id )
        level0 += 1

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["jdbcConnector"], detect_gdms=True)
    
    print "Resetting metacarta-agents so mem size checks are meaningful."
    ConnectorHelpers.restart_agents()

    # Before the test begins, record the memory size(s) and temporary file count
    oldvmem, oldrmem = ConnectorHelpers.calculate_daemon_memory()
    temp_file_footprint = ConnectorHelpers.count_temporary_files("_MC_")

    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # PHASE 1: Ingestion


    print "Load Test."

    # In case there is clock skew, sleep a minute
    wait_for_jdbc( database_type, database_host, database_name, database_username, database_password )

    ConnectorHelpers.define_repositoryconnection( "DatabaseConnection",
                                 "Database Connection",
                                 "com.metacarta.crawler.connectors.jdbc.JDBCConnector",
                                 configparams = [ "JDBC Provider="+database_type,
                                                  "Host="+database_host,
                                                  "Database name="+database_name,
                                                  "User name="+database_username,
                                                  "Password="+database_password ] )

    # Define job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><idquery>SELECT id AS &#34;&#36;(IDCOLUMN)&#34; FROM largetest</idquery><versionquery>SELECT id AS &#34;&#36;(IDCOLUMN)&#34;, version AS &#34;&#36;(VERSIONCOLUMN)&#34; FROM largetest WHERE id IN &#36;(IDLIST)</versionquery><dataquery>SELECT id AS &#34;&#36;(IDCOLUMN)&#34;, url AS &#34;&#36;(URLCOLUMN)&#34;, data AS &#34;&#36;(DATACOLUMN)&#34; FROM largetest WHERE id IN &#36;(IDLIST)</dataquery></specification>'
    job_id = ConnectorHelpers.define_job( "Database load test job",
                             "DatabaseConnection",
                             doc_spec_xml )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    # While the test is running, check to be sure that the connections to database do not grow beyond the
    # 10 that are permitted based on the maximum repository connections parameter
    for check_number in range(10):
        time.sleep(10)
        count_of_connections = ConnectorHelpers.count_lsof_daemon_lines(ConnectorHelpers.regexp_encode(database_host.lower()))
        if count_of_connections > 10:
            raise Exception("Too many database server connections active at one time: %d" % count_of_connections)

    # Wait until it's really working, then cycle the service, to be sure it is shutting down cleanly
    ConnectorHelpers.restart_agents( )

    # Now, temporarily block port access during the test, so we see if the connector properly detects temporary failures

    disable_receive_port(database_hostname, database_port)
    results = ConnectorHelpers.list_job_statuses_api()
    if len(results) != 1:
        raise Exception("Expected 1 job to have a status, instead found %d" % len(results))
    if results[0]["status"] == "done":
        raise Exception("Test run completed before firewall test: test invalid")

    time.sleep(180)
    enable_receive_port(database_hostname, database_port)
    time.sleep(30)

    ConnectorHelpers.wait_job_complete( job_id )
    
    # Get job status to see whether we aborted
    results = ConnectorHelpers.list_job_statuses_api()
    if len(results) != 1:
        raise Exception("Expected 1 job to have a status, instead found %d" % len(results))
    if results[0]["status"] != "done":
        raise Exception("Expected job to be alive after firewall break, but it wasn't!  Status = %s" % results[0]["status"])

    # While we are waiting, make sure our memory footprint is reasonable and we haven't left
    # temp files around
    newvmem, newrmem = ConnectorHelpers.calculate_daemon_memory()
    new_temp_file_footprint = ConnectorHelpers.count_temporary_files("_MC_")
    if temp_file_footprint < new_temp_file_footprint:
        raise Exception("Temporary file count has grown during test")
    # Memory is more tricky to assess.  Resident memory used to climb above 140,000
    # in tests with the file-based leak bug - and it started out at about 50,000.  In JDK 1.5, the pattern
    # is different, and of course it matters if you start fresh or not as well.
    #
    # So, I've been forced to change the strategy for the test.  First, we have to always start from a known state - and that means restarting the daemon
    # before running the test.  Second, jmap will give us an idea of the amount of memory actually used within the heap - and this could be a much more sensitive
    # measure than process memory.
    #
    print "Resident memory grew from %d->%d" % (oldrmem,newrmem)
    print "Virtual memory grew from %d->%d" % (oldvmem,newvmem)

    if newrmem > { "oracle" : 190000, "mssql" : 250000 }[database_mfg]:
        raise Exception("Too large an increase in resident memory during test: %d->%d" % (oldrmem,newrmem))
    if newvmem > { "oracle" : 1130000, "mssql" : 1240000}[database_mfg]:
        raise Exception("Too large an increase in virtual memory during test: %d->%d" % (oldvmem,newvmem))

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_exists_check( [ "divestment url:" + make_url_protocol( doc_id_array[0]) ], None, ConnectorHelpers.regexp_encode(make_url( doc_id_array[0] )) )
    ConnectorHelpers.search_exists_check( [ "visitors url:" + make_url_protocol(doc_id_array[2]) ], None, ConnectorHelpers.regexp_encode(make_url( doc_id_array[2] )) )

    # Success: done
    print "Done load test."

    # PHASE 5: Delete Job

    print "Job Delete Test."
    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    # Make sure the documents all went away
    ConnectorHelpers.search_nonexists_check( [ "divestment url:" + make_url_protocol(doc_id_array[0]) ], None, ConnectorHelpers.regexp_encode(make_url( doc_id_array[0] )) )
    ConnectorHelpers.search_nonexists_check( [ "visitors url:" + make_url_protocol(doc_id_array[2]) ], None, ConnectorHelpers.regexp_encode(make_url( doc_id_array[2] )) )

    print "Done Job Delete Test."

    ConnectorHelpers.delete_repositoryconnection( "DatabaseConnection" )

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "JDBCConnector load tests PASSED"
