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
import SOAPpy
from wintools import filetools
from wintools import sqa_domain_info
from wintools import ambassador_client
from wintools import adtools
from sqatools import LicenseMakerClient
from sqatools.sqautils import edit_file
import TestDocs
import VirtualBrowser

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

# Turn off service access-protection switch
def enable_service_protection():
    ConnectorHelpers.invoke_root_script( [ "auth_control",
                          "authorization",
                          "search-web-ui,secure-soap-search",
                          "--add",
                          "infer_service_authz" ] )

# Copy a folder to a (new) area
def copy_folder( source, target ):
    ConnectorHelpers.invoke_root_script( [ "mkdir", "-p", target ] )
    ConnectorHelpers.invoke_root_script( [ "cp", "-r", source, target ] )

# Remove a folder
def delete_folder( target ):
    ConnectorHelpers.invoke_root_script( [ "rm", "-rf", target ] )

# Method to initialize docbroker reference
def initialize(server):
    """ Initialize DFC on the appliance """
    listparams = [ "/usr/bin/metacarta-setupdocumentum",
            ConnectorHelpers.process_argument(server) ]
    return ConnectorHelpers.invoke_root_script( listparams )

# Method to add a document to the livelink repository
def add_livelink_document(servername, port, user, password, llpath, llname, filename):
    """Add a document to the repository"""
    listparams = [ ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(llpath),
            ConnectorHelpers.process_argument(llname),
            ConnectorHelpers.process_argument(filename) ]
    return ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.livelink.AddDoc", 
                                    argument_list=listparams,
                                    additional_classpath="livelink-testing-package/metacarta-livelinkconnector-test.jar" )

# Method to remove a document from the livelink repository
def remove_livelink_document(servername, port, user, password, llpath):
    """Remove a document from the repository"""
    listparams = [ ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(llpath) ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.livelink.RemoveDoc", 
                                    argument_list=listparams,
                                    additional_classpath="livelink-testing-package/metacarta-livelinkconnector-test.jar" )

# Method to update a document in the livelink repository
def version_livelink_document(servername, port, user, password, llpath, filename):
    """Create a new version of an existing document"""
    listparams = [ ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(llpath),
            ConnectorHelpers.process_argument(filename) ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.livelink.VersionDoc", 
                                    argument_list=listparams,
                                    additional_classpath="livelink-testing-package/metacarta-livelinkconnector-test.jar" )

def add_livelink_document_right(servername, port, user, password, llpath, username, domain):
    """Adds the right to see & view contents of the specified path, for the given username"""
    listparams = [ ConnectorHelpers.process_argument(servername),
            ConnectorHelpers.process_argument(port),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(llpath),
            ConnectorHelpers.process_argument(username),
            ConnectorHelpers.process_argument(domain) ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.livelink.AddDocRights", 
                                    argument_list=listparams,
                                    additional_classpath="livelink-testing-package/metacarta-livelinkconnector-test.jar" )

# Method to wait whatever time is needed after changing livelink documents
# for them to be noted as changed.
def wait_for_livelink(servername, port, user, password):
    """Nothing needed"""
    pass

# Invoke documentum-classpath java code
def execute_documentum_java( classname, argument_list=[], input=None ):
    """ Invoke documentum classpath java code """
    command_arguments = [ "documentum-testing-package/executejava", classname ] + argument_list
    return ConnectorHelpers.invoke_script(command_arguments,input=input)

# Method to add a document to the documentum docbase
def add_documentum_document(docbase, domain, user, password, location, file, path):
    """Add a document to the docbase"""
    listparams = [ ConnectorHelpers.process_argument(docbase),
            ConnectorHelpers.process_argument(domain),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(location),
            ConnectorHelpers.process_argument(file),
            ConnectorHelpers.process_argument(path) ]
    return execute_documentum_java( "com.metacarta.crawler.connectors.DCTM.AddDoc", argument_list=listparams )

# Method to remove a document from the documentum docbase
def remove_documentum_document(docbase, domain, user, password, location, file):
    """Remove a document from the docbase"""
    listparams = [ ConnectorHelpers.process_argument(docbase),
            ConnectorHelpers.process_argument(domain),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(location),
            ConnectorHelpers.process_argument(file) ]
    execute_documentum_java( "com.metacarta.crawler.connectors.DCTM.RemoveDoc", argument_list=listparams )

# Method to update a document in the documentum docbase
def version_documentum_document(docbase, domain, user, password, location, file):
    """Create a new version of an existing document"""
    listparams = [ ConnectorHelpers.process_argument(docbase),
            ConnectorHelpers.process_argument(domain),
            ConnectorHelpers.process_argument(user),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(location),
            ConnectorHelpers.process_argument(file) ]
    execute_documentum_java( "com.metacarta.crawler.connectors.DCTM.VersionDoc", argument_list=listparams )

# Method to outwait the time skew between connector framework box
# and documentum docbase
def wait_for_documentum(docbase, domain, user, password):
    """This will eventually create a temporary document, get its time, and wait until then"""
    time.sleep(60)

# Method to add a document to the meridio repository
def add_meridio_document(docurl, recurl, domainuser, password, folder, filepath, filename, filetitle, category="MetacartaSearchable"):
    """Add a document to the repository"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(folder),
            ConnectorHelpers.process_argument(filepath),
            ConnectorHelpers.process_argument(filename),
            ConnectorHelpers.process_argument(filetitle),
            ConnectorHelpers.process_argument(category) ]
    stringval = ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.AddDoc", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )
    stringarry = stringval.split("\n")
    # Take the last line
    return stringarry[len(stringarry)-1]

# Method to add a record to the meridio repository
def add_meridio_record(docurl, recurl, domainuser, password, folder, filepath, filename, filetitle, category="MetacartaSearchable"):
    """Add a document to the repository"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(folder),
            ConnectorHelpers.process_argument(filepath),
            ConnectorHelpers.process_argument(filename),
            ConnectorHelpers.process_argument(filetitle),
            ConnectorHelpers.process_argument(category) ]
    stringval = ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.AddRec", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )
    stringarry = stringval.split("\n")
    # Take the last line
    return stringarry[len(stringarry)-1]

# Method to remove a document from the meridio repository
def remove_meridio_document(docurl, recurl, domainuser, password, docid):
    """Remove a document from the repository"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(docid) ]
    try:
        ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.DeleteDoc", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )
    except Exception, e:
        print "Warning: Error deleting document: %s" % str(e)

# Method to remove a record from the meridio repository
def remove_meridio_record(docurl, recurl, domainuser, password, docid):
    """Remove a document from the repository"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(docid) ]
    try:
        ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.DeleteRec", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )
    except Exception, e:
        print "Warning: Error deleting record: %s" % str(e)

# Method to lookup a document in the meridio repository
def lookup_meridio_document(docurl, recurl, domainuser, password, folder, title):
    """Lookup a document in the repository"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(folder),
            ConnectorHelpers.process_argument(title) ]
    stringval = ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.LookupDoc", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )
    stringarry = stringval.split("\n")
    # Take the last line
    return stringarry[len(stringarry)-1]

# Method to lookup a record in the meridio repository
def lookup_meridio_record(docurl, recurl, domainuser, password, folder, title):
    """Lookup a record in the repository"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(folder),
            ConnectorHelpers.process_argument(title) ]
    stringval = ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.LookupRec", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )
    stringarry = stringval.split("\n")
    # Take the last line
    return stringarry[len(stringarry)-1]

