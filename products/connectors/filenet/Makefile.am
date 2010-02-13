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
baseagentssize_DATA = filenetconnector-agents-base-size

incragentssizedir = /var/lib/metacarta/java-agents-incremental-sizes
incragentssize_DATA = filenetconnector-agents-incr-size

basetomcatsizedir = /var/lib/metacarta/tomcat-base-sizes
basetomcatsize_DATA = filenetconnector-tomcat-base-size

incrtomcatsizedir = /var/lib/metacarta/tomcat-incremental-sizes
incrtomcatsize_DATA = filenetconnector-tomcat-incr-size

baseutilitiessizedir = /var/lib/metacarta/java-utilities-base-sizes
baseutilitiessize_DATA = filenetconnector-utilities-base-size

incrutilitiessizedir = /var/lib/metacarta/java-utilities-incremental-sizes
incrutilitiessize_DATA = filenetconnector-utilities-incr-size

agentsregisterdir=/usr/lib/metacarta/agents-register.d
agentsregister_SCRIPTS = 50_filenetconnector

javasharelibdir = /usr/share/java
javasharelib_DATA = \
	metacarta-filenetconnector-server.jar \
	metacarta-filenetconnector-registry.jar \
	metacarta-filenetconnector-ifc.jar \
	metacarta-filenetconnector-stub.jar \
	metacarta-filenetconnector-skel.jar \
	metacarta-filenetconnector-impl.jar \
	metacarta-filenetconnector.jar

securitylibdir = /etc/tomcat5.5/policy.d
securitylib_DATA = 08filenetconnector.policy

metacartasharedir = /usr/share/metacarta
metacartashare_DATA = filenetconnector.war

metacartabindir = /usr/lib/metacarta
metacartabin_SCRIPTS = filenetconnector-rmiregistry \
	filenetconnector-server \
	filenet-log-clean

etcdir = /etc/init.d
etc_SCRIPTS = metacarta-filenetconnector-rmiregistry metacarta-filenetconnector-server

JAVA_FILES = com/metacarta/crawler/connectors/filenet/FilenetConnector.java \
	com/metacarta/crawler/connectors/filenet/DocClassSpec.java \
	com/metacarta/crawler/connectors/filenet/FilenetLicense.java

IFC_JAVA_FILES = \
	com/metacarta/crawler/common/filenet/IFilenetFactory.java \
	com/metacarta/crawler/common/filenet/IFilenet.java \
	com/metacarta/crawler/common/filenet/FilenetException.java \
	com/metacarta/crawler/common/filenet/FileInfo.java \
	com/metacarta/crawler/common/filenet/FileSeedInfo.java \
	com/metacarta/crawler/common/filenet/NameDescription.java \
	com/metacarta/crawler/common/filenet/DocumentClassDefinition.java \
	com/metacarta/crawler/common/filenet/MetadataFieldDefinition.java \
	com/metacarta/crawler/common/filenet/RMILocalSocketFactory.java \
	com/metacarta/crawler/common/filenet/RMILocalClientSocketFactory.java \
	com/metacarta/crawler/common/filenet/LocalClientSocket.java

IMPL_JAVA_FILES = \
	com/metacarta/crawler/common/filenet/FilenetFactoryImpl.java \
	com/metacarta/crawler/common/filenet/FilenetImpl.java

SERVER_JAVA_FILES = \
	com/metacarta/crawler/server/filenet/Filenet.java

REGISTRY_JAVA_FILES = \
	com/metacarta/crawler/registry/filenet/Filenet.java

IMPL_STUB_JAVA_FILES = $(subst .java,_Stub.java,$(IMPL_JAVA_FILES))
IMPL_SKEL_JAVA_FILES = $(subst .java,_Skel.java,$(IMPL_JAVA_FILES))

CLASS_FILES = $(addprefix classtemp/, $(subst .java,.class, $(JAVA_FILES)))
IFC_CLASS_FILES = $(addprefix ifc_classtemp/, $(subst .java,.class, $(IFC_JAVA_FILES)))
IMPL_CLASS_FILES = $(addprefix impl_classtemp/, $(subst .java,.class, $(IMPL_JAVA_FILES)))
STUB_CLASS_FILES = $(addprefix stub_classtemp/, $(subst .java,.class, $(IMPL_STUB_JAVA_FILES)))
SKEL_CLASS_FILES = $(addprefix skel_classtemp/, $(subst .java,.class, $(IMPL_SKEL_JAVA_FILES)))
SERVER_CLASS_FILES = $(addprefix server_classtemp/, $(subst .java,.class, $(SERVER_JAVA_FILES)))
REGISTRY_CLASS_FILES = $(addprefix registry_classtemp/, $(subst .java,.class, $(REGISTRY_JAVA_FILES)))

