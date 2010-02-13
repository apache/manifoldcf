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

JSPC=/usr/share/tomcat5.5/bin/jspc.sh
JAVAC=/usr/lib/jvm/java-1.5.0-sun/bin/javac -source 1.4
JAR=/usr/lib/jvm/java-1.5.0-sun/bin/jar
JAVA_CLASSPATH=/usr/share/tomcat5.5/common/lib/servlet-api.jar

TEST_JAVA_FILES = com/metacarta/crawler/WaitJobPaused.java \
	com/metacarta/crawler/AbortJob.java \
	com/metacarta/crawler/PauseJob.java \
	com/metacarta/crawler/DefineRepositoryConnection.java \
	com/metacarta/crawler/DefineOutputConnection.java \
	com/metacarta/crawler/ResetAll.java \
	com/metacarta/crawler/RestartJob.java \
	com/metacarta/crawler/ChangeJobDocSpec.java \
	com/metacarta/crawler/DeleteRepositoryConnection.java \
	com/metacarta/crawler/DeleteOutputConnection.java \
	com/metacarta/crawler/CheckConfigured.java \
	com/metacarta/crawler/DefineJob.java \
	com/metacarta/crawler/AddScheduledTime.java \
	com/metacarta/crawler/StartJob.java \
	com/metacarta/crawler/WaitForJobInactive.java \
	com/metacarta/crawler/DeleteJob.java \
	com/metacarta/crawler/WaitForJobDeleted.java \
        com/metacarta/crawler/JobStartSpinner.java \
	com/metacarta/authorities/ChangeAuthSpec.java \
	com/metacarta/authorities/CheckConfigured.java \
	com/metacarta/authorities/DefineAuthorityConnection.java \
	com/metacarta/authorities/DeleteAuthorityConnection.java \
	com/metacarta/authorities/ResetAll.java


TEST_CLASS_FILES = $(addprefix classtemp/, $(subst .java,.class, $(TEST_JAVA_FILES)))

TEST_CLASS_LIST = $(subst .java,.class, $(TEST_JAVA_FILES)) $(subst .java,\$$*.class, $(TEST_JAVA_FILES))

JAR_FILES = /usr/share/java/jdbcpool-0.99.jar \
	/usr/share/java/log4j-1.2.jar \
	/usr/share/java/metacarta-core.jar \
	/usr/share/java/metacarta-agents.jar \
	/usr/share/java/metacarta-pullagent.jar

space:= $(empty) $(empty)

JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(JAR_FILES)))


$(TEST_CLASS_FILES) : $(TEST_JAVA_FILES) $(JAR_FILES)
	mkdir -p classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(JAR_CLASSPATH) -d classtemp/ $(TEST_JAVA_FILES)

manifest_test : $(TEST_CLASS_FILES)
	-( cd classtemp ; ls -1 $(TEST_CLASS_LIST) >../manifest_test || true )

metacarta-pullagent-test.jar : $(TEST_CLASS_FILES) manifest_test
	-rm -f $@
	-( cd classtemp ; $(JAR) cvf ../$@ $$(<../manifest_test) )

all : metacarta-pullagent-test.jar

clean-local: 
	-rm -f metacarta-pullagent-test.jar
	-rm -rf classtemp/

