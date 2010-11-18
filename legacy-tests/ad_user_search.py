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
import commands
import sys
import time
import urllib2
import math
import pprint
import traceback

sys.path.append("/usr/lib/metacarta")

import SearchCanary
import TestDocs
import ADTestHelpers
import SQAhelpers
import CrawlerEngineBase

from sqatools import appliance
from sqatools.sqautils import test_run

if "legacy" in sys.argv:
    from wintools import adtools
    CUDA_TEMPLATE = "check_user_doc_access %s@%s %s"
    SAMACCOUNT_TEMPLATE = "samAccountName: %s"
else:
    from wintools import adtools_md as adtools
    CUDA_TEMPLATE = "check_user_doc_access check %s@%s --uri %s"
    SAMACCOUNT_TEMPLATE = "sAMAccountName: %s"

from wintools import filetools
from wintools import sqa_domain_info
from wintools import ambassador_client

debug = False

# constants for referencing parts of user, group, doc, or acl structures
SID = 0
ENTITY_EXISTS_IN_AD = 1
EXPECTED_SEARCH_RESULTS = 2
GROUP_TYPE = 2
USER_MEMBERS = 3
GROUP_MEMBERS = 4
ACL_ENTRY_TYPE = 0
ACL_ENTRY_USER = 1
CONTENTS = 0
DOC_ID = 1
DOC_ACL = 2
DOC_PATH = 3

# other constants
A_GOOD_NUMBER = 300
A_LOT = 100


def check_ad_account_info(username, users, domain_info):
    """Confirms that the ad_account_info tools displays the expected
    user information for <username>.  Its test each of the three
    user identifies that the tool accepts: samAccountName, SID, and
    distinguished name."""

    # samAccountName
    cmd = "ad_account_info %s" % (username)
    output = commands.getoutput(cmd)
    if not re.search("distinguishedName: CN=%s" % (username),
                     output) or \
       not re.search("objectSid: %s" % (users[username][SID]),
                     output):
        raise Exception(("Did not get expected DN and SID for %s.\n%s\n%s") %
                           (username, cmd, output))

    # distinguished name
    cmd = "ad_account_info 'CN=%s,CN=Users,%s'" % \
             (username, adtools.get_dc_string(domain_info.ad_domain))
    output = commands.getoutput(cmd)
    if not re.search(SAMACCOUNT_TEMPLATE % (username),
                     output) or \
       not re.search("objectSid: %s" % (users[username][SID]),
                     output):
        raise Exception(("Did not get expected sAMAccountName and SID for " +
                           "%s.\n%s\n%s") % (username, cmd, output))

    # SID
    output = commands.getoutput("ad_account_info %s" %
                                  (users[username][SID]))
    if not re.search(SAMACCOUNT_TEMPLATE % (username),
                     output) or \
       not re.search("distinguishedName: CN=%s" % (username),
                     output):
        raise Exception(("Did not get expected sAMAccountName and DN for " +
                           "%s.\n%s\n%s") %
                           (username, cmd, output))


def check_group_membership_with_cuda(member, groupname, doc_path, domain_info):
    """Checks that the SID of <groupname> shows up in the cuda
    output for <member>.  The document in this case is irrelevant,
    but cuda requires one, so <doc_path> must exist on the appliance."""

    cmd = CUDA_TEMPLATE  % \
             (member, domain_info.ad_domain, doc_path)
    # find_document_metadata might fail temporarily if the document is in
    # a slice that is currently being merged.
    for retry in range(3):
        output = commands.getoutput(cmd)
        if not re.search("find_document_metadata failed", output):
            break
        time.sleep(300)
    else:
        raise Exception("check_user_doc_access failed after three retries:" +
                          "\n%s\n%s" % (cmd, output))

    if not re.search(adtools.get_sid(groupname), output):
        raise Exception(("cuda output should contain sid %s of group %s for " +
                           "user %s but does not:\n%s\n%s") %
                           (adtools.get_sid(groupname),
                            groupname, member, cmd, output))

def check_doc_access_with_cuda(username, doc, domain_info, has_access):
    """Using, cuda, checks that <username> does or does not have access
    to <doc>."""

    cmd = CUDA_TEMPLATE % \
             (username, domain_info.ad_domain, doc[DOC_PATH])
    # find_document_metadata might fail temporarily if the document is in
    # a slice that is currently being merged.
    for retry in range(3):
        output = commands.getoutput(cmd)
        if not re.search("find_document_metadata failed", output):
            break
        time.sleep(300)
    else:
        raise Exception("check_user_doc_access failed after three retries:" +
                          "\n%s\n%s" % (cmd, output))

    if (has_access == True) and not re.search("has access to", output):
        raise Exception(("cuda output should show that %s has access to " +
                           "document %d but does not:\n%s\n%s") %
                           (username, doc[DOC_ID], cmd, output))
    elif (has_access == False) and not re.search("does not have access to",
                                                 output):
        raise Exception(("cuda output should show that %s does not have " +
                           "access to document %d but does not:\n%s\n%s") %
                           (username, doc[DOC_ID], cmd, output))


