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

data_DATA = MetaCartaSharePointWebServiceInstaller.zip

baseagentssizedir = /var/lib/metacarta/java-agents-base-sizes
baseagentssize_DATA = sharepointconnector-agents-base-size

incragentssizedir = /var/lib/metacarta/java-agents-incremental-sizes
incragentssize_DATA = sharepointconnector-agents-incr-size

basetomcatsizedir = /var/lib/metacarta/tomcat-base-sizes
basetomcatsize_DATA = sharepointconnector-tomcat-base-size

incrtomcatsizedir = /var/lib/metacarta/tomcat-incremental-sizes
incrtomcatsize_DATA = sharepointconnector-tomcat-incr-size

baseutilitiessizedir = /var/lib/metacarta/java-utilities-base-sizes
baseutilitiessize_DATA = sharepointconnector-utilities-base-size

incrutilitiessizedir = /var/lib/metacarta/java-utilities-incremental-sizes
incrutilitiessize_DATA = sharepointconnector-utilities-incr-size

agentsregisterdir=/usr/lib/metacarta/agents-register.d
agentsregister_SCRIPTS = 50_sharepointconnector

environmentlibdir = /usr/lib/metacarta/java-environment
environmentlib_DATA = metacarta-sharepointconnector.jar metacarta-sharepointstubs.jar

metacartabindir = /usr/lib/metacarta
metacartabin_DATA = sharepointconnector.war sharepoint-client-config.wsdd

tomcatsecuritydir = /etc/tomcat5.5/policy.d
tomcatsecurity_DATA = 08sharepointconnector.policy

WINBUILDER=python /usr/lib/metacarta/Winbuilder.py --verbose

WSDL_FILES = \
	webs.wsdl \
	DspSts.wsdl \
	Lists.wsdl \
	Permissions.wsdl \
	usergroup.wsdl \
	versions.wsdl \
	MCPermissions.wsdl

JAVA_FILES = \
	com/metacarta/crawler/connectors/sharepoint/SharepointLicense.java \
	com/metacarta/crawler/connectors/sharepoint/SPSProxyHelper.java \
	com/metacarta/crawler/connectors/sharepoint/SharePointRepository.java \
	com/metacarta/crawler/connectors/sharepoint/VersionsHeader.java \
	com/metacarta/crawler/connectors/sharepoint/CommonsHTTPSender.java \
	com/metacarta/crawler/connectors/sharepoint/MatchMap.java \
	com/metacarta/crawler/connectors/sharepoint/NameValue.java

CLASS_FILES = $(addprefix classtemp/, $(subst .java,.class, $(JAVA_FILES)))

AXIS_JAR_FILES = \
	/usr/share/java/axis.jar \
	/usr/share/java/commons-discovery-0.4.jar \
	/usr/share/java/jaxrpc.jar \
	/usr/share/java/saaj.jar \
	/usr/share/java/wsdl4j-1.6.2.jar

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
	$(AXIS_JAR_FILES)

JSP_JAR_FILES = $(JSP_CRAWLER_LIB_ENV_FILES) \
	metacarta-sharepointstubs.jar \
	$(JAR_FILES)

JSP_FILES = connectors/sharepoint/editconfig.jsp \
	connectors/sharepoint/editspec.jsp \
	connectors/sharepoint/headerconfig.jsp \
	connectors/sharepoint/headerspec.jsp \
	connectors/sharepoint/postconfig.jsp \
	connectors/sharepoint/postspec.jsp \
	connectors/sharepoint/viewconfig.jsp \
	connectors/sharepoint/viewspec.jsp

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

$(JSP_CRAWLER_LIB_ENV_FILES) $(JSP_CRAWLER_TLD_ENV_FILES) $(JSP_CRAWLER_WEBINF_ENV_FILES) $(JSP_CRAWLER_ENV_FILES) : /usr/share/metacarta/crawler.war
	-rm -rf crawler-tmp
	mkdir -p crawler-tmp
	( cd crawler-tmp ; jar xf /usr/share/metacarta/crawler.war )

JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(JAR_FILES)))
AXIS_CLASSPATH = $(subst jar$(space),jar:,$(strip $(AXIS_JAR_FILES)))

