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
import time
import shutil
import urllib
import urllib2
import commands
import traceback

import TestDocs
import SQAhelpers
import SearchCanary
import ADTestHelpers
import CrawlerEngineBase

if "legacy" in sys.argv:
    from wintools import adtools
else:
    from wintools import adtools_md as adtools

from wintools import filetools
from wintools import sqa_domain_info
from wintools.ambassador_client import AmbassadorError

sys.path.append("/usr/lib/metacarta")


user = "shareTestUser"
iface_config = "/etc/metacarta/iface.conf.appliance"

# the following two functions are ripped out of newwinbox
def waitfor_notthere(ip, threshold=100):
    for attempt in range(threshold):
        if os.system('ping -qn -c 1 %s >/dev/null' % (ip)):
            break
        time.sleep(1)
        sys.stdout.write('.')
        sys.stdout.flush()
    print

def waitfor_ambassador(windows_machine_ambassador, threshold=100):
    TAG = 'HELLO'
    run_result = ''
    for attempt in range(threshold):
        try:
            run_result = windows_machine_ambassador.run('echo ' + TAG)
        except IOError, e:
            sys.stdout.write('.')
            sys.stdout.flush()
            time.sleep(1)
        except urllib2.HTTPError, e:
            sys.stdout.write('..')
            sys.stdout.flush()
            time.sleep(1)
        except AmbassadorError, e:
            sys.stdout.write('...')
            sys.stdout.flush()
            time.sleep(1)
        # Stop checking if we've got a sucessful run
        if run_result.find(TAG) == 0:
            break
    print
    if run_result.find(TAG) == -1:
        raise Exception('Waiting for Ambassador failed')

def verify_file_url(url, windows_machine_ambassador):
    filename = "/tmp/vbscript-verify-url.vbs"
    vbscript_file = open(filename, 'w')
    vbscript_file.write('set args=wscript.Arguments\n')
    vbscript_file.write('URL=args.Item(0)\n')
    vbscript_file.write('Set xml = CreateObject("Microsoft.XMLHTTP")\n')
    vbscript_file.write('xml.Open "GET", URL, False\n')
    vbscript_file.write('xml.Send\n')
    vbscript_file.write('wscript.echo("url verified")\n')
    vbscript_file.write('Set xml = Nothing\n')
    vbscript_file.close()
    output = windows_machine_ambassador.run_vb_script(filename, args=[url])
    if not re.search("url verified", output):
        raise Exception("Unable to verify %s:%s" % (url, output))

def create_shares_crawl_search_and_cleanup(engine, domain_info, shared_dirs,
    random_string, expected_search_results, ingested, errors, scanned_files,
    debug_info, assume_open=False):

    print "Creating Windows shares"
    CrawlerEngineBase.create_shares(shared_dirs,
                                       domain_info.fileserver_ambassador)

    CrawlerEngineBase.configure_job(engine, "ShareJob", shared_dirs, assume_open=assume_open, fileserver_fqdn=domain_info.fileserver_fqdn)

    num_of_dirs = 0

    # share the directory underneath the shared directory
    for shared_dir in shared_dirs.keys():
        if shared_dirs[shared_dir].has_key("shared_subdir"):
            num_of_dirs = 1

    engine.crawl_until_done()

    # wait for the canary to make sure that all files have been ingested
    canaryID = None
    print "Waiting for the Search Canary"
    canaryID = SearchCanary.create_canary("HTTP", "ambassador", timeout=32400,
        search_opts={"win_host":     domain_info.ie_client_fqdn + ":" + \
                                       domain_info.ambassador_port,
                     "win_user":     domain_info.realm_admin,
                     "win_password": domain_info.realm_admin_password})

    print "Searching"
    match_list = TestDocs.search_documents(
                    method="ambassador",
                     keywords=[random_string],
                     win_host=domain_info.ie_client_fqdn + ":" + \
                       domain_info.ambassador_port,
                     win_user=user,
                     win_password=CrawlerEngineBase.default_share_user.password,
                     unique=0)

    original_expected_list = expected_search_results
    expected_search_results.sort()
    match_list.sort()
    print "found", match_list
    if expected_search_results != match_list:
        raise Exception(("Did not get expected matches in search: got %s; " +
                          "expected %s") %
                          (match_list, expected_search_results))

    hostname = SQAhelpers.reverse_resolve()
    print "Verifying urls"
    for url in original_expected_list:
        verify_file_url("file://" + url, domain_info.ie_client_ambassador)

    print "Checking log stats"
    engine.check_result_log(context=debug_info,
                            directories=num_of_dirs,
                            files=scanned_files,
                            changed=0,
                            ingested=ingested,
                            errors=errors)

    print "Destroying documents"
    engine.remove_job()

    filetools.delete_shares(shared_dirs, domain_info.fileserver_ambassador)

    match_list = TestDocs.search_documents(
                     method="ambassador",
                     keywords=[random_string],
                     win_host=domain_info.ie_client_fqdn + ":" + \
                                domain_info.ambassador_port,
                     win_user=user,
                     win_password=CrawlerEngineBase.default_share_user.password,
                     unique=0)
    if match_list != []:
        raise Exception("Documents should have been destroyed, but found %s" %
                          (match_list))

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

