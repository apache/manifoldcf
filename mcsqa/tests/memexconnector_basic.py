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
import VirtualBrowser

# Set up SSL mode
def setup_ssl_mode( file_name ):
    ConnectorHelpers.invoke_root_script( [ "/usr/bin/memexconnector_control", "installcert", file_name ] )

# Tear down SSL mode
def teardown_ssl_mode( ):
    ConnectorHelpers.invoke_root_script( [ "/usr/bin/memexconnector_control", "removecert" ] )

# Copy a folder to a (new) area
def copy_folder( source, target ):
    ConnectorHelpers.invoke_root_script( [ "mkdir", "-p", target ] )
    ConnectorHelpers.invoke_root_script( [ "cp", "-r", source, target ] )

# Remove a folder
def delete_folder( target ):
    ConnectorHelpers.invoke_root_script( [ "rm", "-rf", target ] )

# Run a remote memex command
def remote_memex_command( command_string, host_name ):
    """ Run a remote memex command """
    ConnectorHelpers.invoke_root_script(["chmod", "600", "memex_keyfile"])
    ConnectorHelpers.invoke_root_script(["ssh", "-o", "CheckHostIP=no",
                                                "-o", "StrictHostKeyChecking=no",
                                                "-o", "UserKnownHostsFile=/dev/null",
                                                "-o", "PasswordAuthentication=no",
                                                "-i", "memex_keyfile",
                                                "-F", "/dev/null",
                                                "mxadmin@%s" % host_name,
                                                command_string] ) 

# Restore memex audit history
def restore_memex_server(host_name):
    """ Restore audit history for memex server. """
    remote_memex_command("set -x; if [ -f audit_backup.tar ] ; then rm -rf /opt/memex/im/CS/mxAudit001/* ; ( cd /opt/memex/im/CS/mxAudit001 ; tar xf ~/audit_backup.tar ) ; rm audit_backup.tar ; fi", host_name)

# Save memex audit history for this test run
def snapshot_memex_server(host_name):
    """ Create a snapshot of the memex server audit history. """
    remote_memex_command("( cd /opt/memex/im/CS/mxAudit001 ; tar cf ~/audit_backup.tar . )", host_name)

