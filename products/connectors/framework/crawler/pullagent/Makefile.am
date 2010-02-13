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

javalibdir = /usr/share/java
javalib_DATA = metacarta-pullagent.jar

JAVA_FILES = \
	com/metacarta/crawler/ExportConfiguration.java \
	com/metacarta/crawler/ImportConfiguration.java \
	com/metacarta/crawler/Register.java \
	com/metacarta/crawler/UnRegister.java \
	com/metacarta/crawler/UnRegisterAll.java \
	com/metacarta/crawler/SynchronizeConnectors.java \
	com/metacarta/crawler/FindJob.java \
	com/metacarta/crawler/RunDocumentStatus.java \
	com/metacarta/crawler/RunQueueStatus.java \
	com/metacarta/crawler/RunSimpleHistory.java \
	com/metacarta/crawler/RunResultHistory.java \
	com/metacarta/crawler/RunMaxActivityHistory.java \
	com/metacarta/crawler/RunMaxBandwidthHistory.java \
	com/metacarta/crawler/ListJobs.java \
	com/metacarta/crawler/ListJobStatuses.java \
	com/metacarta/crawler/GetJobSchedule.java \
	com/metacarta/authorities/RegisterAuthority.java \
	com/metacarta/authorities/UnRegisterAuthority.java \
	com/metacarta/authorities/UnRegisterAllAuthorities.java \
	com/metacarta/authorities/SynchronizeAuthorities.java \
	com/metacarta/authorities/CheckAll.java \
	com/metacarta/crawler/connmgr/ConnectorManager.java \
	com/metacarta/authorities/interfaces/AuthorizationResponse.java \
	com/metacarta/authorities/interfaces/IAuthorityConnectorManager.java \
	com/metacarta/authorities/interfaces/IAuthorityConnector.java \
	com/metacarta/authorities/interfaces/IAuthorityConnectionManager.java \
	com/metacarta/authorities/interfaces/IAuthorityConnection.java \
	com/metacarta/authorities/interfaces/AuthorityConnectorFactory.java \
	com/metacarta/authorities/interfaces/AuthorityConnectorManagerFactory.java \
	com/metacarta/authorities/interfaces/AuthorityConnectionManagerFactory.java \
	com/metacarta/authorities/interfaces/CacheKeyFactory.java \
	com/metacarta/authorities/authorities/BaseAuthorityConnector.java \
	com/metacarta/authorities/system/Logging.java \
	com/metacarta/authorities/system/Metacarta.java \
	com/metacarta/authorities/system/IdleCleanupThread.java \
	com/metacarta/authorities/system/AuthCheckThread.java \
	com/metacarta/authorities/system/AuthRequest.java \
	com/metacarta/authorities/system/RequestQueue.java \
	com/metacarta/crawler/interfaces/JobStartRecord.java \
	com/metacarta/crawler/interfaces/CacheKeyFactory.java \
	com/metacarta/crawler/interfaces/ConnectorManagerFactory.java \
	com/metacarta/crawler/interfaces/DocumentDescription.java \
	com/metacarta/crawler/interfaces/DocumentSpecification.java \
	com/metacarta/crawler/interfaces/EnumeratedValues.java \
	com/metacarta/crawler/interfaces/IConnectorManager.java \
	com/metacarta/crawler/interfaces/IDocumentIdentifierStream.java \
	com/metacarta/crawler/interfaces/IJobDescription.java \
	com/metacarta/crawler/interfaces/IJobManager.java \
	com/metacarta/crawler/interfaces/IHistoryActivity.java \
	com/metacarta/crawler/interfaces/INamingActivity.java \
	com/metacarta/crawler/interfaces/IEventActivity.java \
	com/metacarta/crawler/interfaces/IAbortActivity.java \
	com/metacarta/crawler/interfaces/IProcessActivity.java \
	com/metacarta/crawler/interfaces/IVersionActivity.java \
	com/metacarta/crawler/interfaces/ISeedingActivity.java \
	com/metacarta/crawler/interfaces/IRepositoryConnection.java \
	com/metacarta/crawler/interfaces/IRepositoryConnectionManager.java \
	com/metacarta/crawler/interfaces/IRepositoryConnector.java \
	com/metacarta/crawler/interfaces/JobManagerFactory.java \
	com/metacarta/crawler/interfaces/JobStatus.java \
	com/metacarta/crawler/interfaces/RepositoryConnectionManagerFactory.java \
	com/metacarta/crawler/interfaces/RepositoryConnectorFactory.java \
	com/metacarta/crawler/interfaces/ScheduleRecord.java \
	com/metacarta/crawler/interfaces/FilterCriteria.java \
	com/metacarta/crawler/interfaces/StatusFilterCriteria.java \
	com/metacarta/crawler/interfaces/SortOrder.java \
	com/metacarta/crawler/interfaces/BucketDescription.java \
	com/metacarta/crawler/interfaces/RegExpCriteria.java \
	com/metacarta/crawler/jobs/JobDescription.java \
	com/metacarta/crawler/jobs/JobManager.java \
	com/metacarta/crawler/jobs/JobQueue.java \
	com/metacarta/crawler/jobs/Jobs.java \
	com/metacarta/crawler/jobs/ScheduleManager.java \
	com/metacarta/crawler/jobs/ScheduleList.java \
	com/metacarta/crawler/jobs/HopCount.java \
	com/metacarta/crawler/jobs/IntrinsicLink.java \
	com/metacarta/crawler/jobs/HopDeleteDeps.java \
	com/metacarta/crawler/jobs/DeleteDependency.java \
	com/metacarta/crawler/jobs/Carrydown.java \
	com/metacarta/crawler/jobs/HopFilterManager.java \
	com/metacarta/crawler/jobs/EventManager.java \
	com/metacarta/crawler/jobs/PrereqEventManager.java \
	com/metacarta/authorities/authconnmgr/AuthorityConnectorManager.java \
	com/metacarta/authorities/authority/AuthorityConnectionManager.java \
	com/metacarta/authorities/authority/AuthorityConnection.java \
	com/metacarta/crawler/repository/RepositoryConnection.java \
	com/metacarta/crawler/repository/RepositoryConnectionManager.java \
	com/metacarta/crawler/repository/RepositoryHistoryManager.java \
	com/metacarta/crawler/repository/ThrottleSpecManager.java \
	com/metacarta/crawler/system/CrawlerAgent.java \
	com/metacarta/crawler/system/DocumentQueue.java \
	com/metacarta/crawler/system/QueuedDocument.java \
	com/metacarta/crawler/system/QueuedDocumentSet.java \
	com/metacarta/crawler/system/ResetManager.java \
	com/metacarta/crawler/system/FinisherThread.java \
	com/metacarta/crawler/system/JobStartThread.java \
	com/metacarta/crawler/system/StartupThread.java \
	com/metacarta/crawler/system/JobDeleteThread.java \
	com/metacarta/crawler/system/Logging.java \
	com/metacarta/crawler/system/Metacarta.java \
	com/metacarta/crawler/system/StufferThread.java \
	com/metacarta/crawler/system/ExpireStufferThread.java \
	com/metacarta/crawler/system/SeedingActivity.java \
	com/metacarta/crawler/system/SeedingThread.java \
	com/metacarta/crawler/system/WorkerThread.java \
	com/metacarta/crawler/system/ExpireThread.java \
	com/metacarta/crawler/system/DocumentDeleteQueue.java \
	com/metacarta/crawler/system/DocumentDeleteSet.java \
	com/metacarta/crawler/system/DeleteQueuedDocument.java \
	com/metacarta/crawler/system/DocumentDeleteStufferThread.java \
	com/metacarta/crawler/system/DocumentDeleteThread.java \
	com/metacarta/crawler/system/JobResetThread.java \
	com/metacarta/crawler/system/IdleCleanupThread.java \
	com/metacarta/crawler/system/WorkerResetManager.java \
	com/metacarta/crawler/system/DocDeleteResetManager.java \
	com/metacarta/crawler/interfaces/QueueTracker.java \
	com/metacarta/crawler/system/SetPriorityThread.java \
	com/metacarta/crawler/interfaces/BlockingDocuments.java \
	com/metacarta/crawler/connectors/BaseRepositoryConnector.java \
	com/metacarta/crawler/interfaces/PerformanceStatistics.java \
	com/metacarta/crawler/interfaces/DepthStatistics.java

