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
import RSSConnectorHelpers
import sqatools
from wintools import sqa_domain_info
from wintools import filetools
from wintools import ambassador_client
from sqatools import LicenseMakerClient
import TestDocs
import VirtualBrowser

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion



# Run a document status report to get the expire and refetch times
def run_document_status_report( username, password, connection_name, job_id, url_match ):
    """ Return tuples of (expiration time, refetch time) """
    results = ConnectorHelpers.run_document_status_ui( username, password, connection_name,
            [ job_id ], identifier_regexp=url_match )

    if len(results) != 1:
        raise Exception("Expecting to see a single row for identifier %s in document status report, saw %d" % (url_match,len(results)))

    result = results[0]
    scheduled_time = result[ "Scheduled" ]
    scheduled_action = result[ "Scheduled Action" ]
    if scheduled_action == "Expire":
        time_value = ConnectorHelpers.parse_date_time(scheduled_time)
        return ( time_value, -1 )
    elif scheduled_action == "Process":
        time_value = ConnectorHelpers.parse_date_time(scheduled_time)
        return ( -1, time_value )
    return ( -1, -1 )

# Server name to talk to
rssServerName = None
# Domain
rssDomain = None
# Proxy host
rssProxyHost = None
# Server port to talk to
rssServerPort = "81"
# User
rssUser = None
# Password
rssPassword = None
# Proxy port
rssProxyPort = 8080

# Names of feeds
feed_names = [ "feed1.xml", "feed2.xml", "feed3.xml", "feed4.xml", "feed5.xml", "feed6.xml", "feed7.xml", "feed8.xml", "feed10.xml",
                      "feed11.xml", "feed12.xml", "feed13.xml", "feed14.xml", "feed18.xml" ]

# Names of documents that match 'redirected'
redirected_documents = [ "redirected_2635.htm" ]

# Names of documents that match 'atom'
atom_documents = [ "atom_article_1.htm",
                   "atom_article_2.htm",
                   "atom_article_3.htm",
                   "atom_article_4.htm",
                   "atom_article_5.htm",
                   "atom_article_6.htm",
                   "atom_article_7.htm",
                   "atom_article_8.htm",
                   "atom_article_9.htm",
                   "atom_article_10.htm",
                   "atom_article_11.htm",
                   "atom_article_12.htm",
                   "atom_article_13.htm",
                   "atom_article_14.htm",
                   "atom_article_15.htm" ]

# Names of documents that match 'latah'
latah_documents = [ "latah_2635.htm",
                        "latah_2637.htm",
                        "latah_2638.htm",
                        "latah_2639.htm",
                        "latah_2640.htm",
                        "latah_2641.htm",
                        "latah_2642.htm",
                        "latah_2643.htm",
                        "latah_2644.htm",
                        "latah_2645.htm",
                        "latah_2646.htm",
                        "latah_2647.htm",
                        "latah_2649.htm",
                        "latah_2650.htm",
                        "latah_2651.htm",
                        "latah_2652.htm",
                        "latah_2653.htm",
                        "latah_2654.htm",
                        "latah_2655.htm",
                        "latah_2656.htm",
                        "latah_2657.htm",
                        "latah_2658.htm",
                        "latah_2659.htm",
                        "latah_2660.htm",
                        "latah_2661.htm",
                        "latah_2662.htm",
                        "latah_2663.htm",
                        "latah_2664.htm",
                        "latah_2665.htm",
                        "latah_2666.htm",
                        "latah_2667.htm" ]
# Names of documents that match 'Jamestown'
jamestown_specialdocument = "jamestown_58996.htm"
jamestown_basedocuments = [ "jamestown_58978.htm",
                        "jamestown_58980.htm",
                        "jamestown_58981.htm",
                        "jamestown_58982.htm",
                        "jamestown_58987.htm",
                        "jamestown_58988.htm",
                        "jamestown_58991.htm",
                        "jamestown_58993.htm",
                        "jamestown_58994.htm",
                        "jamestown_58995.htm",
                        "jamestown_58997.htm",
                        "jamestown_59011.htm" ]
jamestown_documents = jamestown_basedocuments + [ jamestown_specialdocument ]

# Names of documents that match 'Allafrica'
allafrica_specialdocument = "200712310708.html"
allafrica_basedocuments = [ "200712310370.html",
                        "200712310447.html",
                        "200712310449.html",
                        "200712310454.html",
                        "200712310587.html",
                        "200712310588.html",
                        "200712310590.html",
                        "200712310632.html",
                        "200712310684.html",
                        "200712310698.html",
                        "200712310711.html",
                        "200712310712.html",
                        "200712310714.html",
                        "200712310715.html",
                        "200712310718.html",
                        "200712310744.html",
                        "200712310746.html",
                        "200712310748.html",
                        "200712310760.html",
                        "200712310795.html",
                        "200712310799.html",
                        "200712310808.html",
                        "200712310811.html",
                        "200712310816.html",
                        "200712310817.html",
                        "200712310820.html",
                        "200712310825.html",
                        "200712310829.html",
                        "200712310876.html" ]
allafrica_documents = allafrica_basedocuments + [ allafrica_specialdocument ]