# Create a memex repository connection via the UI
def define_memex_authority_connection_ui( username,
                                        password,
                                        connection_name,
                                        connection_description,
                                        server_name,
                                        server_port,
                                        mmxuser_name,
                                        mmxuser_password,
                                        usernameregexp=None,
                                        memexuserexpr=None,
                                        max_connections=2 ):
    # Set up virtual browser instance
    vb = VirtualBrowser.VirtualBrowser( username=username, password=password )

    # First, go to main page
    vb.load_main_window( "http://localhost/crawler/index.jsp" )

    # Find the link for repository connection management and click it
    window = vb.find_window("")
    link = window.find_link("List authorities")
    link.click( )

    # Click "add a connection"
    window = vb.find_window("")
    link = window.find_link("Add new connection")
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
    connectortypefield.select_value( "com.metacarta.crawler.connectors.memex.MemexAuthority" )
    # Click the "Continue" button
    continue_button = window.find_button("Continue to next page")
    continue_button.click( )

    # "Throttling" tab
    window = vb.find_window("")
    link = window.find_link("Throttling tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    if max_connections != None:
        form.find_textarea("maxconnections").set_value( str(max_connections) )

    # "Memex Server" tab
    window = vb.find_window("")
    link = window.find_link("Memex Server tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    # Set memex-specific stuff
    form.find_textarea("memexservername").set_value( server_name )
    form.find_textarea("memexserverport").set_value( server_port )
    form.find_textarea("crawluser").set_value( mmxuser_name )
    form.find_textarea("crawluserpassword").set_value( mmxuser_password )

    # "User Mapping" tab
    window = vb.find_window("")
    link = window.find_link("User Mapping tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    if usernameregexp != None:
        form.find_textarea("usernameregexp").set_value(usernameregexp)
    if memexuserexpr != None:
        form.find_textarea("memexuserexpr").set_value(memexuserexpr)

    # Now, save this page
    save_button = window.find_button("Save this authority connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->", 1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")

# Create a memex repository connection via the UI
def define_memex_repository_connection_ui( username,
                                        password,
                                        connection_name,
                                        connection_description,
                                        server_name,
                                        server_port,
                                        mmxuser_name,
                                        mmxuser_password,
                                        authority_name=None,
                                        view_protocol=None,
                                        view_server=None,
                                        view_port=None,
                                        max_connections=2 ):
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
    connectortypefield.select_value( "com.metacarta.crawler.connectors.memex.MemexConnector" )
    if authority_name != None:
        form.find_selectbox("authorityname").select_value( authority_name )
    # Click the "Continue" button
    continue_button = window.find_button("Continue to next page")
    continue_button.click( )

    # "Throttling" tab
    window = vb.find_window("")
    link = window.find_link("Throttling tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    if max_connections != None:
        form.find_textarea("maxconnections").set_value( str(max_connections) )

    # "Memex Server" tab
    window = vb.find_window("")
    link = window.find_link("Memex Server tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    # Set memex-specific stuff
    form.find_textarea("memexservername").set_value( server_name )
    form.find_textarea("memexserverport").set_value( server_port )
    form.find_textarea("crawluser").set_value( mmxuser_name )
    form.find_textarea("crawluserpassword").set_value( mmxuser_password )

    # "Web Server" tab
    link = window.find_link("Web Server tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editconnection")
    if view_protocol != None:
        form.find_selectbox("webserverprotocol").select_value( view_protocol )
    if view_port != None:
        form.find_textarea("webserverport").set_value( str(view_port) )
    if view_server != None:
        form.find_textarea("webservername").set_value( view_server )

    # Now, save this page
    save_button = window.find_button("Save this connection")
    save_button.click( )

    # See if the connector saved
    window = vb.find_window("")
    found_connection_name = window.find_match("<!--connection=(.*)-->", 1)
    if found_connection_name != connection_name:
        raise Exception("Created connection doesn't match")

# Define a standard livelink job using the UI
def define_memex_job_ui( username,
                password,
                job_name,
                connection_name,
                rules,
                entities,
                security_enabled=False,
                collection_name=None,
                type="specified",
                startmethod="windowbegin",
                recrawlinterval=0 ):
    """connection_name is the name of the memex connection.
       rules is an array of tuples of (virtual_server_name, entity_prefix:entity_description, field_name, operation, field_value), where
         all values may be None.
       entities is an array of tuples of (entity_name, primary_field_list, metadata_field_list), where
         primary_field_list is an array of field names, and metadata_field_list also an array
         of field names.
       security_enabled is True if security should be enabled.
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

    # "Record Criteria" tab
    link = window.find_link("Record Criteria tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # Loop through all virtual server specs
    for vs_name, entity, field_name, operation, field_value in rules:
        # Build a rule
        if vs_name != None:
            form.find_selectbox("rulevirtualserverselect").select_value( vs_name )
            window.find_button("Set virtual server in rule").click()
            window = vb.find_window("")
            form = window.find_form("editjob")
            if entity != None:
                form.find_selectbox("ruleentityselect").select_value( entity )
                window.find_button("Set entity in rule").click()
                window = vb.find_window("")
                form = window.find_form("editjob")
                if field_name != None:
                    form.find_selectbox("rulefieldnameselect").select_value( field_name )
                    form.find_selectbox("ruleoperationselect").select_value( operation )
                    form.find_textarea("rulefieldvalueselect").set_value( str(field_value) )
                    window.find_button("Set criteria in rule").click()
        window.find_button("Add rule").click()
        window = vb.find_window("")
        form = window.find_form("editjob")

    # "Entities" tab
    link = window.find_link("Entities tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # Set up includes/excludes
    for entity_name, primary_fields, metadata_fields in entities:
        # We have to locate the correct entity index in the UI table.  The best way to do this is using javascript.
        total_entity_count = int(form.execute_javascript_expression("javascript:editjob.entitytypecount.value").num_value())
        entity_index = None
        for this_index in range(total_entity_count):
            this_entity_type = form.execute_javascript_expression("javascript:editjob.entitytype_%d.value" % this_index).str_value()
            if this_entity_type == entity_name:
                entity_index = this_index
                break
                                
        if entity_index == None:
            raise Exception("Could not find entity %s in displayed entity list!")
                
        # Located the right form elements!
        # Go through the primary fields, selecting each such field in order, and moving it to the primary field box
        for primary_field in primary_fields:
            form.find_selectbox("availablefields_%d" % entity_index).multi_select_value(primary_field)
            # Click the "add to primary fields" button
            window.find_button("Add %d to tagged fields" % entity_index).click()
                
        # Go through the metadata fields, selecting all specified fields, and then we'll click the button at the end
        for metadata_field in metadata_fields:
            form.find_selectbox("availablefields_%d" % entity_index).multi_select_value(metadata_field)
                
        window.find_button("Move %d to metadata fields" % entity_index).click()
        
    # "Security" tab
    link = window.find_link("Security tab")
    link.click()
    window = vb.find_window("")
    form = window.find_form("editjob")
    # Set up security
    if security_enabled:
        form.find_radiobutton("specsecurity","on").select()
    else:
        form.find_radiobutton("specsecurity","off").select()

    # Finally, submit the form
    window.find_button("Save this job").click( )
    window = vb.find_window("")
    jobid = window.find_match("<!--jobid=(.*)-->", 1)
    return jobid

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
        
# Method to remove a record from the memex repository
def remove_record(servername, port, user, password, identifier):
    """Remove a record from the repository"""
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.memex.RemoveRecord", 
                                    argument_list=[servername,port,user,password,identifier],
                                    additional_classpath="memex-testing-package/metacarta-memexconnector-test.jar" )

# Method to update a record in the memex repository
def version_record(servername, port, user, password, identifier, field_dict):
    """Create a new version of an existing record"""
    fields = []
    for field in field_dict.keys():
        value = field_dict[field]
        fields = fields + [ field, value ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.memex.ModifyRecord", 
                                    argument_list=[servername,port,user,password,identifier] + fields,
                                    additional_classpath="memex-testing-package/metacarta-memexconnector-test.jar" )

def add_covert_record_rights(servername, port, user, password, identifier, group_or_user_list):
    """Adds the right to see & view contents of the specified record, for the given username"""
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.memex.SetRecordSecurity", 
                                    argument_list=[servername,port,user,password,identifier,str(len(group_or_user_list)),"0",""] + group_or_user_list,
                                    additional_classpath="memex-testing-package/metacarta-memexconnector-test.jar" )

def add_protected_record_rights(servername, port, user, password, identifier, group_or_user_list):
    """Adds the right to see & view contents of the specified record, for the given username"""
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.memex.SetRecordSecurity", 
                                    argument_list=[servername,port,user,password,identifier,"0",str(len(group_or_user_list)),"Protected!"] + group_or_user_list,
                                    additional_classpath="memex-testing-package/metacarta-memexconnector-test.jar" )

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
memexVirtualServer = "Professional Standards"
# Memex entity name
memexEntityName = "PS"
memexEntityDisplayName = "PSDNom"
# Memex key field name
memexRecordKeyField = "surname"
# Memex data field name
memexDataField = "text"

def build_memex_url( id, port=None, server=None ):
    """Build the url from pieces"""
    server_part = server
    if server_part == None:
        server_part = memexServerName
    if port != None:
        server_part = "%s:%s" % (server_part, port)
    return "%s/search.jsp?urn=%s" % (server_part, id)

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

    # Due to license issues, we have to shut down tomcat and metacarta-agents while we clean up these docs
    ConnectorHelpers.shutdown_tomcat( )
    ConnectorHelpers.shutdown_agents( )
    
    # Remove test documents first
    for docname in [ "TestDocs/f001", "TestDocs/f002", "TestDocs/f003", "TestDocs/f004", "TestDocs/f005",
                     "TestDocs/f006", "TestDocs/f007", "TestDocs/f007", "TestDocs/newfolder/f008",
                     "TestDocs/f009", "TestDocs/f010",
                     "TestDocs/userafolder/f003", "TestDocs/userafolder/f004",
                     "TestDocs/userbfolder/f005", "TestDocs/userbfolder/f006", "TestDocs/usercfolder/f007" ]:
        try:
            while True:
                id = locate_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName,
                        memexRecordKeyField, docname)
                if id != None:
                    remove_record(memexServerName, memexServerPort, memexUser, memexPassword, id )
                else:
                    break
        except Exception, e:
            if print_errors:
                print "Error deleting test record %s" % docname
                print e

    ConnectorHelpers.start_agents( )
    ConnectorHelpers.start_tomcat( )
    
    # Fix up memex server
    restore_memex_server(memexServerName)

    # Turn off memex client SSL
    try:
        teardown_ssl_mode( )
    except Exception, e:
            if print_errors:
                print "Error tearing down SSL mode"
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
    """ Perform the ad part of the test.  This may be called twice, if legacy mode testing is requested """

    ad_win_host = ad_domain_info.ie_client_fqdn

    # PHASE 7: Security test with AD and ACLs
    # This particular test involves putting documents into Livelink and setting up specific user rights.
    # In addition to creating a job, an authority connection must also be created.
    # Then, the search is done by specific AD users, which should return documents that have only the
    # right permissions.

    # Now, set up ad with the following users:
    # p_memexusera
    # p_memexuserb
    # p_memexuserc
    # These correspond to users already present in the livelink instance, except for the "p_livelink" prefix.

    # Set up system to use ad
    ConnectorHelpers.configure_ad( ad_domain_info )

    # Enable CF security again
    ConnectorHelpers.enable_connector_framework( )
    
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_memexusera", "usera" )
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_memexuserb", "userb" )
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_memexuserc", "userc" )

    # Add the current set of docs to the repository, and twiddle their security
    id1 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/f001", memexDataField:load_file("/root/crawlarea/testfiles/f001.txt")})
    id2 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/f002", memexDataField:load_file("/root/crawlarea/testfiles/f002.txt")})
    # TestDocs.userafolder will be visible by llusera only
    id3 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/userafolder/f003", memexDataField:load_file("/root/crawlarea/testfiles/f003.txt")})
    add_covert_record_rights( memexServerName, memexServerPort, memexUser, memexPassword, id3, [memexUser, "usera"] )
    id4 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/userafolder/f004", memexDataField:load_file("/root/crawlarea/testfiles/f004.txt")})
    add_protected_record_rights( memexServerName, memexServerPort, memexUser, memexPassword, id4, [memexUser, "usera"] )
    # TestDocs.userbfolder will be visible by userb only
    id5 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/userbfolder/f005", memexDataField:load_file("/root/crawlarea/testfiles/f005.txt")})
    add_covert_record_rights( memexServerName, memexServerPort, memexUser, memexPassword, id5, [memexUser, "userb"] )
    id6 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/userbfolder/f006", memexDataField:load_file("/root/crawlarea/testfiles/f006.txt")})
    add_protected_record_rights( memexServerName, memexServerPort, memexUser, memexPassword, id6, [memexUser, "userb"] )
    # TestDocs.usercfolder will be visible by userc only
    id7 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/usercfolder/f007", memexDataField:load_file("/root/crawlarea/testfiles/f007.txt")})
    add_covert_record_rights( memexServerName, memexServerPort, memexUser, memexPassword, id7, [memexUser, "userc"] )
    # TestDocs.newfolder will be visible by both llusera and userb, but not userc
    id8 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/newfolder/f008", memexDataField:load_file("/root/crawlarea/testfiles/newfolder/f008.txt")})
    add_covert_record_rights( memexServerName, memexServerPort, memexUser, memexPassword, id8, [ memexUser, "usera", "userb" ] )

    # Define authority connection
    define_memex_authority_connection_ui( username,
                                        password,
                                        "MemexAuthority",
                                        "Memex Authority",
                                        memexServerName,
                                        memexServerPort,
                                        memexUser,
                                        memexPassword,
                                        usernameregexp="^p_memex([^\\@]*).*$",
                                        memexuserexpr="$(1)")

    # Define repository connection
    define_memex_repository_connection_ui( username,
                                        password,
                                        "MemexConnection",
                                        "Memex Connection",
                                        memexServerName,
                                        memexServerPort,
                                        memexUser,
                                        memexPassword,
                                        authority_name="MemexAuthority" )

    # PHASE 6: Ingest documents with the specified connectors.
    # There will be several separate kinds of security as well.

    # Define job
    job_id = define_memex_job_ui( username,
                password,
                "Memex Job",
                "MemexConnection",
                [ (memexVirtualServer,"%s:%s" % (memexEntityName, memexEntityDisplayName),None,None,None) ],
                [ (memexEntityName,[memexDataField],[memexRecordKeyField]) ],
                security_enabled=True )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Check for proper visibility of all documents
    # usera
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_memex_url( id1 ) ], username="p_memexusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_memex_url( id3 ) ], username="p_memexusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="p_memexusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="p_memexusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="p_memexusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ build_memex_url( id8 ) ], username="p_memexusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_memex_url( id2 ) ], username="p_memexusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_memex_url( id4 ) ], username="p_memexusera", password="usera", win_host=ad_win_host )

    # userb
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_memex_url( id1 ) ], username="p_memexuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="p_memexuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_memex_url( id5 ) ], username="p_memexuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_memex_url( id6 ) ], username="p_memexuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username="p_memexuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ build_memex_url( id8 ) ], username="p_memexuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_memex_url( id2 ) ], username="p_memexuserb", password="userb", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="p_memexuserb", password="userb", win_host=ad_win_host )

    # userc
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_memex_url( id1 ) ], username="p_memexuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username="p_memexuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username="p_memexuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username="p_memexuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_memex_url( id7 ) ], username="p_memexuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ ], username="p_memexuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_memex_url( id2 ) ], username="p_memexuserc", password="userc", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username="p_memexuserc", password="userc", win_host=ad_win_host )


    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )
    print "...job has vanished"

    ConnectorHelpers.delete_repositoryconnection( "MemexConnection" )

    ConnectorHelpers.delete_authorityconnection( "MemexAuthority" )

    # Clean up the documents.
    # But before we do, better restart tomcat so we free up a license or two
    ConnectorHelpers.restart_tomcat()
    
    for docname in [ id1, id2, id3, id4, id5, id6, id7, id8 ]:
        remove_record(memexServerName, memexServerPort, memexUser, memexPassword, docname)

    ConnectorHelpers.turn_off_ad( ad_domain_info )


# Main
if __name__ == '__main__':

    # AD parameters
    ad_group = "76"
    if len(sys.argv) > 1:
        ad_group = sys.argv[1]
    perform_legacy_pass = False
    if len(sys.argv) > 2 and sys.argv[2] == "legacy":
        perform_legacy_pass = True

    ad_domain_info = sqa_domain_info.SQADomainInfo( ad_group )

    memexServerName = getattr( ad_domain_info, "memex_server_fqdn" )

    print "Precleaning!"

    preclean( ad_domain_info, perform_legacy_pass, print_errors=False )

    print "Restarting services (to assure consistency with handle timeouts)"

    ConnectorHelpers.restart_agents()
    ConnectorHelpers.restart_tomcat()

    print "Setting up ssl"
    setup_ssl_mode( "memex-client-cert/cacert.pem" )

    print "Making snapshot of memex audit log."
    snapshot_memex_server(memexServerName)
    
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
    sqatools.appliance.install_license(extra_services=["memexConnector"], detect_gdms=True)


    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    # PHASE 1: Ingestion

    print "Ingestion Test."

    # MHL
    
    # Add some docs to the repository
    id1 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/f001", memexDataField:load_file("/root/crawlarea/testfiles/f001.txt")})
    id2 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/f002", memexDataField:load_file("/root/crawlarea/testfiles/f002.txt")})
    id3 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/f003", memexDataField:load_file("/root/crawlarea/testfiles/f003.txt")})
    id4 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/f004", memexDataField:load_file("/root/crawlarea/testfiles/f004.txt")})
    id5 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/f005", memexDataField:load_file("/root/crawlarea/testfiles/f005.txt")})
    id6 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/f006", memexDataField:load_file("/root/crawlarea/testfiles/f006.txt")})
    id7 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/f007", memexDataField:load_file("/root/crawlarea/testfiles/f007.txt")})
    id8 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/newfolder/f008", memexDataField:load_file("/root/crawlarea/testfiles/newfolder/f008.txt")})

    # In case there is clock skew, sleep a minute
    wait_for_memex(memexServerName, memexServerPort, memexUser, memexPassword)

    # Define repository connection.
    define_memex_repository_connection_ui( username,
                                        password,
                                        "MemexConnection",
                                        "Memex Connection",
                                        memexServerName,
                                        memexServerPort,
                                        memexUser,
                                        memexPassword )

    # Make sure connection is happy
    ConnectorHelpers.view_repository_connection_ui( username, password, "MemexConnection" )

    # Define job
    job_id = define_memex_job_ui( username,
                        password,
                        "Memex test job",
                        "MemexConnection",
                        [ (memexVirtualServer,"%s:%s" % (memexEntityName, memexEntityDisplayName),None,None,None) ],
                        [ (memexEntityName,[memexDataField],[memexRecordKeyField]) ] )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_memex_url( id1 ) ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_memex_url( id2 ) ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_memex_url( id3 ) ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_memex_url( id4 ) ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_memex_url( id5 ) ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_memex_url( id6 ) ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_memex_url( id7 ) ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ build_memex_url( id8 ) ] )

    # Check if metadata works
    ConnectorHelpers.search_check( [ "reference", "metadata:%s=TestDocs/f001" % memexRecordKeyField ], None, [ build_memex_url( id1 ) ] )

    # Success: done
    print "Done ingestion test."


    # PHASE 2: Document Change Detection

    print "Document Change Test."
    # Modify the documents
    version_record(memexServerName, memexServerPort, memexUser, memexPassword, id2, {memexDataField:load_file("/root/crawlarea/testfiles/f002a.txt")})
    version_record(memexServerName, memexServerPort, memexUser, memexPassword, id4, {memexDataField:load_file("/root/crawlarea/testfiles/f004a.txt")})
    # Sleep, in case there's clock skew
    wait_for_memex(memexServerName, memexServerPort, memexUser, memexPassword)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Look for state of index being right
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_memex_url( id1 ) ] )
    ConnectorHelpers.search_check( [ "good" ], None, [ ] )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_memex_url( id3 ) ] )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_memex_url( id5 ) ] )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_memex_url( id6 ) ] )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_memex_url( id7 ) ] )
    ConnectorHelpers.search_check( [ "restaurants" ], None, [ build_memex_url( id8 ) ] )
    ConnectorHelpers.search_check( [ "platypus" ], None, [ build_memex_url( id2 ) ] )
    ConnectorHelpers.search_check( [ "establishments" ], None, [ build_memex_url( id4 ) ] )

    print "Done Document Change Test."

    # PHASE 3: Document Delete Detection

    print "Document Delete Test."
    remove_record(memexServerName, memexServerPort, memexUser, memexPassword, id3 )
    remove_record(memexServerName, memexServerPort, memexUser, memexPassword, id5 )
    # Sleep, in case of clock skew
    wait_for_memex(memexServerName, memexServerPort, memexUser, memexPassword)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ] )
    ConnectorHelpers.search_check( [ "city" ], None, [ ] )
    print "Done Document Delete Test."

    # PHASE 4: Document Addition Detection

    print "Document Add Test."
    id9 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/f009", memexDataField:load_file("/root/crawlarea/testfiles/f009.txt")})
    id10 = add_record(memexServerName, memexServerPort, memexUser, memexPassword, memexVirtualServer, memexEntityName, {memexRecordKeyField:"TestDocs/f010", memexDataField:load_file("/root/crawlarea/testfiles/f010.txt")})
    wait_for_memex(memexServerName, memexServerPort, memexUser, memexPassword)

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    ConnectorHelpers.search_check( [ "albemarle" ], None, [ build_memex_url( id9 ) ] )
    ConnectorHelpers.search_check( [ "golfcarts" ], None, [ build_memex_url( id10 ) ] )
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

    print "Waiting 16 minutes and running a new job"
    
    # This test verifies that the connections can be re-established after being idle for a while.
    time.sleep(16*60)
    # Define job
    job_id = define_memex_job_ui( username,
                        password,
                        "Memex test job",
                        "MemexConnection",
                        [ (memexVirtualServer,"%s:%s" % (memexEntityName, memexEntityDisplayName),None,None,None) ],
                        [ (memexEntityName,[memexDataField],[memexRecordKeyField]) ] )
    # Run job.  If the problem exists, it will never finish.
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    print "Successfully ran a job after allowing connections to become idle!"
    # Success!
    ConnectorHelpers.delete_job( job_id )
    print "...job delete request sent"
    ConnectorHelpers.wait_job_deleted( job_id )

    ConnectorHelpers.delete_repository_connection_ui( username, password, "MemexConnection" )

    # Clean up the documents we dumped into the folders on memex
    for docname in [ id1, id2, id3, id4, id5, id6, id7, id8, id9, id10 ]:
        remove_record(memexServerName, memexServerPort, memexUser, memexPassword, docname )

    # Run the ad part of the test
    run_ad_test_part( ad_domain_info )
    if perform_legacy_pass:
        ConnectorHelpers.select_legacy_mode()
        run_ad_test_part( ad_domain_info )
        ConnectorHelpers.cancel_legacy_mode()

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    # Clean up temporary folder
    delete_folder("/root/crawlarea")
    LicenseMakerClient.revoke_license

    teardown_ssl_mode( )
    
    ConnectorHelpers.teardown_connector_environment( )

    restore_memex_server(memexServerName)
    
    print "Basic MemexConnector tests PASSED"
