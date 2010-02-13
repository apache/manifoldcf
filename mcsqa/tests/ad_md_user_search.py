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

""" This test exercises problems unique to multi-domain Active Directory.
    It tests searches for documents with various combinations of ACLs from
    multiple domains, with users who have group memberships that cross domains
    and memberships in groups whose memberships cross domains, and so on.

    It is not meant to be a substitute for ad_user_search.py, but only
    to exercise additional features of multi-domain. """

import re
import commands
import sys
import time
import math
import pprint
import traceback

sys.path.append("/usr/lib/metacarta")

import SearchCanary
import TestDocs
import ADTestHelpers
import SQAhelpers

from wintools import adtools_md as adtools
from wintools import filetools
from wintools import sqa_domain_info
from wintools import sharecrawlertools
from wintools import ambassador_client

debug = False
nojoin = False
TestDocs.verbose = False

# constants for referencing parts of user, group, doc, or acl structures
EXPECTED_SEARCH_RESULTS = 2
ACL_ENTRY_TYPE = 0
ACL_ENTRY_USER = 1
CONTENTS = 0
DOC_ID = 1
DOC_ACL = 2
DOC_PATH = 3

# other constants
A_GOOD_NUMBER = 300
A_LOT = 100

def makepswd(username):
    return username+"paswd"

def print_flush( *args ):
    """ print the args and flush them """
    print time.ctime(),' '.join(map(str,args))
    sys.stdout.flush()

def debug_flush( *args ):
    """ print the args and flush them if debug is on"""
    if debug:
        print time.ctime(),' '.join(map(str,args))
        sys.stdout.flush()

# Search through the ambassador as the given username.  Return
# the sorted list of document numbers found
def get_user_search_results(username, random_string, domain_info):
    """ return matches, soap_matches, json_matches soap_matches, json_matches  will be None
    if the other api/ui's match the soap_headnode results.
    Otherwise they will be the mismatched results for reporting at a higher level """

    password = makepswd(username)

    # Search using soap headnode and json, we used to search the classic webui
    # but now it's going away so it'll just be these two, the matches variable
    # is left over, but in the interests of simplicity we're just setting it
    # to the soap results
    soap_matches = TestDocs.search_documents(
        method="soapheadnode-ambassador",
        keywords=[random_string],
        win_host=domain_info.ie_client_fqdn + ":" + domain_info.ambassador_port,
        win_user=username,
        win_password=password,
        unique=0)         

    matches = soap_matches

    if TestDocs.JSON_AVAILABLE:
        json_matches = TestDocs.search_documents(
            method="jsonsearchapi-ambassador",
            keywords=[random_string],
            win_host=domain_info.ie_client_fqdn + ":" + domain_info.ambassador_port,
            win_user=username,
            win_password=password,
            unique=0)
    else:
        # So we don't have to special case the compare below.
        json_matches = soap_matches

    # Sort them both for the comparision
    matches.sort()
    soap_matches.sort()
    json_matches.sort()

    # They are expected, and supposed, to be equal.  If so then we're
    # done here.
    if matches == soap_matches == json_matches:
        # get rid of the beginning of the path name so we can treat all the
        # document URLs as integers for easy comparision against the expected
        # documents in the callers.  Since we know the soap_matches are the same
        # as the ASCII headnode matches we can ignore them.
        matches = [match.split("/")[-1] for match in matches]

        # get rid of any non-numerical (i.e. canary) results and make
        # it a sorted list of integers
        for match in matches:
            if not re.search("^[0-9]+$", match):
                matches.remove(match)
        # convert to integers, resort
        matches = map(int, matches)
        matches.sort()
        # returning None for these indicates that they matched
        json_matches = None
        soap_matches = None

        #otherwise we'll just return the raw stuff to be processed above

    return matches,soap_matches,json_matches