JAR_FILES = /usr/share/java/jdbcpool-0.99.jar \
	/usr/share/java/log4j-1.2.jar \
	/usr/share/java/metacarta-core.jar \
	/usr/share/java/metacarta-agents.jar \
	/usr/share/java/metacarta-pullagent.jar \
	/usr/lib/metacarta/java-common/metacarta-license.jar

FILENET_JAR_FILES = \
	/usr/share/java/ibm-activation.jar \
	/usr/share/java/ibm-antlr.jar \
	/usr/share/java/ibm-builtin_serialization.jar \
	/usr/share/java/ibm-core_services_client.jar \
	/usr/share/java/ibm-jaas.jar \
	/usr/share/java/ibm-Jace.jar \
	/usr/share/java/ibm-jaxm.jar \
	/usr/share/java/ibm-jaxrpc.jar \
	/usr/share/java/ibm-jetty.jar \
	/usr/share/java/ibm-runner.jar \
	/usr/share/java/ibm-saaj.jar \
	/usr/share/java/ibm-validator.jar \
	/usr/share/java/ibm-wasp.jar \
	/usr/share/java/ibm-wsdl_api.jar \
	/usr/share/java/ibm-xercesImpl.jar \
	/usr/share/java/ibm-xml-apis.jar \
	/usr/share/java/ibm-xmlParserAPIs.jar

JSP_JAR_FILES = $(JSP_CRAWLER_LIB_ENV_FILES) $(JAR_FILES)

JSP_FILES =  connectors/filenet/editconfig.jsp \
        connectors/filenet/editspec.jsp \
        connectors/filenet/headerconfig.jsp \
        connectors/filenet/headerspec.jsp \
        connectors/filenet/postconfig.jsp \
        connectors/filenet/postspec.jsp \
	connectors/filenet/viewconfig.jsp \
        connectors/filenet/viewspec.jsp

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

$(JSP_CRAWLER_LIB_ENV_FILES) $(JSP_CRAWLER_WEBINF_ENV_FILES) $(JSP_CRAWLER_ENV_FILES) $(JSP_CRAWLER_TLD_ENV_FILES) : /usr/share/metacarta/crawler.war
	-rm -rf crawler-tmp
	mkdir -p crawler-tmp
	( cd crawler-tmp ; jar xf /usr/share/metacarta/crawler.war )

JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(JAR_FILES)))
FILENET_JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(FILENET_JAR_FILES)))

IMPL_CLASS_NAMES = $(subst /,.,$(subst .java,$(space),$(IMPL_JAVA_FILES)))

$(IMPL_SKEL_JAVA_FILES) $(IMPL_STUB_JAVA_FILES) : $(IMPL_CLASS_FILES) metacarta-filenetconnector-ifc.jar metacarta-filenetconnector-impl.jar $(FILENET_JAR_FILES)
	$(RMIC) -vcompat -nowarn -keep -nowrite -classpath $(FILENET_JAR_CLASSPATH):metacarta-filenetconnector-ifc.jar:metacarta-filenetconnector-impl.jar $(IMPL_CLASS_NAMES)

$(IFC_CLASS_FILES) : $(IFC_JAVA_FILES)
	mkdir -p ifc_classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH) -d ifc_classtemp/ $(IFC_JAVA_FILES)

$(IMPL_CLASS_FILES) : $(IMPL_JAVA_FILES) metacarta-filenetconnector-ifc.jar $(FILENET_JAR_FILES)
	mkdir -p impl_classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(FILENET_JAR_CLASSPATH):metacarta-filenetconnector-ifc.jar -d impl_classtemp/ $(IMPL_JAVA_FILES)

$(CLASS_FILES) : $(JAVA_FILES) metacarta-filenetconnector-ifc.jar $(JAR_FILES)
	mkdir -p classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(JAR_CLASSPATH):metacarta-filenetconnector-ifc.jar -d classtemp/ $(JAVA_FILES)

$(SERVER_CLASS_FILES) : $(SERVER_JAVA_FILES) $(FILENET_JAR_FILES) metacarta-filenetconnector-ifc.jar metacarta-filenetconnector-impl.jar
	mkdir -p server_classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(FILENET_JAR_CLASSPATH):metacarta-filenetconnector-ifc.jar:metacarta-filenetconnector-impl.jar -d server_classtemp/ $(SERVER_JAVA_FILES)

$(REGISTRY_CLASS_FILES) : $(REGISTRY_JAVA_FILES) metacarta-filenetconnector-ifc.jar
	mkdir -p registry_classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):metacarta-filenetconnector-ifc.jar -d registry_classtemp/ $(REGISTRY_JAVA_FILES)

