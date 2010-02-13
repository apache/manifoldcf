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
clone_DATA = jcifsconnector-clone-files

environmentlibdir = /usr/lib/metacarta/java-environment
environmentlib_DATA = metacarta-jcifsconnector.jar

baseagentssizedir = /var/lib/metacarta/java-agents-base-sizes
baseagentssize_DATA = jcifsconnector-agents-base-size

incragentssizedir = /var/lib/metacarta/java-agents-incremental-sizes
incragentssize_DATA = jcifsconnector-agents-incr-size

basetomcatsizedir = /var/lib/metacarta/tomcat-base-sizes
basetomcatsize_DATA = jcifsconnector-tomcat-base-size

incrtomcatsizedir = /var/lib/metacarta/tomcat-incremental-sizes
incrtomcatsize_DATA = jcifsconnector-tomcat-incr-size

baseutilitiessizedir = /var/lib/metacarta/java-utilities-base-sizes
baseutilitiessize_DATA = jcifsconnector-utilities-base-size

incrutilitiessizedir = /var/lib/metacarta/java-utilities-incremental-sizes
incrutilitiessize_DATA = jcifsconnector-utilities-incr-size

agentsregisterdir=/usr/lib/metacarta/agents-register.d
agentsregister_SCRIPTS = 50_jcifsconnector

definedir = /usr/lib/metacarta/java-defines
define_DATA = jcifs.resolveOrder \
	jcifs.smb.client.dfs.strictView \
	jcifs.smb.client.soTimeout \
	jcifs.smb.client.responseTimeout \
	jcifs.smb.client.listCount

usrbindir = /usr/bin
usrbin_SCRIPTS = shareconnector_control

metacartabindir = /usr/lib/metacarta
metacartabin_DATA = jcifsconnector.war

securitydir = /etc/tomcat5.5/policy.d
security_DATA = 08jcifsconnector.policy

JAVA_FILES = com/metacarta/crawler/connectors/sharedrive/SharedDriveConnector.java \
	com/metacarta/crawler/connectors/sharedrive/SharedDriveParameters.java \
	com/metacarta/crawler/connectors/sharedrive/SharedDriveLicense.java \
	com/metacarta/crawler/connectors/sharedrive/MatchMap.java \
	com/metacarta/crawler/connectors/sharedrive/GetConnectionInfo.java

CLASS_FILES = $(addprefix classtemp/, $(subst .java,.class, $(JAVA_FILES)))

JAR_FILES = /usr/share/java/jdbcpool-0.99.jar \
	/usr/share/java/log4j-1.2.jar \
	/usr/share/java/metacarta-core.jar \
	/usr/share/java/metacarta-agents.jar \
	/usr/share/java/metacarta-pullagent.jar \
	/usr/lib/metacarta/java-common/metacarta-license.jar \
	/usr/share/java/jcifs.jar \
	/usr/share/java/jakarta-poi.jar

JSP_JAR_FILES = $(JSP_CRAWLER_LIB_ENV_FILES) \
	$(JAR_FILES)

JSP_FILES = connectors/sharedrive/editconfig.jsp \
	connectors/sharedrive/editspec.jsp \
	connectors/sharedrive/headerconfig.jsp \
	connectors/sharedrive/headerspec.jsp \
	connectors/sharedrive/postconfig.jsp \
	connectors/sharedrive/postspec.jsp \
	connectors/sharedrive/viewconfig.jsp \
	connectors/sharedrive/viewspec.jsp

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

CLASS_LIST = $(subst .java,.class, $(JAVA_FILES)) $(subst .java,\$$*.class, $(JAVA_FILES))

manifest : $(CLASS_FILES)
	( cd classtemp ; ls -1 $(CLASS_LIST) >../manifest || true )

metacarta-jcifsconnector.jar : $(CLASS_FILES) manifest
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

$(JSP_JAVA_FILES) : $(JSP_FILES) $(JSP_LOCAL_ENV_FILES) $(JSP_LOCAL_WEBINF_ENV_FILES) $(JSP_LOCAL_TLD_ENV_FILES) $(JSP_LOCAL_LIB_ENV_FILES) $(JSP_JAR_FILES) metacarta-jcifsconnector.jar
	rm -rf jsptmp
	mkdir -p jsptmp/
	env JAVA_HOME=$(JAVA_HOME)  $(JSPC) -v -l -s -d jsptmp/ -classpath $(CLASSPATH):metacarta-jcifsconnector.jar:$(JSP_JAR_CLASSPATH):$(JSP_CLASSPATH) -p com.metacarta.crawler -uriroot . $(JSP_FILES)

$(JSP_CLASS_FILES) : $(JSP_JAVA_FILES) $(JSP_JAR_FILES) metacarta-jcifsconnector.jar
	$(JAVAC) -O -classpath $(JAVA_CLASSPATH):metacarta-jcifsconnector.jar:$(JSP_JAR_CLASSPATH):$(JSP_CLASSPATH) -d jsptmp/ $(JSP_JAVA_FILES)

jcifsconnector.war : $(JSP_FILES) $(JSP_CLASS_FILES)
	-rm -f $@
	$(JAR) cvf $@ $(JSP_FILES)

clean-local: 
	-rm -f metacarta-jcifsconnector.jar
	-rm -f jcifsconnector.war
	-rm -rf classtemp/
	-rm -rf jsptmp/
	-rm -rf crawler-tmp/
	-rm -f $(JSP_LOCAL_ENV_FILES)
	-rm -rf WEB-INF/
	-rm -f manifest