if __name__ == '__main__':

    # process the command line arguments
    (options, args) = ADTestHelpers.process_ad_arguments(sys.argv)

    domain_info = ADTestHelpers.setup_ad_tests(options.domain_id)
    engine = CrawlerEngineBase.build_engine_accessor(options, domain_info)

    # Initialize shares here so we can clean up in the preclean
    random_string = SQAhelpers.create_random_string(8)

    shared_dirs_1 = {
        "BadUser": {
            "share_user" : "Foo",
            "files": [
                {"name": "baduser.txt",
                 "contents" : SQAhelpers.ShanghaiChina + " : " + \
                     random_string}]},
        "BadPassword": {
            "share_user" : CrawlerEngineBase.default_share_user.username,
            "share_user_pswd" : \
                       CrawlerEngineBase.secondary_share_user.shrouded_password,
            "files": [
                {"name": "badpassword.txt",
                 "contents" : SQAhelpers.ShanghaiChina + " : " + \
                                random_string}]},
        "Good": {
            "files": [
                {"name": "good.txt",
                 "contents": SQAhelpers.ShanghaiChina + " : " + \
                               random_string}]}}
    shared_dirs_2 = {
        "Open": {
            "acl" : None,
            "share_user" : user,
            "share_user_pswd" :
                         CrawlerEngineBase.default_share_user.shrouded_password,
            "files": [
                {"name": "open.txt",
                 "contents" : SQAhelpers.ShanghaiChina + " : " + \
                                random_string}]}}

    shared_dirs_3 = {
        "User0": {
            "share_user" : CrawlerEngineBase.default_share_user.username,
            "share_user_pswd" :
                      CrawlerEngineBase.default_share_user.shrouded_password,
            "files": [
                {"name": "user0file.txt",
                 "contents" : SQAhelpers.ShanghaiChina + " : " + \
                                random_string}]},
        "User1": {
            "share_user" : CrawlerEngineBase.secondary_share_user.username,
            "share_user_pswd" :
                       CrawlerEngineBase.secondary_share_user.shrouded_password,
            "files": [
                {"name": "user1file.txt",
                 "contents" : SQAhelpers.MumbaiIndia + " : " + \
                                random_string}]}}

    shared_dirs_4 = {
        "ShareTests": {
            # TODO: Make this code not hurt my eyes -- it should be
            # more general and less hackish
            "subdir" : "Subdir",
            "shared_subdir": "Subdir",
            "files": [
                {"name": "nested.txt",
                 "contents" : SQAhelpers.ShanghaiChina + " : " + \
                                random_string}]}}

    if options.cleanup_only:
        print "Beginning cleanup.  No tests will be run"
    else:
        print "Cleaning up from previous tests"

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

    if ADTestHelpers.cleanup(domain_info, users=[user], print_errors=options.cleanup_only):
        exit_status = 1

    # Get rid of leftover shares
    for shared_dirs in [ shared_dirs_1, shared_dirs_2, shared_dirs_3, shared_dirs_4 ]:
        try:
            filetools.delete_shares(shared_dirs, domain_info.fileserver_ambassador)
        except:
            if options.cleanup_only:
                traceback.print_exc()
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

    print "Creating user"
    # use the share user password because the shrouded value of
    # this is known, and we'll need a shrouded password for
    # this user later in the tests
    adtools.create_user(user,
                        CrawlerEngineBase.default_share_user.password,
                        domain_info.domain_controller_ambassador,
                        domain_info.ad_domain,
                        ie_client_ambassador=domain_info.ie_client_ambassador)

    # Now, set up what we need for the test
    CrawlerEngineBase.setup_share_users( domain_info.domain_controller_ambassador,
                                  domain_info.crawler_ambassador,
                                  domain_info.ad_domain )

    print "Setting up ShareCrawler/Shareconnector"
    engine.set_up()

    share_server, share_user, share_password, share_shrouded_password = CrawlerEngineBase.get_share_credentials(domain_info.fileserver_fqdn)

    engine.configure_connection("ShareConnection",
                                share_server,
                                server_username=share_user,
                                server_password=share_password,
                                server_shrouded_password=share_shrouded_password)

    ie_client_host = domain_info.ie_client_fqdn.split(".")[0]
    ie_client_fqdn = domain_info.ie_client_fqdn

    # Connect to share as specific user -- this is tested as part of every
    # ad test, and doesn't need to be duplicated here

    print "Crawl share with incorrect user/password"
    shared_dirs = shared_dirs_1

    # The share connector ignores individual credentials on individual shares, because that's
    # not how it works.  So expect different things.
    if options.method == "shareconnector":
        expected_results = ['%s/Goodshare/good.txt' % (ie_client_fqdn),
                            '%s/BadUsershare/baduser.txt' % (ie_client_fqdn),
                            '%s/BadPasswordshare/badpassword.txt' % (ie_client_fqdn) ]
        error_count = 0
        ingested_count = 3
    else:
        expected_results = ['%s/Goodshare/good.txt' % (ie_client_host)]
        error_count = 2
        ingested_count = 1
    create_shares_crawl_search_and_cleanup(engine,
                                           domain_info,
                                           shared_dirs,
                                           random_string,
                                           expected_search_results=expected_results,
                                           scanned_files=1,
                                           ingested=ingested_count,
                                           # two shares are inaccessible
                                           errors=error_count,
                                           debug_info="bad user/password")

    print "Crawl with AssumeOpenShare"
    shared_dirs = shared_dirs_2

