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
import ConnectorHelpers
import sqatools
from wintools import sqa_domain_info
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

# Create a jdbc repository connection via the UI
def define_jdbc_repository_connection_ui( username,
                                          password,
                                          connection_name,
                                          connection_description,
                                          jdbc_provider,
                                          jdbc_host,
                                          jdbc_databasename,
                                          jdbc_username,
                                          jdbc_password ) :

    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for repository connection management and click it
    window = vb.find_window("")
    link = window.find_link("List repository connections")
    link.click( )

    # Click "add a connection"
    window = vb.find_window("")
    link = window.find_link("Add a connection")
    link.click( )

    # Find the right form elements and set them
    window = vb.find_window("")
    form = window.find_form("editconnection")

    # "Name" tab
    namefield = form.find_textarea("connname")
    descriptionfield = form.find_textarea("description")
    namefield.set_value( connection_name )
    descriptionfield.set_value( connection_description )

    # "Type" tab
    link = window.find_link("Type tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    connectortypefield = form.find_selectbox("classname")
    connectortypefield.select_value( "com.metacarta.crawler.connectors.jdbc.JDBCConnector" )
    # Click the "Continue" button
    continue_button = window.find_button("Continue to next page")
    continue_button.click( )
    window = vb.find_window("")

    # "Database Type" tab
    link = window.find_link("Database Type tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    form.find_selectbox("databasetype").select_value( jdbc_provider )

    # "Server" tab
    link = window.find_link("Server tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    form.find_textarea("databasehost").set_value( jdbc_host )
    form.find_textarea("databasename").set_value( jdbc_databasename )

    # "Credentials" tab
    link = window.find_link("Credentials tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    form.find_textarea("username").set_value( jdbc_username )
    form.find_textarea("password").set_value( jdbc_password )

    # Now, save this page
    save_button = window.find_button("Save this connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->",1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")

# Define a standard jdbc job using the UI
def define_jdbc_job_ui( username,
                password,
                job_name,
                connection_name,
                id_query,
                version_query,
                data_query,
                access_tokens=None,
                collection_name=None,
                type="specified",
                startmethod="windowbegin",
                recrawlinterval=0 ):
    """connection_name is the name of the jdbc connection.  id_query, version_query,
       and data_query are the queries to use for jdbc document fetching.
       Legal values for type are: "specified" or "continuous"
       Legal values for start method are: "windowbegin", "windowinside", or "disable".
    """
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for job management and click it
    window = vb.find_window("")
    link = window.find_link("List jobs")
    link.click( )

    # Grab the new window
    window = vb.find_window("")
    # Add a job
    link = window.find_link("Add a job")
    link.click( )

    # Grab the edit window
    window = vb.find_window("")
    # Start setting stuff in the form
    form = window.find_form("editjob")

    # "Name" tab
    # textarea for setting description
    form.find_textarea("description").set_value( job_name )

    # "Connection" tab
    link = window.find_link("Connection tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # start method
    if startmethod == "windowbegin":
        startmethod_value = 0
    elif startmethod == "windowinside":
        startmethod_value = 1
    elif startmethod == "disable":
        startmethod_value = 2
    else:
        raise Exception("Illegal start method value: '%s'" % startmethod )
    form.find_selectbox("startmethod").select_value( str(startmethod_value) )
    # connection name
    form.find_selectbox("connectionname").select_value( connection_name )
    form.find_selectbox("outputname").select_value( "GTS" )
    # Click the "Continue" button
    window.find_button("Continue to next screen").click( )
    window = vb.find_window("")
    form = window.find_form("editjob")

    # "Collections" tab
    link = window.find_link("Collections tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # textarea for setting collection
    if collection_name != None:
        form.find_textarea("gts_collectionname").set_value( collection_name )

    # "Scheduling" tab
    link = window.find_link("Scheduling tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # type
    if type == "specified":
        type_value = 1
    elif type == "continuous":
        type_value = 0
    else:
        raise Exception("Illegal type value: '%s'" % type )
    form.find_selectbox("scheduletype").select_value( str(type_value) )
    # Recrawl interval
    if type == "continuous":
        form.find_textarea("recrawlinterval").set_value( str(recrawlinterval * 1000 * 60) )

    # "Queries" tab
    link = window.find_link("Queries tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # Now, set up queries
    form.find_textarea("idquery").set_value( id_query )
    form.find_textarea("versionquery").set_value( version_query )
    form.find_textarea("dataquery").set_value( data_query )

    # "Security" tab
    link = window.find_link("Security tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    if access_tokens != None:
        for access_token in access_tokens:
            form.find_textarea("spectoken").set_value(access_token)
            window.find_button("Add access token").click()
            window = vb.find_window("")
            form = window.find_form("editjob")

    # Finally, submit the form
    window.find_button("Save this job").click( )
    window = vb.find_window("")
    jobid = window.find_match("<!--jobid=(.*)-->",1)
    return jobid


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
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.jdbc.RemoveDoc", argument_list=listparams, additional_classpath="jdbc-testing-package/metacarta-jdbcconnector-test.jar" )

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

    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.jdbc.UpdateDoc", argument_list=listparams, additional_classpath="jdbc-testing-package/metacarta-jdbcconnector-test.jar" )


# Method to wait whatever time is needed after changing jdbc documents
# for them to be noted as changed.
def wait_for_jdbc( provider,
                   host,
                   databasename,
                   username,
                   password ):
    """Nothing needed"""
    pass

# Database mfg
database_mfg = None
# Database type
database_type = None
# Database host
database_host = None
# Database name
database_name = None
# Database username
database_username = None
# Database password
database_password = None

# Database table
database_table = "testdocuments"
# ID column
database_idcolumn = "id"
# Version column
database_versioncolumn = "version"
# Data column
database_datacolumn = "data"
# URL column
database_urlcolumn = "url"

# Database urls are currently totally fake, since we don't bother to set up any Oracle service that
# serves documents.
def make_url_protocol( idvalue ):
    return "http://" + make_url( idvalue )

def make_url( idvalue ):
    return database_host+"/"+idvalue

# Versions are based on file timestamps
def make_version( filename_value ):
    # Get the modify date as a string
    return str(os.stat(filename_value).st_mtime)

# The filenames we will use
id1 = "testfiles/f001"
id2 = "testfiles/f002"
id3 = "testfiles/f003"
id4 = "testfiles/f004"
id5 = "testfiles/f005"
id6 = "testfiles/f006"
id7 = "testfiles/f007"
id8 = "testfiles/newfolder/f008"
id9 =  "testfiles/f009"
id10 =  "testfiles/f010"

# Crawl user credentials
username = "testingest"
password = "testingest"

def preclean( ad_domain_info, perform_legacy_pass, print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

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
    # Clean up the documents we dumped into the folders on Oracle server
    for docid in [id1,id2,id3,id4,id5,id6,id7,id8,id9,id10]:
        try:
            remove_document(database_type, database_host, database_name, database_username, database_password,
                        database_table, database_idcolumn, docid )
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % docid
                print e

    # Delete users
    for user in ["usera","userb","userc"]:
        try:
            ConnectorHelpers.delete_ad_user(ad_domain_info, user)
        except Exception, e:
            if print_errors:
                print "Error deleting user %s" % user
                print e

    # Disable ad
    try:
        ConnectorHelpers.turn_off_ad( ad_domain_info )
    except Exception, e:
        if print_errors:
            print "Error disabling AD"
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

    if perform_legacy_pass:
        try:
            ConnectorHelpers.select_legacy_mode(use_legacy_tools=False)
        except Exception, e:
            if print_errors:
                print "Error turning off legacy AD mode"
                print e

def run_ad_test_part( ad_domain_info ):
    """ Do the part of the test that requires joining to the ad domain, so we can try it in both normal and legacy modes. """

    ad_win_host = ad_domain_info.ie_client_fqdn

    # Join AD
    ConnectorHelpers.configure_ad( ad_domain_info )

    # Create three specific AD users
    for ad_username,ad_password in [ ("usera","usera"), ("userb","userb"), ("userc","userc") ]:
        ConnectorHelpers.add_ad_user( ad_domain_info, ad_username, ad_password )

    # Obtain the SID for the first user
    usera_sid = ConnectorHelpers.get_ad_user_sid("usera")

    # Create a job with this sid as an access token
    job_id = define_jdbc_job_ui( username,
                        password,
                        "Database test job",
                        "DatabaseConnection",
                        "SELECT id AS \"$(IDCOLUMN)\" FROM testdocuments",
                        "SELECT id AS \"$(IDCOLUMN)\", version AS \"$(VERSIONCOLUMN)\" FROM testdocuments WHERE id IN $(IDLIST)",
                        "SELECT id AS \"$(IDCOLUMN)\", url AS \"$(URLCOLUMN)\", data AS \"$(DATACOLUMN)\" FROM testdocuments WHERE id IN $(IDLIST)",
                        access_tokens = [ usera_sid ] )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Now, attempt to search.  Searching as usera should find the documents, while searching as userb or userc should not.
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_url(id1) ], username="usera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "reference" ], None, [ ], username="userb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "reference" ], None, [ ], username="userc", password="userc", win_host=ad_win_host )

    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    # Delete AD users
    for user in ["usera","userb","userc"]:
        ConnectorHelpers.delete_ad_user(ad_domain_info, user)

    # Disable ad
    ConnectorHelpers.turn_off_ad( ad_domain_info )

# Main
if __name__ == '__main__':

    # AD parameters
    ad_group = "76"
    if len(sys.argv) > 1:
        ad_group = sys.argv[1]
    if len(sys.argv) > 2:
        database_mfg = sys.argv[2]
    else:
        database_mfg = "oracle"
    perform_legacy_pass = False
    if len(sys.argv) > 3 and sys.argv[3] == "legacy":
        perform_legacy_pass = True

    ad_domain_info = sqa_domain_info.SQADomainInfo( ad_group )

    database_username = { "oracle" : "metacarta", "mssql" : "sa" }[database_mfg]
    database_password = { "oracle" : "atracatem", "mssql" : "metacarta" }[database_mfg]
    database_type = { "oracle" : "oracle:thin:@", "mssql" : "jtds:sqlserver:" }[database_mfg]
    database_host = None
    if database_mfg == "oracle":
        database_host = getattr(ad_domain_info,"oracle_server_fqdn") + ":1521"
    elif database_mfg == "mssql":
        database_host = getattr(ad_domain_info,"mssql_server_fqdn") + ":4686"

    # This is a temporary hack, until I can figure out a way to map
    # servers to database names.
    database_name = { "89": "meta8911", "76": "metacarta"}[ad_group]
    
    print "Precleaning!"

    preclean( ad_domain_info, perform_legacy_pass, print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up file area."
    copy_folder("/root/testfiles","/root/crawlarea")

    # Enable CF security
    ConnectorHelpers.enable_connector_framework( )

    print "Initializing test documents."
    time.sleep(60)
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
    sqatools.appliance.install_license(extra_services=["jdbcConnector"], detect_gdms=True)


    # Set up the ingestion user.

    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # PHASE 0: Make sure connection pooling is working properly

    print "Connection pooling test."

    # Restart tomcat, to be sure we have no connection pooling already going on
    ConnectorHelpers.restart_tomcat()
    time.sleep(60)

    # Define repository connection
    define_jdbc_repository_connection_ui( username,
                                        password,
                                        "DatabaseConnection",
                                        "Database Connection",
                                        database_type,
                                        database_host,
                                        database_name,
                                        database_username,
                                        "bad_password" )

    # View the connection; there should be an error
    try:
        ConnectorHelpers.view_repository_connection_ui( username, password, "DatabaseConnection" )
        # No error!  Bad news...
        raise Exception("Bad connection password did not cause a connection error!")
    except Exception, e:
        print "Bad connection reported as expected."

    # Delete the connection
    ConnectorHelpers.delete_repository_connection_ui( username, password, "DatabaseConnection" )

    # The rest of this test occurs after we create the connection with the CORRECT parameters.  We then expect the
    # feedback in the UI to be "Connection working".

    # PHASE 1: Ingestion


    print "Ingestion Test."

    # Add some docs to the repository

    add_document(database_type, database_host, database_name, database_username, database_password,
                 database_table, database_idcolumn, id1, database_urlcolumn, make_url_protocol(id1),
                 database_versioncolumn, make_version("/root/crawlarea/testfiles/f001.txt"), database_datacolumn, "/root/crawlarea/testfiles/f001.txt" )
    add_document(database_type, database_host, database_name, database_username, database_password,
                 database_table, database_idcolumn, id2, database_urlcolumn, make_url_protocol(id2),
                 database_versioncolumn, make_version("/root/crawlarea/testfiles/f002.txt"), database_datacolumn, "/root/crawlarea/testfiles/f002.txt" )
    add_document(database_type, database_host, database_name, database_username, database_password,
                 database_table, database_idcolumn, id3, database_urlcolumn, make_url_protocol(id3),
                 database_versioncolumn, make_version("/root/crawlarea/testfiles/f003.txt"), database_datacolumn, "/root/crawlarea/testfiles/f003.txt" )
    add_document(database_type, database_host, database_name, database_username, database_password,
                 database_table, database_idcolumn, id4, database_urlcolumn, make_url_protocol(id4),
                 database_versioncolumn, make_version("/root/crawlarea/testfiles/f004.txt"), database_datacolumn, "/root/crawlarea/testfiles/f004.txt" )
    add_document(database_type, database_host, database_name, database_username, database_password,
                 database_table, database_idcolumn, id5, database_urlcolumn, make_url_protocol(id5),
                 database_versioncolumn, make_version("/root/crawlarea/testfiles/f005.txt"), database_datacolumn, "/root/crawlarea/testfiles/f005.txt" )
    add_document(database_type, database_host, database_name, database_username, database_password,
                 database_table, database_idcolumn, id6, database_urlcolumn, make_url_protocol(id6),
                 database_versioncolumn, make_version("/root/crawlarea/testfiles/f006.txt"), database_datacolumn, "/root/crawlarea/testfiles/f006.txt")
    add_document(database_type, database_host, database_name, database_username, database_password,
                 database_table, database_idcolumn, id7, database_urlcolumn, make_url_protocol(id7),
                 database_versioncolumn, make_version("/root/crawlarea/testfiles/f007.txt"), database_datacolumn, "/root/crawlarea/testfiles/f007.txt")
    add_document(database_type, database_host, database_name, database_username, database_password,
                 database_table, database_idcolumn, id8, database_urlcolumn, make_url_protocol(id8),
                 database_versioncolumn, make_version("/root/crawlarea/testfiles/newfolder/f008.txt"), database_datacolumn, "/root/crawlarea/testfiles/newfolder/f008.txt")

    # In case there is clock skew, sleep a minute
    wait_for_jdbc(database_type, database_host, database_name, database_username, database_password)

    # Define repository connection
    define_jdbc_repository_connection_ui( username,
                                        password,
                                        "DatabaseConnection",
                                        "Database Connection",
                                        database_type,
                                        database_host,
                                        database_name,
                                        database_username,
                                        database_password )

    # Make sure the connection is working.
    ConnectorHelpers.view_repository_connection_ui( username, password, "DatabaseConnection" )

    # Define job
    job_id = define_jdbc_job_ui( username,
                        password,
                        "Database test job",
                        "DatabaseConnection",
                        "SELECT id AS \"$(IDCOLUMN)\" FROM testdocuments",
                        "SELECT id AS \"$(IDCOLUMN)\", version AS \"$(VERSIONCOLUMN)\" FROM testdocuments WHERE id IN $(IDLIST)",
                        "SELECT id AS \"$(IDCOLUMN)\", url AS \"$(URLCOLUMN)\", data AS \"$(DATACOLUMN)\" FROM testdocuments WHERE id IN $(IDLIST)" )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_url(id1) ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_url(id2) ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_url(id3) ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_url(id4) ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_url(id5) ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_url(id6) ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_url(id7) ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ make_url(id8) ] )

    # Success: done
    print "Done ingestion test."


    # PHASE 2: Document Change Detection

    print "Document Change Test."
    # Modify the documents

    version_document(database_type, database_host, database_name, database_username, database_password,
                     database_table, database_idcolumn, id2,
                     database_versioncolumn, make_version("/root/crawlarea/testfiles/f002a.txt"), database_datacolumn, "/root/crawlarea/testfiles/f002a.txt")
    version_document(database_type, database_host, database_name, database_username, database_password,
                     database_table, database_idcolumn, id4,
                     database_versioncolumn, make_version("/root/crawlarea/testfiles/f004a.txt"), database_datacolumn, "/root/crawlarea/testfiles/f004a.txt")
    # Sleep, in case there's clock skew
    wait_for_jdbc(database_type, database_host, database_name, database_username, database_password)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Look for state of index being right
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_url(id1) ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_url(id3) ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_url(id5) ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_url(id6) ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_url(id7) ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ make_url(id8) ] )
    ConnectorHelpers.search_check( [ "platypus" ], None, [ make_url(id2) ] )
    ConnectorHelpers.search_check( [ "establishments" ], None, [ make_url(id4) ] )

    print "Done Document Change Test."

    # PHASE 3: Document Delete Detection

    print "Document Delete Test."
    remove_document(database_type, database_host, database_name, database_username, database_password,
                    database_table, database_idcolumn, id3 )
    remove_document(database_type, database_host, database_name, database_username, database_password,
                    database_table, database_idcolumn, id5 )
    # Sleep, in case of clock skew
    wait_for_jdbc(database_type, database_host, database_name, database_username, database_password)


    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ ] )
    print "Done Document Delete Test."

    # PHASE 4: Document Addition Detection

    print "Document Add Test."
    add_document(database_type, database_host, database_name, database_username, database_password,
                 database_table, database_idcolumn, id9, database_urlcolumn, make_url_protocol(id9),
                 database_versioncolumn, make_version("/root/crawlarea/testfiles/f009.txt"), database_datacolumn, "/root/crawlarea/testfiles/f009.txt")
    add_document(database_type, database_host, database_name, database_username, database_password,
                 database_table, database_idcolumn, id10, database_urlcolumn, make_url_protocol(id10),
                 database_versioncolumn, make_version("/root/crawlarea/testfiles/f010.txt"), database_datacolumn, "/root/crawlarea/testfiles/f010.txt")
    wait_for_jdbc(database_type, database_host, database_name, database_username, database_password)


    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    ConnectorHelpers.search_check( [ "albemarle" ], None, [ make_url(id9) ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [ make_url(id10) ] )
    print "Done Document Add Test."

    # PHASE 4.5: Check to see that the "external query" activities are recorded
    report_result = ConnectorHelpers.run_simple_history_report_api( "DatabaseConnection", ["external query"] )
    expected_results = 12
    if len(report_result) != expected_results:
        raise Exception("Wrong number of external query results; expected %d, saw %d" % (expected_results,len(report_result)))

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


    # PHASE 6: Check security

    print "Security test"

    # The ad part of this test may need to run in both legacy and non-legacy modes
    run_ad_test_part( ad_domain_info )
    if perform_legacy_pass:
        # Set up legacy mode
        ConnectorHelpers.select_legacy_mode()
        # Run the ad part of the test again
        run_ad_test_part( ad_domain_info )
        # Cancel legacy mode
        ConnectorHelpers.cancel_legacy_mode()

    print "Done security test"

    ConnectorHelpers.delete_repository_connection_ui( username, password, "DatabaseConnection" )

    # Clean up the documents we dumped into the folders on Oracle
    for docid in [ id1,id2,id3,id4,id5,id6,id7,id8,id9,id10 ]:
        remove_document(database_type, database_host, database_name, database_username, database_password,
                        database_table, database_idcolumn, docid )

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    # Clean up temporary folder
    delete_folder("/root/crawlarea")

    LicenseMakerClient.revoke_license

    ConnectorHelpers.teardown_connector_environment( )

    print "Basic JDBCConnector tests PASSED"