# Names of documents that match "Chinanews"
chinanews_specialdocument = "237544.htm"
chinanews_basedocuments = [ "237532.htm",
                        "237543.htm",
                        "237545.htm",
                        "237546.htm",
                        "237548.htm",
                        "237552.htm",
                        "237554.htm",
                        "237560.htm",
                        "237566.htm",
                        "237568.htm",
                        "237575.htm",
                        "237611.htm",
                        "237613.htm",
                        "237614.htm",
                        "237617.htm",
                        "237619.htm",
                        "237636.htm",
                        "237645.htm",
                        "237664.htm" ]
chinanews_documents = chinanews_basedocuments + [ chinanews_specialdocument ]

# Names of documents that match "Baghdadfeed"

baghdadfeed_specialdocument = "baghdad_sacrifice.html"
baghdadfeed_basedocuments = [ "baghdad_green-zone-blue.html",
                        "baghdad_hot-water.html",
                        "baghdad_i-just-want-one.html",
                        "baghdad_in-the-glass-ca.html",
                        "baghdad_leaving.html",
                        "baghdad_return.html",
                        "baghdad_sage.html",
                        "baghdad_stray-bullets.html",
                        "baghdad_truth.html" ]
baghdadfeed_documents = baghdadfeed_basedocuments + [ baghdadfeed_specialdocument ]

# Names of documents that match "sportchannel"
sportschannel_documents = [ "feed3.htm?Itemid=42&id=3018&option=com_content&task=view",
                        "feed3.htm?Itemid=148&id=3020&option=com_content&task=view",
                        "feed3.htm?Itemid=148&id=3016&option=com_content&task=view",
                        "feed3.htm?Itemid=40&id=3014&option=com_content&task=view",
                        "feed3.htm?Itemid=148&id=3017&option=com_content&task=view",
                        "feed3.htm?Itemid=40&id=3015&option=com_content&task=view",
                        "feed3.htm?Itemid=43&id=3019&option=com_content&task=view",
                        "feed3.htm?Itemid=148&id=3022&option=com_content&task=view",
                        "feed3.htm?Itemid=43&id=3013&option=com_content&task=view",
                        "feed3.htm?Itemid=43&id=3021&option=com_content&task=view" ]

# Names of documents that match "Bostoncom"
bostoncomfeed_basedocuments = [ "cannon_mountain_offering_new_ski_pass_next_year.htm",
                        "harvard_will_not_accept_transfers_for_2_years.htm",
                        "lufthansa_emergency_landing_in_poland.htm",
                        "winning_firm_gave_to_dimasi_charity.htm",
                        "gas_prices_down_a_penny_in_massachusetts.htm",
                        "detroits_democrat_mayor_indicted_in_sex_scandal.htm",
                        "bush_mourns_all_4000_dead_in_iraq_white_house.htm",
                        "kevorkian_kicks_off_congressional_run.htm",
                        "mogadishu_port_slowly_changing_lives_in_somalia.htm",
                        "bill_would_exonerate_witches.htm",
                        "courthouses_bold_art_draws_a_mixed_verdict.htm",
                        "many_arabs_fear_mccain_would_continue_bush_policy.htm",
                        "rice_urges_china_talks_with_dalai_lama.htm",
                        "bush_committed_to_iraq_success.htm",
                        "way_cleared_for_removal_of_portion_of_fort_halifax_dam.htm",
                        "worlds_tallest_man_struggles_to_fit_in.htm",
                        "5_killed_in_iowa_city_shooting.htm",
                        "gas_prices_drop_a_penny_in_ocean_state.htm",
                        "detroit_mayor_charged_with_perjury.htm",
                        "white_house_hosts_annual_egg_roll.htm",
                        "committee_passes_that_makes_noose_hanging_a_crime.htm",
                        "muslims_question_vatican_baptism_of_islamic_critic.htm",
                        "fairpoint_announces_tentative_accord_with_unions.htm",
                        "logging_truck_operator_stung_by_high_cost_of_diesel_fuel.htm",
                        "hezbollah_says_israel_prisoner_swap_talks_go_on.htm",
                        "comoros_warns_rebel_island_of_attack.htm",
                        "kwame_kilpatrick_at_a_glance.htm",
                        "skycaps_sue_airline_over_tips_lost_to_bag_fee.htm",
                        "college_to_buy_renewable_energy_offsets_from_wind_farm.htm",
                        "state_health_plan_underfunded.htm",
                        "entry_point.htm",
                        "rice_urges_chinese_to_listen_to_dalai_lama_on_tibet.htm",
                        "holiday_or_not_schools_gird_for_absences.htm",
                        "the_battle_scarred_caretakers.htm",
                        "coming_up_dry_on_bottles.htm" ]
bostoncomfeed_documents = bostoncomfeed_basedocuments

# Documents that are special because they have at least one pubdate metadata attribute with a particular value
specialdate_documents = [ "feed3.htm?Itemid=148&id=3022&option=com_content&task=view",
                                      "feed3.htm?Itemid=148&id=3020&option=com_content&task=view",
                                      "237664.htm",
                                      "bill_would_exonerate_witches.htm",
                                      "atom_article_1.htm" ]