def run_searches(domain_info, users, random_strings, documents_destroyed=0,
                 user_slice=0):
    """ Search through the ambassador as each user in the users list.  Check that the
        results returned match each user's expected results list or, if <documents_destroyed>,
        that each user gets an empty list.

        Runs len(users)*len(groups) searches (because len(random_strings) = len(groups)),
        so this scales as T(d) = (4d^4+16d^3+20d^2+8d+1)*2*(d^3+3d^2+3d) = O(d^7), where
        d is the number of domains in the forest.
        """
    debug_flush("run_searches")
    userkeys = users.keys()
    userkeys = userkeys[user_slice:] + userkeys[:user_slice]
    mismatches = []
    for username in userkeys:
        print_flush("Searching domain %s with user %s"%(domain_info.ad_domain,username))
        if (not users[username]['exists_in_ad']):
            # don't search with a user who wasn't created/is deleted
            continue
        if documents_destroyed and not users[username].get('nosearch'):
            # nosearch tag is for SuperUser, who has access to all documents,
            # to speed deletion searches
            continue
        if not documents_destroyed and ('expected_search_results' not in users[username]):
            # or has not expected search results
            continue

        expected_ids_sort = users[username].get('expected_search_results',[])
        expected_ids_sort.sort()

        # try 2 times for not getting enough results
        tries = 2
        user_mismatches = []
        while tries:
            matches = []
            user_mismatches = []
            for r in random_strings:
                web_matches = []
                soap_matches = []
                json_matches = []
                try:
                    # try 3 times for maybe having a merge cause a disagreement between interfaces
                    search_tries = 3
                    while search_tries:
                        web_matches,soap_matches,json_matches = get_user_search_results(username,r,domain_info)
                        # intefaces matched no retry, none for both of these indicates this
                        if not (soap_matches or json_matches):
                            break
                        # could be merging, so try again
                        search_tries = search_tries - 1
                        time.sleep(5)
                    else:
                        # used up our retries, record this mismatch occurrence
                        print_flush("Failed retrying due to merge",username,domain_info.ad_domain)
                        user_mismatches.append((username,r,domain_info.ad_domain,web_matches,soap_matches,json_matches,[],"Different results from interfaces (WEBUI,SOAP,JSON)"))
                        continue
                except Exception,e:
                    print_flush("Failed due to exception:",traceback.format_exc(),username,domain_info.ad_domain)
                    user_mismatches.append((username,r,domain_info.ad_domain,[],[],[],[],"Exception %s"%(traceback.format_exc())))
                    continue
                # record the matches we got
                matches.extend(web_matches)

            # if we have enough matches or we have mismatches that make this retry loop meaningless, get out of here
            if len(matches) >= len(expected_ids_sort) or mismatches:
                break

            # could have been stoplist processing, so give it another try
            print_flush("Retrying because the number of matches was too low")
            tries = tries - 1
            time.sleep(5)

        # sort the matches for comparison
        matches.sort()

        # shouldn't have any matches if there are no documents
        if documents_destroyed:
            if matches:
                user_mismatches.append((username,"",domain_info.ad_domain,matches,[],[],[],"Documents should have been destroyed"))
        else:
            # if the sequence expected doesn't match then we didn't find the docs we should have
            if not expected_ids_sort == matches:
                user_mismatches.append((username,"",domain_info.ad_domain,matches,[],[],expected_ids_sort,"Results didn't match expected"))

        # collect this users errors into the big list of errors
        mismatches.extend(user_mismatches)

        # get of of here after lets say 5 mismatches
        if len(mismatches) > 5:
            break

    # if there are any summary errors then sort them and report them back with an exception
    if mismatches:
        mismatches.sort()
        raise Exception("Search errors found %s" % (pprint.pformat(mismatches)))

class TestDocsIngestion:
    def __init__(self, ingestion_method):
        self.ingestion_method = ingestion_method
        self.ingested_documents = []

    def ingest_docs(self, docs, doc_acls, force_public=0,
                    debug_info=None):
        print_flush( "Ingesting documents using %s" % self.ingestion_method)
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
        print_flush( "Destroying documents")
        for doc_id in self.ingested_documents:
            TestDocs.delete_document(doc_id, self.ingestion_method)
        self.ingested_documents = []
        
