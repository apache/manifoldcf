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
import time
import commands
import traceback

import TestDocs
import SQAhelpers
import SearchCanary
import ADTestHelpers
import CrawlerEngineBase

from sqatools import docs

if "legacy" in sys.argv:
    from wintools import adtools
else:
    from wintools import adtools_md as adtools

from wintools import filetools

sys.path.append("/usr/lib/metacarta")

# all the files and directory names that are used
shared_dir_name = "Delta"
shared_dfs_root_name = "Dfsroot"
shared_dfs_link_name = "Mumblefrats"
shared_dfs_dir_name = "DeltaDfs"
branch_name = "Brnch1"
new_branch_name = "Brnch2"
num_of_dirs = 1
date_file = "date_change.txt"
contents_file = "content_change.txt"
size_file = "size_change.txt"
acl_file = "acl_change.txt"
new_file = "new.txt"
delete_file = "delete.txt"
case_file = "case.txt"
new_case_file = case_file.capitalize()
filter_add_file = "filter_add.txt"
filter_rem_file = "filter_rem.txt"
binary_add_file = "binary_add.jpg"
binary_rem_file = "binary_rem.jpg"
num_of_files = 10

users = ["deltaUser0", "deltaUser1"]

def search_and_verify(domain_info, expected_search_results, current_branch_name,
                      alt_branch_name, random_strings):
    print "Searching"
    match_lists = [[], [], [], []]
    search_num = 0
    for user in users:
        for random_string in random_strings:
            match_lists[search_num] = \
                TestDocs.search_documents(
                                   method="ambassador",
                                   keywords=[random_string],
                                   win_host=domain_info.ie_client_fqdn + ":" + \
                                              domain_info.ambassador_port,
                                   win_user=user,
                                   win_password=user+"pswd",
                                           unique=0,
                                           savefile="/tmp/search%d_results" % \
                                                      (search_num))
            print "Search %d: %s, %s" % (search_num, user, random_string)
            for match in match_lists[search_num]:
                # All files should be found in the current branch
                if (not re.search(current_branch_name, match)):
                    raise Exception("found file not in branch %s:%s" %
                                     (current_branch_name, match))
                # No files should be found in the other branch
                if re.search(alt_branch_name, match):
                    raise Exception("found file in branch %s:%s" %
                                     (alt_branch_name, match))
            # now that the directory names have been verified, remove them
            # and just deal with the file names
            match_lists[search_num] = \
                [match.split("/")[-1] for match in match_lists[search_num]]
            search_num += 1
    # for each file, see that it showed up (or not) as expected in
    # the matches.
    search_num = 0
    for file_list, matches in zip(expected_search_results[0:4], match_lists):
        file_list.sort()
        matches.sort()
        if file_list != matches:
            raise Exception(("Did not get expected matches in search %d. "
                             "Got\n%s\nExpected\n%s") %
                             (search_num, matches, file_list))
        search_num += 1
    ingestlog = open(ADTestHelpers.DuckIngestionLog, 'r')
    lines = ingestlog.readlines()
    ingestlog.close()
    #### TODO: move the ingestion log code into a new test suite,
    #### ad_filter.  It doesn't really belong here.
    for filename in expected_search_results[4]:
        found = 0
        for line in lines:
            if re.search("^file://///(%s|%s)/%sshare/%s/%s\n" %
                            (domain_info.fileserver_fqdn,
                             domain_info.fileserver_fqdn.split(".")[0],
                             shared_dir_name, current_branch_name, filename),
                         line):
                found = 1
                break
        if found == 0:
            raise Exception("%s not found in %s" %
                             (filename, ADTestHelpers.DuckIngestionLog))
    for filename in expected_search_results[5]:
        found = 0
        for line in lines:
            if re.search("^-file://///(%s|%s)/%sshare/%s/%s\n" %
                             (domain_info.fileserver_fqdn,
                              domain_info.fileserver_fqdn.split(".")[0],
                              shared_dir_name, current_branch_name, filename),
                         line):
                found = 1
                break
        if found == 0:
            raise Exception("%s not listed as deleted in %s" %
                             (filename, ADTestHelpers.DuckIngestionLog))


