# $Id$

## Licensed to the Apache Software Foundation (ASF) under one or more
## contributor license agreements. See the NOTICE file distributed with
## this work for additional information regarding copyright ownership.
## The ASF licenses this file to You under the Apache License, Version 2.0
## (the "License"); you may not use this file except in compliance with
## the License. You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.

SUBDIRS = pullagent authorityservice

htmldir = /www

html_DATA = 

metacartausrlibdir = /usr/lib/metacarta
metacartausrlib_SCRIPTS = reset-crawler backup-crawler-configuration restore-crawler-configuration

postgresmaintdir = /usr/lib/metacarta/postgres-maintenance.d
postgresmaint_SCRIPTS = 30_analyze-reindex

baseagentssizedir = /var/lib/metacarta/java-agents-base-sizes
baseagentssize_DATA = crawler-agents-base-size

incragentssizedir = /var/lib/metacarta/java-agents-incremental-sizes
incragentssize_DATA = crawler-agents-incr-size

basetomcatsizedir = /var/lib/metacarta/tomcat-base-sizes
basetomcatsize_DATA = crawler-tomcat-base-size

incrtomcatsizedir = /var/lib/metacarta/tomcat-incremental-sizes
incrtomcatsize_DATA = crawler-tomcat-incr-size

baseutilitiessizedir = /var/lib/metacarta/java-utilities-base-sizes
baseutilitiessize_DATA = crawler-utilities-base-size

agentsregisterdir=/usr/lib/metacarta/agents-register.d
agentsregister_SCRIPTS = 20_crawler 99_crawler

incrutilitiessizedir = /var/lib/metacarta/java-utilities-incremental-sizes
incrutilitiessize_DATA = crawler-utilities-incr-size

tomcatpolicydir = /etc/tomcat5.5/policy.d
tomcatpolicy_DATA = 08crawler.policy

metacartabindir = /usr/share/metacarta
metacartabin_DATA = crawler.war

webappregdir = /usr/lib/metacarta/java-webapps
webappreg_DATA = crawler_webapp

healthcheckersdir = /usr/lib/metacarta/health_checkers.d
healthcheckers_SCRIPTS = check_authorities_alive.py check_schema_okay.py

# Script to perform PostgreSQL cloning for BPA
pydir = /usr/lib/metacarta/pgsql_clone.d
py_SCRIPTS = connector_framework.py

JSP_FILES = index.jsp \
	setupAdminProfile.jsp \
	editauthority.jsp \
	editconnection.jsp \
	editoutput.jsp \
	editjob.jsp \
	error.jsp \
	execute.jsp \
	listauthorities.jsp \
	listconnections.jsp \
	listoutputs.jsp \
	listjobs.jsp \
	showjobstatus.jsp \
	viewjob.jsp \
	viewconnection.jsp \
	viewauthority.jsp \
	viewoutput.jsp \
	adminDefaults.jsp \
	adminHeaders.jsp \
	checkAdminLogin.jsp \
	banner.jsp \
	navigation.jsp \
	maxactivityreport.jsp \
	maxbandwidthreport.jsp \
	resultreport.jsp \
	simplereport.jsp \
	documentstatus.jsp \
	queuestatus.jsp

HTML_FILES = style.css Metacarta_logo.png

CONF_FILES = WEB-INF/web.xml

TLD_FILES = WEB-INF/jsp/c.tld \
	WEB-INF/jsp/fmt.tld \
	WEB-INF/jsp/sql.tld \
	WEB-INF/jsp/x.tld

JSP_JAVA_FILES = $(addprefix jsptmp/com/metacarta/crawler/, $(subst .jsp,_jsp.java, $(JSP_FILES)))
JSP_CLASS_FILES = $(addprefix jsptmp/com/metacarta/crawler/, $(subst .jsp,_jsp.class, $(JSP_FILES)))

LIB_LICENSES= 

WEBAPP_JAR_FILES = WEB-INF/lib/jstl.jar \
	WEB-INF/lib/standard.jar \
	WEB-INF/lib/metacarta-uicore.jar

ENV_JAR_FILES = ../java-common/core/metacarta-core.jar \
	../java-agents/metacarta-agents.jar \
	pullagent/metacarta-pullagent.jar \
	../java-common/core/lib/jdbcpool-0.99.jar \
	/usr/share/java/log4j-1.2.jar

space:= $(empty) $(empty)

JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(ENV_JAR_FILES))):$(subst jar$(space),jar:,$(strip $(WEBAPP_JAR_FILES)))

WEB-INF/lib/metacarta-uicore.jar : ../java-common/ui/metacarta-uicore.jar
	cp $< $@

$(JSP_JAVA_FILES) : $(JSP_FILES) $(WEBAPP_JAR_FILES)
	-rm -rf jsptmp/
	mkdir jsptmp/
	env JAVA_HOME=$(JAVA_HOME) $(JSPC) -v -l -s -d jsptmp/ -classpath $(CLASSPATH):$(JAR_CLASSPATH) -p com.metacarta.crawler $(JSP_FILES)

$(JSP_CLASS_FILES) : $(JSP_JAVA_FILES)
	$(JAVAC) -O -classpath $(JAVA_CLASSPATH):$(JAR_CLASSPATH):$(JSP_CLASSPATH) -d jsptmp/ $^

crawler.war : $(JSP_FILES) $(CONF_FILES) $(TLD_FILES) $(HTML_FILES) $(JSP_CLASS_FILES) $(WEBAPP_JAR_FILES)
	-rm -f $@
	$(JAR) cvf $@ $(JSP_FILES) $(TLD_FILES) $(CONF_FILES) $(HTML_FILES) $(WEBAPP_JAR_FILES) $(LIB_LICENSES)

clean-local: 
	-rm -f crawler.war
	-rm -f WEB-INF/lib/metacarta-uicore.jar
	-rm -rf jsptmp/
	-rm -rf classtemp/