#TODO: this case is failing on 4.1.0 but it looks like it should pass...
#    if options.method == "shareconnector":
#        expected_results = ['%s/Openshare/open.txt' % (ie_client_fqdn)]
#       error_count = 0
#       ingested_count = 1
#    else:
#       expected_results = [ ]
#       error_count = 1
#       ingested_count = 0
#    create_shares_crawl_search_and_cleanup(engine,
#                                          domain_info,
#                                           shared_dirs,
#                                           random_string,
#                                           expected_search_results=expected_results,
#                                           ingested=ingested_count,
#                                           # user can't read share permissions
#                                           errors=error_count,
#                                           scanned_files=0,
#                                           debug_info="without open share " +
#                                                      "option")

    if options.method == "shareconnector":
        expected_results = ['%s/Openshare/open.txt' % (ie_client_fqdn)]
        error_count = 0
        ingested_count = 1
    else:
        expected_results = ['%s/Openshare/open.txt' % (ie_client_host)]
        error_count = 0
        ingested_count = 1
    create_shares_crawl_search_and_cleanup(engine,
                                           domain_info,
                                           shared_dirs,
                                           random_string,
                                           assume_open=True,
                                           ingested=ingested_count,
                                           errors=error_count,
                                           scanned_files=1,
                                           debug_info="with open share option",
                                           expected_search_results=expected_results)

    # This test is complete specific to sharecrawler
    if options.method == "sharecrawler":
        print "Crawl two shares on same machine as different users"
        shared_dirs = shared_dirs_3
        create_shares_crawl_search_and_cleanup(engine,
                                             domain_info,
                                             shared_dirs,
                                             random_string,
                                             ingested=2,
                                             errors=0,
                                             scanned_files=2,
                                             debug_info="different users",
                                             expected_search_results=[
                                               "%s/User0share/user0file.txt" % \
                                                 (ie_client_host),
                                               "%s/User1share/user1file.txt" % \
                                                 (ie_client_host)])

    print "Crawl with one shared folder under another shared folder."
    shared_dirs = shared_dirs_4

    if options.method == "shareconnector":
        expected_results = ["%s/ShareTestsshare/Subdir/nested.txt" % (ie_client_fqdn),
                             "%s/Subdirshare/nested.txt" % (ie_client_fqdn)]
        error_count = 0
        ingested_count = 2
    else:
        expected_results = [ "%s/ShareTestsshare/Subdir/nested.txt" % (ie_client_host),
                             "%s/Subdirshare/nested.txt" % (ie_client_host)]
        error_count = 0
        ingested_count = 2
    create_shares_crawl_search_and_cleanup(
                                    engine,
                                    domain_info,
                                    shared_dirs,
                                    random_string,
                                    ingested=ingested_count,
                                    errors=error_count,
                                    scanned_files=2,
                                    debug_info="shared subdirs",
                                    expected_search_results=expected_results)

    if options.method == "sharecrawler":
        # This test is completely sharecrawler specific
        print "Rebooting"
        output = domain_info.crawler_ambassador.run(
                                        "c:\\tools\\pstools\\psshutdown.exe -r")
        if not re.search("Connecting with psshutdown service on local system",
                     output):
            raise Exception("Error while shutting down windows system:%s" %
                          (output))
        print "Waiting for Windows machine to come back up"
        waitfor_notthere(domain_info.crawler_fqdn)
        waitfor_ambassador(domain_info.crawler_ambassador)
        # the Share Crawler may not start up as quickly as the ambassador,
        # so wait a bit before trying
        time.sleep(10)
        print "Verifying that sharecrawler started"
        from wintools import sharecrawlertools
        output = sharecrawlertools.send_sharecrawler_command(
                                                 "stop",
                                                 domain_info.crawler_ambassador)
        if output.rstrip() != "OK":
            raise Exception("Sharecrawler did not start on reboot:%s" % (output))

    print "All tests passed.  Cleaning up"

    # Tear down the "connection"
    engine.close_connection()

    # Tear down the test
    engine.tear_down()

    CrawlerEngineBase.teardown_share_users(domain_info.domain_controller_ambassador)
    CrawlerEngineBase.restore_license()

    exit_status = ADTestHelpers.cleanup(domain_info, users=[user])
    sys.exit(exit_status)
