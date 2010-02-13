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

SUBDIRS =

baseagentssizedir = /var/lib/metacarta/java-agents-base-sizes
baseagentssize_DATA = webconnector-agents-base-size

incragentssizedir = /var/lib/metacarta/java-agents-incremental-sizes
incragentssize_DATA = webconnector-agents-incr-size

basetomcatsizedir = /var/lib/metacarta/tomcat-base-sizes
basetomcatsize_DATA = webconnector-tomcat-base-size

incrtomcatsizedir = /var/lib/metacarta/tomcat-incremental-sizes
incrtomcatsize_DATA = webconnector-tomcat-incr-size

baseutilitiessizedir = /var/lib/metacarta/java-utilities-base-sizes
baseutilitiessize_DATA = webconnector-utilities-base-size

incrutilitiessizedir = /var/lib/metacarta/java-utilities-incremental-sizes
incrutilitiessize_DATA = webconnector-utilities-incr-size

agentsregisterdir=/usr/lib/metacarta/agents-register.d
agentsregister_SCRIPTS = 50_webconnector

environmentlibdir = /usr/lib/metacarta/java-environment
environmentlib_DATA = metacarta-webconnector.jar

metacartabindir = /usr/lib/metacarta
metacartabin_DATA = webconnector.war


JAVA_FILES = \
	com/metacarta/crawler/connectors/webcrawler/WebcrawlerConfig.java \
	com/metacarta/crawler/connectors/webcrawler/DataCache.java \
	com/metacarta/crawler/connectors/webcrawler/RobotsManager.java \
	com/metacarta/crawler/connectors/webcrawler/DNSManager.java \
	com/metacarta/crawler/connectors/webcrawler/CookieManager.java \
	com/metacarta/crawler/connectors/webcrawler/IThrottledConnection.java \
	com/metacarta/crawler/connectors/webcrawler/ThrottledFetcher.java \
	com/metacarta/crawler/connectors/webcrawler/ThrottleDescription.java \
	com/metacarta/crawler/connectors/webcrawler/CredentialsDescription.java \
	com/metacarta/crawler/connectors/webcrawler/AuthenticationCredentials.java \
	com/metacarta/crawler/connectors/webcrawler/PageCredentials.java \
	com/metacarta/crawler/connectors/webcrawler/SequenceCredentials.java \
	com/metacarta/crawler/connectors/webcrawler/LoginParameters.java \
	com/metacarta/crawler/connectors/webcrawler/FormData.java \
	com/metacarta/crawler/connectors/webcrawler/FormDataElement.java \
	com/metacarta/crawler/connectors/webcrawler/LoginCookies.java \
	com/metacarta/crawler/connectors/webcrawler/CookieSet.java \
	com/metacarta/crawler/connectors/webcrawler/TrustsDescription.java \
	com/metacarta/crawler/connectors/webcrawler/WebcrawlerConnector.java \
	com/metacarta/crawler/connectors/webcrawler/WebLicense.java

CLASS_FILES = $(addprefix classtemp/, $(subst .java,.class, $(JAVA_FILES)))

JAR_FILES = /usr/share/java/jdbcpool-0.99.jar \
	/usr/share/java/log4j-1.2.jar \
	/usr/share/java/metacarta-core.jar \
	/usr/share/java/metacarta-agents.jar \
	/usr/share/java/metacarta-pullagent.jar \
	/usr/lib/metacarta/java-common/metacarta-license.jar \
	/usr/share/java/commons-codec.jar \
	/usr/share/java/commons-collections.jar \
	/usr/share/java/commons-httpclient.jar \
	/usr/share/java/commons-logging.jar \
	/usr/lib/metacarta/java-common/metacarta-license.jar

JSP_JAR_FILES = $(JSP_CRAWLER_LIB_ENV_FILES) \
	$(JAR_FILES)

JSP_FILES = connectors/webcrawler/editconfig.jsp \
	connectors/webcrawler/editspec.jsp \
	connectors/webcrawler/headerconfig.jsp \
	connectors/webcrawler/headerspec.jsp \
	connectors/webcrawler/postconfig.jsp \
	connectors/webcrawler/postspec.jsp \
	connectors/webcrawler/viewconfig.jsp \
	connectors/webcrawler/viewspec.jsp

JSP_ENV_FILES = adminDefaults.jsp \
	adminHeaders.jsp \
	error.jsp

JSP_WEBINF_ENV_FILES = \
	web.xml

JSP_TLD_ENV_FILES = \
	c.tld \
	fmt.tld \
	sql.tld \
	x.tld

JSP_LIB_ENV_FILES = \
	jstl.jar \
	standard.jar \
	metacarta-uicore.jar

space:= $(empty) $(empty)

JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(JAR_FILES)))

