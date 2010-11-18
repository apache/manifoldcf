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

import CrawlerEngineBase
import ConnectorHelpers
import ADTestHelpers
from wintools import adtools
from wintools import filetools
from sqatools import LeafblowerHacks

# Share connector engine

class ShareconnectorEngine(CrawlerEngineBase.CrawlerEngineBase):

    def __init__( self, samba_version="3.0", ntlm_mode="ntlmv2" ):
        CrawlerEngineBase.CrawlerEngineBase.__init__(self, samba_version=samba_version)
        self.job_id = None
        self.ntlm_mode = ntlm_mode

    def preclean( self ):

        """ Preclean the environment.  This blows away any exist jobs and (at least) related appliance
            data.  NOTE: This can be called before set_up() (or after tear_down() ). """
        CrawlerEngineBase.CrawlerEngineBase.preclean( self )
        ConnectorHelpers.reset_all()
        # Reset the ntlm status
        ConnectorHelpers.set_shareconnector_default_mode()
        # Re-enable maintenance
        ConnectorHelpers.enable_maintenance()
        # I keep getting documents thrust into the ingestion log even after the preclean is run.
        # So now, I'm gonna try waiting for the canary before doing the total purge, in hopes that will do the trick.
        ConnectorHelpers.wait_for_ingest()
        # Clean documents off of appliance.  This is necessary because the cleanup from the engine will otherwise
        # continue, and fill the logs inappropriately.
        LeafblowerHacks.total_purge()
        # Clear the ingestion log
        ADTestHelpers.clear_ingestion_log()


    def set_up( self ):
        CrawlerEngineBase.CrawlerEngineBase.set_up(self)
        ConnectorHelpers.disable_maintenance()
        if self.ntlm_mode == "ntlmv1":
            ConnectorHelpers.set_shareconnector_ntlmv1_mode()

    def tear_down( self ):
        ConnectorHelpers.set_shareconnector_default_mode()
        ConnectorHelpers.enable_maintenance()
        CrawlerEngineBase.CrawlerEngineBase.tear_down(self)

    def configure_connection( self,
                              connection_name,
                              server_fqdn,
                              server_username=None,
                              server_password=None,
                              server_shrouded_password=None ):

        """ Configure the connection.
            This method allows the instance to be configured with appropriate share connection information.
            Must be called before crawls can be specified! """
        if server_fqdn == None:
            server_fqdn = filetools.SambaParameters[self.samba_version]["SambaMediumLivedMachine"]
            server_domain = server_fqdn.split(".")[0].upper()
        else:
            domain_index = server_fqdn.find(".")
            if domain_index != -1:
                server_domain = server_fqdn[domain_index+1:len(server_fqdn)]
            else:
                server_domain = ""

        CrawlerEngineBase.CrawlerEngineBase.configure_connection( self, connection_name, server_fqdn, server_username, server_password,
                                                server_shrouded_password )

        # Define output connection
        ConnectorHelpers.define_gts_outputconnection( )

        # Define repository connection
        ConnectorHelpers.define_repositoryconnection( connection_name,
                                 connection_name,
                                 "com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector",
                                 configparams = [ "Server="+server_fqdn,
                                                  "User Name="+server_username,
                                                  "Domain/Realm="+server_domain,
                                                  "Password="+server_password ] )

    def close_connection( self ):

        """ Close the connection.
            Call this method before abandoning this object or starting a new connection. """

        ConnectorHelpers.delete_gts_outputconnection( )

        ConnectorHelpers.delete_repositoryconnection( self.connection_name )

        CrawlerEngineBase.CrawlerEngineBase.close_connection( self )



    def configure_job( self,
                       job_name,
                       shared_dirs,
                       assume_open=False,
                       force_public=False ):

        """ Configure a crawl.
            This method allows specification of what to crawl.
            <shared_dirs> must be a dictionary in the form specified
            by wintools.filetools.create_shares().
            If <assume_open> or <force_public> is true, the Share Crawler options
            Assume Open Share and Force Public, respectively, will be used. """

        CrawlerEngineBase.CrawlerEngineBase.configure_job( self, job_name, shared_dirs, assume_open, force_public )

        # Set up the job.  Note that the shared_dirs structure does not have a precise analog
        # in the jcifs connector world, so stuff like BTC path checking won't work.

        doc_spec_xml = '<?xml version="1.0" encoding="UTF-8"?><specification>'
        for dir in shared_dirs.keys():
            stuff = shared_dirs[dir]
            share_name = stuff["share_name"]
            if "filters" in stuff:
                filters = stuff["filters"]
            else:
                filters = [ '+:*:I:I:I' ]
            doc_spec_xml += '<startpoint path="%s">' % share_name
            for filter in filters:
                filterspec = filter.split(':')
                plus_or_minus = filterspec[0]
                filespec = filterspec[1]
                file_or_directory = filterspec[2]
                fingerprint = filterspec[3]
                btc = filterspec[4]
                if btc != 'I':
                    raise Exception("Shareconnector does not support BTC; can't call it with %s" % str(shared_dirs))
                if plus_or_minus == '+':
                    doc_spec_xml += '<include' + self.get_attrs(filespec,file_or_directory,fingerprint) + '/>'
                elif plus_or_minus == '-':
                    doc_spec_xml += '<exclude' + self.get_attrs(filespec,file_or_directory,fingerprint) + '/>'
                else:
                    raise Exception("Unknown value for filter field: '%s'" % plus_or_minus)

            doc_spec_xml += '</startpoint>'
        if force_public:
            doc_spec_xml += '<access token="%s"/>' % adtools.EveryoneSID
            doc_spec_xml += '<shareaccess token="%s"/>' % adtools.EveryoneSID
        doc_spec_xml += '</specification>'
        if self.job_id == None:
            self.job_id = ConnectorHelpers.define_job( job_name,
                                                       self.connection_name,
                                                       doc_spec_xml )
        else:
            ConnectorHelpers.change_job_doc_spec( self.job_id, doc_spec_xml )


    def get_attrs(self, filespec, file_or_directory, fingerprint):
        filespec = filespec.replace("\\","/")
        if filespec.endswith('.'):
            filespec = filespec[0:len(filespec)-1]
        rval = ' filespec="%s"' % filespec
        if file_or_directory == 'Y':
            rval += ' type="directory"'
        elif file_or_directory == 'N':
            rval += ' type="file"'
        elif file_or_directory == 'I':
            pass
        else:
            raise Exception("Illegal file-or-directory filter field: %s" % file_or_directory)

        if fingerprint == 'Y':
            rval += ' indexable="yes"'
        elif fingerprint == 'N':
            rval += ' indexable="no"'
        elif fingerprint == 'I':
            pass
        else:
            raise Exception("Illegal fingerprinting field: %s" % fingerprint)

        return rval

    def remove_job( self ):

        """ Tear down the current job/ingestion group.
            This will remove the data ingested with this job or ingestion group from the index. """

        ConnectorHelpers.delete_job( self.job_id )
        ConnectorHelpers.wait_job_deleted( self.job_id )

        self.job_id = None
        CrawlerEngineBase.CrawlerEngineBase.remove_job( self )


    def crawl_until_done( self ):

        """ Kick off a crawl with the current job, and wait until it completes.
            (The means of communicating with the server should already have been
            set up in the constructor). """

        ConnectorHelpers.start_job( self.job_id )
        ConnectorHelpers.wait_job_complete( self.job_id )