CLASS_FILES = $(addprefix classtemp/, $(subst .java,.class, $(JAVA_FILES)))

JAR_FILES = ../../java-common/core/lib/jdbcpool-0.99.jar \
	/usr/share/java/log4j-1.2.jar \
	../../java-common/core/metacarta-core.jar \
	../../java-agents/metacarta-agents.jar \
	/usr/lib/metacarta/java-common/metacarta-license.jar

space:= $(empty) $(empty)

JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(JAR_FILES)))

$(CLASS_FILES) : $(JAVA_FILES) $(JAR_FILES)
	-rm -rf classtemp/
	mkdir -p classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(JAR_CLASSPATH) -d classtemp/ $(JAVA_FILES)

CLASS_LIST = $(subst .java,.class, $(JAVA_FILES)) $(subst .java,\$$*.class, $(JAVA_FILES))

manifest : $(CLASS_FILES)
	-( cd classtemp ; ls -1 $(CLASS_LIST) >../manifest || true )

metacarta-pullagent.jar : $(CLASS_FILES) manifest
	-rm -f $@
	-( cd classtemp ; $(JAR) cvf ../$@ $$(<../manifest) )

clean-local: 
	-rm -f metacarta-pullagent.jar
	-rm -rf classtemp/
	-rm manifest

