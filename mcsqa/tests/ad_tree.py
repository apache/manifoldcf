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
import commands
import sys
import traceback
import ad_tree_files

import SQAhelpers
import ADTestHelpers
import CrawlerEngineBase

if "legacy" in sys.argv:
    from wintools import adtools
else:
    from wintools import adtools_md as adtools

from wintools import filetools
from wintools import spew_files

if __name__ == '__main__':

    # process the command line arguments
    (options, args) = ADTestHelpers.process_ad_arguments(sys.argv)

    domain_info = ADTestHelpers.setup_ad_tests(options.domain_id)
    engine = CrawlerEngineBase.build_engine_accessor(options, domain_info)

    if options.cleanup_only:
        print "Beginning cleanup.  No tests will be run"
    else:
        print "Cleaning up from previous tests"

    exit_status = 0

    CrawlerEngineBase.preclean_license()

    engine.preclean()

    if ADTestHelpers.cleanup(domain_info, print_errors=options.cleanup_only):
        exit_status = 1

    if options.cleanup_only:
        sys.exit(exit_status)

    if options.method == "shareconnector":
        log_prefix = filetools.SambaParameters[options.sambaversion]["SambaMediumLivedFullIngestionPath"]
    else:
        log_prefix = filetools.SambaParameters[options.sambaversion]["SambaMediumLivedIngestionPath"]

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

    print "Setting up ShareCrawler/Shareconnector"

    engine.set_up()

    share_server, share_user, share_password, share_shrouded_password = CrawlerEngineBase.get_share_credentials(domain_info.fileserver_fqdn,using_samba=True)

    engine.configure_connection("ShareConnection",
                                share_server,
                                server_username=share_user,
                                server_password=share_password,
                                server_shrouded_password=share_shrouded_password)

    CrawlerEngineBase.configure_job(engine,"ShareJob",
                                    ad_tree_files.top_level_dirs,
                                    using_samba=True)

    engine.crawl_until_done()

    ad_tree_files.print_file_list_to_log(log_prefix=log_prefix)

    num_of_files = SQAhelpers.get_file_length(spew_files.logfile_name)
    num_of_directories = 0
    for dirname in ad_tree_files.top_level_dirs.keys():
        num_of_directories += \
               SQAhelpers.count_directories(
                            path=os.path.join(filetools.SambaMediumLivedDirPath,
                                              dirname),
                            machine=filetools.SambaParameters[options.sambaversion]["SambaMediumLivedMachine"],
                            user=filetools.SambaUser,
                            password=filetools.SambaUserPswd)
    engine.check_result_log(context="Initial crawl",
                            directories=num_of_directories,
                            files=num_of_files,
                            changed=0,
                            no_change=0,
                            btc=0,
                            ingested=num_of_files,
                            deleted=0,
                            dirs_filtered=0,
                            files_filtered=0,
                            bin_filtered=0,
                            btc_filtered=0,
                            fingerprinted=num_of_files,
                            errors=0)

    print "Comparing logs of ingested and created files"
    for log in [ADTestHelpers.DuckIngestionLog, spew_files.logfile_name]:
        (status, output) = commands.getstatusoutput("sort %s > %s.sorted"
                                                      % (log, log))
        if status:
            raise Exception("error when sorting %s:%s" % (log, output))
    output = commands.getoutput("diff %s.sorted %s.sorted" %
                                  (ADTestHelpers.DuckIngestionLog,
                                   spew_files.logfile_name))
    if output:
        raise Exception(("found differences between ingested files and " +
                           "created files:\n%s") % (output))

    print "Recrawl.  Observe no change in ingestion logs"
    last_log_line = SQAhelpers.get_file_length(ADTestHelpers.DuckIngestionLog)
    engine.crawl_until_done()
    engine.check_result_log(context="Recrawl",
                            directories=num_of_directories,
                            files=num_of_files,
                            changed=0,
                            no_change=num_of_files,
                            btc=0,
                            ingested=0,
                            deleted=0,
                            dirs_filtered=0,
                            files_filtered=0,
                            bin_filtered=0,
                            btc_filtered=0,
                            fingerprinted=0,
                            errors=0)
    new_last_log_line = SQAhelpers.get_file_length(
                                                 ADTestHelpers.DuckIngestionLog)
    if (last_log_line != new_last_log_line):
        raise Exception(("Unchanged files reingested.  Ingestion log was %d " +
                           "lines long, now %d.") %
                           (last_log_line, new_last_log_line))


    print "All tests passed.  Cleaning up"
    os.remove(ADTestHelpers.DuckIngestionLog)
    engine.remove_job()

    engine.check_result_log(context="Cleanup",
                            directories=0,
                            files=0,
                            btc=0,
                            ingested=0,
                            deleted=num_of_files,
                            dirs_filtered=0,
                            files_filtered=0,
                            bin_filtered=0,
                            btc_filtered=0,
                            fingerprinted=0,
                            errors=0)

    # check that every file that was created is listed as deleted in
    # the ingestion log (represented with a '-' in front of the file name
    (status, output) = commands.getstatusoutput(("sed -e 's/^/-/' < %s.sorted "+
                                                  "> %s.sorted.deleted") %
                                                  (spew_files.logfile_name,
                                                   spew_files.logfile_name))
    if status:
        raise Exception("error when adding '-' to %s: %s" %
                          (spew_files.logfile_name, output))
    (status, output) = commands.getstatusoutput("sort %s > %s.sorted" %
                                               (ADTestHelpers.DuckIngestionLog,
                                                ADTestHelpers.DuckIngestionLog))

    output = commands.getoutput("diff %s.sorted %s.sorted.deleted" %
                                  (ADTestHelpers.DuckIngestionLog,
                                   spew_files.logfile_name))
    if output:
        raise Exception(("Not all files listed as deleted:\n%s") % (output))

    # Tear down the "connection"
    engine.close_connection()

    # Tear down the test
    engine.tear_down()

    CrawlerEngineBase.teardown_share_users(domain_info.domain_controller_ambassador)
    CrawlerEngineBase.restore_license()

    exit_status = ADTestHelpers.cleanup(domain_info)
    sys.exit(exit_status)