# Method to update a document in the meridio repository
def update_meridio_document(docurl, recurl, domainuser, password, docid, folder, filename):
    """Create a new version of an existing document"""
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(docid),
            ConnectorHelpers.process_argument(folder),
            ConnectorHelpers.process_argument(filename),
            ConnectorHelpers.process_argument("Updated for testing") ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.UpdateDoc", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )

# Method to add a read acl to a document
def add_acl_to_meridio_document(docurl, recurl, domainuser, password, docid, read_username):
    """ Add read permission for the given user to the document """
    listparams = [ ConnectorHelpers.process_argument(docurl),
            ConnectorHelpers.process_argument(recurl),
            ConnectorHelpers.process_argument(domainuser),
            ConnectorHelpers.process_argument(password),
            ConnectorHelpers.process_argument(docid),
            ConnectorHelpers.process_argument(read_username),
            ConnectorHelpers.process_argument("READ") ]
    ConnectorHelpers.invoke_crawler_command( "com.metacarta.crawler.connectors.meridio.AddDocAcl", 
                                    argument_list=listparams,
                                    additional_switches=["-Djava.security.egd=file:///dev/urandom"],
                                    additional_classpath="meridio-testing-package/metacarta-meridioconnector-test.jar" )

# Method to wait whatever time is needed after changing meridio documents
# for them to be noted as changed.
def wait_for_meridio(docurl, recurl, domainuser, password):
    """Nothing needed"""
    pass

# Method to add a document to a jcifs share
def add_share_document(jcifs_servername, jcifs_user, jcifs_password, targetpath, sourcepath):
    """Add a document to the share"""
    """ The code below does not work, because we get an access violation creating the file.  Not sure
        why... """
    #listparams = [ "/usr/lib/metacarta/jcifs-adddoc",
    #       ConnectorHelpers.process_argument(jcifs_servername),
    #       ConnectorHelpers.process_argument(jcifs_user),
    #       ConnectorHelpers.process_argument(jcifs_password),
    #       ConnectorHelpers.process_argument(targetpath),
    #       ConnectorHelpers.process_argument(sourcepath) ]
    #return ConnectorHelpers.invoke_script( listparams )
    amb = ambassador_client.AmbassadorClient(jcifs_servername+":8000", jcifs_user, jcifs_password)
    new_targetpath = "C:\\"+targetpath.replace("/","\\")
    permissions = [ ("+", adtools.EveryoneSID) ]
    fd = open(sourcepath, "r")
    try:
        lines = fd.readlines()
        newlines = []
        for line in lines:
            newlines.append( line.strip() )
        string = " ".join(newlines)
        filetools.create_windows_file(new_targetpath, permissions, string, amb)
        return targetpath
    finally:
        fd.close()

# Method to remove a document from a jcifs share
def remove_share_document(jcifs_servername, jcifs_user, jcifs_password, targetpath):
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
    targetpath = "C:\\"+targetpath.replace("/","\\")

    try:
        amb.run('erase "%s"' % targetpath)
    except Exception, e:
        print "Warning: Error deleting document: %s" % str(e)

# Method to update a document in the jcifs repository
def version_share_document(jcifs_servername, jcifs_user, jcifs_password, targetpath, sourcepath):
    """Create a new version of an existing document"""
    amb = ambassador_client.AmbassadorClient(jcifs_servername+":8000", jcifs_user, jcifs_password)
    targetpath = "C:\\"+targetpath.replace("/","\\")
    try:
        amb.run('erase "%s"' % targetpath)
    except Exception, e:
        print "Warning: Error deleting document: %s" % str(e)

    permissions = [ ("+", adtools.EveryoneSID) ]
    fd = open(sourcepath, "r")
    try:
        lines = fd.readlines()
        newlines = []
        for line in lines:
            newlines.append( line.strip() )
        string = " ".join(newlines)
        filetools.create_windows_file(targetpath, permissions, string, amb)
    finally:
        fd.close()

# Method for setting permissions on a folder or file
def set_share_target_permission(jcifs_servername, jcifs_user, jcifs_password, targetpath, usernames):
    amb = ambassador_client.AmbassadorClient(jcifs_servername+":8000", jcifs_user, jcifs_password)
    targetpath = "C:\\" + targetpath.replace("/","\\")
    permissions = []
    permissions.append( ("+",adtools.get_sid(jcifs_user),0x7FFF0000))
    for username in usernames:
        permissions.append( ("+",adtools.get_sid(username),0x7FFF0000) )
    filetools.change_windows_file_acl(targetpath, permissions, amb)

# Method to wait whatever time is needed after changing jcifs documents
# for them to be noted as changed.
def wait_for_share(jcifs_servername, jcifs_user, jcifs_password):
    """Nothing needed"""
    pass

ad_auth_conf = edit_file("/var/lib/metacarta/ad_authority_service.conf")

# Disable the ability of the AD authority to connect to its LDAP server
def disable_ad_authority(legacy = True):
    """ Use iptables to shut off the ability for ad_authority_service to talk with ldap """
    if legacy:
        ConnectorHelpers.invoke_root_script( ["iptables", "-A", "OUTPUT", "-d", "10.32.76.1", "-p", "tcp", "-m", "multiport", "--destination-ports", "389", "-j", "DROP" ] )
    else:
        ad_auth_conf.backup()
        ad_auth_conf.append("num_dc_retries=1")
        ad_auth_conf.close()
        ConnectorHelpers.invoke_root_script( ["check_user_doc_access", "enabledebug"] )
        ConnectorHelpers.invoke_root_script( ["iptables", "-A", "OUTPUT", "-d", "10.32.76.2", "-p", "udp", "-m", "multiport", "--destination-ports", "88", "-j", "DROP" ] )
        ConnectorHelpers.invoke_root_script( ["iptables", "-A", "OUTPUT", "-d", "10.32.76.2", "-p", "tcp", "-m", "multiport", "--destination-ports", "88", "-j", "DROP" ] )


# Re-enable the ability of the AD authority to connect to LDAP
def enable_ad_authority(legacy = True, preclean = False):
    """ Reverse iptables changes to re-enable ad_authority_service communication with ldap """
    if legacy:
        ConnectorHelpers.invoke_root_script( ["iptables", "-D", "OUTPUT", "1" ] )
    else:                                    
        ad_auth_conf.restore()
        ConnectorHelpers.invoke_root_script( ["check_user_doc_access", "disabledebug"] )
        ConnectorHelpers.invoke_root_script( ["iptables", "-D", "OUTPUT", "2" ] )
        ConnectorHelpers.invoke_root_script( ["iptables", "-D", "OUTPUT", "1" ] )
        if not preclean:
            # test for RT#26331 setting the number of dc retries to another number, like 1 in this case
            contents = open("/var/log/metacarta/debug.log").read()
            if "(spnego_inspect.c:1114) failed after 1 retries (principal=HTTP" not in contents:
                raise Exception("num_dc_retries configuration didn't work")
            if "(spnego_inspect.c:1114) failed after 5 retries (principal=HTTP" in contents:
                raise Exception("num_dc_retires configuration using default")

