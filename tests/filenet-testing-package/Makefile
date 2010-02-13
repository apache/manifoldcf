# $Id$

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

JAVAC=/usr/lib/jvm/java-1.5.0-sun/bin/javac -source 1.4
JAR=/usr/lib/jvm/java-1.5.0-sun/bin/jar
JAVA_CLASSPATH=/usr/share/tomcat5.5/common/lib/servlet-api.jar

TEST_JAVA_FILES = \
	com/metacarta/crawler/connectors/filenet/FilenetAddRemove.java \
	com/metacarta/crawler/connectors/filenet/AddDoc.java \
	com/metacarta/crawler/connectors/filenet/RemoveDoc.java \
	com/metacarta/crawler/connectors/filenet/VersionDoc.java \
	com/metacarta/crawler/connectors/filenet/LookupDoc.java \
	com/metacarta/crawler/connectors/filenet/SetDocPermission.java

TEST_CLASS_FILES = $(addprefix classtemp/, $(subst .java,.class, $(TEST_JAVA_FILES)))

TEST_CLASS_LIST = $(subst .java,.class, $(TEST_JAVA_FILES)) $(subst .java,\$$*.class, $(TEST_JAVA_FILES))

JAR_FILES = \
	/usr/share/java/jdbcpool-0.99.jar \
	/usr/share/java/log4j-1.2.jar \
	/usr/share/java/metacarta-core.jar \
	/usr/share/java/metacarta-agents.jar \
	/usr/share/java/metacarta-pullagent.jar \
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


space:= $(empty) $(empty)

JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(JAR_FILES)))


$(TEST_CLASS_FILES) : $(TEST_JAVA_FILES) $(JAR_FILES)
	mkdir -p classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(JAR_CLASSPATH) -d classtemp/ $(TEST_JAVA_FILES)

manifest_test : $(TEST_CLASS_FILES)
	-( cd classtemp ; ls -1 $(TEST_CLASS_LIST) >../manifest_test || true )

metacarta-filenetconnector-test.jar : $(TEST_CLASS_FILES) manifest_test
	-rm -f $@
	-( cd classtemp ; $(JAR) cvf ../$@ $$(<../manifest_test) )

all : metacarta-filenetconnector-test.jar

clean-local: 
	-rm -f metacarta-filenetconnector-test.jar
	-rm -rf classtemp/