# All (searchable) documents
all_documents = latah_documents + jamestown_documents + allafrica_documents + chinanews_documents + baghdadfeed_documents + sportschannel_documents + bostoncomfeed_documents

# Transferable documents
unmappable_transferable_documents = latah_documents + jamestown_documents + allafrica_documents + \
        chinanews_documents + baghdadfeed_documents + bostoncomfeed_documents + atom_documents + [ "feed19.xml", "feed20.xml", "feed21.xml", "feed22.xml", "feed23.xml" ]
mappable_transferable_documents = feed_names + \
        [ "feed3.htm", "feed9.xml", "modified1.htm", "modified2.htm", "feed16.xml", "feed17.xml" ]
transferable_documents = unmappable_transferable_documents + mappable_transferable_documents

def make_rss_url(folder_path, location=""):
    if int(rssServerPort) == 80:
        return "%s%s/%s" % (rssServerName,location,folder_path)
    else:
        return "%s:%s%s/%s" % (rssServerName,rssServerPort,location,folder_path)


# Crawl user credentials
username = "testingest"
password = "testingest"

# Run the ad part of the test
def run_ad_test_part( ad_domain_info ):
    """ This part of the test may be run more than once, if legacy mode is to be tested too.  Therefore I've broken this part into
        a separate method.
    """

    ad_win_host = ad_domain_info.ie_client_fqdn

    # Set up system to use ad
    ConnectorHelpers.configure_ad( ad_domain_info )

    # Add a user (if not already present in the domain)
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_rssusera", "usera" )
    ConnectorHelpers.conditionally_add_ad_user( ad_domain_info, "p_rssuserb", "userb" )

    # Look up the user's SID
    user_sid = ConnectorHelpers.get_ad_user_sid( "p_rssusera" )
    
    # Define repository connection
    RSSConnectorHelpers.define_rss_repository_connection_ui( username,
                                        password,
                                        "RSSConnection",
                                        "RSS Connection",
                                        "kwright@metacarta.com" )

    # Define job
    job_id = RSSConnectorHelpers.define_rss_job_ui( username,
                        password,
                        "RSSJob",
                        "RSSConnection",
                        [ "http://"+make_rss_url("feed1.xml") ],
                        forced_acls=[ user_sid ] )


    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Make sure we can see the documents if we are p_rssuser, and we can't otherwise
    url_list = [ ]
    for document in latah_documents:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "latah" ], None, url_list, username="p_rssusera", password="usera", win_host=ad_win_host )
    ConnectorHelpers.search_check( [ "latah" ], None, [ ], username="p_rssuserb", password="userb", win_host=ad_win_host )

    # Delete job
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    # Tear down repository connection
    ConnectorHelpers.delete_repository_connection_ui( username, password, "RSSConnection" )
    
    # Disable ad
    ConnectorHelpers.turn_off_ad( ad_domain_info )