class CollectionIngestion(TestDocsIngestion):
    def __init__(self):
        self.ingestion_method = "collection"

        # initialize the base class
        TestDocsIngestion.__init__(self, self.ingestion_method)
        
    def merge_docs(self):
        print_flush( "Merging collections")
        original_documents = self.ingested_documents[:]
        TestDocs.delete_document("merged", self.ingestion_method)
        self.ingested_documents.append("merged")
        TestDocs.merge_collections(original_documents, "merged")
        for doc_id in original_documents:
            TestDocs.delete_document(doc_id, self.ingestion_method)
        self.ingested_documents = ["merged"]

# Creates documents, gets them onto the duck using the ingestion method 
# of <ingestion_obj> and waits until the SearchCanary shows up in a
# webui search.  Returns the id of the canary, which is needed to delete
# it later.
def create_documents(domain_info, documents, doc_acls,
                     ingestion_obj, force_public=False, debug_info=None):

    debug_flush("create_documents")
    ingestion_obj.ingest_docs(documents, doc_acls,
                              force_public=force_public, debug_info=debug_info)
        
    # the canary ensures that all the documents are fully ingested
    # and will now show up in a search for them
    canaryID = None
    print_flush( "Waiting for the Search Canary" )
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
        
# Merges the documents, if that can be done in the ingestion method
# of <ingestion_obj>.  Destroys the SearchCanary associated with
# <old_canaryID>, and waits until a new canary shows up in a webui
# search.  Returns the ID of the new canary
def merge_docs(domain_info, ingestion_obj, old_canaryID):

    debug_flush("merge_docs")
    # If the ingestion object has a merge_docs method, run it
    if dir(ingestion_obj.__class__).count("merge_docs") == 1:

    # destroy the Canary, because we'll need a new one to determine
    # when the merged documents are searchable
        SearchCanary.destroy_canary(
                             old_canaryID,
                             ingestion_obj.ingestion_method,
                             "jsonsearchapi-ambassador",
                             timeout=32400,
                             search_opts={
                               "win_host":domain_info.ie_client_fqdn + ":" + \
                                            domain_info.ambassador_port,
                               "win_user":domain_info.realm_admin,
                               "win_password":domain_info.realm_admin_password})
        ingestion_obj.merge_docs()
        print_flush( "Waiting for the Search Canary" )
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

# Deletes all of the documents from the duck and destroys the search canary
def delete_documents(domain_info, users, random_strings, ingestion_obj,
                     canaryID):
    debug_flush("delete_documents")
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
    run_searches(domain_info, users, random_strings, documents_destroyed=1)

def create_docs_with_acls(doc_acls, random_strings):
    debug_flush("create_docs_with_acls")
    # put together the "documents" in the TestDocs library format
    documents = []
    # make doc_id start at 10000 to avoid collision with 1542 which is the name of a mountain in Iran
    doc_id = 10000
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
                random_strings[doc_id % len(random_strings)],
                doc_id),
            doc_id, 
            acl_string,
            # document path will be filled in later
            ''])
        doc_id += 1
    return documents

def ingest_and_search_documents(domains, users, documents, doc_acls, random_strings):
    # instantiate the ingestion objects.  The ingestion objects each
    # have methods that will ingest/destroy documents using that
    # particular ingestion type.  The CollectionIngestion object also
    # has a merge method for merging all collections it has created.
    ingestion_objs = [TestDocsIngestion("HTTP")]
    errors = 0

    for ingestion_obj in ingestion_objs:

        t1 = time.time()
        print_flush( "Ingesting %d documents" % len(documents) )

        # Create documents just needs some domain to search from.
        canaryID = create_documents(domains.values()[0],
                                    documents,
                                    doc_acls,
                                    ingestion_obj,
                                    debug_info="xMain crawl")

        t2 = time.time()
        print_flush( "Ingested %d documents in %0.3f minutes" % (len(documents),(t2-t1)/60.0))

        t1 = time.time()
        print_flush( "Searching %d documents" % len(documents))

        dlist = []
        i = 0
        for domain_info in domains.itervalues():
            user_slice = i * len(users)/len(domains)
            try:
                run_searches(domain_info, users, random_strings, user_slice=user_slice)
            except Exception, e:
                errors = errors + 1
                print_flush(traceback.format_exc())
            i += 1

        t2 = time.time()
        print_flush( "Searched %d documents in %0.3f minutes" % (len(documents),(t2-t1)/60.0))

        t1 = time.time()

        delete_documents(domain_info, users, random_strings, ingestion_obj, canaryID)

        t2 = time.time()
        print_flush( "Deleted %d documents in %0.3f minutes" % (len(documents),(t2-t1)/60.0))

    return errors