# LIVELINK VALUES

# The original livelink search user 'lladmin' was created in a transient way.  I've now replaced this with the permanent user 'p_lladmin'.

# Server name to talk to
llServerName = "livelinksrvr.qa-ad-76.metacarta.com"
# Server port to talk to
llServerPort = "2099"
# Crawl user
llUser = "admin"
# Crawl password
llPassword = "livelink"
obfuscatedllPassword = "023A12EA723A122A"
# Search user
llSearchUser = "p_lladmin@qa-ad-76.metacarta.com"
llSearchPassword = "p_lladmin";

# No protocol base url
veryBaseURL = llServerName + "/livelink/livelink.exe"
# Full URL
fullURL = veryBaseURL + "?func=ll&objID="
fullURLSuffix = "&objAction=download"

def build_livelink_url( id ):
    """Build the url from pieces"""
    return fullURL + id + fullURLSuffix

# DOCUMENTUM VALUES

# The user dmadmin is special, because for documentum there isn't a matching domain user until we create one.  Since we don't want to collide with
# previous incarnations of this test, which create the "dmadmin" domain user and tear it back down again, we've created user "p_dmadmin" on the documentum instance.
# The "p_dmadmin" user should have the same rights as the original dmadmin user.
documentum_docbase = "Metacarta1"
documentum_username = "p_dmadmin"
documentum_password = "metacarta"
obfuscated_documentum_password = "523272FA524252CAFA"
documentum_domain = ""

# No protocol base url
documentum_veryBaseURL = "dctmsrvr.qa-ad-76.metacarta.com:8080/webtop/"
# Base URL
documentum_baseURL = "http://"+documentum_veryBaseURL
# Full URL
documentum_fullURL = documentum_veryBaseURL + "component/drl?versionLabel=CURRENT&objectId="

# Search username and password
documentum_search_username = "p_dmadmin@qa-ad-76.metacarta.com"
documentum_search_password = "p_dmadmin"

def build_documentum_url( id ):
    return documentum_fullURL + id

# MERIDIO VALUES

# The search user for meridio was already permanent, so there was no need to build a 'permanent domain equivalent' user.
# Protocol
meridioProtocol = "http"
# Server name to talk to
meridioServerName = "w2k3-mer-76-1.qa-ad-76.metacarta.com"
# Server port to talk to
meridioServerPort = ""
# User
meridioUser = "meridioServiceUser"
# Password
meridioPassword = "metacarta"
obfuscatedMeridioPassword = "obfuscated_password"
# Domain
meridioDomain = "QA-AD-76.METACARTA.COM"
# Document management location
documentLocation = "/DMWS/MeridioDMWS.asmx";
# Record management location
recordLocation = "/RMWS/MeridioRMWS.asmx";
# MetaCarta WS location
metacartaWSLocation = "/MetaCartaWebService/MetaCarta.asmx";
# Web client location
webClientLocation = "/meridio/browse/downloadcontent.aspx";

# These are the calculated URLs for talking with Meridio
meridioDocURL = None
if len(meridioServerPort) > 0:
    meridioDocURL = "%s://%s:%s%s" % (meridioProtocol,meridioServerName,meridioServerPort,documentLocation)
else:
    meridioDocURL = "%s://%s%s" % (meridioProtocol,meridioServerName,documentLocation)

meridioRecURL = None
if len(meridioServerPort) > 0:
    meridioRecURL = "%s://%s:%s%s" % (meridioProtocol,meridioServerName,meridioServerPort,recordLocation)
else:
    meridioRecURL = "%s://%s%s" % (meridioProtocol,meridioServerName,recordLocation)

meridioDomainUser = "%s\%s" % (meridioDomain,meridioUser)

# Meridio search username
meridioSearchUser = meridioUser + "@" + meridioDomain
meridioSearchPassword = meridioPassword

# Document titles and identifiers
title1 = ("/Testing/TestDocs","f001")
title2 = ("/Testing/TestDocs","f002")
title3 = ("/Testing/TestDocs","f003")
title4 = ("/Testing/TestDocs","f004")
title5 = ("/Testing/TestDocs","f005")
title6 = ("/Testing/TestDocs","f006")
title7 = ("/Testing/TestDocs","f007")

# This makes a URL of the kind that will be ingested
def make_meridio_url(document_id):
    # Note: TestDocs results have protocol stripped off.
    if meridioServerPort == "":
        return "%s%s?launchMode=1&launchAs=0&documentId=%s" % (meridioServerName,webClientLocation,document_id)
    else:
        return "%s:%s/%s?launchMode=1&launchAs=0&documentId=%s" % (meridioServerName,meridioServerPort,webClientLocation,document_id)


# SHARE CONNECTOR VALUES

# The old transient user for Share Connector was "shadmin".  To make a permanent, non-colliding equivalent, the new user is called "p_shadmin".
# Server name to talk to
jcifsServerName = "w2k3-shp-76-1.QA-AD-76.METACARTA.COM"
# Domain
jcifsDomain = "QA-AD-76.METACARTA.COM"
# User
jcifsSimpleUser = "Administrator"
jcifsUser = jcifsSimpleUser + "@QA-AD-76.METACARTA.COM"
# Password
jcifsPassword = "password"
# Share name
jcifsShare = "qashare"

# Search username and password
shareSearchUser = "p_shadmin@qa-ad-76.metacarta.com"
shareSearchPassword = "p_shadmin"

def make_share_search_url(path_id):
    return "%s/%s" % (jcifsServerName, path_id)


# CRAWL USER CREDENTIALS
username = "testingest"
password = "testingest"

# Document identifiers
ll_id1 = None
ll_id2 = None
ll_id3 = None
ll_id4 = None
ll_id5 = None
ll_id6 = None
ll_id7 = None
dctm_id1 = None
dctm_id2 = None
dctm_id3 = None
dctm_id4 = None
dctm_id5 = None
dctm_id6 = None
dctm_id7 = None
m_id1 = None
m_id2 = None
m_id3 = None
m_id4 = None
m_id5 = None
m_id6 = None
m_id7 = None
shr_id1 = None
shr_id2 = None
shr_id3 = None
shr_id4 = None
shr_id5 = None
shr_id6 = None
shr_id7 = None