def preclean( ad_domain_info, perform_legacy_pass, print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    # First order of business: synchronize legacy mode, so our state matches the duck state
    ConnectorHelpers.synchronize_legacy_mode( )

    try:
        ConnectorHelpers.reset_all()
    except Exception, e:
        if print_errors:
            print "Error resetting all jobs"
            print e

    # Clean up the documents we dumped into the folders on the server
    for document in transferable_documents:
        try:
            RSSConnectorHelpers.remove_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\"+document )
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % document
                print e

    # Clean up redirected documents
    for document in redirected_documents:
        try:
            RSSConnectorHelpers.remove_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "redirected_target_content\\"+document)
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % document
                print e

    # Clean up robots.txt
    try:
        RSSConnectorHelpers.remove_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\robots.txt" )
    except Exception, e:
        if print_errors:
            print "Error removing robots.txt"
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
    rssDomain = ad_domain_info.dns_domain.upper()
    rssServerName = getattr(ad_domain_info,"rss_server_fqdn");
    rssProxyHost = getattr(ad_domain_info,"isa_proxy_fqdn");

    # User
    rssUser = ad_domain_info.realm_admin.split("@")[0]
    # Password
    rssPassword = ad_domain_info.realm_admin_password

    print "Precleaning!"

    preclean( ad_domain_info, perform_legacy_pass, print_errors=False )

    print "Cycling service to clear robots cache"

    ConnectorHelpers.restart_agents()

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up ingestion user."
    ConnectorHelpers.create_crawler_user( username, password )

    print "Testing how UI handles bad license."
    sqatools.appliance.install_license(extra_services=[], detect_gdms=True)
    
    # Recycle tomcat, otherwise we might hold onto already-licensed handle
    ConnectorHelpers.restart_tomcat( )
    time.sleep(10)
    
    RSSConnectorHelpers.define_rss_repository_connection_ui( username,
                                        password,
                                        "RSSConnection",
                                        "RSS Connection",
                                        "kwright@metacarta.com" )
    # Viewing the connection should NOT give 'Connection working'!
    saw_error = True
    try:
        ConnectorHelpers.view_repository_connection_ui( username, password, "RSSConnection" )
        saw_error = False
    except:
        pass
        
    if saw_error == False:
        raise Exception("Licensing off but did not see license error!")

    ConnectorHelpers.delete_repository_connection_ui( username, password, "RSSConnection" )

    LicenseMakerClient.revoke_license()

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["rssConnector"], detect_gdms=True)

    ConnectorHelpers.define_gts_outputconnection( )

    # PHASE 0: Canonicalization rules test

    print "Canonicalization rules test."


    # The seed urls we will use for ALL tests are the following:
    canonicalization_seed_urls = []
    for url_args in [ "/s(qqq)/random/path/stuff;jsessionid=zzz?arg1=A&arg2=B&PHPSESSID=xxx&arg3=C&arg1=D&BVSession@@@@=yyy" ]:
        if int(rssServerPort) == 80:
            new_url = "http://%s%s" % (rssServerName,url_args)
        else:
            new_url = "http://%s:%s%s" % (rssServerName,rssServerPort,url_args)

        canonicalization_seed_urls += [ new_url ]

    # Loop over a set of combination tuples
    for canon_parameters, expected_results in [ (("",None,"yes","no","no","no","no"),["/s(qqq)/random/path/stuff;jsessionid=zzz?BVSession@@@@=yyy&PHPSESSID=xxx&arg1=A&arg1=D&arg2=B&arg3=C"]),
                                        (("",None,"no","yes","yes","yes","yes"),["/random/path/stuff?arg1=A&arg2=B&arg3=C&arg1=D"]),
                                        (("",None,"yes","no","yes","no","yes"),["/random/path/stuff;jsessionid=zzz?PHPSESSID=xxx&arg1=A&arg1=D&arg2=B&arg3=C"]) ]:

        # Since we're evaluating canonicalization using the report feature, set up and tear down connection for each round, so the history is clean
        # Set up a repository connection
        RSSConnectorHelpers.define_rss_repository_connection_ui( username,
                                        password,
                                        "RSSConnection",
                                        "RSS Connection",
                                        "kwright@metacarta.com" )

        # Build and run a job
        job_id = RSSConnectorHelpers.define_rss_job_ui( username,
                        password,
                        "RSSJob",
                        "RSSConnection",
                        canonicalization_seed_urls,
                        canonicalization_rules=[ canon_parameters ] )

        # Run the job to completion
        ConnectorHelpers.start_job( job_id )
        ConnectorHelpers.wait_job_complete( job_id )

        # See what the report says about the fetches we did
        results = ConnectorHelpers.run_simple_history_report_api( "RSSConnection", [ "fetch" ] )
        # Throw the results into a map based on document identifier, so we can look uris up
        result_map = {}
        for result in results:
            document_uri = result["identifier"]
            result_map[document_uri] = result

        for expected_result in expected_results:
            if int(rssServerPort) == 80:
                full_url = "http://%s%s" % (rssServerName,expected_result)
            else:
                full_url = "http://%s:%s%s" % (rssServerName,rssServerPort,expected_result)

            if not result_map.has_key(full_url):
                raise Exception("Expected to find canonicalized URL fetch for %s, but didn't; actual: %s" % (full_url,str(results)))

        # Delete the job
        ConnectorHelpers.delete_job( job_id )
        ConnectorHelpers.wait_job_deleted( job_id )

        # Tear down the initial repository connection
        ConnectorHelpers.delete_repository_connection_ui( username, password, "RSSConnection" )


    # PHASE 1: Ingestion

    print "Ingestion Test."

    # Add all docs to the repository
    map = { "%server%" : rssServerName }
    for document in mappable_transferable_documents:
        RSSConnectorHelpers.add_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\"+document, "/root/rssfeeds/"+document, map=map)
    for document in unmappable_transferable_documents:
        RSSConnectorHelpers.add_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\"+document, "/root/rssfeeds/"+document)
    # Add redirected documents
    for document in redirected_documents:
        RSSConnectorHelpers.add_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "redirected_target_content\\"+document, "/root/rssfeeds/"+document)

    feed_list = []
    for feed_name in feed_names:
        feed_list.append( "http://"+make_rss_url(feed_name) )

    # Do one url that isn't in fact on the server, so we can see what the crawler does under those conditions, and two feeds that share the same dead link
    for feed_name in [ "does_not_exist.xml", "feed16.xml", "feed17.xml", "feed21.xml", "feed22.xml", "feed23.xml" ]:
        feed_list.append( "http://"+make_rss_url(feed_name) )

    # Record the start time of the crawl, to be sure we catch everything in the report
    start_time = time.time()
    
    # Define repository connection
    RSSConnectorHelpers.define_rss_repository_connection_ui( username,
                                        password,
                                        "RSSConnection",
                                        "RSS Connection",
                                        "kwright@metacarta.com" )

    # Define job
    job_id = RSSConnectorHelpers.define_rss_job_ui( username,
                        password,
                        "RSSJob",
                        "RSSConnection",
                        feed_list,
                        user_metadata=[ ("test_metadata_1", "hello"), ("test_metadata_2", "there"), ("test_metadata_1", "charlie") ],
                        mappings=[("(.*)","1")])


    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    url_list = []
    for document in redirected_documents:
        url_list.append( make_rss_url(document,location="/redirected") )
    ConnectorHelpers.search_check( [ "redirected" ], None, url_list )
    url_list = []
    for document in latah_documents:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "latah" ], None, url_list )
    url_list = []
    for document in atom_documents:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "atom" ], None, url_list )
    url_list = []
    for document in allafrica_documents:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "Allafrica" ], None, url_list )
    url_list = []
    for document in chinanews_documents:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "Chinanews" ], None, url_list )
    
    # Look for at least one document that has a category metadata attribute
    ConnectorHelpers.search_check( [ "Chinanews", "metadata:category=news" ], None, url_list )
    
    url_list = []
    for document in jamestown_documents:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "Jamestown" ], None, url_list )
    
    # Look for user metadata too
    ConnectorHelpers.search_check( [ "Jamestown", "metadata:test_metadata_1=hello" ], None, url_list )
    ConnectorHelpers.search_check( [ "Jamestown", "metadata:test_metadata_1=charlie" ], None, url_list )
    ConnectorHelpers.search_check( [ "Jamestown", "metadata:test_metadata_2=there" ], None, url_list )

    url_list = []
    for document in baghdadfeed_documents:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "Baghdadfeed" ], None, url_list )
    url_list = []
    for document in sportschannel_documents:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "sportschannel" ], None, url_list )
    url_list = []
    for document in bostoncomfeed_documents:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "bostoncom" ], None, url_list )

    # Look for documents that have dates that need to be trimmed
    # Note that this test's reliability is based on carrydown data being accurate.
    # Also checks for various RFC822 formats for RSS 2.0 feed
    url_list = []
    for document in specialdate_documents:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "metadata:pubdate=1228150860000" ], None, url_list )

    # Look for an earlier pubdate ALSO on one of the specialdate documents.
    ConnectorHelpers.search_check( [ "metadata:pubdate=1199106420000"], None, [ make_rss_url("237664.htm") ] )

    # For the feed that didn't exist, check to make sure exactly one fetch attempt was made.
    results = ConnectorHelpers.run_simple_history_report_api( "RSSConnection", [ "fetch" ], entity_regexp="does_not_exist\\.xml" )
    if len(results) != 1:
        raise Exception("Expected exactly one fetch attempt of non-existent feed, instead saw %d" % len(results))
    if results[0]["result_code"] != "404":
        raise Exception("Expected fetch result code of 404, instead saw %s" % results[0]["result_code"])

    # For the document that didn't exist, check that no more than TWO fetch attempts were made.  (Two attempts are possible because a change to the carrydown information
    # occurs to this document as a result of it being in two feeds).
    results = ConnectorHelpers.run_simple_history_report_api( "RSSConnection", [ "fetch" ], entity_regexp="does_not_exist_on_server\\.htm" )
    if len(results) < 1 or len(results) > 2:
        raise Exception("Expected one or two fetch attempts of non-existent shared document, instead saw %d" % len(results))
    if results[0]["result_code"] != "404":
        raise Exception("Expected fetch result code of 404, instead saw %s" % results[0]["result_code"])

    # Success: done
    print "Done ingestion test."


    # PHASE 2: Document Change Detection

    print "Document Change Test."
    # Create two modified documents
    RSSConnectorHelpers.version_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\"+chinanews_specialdocument, "/root/rssfeeds/modified1.htm" )
    RSSConnectorHelpers.version_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\"+jamestown_specialdocument, "/root/rssfeeds/modified2.htm" )

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Look for state of index being right
    ConnectorHelpers.search_check( [ "modified" ], None, [ make_rss_url(chinanews_specialdocument), make_rss_url(jamestown_specialdocument) ] )
    url_list = []
    for document in jamestown_basedocuments:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "Jamestown" ], None, url_list )
    url_list = []
    for document in chinanews_basedocuments:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "Chinanews" ], None, url_list )

    print "Done Document Change Test."

    # PHASE 3: Document Delete Detection

    print "Document Delete Test."
    RSSConnectorHelpers.remove_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\"+baghdadfeed_specialdocument )
    RSSConnectorHelpers.remove_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\"+allafrica_specialdocument )

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    url_list = []
    for document in baghdadfeed_basedocuments:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "Baghdadfeed" ], None, url_list )
    url_list = []
    for document in allafrica_basedocuments:
        url_list.append( make_rss_url(document) )
    ConnectorHelpers.search_check( [ "Allafrica" ], None, url_list )

    print "Done Document Delete Test."

    # PHASE 5: Delete Job

    print "Job Delete Test."
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    # Make sure the documents all went away
    ConnectorHelpers.search_check( [ "latah" ], None, [] )
    ConnectorHelpers.search_check( [ "atom" ], None, [] )
    ConnectorHelpers.search_check( [ "Allafrica" ], None, [] )
    ConnectorHelpers.search_check( [ "Chinanews" ], None, [] )
    ConnectorHelpers.search_check( [ "Jamestown" ], None, [] )
    ConnectorHelpers.search_check( [ "Baghdadfeed" ], None, [] )
    ConnectorHelpers.search_check( [ "sportschannel" ], None, [] )
    ConnectorHelpers.search_check( [ "bostoncom" ], None, [] )
    ConnectorHelpers.search_check( [ "modified" ], None, [] )

    # PHASE 5.2: Do a crawl of a single feed that has embedded, dechromed content
    # This test must also verify that chromed content is not taken when dechromed content is not available.

    print "Dechromed content test."

    # Define job
    job_id = RSSConnectorHelpers.define_rss_job_ui( username,
                        password,
                        "RSSJob",
                        "RSSConnection",
                        [ "http://"+make_rss_url("feed9.xml") ],
                        dechromed_mode="description",
                        chromed_mode="skip",
                        mappings=[("(.*)","1")])

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # We'd better be able to find a document with the keyword "cobblestones"...
    ConnectorHelpers.search_check( [ "cobblestones" ], None, [ make_rss_url("some_uningested_url.htm") ] )
    # We'd better NOT be able to find a document with the keyword "latah"
    ConnectorHelpers.search_check( [ "latah" ], None, [ ] )

    # Delete the job
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    print "Crawl a feeds with bad utf-8 character encoding, and make sure we got all the docs"

    # Define job
    job_id = RSSConnectorHelpers.define_rss_job_ui( username,
                        password,
                        "RSSJob",
                        "RSSConnection",
                        [ "http://"+make_rss_url("feed19.xml"), "http://"+make_rss_url("feed20.xml") ],
                        dechromed_mode="description",
                        chromed_mode="skip",
                        mappings=[("(.*)","1")])

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Measure success by whether we see certain documents, searched by url metadata
    for expected_doc in [ "www.nypost.com/seven/07042009/gossip/pagesix/levi_vs__sarah_palin_battle_continues_177489.htm",
            "www.nypost.com/seven/07042009/horoscope/libra.htm",
            "www.nypost.com/seven/07042009/news/regionalnews/manhattan/loyal_sis_sticks_up_for_ruth_177509.htm",
            "www.nypost.com/seven/07072009/news/regionalnews/wiseguy_in_77_hit_trial_178020.htm",
            "www.theflatulent.com/seven/04012010/entertainment/travel/_42_staunton__virginia_167773.htm",
            "www.theflatulent.com/seven/05152010/sports/boxing/opportunity_knocks_for_vargas_169473.htm",
            "www.theflatulent.com/seven/06072009/news/nationalnews/bam_threat_nut_busted_172944.htm" ]:
        ConnectorHelpers.search_check( [ "url:http://%s" % expected_doc ], None, [ expected_doc ] )
        
    # Delete the job
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    print "Assessing robots.txt combinations"

    url_list = []
    for document in latah_documents:
        url_list.append( make_rss_url(document) )

    redirected_url_list = []
    for document in redirected_documents:
        redirected_url_list.append( make_rss_url(document,location="/redirected") )

    # Cycle through a bunch more robots.txt variants.  Each variant should either show documents, or not.
    #robots.txt - Verbatim NYPost robots test, tests inclusion where agents matches, no disallow or allow for any interesting items, blank disallow lines, LF-style newlines,
    #                   check that comment at start of line doesn't disrupt parse.
    #robots_1.txt - Tests exclusion due to no matching agent, with subsequent "*" agent causing the disallow, also bad line: "alsdfkjasdjfhaklsdfh", LF-style newlines,
    #                   check that commented-out rule is actually ignored.
    #robots_2.txt - Tests agent match case insensitivity, and match in the middle of the agents value, LF-style newlines
    #robots_3.txt - Tests ordering, where "*" agent appears earlier than substring agent match, LF-style newlines
    #robots_4.txt - Tests precedence of allow operation over disallow operation for an agent, also specific path disallows, and CRLF-style newlines,
    #                   check that trailing comment is not disruptive.
    #robots_5.txt - Tests the other half of the allow/disallow precedence requirement, LF-style newlines
    #robots_6.txt - Test what happens when there is no matching agent at all (including * embedded in a string); LF-style newlines
    #robots_7.txt - A standard HTML page returned by www.11alive.com/robots.txt
    for robots_file,show_documents,show_redirected in [ ("robots.txt", True, True),
                                                                            ("robots_1.txt", False, False),
                                                                            ("robots_2.txt", True, True),
                                                                            ("robots_3.txt", True, True),
                                                                            ("robots_4.txt", False, True),
                                                                            ("robots_5.txt", True, True),
                                                                            ("robots_6.txt", True, True),
                                                                            ("robots_7.txt", True, True),
                                                                            ("robots_8.txt", True, False),
                                                                            ("robots_9.txt", True, True) ]:
        # Copy the specified robots file to the server
        RSSConnectorHelpers.add_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\robots.txt", "/root/rssfeeds/"+robots_file)

        # Restart metacarta-agents, so that robots cache is flushed.
        ConnectorHelpers.restart_agents()

        print "Assessing robots file %s..." % robots_file


        # Create a simple job and crawl it
        job_id = RSSConnectorHelpers.define_rss_job_ui( username,
                                password,
                                "RSSJob",
                                "RSSConnection",
                                [ "http://"+make_rss_url("feed1.xml"), "http://"+make_rss_url("feed8.xml") ],
                                mappings=[("(.*)","1")])

        # Run the job to completion
        ConnectorHelpers.start_job( job_id )
        ConnectorHelpers.wait_job_complete( job_id )

        # Wait until ingest has caught up
        ConnectorHelpers.wait_for_ingest( )

        # EITHER check for existence of documents, OR check for non-existence
        if show_documents:
            ConnectorHelpers.search_check( [ "latah" ], None, url_list )
        else:
            ConnectorHelpers.search_check( [ "latah" ], None, [] )

        if show_redirected:
            ConnectorHelpers.search_check( [ "redirected" ], None, redirected_url_list )
        else:
            ConnectorHelpers.search_check( [ "redirected" ], None, [] )

        # Delete the job
        ConnectorHelpers.delete_job( job_id )
        ConnectorHelpers.wait_job_deleted( job_id )

        # Remove the robots.txt that's on the server
        RSSConnectorHelpers.remove_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\robots.txt" )

    # Finally,  do a robots parsing report, and compare the stats against what we should expect.
    report_result = ConnectorHelpers.run_result_histogram_history_report_ui(username, password, "RSSConnection",
        ["robots parse"],
        entity_bin_regexp="()",
        result_bin_regexp="(.*)")

    # We expect SUCCESS, ERRORS, and HTML back - so that's three rows
    if len(report_result) != 3:
        raise Exception("Expecting exactly three rows in robots parsing report; got %d" % len(report_result))
    result_class_dict = {}
    for result_row in report_result:
        event_count = int(result_row["Event Count"])
        result_class = result_row["Result Class"]
        result_class_dict[result_class] = event_count

    for expected_event_count,expected_result_class in [(7,"success"),(2,"errors"),(1,"html")]:
        actual_event_count = result_class_dict[expected_result_class]
        if actual_event_count != expected_event_count:
            raise Exception("Actual event count %d and expected event count %d differ for robots parsing return class %s" % (actual_event_count,expected_event_count,expected_result_class))

    print "Done assessing robots.txt logic"

    # Recycle metacarta-agents, because otherwise the robots info gets cached
    ConnectorHelpers.restart_agents()

    # PHASE 5.5: Do a continuous crawl and make sure everything gets queued as expected.
    # (This tests feed requeuing as well as document origin date parsing and tracking).

    print "Document requeuing assessment."

    # To be able to look at this reasonably, we need a continuous job that does not refetch but DOES expire things out
    # some ridiculous amount (say about 10 years).
    job_id = RSSConnectorHelpers.define_rss_job_ui( username,
                        password,
                        "RSSJob",
                        "RSSConnection",
                        feed_list,
                        type="continuous",
                        default_feed_refetch_time_minutes=15,
                        min_feed_refetch_time_minutes=5,
                        bad_feed_refetch_time_minutes=60,
                        recrawlinterval="",
                        expireinterval=10*365*24*60
                        )

    # Now, we have to start the job, and wait long enough so that the first pass over all documents is complete.
    # Then, we run a document queue report for each feed and for one document from each feed, to be sure that the
    # queued action is in fact correct in all details
    ConnectorHelpers.start_job( job_id )
    # Wait 2 minutes; this should be enough time for the first pass to have completed
    time.sleep(120)
    assessment_time = time.time()

    # Now, get a document queue report for each seed document
    # The correct result depends on the details of each feed in the dataset.  But the important take-away is that
    # none of the feeds should have "expire" as their action.
    # Note: I've added an invalid feed (feed18.xml) to this list.
    for url in feed_names:
        expire_time, refetch_time = run_document_status_report( username, password, "RSSConnection", job_id, url )
        if expire_time != -1:
            raise Exception("For feed name %s, did not expect queued action to be 'expire'!" % url)
        # For the bad feed, we should see a refetch time greater than 15 minutes
        if url == "feed18.xml":
            if refetch_time - assessment_time <= 15 * 60:
                raise Exception("For invalid feed %s, expected rescan time to be bigger than 15 minutes, instead found %d seconds" % (refetch_time-assessment_time))

    # Each feed we have in the set has its own origination date format.  All we want to do here is
    # spot-check the expiration dates for a set of the documents in question.
    # Times are (in order):
    # Thu, 27 Dec 2007 00:00:00 -0500
    # Tue, 20 Nov 2007 16:00:08 -0500
    # Wed, 02 Jan 2008 02:26:55 -500
    # 2007/12/30 11:01
    # 2008-03-24T04:00:00Z
    for url_expiration in [ ("latah_2656.htm", 1198731600),
                                 ("baghdad_truth.html",1195592408),
                                 ("feed3.htm.*Itemid=42.*id=3018",1199258815),
                                 ("237617.htm",1199012460),
                                 ("winning_firm_gave_to_dimasi_charity.htm",1206331200)]:
        url_match, orig_date = url_expiration
        expire_time, refetch_time = run_document_status_report( username, password, "RSSConnection", job_id, url_match )
        if refetch_time != -1:
            raise Exception("Found a document %s that is queued for refetch rather than expiration!" % url_match)
        if expire_time != orig_date + 10*365*24*60*60:
            raise Exception("Expiration date for document %s is not what was expected; expected %f, found %f" % (url_match,orig_date+10*365*24*60*60,expire_time))

    # Abort the job
    ConnectorHelpers.abort_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Delete the job
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    # Now, run an ingestion report, and make sure there are no issues around our use of the ingestion system
    result = ConnectorHelpers.run_simple_history_report_api( "RSSConnection", ["document remove"], start_time=int(start_time*1000), result_regexp="400" )
    if len(result) > 0:
        raise Exception("Saw 400 errors as a result of document deletion!")

    ConnectorHelpers.delete_repository_connection_ui( username, password, "RSSConnection" )

    print "Done Job Delete Test."

    # PHASE 5.9: Make sure that the crawl fails in the right way when crawling through a proxy when the credentials are wrong!  This
    # guarantees that we are actually about to test what we think we are.
    # Define repository connection
    # Use proxy crawling, for variety
    url_list = []
    for feed_name in feed_names:
        url_list.append( "http://"+make_rss_url(feed_name) )

    RSSConnectorHelpers.define_rss_repository_connection_ui( username,
                                        password,
                                        "RSSConnection",
                                        "RSS Connection",
                                        "kwright@metacarta.com",
                                        proxy_host=rssProxyHost,
                                        proxy_port=rssProxyPort,
                                        proxy_domain=rssDomain,
                                        proxy_username="foo",
                                        proxy_password="foopassword" )

    job_id = RSSConnectorHelpers.define_rss_job_ui( username,
                        password,
                        "RSSJob",
                        "RSSConnection",
                        url_list )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # We'd better *not* be able to find a document with the keyword "latah"!
    ConnectorHelpers.search_check( [ "latah" ], None, [ ] )

    # Delete the job
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    # Delete the connection
    ConnectorHelpers.delete_repository_connection_ui( username,
                                        password,
                                        "RSSConnection" )

    # PHASE 6: Max fetch rate throttle test

    url_list = []
    for feed_name in feed_names:
        url_list.append( "http://"+make_rss_url(feed_name) )

    # Define repository connection
    RSSConnectorHelpers.define_rss_repository_connection_ui( username,
                                        password,
                                        "RSSConnection",
                                        "RSS Connection",
                                        "kwright@metacarta.com",
                                        max_fetches_per_minute_per_server=str(4) )

    job_id = RSSConnectorHelpers.define_rss_job_ui( username,
                        password,
                        "RSSJob",
                        "RSSConnection",
                        url_list )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    max_activity_results = ConnectorHelpers.run_max_activity_history_report_ui( username, password, "RSSConnection", [ "fetch" ], entity_bin_regexp="()" )
    if len(max_activity_results) != 1:
        raise Exception("Expecting 1 row in max activity report; got %d" % len(max_activity_results))
    rate_column = float(max_activity_results[0]["Highest Activity Rate [per min]"])
    if rate_column > 5.2:
        raise Exception("Maximum fetch rate exceeded the 1-sigma limit of 5.2 documents per minute; got %f" % rate_column)


    ConnectorHelpers.delete_repository_connection_ui( username, password, "RSSConnection" )

    # PHASE 7: Max bandwidth throttle test

    # Define repository connection
    # Use proxy crawling, for variety
    RSSConnectorHelpers.define_rss_repository_connection_ui( username,
                                        password,
                                        "RSSConnection",
                                        "RSS Connection",
                                        "kwright@metacarta.com",
                                        max_kbytes_per_second_per_server=str(1),
                                        proxy_host=rssProxyHost,
                                        proxy_port=rssProxyPort,
                                        proxy_domain=rssDomain,
                                        proxy_username=rssUser,
                                        proxy_password=rssPassword )

    job_id = RSSConnectorHelpers.define_rss_job_ui( username,
                        password,
                        "RSSJob",
                        "RSSConnection",
                        url_list )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    max_bandwidth_results = ConnectorHelpers.run_max_bandwidth_history_report_ui( username, password, "RSSConnection", [ "fetch" ], entity_bin_regexp="()", window_size_minutes="5" )
    if len(max_bandwidth_results) != 1:
        raise Exception("Expecting 1 row in max bandwidth report; got %d" % len(max_bandwidth_results))
    rate_column = float(max_bandwidth_results[0]["Highest Bandwidth [bps]"])
    if rate_column > 1200.0:
        raise Exception("Bandwidth has exceeded the one-sigma maximum value of 1200 bps: %f" % rate_column)

    ConnectorHelpers.delete_repository_connection_ui( username, password, "RSSConnection" )

    # The ad part of this test may need to run in both legacy and non-legacy modes
    run_ad_test_part( ad_domain_info )
    if perform_legacy_pass:
        # Set up legacy mode
        ConnectorHelpers.select_legacy_mode()
        # Run the ad part of the test again
        run_ad_test_part( ad_domain_info )
        # Cancel legacy mode
        ConnectorHelpers.cancel_legacy_mode()

    # Clean up the documents we dumped into the folders on server
    for document in transferable_documents:
        RSSConnectorHelpers.remove_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "rss\\"+document )

    for document in redirected_documents:
        RSSConnectorHelpers.remove_document(rssServerName, rssUser+"@"+rssDomain, rssPassword, "redirected_target_content\\"+document)

    LicenseMakerClient.revoke_license()

    ConnectorHelpers.delete_gts_outputconnection( )
    ConnectorHelpers.delete_crawler_user( username )

    ConnectorHelpers.teardown_connector_environment( )

    print "Basic RSSConnector tests PASSED"
