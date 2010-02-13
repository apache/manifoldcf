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

htmldir = /www
html_DATA = 

tomcatpolicydir = /etc/tomcat5.5/policy.d
tomcatpolicy_DATA = 08authorityservice.policy

webappdir = /usr/share/metacarta
webapp_DATA = authorityservice.war

webappregdir = /usr/lib/metacarta/java-webapps
webappreg_DATA = authorityservice_webapp

JSP_FILES = 

JAVA_FILES = com/metacarta/authorityservice/servlet/UserACLServlet.java

JAVA_SOURCE_FILES = $(addprefix WEB-INF/classes/,$(JAVA_FILES))

CLASS_FILES = $(addprefix classtemp/, $(subst .java,.class, $(JAVA_FILES)))

HTML_FILES = 

CONF_FILES = WEB-INF/web.xml

TLD_FILES =

JSP_JAVA_FILES = $(addprefix jsptmp/, $(subst .jsp,.java, $(JSP_FILES)))
JSP_CLASS_FILES = $(addprefix jsptmp/com/metacarta/authorityservice/, $(subst .jsp,.class, $(JSP_FILES)))

LIB_LICENSES= 

WEBAPP_JAR_FILES = 

ENV_JAR_FILES = ../../java-common/core/metacarta-core.jar \
	../../java-agents/metacarta-agents.jar \
	../pullagent/metacarta-pullagent.jar \
	../../java-common/core/lib/jdbcpool-0.99.jar \
	/usr/share/java/log4j-1.2.jar

space:= $(empty) $(empty)

JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(ENV_JAR_FILES))):$(subst jar$(space),jar:,$(strip $(WEBAPP_JAR_FILES)))

$(CLASS_FILES) : $(JAVA_SOURCE_FILES) $(JAR_FILES) $(ENV_JAR_FILES) $(WEBAPP_JAR_FILES)
	-rm -rf classtemp/
	mkdir -p classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(JAR_CLASSPATH) -sourcepath ./WEB-INF/classes/ -d classtemp/ $(JAVA_SOURCE_FILES)

WEB-INF/lib/metacarta-authorityservice.jar : $(CLASS_FILES)
	-rm -f $@
	$(JAR) cvf $@ -C classtemp com/metacarta/authorityservice/

$(JSP_JAVA_FILES) : $(JSP_FILES) $(WEBAPP_JAR_FILES)
	-rm -rf jsptmp/
	mkdir jsptmp/
	env JAVA_HOME=$(JAVA_HOME) env CLASSPATH="$(CLASSPATH):$(WEBAPP_JAR_FILES)" $(JSPC) -v -l -s -d jsptmp/ -p com.metacarta.authorityservice $(JSP_FILES)

$(JSP_CLASS_FILES) : $(JSP_JAVA_FILES)
	$(JAVAC) -O -classpath $(JAVA_CLASSPATH):$(JAR_CLASSPATH):/usr/share/tomcat5.5/lib/jasper-runtime.jar -d jsptmp/ $^

authorityservice.war : $(JSP_FILES) $(CONF_FILES) $(TLD_FILES) $(HTML_FILES) $(JSP_CLASS_FILES) $(WEBAPP_JAR_FILES) WEB-INF/lib/metacarta-authorityservice.jar
	-rm -f $@
	$(JAR) cvf $@ $(JSP_FILES) $(CONF_FILES) $(HTML_FILES) $(WEBAPP_JAR_FILES) $(LIB_LICENSES) WEB-INF/lib/metacarta-authorityservice.jar

clean-local: 
	-rm -f authorityservice.war
	-rm -f WEB-INF/lib/metacarta-authorityservice.jar
	-rm -rf jsptmp/
	-rm -rf classtemp/