metacarta-sharepointstubs.jar : $(WSDL_FILES)
	-rm -f $@
	-rm -rf wsdlclasses
	-rm -rf wsdljava
	mkdir -p wsdlclasses
	mkdir -p wsdljava
	( for filename in $(WSDL_FILES) ; do $(WSDL2JAVA) -o wsdljava "$$filename" ; done )
	$(JAVAC) -classpath $(JAVA_CLASSPATH):$(AXIS_CLASSPATH) -d wsdlclasses -O `find wsdljava/ -name "*.java"`
	mkdir -p wsdlclasses/org/apache/axis/client
	$(JAR) cvf $@ -C wsdlclasses com/microsoft/

$(CLASS_FILES) : $(JAVA_FILES) $(JAR_FILES) metacarta-sharepointstubs.jar
	mkdir -p classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):metacarta-sharepointstubs.jar:$(JAR_CLASSPATH) -d classtemp/ $(JAVA_FILES)

CLASS_LIST = $(subst .java,.class, $(JAVA_FILES)) $(subst .java,\$$*.class, $(JAVA_FILES))

manifest : $(CLASS_FILES)
	( cd classtemp ; ls -1 $(CLASS_LIST) >../manifest || true )

metacarta-sharepointconnector.jar : $(CLASS_FILES) manifest
	-rm -f $@
	( cd classtemp ; $(JAR) cvf ../$@ $$(<../manifest) )

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

$(JSP_JAVA_FILES) : $(JSP_FILES) $(JSP_LOCAL_ENV_FILES) $(JSP_LOCAL_WEBINF_ENV_FILES) $(JSP_LOCAL_TLD_ENV_FILES) $(JSP_LOCAL_LIB_ENV_FILES) $(JSP_JAR_FILES) metacarta-sharepointconnector.jar
	rm -rf jsptmp
	mkdir -p jsptmp/
	env JAVA_HOME=$(JAVA_HOME) $(JSPC) -v -l -s -d jsptmp/ -classpath $(CLASSPATH):metacarta-sharepointconnector.jar:$(JSP_JAR_CLASSPATH):$(JSP_CLASSPATH) -p com.metacarta.crawler -uriroot . $(JSP_FILES)

$(JSP_CLASS_FILES) : $(JSP_JAVA_FILES) $(JSP_JAR_FILES) metacarta-sharepointconnector.jar
	$(JAVAC) -O -classpath $(JAVA_CLASSPATH):metacarta-sharepointconnector.jar:$(JSP_JAR_CLASSPATH):$(JSP_CLASSPATH) -d jsptmp/ $(JSP_JAVA_FILES)

sharepointconnector.war : $(JSP_FILES) $(JSP_CLASS_FILES)
	-rm -f $@
	$(JAR) cvf $@ $(JSP_FILES)

MetaCartaSharePointWebServiceInstaller.zip :
	-rm -rf webservicetmp/
	mkdir -p webservicetmp
	( cd webservice ; $(WINBUILDER) -m windows-dotnet-builder Package/MetaCarta.SharePoint.MCPermissionsService.wsp :build.bat )
	cp webservice/Package/MetaCarta.SharePoint.MCPermissionsService.wsp webservicetmp
	cp webservice/Package/setup.bat webservicetmp
	cp webservice/Package/upgrade.bat webservicetmp
	cp webservice/Package/remove.bat webservicetmp
	cp "webservice/Package/Installation Readme.txt" webservicetmp
	( cd webservicetmp ; find -path "*/.svn" -prune -o -print | zip ../MetaCartaSharePointWebServiceInstaller.zip -@ )

clean-local: 
	-rm -f manifest
	-rm -f metacarta-sharepointconnector.jar
	-rm -f metacarta-sharepointstubs.jar
	-rm -f sharepointconnector.war
	-rm -f MetaCartaSharePointWebServiceInstaller.zip
	-rm -rf classtemp/
	-rm -rf wsdljava/
	-rm -rf wsdlclasses/
	-rm -rf jsptmp/
	-rm -rf crawler-tmp/
	-rm -f $(JSP_LOCAL_ENV_FILES)
	-rm -rf WEB-INF/