def group_fullname(groupname, groupinfo):
    """ Returns full domain\name for group. """

    if 'domain' in groupinfo:
        return r'%s\%s' % (groupinfo['domain'], groupname)
    else:
        return groupname

def create_groups(domains, tag):
    """ Generate a set of groups for permissions.  For d domains, this creates
        g+u+dl=(d)+(d^2+d)+(d^3+2d^2+d)=d^3+3d^2+3d groups. """
    # Kouti, Seitsonen p. 214; There are 5 "levels":
    # 1. User/computer
    # 2. Global Groups
    # 3. Universal Groups
    # 4. Domain Local Groups

    # Any upper-level (lower numbered) object can be a member in any
    # lower-level (higher-numbered) object.  Global groups cannot
    # accept members from other domains.  Universal and domain local
    # groups can, however.  Built-in groups are like domain-local
    # groups with the "domain" "Builtin" (p. 217).

    # First, create g = d global groups
    groups = {}
    i = 0
    for domain in domains:
        groups['G_G_D%d_%s' % (i, tag)] = dict(domain=domain,
                                              group_type='glsec', user_members=[],
                                              group_members=[])
        i += 1

    # Next, create u = d*(g+1) = d^2+d universal groups
    glgroups = dict((k,v) for k,v in groups.iteritems() if v.get('group_type') == 'glsec')

    for d in range(len(domains)):
        domain = domains[d]
        for (i,glg) in zip([i for i in range(len(glgroups))],glgroups.items()):
            groups['G_U_D%d_%d_%s'% (d, i, tag)] = \
                dict(group_type='unsec',
                     group_members=[glg[0]],
                     domain=domain, user_members=[])
        groups['G_U_D%d_%d_%s' % (d,len(glgroups),tag)] = \
                       dict(group_type='unsec', domain=domain, user_members=[],
                            group_members=[])

    # Next, create dl = d*(u+g+1) = d*(d^2+d+d+1) = d^3+2d^2+d
    # domain local groups
    newgroups = {}
    i = 0

    for d in range(len(domains)):
        domain = domains[d]
        count = 0
        newgroups['G_DL_D%d_%d_%s'%(i, count, tag)] = \
            dict(group_type='dlsec', domain=domain, user_members=[], group_members=[])
        count += 1
        for (groupname,groupinfo) in groups.iteritems():
            count += 1
            newgroups['G_DL_D%d_%d_%s'%(i, count, tag)] = \
                            dict(group_type='dlsec', domain=domain,
                                 group_members=[groupname],
                                 user_members=[])
        i += 1

    groups.update(newgroups)
    return groups

def filter_groups(groups, domain):
    """ Return groups which can be used for permissions for objects in domain. """
    debug_flush("filter_groups")
    return dict((gk, gv) for (gk,gv) in groups.iteritems() \
                if gv.get('group_type') != 'dlsec' or gv.get('domain') == domain)

def filter_groups_membership(groups, domain):
    """ Return groups to which a user in domain can belong. """
    debug_flush("filter_groups_membership")
    return dict((gk, gv) for (gk,gv) in groups.iteritems() \
                if gv.get('group_type') != 'glsec' or gv.get('domain') == domain)

