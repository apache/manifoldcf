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

import traceback

from sqatools.appliance import check_release_version
if check_release_version() < "4.1.0":
    from wintools import adtools_pre_410 as adtools
else:
    from wintools import adtools as adtools

from wintools import filetools
import sqatools.appliance
from sqatools import LicenseMakerClient
import MetaCartaVersion


class CrawlerEngineBase:

    """ This base class represents functionality needed for a single job (and crawl)
        on either Sharecrawler or the Share Connector running on the connector framework.
        It's meant to be extended either way.  Nevertheless, the model is that the
        state of the crawler instance is contained entirely within - so when a job
        is run (for instance), the job identifier would be kept in this instance.

        Note well: The responsibility of setup here does NOT extend to setting up
        anything in common for the test proper, e.g. the test users.  That should
        be done by common code (which I can put at the end of this file as a static,
        but nevertheless, not part of the class structure). """


    def __init__( self, samba_version="3.0" ):

        """ Base class has nothing to do yet, except initialize debug info
            and log results. """

        self.crawl_debug_info = None
        self.log_results = None
        self.connection_name = None
        self.server_fqdn = None
        self.server_username = None
        self.server_password = None
        self.server_shrouded_password = None
        self.job_name = None
        self.samba_version = samba_version


    def preclean( self ):

        """ Preclean the environment.  This blows away any exist jobs and (at least) related appliance
            data.  NOTE: This can be called before set_up() (or after tear_down() ). """

        pass


    def set_up( self ):

        """ Setup the environment.  Performs the needed logic which installs sharecrawler,
            or debs etc.  Meant to be called once for a number of connections. """

        pass


    def tear_down( self ):
        """ Tear down the environment.  Opposite of set_up(). """

        pass


    def configure_connection( self,
                              connection_name,
                              server_fqdn,
                              server_username=None,
                              server_password=None,
                              server_shrouded_password=None ):

        """ Configure the connection.
            This method allows the instance to be configured with appropriate share connection information.
            Must be called before crawls can be specified! """

        self.connection_name = connection_name
        self.server_fqdn = server_fqdn
        self.server_username = server_username
        self.server_password = server_password
        self.server_shrouded_password = server_shrouded_password


    def close_connection( self ):

        """ Close the connection.
            Call this method before abandoning this object or starting a new connection. """

        self.connection_name = None
        self.server_fqdn = None
        self.server_username = None
        self.server_password = None
        self.server_shrouded_password = None


    def configure_job( self,
                       job_name,
                       shared_dirs,
                       assume_open=False,
                       force_public=False ):

        """ Configure a crawl.
            This method allows specification of what to crawl.
            <shared_dirs> must be a dictionary in the form specified
            by wintools.filetools.create_shares().
            If <assume_open> or <force_public> is true, the Share Crawler options
            Assume Open Share and Force Public, respectively, will be used. """

        self.job_name = job_name


    def remove_job( self ):

        """ Tear down the current job/ingestion group.
            This will remove the data ingested with this job or ingestion group from the index. """

        self.job_name = None


    def crawl_until_done( self ):

        """ Kick off a crawl with the current job, and wait until it completes.
            (The means of communicating with the server should already have been
            set up in the constructor). """

        pass


    def check_result_log( self,
                          context,
                          directories,
                          files,
                          changed=None,
                          no_change=None,
                          btc=None,
                          ingested=None,
                          deleted=None,
                          dirs_filtered=None,
                          files_filtered=None,
                          bin_filtered=None,
                          btc_filtered=None,
                          fingerprinted=None,
                          errors_rejected=None,
                          errors=0):

        """ Verify that the last crawl matched the expected parameters.
            Throws an exception if it didn't.  All arguments should be numerical values. """

        pass


# Share users we will need for some tests

class _ADUser:
    """internal class to group together a user and its passwords"""
    def __init__(self, username, password, shrouded_password):
        self.username = username
        self.password = password
        self.shrouded_password = shrouded_password

default_share_user = _ADUser("ShareAdmin", "password",
                                  "shrouded_passwed")
secondary_share_user = _ADUser("ShareAdmin2", "password",
                                    "shrouded_password")
samba_share_user = _ADUser("qashare", "password",
                                "shrouded_password")
share_users = [default_share_user, secondary_share_user]


def build_engine_accessor(options, domain_info, target_appliance=None):

    """ Build an engine_accessor given command-line options and a domain_info object. """

    method = options.method
    if method == None:
        raise Exception("Missing method option")
    elif method == "sharecrawler":
        import SharecrawlerEngine
        return SharecrawlerEngine.SharecrawlerEngine( target_appliance, domain_info.crawler_ambassador, samba_version=options.sambaversion )
    elif method == "shareconnector":
        import ShareconnectorEngine
        return ShareconnectorEngine.ShareconnectorEngine( samba_version=options.sambaversion, ntlm_mode=options.authmode )
    else:
        raise Exception("Unrecognized method option - %s" % method)

