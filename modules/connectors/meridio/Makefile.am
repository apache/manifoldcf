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

SUBDIRS = lib

data_DATA = MetaCartaMeridioWebServiceInstaller.zip

baseagentssizedir = /var/lib/metacarta/java-agents-base-sizes
baseagentssize_DATA = meridioconnector-agents-base-size

incragentssizedir = /var/lib/metacarta/java-agents-incremental-sizes
incragentssize_DATA = meridioconnector-agents-incr-size

basetomcatsizedir = /var/lib/metacarta/tomcat-base-sizes
basetomcatsize_DATA = meridioconnector-tomcat-base-size

incrtomcatsizedir = /var/lib/metacarta/tomcat-incremental-sizes
incrtomcatsize_DATA = meridioconnector-tomcat-incr-size

baseutilitiessizedir = /var/lib/metacarta/java-utilities-base-sizes
baseutilitiessize_DATA = meridioconnector-utilities-base-size

incrutilitiessizedir = /var/lib/metacarta/java-utilities-incremental-sizes
incrutilitiessize_DATA = meridioconnector-utilities-incr-size

agentsregisterdir=/usr/lib/metacarta/agents-register.d
agentsregister_SCRIPTS = 50_meridioconnector

environmentlibdir = /usr/lib/metacarta/java-environment
environmentlib_DATA = metacarta-meridioconnector.jar metacarta-meridiostubs.jar metacarta-meridioxsd.jar

metacartabindir = /usr/lib/metacarta
metacartabin_DATA = meridioconnector.war meridio-client-config.wsdd

tomcatsecuritydir = /etc/tomcat5.5/policy.d
tomcatsecurity_DATA = 08meridioconnector.policy

WSDL_FILES = MeridioDMWS_axis.wsdl MeridioRMWS_axis.wsdl MetaCartaWS_axis.wsdl
XSD_FILES = DMDataSet.xsd RMClassificationDataSet.xsd RMDataSet.xsd

JAVA_FILES = com/metacarta/crawler/connectors/meridio/CommonsHTTPSender.java \
	com/metacarta/crawler/connectors/meridio/MeridioSecureSocketFactory.java \
	com/metacarta/crawler/connectors/meridio/MeridioClassContents.java \
	com/metacarta/crawler/connectors/meridio/MeridioConnector.java \
	com/metacarta/crawler/connectors/meridio/MeridioAuthority.java \
	com/metacarta/crawler/connectors/meridio/MatchMap.java \
	com/metacarta/crawler/connectors/meridio/MeridioLicense.java \
	com/metacarta/crawler/connectors/meridio/meridiowrapper/DMSearchResults.java \
	com/metacarta/crawler/connectors/meridio/meridiowrapper/MeridioDataSetException.java \
	com/metacarta/crawler/connectors/meridio/meridiowrapper/MeridioWrapper.java \
	com/metacarta/crawler/connectors/meridio/meridiowrapper/MeridioWrapperException.java \
	com/metacarta/crawler/connectors/meridio/ConnectionConfig.java

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
	/usr/share/java/castor.jar \
	/usr/share/java/xercesImpl.jar \
	/usr/share/java/xml-apis.jar \
	/usr/share/java/activation.jar \
	lib/mailapi.jar \
	$(AXIS_JAR_FILES)

JSP_JAR_FILES = $(JSP_CRAWLER_LIB_ENV_FILES) \
	metacarta-meridiostubs.jar \
	metacarta-meridioxsd.jar \
	$(JAR_FILES)

JSP_FILES = connectors/meridio/editconfig.jsp \
	connectors/meridio/editspec.jsp \
	connectors/meridio/headerconfig.jsp \
	connectors/meridio/headerspec.jsp \
	connectors/meridio/postconfig.jsp \
	connectors/meridio/postspec.jsp \
	connectors/meridio/viewconfig.jsp \
	connectors/meridio/viewspec.jsp \
	authorities/meridio/editconfig.jsp \
	authorities/meridio/headerconfig.jsp \
	authorities/meridio/postconfig.jsp \
	authorities/meridio/viewconfig.jsp

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

CASTOR_JAR_FILES = \
	/usr/share/java/castor.jar \
	/usr/share/java/commons-logging.jar \
	/usr/share/java/xercesImpl.jar \
	/usr/share/java/xml-apis.jar

space:= $(empty) $(empty)

JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(JAR_FILES)))
AXIS_CLASSPATH = $(subst jar$(space),jar:,$(strip $(AXIS_JAR_FILES)))
CASTOR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(CASTOR_JAR_FILES)))

$(JSP_CRAWLER_LIB_ENV_FILES) $(JSP_CRAWLER_TLD_ENV_FILES) $(JSP_CRAWLER_WEBINF_ENV_FILES) $(JSP_CRAWLER_ENV_FILES) : /usr/share/metacarta/crawler.war
	-rm -rf crawler-tmp
	mkdir -p crawler-tmp
	( cd crawler-tmp ; jar xf /usr/share/metacarta/crawler.war )