def create_users(domains, allow_groups, deny_groups):
    """ Create a set of users that have combinations of memberships in
        allow_groups and deny_groups.  Creates 4d^4+16d^3+20d^2+8d+1 users,
        where d = len(domains). """
    debug_flush("create_users")
    users = {}

    # Create every combination of accept and deny.  Use counting variables
    # to shorten usernames.
    d = 0
    for domain in domains:
        i = 0

        # the filter_groups_membership will return g+u+(dl/d) = u+g+(u+g+1) groups,
        # which is 2((d)+(d^2+d))+1 = 2d^2+4d+1.
        # (2d^2+4d+1)^2 = 2d^2(2d^2+4d+1) + 4d(2d^2+4d+1) + (2d^2+4d+1)
        # = 4d^4+8d^3+2d^2 + 8d^3+16d^2+4d + 2d^2+4d+1 = 4d^4+16d^3+20d^2+8d+1 users total.
        domain_allow_groups = filter_groups_membership(dict((k,v) for k,v in \
                                                            allow_groups.iteritems() \
                                                            if not v['group_members']),domain)
        for (ik,iv) in domain_allow_groups.iteritems():
            j = 0

            for (jk,jv) in filter_groups_membership(dict((k,v) for k,v in deny_groups.iteritems() \
                                                     if not v['group_members']),domain).iteritems():
                username = 'User_%d_%d_D%d' % (i,j,d)
                users[username] = {'domain': domain, 'groups': [ik, jk]}
                iv['user_members'].append(username)
                jv['user_members'].append(username)

                j = j+1
            i = i+1

        # Create one user per domain with access to all of the documents.  This user
        # won't be used in the initial searches, but only once documents have been
        # deleted to verify that these users get no matches.
        #
        # We need one in every domain so that they can belong to all global groups.
        users['SuperUser_D%d' % d] = {'domain': domain,
                          'groups': domain_allow_groups,
                          'nosearch': True}
        for v in domain_allow_groups.itervalues():
            v['user_members'].append('SuperUser_D%d' % d)

        d = d+1

    return users

def create_doc_acls(allow_groups, deny_groups):
    """ Create a list of document ACLs to test on documents. """
    # Each acl is a dictionary of the form
    #    {scope: [('+'/'-', name), ... ]}
    # scope is "file", "share", or "dir"
    # Each name must be in the entities_in_acls dictionary.
    # An empty dictionary means the document will be public.
    debug_flush("create_doc_acls")
    doc_acls = [
        {}
        ]

    for accept_group, accept_group_info in allow_groups.iteritems():
        # Create one document for each element of the cartesian product
        # of allow_groups and deny_groups minus combinations that have
        # domain local ACLs from two different domains, since Windows
        # only allows a document to have domain local groups in ACLs if
        # the document exists in the domain the group is local to, so
        # these ACLs can't exist.
        doc_acls.extend([{'file': [['+', accept_group], ['-', deny_group]]} \
                         for deny_group, deny_group_info in deny_groups.iteritems() \
                         if deny_group_info['group_type'] != 'dlsec' or \
                         accept_group_info['group_type'] != 'dlsec' or
                         accept_group_info['domain'] == deny_group_info['domain']])
    return doc_acls

def check_group_access(group, groups, acl):
    """ Returns -1 if explicitly denied, 0 if neither allowed nor denied explicitly
        (and therefore denied), 1 if allowed. """
    # First, check other groups this group is a member of.
    debug_flush("check_group_access")
    allowed = 0
    for g in groups.iterkeys():
        if group in groups[g]['group_members']:
            ret = check_group_access(g, groups, acl)
            if ret == -1:
                return -1
            if ret == 1:
                allowed = 1

    # Now check the group we've been explicitly given
    for acl_entry in acl.get('file',[])+acl.get('share',[]):
        if acl_entry[0] == '-' and acl_entry[1] == group:
            return -1
        if acl_entry[0] == '+' and acl_entry[1] == group:
            allowed = 1
    return allowed


