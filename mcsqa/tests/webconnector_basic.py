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

import pg
import sys
import time
import ConnectorHelpers
import WebConnectorHelpers
import sqatools.appliance
from wintools import sqa_domain_info
from wintools import filetools
from wintools import ambassador_client
from sqatools import LicenseMakerClient
import TestDocs
import VirtualBrowser

sys.path.append("/usr/lib/metacarta")

import MetaCartaVersion

# Server name to talk to
webServerName = None
# Domain
webDomain = None
# User
webUser = None
# Password
webPassword = None

# Server port to talk to
webServerPort = "81"
# Secure server port to talk to
webServerSecurePort = "444"
# Session authentication site web server
auth_web_server = "crystal.metacarta.com"

# Names of feeds
feed_names = [ "feed1.xml", "feed2.xml", "feed3.xml", "feed4.xml", "feed5.xml", "feed6.xml", "feed7.xml", "feed8.xml", "feed10.xml" ]

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

special_html_documents = [ "big-apple-barbe.html", "flying-disc-ranch.html" ]

# Transferable documents
unmappable_transferable_documents = latah_documents + jamestown_documents + allafrica_documents + \
        chinanews_documents + baghdadfeed_documents + bostoncomfeed_documents + atom_documents + [ "feed20.xml" ] + \
        special_html_documents
mappable_transferable_documents = feed_names + \
        [ "feed3.htm", "modified1.htm", "modified2.htm", "feed16.xml", "feed17.xml" ]
transferable_documents = unmappable_transferable_documents + mappable_transferable_documents

def make_web_url(folder_path, location=""):
    if int(webServerPort) == 80:
        return "%s%s/%s" % (webServerName,location,folder_path)
    else:
        return "%s:%s%s/%s" % (webServerName,webServerPort,location,folder_path)

def make_secure_web_url(folder_path, location=""):
    if int(webServerSecurePort) == 443:
        return "%s%s/%s" % (webServerName,location,folder_path)
    else:
        return "%s:%s%s/%s" % (webServerName,webServerSecurePort,location,folder_path)

def clear_robots_cache():
    """ Clean out robots cache. """
    ConnectorHelpers.shutdown_agents()

    # Clear out robots database table
    db = pg.DB( "metacarta", "localhost", 5432, None, None, "metacarta", "atracatem" )
    try:
        db.query( "DELETE FROM robotsdata" )
    finally:
        db.close()

    ConnectorHelpers.start_agents()

def clear_session_cache():
    """ Clean out robots cache. """
    ConnectorHelpers.shutdown_agents()

    # Clear out robots database table
    db = pg.DB( "metacarta", "localhost", 5432, None, None, "metacarta", "atracatem" )
    try:
        db.query( "DELETE FROM cookiedata" )
    finally:
        db.close()

    ConnectorHelpers.start_agents()

# Crawl user credentials
username = "testingest"
password = "testingest"

def preclean( ad_domain_info, print_errors=True ):
    ''' Clean up everything we might have done during the execution of this test.
        This will include all jobs and ingested documents. '''

    try:
        ConnectorHelpers.reset_all()
    except Exception, e:
        if print_errors:
            print "Error resetting all jobs"
            print e

    # Clean up the documents we dumped into the folders on the server
    for document in transferable_documents:
        try:
            WebConnectorHelpers.remove_document(webServerName, webUser+"@"+webDomain, webPassword, "rss\\"+document )
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % document
                print e

    # Clean up redirected documents
    for document in redirected_documents:
        try:
            WebConnectorHelpers.remove_document(webServerName, webUser+"@"+webDomain, webPassword, "redirected_target_content\\"+document)
        except Exception, e:
            if print_errors:
                print "Error deleting test document %s" % document
                print e

    # Clean up robots.txt
    try:
        WebConnectorHelpers.remove_document(webServerName, webUser+"@"+webDomain, webPassword, "rss\\robots.txt" )
    except Exception, e:
        if print_errors:
            print "Error removing robots.txt"
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