def crawl_and_search(engine, domain_info, expected_search_results, current_branch_name,
    alt_branch_name, random_strings, files_changed, files_ingested,
    files_deleted, fingerprinted, filtered, bin_filtered, context):

    engine.crawl_until_done()

    # Check the values in the debug log
    engine.check_result_log(context=context,
                            directories=num_of_dirs,
                            files=num_of_files,
                            changed=files_changed,
                            ingested=files_ingested,
                            deleted=files_deleted,
                            fingerprinted=fingerprinted,
                            files_filtered=filtered,
                            bin_filtered=bin_filtered)

    # wait for the canary to make sure that all files have been ingested
    docs.canary_preflight()
    canaryID = None
    print "Waiting for the Search Canary"
    canaryID = SearchCanary.create_canary(
                             "HTTP",
                             "ambassador",
                             timeout=32400,
                             search_opts={
                               "win_host":domain_info.ie_client_fqdn + ":" + \
                                            domain_info.ambassador_port,
                               "win_user":domain_info.realm_admin,
                               "win_password":domain_info.realm_admin_password})

    # Sometimes, the stoplist won't have finished updating before the
    # search is performed.
    # Thus, if a search does not get the correct results, it should be
    # retried once, after a short sleep.
    try:
        search_and_verify(domain_info,
                          expected_search_results,
                          current_branch_name,
                          alt_branch_name,
                          random_strings)
    except:
        time.sleep(1)
        search_and_verify(domain_info,
                          expected_search_results,
                          current_branch_name,
                          alt_branch_name,
                          random_strings)

    # the caller needs this to destroy it later
    return canaryID