def check_user_access(userinfo, groups, acl):
    """ See if the user given by userinfo can access the document specified by acl """

    debug_flush("check_user_access")
    allowed = 0

    # Empty ACLs mean public.
    if acl == {}:
        return True

    default_groups=["authenticated_users","everyone","networkUsers","users","domainUsers"]
    user_groups = [g for g in userinfo['groups'] if g in groups]
    for g in user_groups+default_groups:
        ret = check_group_access(g, groups, acl)
        if ret == -1:
            return False
        if ret == 1:
            allowed = 1
    if allowed:
        return True
    else:
        return False

def resolve_user_doc_access(users,groups,doc_acls):
    debug_flush("resolve_user_doc_access")
    for username, userinfo in users.iteritems():
        if userinfo.get('nosearch'):
            continue

        userinfo['expected_search_results'] = []
        for aclnum in range(len(doc_acls)):
            if check_user_access(userinfo, groups, doc_acls[aclnum]):
                # bias the acl num by 10000 due to hack to avoid geographic numbers
                userinfo['expected_search_results'].append(10000+aclnum)

def init_users_groups_docacls(domain_id, limit_domains = False):
    debug_flush("init_users_groups_docacls",domain_id)
    # Initialize list of domains
    domain_info = ADTestHelpers.setup_ad_tests(domain_id)

    # FIXME: generalize to include domain_info or not based upon configuration.
    # if limit_domains is true only do the first child domain
    if limit_domains:
        domains = { domain_info.child_domains[0].ad_domain:domain_info.child_domains[0] }
    else:
        domains = dict((d.ad_domain, d) for d in domain_info.child_domains)

    # dictionary of all groups that we will be creating
    # group tuple is (sid, exists in AD, type, user members, group members)
    allow_groups = create_groups(domains.keys(), 'Allow')
    deny_groups = create_groups(domains.keys(), 'Deny')
    groups = {}
    groups.update(allow_groups)
    groups.update(deny_groups)

    users = create_users(domains = domains, allow_groups = allow_groups,
                         deny_groups = deny_groups)

    doc_acls = create_doc_acls(allow_groups, deny_groups)

    return groups, allow_groups, deny_groups, users, doc_acls, domains, domain_info

def update_acl_entries(users,groups,doc_acls):
    debug_flush("update_acl_entries")
    # dictionary of all entities that might be found on acls --
    # the search users, groups, existing users, a hack for
    # creating a blank acl, an invalid SID, etc.
    entities_in_acls = {}
    entities_in_acls.update(users)
    entities_in_acls.update(groups)
    entities_in_acls["everyone"] = {'sid':adtools.EveryoneSID, 'exists_in_ad':1}
    entities_in_acls["authenticated_users"] = {'sid':adtools.AuthenticatedUsersSID,
                                               'exists_in_ad':1}
    entities_in_acls["networkUsers"] = {'sid':adtools.NetworkUsersSID, 'exists_in_ad':1}
    entities_in_acls["users"] = {'sid':adtools.UsersSID, 'exists_in_ad':1}
    entities_in_acls["invalidSID"] = {'sid':adtools.InvalidSID, 'exists_in_ad':0}
    entities_in_acls["domainUsers"] = {'sid':adtools.get_sid("Domain Users"), 'exists_in_ad': 1}

    # replace all of the user/group names with SIDs
    for acl in doc_acls:
        for scope in acl.keys():
            for entry in acl[scope]:
                entry[ACL_ENTRY_USER] = entities_in_acls[entry[ACL_ENTRY_USER]]['sid']

def cleanup_users_groups(domain_info, domains, users, groups ):
    """ clean up users and groups that we may have created """
    # We want to try to delete the users from every domain, since if user u1 exists in domain d2,
    # trying to create d1\u1 will fail.  This might happen if the test is reconfigured.
    debug_flush("cleanup_users_groups")
    
    for d in domains.itervalues():
        for u in users.iterkeys():
            try:
                adtools.delete_user(u, d.domain_controller_ambassador)
            except Exception, e:
                pass

    # Delete the groups before we try to create them
    for groupname, groupinfo in groups.iteritems():
        if 'domain' in groupinfo:
            d = domains[groupinfo['domain']]
        else:
            d = domain_info
        try:
            adtools.delete_group(groupname, d.domain_controller_ambassador, domain=d.ad_domain,verbose=debug)
        except Exception, e:
            pass

