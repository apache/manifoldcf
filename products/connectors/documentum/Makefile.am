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

SUBDIRS = commands

clonedir = /var/lib/cluster/clone_files_simple.d
clone_DATA = documentumconnector-clone-files

baseagentssizedir = /var/lib/metacarta/java-agents-base-sizes
baseagentssize_DATA = documentumconnector-agents-base-size

incragentssizedir = /var/lib/metacarta/java-agents-incremental-sizes
incragentssize_DATA = documentumconnector-agents-incr-size

basetomcatsizedir = /var/lib/metacarta/tomcat-base-sizes
basetomcatsize_DATA = documentumconnector-tomcat-base-size

incrtomcatsizedir = /var/lib/metacarta/tomcat-incremental-sizes
incrtomcatsize_DATA = documentumconnector-tomcat-incr-size

baseutilitiessizedir = /var/lib/metacarta/java-utilities-base-sizes
baseutilitiessize_DATA = documentumconnector-utilities-base-size

incrutilitiessizedir = /var/lib/metacarta/java-utilities-incremental-sizes
incrutilitiessize_DATA = documentumconnector-utilities-incr-size

agentsregisterdir=/usr/lib/metacarta/agents-register.d
agentsregister_SCRIPTS = 50_documentumconnector

javasharelibdir = /usr/share/java
javasharelib_DATA = \
	metacarta-documentumconnector-server.jar \
	metacarta-documentumconnector-registry.jar \
	metacarta-documentumconnector-ifc.jar \
	metacarta-documentumconnector-stub.jar \
	metacarta-documentumconnector-skel.jar \
	metacarta-documentumconnector-impl.jar \
	metacarta-documentumconnector.jar

securitylibdir = /etc/tomcat5.5/policy.d
securitylib_DATA = 08documentumconnector.policy

metacartasharedir = /usr/share/metacarta
metacartashare_DATA = documentumconnector.war

metacartabindir = /usr/lib/metacarta
metacartabin_SCRIPTS = documentumconnector-rmiregistry \
	documentumconnector-server \
	documentumconnector-monitor \
	documentumconnector-monitor-daemon \
	documentum-log-clean

etcdir = /etc/init.d
etc_SCRIPTS = metacarta-documentumconnector-rmiregistry metacarta-documentumconnector-server metacarta-documentumconnector-monitor

JAVA_FILES = com/metacarta/crawler/connectors/DCTM/DCTM.java \
	com/metacarta/crawler/connectors/DCTM/DCTMLicense.java \
	com/metacarta/crawler/connectors/DCTM/MatchMap.java \
	com/metacarta/crawler/authorities/DCTM/AuthorityConnector.java

IFC_JAVA_FILES = \
	com/metacarta/crawler/common/DCTM/IDocumentumFactory.java \
	com/metacarta/crawler/common/DCTM/IDocumentum.java \
	com/metacarta/crawler/common/DCTM/IDocumentumResult.java \
	com/metacarta/crawler/common/DCTM/IDocumentumObject.java \
	com/metacarta/crawler/common/DCTM/DocumentumException.java \
	com/metacarta/crawler/common/DCTM/RMILocalSocketFactory.java \
	com/metacarta/crawler/common/DCTM/RMILocalClientSocketFactory.java \
	com/metacarta/crawler/common/DCTM/LocalClientSocket.java

IMPL_JAVA_FILES = \
	com/metacarta/crawler/common/DCTM/DocumentumFactoryImpl.java \
	com/metacarta/crawler/common/DCTM/DocumentumImpl.java \
	com/metacarta/crawler/common/DCTM/DocumentumResultImpl.java \
	com/metacarta/crawler/common/DCTM/DocumentumObjectImpl.java

SERVER_JAVA_FILES = \
	com/metacarta/crawler/server/DCTM/DCTM.java

REGISTRY_JAVA_FILES = \
	com/metacarta/crawler/registry/DCTM/DCTM.java


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