if __name__ == '__main__':

    # process the command line arguments
    (options, args) = ADTestHelpers.process_ad_arguments(sys.argv)

    domain_info = ADTestHelpers.setup_ad_tests(options.domain_id)
    engine = CrawlerEngineBase.build_engine_accessor(options, domain_info)

    # used to assure that only documents created by this test
    # are found in a search
    random_strings = [SQAhelpers.create_random_string(8),
                      SQAhelpers.create_random_string(8)]
    while (random_strings[0] == random_strings[1]):
        random_strings[1] = SQAhelpers.create_random_string(8)


    # these are the files that should turn up in the first round
    # of searching, before the files are changed
    before_expected_search_results = [
        # search 0: user0, random_strings[0]
        [size_file, delete_file, date_file, contents_file, acl_file,
         filter_rem_file, case_file],
        # search 1: user0, random_strings[1]
        [],
        # search 2: user1, random_strings[0]
        [size_file, delete_file, date_file, contents_file, filter_rem_file,
         case_file],
        # search 3: user1, random_strings[1]
        [],
        # ingested
        [binary_rem_file],
        # removed
        []
    ]

    # these are the files that should turn up in the second round
    # of searching, after the files are changed
    after_expected_search_results = [
        # search 0: user0, random_strings[0]
        [size_file, new_file, date_file, filter_add_file],
        # search 1: user0, random_strings[1]
        [size_file, contents_file],
        # search 2: user1, random_strings[0]
        [size_file, new_file, date_file, acl_file, filter_add_file],
        # search 3: user1, random_strings[1]
        [size_file, contents_file],
        # ingested
        [binary_add_file],
        # removed
        [binary_rem_file]
    ]

    # If the SC is not case-sensitive, the file that has only
    # changed case will not be re-ingested with the new name.
    after_expected_search_results[0].append(new_case_file)
    after_expected_search_results[2].append(new_case_file)

    after_branch_moved = [
        # search 0: user0, random_strings[0]
        [size_file, new_file, date_file, filter_add_file, new_case_file],
        # search 1: user0, random_strings[1]
        [size_file, contents_file],
        # search 2: user1, random_strings[0]
        [size_file, new_file, date_file, acl_file, filter_add_file,
         new_case_file],
        # search 3: user1, random_strings[1]
        [size_file, contents_file],
        # ingested
        [binary_add_file],
        # removed
        # The filtered file will not get removed again; it isn't on the
        # duck when the new branch is ingested
        []
    ]

    # these are the files that should turn up if, after all of
    # changes have been made, the ACLs are forced public
    forced_public_expected_search_results = [
        [date_file, size_file, acl_file, new_file, filter_add_file,
         new_case_file],
        [contents_file, size_file],
        [date_file, size_file, acl_file, new_file, filter_add_file,
         new_case_file],
        [contents_file, size_file],
        [],
        []
    ]

    # shared_dirs initial definition
    # No files yet, because we can't look up the sids yet.
    shared_dirs = {shared_dir_name: {}}

    # DFS root setup
    shared_dfs_root = {
        shared_dfs_root_name: {
            "dfs_root": True,
            "files": [] }}

    # DFS target share setup
    # No files yet, because we can't look up the sids yet.
    shared_dfs_dirs = {
        shared_dfs_dir_name: {
            "dfs_mountpoint": shared_dfs_root_name + "\\" + shared_dfs_link_name,
            "subdir": branch_name}}

    if options.cleanup_only:
        print "Beginning cleanup.  No tests will be run"
        print_errors = True
    else:
        print "Cleaning up from previous tests"
        print_errors = False
    exit_status = 0

    try:
        filetools.delete_shares(shared_dirs, domain_info.fileserver_ambassador)
    except:
        if options.cleanup_only:
            traceback.print_exc()
        exit_status = 1

    # DFS tests are not complete, keep this out until they are done.
    if False and options.method == "shareconnector":
        try:
            filetools.delete_shares(shared_dfs_dirs, domain_info.fileserver_ambassador)
        except:
            if options.cleanup_only:
                traceback.print_exc()
            exit_status = 1

        try:
            filetools.delete_shares(shared_dfs_root, domain_info.fileserver_ambassador)
        except:
            if options.cleanup_only:
                traceback.print_exc()
            exit_status = 1

    # Preclean the environment
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
                                                users=users,
                                                print_errors=print_errors)

    if options.cleanup_only:
        sys.exit(exit_status|cleanup_exit_status)

    print "Setting up license."
    CrawlerEngineBase.standard_ad_test_license()

    print "Joining the AD domain."
    adtools.join_ad_with_defaults(domain_info.realm_admin_password)

    # Jen says that this should happen BEFORE setup_share_users, since
    # the test might fail otherwise...
    adtools.add_to_group(SQAhelpers.reverse_resolve().split('.')[0],
                         adtools.CompatibilityGroup,
                         domain_info.domain_controller_ambassador)

    # Now, set up what we need for the test
    CrawlerEngineBase.setup_share_users( domain_info.domain_controller_ambassador,
                                  domain_info.crawler_ambassador,
                                  domain_info.ad_domain )

    # Initialize the accessor
    engine.set_up()

    print "Creating users"
    # create the users needed for the test
    for user in users:
        adtools.create_user(
                          user,
                          user + "pswd",
                          domain_info.domain_controller_ambassador,
                          domain_info.ad_domain,
                          ie_client_ambassador=domain_info.ie_client_ambassador)


    print "Creating test data"

    shared_dirs = {
        shared_dir_name: {
            "subdir": branch_name,
            "files": [
                {"name": date_file,
                 "contents" : SQAhelpers.ShanghaiChina + " : " + \
                                random_strings[0]},

               {"name": contents_file,
                "contents" : SQAhelpers.MumbaiIndia + " : " + \
                                random_strings[0]},

               {"name": size_file,
                "contents" : SQAhelpers.BuenosAiresArgentina + " : " + \
                                random_strings[0]},

               {"name": acl_file,
                "contents" : SQAhelpers.MoscowRussia + " : " +
                                   random_strings[0],
                "acl" : [('+', adtools.get_sid(users[0]))]},

                {"name": delete_file,
                "contents" : SQAhelpers.KarachiPakistan + " : " + \
                                  random_strings[0]},

                {"name": case_file,
                "contents" : SQAhelpers.SaoPauloBrazil + " : " + \
                                  random_strings[0]},

               {"name": filter_rem_file,
                "contents" : SQAhelpers.DelhiIndia + ": " + \
                                  random_strings[0]},

               {"name": filter_add_file,
                "contents" : SQAhelpers.ManilaPhilippines + ": " +\
                                  random_strings[0]},

               {"name": binary_add_file,
                "size" : 0},

               {"name": binary_rem_file,
                "size" : 0}]}}


    # DFS target share setup
    shared_dfs_dirs = {
        shared_dfs_dir_name: {
            "dfs_mountpoint": shared_dfs_root_name + "\\" + shared_dfs_link_name,
            "subdir": branch_name,
            "files": [
                {"name": date_file,
                 "contents" : SQAhelpers.ShanghaiChina + " : " + \
                                random_strings[0]},

               {"name": contents_file,
                "contents" : SQAhelpers.MumbaiIndia + " : " + \
                                random_strings[0]},

               {"name": size_file,
                "contents" : SQAhelpers.BuenosAiresArgentina + " : " + \
                                random_strings[0]},

               {"name": acl_file,
                "contents" : SQAhelpers.MoscowRussia + " : " +
                                   random_strings[0],
                "acl" : [('+', adtools.get_sid(users[0]))]},

                {"name": delete_file,
                "contents" : SQAhelpers.KarachiPakistan + " : " + \
                                  random_strings[0]},

                {"name": case_file,
                "contents" : SQAhelpers.SaoPauloBrazil + " : " + \
                                  random_strings[0]},

               {"name": filter_rem_file,
                "contents" : SQAhelpers.DelhiIndia + ": " + \
                                  random_strings[0]},

               {"name": filter_add_file,
                "contents" : SQAhelpers.ManilaPhilippines + ": " +\
                                  random_strings[0]},

               {"name": binary_add_file,
                "size" : 0},

               {"name": binary_rem_file,
                "size" : 0}]}}


    print "Setting up ShareCrawler"

    shared_dirs[shared_dir_name]["filters"] = ['-:*add.txt:N:I:I',
                                               '-:*add.jpg:I:N:I',
                                               '+:*.jpg:N:I:I',
                                               '+:*:I:I:I']

    share_server, share_user, share_password, share_shrouded_password = CrawlerEngineBase.get_share_credentials(domain_info.fileserver_fqdn)

    engine.configure_connection("ShareConnection",
                                share_server,
                                server_username=share_user,
                                server_password=share_password,
                                server_shrouded_password=share_shrouded_password)

    CrawlerEngineBase.configure_job(engine,"ShareCrawl",shared_dirs,fileserver_fqdn=domain_info.fileserver_fqdn)

    print "Creating Windows shares"
    CrawlerEngineBase.create_shares(shared_dirs,
                                domain_info.fileserver_ambassador)

    print "Initial crawl"

    canaryID = crawl_and_search(engine,
                                domain_info,
                                before_expected_search_results,
                                branch_name,
                                new_branch_name,
                                random_strings,
                                files_changed=0,
                                files_ingested=(num_of_files - 2),
                                files_deleted=0,
                                fingerprinted=1,
                                filtered=1,
                                bin_filtered=1,
                                context="Initial Crawl")

    print "Altering documents"

    temp_dir = domain_info.ie_client_ambassador.remote_temp_dir
    dir_str = "%s\\%s\\%s\\" % (temp_dir, shared_dir_name, branch_name)
    # when writing to a python file, you need four backslashes to end
    # up with one once you get to excuting the python code
    python_dir_str = "%s\\\\%s\\\\%s\\\\" % ("\\\\".join(temp_dir.split("\\")),
                                             shared_dir_name,
                                             branch_name)

    # date/time change, but not content change
    # touch the file to change the modification time
    winfile = "%s%s" % (dir_str, date_file)
    filetools.touch_windows_file(winfile, domain_info.ie_client_ambassador)
    # stoplist the file to get it off the duck.  If it shows up again
    # after the re-crawl, then that means the sharecrawler saw the
    # last modification time change and re-ingested it
    if options.method == "sharecrawler":
        stopurl = "file://///%s/%sshare/%s/%s" % (domain_info.ie_client_fqdn.split(".")[0],
                         shared_dir_name,
                         branch_name,
                         date_file)
    elif options.method == "shareconnector":
        stopurl = "file://///%s/%sshare/%s/%s" % (domain_info.ie_client_fqdn,
                         shared_dir_name,
                         branch_name,
                         date_file)
    else:
        raise Exception("Bad crawl method: %s" % options.method)

    (status, output) = commands.getstatusoutput(
                           'delete "%s"' % stopurl)
    if status:
        raise Exception("error when deleting %s:%s" % (date_file, output))
    # make sure that this file doesn't show up in a search before crawling a
    # second time
    # The search canary from the first search would have to be
    # destroyed at some point.  Might as well do it here.
    SearchCanary.destroy_canary(
                             canaryID,
                             "HTTP",
                             "ambassador",
                             timeout=32400,
                             search_opts={
                               "win_host":domain_info.ie_client_fqdn + ":" + \
                                            domain_info.ambassador_port,
                               "win_user":domain_info.realm_admin,
                               "win_password":domain_info.realm_admin_password})
    tmp_match_list = TestDocs.search_documents(
                                   method="ambassador",
                                   keywords=[random_strings[0]],
                                   win_host=domain_info.ie_client_fqdn + ":" + \
                                              domain_info.ambassador_port,
                                   win_user=users[0],
                                   win_password=users[0] + "pswd",
                                   unique=0,
                                   savefile="/tmp/search_results_all_deleted")
    for match in tmp_match_list:
        if re.search(date_file, match):
            raise Exception("Stoplisted file %s still found in search:%s" %
                              (date_file, tmp_match_list))

    # content/size change
    # create a python script that will write to the file
    winfile = "%s%s" % (python_dir_str, size_file)
    output = filetools.write_to_windows_file(winfile,
                                             "\\n" + random_strings[1] + "\\n",
                                             domain_info.fileserver_ambassador)
    if output:
        raise Exception("error when writing to %s:\n%s" % (winfile, output))

    # acl change
    winfile = "%s%s" % (dir_str, acl_file)
    winfile_acl = [("+", adtools.get_sid(users[1])),
                   ("+", adtools.get_sid(
                                CrawlerEngineBase.default_share_user.username))]
    filetools.change_windows_file_acl(winfile,
                                    winfile_acl,
                                    domain_info.fileserver_ambassador)

    # delete file
    winfile = "%s%s" % (dir_str, delete_file)
    output = domain_info.ie_client_ambassador.run('del /q %s' % (winfile))
    if output:
        raise Exception("error when deleting file %s:\n%s" % (winfile, output))

    # add new file
    output = filetools.create_windows_file("%s%s" % (dir_str, new_file),
                                           [('+', adtools.EveryoneSID)],
                                           SQAhelpers.DelhiIndia + " : " + \
                                             random_strings[0],
                                           domain_info.fileserver_ambassador)
    if output:
        raise Exception("error when creating new file %s%s:\n%s" %
                          (dir_str, new_file, output))

    # content change, no size change
    # change the random string in the file, and see that it comes
    # up only in the second random string search
    winfile = "%s%s" % (python_dir_str, contents_file)
    output = filetools.write_to_windows_file(winfile,
                                           SQAhelpers.MumbaiIndia + " : " + \
                                             random_strings[1],
                                           domain_info.fileserver_ambassador,
                                           'w')
    if output:
        raise Exception("error when writing to %s:\n%s" % (winfile, output))

    # change the case of the filename
    python_file = open("change_case.py", 'w')
    python_file.write("import os\n\n")
    python_file.write("os.rename('%s%s', '%s%s')\n" %
                        (python_dir_str, case_file, python_dir_str,
                         new_case_file))
    python_file.close()
    output = domain_info.fileserver_ambassador.run_python_script(
                                                               "change_case.py")
    if output:
        raise Exception("error while changing case of %s%s:\n%s" %
                          (python_dir_str, case_file, output))

    # change filters: remove the "*add*" filter and add the "*rem*" filter
    shared_dirs[shared_dir_name]["filters"] = ['-:*rem.txt:N:I:I',
                                               '-:*rem.jpg:I:N:I',
                                               '+:*.jpg:N:I:I',
                                               '+:*:I:I:I']

    CrawlerEngineBase.configure_job(engine,"ShareCrawl",shared_dirs,fileserver_fqdn=domain_info.fileserver_fqdn)

    canaryID = crawl_and_search(engine,
                                domain_info,
                                after_expected_search_results,
                                branch_name,
                                new_branch_name,
                                random_strings,
                                files_changed=4,
                                files_ingested=(num_of_files - 2),
                                files_deleted=4,
                                fingerprinted=1,
                                filtered=1,
                                bin_filtered=1,
                                context="Second crawl (files altered)")

    print "Renaming entire branch"
    domain_info.ie_client_ambassador.run('move /Y %s\\%s\\%s %s\\%s\\%s' %
                                           (temp_dir, shared_dir_name,
                                            branch_name, temp_dir,
                                            shared_dir_name, new_branch_name))

    # destroy the canary created for the previous search
    SearchCanary.destroy_canary(
                             canaryID,
                             "HTTP",
                             "ambassador",
                             timeout=32400,
                             search_opts={
                               "win_host":domain_info.ie_client_fqdn + ":" + \
                                               domain_info.ambassador_port,
                               "win_user":domain_info.realm_admin,
                               "win_password":domain_info.realm_admin_password})

    canaryID = crawl_and_search(engine,
                                domain_info,
                                after_branch_moved,
                                new_branch_name,
                                branch_name,
                                random_strings,
                                files_changed=0,
                                files_ingested=(num_of_files - 2),
                                files_deleted=(num_of_files - 2),
                                fingerprinted=1,
                                filtered=1,
                                bin_filtered=1,
                                context="Third crawl (rename branch)")

    print "Forcing public ACLs"
    # destroy the canary created for the previous search
    SearchCanary.destroy_canary(
                             canaryID,
                             "HTTP",
                             "ambassador",
                             timeout=32400,
                             search_opts={
                               "win_host":domain_info.ie_client_fqdn + ":" + \
                                            domain_info.ambassador_port,
                               "win_user":domain_info.realm_admin,
                               "win_password":domain_info.realm_admin_password})

    CrawlerEngineBase.configure_job(engine,
                                    "ShareCrawl",
                                    shared_dirs,
                                    force_public=1,
                                    fileserver_fqdn=domain_info.fileserver_fqdn)

    canaryID = crawl_and_search(engine,
                                domain_info,
                                forced_public_expected_search_results,
                                new_branch_name,
                                branch_name,
                                random_strings,
                                files_changed=(num_of_files - 1),
                                files_ingested=(num_of_files - 2),
                                files_deleted=0,
                                fingerprinted=1,
                                filtered=1,
                                bin_filtered=1,
                                context="Fourth crawl (force public)")


    print "Non-DFS tests passed."

    SearchCanary.destroy_canary(
                             canaryID,
                             "HTTP",
                             "ambassador",
                             timeout=32400,
                             search_opts={
                               "win_host":domain_info.ie_client_fqdn + ":" + \
                                            domain_info.ambassador_port,
                               "win_user":domain_info.realm_admin,
                               "win_password":domain_info.realm_admin_password})



    # cleanup the files and recrawl to remove them from the duck.
    filetools.delete_shares(shared_dirs, domain_info.fileserver_ambassador)


    # DFS tests next!  But - only shareconnector can do them.
    # Actually, it turns out that python does nto have bindings in 2.4 for NetDfs methods,
    # so disable this, for now...
    if False and options.method == "shareconnector":
        # Create dfs root
        filetools.create_shares(shared_dfs_root, domain_info.fileserver_ambassador)
        # Create dfs share
        filetools.create_shares(shared_dfs_dirs, domain_info.fileserver_ambassador)

        shared_dfs_dirs[shared_dfs_dir_name]["filters"] = ['-:*add.txt:N:I:I',
                                                           '-:*add.jpg:I:N:I',
                                                           '+:*.jpg:N:I:I',
                                                           '+:*:I:I:I']

        # Configure job
        CrawlerEngineBase.configure_job(engine,"ShareCrawl",shared_dfs_dirs,fileserver_fqdn=domain_info.fileserver_fqdn)

        canaryID = crawl_and_search(engine,
                                    domain_info,
                                    before_expected_search_results,
                                    branch_name,
                                    new_branch_name,
                                    random_strings,
                                    files_changed=0,
                                    files_ingested=(num_of_files - 2),
                                    files_deleted=0,
                                    fingerprinted=1,
                                    filtered=1,
                                    bin_filtered=1,
                                    context="Initial DFS Crawl")

        print "DFS tests passed."

        # Clean up files and dfs root
        filetools.delete_shares(shared_dfs_dirs, domain_info.fileserver_ambassador)
        filetools.delete_shares(shared_dfs_root, domain_info.fileserver_ambassador)

    # Get rid of the "job"
    engine.remove_job()

    print "All tests passed.  Cleaning up."


    # Tear down the "connection"
    engine.close_connection()

    # Tear down the test
    engine.tear_down()

    CrawlerEngineBase.teardown_share_users(domain_info.domain_controller_ambassador)
    CrawlerEngineBase.restore_license()

    exit_status = ADTestHelpers.cleanup(domain_info, users=users)

    sys.exit(exit_status)