# Main
if __name__ == '__main__':

    # AD parameters
    ad_group = "76"
    srvrname = "w2k3-shp-76-1"

    if len(sys.argv) > 1:
        ad_group = sys.argv[1]

    ad_domain_info = sqa_domain_info.SQADomainInfo( ad_group )
    
    webDomain = ad_domain_info.dns_domain.upper()
    webServerName = getattr(ad_domain_info,"web_server_fqdn");

    # User
    webUser = ad_domain_info.realm_admin.split("@")[0]
    # Password
    webPassword = ad_domain_info.realm_admin_password

    print "Precleaning!"

    preclean( ad_domain_info, print_errors=False )
    clear_robots_cache()

    print "Setup Connector Environment."
    ConnectorHelpers.setup_connector_environment()

    print "Setting up ingestion user."
    ConnectorHelpers.create_crawler_user( username, password )

    print "Testing how UI handles bad license."
    sqatools.appliance.install_license(extra_services=[], detect_gdms=True)

    # Restart, since otherwise we may have already passed the license check
    ConnectorHelpers.restart_tomcat()
    time.sleep(10)

    WebConnectorHelpers.define_web_repository_connection_ui( username,
                                        password,
                                        "WEBConnection",
                                        "WEB Connection",
                                        "kwright@metacarta.com" )
    # Viewing the connection should NOT give 'Connection working'!
    saw_error = True
    try:
        ConnectorHelpers.view_repository_connection_ui( username, password, "WEBConnection" )
        saw_error = False
    except:
        pass
        
    if saw_error == False:
        raise Exception("Licensing off but did not see license error!")

    ConnectorHelpers.delete_repository_connection_ui( username, password, "WEBConnection" )
    
    LicenseMakerClient.revoke_license()

    print "Setting up license."
    sqatools.appliance.install_license(extra_services=["webConnector"], detect_gdms=True)

    ConnectorHelpers.define_gts_outputconnection( )
    
    # PHASE 0: Canonicalization rules test

    print "Canonicalization rules test."


    # The seed urls we will use for ALL tests are the following:
    canonicalization_seed_urls = []
    for url_args in [ "/s(qqq)/random/path/stuff;jsessionid=zzz?arg1=A&arg2=B&PHPSESSID=xxx&arg3=C&arg1=D&BVSession@@@@=yyy" ]:
        if int(webServerPort) == 80:
            new_url = "http://%s%s" % (webServerName,url_args)
        else:
            new_url = "http://%s:%s%s" % (webServerName,webServerPort,url_args)

        canonicalization_seed_urls += [ new_url ]

    # Loop over a set of combination tuples
    for canon_parameters, expected_results in [ (("",None,"yes","no","no","no","no"),["/s(qqq)/random/path/stuff;jsessionid=zzz?BVSession@@@@=yyy&PHPSESSID=xxx&arg1=A&arg1=D&arg2=B&arg3=C"]),
                                        (("",None,"no","yes","yes","yes","yes"),["/random/path/stuff?arg1=A&arg2=B&arg3=C&arg1=D"]),
                                        (("",None,"yes","no","yes","no","yes"),["/random/path/stuff;jsessionid=zzz?PHPSESSID=xxx&arg1=A&arg1=D&arg2=B&arg3=C"]) ]:

        # Since we're evaluating canonicalization using the report feature, set up and tear down connection for each round, so the history is clean
        # Set up a repository connection
        WebConnectorHelpers.define_web_repository_connection_ui( username,
                                        password,
                                        "WEBConnection",
                                        "WEB Connection",
                                        "kwright@metacarta.com" )

        # Build and run a job
        job_id = WebConnectorHelpers.define_web_job_ui( username,
                        password,
                        "WEBJob",
                        "WEBConnection",
                        canonicalization_seed_urls,
                        canonicalization_rules=[ canon_parameters ],
                        inclusions = [ "^http://%s:%s" % (webServerName,webServerPort) ] )

        # Run the job to completion
        ConnectorHelpers.start_job( job_id )
        ConnectorHelpers.wait_job_complete( job_id )

        # See what the report says about the fetches we did
        results = ConnectorHelpers.run_simple_history_report_api( "WEBConnection", [ "fetch" ] )
        # Throw the results into a map based on document identifier, so we can look uris up
        result_map = {}
        for result in results:
            document_uri = result["identifier"]
            result_map[document_uri] = result

        for expected_result in expected_results:
            if int(webServerPort) == 80:
                full_url = "http://%s%s" % (webServerName,expected_result)
            else:
                full_url = "http://%s:%s%s" % (webServerName,webServerPort,expected_result)

            if not result_map.has_key(full_url):
                raise Exception("Expected to find canonicalized URL fetch for %s, but didn't; actual: %s" % (full_url,str(results)))

        # Delete the job
        ConnectorHelpers.delete_job( job_id )
        ConnectorHelpers.wait_job_deleted( job_id )

        # Tear down the initial repository connection
        ConnectorHelpers.delete_repository_connection_ui( username, password, "WEBConnection" )

    # PHASE 1: Ingestion
    print "Ingestion Test."

    # Add all docs to the repository
    map = { "%server%" : webServerName }
    for document in mappable_transferable_documents:
        WebConnectorHelpers.add_document(webServerName, webUser+"@"+webDomain, webPassword, "rss\\"+document, "/root/rssfeeds/"+document, map=map)
    for document in unmappable_transferable_documents:
        WebConnectorHelpers.add_document(webServerName, webUser+"@"+webDomain, webPassword, "rss\\"+document, "/root/rssfeeds/"+document)
    # Add redirected documents
    for document in redirected_documents:
        WebConnectorHelpers.add_document(webServerName, webUser+"@"+webDomain, webPassword, "redirected_target_content\\"+document, "/root/rssfeeds/"+document)

    url_list = []
    for feed_name in feed_names:
        url_list.append( "https://"+make_secure_web_url(feed_name) )

    # Do one url that isn't in fact on the server, so we can see what the crawler does under those conditions, and two feeds that share the same dead link,
    # Also give it a feed that has illegal utf-8 characters and points to documents that will not be found.
    for feed_name in [ "does_not_exist.xml", "feed16.xml", "feed17.xml", "feed20.xml" ]:
        url_list.append( "https://"+make_secure_web_url(feed_name) )

    # Add in html documents which did not parse correctly before.  These should be fetched and parsed, without
    # blocking the job from completing.
    for doc_name in special_html_documents:
        url_list.append( "https://"+make_secure_web_url(doc_name) )

    # Define repository connection
    WebConnectorHelpers.define_web_repository_connection_ui( username,
                                        password,
                                        "WEBConnection",
                                        "WEB Connection",
                                        "kwright@metacarta.com",
                                        page_access_credentials=[{"type":"ntlm","domain":webDomain,"username":webUser,"password":webPassword}],
                                        certificates=[{"certificate":"livelinksrvr/bigiiscax509.cer"}])

    # Define job, including special documents that we want processed and extracted
    job_id = WebConnectorHelpers.define_web_job_ui( username,
                        password,
                        "WEBJob",
                        "WEBConnection",
                        url_list ,
                        user_metadata=[ ("test_metadata_1", "hello"), ("test_metadata_2", "there"), ("test_metadata_1", "charlie") ],
                        inclusions = [ "http://www\\.theflatulent\\.com", "^https://%s:%s" % (webServerName,webServerSecurePort), "^http://%s:%s" % (webServerName,webServerPort) ] )


    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # See if we can find the documents we just ingested
    
    # Special documents first - just want to be sure we saw them ingested properly...
    ConnectorHelpers.search_check( [ "velvety" ], None, [ make_secure_web_url( "big-apple-barbe.html" ) ] )
    ConnectorHelpers.search_check( [ "greenpoint" ], None, [ make_secure_web_url( "big-apple-barbe.html" ), make_secure_web_url( "flying-disc-ranch.html" ) ] )
    
    url_list = []
    for document in redirected_documents:
        url_list.append( make_web_url(document,location="/redirect_target") )
    url_list.append( make_secure_web_url( "feed8.xml" ) )
    ConnectorHelpers.search_check( [ "redirected" ], None, url_list )
    url_list = []
    for document in latah_documents:
        url_list.append( make_web_url(document) )
    url_list.append( make_secure_web_url( "feed1.xml" ) )
    url_list.append( make_secure_web_url( "feed8.xml" ) )
    ConnectorHelpers.search_check( [ "latah" ], None, url_list )
    url_list = []
    for document in atom_documents:
        url_list.append( make_web_url(document) )
    url_list.append( make_secure_web_url( "feed20.xml" ) )
    ConnectorHelpers.search_check( [ "atom" ], None, url_list )
    url_list = []
    for document in allafrica_documents:
        url_list.append( make_web_url(document) )
    url_list.append( make_secure_web_url( "feed4.xml" ) )
    url_list.append( make_web_url(allafrica_specialdocument) )
    ConnectorHelpers.search_check( [ "Allafrica" ], None, url_list )
    url_list = []
    for document in chinanews_documents:
        url_list.append( make_web_url(document) )
    url_list.append( make_web_url(chinanews_specialdocument) )
    ConnectorHelpers.search_check( [ "Chinanews" ], None, url_list )
    url_list = []
    for document in jamestown_documents:
        url_list.append( make_web_url(document) )
    url_list.append( make_secure_web_url( "feed5.xml" ) )
    url_list.append( make_web_url(jamestown_specialdocument) )
    ConnectorHelpers.search_check( [ "Jamestown" ], None, url_list )
    
    # Look for user metadata too
    ConnectorHelpers.search_check( [ "Jamestown", "metadata:test_metadata_1=hello" ], None, url_list )
    ConnectorHelpers.search_check( [ "Jamestown", "metadata:test_metadata_1=charlie" ], None, url_list )
    ConnectorHelpers.search_check( [ "Jamestown", "metadata:test_metadata_2=there" ], None, url_list )

    url_list = []
    for document in baghdadfeed_documents:
        url_list.append( make_web_url(document) )
    url_list.append( make_web_url(baghdadfeed_specialdocument) )
    ConnectorHelpers.search_check( [ "Baghdadfeed" ], None, url_list )
    url_list = []
    for document in sportschannel_documents:
        url_list.append( make_web_url(document) )
    ConnectorHelpers.search_check( [ "sportschannel" ], None, url_list )
    url_list = []
    for document in bostoncomfeed_documents:
        url_list.append( make_web_url(document) )
    ConnectorHelpers.search_check( [ "bostoncom" ], None, url_list )

    # For the feed that didn't exist, check to make sure exactly one fetch attempt was made.
    results = ConnectorHelpers.run_simple_history_report_api( "WEBConnection", [ "fetch" ], entity_regexp="does_not_exist\\.xml" )
    if len(results) != 1:
        raise Exception("Expected exactly one fetch attempt of non-existent feed, instead saw %d" % len(results))
    if results[0]["result_code"] != "404":
        raise Exception("Expected fetch result code of 404, instead saw %s" % results[0]["result_code"])

    # For the document that didn't exist, check that exactly one fetch attempt was made.
    results = ConnectorHelpers.run_simple_history_report_api( "WEBConnection", [ "fetch" ], entity_regexp="does_not_exist_on_server\\.htm" )
    if len(results) != 1:
        raise Exception("Expected exactly one fetch attempt of non-existent shared document, instead saw %d" % len(results))
    if results[0]["result_code"] != "404":
        raise Exception("Expected fetch result code of 404, instead saw %s" % results[0]["result_code"])

    # For the documents in the feed with bad utf-8 characters, make sure the appropriate fetches were all attempted.
    for url in [ "www\\.theflatulent\\.com/seven/04012010/entertainment/travel/_42_staunton__virginia_167773\\.htm",
            "www\\.theflatulent\\.com/seven/05152010/sports/boxing/opportunity_knocks_for_vargas_169473\\.htm",
            "www\\.theflatulent\\.com/seven/06072009/news/nationalnews/bam_threat_nut_busted_172944\\.htm" ]:
        full_url = "http://" + url
        results = ConnectorHelpers.run_simple_history_report_api( "WEBConnection", [ "fetch" ], entity_regexp=full_url )
        if len(results) != 1:
            raise Exception("Expected exactly one fetch attempt of %s, instead saw %d" % (full_url,len(results)))
        if results[0]["result_code"] != "-10":
            raise Exception("For %s, expected fetch result code of -10, instead saw %s" % (full_url,results[0]["result_code"]))
    
    # Success: done
    print "Done ingestion test."


    # PHASE 2: Document Change Detection

    print "Document Change Test."
    # Create two modified documents
    WebConnectorHelpers.version_document(webServerName, webUser+"@"+webDomain, webPassword, "rss\\"+chinanews_specialdocument, "/root/rssfeeds/modified1.htm" )
    WebConnectorHelpers.version_document(webServerName, webUser+"@"+webDomain, webPassword, "rss\\"+jamestown_specialdocument, "/root/rssfeeds/modified2.htm" )

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    # Wait until ingest has caught up
    ConnectorHelpers.wait_for_ingest( )

    # Look for state of index being right
    ConnectorHelpers.search_check( [ "modified" ], None, [ make_web_url(chinanews_specialdocument), make_web_url(jamestown_specialdocument) ] )
    url_list = []
    for document in jamestown_basedocuments:
        url_list.append( make_web_url(document) )
    url_list.append( make_secure_web_url("feed5.xml") )
    ConnectorHelpers.search_check( [ "Jamestown" ], None, url_list )
    url_list = []
    for document in chinanews_basedocuments:
        url_list.append( make_web_url(document) )
    ConnectorHelpers.search_check( [ "Chinanews" ], None, url_list )

    print "Done Document Change Test."

    # PHASE 3: Document Delete Detection

    print "Document Delete Test."
    WebConnectorHelpers.remove_document(webServerName, webUser+"@"+webDomain, webPassword, "rss\\"+baghdadfeed_specialdocument )
    WebConnectorHelpers.remove_document(webServerName, webUser+"@"+webDomain, webPassword, "rss\\"+allafrica_specialdocument )

    # Restart job, which should pick up the changes
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    url_list = []
    for document in baghdadfeed_basedocuments:
        url_list.append( make_web_url(document) )
    ConnectorHelpers.search_check( [ "Baghdadfeed" ], None, url_list )
    url_list = []
    for document in allafrica_basedocuments:
        url_list.append( make_web_url(document) )
    url_list.append( make_secure_web_url("feed4.xml") )
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


    print "Done Job Delete Test."

    print "Assessing robots.txt combinations"

    url_list = []
    for document in latah_documents:
        url_list.append( make_web_url(document) )
    url_list.append( make_web_url("feed1.xml"))
    url_list.append( make_web_url("feed8.xml"))

    redirected_url_list = []
    for document in redirected_documents:
        redirected_url_list.append( make_web_url(document,location="/redirect_target") )
    redirected_url_list.append( make_web_url("feed8.xml"))

    feed_url_list = [ make_web_url("feed1.xml"), make_web_url("feed8.xml") ]
    feed_redirected_url_list = [ make_web_url("feed8.xml") ]

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
    for robots_file,show_documents,show_redirected,show_feeds in [ ("robots.txt", True, True, False),
                                                                            ("robots_1.txt", False, False, False),
                                                                            ("robots_2.txt", True, True, False),
                                                                            ("robots_3.txt", True, True, False),
                                                                            ("robots_4.txt", False, True, True),
                                                                            ("robots_5.txt", True, True, False),
                                                                            ("robots_6.txt", True, True, False),
                                                                            ("robots_7.txt", True, True, False),
                                                                            ("robots_8.txt", True, False, True),
                                                                            ("robots_9.txt", True, True, True) ]:
        # Copy the specified robots file to the server
        WebConnectorHelpers.add_document(webServerName, webUser+"@"+webDomain, webPassword, "rss\\robots.txt", "/root/rssfeeds/"+robots_file)

        # Restart metacarta-agents, so that robots cache is flushed.
        clear_robots_cache()

        print "Assessing robots file %s..." % robots_file


        # Create a simple job and crawl it
        job_id = WebConnectorHelpers.define_web_job_ui( username,
                                password,
                                "WEBJob",
                                "WEBConnection",
                                [ "http://"+make_web_url("feed1.xml"), "http://"+make_web_url("feed8.xml") ],
                                inclusions = [ "^http://%s:%s" % (webServerName,webServerPort) ] )

        # Run the job to completion
        ConnectorHelpers.start_job( job_id )
        ConnectorHelpers.wait_job_complete( job_id )

        # Wait until ingest has caught up
        ConnectorHelpers.wait_for_ingest( )

        # EITHER check for existence of documents, OR check for non-existence
        if show_documents:
            ConnectorHelpers.search_check( [ "latah" ], None, url_list )
        else:
            if show_feeds:
                ConnectorHelpers.search_check( [ "latah" ], None, feed_url_list )
            else:
                ConnectorHelpers.search_check( [ "latah" ], None, [] )

        if show_redirected:
            ConnectorHelpers.search_check( [ "redirected" ], None, redirected_url_list )
        else:
            if show_feeds:
                ConnectorHelpers.search_check( ["redirected"], None, feed_redirected_url_list )
            else:
                ConnectorHelpers.search_check( [ "redirected" ], None, [] )

        # Delete the job
        ConnectorHelpers.delete_job( job_id )
        ConnectorHelpers.wait_job_deleted( job_id )

        # Remove the robots.txt that's on the server
        WebConnectorHelpers.remove_document(webServerName, webUser+"@"+webDomain, webPassword, "rss\\robots.txt" )

    # Finally, do a robots parsing report, and compare the stats against what we should expect.
    report_result = ConnectorHelpers.run_result_histogram_history_report_ui(username, password, "WEBConnection",
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

    ConnectorHelpers.delete_repository_connection_ui( username, password, "WEBConnection" )

    # Clear robot cache
    clear_robots_cache()

    # PHASE 5.9: Session authentication test
    print "Assessing session authentication..."
    
    # For this test, we set up crawls against several different sites.  These crawls are meant to force the crawl to go through one or more login sequences to obtain the content in question.
    clear_session_cache()
    
    # Define repository connection
    WebConnectorHelpers.define_web_repository_connection_ui( username,
                                        password,
                                        "WEBConnection",
                                        "WEB Connection",
                                        "kwright@metacarta.com",
                                        page_access_credentials=[ { "regexp" : ConnectorHelpers.regexp_encode("://%s:8082/" % auth_web_server),
                                                                                "type" : "basic",
                                                                                "username" : "geoff",
                                                                                "password" : "geoff" } ],
                                        session_access_credentials=[ { "regexp" : ConnectorHelpers.regexp_encode("://%s:8081/" % auth_web_server),
                                                                                    "loginpages" : [ { "regexp" : ConnectorHelpers.regexp_encode("://%s:8081/index.php" % auth_web_server),
                                                                                                                "pagetype" : "form",
                                                                                                                "matchexpr" : "^$",
                                                                                                                "parameters" : [ { "nameregexp" : "^username$",
                                                                                                                                            "value" : "geoff" },
                                                                                                                                        { "nameregexp" : "^password$",
                                                                                                                                            "password" : "geoff" } ] },
                                                                                                            { "regexp" : ConnectorHelpers.regexp_encode("://%s:8081/content_" % auth_web_server),
                                                                                                                "pagetype" : "link",
                                                                                                                "matchexpr" : ConnectorHelpers.regexp_encode("://%s:8081/index.php" % auth_web_server) } ] },
                                                                                    { "regexp" : ConnectorHelpers.regexp_encode("://%s:8083/" % auth_web_server),
                                                                                        "loginpages" : [ { "regexp" : ConnectorHelpers.regexp_encode("://%s:8083/dologin.php" % auth_web_server),
                                                                                                                    "pagetype" : "form",
                                                                                                                    "matchexpr" : "^$",
                                                                                                                    "parameters" : [ { "nameregexp" : "^username$",
                                                                                                                                                "value" : "geoff" },
                                                                                                                                            { "nameregexp" : "^password$",
                                                                                                                                                "password" : "geoff" } ] },
                                                                                                                { "regexp" : ConnectorHelpers.regexp_encode("://%s:8083/" % auth_web_server),
                                                                                                                    "pagetype" : "redirection",
                                                                                                                    "matchexpr" : ConnectorHelpers.regexp_encode("://%s:8083/" % auth_web_server) } ] } ] )

    job_id = WebConnectorHelpers.define_web_job_ui( username,
                        password,
                        "WEBJob",
                        "WEBConnection",
                        [ "http://%s:8081/index.php" % auth_web_server,
                          "http://%s:8082/protected.php" % auth_web_server,
                          "http://%s:8083/index.php" % auth_web_server ],
                        inclusions = [ ConnectorHelpers.regexp_encode("://%s" % auth_web_server) ] )

    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    
    ConnectorHelpers.wait_for_ingest()
    
    # Look for the two content documents we expect to have found
    ConnectorHelpers.search_check( [ "redirection" ], None, [ "%s:8083/content_1.php" % auth_web_server ] )
    ConnectorHelpers.search_check( [ "content" ], None, [ "%s:8081/content_1.php" % auth_web_server, "%s:8083/content_1.php" % auth_web_server, "%s:8082/protected.php" % auth_web_server ] )
    ConnectorHelpers.search_check( [ "basic" ], None, [ "%s:8082/protected.php" % auth_web_server ] )

    # Delete job and connection
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    ConnectorHelpers.delete_repository_connection_ui( username, password, "WEBConnection" )
    
    clear_session_cache()
    
    # PHASE 6: Max fetch rate throttle test
    print "Max fetch rate throttle test..."

    url_list = []
    for feed_name in feed_names:
        url_list.append( "http://"+make_web_url(feed_name) )

    # Define repository connection
    WebConnectorHelpers.define_web_repository_connection_ui( username,
                                        password,
                                        "WEBConnection",
                                        "WEB Connection",
                                        "kwright@metacarta.com",
                                        limits=[ { "regexp":"^[^\\.]", "fetchesperminute":str(4) } ] )

    job_id = WebConnectorHelpers.define_web_job_ui( username,
                        password,
                        "WEBJob",
                        "WEBConnection",
                        url_list,
                        inclusions = [ "^http://%s:%s" % (webServerName,webServerPort) ] )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )
    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    max_activity_results = ConnectorHelpers.run_max_activity_history_report_ui( username, password, "WEBConnection", [ "fetch" ], entity_bin_regexp="()" )
    if len(max_activity_results) != 1:
        raise Exception("Expecting 1 row in max activity report; got %d" % len(max_activity_results))
    rate_column = float(max_activity_results[0]["Highest Activity Rate [per min]"])
    if rate_column > 4.5:
        raise Exception("Maximum fetch rate exceeded the 1-sigma limit of 4.5 documents per minute; got %f" % rate_column)


    ConnectorHelpers.delete_repository_connection_ui( username, password, "WEBConnection" )

    # PHASE 7: Max bandwidth throttle test

    # Define repository connection
    WebConnectorHelpers.define_web_repository_connection_ui( username,
                                        password,
                                        "WEBConnection",
                                        "WEB Connection",
                                        "kwright@metacarta.com",
                                        limits=[ { "regexp":"^[^\\.]", "kbpersecond":str(1) } ] )

    job_id = WebConnectorHelpers.define_web_job_ui( username,
                        password,
                        "WEBJob",
                        "WEBConnection",
                        url_list,
                        inclusions = [ "^http://%s:%s" % (webServerName,webServerPort) ] )

    # Run the job to completion
    ConnectorHelpers.start_job( job_id )
    ConnectorHelpers.wait_job_complete( job_id )

    ConnectorHelpers.delete_job( job_id )
    ConnectorHelpers.wait_job_deleted( job_id )

    max_bandwidth_results = ConnectorHelpers.run_max_bandwidth_history_report_ui( username, password, "WEBConnection", [ "fetch" ], entity_bin_regexp="()", window_size_minutes="5" )
    if len(max_bandwidth_results) != 1:
        raise Exception("Expecting 1 row in max bandwidth report; got %d" % len(max_bandwidth_results))
    rate_column = float(max_bandwidth_results[0]["Highest Bandwidth [bps]"])
    if rate_column > 1200.0:
        raise Exception("Bandwidth has exceeded the one-sigma maximum value of 1200 bps: %f" % rate_column)

    ConnectorHelpers.delete_repository_connection_ui( username, password, "WEBConnection" )

    # Clean up the documents we dumped into the folders on server
    for document in transferable_documents:
        WebConnectorHelpers.remove_document(webServerName, webUser+"@"+webDomain, webPassword, "rss\\"+document )

    ConnectorHelpers.delete_gts_outputconnection( )

    LicenseMakerClient.revoke_license()

    ConnectorHelpers.delete_crawler_user( username )

    ConnectorHelpers.teardown_connector_environment( )

    print "Basic WebConnector tests PASSED"