$(JSP_CRAWLER_LIB_ENV_FILES) $(JSP_CRAWLER_TLD_ENV_FILES) $(JSP_CRAWLER_WEBINF_ENV_FILES) $(JSP_CRAWLER_ENV_FILES) : /usr/share/metacarta/crawler.war
	-rm -rf crawler-tmp
	mkdir -p crawler-tmp
	( cd crawler-tmp ; jar xf /usr/share/metacarta/crawler.war )

$(CLASS_FILES) : $(JAVA_FILES) $(JAR_FILES)
	mkdir -p classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(JAR_CLASSPATH) -d classtemp/ $(JAVA_FILES)

metacarta-webconnector.jar : $(CLASS_FILES)
	-rm -f $@
	$(JAR) cvf $@ -C classtemp com/metacarta/crawler/connectors/webcrawler/

JSP_LOCAL_ENV_FILES = $(JSP_ENV_FILES)
JSP_CRAWLER_ENV_FILES = $(addprefix crawler-tmp/, $(JSP_ENV_FILES))

JSP_LOCAL_WEBINF_ENV_FILES = $(addprefix WEB-INF/, $(JSP_WEBINF_ENV_FILES))
JSP_CRAWLER_WEBINF_ENV_FILES = $(addprefix crawler-tmp/WEB-INF/, $(JSP_WEBINF_ENV_FILES))

JSP_LOCAL_TLD_ENV_FILES = $(addprefix WEB-INF/jsp/, $(JSP_TLD_ENV_FILES))
JSP_CRAWLER_TLD_ENV_FILES = $(addprefix crawler-tmp/WEB-INF/jsp/, $(JSP_TLD_ENV_FILES))

JSP_LOCAL_LIB_ENV_FILES = $(addprefix WEB-INF/lib/, $(JSP_LIB_ENV_FILES))
JSP_CRAWLER_LIB_ENV_FILES = $(addprefix crawler-tmp/WEB-INF/lib/, $(JSP_LIB_ENV_FILES))

$(JSP_LOCAL_ENV_FILES) : $(JSP_CRAWLER_ENV_FILES)
	cp crawler-tmp/$@ .

$(JSP_LOCAL_WEBINF_ENV_FILES) : $(JSP_CRAWLER_WEBINF_ENV_FILES)
	mkdir -p WEB-INF
	cp crawler-tmp/$@ WEB-INF

$(JSP_LOCAL_TLD_ENV_FILES) : $(JSP_CRAWLER_TLD_ENV_FILES)
	mkdir -p WEB-INF/jsp
	cp crawler-tmp/$@ WEB-INF/jsp

$(JSP_LOCAL_LIB_ENV_FILES) : $(JSP_CRAWLER_LIB_ENV_FILES)
	mkdir -p WEB-INF/lib
	cp crawler-tmp/$@ WEB-INF/lib

JSP_JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(JSP_JAR_FILES)))

JSP_JAVA_FILES = $(addprefix jsptmp/com/metacarta/crawler/, $(subst .jsp,_jsp.java, $(JSP_FILES)))
JSP_CLASS_FILES = $(addprefix jsptmp/com/metacarta/crawler/, $(subst .jsp,_jsp.class, $(JSP_FILES)))

$(JSP_JAVA_FILES) : $(JSP_FILES) $(JSP_LOCAL_ENV_FILES) $(JSP_LOCAL_WEBINF_ENV_FILES) $(JSP_LOCAL_TLD_ENV_FILES) $(JSP_LOCAL_LIB_ENV_FILES) $(JSP_JAR_FILES) metacarta-webconnector.jar
	rm -rf jsptmp
	mkdir -p jsptmp/
	env JAVA_HOME=$(JAVA_HOME) $(JSPC) -v -l -s -d jsptmp/ -classpath $(CLASSPATH):metacarta-webconnector.jar:$(JSP_JAR_CLASSPATH):$(JSP_CLASSPATH) -p com.metacarta.crawler -uriroot . $(JSP_FILES)

$(JSP_CLASS_FILES) : $(JSP_JAVA_FILES) $(JSP_JAR_FILES) metacarta-webconnector.jar
	$(JAVAC) -O -classpath $(JAVA_CLASSPATH):metacarta-webconnector.jar:$(JSP_JAR_CLASSPATH):$(JSP_CLASSPATH) -d jsptmp/ $(JSP_JAVA_FILES)

webconnector.war : $(JSP_FILES) $(JSP_CLASS_FILES)
	-rm -f $@
	$(JAR) cvf $@ $(JSP_FILES)

clean-local: 
	-rm -f metacarta-webconnector.jar
	-rm -f webconnector.war
	-rm -rf classtemp/
	-rm -rf jsptmp/
	-rm -rf crawler-tmp/
	-rm -f $(JSP_LOCAL_ENV_FILES)
	-rm -rf WEB-INF/