DOCUMENTUM_JAR_FILES = \
	/usr/share/java/DmcRecords.jar \
	/usr/share/java/messageService.jar \
	/usr/share/java/messageArchive.jar \
	/usr/share/java/All-MB.jar \
	/usr/share/java/XformsCommon.jar \
	/usr/share/java/bpmutil.jar \
	/usr/share/java/bsf.jar \
	/usr/share/java/castor-0.9.5.2.jar \
	/usr/share/java/ci.jar \
	/usr/share/java/collaboration.jar \
	/usr/share/java/commons-jxpath.jar \
	/usr/share/java/ctsTransform.jar \
	/usr/share/java/ctsTransformImpl.jar \
	/usr/share/java/dctm.jar \
	/usr/share/java/dfc.jar \
	/usr/share/java/dfcbase.jar \
	/usr/share/java/subscription.jar \
	/usr/share/java/workflow.jar \
	/usr/share/java/xalan2.jar \
	/usr/share/java/xercesImpl.jar \
	/usr/share/java/xforms.jar \
	/usr/share/java/xtrim-api.jar

JSP_JAR_FILES = $(JSP_CRAWLER_LIB_ENV_FILES) $(JAR_FILES)

JSP_FILES =  connectors/DCTM/editconfig.jsp \
        connectors/DCTM/editspec.jsp \
        connectors/DCTM/headerconfig.jsp \
        connectors/DCTM/headerspec.jsp \
        connectors/DCTM/postconfig.jsp \
        connectors/DCTM/postspec.jsp \
	connectors/DCTM/viewconfig.jsp \
        connectors/DCTM/viewspec.jsp \
	authorities/DCTM/editconfig.jsp \
	authorities/DCTM/headerconfig.jsp \
	authorities/DCTM/postconfig.jsp \
	authorities/DCTM/viewconfig.jsp

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
DOCUMENTUM_JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(DOCUMENTUM_JAR_FILES)))

IMPL_CLASS_NAMES = $(subst /,.,$(subst .java,$(space),$(IMPL_JAVA_FILES)))

$(IMPL_SKEL_JAVA_FILES) $(IMPL_STUB_JAVA_FILES) : $(IMPL_CLASS_FILES) metacarta-documentumconnector-ifc.jar metacarta-documentumconnector-impl.jar $(DOCUMENTUM_JAR_FILES)
	$(RMIC) -vcompat -nowarn -keep -nowrite -classpath $(DOCUMENTUM_JAR_CLASSPATH):metacarta-documentumconnector-ifc.jar:metacarta-documentumconnector-impl.jar $(IMPL_CLASS_NAMES)

$(IFC_CLASS_FILES) : $(IFC_JAVA_FILES)
	mkdir -p ifc_classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH) -d ifc_classtemp/ $(IFC_JAVA_FILES)

$(IMPL_CLASS_FILES) : $(IMPL_JAVA_FILES) metacarta-documentumconnector-ifc.jar $(DOCUMENTUM_JAR_FILES)
	mkdir -p impl_classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(DOCUMENTUM_JAR_CLASSPATH):metacarta-documentumconnector-ifc.jar -d impl_classtemp/ $(IMPL_JAVA_FILES)

$(CLASS_FILES) : $(JAVA_FILES) metacarta-documentumconnector-ifc.jar $(JAR_FILES)
	mkdir -p classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(JAR_CLASSPATH):metacarta-documentumconnector-ifc.jar -d classtemp/ $(JAVA_FILES)

$(SERVER_CLASS_FILES) : $(SERVER_JAVA_FILES) $(DOCUMENTUM_JAR_FILES) metacarta-documentumconnector-ifc.jar metacarta-documentumconnector-impl.jar
	mkdir -p server_classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(DOCUMENTUM_JAR_CLASSPATH):metacarta-documentumconnector-ifc.jar:metacarta-documentumconnector-impl.jar -d server_classtemp/ $(SERVER_JAVA_FILES)

$(REGISTRY_CLASS_FILES) : $(REGISTRY_JAVA_FILES) metacarta-documentumconnector-ifc.jar
	mkdir -p registry_classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):metacarta-documentumconnector-ifc.jar -d registry_classtemp/ $(REGISTRY_JAVA_FILES)

