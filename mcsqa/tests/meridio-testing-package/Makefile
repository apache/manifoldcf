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
	com/metacarta/crawler/connectors/meridio/meridiowrapper/MeridioTestWrapper.java \
	com/metacarta/crawler/connectors/meridio/AddDoc.java \
	com/metacarta/crawler/connectors/meridio/AddDocMultiple.java \
	com/metacarta/crawler/connectors/meridio/AddRec.java \
	com/metacarta/crawler/connectors/meridio/AddRecMultiple.java \
	com/metacarta/crawler/connectors/meridio/UpdateDoc.java \
	com/metacarta/crawler/connectors/meridio/DeleteDoc.java \
	com/metacarta/crawler/connectors/meridio/DeleteRec.java \
	com/metacarta/crawler/connectors/meridio/AddDocAcl.java \
	com/metacarta/crawler/connectors/meridio/AddRecAcl.java \
	com/metacarta/crawler/connectors/meridio/DeleteDocAcl.java \
	com/metacarta/crawler/connectors/meridio/DeleteRecAcl.java \
	com/metacarta/crawler/connectors/meridio/LookupDoc.java \
	com/metacarta/crawler/connectors/meridio/LookupRec.java

TEST_CLASS_FILES = $(addprefix classtemp/, $(subst .java,.class, $(TEST_JAVA_FILES)))

TEST_CLASS_LIST = $(subst .java,.class, $(TEST_JAVA_FILES)) $(subst .java,\$$*.class, $(TEST_JAVA_FILES))

JAR_FILES = /usr/share/java/axis.jar \
	/usr/share/java/commons-discovery-0.4.jar \
	/usr/share/java/jaxrpc.jar \
	/usr/share/java/saaj.jar \
	/usr/share/java/wsdl4j-1.6.2.jar \
	/usr/share/java/jdbcpool-0.99.jar \
	/usr/share/java/log4j-1.2.jar \
	/usr/share/java/metacarta-core.jar \
	/usr/share/java/metacarta-agents.jar \
	/usr/share/java/metacarta-pullagent.jar \
	/usr/share/java/commons-codec.jar \
	/usr/share/java/commons-collections.jar \
	/usr/share/java/commons-httpclient.jar \
	/usr/share/java/commons-logging.jar \
	/usr/share/java/castor.jar \
	/usr/share/java/xercesImpl.jar \
	/usr/share/java/xml-apis.jar \
	/usr/share/java/activation.jar \
	/usr/lib/metacarta/java-environment/mailapi.jar \
	/usr/lib/metacarta/java-environment/metacarta-meridioconnector.jar \
	/usr/lib/metacarta/java-environment/metacarta-meridiostubs.jar \
	/usr/lib/metacarta/java-environment/metacarta-meridioxsd.jar

space:= $(empty) $(empty)

JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(JAR_FILES)))


$(TEST_CLASS_FILES) : $(TEST_JAVA_FILES) $(JAR_FILES)
	mkdir -p classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(JAR_CLASSPATH) -d classtemp/ $(TEST_JAVA_FILES)

manifest_test : $(TEST_CLASS_FILES)
	-( cd classtemp ; ls -1 $(TEST_CLASS_LIST) >../manifest_test || true )

metacarta-meridioconnector-test.jar : $(TEST_CLASS_FILES) manifest_test
	-rm -f $@
	-( cd classtemp ; $(JAR) cvf ../$@ $$(<../manifest_test) )

all : metacarta-meridioconnector-test.jar

clean-local: 
	-rm -f metacarta-meridioconnector-test.jar
	-rm -rf classtemp/

