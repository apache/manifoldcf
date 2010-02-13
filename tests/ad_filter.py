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
import re
import sys
import traceback

import ADTestHelpers
import SQAhelpers
import CrawlerEngineBase

if "legacy" in sys.argv:
    from wintools import adtools
else:
    from wintools import adtools_md as adtools
from wintools import filetools
from wintools import sqa_domain_info

from sqatools import appliance

sys.path.append("/usr/lib/metacarta")

directory_dir = "directory"
dir_num_dirs = 2
dir_num_files = 4
dir_path0 = "GTSFiles/blah.txt"
dir_path1 = "GTSFiles/GTS.txt"
dir_path2 = "Other/blah.txt"
dir_path3 = "Other/GTS.txt"
directory_crawls = [
     {"filters" : ['+:*GTS*:Y:I:I', '+:*:N:I:I'],
      "directories" : dir_num_dirs,
      "files" : 2,
      "dirs_filtered" : 1,
      "files_filtered" : 0,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 0,
      "fingerprinted" : 0,
      "ingested": [dir_path0, dir_path1],
      "deleted" :  []},
     {"filters" : ['-:*GTS*:Y:I:I', '+:*:Y:I:I', '+:*GTS*:I:I:I'],
      "directories" : dir_num_dirs,
      "files" : 2,
      "dirs_filtered" : 1,
      "files_filtered" : 1,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 0,
      "fingerprinted" : 0,
      "ingested": [dir_path3],
      "deleted" : [dir_path0, dir_path1]},
     {"filters" : ['+:*:Y:I:I', '+:*GTS*:I:I:I'],
      "directories" : dir_num_dirs,
      "files" : dir_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 1,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 1,
      "fingerprinted" : 0,
      "ingested": [dir_path0, dir_path1],
      "deleted" : []},
     {"filters" : ['+:*GTS*:Y:I:I', '+:*:N:I:I'],
      "directories" : dir_num_dirs,
      "files" : 2,
      "dirs_filtered" : 1,
      "files_filtered" : 0,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 2,
      "fingerprinted" : 0,
      "ingested": [],
      "deleted" : [dir_path3]},
     {"filters" : ['+:*:Y:I:I', '+:*GTS*:N:I:I'],
      "directories" : dir_num_dirs,
      "files" : dir_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 1,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 2,
      "fingerprinted" : 0,
      "ingested": [dir_path3],
      "deleted" : []},
     {"filters" : ['-:*GTS*:I:I:I', '+:*GTS*:Y:I:I'],
      "directories" : dir_num_dirs,
      "files" : 0,
      "dirs_filtered" : 2,
      "files_filtered" : 0,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 0,
      "fingerprinted" : 0,
      "ingested": [],
      "deleted" : [dir_path0, dir_path1, dir_path3]},
     {"filters" : ['+:*:I:I:I'],
      "directories" : dir_num_dirs,
      "files" : dir_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 0,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 0,
      "fingerprinted" : 0,
      "ingested": [dir_path0, dir_path1, dir_path2, dir_path3],
      "deleted" : []}]