$(SKEL_CLASS_FILES) : $(IMPL_SKEL_JAVA_FILES) metacarta-documentumconnector-ifc.jar metacarta-documentumconnector-impl.jar $(DOCUMENTUM_JAR_FILES)
	mkdir -p skel_classtemp/
	$(JAVAC) -O -classpath $(JAVA_CLASSPATH):$(DOCUMENTUM_JAR_CLASSPATH):metacarta-documentumconnector-ifc.jar:metacarta-documentumconnector-impl.jar -d skel_classtemp/ $(IMPL_SKEL_JAVA_FILES)

$(STUB_CLASS_FILES) : $(IMPL_STUB_JAVA_FILES) metacarta-documentumconnector-ifc.jar
	mkdir -p stub_classtemp/
	$(JAVAC) -O -classpath $(JAVA_CLASSPATH):metacarta-documentumconnector-ifc.jar -d stub_classtemp/ $(IMPL_STUB_JAVA_FILES)

metacarta-documentumconnector-ifc.jar : $(IFC_CLASS_FILES)
	-rm -f $@
	-( cd ifc_classtemp ; $(JAR) cvf ../$@ com/metacarta/ )

metacarta-documentumconnector.jar : $(CLASS_FILES)
	-rm -f $@
	-( cd classtemp ; $(JAR) cvf ../$@ com/metacarta/ )

metacarta-documentumconnector-server.jar : $(SERVER_CLASS_FILES)
	-rm -f $@
	-( cd server_classtemp ; $(JAR) cvf ../$@ com/metacarta/ )

metacarta-documentumconnector-registry.jar : $(REGISTRY_CLASS_FILES)
	-rm -f $@
	-( cd registry_classtemp ; $(JAR) cvf ../$@ com/metacarta/ )

metacarta-documentumconnector-impl.jar : $(IMPL_CLASS_FILES)
	-rm -f $@
	-( cd impl_classtemp ; $(JAR) cvf ../$@ com/metacarta/ )

metacarta-documentumconnector-stub.jar : $(STUB_CLASS_FILES)
	-rm -f $@
	-( cd stub_classtemp ; $(JAR) cvf ../$@ com/metacarta/ )

metacarta-documentumconnector-skel.jar : $(SKEL_CLASS_FILES)
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

$(JSP_JAVA_FILES) : $(JSP_FILES) $(JSP_LOCAL_ENV_FILES) $(JSP_LOCAL_WEBINF_ENV_FILES) $(JSP_LOCAL_TLD_ENV_FILES) $(JSP_LOCAL_LIB_ENV_FILES) $(JSP_JAR_FILES) metacarta-documentumconnector.jar
	rm -rf jsptmp
	mkdir -p jsptmp/
	env JAVA_HOME=$(JAVA_HOME) $(JSPC) -v -l -s -d jsptmp/ -classpath $(CLASSPATH):metacarta-documentumconnector.jar:$(JSP_JAR_CLASSPATH):$(JSP_CLASSPATH) -p com.metacarta.crawler -uriroot . $(JSP_FILES)

$(JSP_CLASS_FILES) : $(JSP_JAVA_FILES) $(JSP_JAR_FILES) metacarta-documentumconnector.jar
	$(JAVAC) -O -classpath $(JAVA_CLASSPATH):metacarta-documentumconnector.jar:$(JSP_JAR_CLASSPATH):$(JSP_CLASSPATH) -d jsptmp/ $(JSP_JAVA_FILES)

documentumconnector.war : $(JSP_FILES) $(JSP_CLASS_FILES)
	-rm -f $@
	$(JAR) cvf $@ $(JSP_FILES)

clean-local: 
	-rm -f metacarta-documentumconnector.jar
	-rm -f metacarta-documentumconnector-ifc.jar
	-rm -f metacarta-documentumconnector-impl.jar
	-rm -f metacarta-documentumconnector-stub.jar
	-rm -f metacarta-documentumconnector-skel.jar
	-rm -f metacarta-documentumconnector-server.jar
	-rm -f metacarta-documentumconnector-registry.jar
	-rm -f documentumconnector.war
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