$(SKEL_CLASS_FILES) : $(IMPL_SKEL_JAVA_FILES) metacarta-filenetconnector-ifc.jar metacarta-filenetconnector-impl.jar $(FILENET_JAR_FILES)
	mkdir -p skel_classtemp/
	$(JAVAC) -O -classpath $(JAVA_CLASSPATH):$(FILENET_JAR_CLASSPATH):metacarta-filenetconnector-ifc.jar:metacarta-filenetconnector-impl.jar -d skel_classtemp/ $(IMPL_SKEL_JAVA_FILES)

$(STUB_CLASS_FILES) : $(IMPL_STUB_JAVA_FILES) metacarta-filenetconnector-ifc.jar
	mkdir -p stub_classtemp/
	$(JAVAC) -O -classpath $(JAVA_CLASSPATH):metacarta-filenetconnector-ifc.jar -d stub_classtemp/ $(IMPL_STUB_JAVA_FILES)

metacarta-filenetconnector-ifc.jar : $(IFC_CLASS_FILES)
	-rm -f $@
	-( cd ifc_classtemp ; $(JAR) cvf ../$@ com/metacarta/ )

metacarta-filenetconnector.jar : $(CLASS_FILES)
	-rm -f $@
	-( cd classtemp ; $(JAR) cvf ../$@ com/metacarta/ )

metacarta-filenetconnector-server.jar : $(SERVER_CLASS_FILES)
	-rm -f $@
	-( cd server_classtemp ; $(JAR) cvf ../$@ com/metacarta/ )

metacarta-filenetconnector-registry.jar : $(REGISTRY_CLASS_FILES)
	-rm -f $@
	-( cd registry_classtemp ; $(JAR) cvf ../$@ com/metacarta/ )

metacarta-filenetconnector-impl.jar : $(IMPL_CLASS_FILES)
	-rm -f $@
	-( cd impl_classtemp ; $(JAR) cvf ../$@ com/metacarta/ )

metacarta-filenetconnector-stub.jar : $(STUB_CLASS_FILES)
	-rm -f $@
	-( cd stub_classtemp ; $(JAR) cvf ../$@ com/metacarta/ )

metacarta-filenetconnector-skel.jar : $(SKEL_CLASS_FILES)
	-rm -f $@
	-( cd skel_classtemp ; $(JAR) cvf ../$@ com/metacarta/ )

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

$(JSP_JAVA_FILES) : $(JSP_FILES) $(JSP_LOCAL_ENV_FILES) $(JSP_LOCAL_WEBINF_ENV_FILES) $(JSP_LOCAL_TLD_ENV_FILES) $(JSP_LOCAL_LIB_ENV_FILES) $(JSP_JAR_FILES) metacarta-filenetconnector.jar
	rm -rf jsptmp
	mkdir -p jsptmp/
	env JAVA_HOME=$(JAVA_HOME) $(JSPC) -v -l -s -d jsptmp/ -classpath $(CLASSPATH):metacarta-filenetconnector.jar:metacarta-filenetconnector-ifc.jar:$(JSP_JAR_CLASSPATH):$(JSP_CLASSPATH) -p com.metacarta.crawler -uriroot . $(JSP_FILES)

$(JSP_CLASS_FILES) : $(JSP_JAVA_FILES) $(JSP_JAR_FILES) metacarta-filenetconnector.jar
	$(JAVAC) -O -classpath $(JAVA_CLASSPATH):metacarta-filenetconnector.jar:metacarta-filenetconnector-ifc.jar:$(JSP_JAR_CLASSPATH):$(JSP_CLASSPATH) -d jsptmp/ $(JSP_JAVA_FILES)

filenetconnector.war : $(JSP_FILES) $(JSP_CLASS_FILES)
	-rm -f $@
	$(JAR) cvf $@ $(JSP_FILES)

clean-local: 
	-rm -f metacarta-filenetconnector.jar
	-rm -f metacarta-filenetconnector-ifc.jar
	-rm -f metacarta-filenetconnector-impl.jar
	-rm -f metacarta-filenetconnector-stub.jar
	-rm -f metacarta-filenetconnector-skel.jar
	-rm -f metacarta-filenetconnector-server.jar
	-rm -f metacarta-filenetconnector-registry.jar
	-rm -f filenetconnector.war
	-rm -rf classtemp/
	-rm -rf ifc_classtemp/
	-rm -rf impl_classtemp/
	-rm -rf skel_classtemp/
	-rm -rf stub_classtemp/
	-rm -rf server_classtemp/
	-rm -rf crawler-tmp/
	-rm -rf jsptmp/
	-rm -f $(JSP_LOCAL_ENV_FILES)
	-rm -rf WEB-INF/
	-rm -f manifest