def get_user_search_results(username, random_string, domain_info, fallback= False):
    """Search through the ambassador as the given username.  Return the
       sorted list of document numbers found."""
    password = username + "pswd"

    # Must attempt more than once because sometimes a merge operation will
    # result in a temporary duplicating of results.
    for merge_happens in range(2):
        # Search using soap headnode and json, we used to search the classic webui
        # but now it's going away so it'll just be these two, the matches variable
        # is left over, but in the interests of simplicity we're just setting it
        # to the soap results
        soap_matches = TestDocs.search_documents(
                                   method="soapheadnode-ambassador",
                                   keywords=[random_string],
                                   win_host=domain_info.ie_client_fqdn + ":" + \
                                              domain_info.ambassador_port,
                                   win_user=username,
                                   win_password=password,
                                   unique=0)
        soap_matches.sort()

        if TestDocs.JSON_AVAILABLE:
            json_matches = TestDocs.search_documents(
                                       method="jsonsearchapi-ambassador",
                                       keywords=[random_string],
                                       win_host=domain_info.ie_client_fqdn + ":" + \
                                                  domain_info.ambassador_port,
                                       win_user=username,
                                       win_password=password,
                                       unique=0)
            json_matches.sort()
            
            # if active directory fallback is enabled then
            # use a vbscript to simulate the user going to the appliance
            # using basic auth and then falling back
            if fallback:
                json_fallback_matches = TestDocs.search_documents(
                                           method="jsonsearchapi-ambassador",
                                           keywords=[random_string],
                                           win_host=domain_info.ie_client_fqdn + ":" + \
                                                     domain_info.ambassador_port,
                                           win_user=domain_info.realm_admin,
                                           win_password=domain_info.realm_admin_password,
                                           unique=0,
                                           fallback=True,
                                           fallback_user=username,
                                           fallback_password=password)
                                           
                json_fallback_matches.sort()
            else:
                json_fallback_matches = json_matches
        else:
            # So we don't have to special case the compare below.
            json_fallback_matches = json_matches = soap_matches

        # They are expected, and supposed, to be equal.  If so then we're
        # done here.
        if soap_matches == json_matches == json_fallback_matches:
            break

        def search_description():
            """Quick utility function to make the error reporting easier
            to read."""
            json = " JSON:\n" + pprint.pformat(json_matches)
            json = json + "JSON fallback:\n" + pprint.pformat(json_fallback_matches)
            return "user='%s' random='%s'\nSOAP: %s%s" % (
                    username, random_string,
                    pprint.pformat(soap_matches), json)

        # If they give a different number of results, then it might be
        # the fact that a merge just came online.
        if not (len(json_matches) == len(soap_matches) == len(json_fallback_matches)):
            print "SOAP headnode and JSON or JSON w/fallback have a different number of " + \
                  "results for " + search_description()
            print "Retrying in case a merge has just happened around %s" % (
                    time.asctime())
            time.sleep(5)
            continue

    else:
        # At this point, we've exhausted any other possibilities, things
        # are just wrong.
        raise Exception("SOAP headnode has returned different results" + \
                        " than JSON or JSON w/fallback for " + search_description())

    # get rid of the beginning of the path name so we can treat all the
    # document URLs as integers for easy comparision against the expected
    # documents in the callers.  Since we know the soap_matches are the same
    # as the ASCII headnode matches we can ignore them.
    matches = [match.split("/")[-1] for match in json_matches]

    # get rid of any non-numerical (i.e. canary) results and make
    # it a sorted list of integers
    for match in matches:
        if not re.search("^[0-9]+$", match):
            matches.remove(match)
    matches = map(int, matches)
    matches.sort()
    print "user %s got matches: %s" % (username, matches)
    return matches

def run_searches(domain_info, users, random_string, documents_destroyed=0, fallback=False):
    """Search through the ambassador as each user in the users list.  Check
       that the results returned match each user's expected results list or,
       if <documents_destroyed>, that each user gets an empty list."""
    for username in users.keys():
        if (not users[username][ENTITY_EXISTS_IN_AD]) or \
           (len(users[username]) <= EXPECTED_SEARCH_RESULTS):
            # don't search with a user who wasn't created/is deleted
            # or has not expected search results
            continue
        expected_ids_sort = users[username][EXPECTED_SEARCH_RESULTS]
        expected_ids_sort.sort()

        matches = get_user_search_results(username, random_string, domain_info, fallback=fallback)
        if documents_destroyed:
            assert [] == matches, \
               ("Documents should have been destroyed", matches)
        else:
            # Sometimes, the stoplist won't have finished updating before the
            # search is performed.
            # Thus, if a search is getting too many results, it should be
            # retried once, after a short sleep.
            if len(expected_ids_sort) < len(matches):
                time.sleep(5)
                matches = get_user_search_results(username,
                                                  random_string,
                                                  domain_info)
            assert expected_ids_sort == matches, \
                (username, expected_ids_sort, matches)

class CrawlerEngineIngestion:
    """A translation layer to make a crawler engine (either ShareCrawler or ShareConnector) look
       like the other ingestion methods."""
    def __init__(self, engine, method, domain_info):
        """Create an object wrapping the CrawlerEngineBase derivative
        instance <engine>, which corresponds to the name in <method>. 
        Operations will take place against the AD domain defined in
        <domain_info>."""
        self.engine = engine
        self.method = method
        self.domain_info = domain_info
        self.shared_dirs = {}
        self.num_of_files = 0
        self.num_unreadable_files = 0
        self.num_unscannable_files = 0
        # the ingestion method is what method will be used to ingest
        # the SearchCanary.  The SearchCanary is necessary
        # to determine when the documents are searchable (as opposed to
        # the sharecrawler being done pushing them to the duck)
        self.ingestion_method = "HTTP"

    def is_crawler_backed(self):
        """Returns true to signify that this ingestion method is backed by a crawler, so the
           results can be treated as "real" instead of faked up test docs with possible non-accsessible
           URLs."""
        return True
        
    def ingest_docs(self, docs, doc_acls, random_string, force_public=False,
                    debug_info=None):

        # see the comments above ADTestDocs.create_shares for explanation
        # of the shared_dirs structure

        # Make a publicly-accessible shared directory for all files without
        # share acls
        self.shared_dirs["dir0"] = {"acl":None, "files":[]}
        doc_id = 0
        self.num_of_files = 0
        self.num_unreadable_files = 0
        self.num_unscannable_files = 0
        for acl in doc_acls:
            self.num_of_files += 1
            # If the share ACL is [], the directory cannot be scanned, and
            # the file will not be scanned, ingested or fingerprinted.
            # The error count isn't kept until after scanning, though,
            # so the unscannable directories will not show up as errors.
            # If the file ACL is [], the file is unreadable and will not
            # be ingested or fingerprinted.  These will be included in
            # the error count.
            # If the ACL denies Domain Users, the ACL will be inaccessible,
            # regardless of any allow rules in the ACL.
            if acl.has_key("share") and \
               ((acl["share"] == []) or \
                ((acl["share"][0][0] == '-') and \
                 (acl["share"][0][1] == adtools.get_sid("Domain Users")))):
                self.num_unscannable_files += 1
            elif acl.has_key("file") and \
                 ((acl["file"] == []) or \
                  ((acl["file"][0][0] == '-') and \
                   (acl["file"][0][1] == adtools.get_sid("Domain Users")))):
                self.num_unreadable_files += 1
            # if there are no specific share permissions,
            dir_name = "dir0"
            if acl.has_key("share"):
                dir_name = "dir" + str(doc_id)
                self.shared_dirs[dir_name] = {"acl":acl["share"], "files":[]}
            if acl.has_key("dir"):
                # Testing the crawler's navigation of deep directories will
                # be left to a different test
                pass
            if acl.has_key("file"):
                self.shared_dirs[dir_name]["files"].append(
                    {"name":str(doc_id),
                     "acl":acl["file"],
                     "contents": "%s: %s: %d" %
                                   (SQAhelpers.LocationList[
                                         doc_id % len(SQAhelpers.LocationList)],
                                    random_string,
                                    doc_id)})
            else:
                self.shared_dirs[dir_name]["files"].append(
                    {"name":str(doc_id),
                     "acl":None,
                     "contents":"%s: %s: %d" % \
                                  (SQAhelpers.LocationList[
                                         doc_id % len(SQAhelpers.LocationList)],
                                   random_string,
                                   doc_id)})
            if self.method == "shareconnector":
                docs[doc_id][DOC_PATH] = \
                                  "file://///%s/%sshare/%d" % \
                                     (self.domain_info.fileserver_fqdn,
                                      dir_name,
                                      doc_id)
            else:
                docs[doc_id][DOC_PATH] = \
                                  "file://///%s/%sshare/%d" % \
                                     (self.domain_info.fileserver_fqdn.split(".")[0],
                                      dir_name,
                                      doc_id)
            doc_id += 1

        CrawlerEngineBase.create_shares(self.shared_dirs,
                                        self.domain_info.fileserver_ambassador)

        # create configuration file for the share crawler, create
        # the shares mentioned in the config file, and then crawl
        CrawlerEngineBase.configure_job(self.engine,
                             "ShareJob",
                             self.shared_dirs,
                             force_public=force_public,
                             fileserver_fqdn=self.domain_info.fileserver_fqdn)

        self.engine.crawl_until_done()

        if force_public:
            # If force_public, all of these errors will be retried, causing
            # two errors for every problem file
            errors = 2 * (self.num_unreadable_files +
                          self.num_unscannable_files)
        else:
            # The unreadable files will be retried, causing two errors for
            # every problem file
            errors = (2 * self.num_unreadable_files) + \
                     self.num_unscannable_files
        self.engine.check_result_log(debug_info,
                                directories=0,
                                files=(self.num_of_files -
                                       self.num_unscannable_files),
                                btc=0,
                                ingested=(self.num_of_files -
                                          (self.num_unreadable_files +
                                           self.num_unscannable_files)),
                                deleted=0,
                                dirs_filtered=0,
                                files_filtered=0,
                                bin_filtered=0,
                                btc_filtered=0,
                                fingerprinted=(self.num_of_files -
                                               (self.num_unreadable_files +
                                                self.num_unscannable_files)),
                                errors=errors)


    def destroy_docs(self):
        print "Destroying documents"
        if len(self.shared_dirs.keys()) > 0:
            self.engine.remove_job()
            filetools.delete_shares(self.shared_dirs,
                                    self.domain_info.crawler_ambassador)
            self.shared_dirs = {}
            self.engine.check_result_log(
                                         context="deleting docs",
                                         directories=0,
                                         files=0,
                                         btc=0,
                                         ingested=0,
                                         fingerprinted=0,
                                         errors=0,
                                         deleted=(self.num_of_files -
                                                  (self.num_unreadable_files +
                                                   self.num_unscannable_files)))