pattern_dir = "pattern"
patt_num_dirs = 0
patt_num_files = 9
patt_file0 = "foo"
patt_file1 = "ffoo"
patt_file2 = "foxxo"
patt_file3 = "thefoo"
patt_file4 = "thefoo.txt"
patt_file5 = "foo.txt"
patt_file6 = "fxoo.txt"
patt_file7 = "folao.txt"
patt_file8 = "barfroyo"
pattern_crawls = [
     {"filters" : ['-:*\\f*o*o:I:I:I', '+:*:I:I:I'],
      "directories" : patt_num_dirs,
      "files" : patt_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 3,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 0,
      "fingerprinted" : 0,
      "ingested": [patt_file3, patt_file4, patt_file5, patt_file6,
                   patt_file7, patt_file8],
      "deleted" : []},
     {"filters" : ['+:*\\*f*o*o:I:I:I'],
      "directories" : patt_num_dirs,
      "files" : patt_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 4,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 2,
      "fingerprinted" : 0,
      "ingested": [patt_file0, patt_file1, patt_file2],
      "deleted" : [patt_file4, patt_file5, patt_file6, patt_file7]},
     {"filters" : ['+:*\\f*o*o*:I:I:I'],
      "directories" : patt_num_dirs,
      "files" : patt_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 3,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 3,
      "fingerprinted" : 0,
      "ingested": [patt_file5, patt_file6, patt_file7],
      "deleted" : [patt_file3, patt_file8]},
     {"filters" : ['-:*\\*fo*o*:I:I:I', '+:*:I:I:I'],
      "directories" : patt_num_dirs,
      "files" : patt_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 7,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 1,
      "fingerprinted" : 0,
      "ingested": [patt_file8],
      "deleted" : [patt_file0, patt_file1, patt_file2, patt_file5, patt_file7]},
     {"filters" : ['-:*\\foo:I:I:I', '+:*:I:I:I'],
      "directories" : patt_num_dirs,
      "files" : patt_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 1,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 2,
      "fingerprinted" : 0,
      "ingested": [patt_file1, patt_file2, patt_file3, patt_file4, patt_file5,
                   patt_file7],
      "deleted" : []},
     {"filters" : ['+:*\\foo:I:I:I', '-:*\\foo:I:I:I'],
      "directories" : patt_num_dirs,
      "files" : patt_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 8,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 0,
      "fingerprinted" : 0,
      "ingested": [patt_file0],
      "deleted" : [patt_file1, patt_file2, patt_file3, patt_file4, patt_file5,
                   patt_file6, patt_file7, patt_file8]},
     {"filters" : ['-:*\\foo:I:I:I', '+:*\\foo:I:I:I', '+:*:I:I:I'],
      "directories" : patt_num_dirs,
      "files" : patt_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 1,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 0,
      "fingerprinted" : 0,
      "ingested": [patt_file1, patt_file2, patt_file3, patt_file4, patt_file5,
                   patt_file6, patt_file7, patt_file8],
      "deleted" : [patt_file0]},
     {"filters" : ['+:*\\*f?o?o*:I:I:I'],
      "directories" : patt_num_dirs,
      "files" : patt_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 8,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 1,
      "fingerprinted" : 0,
      "ingested": [],
      "deleted" : [patt_file1, patt_file2, patt_file3, patt_file4, patt_file5,
                   patt_file6, patt_file7]},
     {"filters" :['+:*\\foo.:I:I:I'],
      "directories" : patt_num_dirs,
      "files" : patt_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 8,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 0,
      "fingerprinted" : 0,
      "ingested": [patt_file0],
      "deleted" : [patt_file8]},
     {"filters" :['+:*\\*foo.:I:I:I'],
      "directories" : patt_num_dirs,
      "files" : patt_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 6,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 1,
      "fingerprinted" : 0,
      "ingested": [patt_file1, patt_file3],
      "deleted" : []}]
btc_dir = "btc"
btc_num_dirs = 2
btc_num_files = 2
btc_path0 = "Everyone/everyone.txt"
btc_path1 = "Administrators/everyone.txt"
btc_crawls = [
     {"filters"  : ['-:*:I:I:Y', '+:*:I:I:I'],
      "directories" : btc_num_dirs,
      "files" : btc_num_files,
      "btc" : 0,
      "dirs_filtered" : 0,
      "files_filtered" : 0,
      "bin_filtered" : 0,
      "btc_filtered" : 1,
      "no_change" : 0,
      "fingerprinted" : 0,
      "ingested" : [btc_path0],
      "deleted"  : []},
     {"filters"  : ['+:*:Y:I:I','+:*:I:I:Y'],
      "directories" : btc_num_dirs,
      "files" : btc_num_files,
      "btc" : 1,
      "dirs_filtered" : 0,
      "files_filtered" : 0,
      "bin_filtered" : 0,
      "btc_filtered" : 1,
      "no_change" : 1,
      "fingerprinted" : 0,
      "ingested" : [btc_path1],
      "deleted"  : [btc_path0]},
     {"filters"  : ['+:*:Y:I:I','+:*:I:I:N'],
      "directories" : btc_num_dirs,
      "files" : btc_num_files,
      "btc" : 0,
      "dirs_filtered" : 0,
      "files_filtered" : 0,
      "bin_filtered" : 0,
      "btc_filtered" : 1,
      "no_change" : 1,
      "fingerprinted" : 0,
      "ingested" : [btc_path0],
      "deleted"  : [btc_path1]},
     {"filters"  : ['+:*:Y:I:I','-:*:I:I:N', '+:*:I:I:I'],
      "directories" : btc_num_dirs,
      "files" : btc_num_files,
      "btc" : 1,
      "dirs_filtered" : 0,
      "files_filtered" : 0,
      "bin_filtered" : 0,
      "btc_filtered" : 1,
      "no_change" : 1,
      "fingerprinted" : 0,
      "ingested" : [btc_path1],
      "deleted"  : [btc_path0]}]