def preclean( ad_domain_info, perform_legacy_pass, print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    # Reregister livelink authority, since we unregister as part of this test at one point
    try:
        ConnectorHelpers.register_authorityconnector("com.metacarta.crawler.connectors.livelink.LivelinkAuthority","LivelinkAuthority")
    except Exception, e:
        if print_errors:
            print "Error registering livelink authority"
            print e

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

    # Remove test documents from share connector
    for docid in [ "TestDocs/f001.txt", "TestDocs/f002.txt", "TestDocs/f003.txt", "TestDocs/f004.txt", "TestDocs/f005.txt",
                        "TestDocs/f006.txt", "TestDocs/f007.txt" ]:
        try:
            remove_share_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/"+docid )
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % docid
                print e

    # Remove test documents from livelink
    for docname in [ "TestDocs/f001", "TestDocs/f002", "TestDocs/f003", "TestDocs/f004", "TestDocs/f005",
                     "TestDocs/f006", "TestDocs/f007" ]:
        try:
            remove_livelink_document(llServerName, llServerPort, llUser, llPassword, docname )
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % docname
                print e

    # Remove test documents from documentum
    delete_docs = [ ("/TestDocs","f001.txt"), ("/TestDocs","f002.txt"), ("/TestDOcs","f003.txt"), ("/TestDocs","f004.txt"),
                ("/TestDocs","f005.txt"), ("/TestDocs","f006.txt"), ("/TestDocs","f007.txt") ]

    for folder,file in delete_docs:
        try:
            remove_documentum_document(documentum_docbase,documentum_domain,documentum_username,documentum_password,folder,file)
        except Exception, e:
            if print_errors:
                print "Error deleting document %s" % file
                print e

    # Remove test documents from meridio
    for docstuff in [title1,title2,title3,title4,title5,title6,title7]:
        (docpath,doctitle) = docstuff
        try:
            docid = lookup_meridio_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,docpath,doctitle)
            if docid != "":
                remove_meridio_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,docid)
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s/%s" % (docpath,doctitle)
                print e

    # Open up ability to talk with AD
    try:
        enable_ad_authority(perform_legacy_pass, True)
    except Exception, e:
        if print_errors:
            print "Error re-enabling ad authority"
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