def main():

    pp = pprint.PrettyPrinter(indent=2, width=100)

    # process the command line arguments
    (options, args) = ADTestHelpers.process_ad_arguments(sys.argv)

    # if args contain debug then set the debug flag
    if "debug" in args:
        global debug
        debug = True

    # if args contain nojoin then don't set up and join assume already joined
    if "nojoin" in args:
        global nojoin
        nojoin = True

    # dictionary of all users that we will be creating
    # these users will be performing searches
    # user tuple is (sid, exists in AD, expected search results)

    (groups, allow_groups, deny_groups, users, doc_acls, domains, domain_info) = \
             init_users_groups_docacls(options.domain_id, limit_domains = (options.pre_upgrade or options.post_upgrade))

    # Setup DNS to be what we want (primary domain is an A record, everything
    # else is CNAMEs)
    duck_name = SQAhelpers.reverse_resolve().split('.')[0]

    # dns should already be configured for post upgrade test and we want to keep the machine accts
    if not nojoin and not options.post_upgrade:
        # make sure there aren't any lingering machine accounts
        adtools.ad_md_remove_machine_accts( duck_name, domain_info, verbose=debug)
        # set up DNS 
        adtools.ad_md_setup_dns(duck_name, SQAhelpers.get_host_address(),domain_info,verbose=debug)
        
    pp.pprint(users)
    pp.pprint(zip(range(10000,10000+len(doc_acls)),doc_acls))

    if options.cleanup_only:
        print_flush( "Beginning cleanup.  No tests will be run")
        print_errors = True
    else:
        print_flush( "Cleaning up from previous tests")
        print_errors = False


    # active_directory_tool join, accept default realm and
    # Administrator as user and enter Administrator password.
    # don't join for a post upgrade test, we should already be joined
    if not nojoin and not options.post_upgrade:
        print_flush( "Joining the AD domain %s."%domain_info.ad_domain)
        adtools.ad_md_setup_join(domain_info,verbose=debug)

    # clean up users and groups
    cleanup_users_groups(domain_info, domains,users,groups)

    print_flush( "Force replication of cleanup changes" )

    for d in domains.values():
        output = d.domain_controller_ambassador.run("repadmin /syncall")
        print_flush(output)

    if options.cleanup_only:
        sys.exit(0)

    # make sure we can search from all of the domains
    # New plan, try to wait until the issue with DNS clears up, right now allow up to an hour in 5 minute increments
    # it may time out the test we'll have to adjust the test timeout once we know when the problem resolves in the domain
    for d in domains.values():
        tries = 24
        while tries:
            try:
                print "Test search domain %s ie client ambassador %s :"%(d.ad_domain,d.ie_client_ambassador.ambassador)
                adtools.confirm_user_with_search( "Administrator@"+d.ad_domain,
                                                  d.realm_admin_password,
                                                  d.ie_client_ambassador,
                                                  hostname=duck_name )
                print "OK after %d seconds"%((12-tries)*300)
                break
            except Exception, e:
                print "Failed %s"%(str(e))                  