metacarta-meridiostubs.jar : $(WSDL_FILES)
	-rm -f $@
	-rm -rf wsdlclasses
	-rm -rf wsdljava
	mkdir -p wsdlclasses
	mkdir -p wsdljava
	( for filename in $(WSDL_FILES) ; do $(WSDL2JAVA) --timeout=0 --noImports -o wsdljava "$$filename" ; done )
	$(JAVAC) -classpath $(JAVA_CLASSPATH):$(AXIS_CLASSPATH) -d wsdlclasses -O `find wsdljava/ -name "*.java"`
	mkdir -p wsdlclasses/org/apache/axis/client
	$(JAR) cvf $@ -C wsdlclasses com/meridio/
	$(JAR) uvf $@ -C wsdlclasses org/tempuri/

metacarta-meridioxsd.jar : $(XSD_FILES)
	-rm -f $@
	-rm -rf xsdclasses
	-rm -rf xsdjava
	mkdir -p xsdclasses
	mkdir -p xsdjava
	( for filename in $(XSD_FILES) ; do echo "$$filename" >tempfile ; export tempvar=$$(sed s/.xsd// <tempfile) ; $(JAVA) -cp $(CASTOR_CLASSPATH) org.exolab.castor.builder.SourceGeneratorMain -i "$$filename" -f -dest xsdjava -package com.metacarta.crawler.connectors.meridio.$$tempvar ;  done )
	$(JAVAC) -classpath $(JAVA_CLASSPATH):$(CASTOR_CLASSPATH) -d xsdclasses -O `find xsdjava/ -name "*.java"`
	$(JAR) cvf $@ -C xsdclasses com/

$(CLASS_FILES) : $(JAVA_FILES) $(JAR_FILES) metacarta-meridiostubs.jar metacarta-meridioxsd.jar
	mkdir -p classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):metacarta-meridiostubs.jar:metacarta-meridioxsd.jar:$(JAR_CLASSPATH) -d classtemp/ $(JAVA_FILES)

CLASS_LIST = $(subst .java,.class, $(JAVA_FILES)) $(subst .java,\$$*.class, $(JAVA_FILES))

manifest : $(CLASS_FILES)
	( cd classtemp ; ls -1 $(CLASS_LIST) >../manifest || true )

metacarta-meridioconnector.jar : $(CLASS_FILES) manifest
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

$(JSP_JAVA_FILES) : $(JSP_FILES) $(JSP_LOCAL_ENV_FILES) $(JSP_LOCAL_WEBINF_ENV_FILES) $(JSP_LOCAL_TLD_ENV_FILES) $(JSP_LOCAL_LIB_ENV_FILES) $(JSP_JAR_FILES) metacarta-meridioconnector.jar
	rm -rf jsptmp
	mkdir -p jsptmp/
	env JAVA_HOME=$(JAVA_HOME) $(JSPC) -v -l -s -d jsptmp/ -classpath $(CLASSPATH):metacarta-meridioconnector.jar:$(JSP_JAR_CLASSPATH):$(JSP_CLASSPATH) -p com.metacarta.crawler -uriroot . $(JSP_FILES)

$(JSP_CLASS_FILES) : $(JSP_JAVA_FILES) $(JSP_JAR_FILES) metacarta-meridioconnector.jar
	$(JAVAC) -O -classpath $(JAVA_CLASSPATH):metacarta-meridioconnector.jar:$(JSP_JAR_CLASSPATH):$(JSP_CLASSPATH) -d jsptmp/ $(JSP_JAVA_FILES)

meridioconnector.war : $(JSP_FILES) $(JSP_CLASS_FILES)
	-rm -f $@
	$(JAR) cvf $@ $(JSP_FILES)

MetaCartaMeridioWebServiceInstaller.zip : 
	-rm -rf webservicetmp/
	mkdir -p webservicetmp
	cp "webservice/Installation readme.txt" webservicetmp/readme.txt
	cp -r "webservice/Web Service/Installation files" webservicetmp
	( cd webservicetmp ; find -path "*/.svn" -prune -o -print | zip ../MetaCartaMeridioWebServiceInstaller.zip -@ )

clean-local: 
	-rm -f manifest
	-rm -f metacarta-meridioconnector.jar
	-rm -f metacarta-meridiostubs.jar
	-rm -f metacarta-meridioxsd.jar
	-rm -f meridioconnector.war
	-rm -f MetaCartaMeridioWebServiceInstaller.zip
	-rm -rf classtemp/
	-rm -rf webservicetmp/
	-rm -rf wsdljava/
	-rm -rf wsdlclasses/
	-rm -rf xsdjava/
	-rm -rf xsdclasses/
	-rm -rf jsptmp/
	-rm -rf crawler-tmp/
	-rm -f $(JSP_LOCAL_ENV_FILES)
	-rm -rf WEB-INF/