bin_dir = "binary"
bin_num_dirs = 0
bin_num_files = 7
bin_ppt = "Geography.ppt"
bin_xls = "UrbanPoliticalGeography.xls"
bin_html = "geography.html"
bin_gif = "geography.gif"
bin_jpg = "geography.jpg"
bin_doc = "GeographyFall2005.doc"
bin_pdf = "geographymatters.pdf"
binary_crawls = [
     {"filters"  : ['-:*:I:N:I', '+:*:I:I:I'],
      "directories" : bin_num_dirs,
      "files" : bin_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 0,
      "bin_filtered" : 2,
      "btc_filtered" : 0,
      "no_change" : 0,
      "fingerprinted" : bin_num_files,
      "ingested" : [bin_pdf, bin_doc, bin_ppt, bin_xls, bin_html],
      "deleted"  : []},
     {"filters"  : ['-:*:I:N:I', '+:*:I:Y:I'],
      "directories" : bin_num_dirs,
      "files" : bin_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 0,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : bin_num_files,
      "fingerprinted" : 0,
      "ingested" : [],
      "deleted"  : []},
     {"filters"  : ['+:*:I:N:I'],
      "directories" : bin_num_dirs,
      "files" : bin_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 0,
      "bin_filtered" : 5,
      "btc_filtered" : 0,
      "no_change" : bin_num_files,
      "fingerprinted" : 0,
      "ingested" : [bin_jpg, bin_gif],
      "deleted"  : [bin_pdf, bin_doc, bin_ppt, bin_xls, bin_html]},
     {"filters"  : ['+:*:I:Y:I'],
      "directories" : bin_num_dirs,
      "files" : bin_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 0,
      "bin_filtered" : 2,
      "btc_filtered" : 0,
      "no_change" : 2,
      "fingerprinted" : 5,
      "ingested" : [bin_pdf, bin_doc, bin_ppt, bin_xls, bin_html],
      "deleted"  : [bin_jpg, bin_gif]},
     {"filters"  : ['+:*.pdf:I:N:I', '+:*.doc:I:N:I', '+:*.ppt:I:N:I',
                    '+:*.xls:I:N:I', '+:*.jpg:I:N:I', '+:*.gif:I:N:I',
                    '+:*.html:I:N:I'],
      "directories" : bin_num_dirs,
      "files" : bin_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 0,
      "bin_filtered" : 5,
      "btc_filtered" : 0,
      "no_change" : 5,
      "fingerprinted" : 2,
      "ingested" : [bin_jpg, bin_gif],
      "deleted"  : [bin_pdf, bin_doc, bin_ppt, bin_xls, bin_html]},
     {"filters"  : ['+:*.pdf:I:Y:I', '+:*.doc:I:Y:I', '+:*.ppt:I:Y:I',
                    '+:*.xls:I:Y:I', '+:*.jpg:I:Y:I', '+:*.gif:I:Y:I',
                    '+:*.html:I:Y:I'],
      "directories" : bin_num_dirs,
      "files" : bin_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 0,
      "bin_filtered" : 2,
      "btc_filtered" : 0,
      "no_change" : 2,
      "fingerprinted" : 5,
      "ingested" : [bin_pdf, bin_doc, bin_ppt, bin_xls, bin_html],
      "deleted"  : [bin_jpg, bin_gif]},
     {"filters"  : ['+:*.pdf:I:I:I', '+:*.doc:I:I:I', '+:*.ppt:I:I:I',
                    '+:*.xls:I:I:I', '+:*.jpg:I:I:I', '+:*.gif:I:I:I',
                    '+:*.html:I:I:I'],
      "directories" : bin_num_dirs,
      "files" : bin_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 0,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 5,
      "fingerprinted" : 0,
      "ingested" : [bin_jpg, bin_gif],
      "deleted"  : []}]

