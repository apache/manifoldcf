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

"""This file contains functions meant to help scripts that are testing
Active Directory functionality."""

import os
import re
import sys
import commands
import optparse
import traceback

import SQAhelpers

# we need to import the proper adtools version for pre_410, legacy or default mode AD
from sqatools.appliance import check_release_version
if check_release_version() < "4.1.0":
    from wintools import adtools_pre_410 as adtools
elif "legacy" in sys.argv:
    from wintools import adtools
else:
    from wintools import adtools_md as adtools

from wintools import sqa_domain_info

from sqatools import appliance

sys.path.append("/usr/lib/metacarta")

import MetaPaths

class ADTestHelpersException(Exception):
    """The Exception class for the ADTestHelpers module."""
    pass

IngestionTraceFlagName = "ingestion_trace"
DuckIngestionLog = os.path.join(MetaPaths.metarundir, "IngestionTrace.log")

def process_ad_arguments(argv, parser = None):
    """Processes the <argv> string and returns a tuple consisting of
    * an object that contains values 'domain_id' (a string), 'hosts' (a
      list), 'cleanup_only' (a boolean), and 'method' (a string with values
      of 'sharecrawler' or 'shareconnector'), 'authmode' (a string with values
      of 'ntlmv1' or 'ntlmv2'), 'sambaversion' (a string with values of
      '3.0' or '3.2').
    * a list of non-argument options"""

    usage = 'Usage: %prog [options]'
    if not parser:
        parser = optparse.OptionParser(usage=usage)

    parser.add_option("-d", "--domain-identifier", dest="domain_id",
                      default="65")
    parser.add_option("--host", dest="hosts", action="append",
                      default=[])
    parser.add_option("-c", "--cleanup-only", dest="cleanup_only",
                      action="store_true", default=False)
    parser.add_option("-p", "--pre", dest="pre_upgrade",
                      action="store_true", default=False)
    parser.add_option("-P", "--post", dest="post_upgrade",
                      action="store_true", default=False)
    parser.add_option("-m", "--method", dest="method", default="sharecrawler")
    parser.add_option("-a", "--authmode", dest="authmode", default="ntlmv1")
    parser.add_option("-v", "--sambaversion", dest="sambaversion", default="3.0")
    parser.add_option("-M", "--multi-domain", dest="multi_domain",
                      action="store_true", default=False)

    return parser.parse_args(args=argv)

def setup_ad_tests(domain_identifier):
    """Installs a standard license, sets the ingestion debug flag,
    and initializes the SQADomainInfo object for <domain_identifier>.

    Returns the SQADomainInfo object."""

    # TODO:  This isn't quite what I want.  It turns on too many
    # services, doesn't allow you to restore an old version, doesn't
    # allow you to enable aggregated search.  I may move Karl's
    # additions to LicenseMakerClient into the sqatools licensing module
    appliance.install_default_license()

    domain_info = sqa_domain_info.SQADomainInfo(domain_identifier)

    # Check that the domain of the local host matches the dns
    # domain for the AD domain
    hostname = SQAhelpers.reverse_resolve()
    if ".".join(hostname.split(".")[-3:]) != domain_info.dns_domain:
        raise ADTestHelpersException(("The duck is not in the correct " +
                                        "subnet:\n    hostname is %s\n" +
                                        "   AD domain is %s\n\n") %
                                        (hostname, domain_info.dns_domain))

    # set the DEBUG flag so that the duck will create a log of all files
    # ingested in log_dir
    cmd = "xyzzy_control --non-interactive enable %s" % \
             (IngestionTraceFlagName)
    (status, output) = commands.getstatusoutput(cmd)
    if status != 0:
        raise ADTestHelpersException("'%s' returned error:\n%s" % (cmd, output))
    clear_ingestion_log()
    return domain_info

def clear_ingestion_log():
    # clear away any previous results
    if os.path.exists(DuckIngestionLog):
        os.remove(DuckIngestionLog)

def cleanup(domain_info, users=None, groups=None,
            print_errors=True):
    """Deletes each user and group in <users> and <groups>, which should
    be lists consisting of usernames or group names.  Leaves the AD domain.
    Restores the license that was present before the tests started.
    If any exceptions are thrown during cleanup, they are caught, the
    information is printed to stderr if <print_errors> is true, and
    cleanup continues.  <domain_info> should be an SQADomainInfo
    object initialized for this domain.

    Returns 0 if no exceptions were caught, nonzero otherwise."""

    error_occurred = 0

    print "Deleting users and groups"
    if (users != None):
        filename = "/tmp/delete_users.ldf"
        ldif_file = open(filename, 'w')
        for username in users:
            adtools.add_ldif_delete_entry_to_file(ldif_file,
                                                  domain_info.ad_domain,
                                                  username)
        ldif_file.close()
        try:
            adtools.import_ldif_file(
                                  filename,
                                  domain_info.domain_controller_ambassador)
        except:
            if print_errors:
                traceback.print_exc()
            error_occurred = 1
            for username in users:
                try:
                    adtools.delete_user(
                                  username,
                                  domain_info.domain_controller_ambassador)
                except:
                    if print_errors:
                        traceback.print_exc()
    if (groups != None):
        filename = "/tmp/delete_groups.ldf"
        ldif_file = open(filename, 'w')
        for groupname in groups:
            adtools.add_ldif_delete_entry_to_file(ldif_file,
                                                  domain_info.ad_domain,
                                                  groupname)
        ldif_file.close()
        try:
            adtools.import_ldif_file(
                                  filename,
                                  domain_info.domain_controller_ambassador)
        except:
            if print_errors:
                traceback.print_exc()
            error_occurred = 1
            for groupname in groups:
                try:
                    adtools.delete_group(
                                  groupname,
                                  domain_info.domain_controller_ambassador)
                except:
                    if print_errors:
                        traceback.print_exc()

    # Turn off ingestion debugging
    # TODO: It might be nice if we turned off ingestion logging, but apparently
    # we didn't before, and the tests would have to change, since the cleanup
    # runs at the beginning (but after the setup) and the end of the test
    #cmd = "xyzzy_control --non-interactive disable %s" % \
              #(IngestionTraceFlagName)
    #(status, output) = commands.getstatusoutput(cmd)
    #if status != 0:
    #    if print_errors:
    #        print "'%s' returned error:\n%s" % (cmd, output)
    #    error_occurred = 1

    print "Leaving the AD domain"
    try:
        adtools.leave_ad(realm_admin_password=domain_info.realm_admin_password)
    except:
        if print_errors:
            traceback.print_exc()
        error_occurred = 1
    SQAhelpers.wait_for_apache()
    # confirm that AD is no longer required for the Web interface
    if adtools.test_search_auth(SQAhelpers.reverse_resolve()):
        if print_errors:
            sys.stderr.write("Unable to reach search page without AD: %s\n" %
                               (output))
        error_occurred = 1;
    try:
        # TODO: restore the license
        pass
    except:
        if print_errors:
            traceback.print_exc()
        error_occurred = 1
    return (error_occurred)
