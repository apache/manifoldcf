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

import re
import sys
import traceback

import SQAhelpers
import ADTestHelpers
import TestDocs
import SearchCanary
import CrawlerEngineBase
import ad_file_files
from sqatools import docs

if "legacy" in sys.argv:
    from wintools import adtools
else:
    from wintools import adtools_md as adtools

from wintools import filetools
from wintools import spew_files
from wintools import sqa_domain_info

# Note:  Most of the AD file testing is done in ad_user_search.py, which is
# really more of a general acl test

user = "user0"
password = user + "pswd"

if __name__ == '__main__':

    # process the command line arguments
    (options, args) = ADTestHelpers.process_ad_arguments(sys.argv)

    domain_info = ADTestHelpers.setup_ad_tests(options.domain_id)
    engine = CrawlerEngineBase.build_engine_accessor(options, domain_info)

    shared_dirs = {ad_file_files.branch_name:{}}

    if options.cleanup_only:
        print "Beginning cleanup.  No tests will be run"
        print_errors = True
    else:
        print "Cleaning up from previous tests"
        print_errors = False

    # Use the engine preclean to zap jobs/crawls
    exit_status = 0

    try:
        CrawlerEngineBase.preclean_license()
    except:
        if options.cleanup_only:
            traceback.print_exc()
        exit_status = 1

    try:
        engine.preclean()
    except:
        if options.cleanup_only:
            traceback.print_exc()
        exit_status = 1

    if ADTestHelpers.cleanup(domain_info, users=[user], print_errors=print_errors):
        exit_status = 1

    if options.cleanup_only:
        sys.exit(exit_status)

    print "Setting up license."
    CrawlerEngineBase.standard_ad_test_license()

    print "Joining the AD domain."
    adtools.join_ad_with_defaults(domain_info.realm_admin_password)
    adtools.add_to_group(SQAhelpers.reverse_resolve().split('.')[0],
                         adtools.CompatibilityGroup,
                         domain_info.domain_controller_ambassador)

    # Now, set up what we need for the test
    CrawlerEngineBase.setup_share_users( domain_info.domain_controller_ambassador,
                                  domain_info.crawler_ambassador,
                                  domain_info.ad_domain )

    print "Creating user"
    # create the user needed for the test
    adtools.create_user(user,
                        password,
                        domain_info.domain_controller_ambassador,
                        domain_info.ad_domain,
                        ie_client_ambassador=domain_info.ie_client_ambassador)

    print "Setting up ShareCrawler"
    engine.set_up()

    share_server, share_user, share_password, share_shrouded_password = CrawlerEngineBase.get_share_credentials(domain_info.fileserver_fqdn,
                                using_samba=True)

    engine.configure_connection("ShareConnection",
                                share_server,
                                server_username=share_user,
                                server_password=share_password,
                                server_shrouded_password=share_shrouded_password)

    print "Setting up files for main test"
    # Create a config file with the branch directory on the samba server
    CrawlerEngineBase.configure_job(engine,"ShareJob",shared_dirs,using_samba=True)

    engine.crawl_until_done()

    # wait for the canary to make sure that all files have been ingested
    docs.canary_preflight()
    canaryID = None
    print "Waiting for Search Canary"
    canaryID = SearchCanary.create_canary(
                             "HTTP",
                             "ambassador",
                             timeout=32400,
                             search_opts={
                               "win_host":domain_info.ie_client_fqdn + ":" + \
                                            domain_info.ambassador_port,
                               "win_user":domain_info.realm_admin,
                               "win_password":domain_info.realm_admin_password})

    # perform a search
    print "Searching"
    match_list = TestDocs.search_documents(
                                method="ambassador",
                                keywords=[ad_file_files.branch_name],
                                win_host=domain_info.ie_client_fqdn + ":" + \
                                           domain_info.ambassador_port,
                                win_user=user,
                                win_password=password)
    print "Found\n\t%s" % ("\n\t".join(match_list))

    # make sure all files were found
    # print out the list
    ad_file_files.print_file_list_to_log(options.sambaversion)
    log = open(spew_files.logfile_name, 'r')
    loglines = log.readlines()
    log.close()
    for line in loglines:

        # Adjust the line to have the proper domain before we check it
        # against the length cutoff.
        if options.method == "shareconnector":
            samba_machine = filetools.SambaParameters[options.sambaversion]["SambaMediumLivedMachine"]
            line = re.sub(samba_machine.split('.')[0]+"/", samba_machine + "/", line)
        # the spaces will be url-encoded in the ingestion log or match list,
        # and will count towards
        # the 60 characters maximum.
        line = re.sub(' ', '%20', line)

        if (len(line) < 60):
            # TestDocs does not include the leading 'file://///'
            # part of the url in the results
            line = re.sub('file://///', '', line.rstrip())
            try:
                index = match_list.index(line)
            except ValueError:
                raise Exception("File '%s' not found in search:%s" %
                                  (line, match_list))
        else:
            # Very long filenames will be truncated on the search page.
            # Look for these in the ingestion debug log instead
            ingestlog = open(ADTestHelpers.DuckIngestionLog, 'r')
            ingestlines = ingestlog.readlines()
            ingestlog.close()
            try:
                index = ingestlines.index(line)
            except ValueError:
                raise Exception("File '%s' not found in %s:%s" % \
                                  (line, ADTestHelpers.DuckIngestionLog,
                                   ingestlines))
    engine.check_result_log(context="Main ad_file crawl",
                            directories=0,
                            files=len(loglines),
                            changed=0,
                            no_change=0,
                            btc=0,
                            ingested=len(loglines),
                            deleted=0,
                            dirs_filtered=0,
                            files_filtered=0,
                            bin_filtered=0,
                            btc_filtered=0,
                            fingerprinted=len(loglines),
                            errors=0)

    print "Recrawling"
    ingestlog_length = SQAhelpers.get_file_length(
                                                 ADTestHelpers.DuckIngestionLog)

    engine.crawl_until_done()

    new_ingestlog_length = SQAhelpers.get_file_length(
                                                 ADTestHelpers.DuckIngestionLog)

    num_ingested = (new_ingestlog_length - ingestlog_length)
    expected_ingested = 0
    changed = 0
    if num_ingested != expected_ingested:
        # The shareconnector will not crawl the files in the same order.  This is OK, but for the
        # three casediff files, samba will return different file sizes depending on the order crawled.
        # In this instance, this is not a test failure, but rather a samba quirk.
        #
        if (options.method == "shareconnector") and (new_ingestlog_length - ingestlog_length == 3):
            print "WARNING: The casediff files have been crawled in a different order and then detected as changed!"
        else:
            raise Exception(("No files changed before recrawl, but %s went from " +
                               "%d lines to %d lines.") %
                               (ADTestHelpers.DuckIngestionLog, ingestlog_length,
                                new_ingestlog_length))


    engine.check_result_log(context="Only ad_file crawl",
                            directories=0,
                            files=len(loglines),
                            changed=changed,
                            no_change=(len(loglines) - expected_ingested),
                            btc=expected_ingested,
                            ingested=0,
                            deleted=0,
                            dirs_filtered=0,
                            files_filtered=0,
                            bin_filtered=0,
                            btc_filtered=0,
                            fingerprinted=expected_ingested,
                            errors=0)

    print "Tests passed.  Cleaning up"
    # cleanup the files
    engine.remove_job()
    match_list = TestDocs.search_documents(
                                   method="ambassador",
                                   keywords=[ad_file_files.branch_name],
                                   win_host=domain_info.ie_client_fqdn + ":" + \
                                              domain_info.ambassador_port,
                                   win_user=user,
                                   win_password=password)
    expected_length = 0
    if len(match_list) > expected_length:
        raise Exception(("Files found in search after all should have been " +
                           "deleted: %s") % (match_list))

    SearchCanary.destroy_canary(
                             canaryID,
                             "HTTP",
                             "ambassador",
                             timeout=32400,
                             search_opts={
                               "win_host": domain_info.ie_client_fqdn + ":" + \
                                             domain_info.ambassador_port,
                               "win_user":domain_info.realm_admin,
                               "win_password":domain_info.realm_admin_password})


    # Tear down the "connection"
    engine.close_connection()

    # Tear down the test
    engine.tear_down()

    CrawlerEngineBase.teardown_share_users(domain_info.domain_controller_ambassador)
    CrawlerEngineBase.restore_license()

    exit_status = ADTestHelpers.cleanup(domain_info, users=[user])
    sys.exit(exit_status)