def run_ad_test_part( ad_domain_info, perform_legacy_pass ):
    """ Run the part of the test that deals with ad.  This may be repeated if the test exercises legacy mode. """

    ad_win_host = ad_domain_info.ie_client_fqdn

    print "Setting up Active Directory"

    # Set up system to use ad
    ConnectorHelpers.configure_ad( ad_domain_info, join_multidomain = not perform_legacy_pass )

    # Enable CF security again
    ConnectorHelpers.enable_connector_framework( )

    # Add the search users
    for username in [ "p_lladmin", "p_dmadmin", "p_shadmin" ]:
        ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, username, username )

    # Set the protections that need setting
    set_share_target_permission(jcifsServerName, jcifsUser, jcifsPassword, shr_id1, [shareSearchUser])
    set_share_target_permission(jcifsServerName, jcifsUser, jcifsPassword, shr_id2, [shareSearchUser])
    set_share_target_permission(jcifsServerName, jcifsUser, jcifsPassword, shr_id3, [shareSearchUser])
    set_share_target_permission(jcifsServerName, jcifsUser, jcifsPassword, shr_id4, [shareSearchUser])
    set_share_target_permission(jcifsServerName, jcifsUser, jcifsPassword, shr_id5, [shareSearchUser])
    set_share_target_permission(jcifsServerName, jcifsUser, jcifsPassword, shr_id6, [shareSearchUser])
    set_share_target_permission(jcifsServerName, jcifsUser, jcifsPassword, shr_id7, [shareSearchUser])

    print "Defining authority connections"

    # Define the authority connections...

    # Define livelink authority connection
    ConnectorHelpers.define_authorityconnection( "LivelinkConnection",
                                 "Livelink Connection",
                                 "com.metacarta.crawler.connectors.livelink.LivelinkAuthority",
                                 configparams = [ "Server name="+llServerName,
                                                  "Server port="+llServerPort,
                                                  "Server user name="+llUser,
                                                  "Server password="+llPassword,
                                                  "Livelink user name map=^p_lladmin@.*\\$=admin" ] )

    # Define documentum authority connection
    ConnectorHelpers.define_authorityconnection( "DocumentumConnection",
                                "Documentum Connection",
                                "com.metacarta.crawler.authorities.DCTM.AuthorityConnector",
                                configparams = [ "docbasename="+documentum_docbase,
                                        "docbaseusername="+documentum_username,
                                        "docbasepassword="+documentum_password,
                                        "domain="+documentum_domain  ] )

    # Define meridio authority connection
    ConnectorHelpers.define_authorityconnection( "MeridioConnection",
                                "Meridio Connection",
                                "com.metacarta.crawler.connectors.meridio.MeridioAuthority",
                                configparams = [ "DMWSServerProtocol="+meridioProtocol,
                                        "DMWSServerName="+meridioServerName,
                                        "DMWSServerPort="+meridioServerPort,
                                        "DMWSLocation="+documentLocation,
                                        "RMWSServerProtocol="+meridioProtocol,
                                        "RMWSServerName="+meridioServerName,
                                        "RMWSServerPort="+meridioServerPort,
                                        "RMWSLocation="+recordLocation,
                                        "MetaCartaWSServerProtocol="+meridioProtocol,
                                        "MetaCartaWSServerName="+meridioServerName,
                                        "MetaCartaWSServerPort="+meridioServerPort,
                                        "MetaCartaWSLocation="+metacartaWSLocation,
                                        "UserName="+meridioDomain + "\\" + meridioUser,
                                        "Password="+meridioPassword ] )


    print "Defining repository connections"

    # Define livelink repository connection
    ConnectorHelpers.define_repositoryconnection( "LivelinkConnection",
                                 "Livelink Connection",
                                 "com.metacarta.crawler.connectors.livelink.LivelinkConnector",
                                 configparams = [ "CGI path=/livelink/livelink.exe",
                                                  "View CGI path=",
                                                  "Server name="+llServerName,
                                                  "Server port="+llServerPort,
                                                  "Server user name="+llUser,
                                                  "Server password="+llPassword,
                                                  "NTLM domain=" ],
                                 authorityname = "LivelinkConnection" )

    # Define documentum repository connection
    ConnectorHelpers.define_repositoryconnection( "DocumentumConnection",
                                        "Documentum Connection",
                                        "com.metacarta.crawler.connectors.DCTM.DCTM",
                                        configparams = [ "docbasename="+documentum_docbase,
                                                "docbaseusername="+documentum_username,
                                                "docbasepassword="+documentum_password,
                                                "docbasedomain="+documentum_domain,
                                                "webtopbaseurl="+documentum_baseURL ],
                                        authorityname="DocumentumConnection")

    # Define meridio connection, using the authority we just defined
    ConnectorHelpers.define_repositoryconnection( "MeridioConnection",
                                        "Meridio Connection",
                                        "com.metacarta.crawler.connectors.meridio.MeridioConnector",
                                        configparams = [ "DMWSServerProtocol="+meridioProtocol,
                                                "DMWSServerName="+meridioServerName,
                                                "DMWSServerPort="+meridioServerPort,
                                                "DMWSLocation="+documentLocation,
                                                "RMWSServerProtocol="+meridioProtocol,
                                                "RMWSServerName="+meridioServerName,
                                                "RMWSServerPort="+meridioServerPort,
                                                "RMWSLocation="+recordLocation,
                                                "MeridioWebClientProtocol="+meridioProtocol,
                                                "MeridioWebClientServerName="+meridioServerName,
                                                "MeridioWebClientServerPort="+meridioServerPort,
                                                "MeridioWebClientDocDownloadLocation="+webClientLocation,
                                                "UserName="+meridioDomain+"\\"+meridioUser,
                                                "Password="+meridioPassword ],
                                        authorityname = "MeridioConnection")

    # Define share connection, using the default authority
    ConnectorHelpers.define_repositoryconnection( "ShareConnection",
                                        "Share Connection",
                                        "com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector",
                                        configparams = [ "Server="+jcifsServerName,
                                                "User Name="+jcifsUser,
                                                "Password="+jcifsPassword] )


    print "Defining jobs"

    # Define livelink job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="TestDocs"/><include filespec="*.txt"/><security value="on"/></specification>'
    livelink_job_id = ConnectorHelpers.define_job( "Livelink test job",
                             "LivelinkConnection",
                             doc_spec_xml )

    # Define documentum job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><docbaselocation path="/TestDocs"/><objecttype token="dm_document"/><mimetype value="crtext"/><security value="on"/></specification>'
    documentum_job_id = ConnectorHelpers.define_job( "Documentum test job",
                "DocumentumConnection",
                doc_spec_xml )

    # Define meridio job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><SearchOn value="DOCUMENTS_AND_RECORDS"/><SearchPath path="Testing/TestDocs"/><SearchCategory category="MetacartaSearchable"/><security value="on"/></specification>'
    meridio_job_id = ConnectorHelpers.define_job( "Meridio test job",
                "MeridioConnection",
                doc_spec_xml )

    # Define share connector job
    doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification><startpoint path="qashare/TestDocs"><include type="file" filespec="*"/><include type="directory" filespec="*"/></startpoint><sharesecurity value="on"/><security value="on"/></specification>'
    share_job_id = ConnectorHelpers.define_job( "Share test job",
                "ShareConnection",
                doc_spec_xml )

    print "Running jobs"

    # Run the jobs to completion
    ConnectorHelpers.start_job( livelink_job_id )
    ConnectorHelpers.start_job( documentum_job_id )
    ConnectorHelpers.start_job( meridio_job_id )
    ConnectorHelpers.start_job( share_job_id )

    ConnectorHelpers.wait_job_complete( livelink_job_id )
    ConnectorHelpers.wait_job_complete( documentum_job_id )
    ConnectorHelpers.wait_job_complete( meridio_job_id )
    ConnectorHelpers.wait_job_complete( share_job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    print "Verifying visibility of ingested documents"

    # Livelink check first
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( ll_id1 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_livelink_url( ll_id3 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_livelink_url( ll_id5 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_livelink_url( ll_id6 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_livelink_url( ll_id7 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_livelink_url( ll_id2 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_livelink_url( ll_id4 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )

    # Now, documentum check
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_documentum_url( dctm_id1 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_documentum_url( dctm_id3 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_documentum_url( dctm_id5 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_documentum_url( dctm_id6 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_documentum_url( dctm_id7 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_documentum_url( dctm_id2 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_documentum_url( dctm_id4 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )

    # Meridio check
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_meridio_url( m_id1 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_meridio_url( m_id3 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_meridio_url( m_id5 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_meridio_url( m_id6 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_meridio_url( m_id7 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_meridio_url( m_id2 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_meridio_url( m_id4 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )

    # Share check
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_share_search_url( shr_id1 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_share_search_url( shr_id3 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_share_search_url( shr_id5 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_share_search_url( shr_id6 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_share_search_url( shr_id7 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_share_search_url( shr_id2 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_share_search_url( shr_id4 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )

    print "Disabling Livelink authority"

    auth_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><configuration>' + \
        '<_PARAMETER_ name="Server name">foo</_PARAMETER_>' + \
        '<_PARAMETER_ name="Server port">' + llServerPort + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Server user name">' + llUser + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Server password">' + obfuscatedllPassword + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Livelink user name map">^p_lladmin@.*\\$=admin</_PARAMETER_>' + \
        '</configuration>'
    ConnectorHelpers.change_auth_spec( "LivelinkConnection", auth_spec_xml )

    print "Verifying visibility of ingested documents"

    # Everything should still be visible except for NON PUBLIC livelink documents

    # Livelink check first
    ConnectorHelpers.search_check( [ "reference" ], None, [ ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )

    # Now, documentum check
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_documentum_url( dctm_id1 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_documentum_url( dctm_id3 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_documentum_url( dctm_id5 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_documentum_url( dctm_id6 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_documentum_url( dctm_id7 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_documentum_url( dctm_id2 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_documentum_url( dctm_id4 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )

    # Meridio check
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_meridio_url( m_id1 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_meridio_url( m_id3 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_meridio_url( m_id5 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_meridio_url( m_id6 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_meridio_url( m_id7 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_meridio_url( m_id2 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_meridio_url( m_id4 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )

    # Share check
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_share_search_url( shr_id1 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_share_search_url( shr_id3 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_share_search_url( shr_id5 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_share_search_url( shr_id6 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_share_search_url( shr_id7 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_share_search_url( shr_id2 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_share_search_url( shr_id4 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )

    print "Disabling Documentum authority"

    auth_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><configuration>' + \
        '<_PARAMETER_ name="Server name">' + llServerName + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Server port">' + llServerPort + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Server user name">' + llUser + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Server password">' + obfuscatedllPassword + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Livelink user name map">^p_lladmin@.*\\$=admin</_PARAMETER_>' + \
        '</configuration>'
    ConnectorHelpers.change_auth_spec( "LivelinkConnection", auth_spec_xml )


    auth_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><configuration>' + \
        '<_PARAMETER_ name="docbasename">foo</_PARAMETER_>' + \
        '<_PARAMETER_ name="docbaseusername">' + documentum_username + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="docbasepassword">' + obfuscated_documentum_password + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="domain">' + documentum_domain + '</_PARAMETER_>' + \
        '</configuration>'
    ConnectorHelpers.change_auth_spec( "DocumentumConnection", auth_spec_xml )

    print "Verifying visibility of ingested documents"

    # All non-public Documentum documents should not show up anymore.

    # Livelink check first
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( ll_id1 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_livelink_url( ll_id3 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_livelink_url( ll_id5 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_livelink_url( ll_id6 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_livelink_url( ll_id7 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_livelink_url( ll_id2 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_livelink_url( ll_id4 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )

    # Now, documentum check
    ConnectorHelpers.search_check( [ "reference" ], None, [ ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )

    # Meridio check
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_meridio_url( m_id1 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_meridio_url( m_id3 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_meridio_url( m_id5 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_meridio_url( m_id6 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_meridio_url( m_id7 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_meridio_url( m_id2 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_meridio_url( m_id4 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )

    # Share check
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_share_search_url( shr_id1 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_share_search_url( shr_id3 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_share_search_url( shr_id5 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_share_search_url( shr_id6 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_share_search_url( shr_id7 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_share_search_url( shr_id2 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_share_search_url( shr_id4 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )

    print "Disabling Meridio authority"

    auth_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><configuration>' + \
        '<_PARAMETER_ name="docbasename">' + documentum_docbase + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="docbaseusername">' + documentum_username + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="docbasepassword">' + obfuscated_documentum_password + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="domain">' + documentum_domain + '</_PARAMETER_>' + \
        '</configuration>'
    ConnectorHelpers.change_auth_spec( "DocumentumConnection", auth_spec_xml )

    auth_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><configuration>' + \
        '<_PARAMETER_ name="DMWSServerProtocol">' + meridioProtocol + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="DMWSServerName">foo</_PARAMETER_>' + \
        '<_PARAMETER_ name="DMWSServerPort">' + meridioServerPort + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="DMWSLocation">' + documentLocation + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSServerProtocol">' + meridioProtocol + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSServerName">foo</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSServerPort">' + meridioServerPort + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSLocation">' + recordLocation + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSServerProtocol">' +meridioProtocol+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSServerName=">'+meridioServerName+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSServerPort=">' +meridioServerPort+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSLocation=">' +metacartaWSLocation+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="UserName">' + meridioDomain+"\\"+meridioUser + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Password">' + obfuscatedMeridioPassword + '</_PARAMETER_>' + \
        '</configuration>'
    ConnectorHelpers.change_auth_spec( "MeridioConnection", auth_spec_xml )

    print "Verifying visibility of ingested documents"

    # Should see NO meridio documents, not even public ones!

    # Livelink check first
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( ll_id1 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_livelink_url( ll_id3 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_livelink_url( ll_id5 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_livelink_url( ll_id6 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_livelink_url( ll_id7 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_livelink_url( ll_id2 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_livelink_url( ll_id4 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )

    # Now, documentum check
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_documentum_url( dctm_id1 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_documentum_url( dctm_id3 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_documentum_url( dctm_id5 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_documentum_url( dctm_id6 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_documentum_url( dctm_id7 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_documentum_url( dctm_id2 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_documentum_url( dctm_id4 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )

    # Meridio check
    ConnectorHelpers.search_check( [ "reference" ], None, [  ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [  ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [  ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [  ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [  ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [  ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [  ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )

    # Share check
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_share_search_url( shr_id1 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_share_search_url( shr_id3 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_share_search_url( shr_id5 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_share_search_url( shr_id6 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_share_search_url( shr_id7 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_share_search_url( shr_id2 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_share_search_url( shr_id4 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )

    print "Re-establishing Meridio authority"

    auth_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><configuration>' + \
        '<_PARAMETER_ name="DMWSServerProtocol">' + meridioProtocol + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="DMWSServerName">'+ meridioServerName + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="DMWSServerPort">' + meridioServerPort + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="DMWSLocation">' + documentLocation + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSServerProtocol">' + meridioProtocol + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSServerName">' + meridioServerName + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSServerPort">' + meridioServerPort + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSLocation">' + recordLocation + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSServerProtocol">' +meridioProtocol+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSServerName">'+meridioServerName+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSServerPort">' +meridioServerPort+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSLocation">' +metacartaWSLocation+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="UserName">' + meridioDomain+"\\"+meridioUser + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Password">' + obfuscatedMeridioPassword + '</_PARAMETER_>' + \
        '</configuration>'
    ConnectorHelpers.change_auth_spec( "MeridioConnection", auth_spec_xml )

    print "Disabling Active Directory authority"

    disable_ad_authority(perform_legacy_pass)

    print "Verifying visibility of ingested documents"

    # Livelink check first
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( ll_id1 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_livelink_url( ll_id3 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_livelink_url( ll_id5 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_livelink_url( ll_id6 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_livelink_url( ll_id7 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_livelink_url( ll_id2 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_livelink_url( ll_id4 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )

    # Now, documentum check
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_documentum_url( dctm_id1 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_documentum_url( dctm_id3 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_documentum_url( dctm_id5 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_documentum_url( dctm_id6 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_documentum_url( dctm_id7 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_documentum_url( dctm_id2 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_documentum_url( dctm_id4 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )

    # Meridio check
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_meridio_url( m_id1 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_meridio_url( m_id3 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_meridio_url( m_id5 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_meridio_url( m_id6 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_meridio_url( m_id7 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_meridio_url( m_id2 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_meridio_url( m_id4 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )

    # Share check
    ConnectorHelpers.search_check( [ "reference" ], None, [ ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )

    print "Reenabling ad authority"

    enable_ad_authority(perform_legacy_pass)

    print "Unregistering livelink authority"

    ConnectorHelpers.deregister_authorityconnector("com.metacarta.crawler.connectors.livelink.LivelinkAuthority")
    # Restart tomcat, so we don't use existing authority handles
    ConnectorHelpers.restart_tomcat()
    time.sleep(60)

    # Do any search check; we should see a 401 error back from soap headnode (NOT a 500!)  However, ambassador fights us here, so all that we see are 500 errors.
    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with an unregistered authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For an unregistered authority, we should have seen a 500 error, but we saw: %s" % str(e))

    print "Reregistering livelink authority"

    ConnectorHelpers.register_authorityconnector("com.metacarta.crawler.connectors.livelink.LivelinkAuthority","LivelinkAuthority")
    # Restart tomcat, so we don't use existing authority handles
    ConnectorHelpers.restart_tomcat()
    time.sleep(60)

    print "Turning on the 'backwards compatibility' switch"

    enable_service_protection()

    print "Checking that each individual user still gets data"

    # With no authorities actually dead, all of these should just return USERNOTFOUND for everything except the specified search user for that repository.
    # So, all these searches should succeed as before.

    # Livelink check first
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( ll_id1 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_livelink_url( ll_id3 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_livelink_url( ll_id5 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_livelink_url( ll_id6 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_livelink_url( ll_id7 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_livelink_url( ll_id2 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_livelink_url( ll_id4 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )

    # Now, documentum check
    ConnectorHelpers.search_check( [ "reference" ], None, [ build_documentum_url( dctm_id1 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ build_documentum_url( dctm_id3 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ build_documentum_url( dctm_id5 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ build_documentum_url( dctm_id6 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ build_documentum_url( dctm_id7 ) ], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ build_documentum_url( dctm_id2 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ build_documentum_url( dctm_id4 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )

    # Meridio check
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_meridio_url( m_id1 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_meridio_url( m_id3 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_meridio_url( m_id5 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_meridio_url( m_id6 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_meridio_url( m_id7 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_meridio_url( m_id2 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_meridio_url( m_id4 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )

    # Share check
    ConnectorHelpers.search_check( [ "reference" ], None, [ make_share_search_url( shr_id1 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "kidneys" ], None, [ make_share_search_url( shr_id3 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "city" ], None, [ make_share_search_url( shr_id5 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "interesting" ], None, [ make_share_search_url( shr_id6 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "smelly" ], None, [ make_share_search_url( shr_id7 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "good" ], None, [ make_share_search_url( shr_id2 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "pub" ], None, [ make_share_search_url( shr_id4 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )

    # Note that it is not currently possible with this setup to test the case for all authorities returning USERNOTFOUND, because the ad_authority_service would have to deny a user's existence
    # even though AD authenticated the user, and I don't know how to fake it out otherwise.  We might be able to do something if AD were not included in the authorization for a service
    # however.

    print "Disabling livelink authority"

    # Disable livelink.  This should make ALL queries fail now, with a 500.

    auth_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><configuration>' + \
        '<_PARAMETER_ name="Server name">foo</_PARAMETER_>' + \
        '<_PARAMETER_ name="Server port">' + llServerPort + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Server user name">' + llUser + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Server password">' + obfuscatedllPassword + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Livelink user name map">^p_lladmin@.*\\$=admin</_PARAMETER_>' + \
        '</configuration>'
    ConnectorHelpers.change_auth_spec( "LivelinkConnection", auth_spec_xml )

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( ll_id1 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled livelink authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled livelink authority, we should have seen a 500 error, but we saw: %s" % str(e))

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ build_documentum_url( dctm_id1 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled livelink authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled livelink authority, we should have seen a 500 error, but we saw: %s" % str(e))

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ make_meridio_url( m_id1 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled livelink authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled livelink authority, we should have seen a 500 error, but we saw: %s" % str(e))

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ make_share_search_url( shr_id1 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled livelink authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled livelink authority, we should have seen a 500 error, but we saw: %s" % str(e))

    print "Reenabling livelink and disabling documentum"

    auth_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><configuration>' + \
        '<_PARAMETER_ name="Server name">' + llServerName + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Server port">' + llServerPort + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Server user name">' + llUser + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Server password">' + obfuscatedllPassword + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Livelink user name map">^p_lladmin@.*\\$=admin</_PARAMETER_>' + \
        '</configuration>'
    ConnectorHelpers.change_auth_spec( "LivelinkConnection", auth_spec_xml )


    auth_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><configuration>' + \
        '<_PARAMETER_ name="docbasename">foo</_PARAMETER_>' + \
        '<_PARAMETER_ name="docbaseusername">' + documentum_username + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="docbasepassword">' + obfuscated_documentum_password + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="domain">' + documentum_domain + '</_PARAMETER_>' + \
        '</configuration>'
    ConnectorHelpers.change_auth_spec( "DocumentumConnection", auth_spec_xml )

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( ll_id1 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled documentum authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled documentum authority, we should have seen a 500 error, but we saw: %s" % str(e))

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ build_documentum_url( dctm_id1 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled documentum authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled documentum authority, we should have seen a 500 error, but we saw: %s" % str(e))

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ make_meridio_url( m_id1 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled documentum authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled documentum authority, we should have seen a 500 error, but we saw: %s" % str(e))

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ make_share_search_url( shr_id1 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled documentum authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled documentum authority, we should have seen a 500 error, but we saw: %s" % str(e))

    print "Reenabling documentum and disabling meridio"

    auth_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><configuration>' + \
        '<_PARAMETER_ name="docbasename">' + documentum_docbase + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="docbaseusername">' + documentum_username + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="docbasepassword">' + obfuscated_documentum_password + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="domain">' + documentum_domain + '</_PARAMETER_>' + \
        '</configuration>'
    ConnectorHelpers.change_auth_spec( "DocumentumConnection", auth_spec_xml )

    auth_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><configuration>' + \
        '<_PARAMETER_ name="DMWSServerProtocol">' + meridioProtocol + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="DMWSServerName">foo</_PARAMETER_>' + \
        '<_PARAMETER_ name="DMWSServerPort">' + meridioServerPort + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="DMWSLocation">' + documentLocation + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSServerProtocol">' + meridioProtocol + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSServerName">foo</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSServerPort">' + meridioServerPort + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSLocation">' + recordLocation + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSServerProtocol">' +meridioProtocol+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSServerName=">'+meridioServerName+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSServerPort=">' +meridioServerPort+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSLocation=">' +metacartaWSLocation+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="UserName">' + meridioDomain+"\\"+meridioUser + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Password">' + obfuscatedMeridioPassword + '</_PARAMETER_>' + \
        '</configuration>'
    ConnectorHelpers.change_auth_spec( "MeridioConnection", auth_spec_xml )

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( ll_id1 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled meridio authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled meridio authority, we should have seen a 500 error, but we saw: %s" % str(e))

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ build_documentum_url( dctm_id1 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled meridio authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled meridio authority, we should have seen a 500 error, but we saw: %s" % str(e))

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ make_meridio_url( m_id1 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled meridio authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled meridio authority, we should have seen a 500 error, but we saw: %s" % str(e))

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ make_share_search_url( shr_id1 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled meridio authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled meridio authority, we should have seen a 500 error, but we saw: %s" % str(e))

    print "Reenabling meridio and disabling AD authority"

    auth_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><configuration>' + \
        '<_PARAMETER_ name="DMWSServerProtocol">' + meridioProtocol + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="DMWSServerName">'+ meridioServerName + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="DMWSServerPort">' + meridioServerPort + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="DMWSLocation">' + documentLocation + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSServerProtocol">' + meridioProtocol + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSServerName">' + meridioServerName + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSServerPort">' + meridioServerPort + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="RMWSLocation">' + recordLocation + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSServerProtocol">' +meridioProtocol+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSServerName=">'+meridioServerName+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSServerPort=">' +meridioServerPort+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="MetaCartaWSLocation=">' +metacartaWSLocation+ '</_PARAMETER_>' + \
        '<_PARAMETER_ name="UserName">' + meridioDomain+"\\"+meridioUser + '</_PARAMETER_>' + \
        '<_PARAMETER_ name="Password">' + obfuscatedMeridioPassword + '</_PARAMETER_>' + \
        '</configuration>'
    ConnectorHelpers.change_auth_spec( "MeridioConnection", auth_spec_xml )

    disable_ad_authority(perform_legacy_pass)

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ build_livelink_url( ll_id1 ) ], username=llSearchUser, password=llSearchPassword, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled active directory authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled active directory authority, we should have seen a 500 error, but we saw: %s" % str(e))

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ build_documentum_url( dctm_id1 )], username=documentum_search_username, password=documentum_search_password, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled active directory authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled active directory authority, we should have seen a 500 error, but we saw: %s" % str(e))

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ make_meridio_url( m_id1 ) ], username=meridioSearchUser, password=meridioSearchPassword, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled active directory authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled active directory authority, we should have seen a 500 error, but we saw: %s" % str(e))

    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ make_share_search_url( shr_id1 ) ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with a disabled active directory authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For a disabled active directory authority, we should have seen a 500 error, but we saw: %s" % str(e))

    print "Re-enabling AD authority"

    enable_ad_authority(perform_legacy_pass)

    print "Unregistering livelink authority"

    ConnectorHelpers.deregister_authorityconnector("com.metacarta.crawler.connectors.livelink.LivelinkAuthority")
    # Restart tomcat, so we don't use existing authority handles
    ConnectorHelpers.restart_tomcat()
    time.sleep(60)

    # Do any search check; we should see a 401 error back from soap headnode (NOT a 500!)  But ambassador hides the true http error code.
    try:
        ConnectorHelpers.search_check( [ "reference" ], None, [ ], username=shareSearchUser, password=shareSearchPassword, win_host=ad_win_host )
        # Success is bad news!  Should have had an exception
        raise Exception("Search should not have succeeded with an unregistered authority!!")
    except  SOAPpy.Errors.HTTPError,e:
        if str(e).find("500") == -1:
            raise Exception("For an unregistered authority, we should have seen a 500 error, but we saw: %s" % str(e))

    print "Reregistering livelink authority"

    ConnectorHelpers.register_authorityconnector("com.metacarta.crawler.connectors.livelink.LivelinkAuthority","LivelinkAuthority")

    print "Removing jobs"

    ConnectorHelpers.delete_job( share_job_id )
    ConnectorHelpers.delete_job( livelink_job_id )
    ConnectorHelpers.delete_job( documentum_job_id )
    ConnectorHelpers.delete_job( meridio_job_id )

    ConnectorHelpers.wait_job_deleted( share_job_id )
    ConnectorHelpers.wait_job_deleted( livelink_job_id )
    ConnectorHelpers.wait_job_deleted( documentum_job_id )
    ConnectorHelpers.wait_job_deleted( meridio_job_id )

    print "Removing repository connections"

    ConnectorHelpers.delete_repositoryconnection( "MeridioConnection" )
    ConnectorHelpers.delete_repositoryconnection( "DocumentumConnection" )
    ConnectorHelpers.delete_repositoryconnection( "LivelinkConnection" )
    ConnectorHelpers.delete_repositoryconnection( "ShareConnection" )

    print "Removing authority connections"

    ConnectorHelpers.delete_authorityconnection( "MeridioConnection" )
    ConnectorHelpers.delete_authorityconnection( "DocumentumConnection" )
    ConnectorHelpers.delete_authorityconnection( "LivelinkConnection" )


    print "Turning off Active Directory"

    ConnectorHelpers.turn_off_ad( ad_domain_info, leave_multidomain = not perform_legacy_pass )

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

    print "Initializing to point to Documentum server"
    initialize( "dctmsrvr.qa-ad-76.metacarta.com" )

    print "Precleaning!"
    preclean( ad_domain_info, perform_legacy_pass, print_errors=False )

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()
    
    print "Setting up file area."
    copy_folder("/root/testfiles","/root/crawlarea")

    # Enable CF security
    ConnectorHelpers.enable_connector_framework( )

    print "Setting up license."
    sqatools.appliance.install_license(
        extra_services=["livelinkConnector", "documentumConnector", "meridioConnector", "shareConnector"],
        detect_gdms=True)


    ConnectorHelpers.create_crawler_user( username, password )
    ConnectorHelpers.define_gts_outputconnection( )
    
    
    print "Adding test documents to repositories."

    # Add some docs to the livelink repository
    ll_id1 = add_livelink_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f001", "/root/crawlarea/testfiles/f001.txt")
    ll_id2 = add_livelink_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f002", "/root/crawlarea/testfiles/f002.txt")
    ll_id3 = add_livelink_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f003", "/root/crawlarea/testfiles/f003.txt")
    ll_id4 = add_livelink_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f004", "/root/crawlarea/testfiles/f004.txt")
    ll_id5 = add_livelink_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f005", "/root/crawlarea/testfiles/f005.txt")
    ll_id6 = add_livelink_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f006", "/root/crawlarea/testfiles/f006.txt")
    ll_id7 = add_livelink_document(llServerName, llServerPort, llUser, llPassword, "TestDocs", "f007", "/root/crawlarea/testfiles/f007.txt")

    # Same for documentum respository
    dctm_id1 = add_documentum_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f001.txt", "/root/crawlarea/testfiles/f001.txt")
    dctm_id2 = add_documentum_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f002.txt", "/root/crawlarea/testfiles/f002.txt")
    dctm_id3 = add_documentum_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f003.txt", "/root/crawlarea/testfiles/f003.txt")
    dctm_id4 = add_documentum_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f004.txt", "/root/crawlarea/testfiles/f004.txt")
    dctm_id5 = add_documentum_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f005.txt", "/root/crawlarea/testfiles/f005.txt")
    dctm_id6 = add_documentum_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f006.txt", "/root/crawlarea/testfiles/f006.txt")
    dctm_id7 = add_documentum_document(documentum_docbase, documentum_domain, documentum_username, documentum_password, "/TestDocs", "f007.txt", "/root/crawlarea/testfiles/f007.txt")

    # Same for meridio repository
    m_id1 = add_meridio_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f001.txt","f001")
    m_id2 = add_meridio_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f002.txt","f002")
    m_id3 = add_meridio_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f003.txt","f003")
    m_id4 = add_meridio_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f004.txt","f004")
    m_id5 = add_meridio_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f005.txt","f005")
    m_id6 = add_meridio_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f006.txt","f006")
    m_id7 = add_meridio_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,"/Testing/TestDocs","/root/crawlarea/testfiles/","f007.txt","f007")

    # Same for share connector
    shr_id1 = add_share_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/f001.txt", "/root/crawlarea/testfiles/f001.txt")
    shr_id2 = add_share_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/f002.txt", "/root/crawlarea/testfiles/f002.txt")
    shr_id3 = add_share_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/f003.txt", "/root/crawlarea/testfiles/f003.txt")
    shr_id4 = add_share_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/f004.txt", "/root/crawlarea/testfiles/f004.txt")
    shr_id5 = add_share_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/f005.txt", "/root/crawlarea/testfiles/f005.txt")
    shr_id6 = add_share_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/f006.txt", "/root/crawlarea/testfiles/f006.txt")
    shr_id7 = add_share_document(jcifsServerName, jcifsUser, jcifsPassword, jcifsShare+"/TestDocs/f007.txt", "/root/crawlarea/testfiles/f007.txt")

    # In case there is clock skew, sleep a minute
    wait_for_meridio(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword)

    # In case there is clock skew, sleep a minute
    wait_for_documentum(documentum_docbase, documentum_domain, documentum_username, documentum_password)

    # In case there is clock skew, sleep a minute
    wait_for_livelink(llServerName, llServerPort, llUser, llPassword)

    # In case there is clock skew, sleep a minute
    wait_for_share(jcifsServerName, jcifsUser, jcifsPassword)

    run_ad_test_part( ad_domain_info, False )
    if perform_legacy_pass:
        ConnectorHelpers.select_legacy_mode()
        run_ad_test_part( ad_domain_info, True )
        ConnectorHelpers.cancel_legacy_mode()

    print "Cleaning up files from repository"

    # Clean up the documents we dumped into the share test area
    for docid in [shr_id1,shr_id2,shr_id3,shr_id4,shr_id5,shr_id6,shr_id7]:
        remove_share_document(jcifsServerName, jcifsUser, jcifsPassword, docid )

    # Clean up the documents we dumped into the folders on livelink
    for docname in [ "TestDocs/f001", "TestDocs/f002", "TestDocs/f003", "TestDocs/f004", "TestDocs/f005",
                     "TestDocs/f006", "TestDocs/f007" ]:
        remove_livelink_document(llServerName, llServerPort, llUser, llPassword, docname )

    # Delete the documents we put into Documentum that are still there
    for file in [ "f001.txt", "f002.txt", "f003.txt", "f004.txt", "f005.txt", "f006.txt", "f007.txt" ]:
        remove_documentum_document(documentum_docbase,documentum_domain,documentum_username,documentum_password,"/TestDocs",file)

    # Clean up the documents we dumped into the folders on meridio
    for docid in [m_id1,m_id2,m_id3,m_id4,m_id5,m_id6,m_id7]:
        remove_meridio_document(meridioDocURL,meridioRecURL,meridioDomainUser,meridioPassword,docid)

    print "Deleting crawl user"

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    # Clean up temporary folder
    delete_folder("/root/crawlarea")
    LicenseMakerClient.revoke_license()

    ConnectorHelpers.teardown_connector_environment( )

    print "Multiauthority tests PASSED"