rej_dir = "rejection"
rej_num_dirs = 0
rej_num_files = 1
rej_vir = "testvirus"
rej_crawls = [
      {"filters"  : ['+:*:I:I:I'],
      "directories" : rej_num_dirs,
      "files" : rej_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 0,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 0,
      "fingerprinted" : 0,
      "ingested" : [],
      "deleted"  : []},
      {"filters"  : ['+:*:I:I:I'],
      "directories" : rej_num_dirs,
      "files" : rej_num_files,
      "dirs_filtered" : 0,
      "files_filtered" : 0,
      "bin_filtered" : 0,
      "btc_filtered" : 0,
      "no_change" : 1,
      "fingerprinted" : 0,
      "ingested" : [],
      "deleted"  : []}]

windows_dirs = {directory_dir : {"crawls": directory_crawls},
                pattern_dir   : {"crawls": pattern_crawls},
                btc_dir       : {"crawls": btc_crawls}}
all_dirs = windows_dirs
all_dirs[bin_dir] = {"crawls":binary_crawls, "location": "samba"}
all_dirs[rej_dir] = {"crawls":rej_crawls, "location": "samba"}

def crawl_and_verify(engine, domain_info, dirname, crawl, using_samba, method, samba_version):

    last_log_line = 0
    if os.path.exists(ADTestHelpers.DuckIngestionLog):
        last_log_line = SQAhelpers.get_file_length(
                                                 ADTestHelpers.DuckIngestionLog)

    engine.crawl_until_done()

    if crawl.has_key("btc"):
        ingested = (len(crawl["ingested"]) - crawl["btc"])
        btc = crawl["btc"]
    else:
        ingested = (len(crawl["ingested"]))
        btc = 0

    print "Verifying:", dirname, crawl["filters"]
    engine.check_result_log(context="%s:%s" % (dirname,crawl["filters"]),
                            directories=crawl["directories"],
                            files=crawl["files"], changed=0,
                            no_change=crawl["no_change"],
                            btc=btc,
                            ingested=ingested,
                            deleted=len(crawl["deleted"]),
                            dirs_filtered=crawl["dirs_filtered"],
                            files_filtered=crawl["files_filtered"],
                            bin_filtered=crawl["bin_filtered"],
                            btc_filtered=crawl["btc_filtered"],
                            fingerprinted=crawl["fingerprinted"],
                            errors=0)

    ingestlog = open(ADTestHelpers.DuckIngestionLog)
    lines = ingestlog.readlines()[last_log_line:]
    if len(lines) > (len(crawl["ingested"]) + len(crawl["deleted"])) and method == "sharecrawler" :
        raise Exception(("More activity than expected in ingestion log.\n" +
                           "Expected %s to be ingested and %s to be deleted\n" +
                           "(filters %s).\nInstead, the following lines "
                           "appeared in %s:%s\n") %
                           (crawl["ingested"], crawl["deleted"],
                            crawl["filters"], ADTestHelpers.DuckIngestionLog,
                            lines))
    ingestlog.close()
    if using_samba == True:
        samba_machine = filetools.SambaParameters[samba_version]["SambaMediumLivedMachine"]
        full_path = os.path.join("file://///(%s|%s)/%s" % \
                (samba_machine.split(".")[0],samba_machine,filetools.SambaMediumLivedShareName),
                dirname)
    else:
        full_path = "file://///(%s|%s)/%sshare" % \
                        (domain_info.fileserver_fqdn,domain_info.fileserver_fqdn.split(".")[0],
                         dirname)
    for filename in crawl["ingested"]:
        found = 0
        for line in lines:
            if re.search("^%s/%s\n" % (full_path, filename), line):
                found = 1
                break
        if found == 0:
            raise Exception(("%s/%s not found after line %d in %s " +
                               "(filters %s):\n%s") %
                               (full_path,
                                filename,
                                last_log_line,
                                ADTestHelpers.DuckIngestionLog,
                                crawl["filters"],
                                lines))
    for filename in crawl["deleted"]:
        found = 0
        for line in lines:
            if re.search("^-%s/%s\n" % (full_path, filename), line):
                found = 1
                break
        if found == 0:
            raise Exception(("-%s/%s not listed as deleted after line %d in " +
                               "%s (filters %s):\n%s") %
                               (full_path,
                                filename,
                                last_log_line,
                                ADTestHelpers.DuckIngestionLog,
                                crawl["filters"],
                                lines))

