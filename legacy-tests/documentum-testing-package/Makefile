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
	com/metacarta/crawler/connectors/DCTM/RemoveDoc.java \
	com/metacarta/crawler/connectors/DCTM/FindDoc.java \
	com/metacarta/crawler/connectors/DCTM/VersionDoc.java \
	com/metacarta/crawler/connectors/DCTM/AddDoc.java \
	com/metacarta/crawler/connectors/DCTM/DCTMAddRemove.java

TEST_CLASS_FILES = $(addprefix classtemp/, $(subst .java,.class, $(TEST_JAVA_FILES)))

TEST_CLASS_LIST = $(subst .java,.class, $(TEST_JAVA_FILES)) $(subst .java,\$$*.class, $(TEST_JAVA_FILES))

JAR_FILES = \
	/usr/share/java/jdbcpool-0.99.jar \
	/usr/share/java/log4j-1.2.jar \
	/usr/share/java/metacarta-core.jar \
	/usr/share/java/metacarta-agents.jar \
	/usr/share/java/metacarta-pullagent.jar \
	TTTools.jar \
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

space:= $(empty) $(empty)

JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(JAR_FILES)))


$(TEST_CLASS_FILES) : $(TEST_JAVA_FILES) $(JAR_FILES)
	mkdir -p classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(JAR_CLASSPATH) -d classtemp/ $(TEST_JAVA_FILES)

manifest_test : $(TEST_CLASS_FILES)
	-( cd classtemp ; ls -1 $(TEST_CLASS_LIST) >../manifest_test || true )

metacarta-documentumconnector-test.jar : $(TEST_CLASS_FILES) manifest_test
	-rm -f $@
	-( cd classtemp ; $(JAR) cvf ../$@ $$(<../manifest_test) )

all : metacarta-documentumconnector-test.jar

clean-local: 
	-rm -f metacarta-documentumconnector-test.jar
	-rm -rf classtemp/

