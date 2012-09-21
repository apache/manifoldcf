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


Getting Started
---------------

There are two ways to hook up security to Solr in this package.  The first is using a Query Parser plugin.
The second is using a Search Component.  In both cases, the first step is to have ManifoldCF installed and running.  
Further information can be found on the "how-to-build-and-deploy.html" documentation page.


Then, you will need to add fields to your Solr schema.xml file that can be used to contain document
authorization information.  There will need to be four of these fields, an 'allow' field for both
documents and shares, and a 'deny' field for both documents and shares.  For example:

  <field name="allow_token_document" type="string" indexed="true" stored="false" multiValued="true" required="false" default="__nosecurity__"/>
  <field name="allow_token_share" type="string" indexed="true" stored="false" multiValued="true" required="false" default="__nosecurity__"/>
  <field name="deny_token_document" type="string" indexed="true" stored="false" multiValued="true" required="false" default="__nosecurity__"/>
  <field name="deny_token_share" type="string" indexed="true" stored="false" multiValued="true" required="false" default="__nosecurity__"/>

Using the Query Parser Plugin
----------------------------

To set up the query parser plugin, modify your solrconfig.xml to add the query parser:

  <!-- ManifoldCF document security enforcement component -->
  <queryParser name="manifoldCFSecurity"
    class="org.apache.solr.mcf.ManifoldCFQParserPlugin">
    <str name="AuthorityServiceBaseURL">http://localhost:8345/mcf-authority-service</str>
  </queryParser>

Hook up the search component in the solrconfig.xml file wherever you want it, e.g.:

<requestHandler name="search" class="solr.SearchHandler" default="true">
  <lst name="appends">
    <str name="fq">{!manifoldCFSecurity}</str>
  </lst>
  ...
</requestHandler>


Using the Search Component
----------------------------

To set up the search component, modify your solrconfig.xml to add the search component:

  <!-- ManifoldCF document security enforcement component -->
  <searchComponent name="manifoldCFSecurity"
    class="org.apache.solr.mcf.ManifoldCFSearchComponent">
    <str name="AuthorityServiceBaseURL">http://localhost:8345/mcf-authority-service</str>
  </searchComponent>

Hook up the search component in the solrconfig.xml file wherever you want it, e.g.:

<requestHandler name="search" class="solr.SearchHandler" default="true">
  <arr name="last-components">
    <str>manifoldCFSecurity</str>
  </arr>
  ...
</requestHandler>