if __name__ == '__main__':

    # process the command line arguments
    (options, args) = ADTestHelpers.process_ad_arguments(sys.argv)

    domain_info = ADTestHelpers.setup_ad_tests(options.domain_id)
    engine = CrawlerEngineBase.build_engine_accessor(options, domain_info)

    if options.cleanup_only:
        print "Beginning cleanup.  No tests will be run"
        print_errors = True
    else:
        print "Cleaning up from previous tests"
        print_errors = False

    exit_status = 0
    try:
        filetools.delete_shares(windows_dirs, domain_info.fileserver_ambassador)
    except:
        if options.cleanup_only:
            traceback.print_exc()
        exit_status = 1

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

    cleanup_exit_status = ADTestHelpers.cleanup(domain_info,
                                                print_errors=print_errors)
    if options.cleanup_only:
        sys.exit(exit_status|cleanup_exit_status)

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

    print "Setting up ShareCrawler"

    # Initialize the accessor
    engine.set_up()

    normal_server, share_user, share_password, share_shrouded_password = CrawlerEngineBase.get_share_credentials(domain_info.fileserver_fqdn)
    samba_server, samba_share_user, samba_share_password, samba_share_shrouded_password = CrawlerEngineBase.get_share_credentials(domain_info.fileserver_fqdn,using_samba=True)


    print "Creating Windows shares"

    filetools.create_dir(directory_dir,
                         domain_info.fileserver_ambassador,
                         share_name=directory_dir+ "share")
    filetools.create_dir("GTSFiles",
                         domain_info.fileserver_ambassador,
                         parent_dir=directory_dir)
    filetools.create_windows_file(
                          "%s\\%s\\GTSFiles\\blah.txt" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                              directory_dir),
                          None,
                          "foo",
                          domain_info.fileserver_ambassador)
    filetools.create_windows_file(
                          "%s\\%s\\GTSFiles\\GTS.txt" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                              directory_dir),
                          None,
                          "bar",
                          domain_info.fileserver_ambassador)

    filetools.create_dir("Other",
                         domain_info.fileserver_ambassador,
                         parent_dir=directory_dir)
    filetools.create_windows_file(
                          "%s\\%s\\Other\\blah.txt" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                              directory_dir),
                          None,
                          "baz",
                          domain_info.fileserver_ambassador)
    filetools.create_windows_file(
                          "%s\\%s\\Other\\GTS.txt" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                              directory_dir),
                          None,
                          "qux",
                          domain_info.fileserver_ambassador)

    filetools.create_dir(pattern_dir,
                         domain_info.fileserver_ambassador,
                         share_name=pattern_dir+"share")
    filetools.create_windows_file(
                          "%s\\%s\\%s" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                              pattern_dir,
                              patt_file0),
                          None,
                          "This",
                          domain_info.fileserver_ambassador)
    filetools.create_windows_file(
                          "%s\\%s\\%s" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                              pattern_dir,
                              patt_file1),
                          None,
                          "is",
                          domain_info.fileserver_ambassador)
    filetools.create_windows_file(
                          "%s\\%s\\%s" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                              pattern_dir,
                              patt_file2),
                          None,
                          "a",
                          domain_info.fileserver_ambassador)
    filetools.create_windows_file(
                          "%s\\%s\\%s" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                              pattern_dir,
                              patt_file3),
                          None,
                          "test",
                          domain_info.fileserver_ambassador)
    filetools.create_windows_file(
                          "%s\\%s\\%s" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                              pattern_dir,
                              patt_file4),
                          None,
                          "This",
                          domain_info.fileserver_ambassador)
    filetools.create_windows_file(
                          "%s\\%s\\%s" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                              pattern_dir,
                              patt_file5),
                          None,
                          "is",
                          domain_info.fileserver_ambassador)
    filetools.create_windows_file(
                          "%s\\%s\\%s" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                              pattern_dir,
                              patt_file6),
                          None,
                          "only",
                          domain_info.fileserver_ambassador)
    filetools.create_windows_file(
                          "%s\\%s\\%s" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                              pattern_dir,
                              patt_file7),
                          None,
                          "a",
                          domain_info.fileserver_ambassador)
    filetools.create_windows_file(
                          "%s\\%s\\%s" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                              pattern_dir,
                              patt_file8),
                          None,
                          "test",
                          domain_info.fileserver_ambassador)

    filetools.create_dir(btc_dir,
                         domain_info.fileserver_ambassador,
                         share_name=btc_dir+"share")
    filetools.create_dir("Everyone",
                         domain_info.fileserver_ambassador,
                         parent_dir=btc_dir)
    filetools.create_windows_file(
                          "%s\\%s\\Everyone\\everyone.txt" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                              btc_dir),
                          None,
                          "woohoo",
                          domain_info.fileserver_ambassador)

    filetools.create_dir("Administrators",
                         domain_info.fileserver_ambassador,
                         parent_dir=btc_dir)
    filetools.create_windows_file(
                          "%s\\%s\\Administrators\\everyone.txt" %
                             (domain_info.fileserver_ambassador.remote_temp_dir,
                             btc_dir),
                          None,
                          "d'oh",
                          domain_info.fileserver_ambassador)
    filetools.change_windows_file_acl(
                     "%s\\%s\\Administrators" %
                        (domain_info.fileserver_ambassador.remote_temp_dir,
                         btc_dir),
                     '[("+", "Domain Admins",' + \
                        'win32acl_util.FILE_TRAVERSE|' + \
                        'win32con.GENERIC_READ),' + \
                      '("+", "%s",' % \
                          (CrawlerEngineBase.default_share_user.username) + \
                        'win32acl_util.FILE_TRAVERSE|' + \
                        'win32con.GENERIC_READ)]',
                     domain_info.fileserver_ambassador)

    # Shareconnector does not support btc, so don't keep this around if
    # shareconnector.
    if options.method == "shareconnector":
        del all_dirs[btc_dir]

    for dirname in all_dirs.keys():
        "Testing", dirname
        if all_dirs[dirname].has_key("location") and \
           (all_dirs[dirname]["location"] == "samba"):
            engine.configure_connection("ShareConnection",
                                samba_server,
                                server_username=samba_share_user,
                                server_password=samba_share_password,
                                server_shrouded_password=samba_share_shrouded_password)

            using_samba = True
            fileserver_fqdn = None
        else:
            engine.configure_connection("ShareConnection",
                                normal_server,
                                server_username=share_user,
                                server_password=share_password,
                                server_shrouded_password=share_shrouded_password)

            using_samba = False
            fileserver_fqdn = domain_info.fileserver_fqdn

        for crawl in all_dirs[dirname]["crawls"]:

            CrawlerEngineBase.configure_job(engine,"ShareJob", {dirname : {"filters": crawl["filters"]}},
                                            using_samba=using_samba, fileserver_fqdn=fileserver_fqdn)
            crawl_and_verify(engine, domain_info, dirname, crawl, using_samba, options.method, options.sambaversion)

        # delete the files from this directory
        engine.remove_job()
        # Delete the connection
        engine.close_connection()

    print "Tests passed.  Cleaning up"
    # clean up the files created on the vmware machine
    filetools.delete_shares(windows_dirs, domain_info.fileserver_ambassador)

    # Tear down the test
    engine.tear_down()

    CrawlerEngineBase.teardown_share_users(domain_info.domain_controller_ambassador)
    CrawlerEngineBase.restore_license()

    exit_status = 0
    exit_status = ADTestHelpers.cleanup(domain_info)
    sys.exit(exit_status)