def process_share_dirs( shared_dirs, fileserver_fqdn=None, using_samba=False, samba_version="3.0" ):
    """ Adds in extra fields describing the proper shares, based on whether it's the SQA default
        samba shares or not. """

    assert (using_samba or (fileserver_fqdn != None)), \
           ("Must specify a fileserver fqdn if not using samba")

    shared_dir_list = shared_dirs.keys()

    for dir_name in shared_dirs.keys():
        if shared_dirs[dir_name].has_key("shared_subdir"):
            shared_dir_list.append(shared_dirs[dir_name]["shared_subdir"])

    for dir_name in shared_dir_list:
        if not shared_dirs.has_key(dir_name):
            shared_dirs[dir_name] = {}
        if using_samba:
            shared_dirs[dir_name]["share_path_spec"] = "%s\\%s" % (filetools.SambaParameters[samba_version]["SambaMediumLivedSharePath"], dir_name)
            shared_dirs[dir_name]["share_name"] = "%s/%s" % (filetools.SambaMediumLivedShareName,dir_name)
        else:
            # If it's DFS, then the "share name" we use is the mount point.
            if shared_dirs[dir_name].has_key("dfs_mountpoint"):
                shared_dirs[dir_name]["share_path_spec"] = "\\\\%s\\%s" % (fileserver_fqdn.split(".")[0], shared_dirs[dir_name]["dfs_mountpoint"])
                shared_dirs[dir_name]["share_name"] = shared_dirs[dir_name]["dfs_mountpoint"].replace("\\","/")
            else:
                share_name = dir_name + "share"
                shared_dirs[dir_name]["share_path_spec"] = "\\\\%s\\%s" % (fileserver_fqdn.split(".")[0], share_name)
                shared_dirs[dir_name]["share_name"] = share_name
    return shared_dirs

def configure_job( engine,
                   job_name,
                   shared_dirs,
                   assume_open=False,
                   force_public=False,
                   fileserver_fqdn=None,
                   using_samba=False ):
    """ Shortcut method to process share_dirs structure, and to configure the job. """
    shared_dirs = process_share_dirs(shared_dirs,fileserver_fqdn,using_samba=using_samba,samba_version=engine.samba_version)
    engine.configure_job(job_name,shared_dirs,assume_open=assume_open,force_public=force_public)

def get_share_credentials( server_name, using_samba=False ):

    """ Get the connection servername, username, password, and shrouded password, as a tuple, depending
        on the parameters passed in. """

    if using_samba:
        user = samba_share_user
        server_name = None
    else:
        user = default_share_user
    return ( server_name, user.username, user.password, user.shrouded_password )


def setup_share_users( domain_controller_ambassador, search_ambassador, ad_domain ):

    """ Do the complete setup operation.  This consists of creating the users and initializing the connector,
        and is a replacement for the old version of sharecrawlertools.setup_sharecrawler. """

    global share_users

    # Create users with Administrator privileges.  These are the users
    # whose credentials the ShareCrawler will use when attempting to
    # get the security acls on shares
    for share_user in share_users:
        try:
            adtools.query_entity_in_ldap( share_user.username )
        except adtools.ADToolsException, e:
            adtools.create_user(share_user.username,
                            share_user.password,
                            domain_controller_ambassador,
                            ad_domain,
                            # the ie_client_ambassador argument is just
                            # a machine to use to perform a test search
                            # on the duck, so giving it the crawler
                            # ambassador instead is fine
                            ie_client_ambassador=search_ambassador)
        try:
            adtools.query_group_member_in_ldap( "Administrators", share_user.username, expected_in_group = True )
        except adtools.ADToolsException, e:
            adtools.add_to_group(share_user.username, "Administrators",
                             domain_controller_ambassador)
        try:
            adtools.query_group_member_in_ldap( "Domain Admins", share_user.username, expected_in_group = True )
        except adtools.ADToolsException, e:
            adtools.add_to_group(share_user.username, "Domain Admins",
                             domain_controller_ambassador)

        # if the users are going to hang around we need to ensure that their passwords don't expire
        # this is unconditional due to a point in time where they were expiring and we want tests
        # to force this setting
        adtools.set_no_password_expire(domain_controller_ambassador,ad_domain,share_user.username)


def teardown_share_users( domain_controller_ambassador,
                          print_errors=True ):

    """ Tear down the test setup.  Delete the share users. """
    # this method is a no-op now...
    pass


def create_shares(shared_dirs, fileserver_ambassador):

    """ Creates shares with ACLs that give ShareCrawlerTools share
        administrators read permissions.  For documentation of the arguments,
        see the documentation for wintools.filetools.create_shares() """

    global share_users

    for dir_name in shared_dirs.keys():
        if not shared_dirs[dir_name].has_key("acl"):
            shared_dirs[dir_name]["acl"] = None
        elif (shared_dirs[dir_name]["acl"] != None) and \
             (shared_dirs[dir_name]["acl"] != []):
            for share_user in share_users:
                shared_dirs[dir_name]["acl"].append(('+',
                                                     adtools.get_sid(share_user.username)))

        for filename in shared_dirs[dir_name]["files"]:
            if not filename.has_key("acl"):
                filename["acl"] = None
            elif (filename["acl"] != []) and (filename["acl"] != None):
                for share_user in share_users:
                    filename["acl"].append(('+',
                                            adtools.get_sid(share_user.username)))

    filetools.create_shares(shared_dirs, fileserver_ambassador)


def preclean_license( ):

    """ Restores previous license, if there seems to have been one. """
    LicenseMakerClient.revoke_license( )


def standard_ad_test_license( ):
    """Install a default GDM-specific license with ShareConnector enabled."""
    sqatools.appliance.install_license(extra_services=["shareConnector"], detect_gdms=True)


def restore_license( ):

    """ Restore old license (at end of test). """
    LicenseMakerClient.revoke_license( )