# This workaround worked manually, but not automatically, there is no evidence that there are cached tickets that
# we can detect at this level
#                print d.ie_client_ambassador.run(r"\\u14e\mcinstall\klist tickets",
#                                                 user="Administrator@"+d.ad_domain,
#                                                 password=d.realm_admin_password)
#                print d.ie_client_ambassador.run(r"\\u14e\mcinstall\Purge.exe",
#                                                 user="Administrator@"+d.ad_domain,
#                                                 password=d.realm_admin_password)
#                print d.ie_client_ambassador.run(r"\\u14e\mcinstall\klist tickets",
#                                                 user="Administrator@"+d.ad_domain,
#                                                 password=d.realm_admin_password)
                tries -= 1
                time.sleep(300)
        else:
            raise Exception("Failed to get search working!")
        
    # create random 8-character strings to use in searches
    random_strings = [SQAhelpers.create_random_string(16) for i in range(len(groups))]

    t1 = time.time()

    print_flush( "Creating %d users" % len(users))
    for username,userinfo in users.iteritems():
        # It would be nice to use ldif here, but you can't set
        # the passwords

        d = domains[userinfo['domain']]

        try:
            adtools.create_user(username, makepswd(username),
                                d.domain_controller_ambassador, d.ad_domain,
                                ie_client_ambassador=d.ie_client_ambassador,
                                confirm_user=False)
        except:
            print_flush( "Exception creating user %s in domain %s" % (username, d.ad_domain))
            raise

        # mark that it has been created
        users[username]['exists_in_ad'] = 1
        # set SID for use in document acls
        output = adtools.get_sid(username, userinfo['domain'])
        users[username]['sid'] = output

    t2 = time.time()
    print_flush( "Created %d users in %0.3f minutes" % (len(users),(t2-t1)/60.0))

    t1 = time.time()

    print_flush( "Creating %d groups" % len(groups))
    for groupname, groupinfo in groups.iteritems():
        # create_group takes the group name and group type string
        # It would be nice to use ldif here, but we can't because
        # of circular groups
        if 'domain' in groupinfo:
            d = domains[groupinfo['domain']]
        else:
            d = domain_info

        try:
            adtools.create_group(groupname, groups[groupname]['group_type'],
                                 d.ad_domain, d.domain_controller_ambassador)

        except:
            print_flush( "Exception creating group %s in domain %s" % (groupname, d.ad_domain))
            raise

        # mark that it has been created
        groups[groupname]['exists_in_ad'] = 1
        # set SID for use in document acls.  Because names are unique
        # across domains, we don't need to specify domain here.
        groups[groupname]['sid'] = adtools.get_sid(groupname, d.ad_domain)

    t2 = time.time()
    print_flush( "Created %d groups in %0.3f minutes" % (len(groups),(t2-t1)/60.0))

    t1 = time.time()

    print_flush( "Populating groups")
    # this can't be combined with the above loop because there are
    # circular memberships
    for groupname, groupinfo in groups.iteritems():
        group_domain = groups[groupname]['domain']
        #d = domains[groupinfo['domain']]

        for member in groups[groupname]['user_members']:
            member_domain = users[member]['domain']
            d = domains[member_domain]

            adtools.add_to_group(member, groupname, d.domain_controller_ambassador,
                                 child_domain = member_domain, parent_domain = group_domain, verbose = debug)
        for member in groups[groupname]['group_members']:
            member_domain = groups[member]['domain']
            d = domains[member_domain]

            adtools.add_to_group(member, groupname, d.domain_controller_ambassador,
                                 child_domain = member_domain, parent_domain = group_domain, verbose = debug)

    t2 = time.time()
    print_flush( "Populated %d groups with %d users in %0.3f minutes" % \
          (len(groups), len(users),(t2-t1)/60.0))

    print_flush( "Resolving Doc Access")
    resolve_user_doc_access(users,groups,doc_acls)

    print_flush( "Creating Documents")

    update_acl_entries(users,groups, doc_acls)

    documents = create_docs_with_acls(doc_acls, random_strings)

    print_flush( "Force replication" )

    for d in domains.values():
        output = d.domain_controller_ambassador.run("repadmin /syncall")
        print_flush(output)

    print_flush( "Searching for Documents")
    # We'll do this for each domain, and test each domain individually.
    errors = ingest_and_search_documents(domains, users, documents, doc_acls, random_strings)

    if not errors:
        print_flush( "All tests have passed.  Beginning cleanup.")
    else:
        print_flush( "Exiting with errors. Beginning cleanup." )

    # clean up users and groups
    cleanup_users_groups(domain_info, domains,users,groups)

    # leave the domain
    if not options.pre_upgrade:
        adtools.leave_ad( realm_admin_password=domain_info.realm_admin_password, disable_machine_acct=False, delete_machine_acct=True, already_left=0 )

    print_flush( "Cleanup complete.")
    
    # exit with no error
    sys.exit(errors)

if __name__ == '__main__':
    main()