class TestDocsIngestion:
    """A translation layer to make TestDocs look like the other ingestion methods."""
    def __init__(self, ingestion_method):
        self.ingestion_method = ingestion_method
        self.ingested_documents = []

    def is_crawler_backed(self):
        """Returns false to signify that this ingestion method is NOT backed by a crawler and
           the URLs and ACLs are test creations and might in fact be illusions."""
        return False

    def ingest_docs(self, docs, doc_acls, random_string, force_public=0,
                    debug_info=None):
        print "Ingesting documents using %s" % self.ingestion_method
        for doc in docs:
            # the arguments to ingest_document are contents, name, acl, method
            # The name of each document is the id string
            self.ingested_documents.append(str(doc[DOC_ID]))
            # make sure there is not an existing collection with that name
            TestDocs.delete_document(str(doc[DOC_ID]), self.ingestion_method)
            TestDocs.ingest_document(doc[CONTENTS],
                                     str(doc[DOC_ID]),
                                     xml_acl=doc[DOC_ACL],
                                     method=self.ingestion_method)

    def destroy_docs(self):
        print "Destroying documents"
        for doc_id in self.ingested_documents:
            TestDocs.delete_document(doc_id, self.ingestion_method)
        self.ingested_documents = []

def create_documents(domain_info, documents, doc_acls, random_string,
                     ingestion_obj, force_public=False, debug_info=None):
    """Creates documents, gets them onto the duck using the ingestion method
       of <ingestion_obj> and waits until the SearchCanary shows up in a
       webui search.  Returns the id of the canary, which is needed to delete
       it later."""

    ingestion_obj.ingest_docs(documents, doc_acls, random_string,
                              force_public=force_public, debug_info=debug_info)

    # the canary ensures that all the documents are fully ingested
    # and will now show up in a search for them
    canaryID = None
    print "Waiting for the Search Canary"
    canaryID = SearchCanary.create_canary(
                             ingestion_obj.ingestion_method,
                             "jsonsearchapi-ambassador",
                             timeout=32400,
                             search_opts={
                               "win_host":domain_info.ie_client_fqdn + ":" + \
                                            domain_info.ambassador_port,
                               "win_user":domain_info.realm_admin,
                               "win_password":domain_info.realm_admin_password})
    return canaryID

def delete_documents(domain_info, users, random_string, ingestion_obj,
                     canaryID):
    """Deletes all of the documents from the duck and destroys the search canary."""
    ingestion_obj.destroy_docs()

    # wait until the Canary no longer shows up in a web search -- then
    # we know that all the documents are now off the duck
    SearchCanary.destroy_canary(
                             canaryID,
                             ingestion_obj.ingestion_method,
                             "jsonsearchapi-ambassador",
                             timeout=32400,
                             search_opts={
                               "win_host":domain_info.ie_client_fqdn + ":" + \
                                            domain_info.ambassador_port,
                               "win_user":domain_info.realm_admin,
                               "win_password":domain_info.realm_admin_password})

    # Make sure that the documents got deleted
    run_searches(domain_info, users, random_string, documents_destroyed=1)

def main():
    """Main test code."""
    # dictionary of all users that we will be creating
    # these users will be performing searches
    # user tuple is (sid, exists in AD, expected search results)
    users = {
        "basicUser1"    : ["", 0, [0,1,2,3,4,6,7,9,10]],
        "basicUser2"    : ["", 0, [0,1,2,3,4,6,7,9,11,12]],
        "basicUser3"    : ["", 0, [0,1,2,3,4,6,7,8,13,14,15]],
        "validUser"     : ["", 0, [0,1,2,3,4,19,20,21,22,36]],
        "circleUser"    : ["", 0, [0,1,2,3,4,23]],
        "triUser"       : ["", 0, [0,1,2,3,4,24]],
        "quadUser"      : ["", 0, [0,1,2,3,4,25]],
        "nestedUser"    : ["", 0, [0,1,2,3,4,28,29]],
        "nonNestedUser" : ["", 0, [0,1,2,3,4,26,27]],
        "flatUser"      : ["", 0, [0,1,2,3,4,30,31]],
        "nonDstGrpUser" : ["", 0, [0,1,2,3,4,32,33]],
        "dstGrpUser1"   : ["", 0, [0,1,2,3,4,34]],
        "dstGrpUser2"   : ["", 0, [0,1,2,3,4,34]],
        "dstGrpUser3"   : ["", 0, [0,1,2,3,4,34]],
        "dstGrpUser4"   : ["", 0, [0,1,2,3,4,34]],
        "dstGrpUser5"   : ["", 0, [0,1,2,3,4,34]],
        "dstGrpUser6"   : ["", 0, [0,1,2,3,4,34]],
        "largeACLUser1" : ["", 0, [0,1,2,3,4,35,37]],
        "largeACLUser2" : ["", 0, [0,1,2,3,4,35]],
        "largeACLUser3" : ["", 0, [0,1,2,3,4,36]],
        "nonTransUser"  : ["", 0, [0,1,2,3,4,41,42,43]],
        "transUser1"    : ["", 0, [0,1,2,3,4,38,39,43]],
        "transUser2"    : ["", 0, [0,1,2,3,4,39,41,43]],
        "transUser3"    : ["", 0, [0,1,2,3,4,38,39,40]],
        "transUserA"    : ["", 0, [0,1,2,3,4,44,45,46]],
        "unsecPrimUser" : ["", 0, [0,1,2,3,4,47]],
        "glsecPrimUser" : ["", 0, [0,1,2,3,4,48]],
        "staleUserSID"  : ["", 0, []]
    }

    # dictionary of all groups that we will be creating
    # group tuple is (sid, exists in AD, type, user members, group members)
    groups = {
      "groupA"        : ["", 0, "unsec", ["basicUser1", "basicUser2"], []],
      "groupB"        : ["", 0, "glsec", ["basicUser2"], []],
      "groupC"        : ["", 0, "dlsec", [], ["groupB"]],
      "circleGroupA"  : ["", 0, "dlsec", ["circleUser"], ["circleGroupB"]],
      "circleGroupB"  : ["", 0, "dlsec", [], ["circleGroupA"]],
      "triangleGroupA": ["", 0, "glsec", ["triUser"], ["triangleGroupB"]],
      "triangleGroupB": ["", 0, "glsec", [], ["triangleGroupC"]],
      "triangleGroupC": ["", 0, "glsec", [], ["triangleGroupA"]],
      "quadGroupA"    : ["", 0, "unsec", ["quadUser"], ["quadGroupB"]],
      "quadGroupB"    : ["", 0, "unsec", [], ["quadGroupC"]],
      "quadGroupC"    : ["", 0, "unsec", [], ["quadGroupD"]],
      "quadGroupD"    : ["", 0, "unsec", [], ["quadGroupA"]],
      "nestedGroup0"  : ["", 0, "glsec", ["nestedUser"], []],
      "gsec1"         : ["", 0, "glsec", ["dstGrpUser1"], []],
      "gsec2"         : ["", 0, "glsec", ["dstGrpUser2"], []],
      "gsec3"         : ["", 0, "glsec", ["dstGrpUser3"], []],
      "dsec4"         : ["", 0, "dlsec", [], ["ldist", "gdist", "udist"]],
      "ldist"         : ["", 0, "dldst", ["dstGrpUser4"], ["gsec1"]],
      "gdist"         : ["", 0, "gldst", ["dstGrpUser5"], ["gsec2"]],
      "udist"         : ["", 0, "undst", ["dstGrpUser6"], ["gsec3"]],
      "unsec1"        : ["", 0, "unsec", ["transUser1"], ["glsec1"]],
      "dlsec1"        : ["", 0, "dlsec", ["transUser2"], ["unsec1", "glsec1"]],
      "glsec1"        : ["", 0, "glsec", ["transUser3"], []],
      "glsec2"        : ["", 0, "glsec", [], ["glsec1"]],
      "dlsecA"        : ["", 0, "dlsec", ["transUserA"], []],
      "glsecB"        : ["", 0, "glsec", ["transUserA"], []],
      "unsecC"        : ["", 0, "unsec", ["transUserA"], []],
      "unsecD"        : ["", 0, "unsec", [], ["unsecC"]],
      "glsecPrime"    : ["", 0, "glsec", [], []],
      "unsecPrime"    : ["", 0, "unsec", [], []]
    }

    tmp_list0 = [['+', "largeACLUser1"]]
    tmp_list0.extend([['-', "validUser"] for i in range(0, A_LOT)])
    tmp_list0.append(['+', "largeACLUser2"])
    tmp_list1 = [['+', "largeACLUser3"], ['-', "largeACLUser1"]]
    tmp_list1.extend([['+', "validUser"] for i in range(0, A_LOT)])
    tmp_list1.append(['-', "largeACLUser2"])

    # Each acl is a dictionary of the form
    #    {scope: [('+'/'-', name), ... ]}
    # scope is "file", "share", or "dir"
    # Each name must be in the entities_in_acls dictionary.
    # An empty dictionary means the document will be public.
    doc_acls = [
        # document0 no acls -- ie public
        {},
        # document1
        {"file": [['+', "everyone"]]},
        # document2
        {"file": [['+', "authenticated_users"]]},
        # document3
        {"file": [['+', "networkUsers"]]},
        # document4
        {"file": [['+', "domainUsers"]]},
        # document5
        {"file": [['-', "domainUsers"]]},
        # document6
        {"file": [['+', "basicUser1"],
                  ['+', "basicUser2"],
                  ['+', "basicUser3"]]},
        # document7
        {"file": [['+', "everyone"]],
         "share": [['+', "basicUser1"],
                   ['+', "basicUser2"],
                   ['+', "basicUser3"]]},
        # document8
        {"file": [['-', "basicUser1"],
                  ['+', "basicUser2"],
                  ['+', "basicUser3"]],
         "share": [['+', "basicUser1"],
                   ['-', "basicUser2"],
                   ['+', "basicUser3"]]},
        # document9
        {"file": [['+', "groupA"]]},
        # document10
        {"file": [['+', "groupA"], ['-', "basicUser2"]]},
        # document11
        {"file": [['+', "groupC"]]},
        # document12
        {"file": [['+', "groupA"]],
         "share": [['+', "groupC"]]},
        # document13
        {"file": [['+', "basicUser2"], ['+', "basicUser3"], ['-', "groupB"]]},
        # document14
        {"file": [['+', "groupB"], ['+', "basicUser3"], ['-', "basicUser2"]]},
        # document15
        {"file": [['+', "basicUser2"], ['+', "basicUser3"], ['-', "groupC"]]},
        # document16
        {"file": [['+', "basicUser1"]],
         "share": []},
        # document17
        {"file": [],
         "share": [['+', "basicUser1"]]},
        # document18
        {"file": [],
         "share": []},
        # document19
        {"file": [['+', "staleUserSID"], ['+', "validUser"]]},
        # document20 F(-stale, +validUser)
        {"file": [['-', "staleUserSID"], ['+', "validUser"]]},
        # document21
        {"file": [['+', "invalidSID"], ['+', "validUser"]]},
        # document22
        {"file": [['-', "invalidSID"], ['+', "validUser"]]},
        # document23
        {"file": [['+', "circleGroupA"]]},
        # document24
        {"file": [['+', "triangleGroupA"]]},
        # document25
        {"file": [['+', "quadGroupA"]]},
        # document26 F(+nonNestedUser, -outer nested group)
        {"file": [['+', "nonNestedUser"],
                  ['-', "nestedGroup" + str(A_GOOD_NUMBER-1)]]},
        # document27 F(+nonNestedUser, -middle nested group)
        {"file":
          [['+', "nonNestedUser"],
           ['-', "nestedGroup" + str(int(math.floor(A_GOOD_NUMBER/2)))]]},
        # document28 F(+outer nested group)
        {"file": [['+', "nestedGroup" + str(A_GOOD_NUMBER - 1)]]},
        # document29 F(+middle nested group)
        {"file":
          [['+', "nestedGroup" + str(int(math.floor(A_GOOD_NUMBER/2)))]]},
        # document30
        {"file": [['+', "flatUser"]]},
        # document31 F(+all flat groups)
        {"file":
          [['+', "flatGroup" + str(i)] for i in range(0, A_GOOD_NUMBER)]},
        # document32
        {"file": [['+', "ldist"],
                  ['+', "gdist"],
                  ['+', "udist"],
                  ['+', "nonDstGrpUser"]]},
        # document33
        {"file": [['+', "dsec4"], ['+', "nonDstGrpUser"]]},
        # document34 F(+all dstGrpUsers)
        {"file": [['+', "dstGrpUser" + str(i)] for i in range(1,7)]},
        # document35 F(+largeACLUser1, a lot of deny entries, +largeACLUser2)
        {"file": tmp_list0},
        # document36 F(+largeACLUser3, -largeACLUser1,
        #              a lot of allow entries -largeACLUser2)
        {"file": tmp_list1},
        # document37 F(+largeACLUser1), a lot of D entries
        {"file": [['+', "largeACLUser1"]],
         "dir": [['+', "largeACLUser1"] for i in range(0,A_LOT)]},
        # document38
        {"file": [['+', "unsec1"]]},
        # document39
        {"file": [['+', "dlsec1"]]},
        # document40
        {"file": [['+', "glsec2"]]},
        # document41
        {"file": [['+', "transUser1"],
                  ['+', "transUser2"],
                  ['+', "transUser3"],
                  ['+', "nonTransUser"],
                  ['-', "unsec1"]]},
        # document42
        {"file": [['+', "transUser1"],
                  ['+', "transUser2"],
                  ['+', "transUser3"],
                  ['+', "nonTransUser"],
                  ['-', "dlsec1"]]},
        # document43
        {"file": [['+', "transUser1"],
                  ['+', "transUser2"],
                  ['+', "transUser3"],
                  ['+', "nonTransUser"],
                  ['-', "glsec2"]]},
        # document44 F(+dlsecA)
        {"file": [['+', "dlsecA"]]},
        # document45 F(+glsecB)
        {"file": [['+', "glsecB"]]},
        # document46 F(+unsecD)
        {"file": [['+', "unsecD"]]},
        # document47 F(+unsecPrime)
        {"file": [['+', "unsecPrime"]]},
        # document48 F(+glsecPrime)
        {"file": [['+', "glsecPrime"]]},
        # document49 F(+Users)
        {"file": [['+', "users"]]}
    ]

    # process the command line arguments
    (options, args) = ADTestHelpers.process_ad_arguments(sys.argv)

    debug = "debug" in args

    domain_info = ADTestHelpers.setup_ad_tests(options.domain_id)
    engine = CrawlerEngineBase.build_engine_accessor(options, domain_info)

    if options.cleanup_only:
        print "Beginning cleanup.  No tests will be run"
        print_errors = True
    else:
        print "Cleaning up from previous tests"
        print_errors = False
    group_list = groups.keys()
    group_list.extend(["flatGroup" + str(i) for
        i in range(0,A_GOOD_NUMBER)])
    group_list.extend(["nestedGroup" + str(i) for
        i in range(0, A_GOOD_NUMBER)])

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

    if ADTestHelpers.cleanup(domain_info,
                             users=users.keys(),
                             groups=group_list,
                             print_errors=print_errors):
        exit_status = 1

    if options.cleanup_only:
        sys.exit(exit_status)

    print "Setting up license."
    CrawlerEngineBase.standard_ad_test_license()

    # active_directory_tool join, accept default realm and
    # Administrator as user and enter Administrator password.
    print "Joining the AD domain."
    adtools.join_ad_with_defaults(domain_info.realm_admin_password)
    adtools.add_to_group(SQAhelpers.reverse_resolve().split('.')[0],
                         adtools.CompatibilityGroup,
                         domain_info.domain_controller_ambassador)

    # The secure WebServices aren't set to active_directory auth by the join
    # operation, so we must manually set their auth.
    if TestDocs.JSON_AVAILABLE:
        for servicename in ("secure-json-search", "secure-kml-search"):
            appliance.spcall(["auth_control", "auth", servicename, "active_directory"])
            
    if "legacy" not in sys.argv:
        # Enable cuda for later in the test
        appliance.spcall(["check_user_doc_access","enabledebug"])
    
    # create a random 8-character string to use in searches
    random_string = SQAhelpers.create_random_string(8)

    print "Creating users"
    for username in users.keys():
        # It would be nice to use ldif here, but you can't set
        # the passwords
        adtools.create_user(
                          username,
                          username + "pswd",
                          domain_info.domain_controller_ambassador,
                          domain_info.ad_domain,
                          ie_client_ambassador=domain_info.ie_client_ambassador)
        # mark that it has been created
        users[username][ENTITY_EXISTS_IN_AD] = 1
        # set SID for use in document acls
        output = adtools.get_sid(username)
        users[username][SID] = output
        
    print "Creating special users"
    special_users = {}
    username = "@StartsName"
    adtools.create_user(username, username + "pswd",
                        domain_info.domain_controller_ambassador,
                        domain_info.ad_domain,
                        confirm_user=False)
    special_users[username] = [adtools.get_sid(username), 1, []]

    print "Creating groups"
    for groupname in groups.keys():
        # create_group takes the group name and group type string
        # It would be nice to use ldif here, but we can't because
        # of circular groups
        adtools.create_group(groupname,
                             groups[groupname][GROUP_TYPE],
                             domain_info.ad_domain,
                             domain_info.domain_controller_ambassador)
        # mark that it has been created
        groups[groupname][ENTITY_EXISTS_IN_AD] = 1
        # set SID for use in document acls
        groups[groupname][SID] = adtools.get_sid(groupname)

    print "Populating groups"
    # this can't be combined with the above loop because there are
    # circular memberships
    for groupname in groups.keys():
        for member in groups[groupname][USER_MEMBERS]:
            adtools.add_to_group(member, groupname,
                                 domain_info.domain_controller_ambassador)
        for member in groups[groupname][GROUP_MEMBERS]:
            adtools.add_to_group(member, groupname,
                                 domain_info.domain_controller_ambassador)

    # change the primary groups of unsecPrimUser and glsecPrimUser
    adtools.change_primary_group("unsecPrimUser", "unsecPrime",
                                 domain_info.domain_controller_ambassador)
    adtools.change_primary_group("glsecPrimUser", "glsecPrime",
                                 domain_info.domain_controller_ambassador)

    # create and populate a good number of nested groups
    filename = "/tmp/groups.ldf"
    ldif_file = open(filename, 'w')
    for i in range(1, A_GOOD_NUMBER):
        try:
            adtools.delete_group("nestedGroup" + str(i),
                                 domain_info.domain_controller_ambassador,
                 domain=domain_info.ad_domain)
        except Exception,e:
            # the most likely error is that the group doesn't exist,
            # which is something we can safely ignore.  If there
            # really was an error in deleting the group, then group
            # creation will fail
            if debug:
                print "Warning: Error removing group nestedGroup%d: %s" % (i,str(e))
                traceback.print_exc()
            pass
        try:
            adtools.add_ldif_group_entry_to_file(ldif_file,
                                                 "nestedGroup" + str(i),
                                                 "nestedGroup" + str(i - 1),
                                                 domain_info.ad_domain)
        except:
            ldif_file.close()
            raise
    ldif_file.close()
    # import (create) all of the groups
    adtools.import_ldif_file(filename, domain_info.domain_controller_ambassador)

    # check that each group was created properly, and add it to the groups
    # list
    ldap_groups = adtools.query_groups_in_ldap("nestedGroup")
    for i in range(1, A_GOOD_NUMBER):
        groupname = "nestedGroup" + str(i)
        membername = "nestedGroup" + str(i - 1)
        if not (membername in ldap_groups[groupname]["members"]):
            raise Exception( "%s should be in %s but isn't!" %
                               (membername, groupname))
        groups[groupname] =  \
            [ldap_groups[groupname]["sid"], 1, "glsec", [], [membername]]

    # create and populate a good number of flat groups
    filename = "/tmp/groups.ldf"
    ldif_file = open(filename, 'w')
    for i in range(0, A_GOOD_NUMBER):
        try:
            adtools.delete_group("flatGroup" + str(i),
                                 domain_info.domain_controller_ambassador,
                 domain=domain_info.ad_domain)
        except Exception,e:
            # the most likely error is that the group doesn't exist,
            # which is something we can safely ignore.  If there
            # really was an error in deleting the group, then group
            # creation will fail
            if debug:
                print "Warn: Exception removing group: %s" % str(e)
            pass
        try:
            adtools.add_ldif_group_entry_to_file(ldif_file,
                                                 "flatGroup" + str(i),
                                                 "flatUser",
                                                 domain_info.ad_domain)
        except:
            ldif_file.close()
            raise
    ldif_file.close()
    # import (create) all of the groups
    adtools.import_ldif_file(filename, domain_info.domain_controller_ambassador)

    # check that each group was created properly, and add it to the groups list
    ldap_groups = adtools.query_groups_in_ldap("flatGroup")
    for i in range(0, A_GOOD_NUMBER):
        groupname = "flatGroup" + str(i)
        membername = "flatUser"
        if not (membername in ldap_groups[groupname]["members"]):
            raise Exception("%s should be in %s but isn't!" %
                              (membername, groupname))
        groups[groupname] =  \
            [ldap_groups[groupname]["sid"], 1, "glsec", [], [membername]]

    # dictionary of all entities that might be found on acls --
    # the search users, groups, existing users, a hack for
    # creating a blank acl, an invalid SID, etc.
    entities_in_acls = {}
    entities_in_acls.update(users)
    entities_in_acls.update(groups)
    entities_in_acls["everyone"] = [adtools.EveryoneSID, 1]
    entities_in_acls["authenticated_users"] = [adtools.AuthenticatedUsersSID, 1]
    entities_in_acls["networkUsers"] = [adtools.NetworkUsersSID, 1]
    entities_in_acls["users"] = [adtools.UsersSID, 1]
    entities_in_acls["invalidSID"] = [adtools.InvalidSID, 0]
    entities_in_acls["domainUsers"] = [adtools.get_sid("Domain Users"), 1]

    # replace all of the user/group names with SIDs
    for acl in doc_acls:
        for scope in acl.keys():
            for entry in acl[scope]:
                entry[ACL_ENTRY_USER] = \
                                    entities_in_acls[entry[ACL_ENTRY_USER]][SID]

    # put together the "documents" in the TestDocs library format
    documents = []
    doc_id = 0
    for acl in doc_acls:
        # add the SID of each object in each acl
        # list with the appropriate surrounding tags
        acl_string = ''
        for scope in acl.keys():
            # the key is the name of the scope
            acl_string += '<acl scope="%s">' % scope
            # the value is the list of acl entries
            for entry in acl[scope]:
                # deal with blank (i.e. no access) acls
                if entry == []:
                    acl_string += '<allow></allow>'
                else:
                    # Put the allow or deny tags around the SID string
                    if entry[ACL_ENTRY_TYPE] == '+':
                        acl_string += '<allow>' + entry[ACL_ENTRY_USER] + \
                            '</allow>'
                    elif entry[ACL_ENTRY_TYPE] == '-':
                        acl_string += '<deny>' + entry[ACL_ENTRY_USER] + \
                            '</deny>'
                    else:
                        raise Exception("Could not process doc %d with acl %s" %
                                          (doc_id, str(acl)))
            acl_string += '</acl>'
        # if there were any acls, put the document designator around them
        # (a blank acl_string means the document is public)
        if acl_string:
            acl_string = '<document-acl>' + acl_string + '</document-acl>'
        else:
            acl_string = None
        # a "document" consists of (contents, id number, acl)
        documents.append([
            # The document consists of a single line --
            #    "location: randomstring: id"
            # Locations rotate through those listed above
            "%s: %s: %d\n" % \
               (SQAhelpers.LocationList[doc_id % len(SQAhelpers.LocationList)],
                random_string,
                doc_id),
            doc_id,
            acl_string,
            # document path will be filled in later
            ''])
        doc_id += 1

    # Make the stale user is actually stale
    adtools.delete_user("staleUserSID",
                        domain_info.domain_controller_ambassador)
    users["staleUserSID"][ENTITY_EXISTS_IN_AD] = 0

    # instantiate the ingestion objects.  The ingestion objects each
    # have methods that will ingest/destroy documents using that
    # particular ingestion type and can also report on whether they are
    # crawler backed or not.
    ingestion_objs = [TestDocsIngestion("HTTP"),
                      CrawlerEngineIngestion(engine,options.method,domain_info)]

    # Now, set up what we need for the test
    CrawlerEngineBase.setup_share_users( domain_info.domain_controller_ambassador,
                                  domain_info.crawler_ambassador,
                                  domain_info.ad_domain )

    # instantiate the sharecrawler ingestion object, and set
    # up the sharecrawler
    engine.set_up()

    share_server, share_user, share_password, share_shrouded_password = CrawlerEngineBase.get_share_credentials(domain_info.fileserver_fqdn)

    engine.configure_connection("ShareConnection",
                                share_server,
                                server_username=share_user,
                                server_password=share_password,
                                server_shrouded_password=share_shrouded_password)

    # Loop through each ingestion object, getting the documents into the system, and then
    # confirming that searches work as expected.
    # Crawler-backed ingestion objects are also confirmed with the cuda utility.
    for ingestion_obj in ingestion_objs:
        # When ingesting docs using a crawler, test the "Can I See It" tool
        # and the SID tool since these ingestions are backed by actual files
        # from within the Windows security model and not things dummied up
        # by the test.
        if ingestion_obj.is_crawler_backed():
            docs_with_broken_acls = [5, 16, 17, 18]
            docs_for_cuda_testing = filter(
                       lambda doc: not docs_with_broken_acls.count(doc[DOC_ID]),
                       documents)
            canaryID = create_documents(domain_info,
                                        documents,
                                        doc_acls,
                                        random_string,
                                        ingestion_obj,
                                        force_public=True,
                                        debug_info="force public/cuda testing")
            print "Testing ForcePublic using cuda"
            for doc in docs_for_cuda_testing:
                for username in users.keys():
                    if users[username][ENTITY_EXISTS_IN_AD] == 1:
                        check_doc_access_with_cuda(username,
                                                   doc,
                                                   domain_info,
                                                   has_access=True)
            delete_documents(domain_info, users, random_string, ingestion_obj,
                             canaryID)
            canaryID = create_documents(domain_info,
                                        documents,
                                        doc_acls,
                                        random_string,
                                        ingestion_obj,
                                        debug_info="cuda testing")
            print "Testing cuda with non-public acls"
            for groupname in groups.keys():
                for member in groups[groupname][GROUP_MEMBERS]:
                    if users.has_key(member) and \
                       users[member][ENTITY_EXISTS_IN_AD] == 1:
                        check_group_membership_with_cuda(
                                                      member,
                                                      groupname,
                                                      documents[0][DOC_PATH],
                                                      domain_info)

            for doc in docs_for_cuda_testing:
                for username in users.keys():
                    if users[username][ENTITY_EXISTS_IN_AD] == 1:
                        has_access = doc[DOC_ID] in \
                                       users[username][EXPECTED_SEARCH_RESULTS]
                        check_doc_access_with_cuda(username,
                                                   doc,
                                                   domain_info,
                                                   has_access)
            for username in users.keys():
                if users[username][ENTITY_EXISTS_IN_AD] == 1:
                    check_ad_account_info(username, users, domain_info)
                    
            run_searches(domain_info, special_users, random_string)
                                                            
            if "legacy" in sys.argv:
                print "Test for RT20219 make sure that we warn if there is no authority"
                test_run(".*",".*","auth_control","auth","search-web-ui","none")
                for username in users.keys():
                    if users[username][ENTITY_EXISTS_IN_AD] == 1:
                        for doc in docs_for_cuda_testing:
                            test_run(r"WARNING\: No user or group sids returned\, this indicates that active_directory is not enabled on search\-web\-ui",
                            None,
                            "check_user_doc_access",
                            username+"@"+domain_info.ad_domain,
                            doc[DOC_PATH])
                            break
                        break
                test_run(".*",".*","auth_control","auth","search-web-ui","active_directory")
        else:
            canaryID = create_documents(domain_info,
                                        documents,
                                        doc_acls,
                                        random_string,
                                        ingestion_obj,
                                        debug_info="Main crawl")
        print "Searching with tokens, computer account in compatibility group"
        run_searches(domain_info, users, random_string)

        print "Searching with fallback enabled"                      
        output = commands.getoutput("auth_control auth secure-json-search active_directory with_fallback")
        print output
        run_searches(domain_info, users, random_string, fallback=True)
        output = commands.getoutput("auth_control auth secure-json-search active_directory")
        print output

        print "Searching using treewalk instead of tokens"
        adtools.alter_ad_configfile_treewalk()
        run_searches(domain_info, users, random_string)
        adtools.restore_ad_configfile()

        print "Searching with tokens, computer account in RAS group"
        #### TODO: Put the computer account in the RAS group
        run_searches(domain_info, users, random_string)

        delete_documents(domain_info, users, random_string, ingestion_obj,
                         canaryID)

    # swap the file and share acl scopes and search again
    print "Searching with file and share acl scopes reversed"
    # this changes the docs used in HTTP and collection ingestion
    for doc in documents:
        if doc[DOC_ACL]:
            doc[DOC_ACL] = re.sub("share", "temp", doc[DOC_ACL])
            doc[DOC_ACL] = re.sub("file", "share", doc[DOC_ACL])
            doc[DOC_ACL] = re.sub("temp", "file", doc[DOC_ACL])
    # this changes the templates used for creating the windows files
    reversed_doc_acls = []
    for doc_acl in doc_acls:
        reversed_doc_acl = {}
        if doc_acl.has_key("share"):
            reversed_doc_acl["file"] = doc_acl["share"]
        if doc_acl.has_key("file"):
            reversed_doc_acl["share"] = doc_acl["file"]
        reversed_doc_acls.append(reversed_doc_acl)
    doc_acls = reversed_doc_acls

    for ingestion_obj in ingestion_objs:
        canaryID = create_documents(domain_info,
                                    documents,
                                    doc_acls,
                                    random_string,
                                    ingestion_obj,
                                    debug_info="Reversed ACLs")
        run_searches(domain_info, users, random_string)
        delete_documents(domain_info, users, random_string, ingestion_obj,
                         canaryID)

    print "Searching with defective users"

    print "Searching with user with no principal name"
    adtools.create_user("hasNoPrinc",
                        "pswd",
                        domain_info.domain_controller_ambassador,
                        domain_info.ad_domain,
                        ie_client_ambassador=domain_info.ie_client_ambassador,
                        no_user_princ=1)

    users["hasNoPrinc"] = [adtools.get_sid("hasNoPrinc"), 1]

    print "Searching with user where cn != principal name"
    adtools.create_user("hasDiffPrinc",
                        "pswd",
                        domain_info.domain_controller_ambassador,
                        domain_info.ad_domain,
                        ie_client_ambassador=domain_info.ie_client_ambassador,
                        user_princ="diffPrinc")

    users["hasDiffPrinc"] = [adtools.get_sid("hasDiffPrinc"), 1]

    print "Searching with user where cn != sAMAccountName"
    # Make sure this user doesn't exist before calling create_user,
    # because create_user will try to delete it using the CN, and
    # it has to be deleted using the sAMAccountName
    try:
        adtools.delete_user("diffSAM", domain_info.domain_controller_ambassador)
    except:
        # ignore errors -- it probably doesn't exist
        pass
    adtools.create_user("hasDiffSAM",
                        "pswd",
                        domain_info.domain_controller_ambassador,
                        domain_info.ad_domain,
                        ie_client_ambassador=domain_info.ie_client_ambassador)
    adtools.change_samaccountname("hasDiffSAM",
                                  "diffSAM",
                                  domain_info.ad_domain,
                                  domain_info.realm_admin,
                                  domain_info.realm_admin_password)
    try:
        adtools.confirm_user_with_search("hasDiffSAM"+"@"+domain_info.ad_domain,
                                    "pswd",
                                    domain_info.ie_client_ambassador,
                                    altuser="diffSAM"+"@"+domain_info.ad_domain)
    except urllib2.HTTPError, e:
        raise Exception("Encountered error when searching with user with " +
                          "different sAMAccountName and CN")
    adtools.delete_user("diffSAM", domain_info.domain_controller_ambassador)

    print "Searching with user where UPN and SAMAccountname have " + \
        "different realms"
    # the AD testing realms have been set up with an alternate
    # UPN suffix of "foo".  This was done by hand, and the tests
    # assume its presence.
    adtools.create_user("hasDiffRealm",
                        "pswd",
                        domain_info.domain_controller_ambassador,
                        domain_info.ad_domain,
                        ie_client_ambassador=domain_info.ie_client_ambassador)
    try:
        adtools.confirm_user_with_search("hasDiffRealm@foo",
                                         "pswd",
                                         domain_info.ie_client_ambassador)
    except urllib2.HTTPError, e:
        if e.code != 403:
            raise
    else:
        raise Exception("Expected error when searching with user with " +
                          "different realms in UPN and sAMAcountName")
    adtools.delete_user("hasDiffRealm",
                        domain_info.domain_controller_ambassador)

    print "Searching with disabled user account"
    adtools.create_user("disabledAccount",
                        "pswd",
                        domain_info.domain_controller_ambassador,
                        domain_info.ad_domain,
                        ie_client_ambassador=domain_info.ie_client_ambassador)
    domain_info.domain_controller_ambassador.run(
                                          "net user disabledAccount /active:no")
    # The creation of the user above and the subsequent confirmation search
    # results in a persistent logon for this user on the IE ambassador.
    # We must purge this so the next attempt fails as unable to logon.
    domain_info.ie_client_ambassador.purge_cached_credentials()
    try:
        adtools.confirm_user_with_search("disabledAccount",
                                         "pswd",
                                         domain_info.ie_client_ambassador)
        users["disabledAccount"] = [adtools.get_sid("disabledAccount"), 1]
    except urllib2.HTTPError, e:
        if e.code != 403:
            raise
    else:
        raise Exception("Expected error when searching with disabled user")

    print "Searching with semi-defective users"

    # By default, user creation confirms that the user is valid by
    # attempting a search as that user
    print "Searching with a user with the longest allowable sAMAccountName"
    samaccountname_length_limit = 20
    limit_name = "a" * samaccountname_length_limit
    adtools.create_user(limit_name,
                        "pswd",
                        domain_info.domain_controller_ambassador,
                        domain_info.ad_domain,
                        ie_client_ambassador=domain_info.ie_client_ambassador)
    adtools.delete_user(limit_name, domain_info.domain_controller_ambassador)
    
    # 
    for user in ["has InName", " StartsName", "has!InName", "!StartsName"]:
        print 'Seaching with "%s"' % (user)
        adtools.create_user(
                         user,
                         "pswd",
                         domain_info.domain_controller_ambassador,
                         domain_info.ad_domain,
                         ie_client_ambassador=domain_info.ie_client_ambassador)
        adtools.delete_user(user, domain_info.domain_controller_ambassador)

    # Having an '@' in the middle of the name will fail authentication to
    # the ambassador netting a 403.  If it doesn't raise this as a failure
    # raise an exception since we'll have to look into it.
    # NOTE: This doesn't fail anymore after 4.1.0 and windows 2008/vista
    try:        
        adtools.create_user(
            "has@InName",
            "pswd",
            domain_info.domain_controller_ambassador,
            domain_info.ad_domain,
            ie_client_ambassador=domain_info.ie_client_ambassador)
    except urllib2.HTTPError, e:
        if e.code != 403:
            raise
    else:
        major,minor,patch = adtools.windows_version(domain_info.ie_client_ambassador)
        # this is ok on windows vista/2008 ie version 6
        if major < 6:
            raise Exception('"has@InName" can now search -- change the tests!')

    # Another expected failure is a name that starts with an '@'.  In 3.9.0, this
    # would cause a 500 error.  In 4.0.0 and later it will return a search page with
    # no results unless the product is set to act in a legacy mode.
    try:
        if int(appliance.check_release_version()[0]) >= 4:
            appliance.spcall(["auth_control", "authorization", "search-web-ui", "--add", "infer_service_authz"])

        adtools.create_user(
                         "@StartsName",
                         "pswd",
                         domain_info.domain_controller_ambassador,
                         domain_info.ad_domain,
                         ie_client_ambassador=domain_info.ie_client_ambassador)
    except ambassador_client.AmbassadorError:
        if not re.search("HTTP Error 500", str(sys.exc_info()[1])):
            raise
    else:
        raise Exception('"@StartsInName" can now search -- change the tests!')

    print "All tests have passed.  Beginning cleanup."

    # Tear down the "connection"
    engine.close_connection()

    # Tear down the test
    engine.tear_down()

    CrawlerEngineBase.teardown_share_users(domain_info.domain_controller_ambassador)
    CrawlerEngineBase.restore_license()

    # Only give the cleanup function users and groups that are currently
    # active in the AD environment
    error_occurred = \
      ADTestHelpers.cleanup(
                 domain_info,
                 users=filter(lambda user: users[user][ENTITY_EXISTS_IN_AD],
                              users.keys()),
                 groups=filter(lambda group: groups[group][ENTITY_EXISTS_IN_AD],
                               groups.keys()))
    if (not error_occurred):
        print "AD user search tests PASSED"
    sys.exit(error_occurred)

if __name__ == '__main__':
    main()
