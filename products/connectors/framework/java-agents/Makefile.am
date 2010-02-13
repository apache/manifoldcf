# $Id$

SUBDIRS = commands

usrlibdir = /usr/lib/metacarta
usrlib_SCRIPTS = agents

definesdir = /usr/lib/metacarta/java-defines
defines_DATA = entityExpansionLimit

postgresmaintdir = /usr/lib/metacarta/postgres-maintenance.d
postgresmaint_SCRIPTS = 20_vacuumfull 99_restart

baseagentssizedir = /var/lib/metacarta/java-agents-base-sizes
baseagentssize_DATA = agents-agents-base-size

incragentssizedir = /var/lib/metacarta/java-agents-incremental-sizes
incragentssize_DATA = agents-agents-incr-size

basetomcatsizedir = /var/lib/metacarta/tomcat-base-sizes
basetomcatsize_DATA = agents-tomcat-base-size

incrtomcatsizedir = /var/lib/metacarta/tomcat-incremental-sizes
incrtomcatsize_DATA = agents-tomcat-incr-size

baseutilitiessizedir = /var/lib/metacarta/java-utilities-base-sizes
baseutilitiessize_DATA = agents-utilities-base-size

incrutilitiessizedir = /var/lib/metacarta/java-utilities-incremental-sizes
incrutilitiessize_DATA = agents-utilities-incr-size

healthcheckersdir = /usr/lib/metacarta/health_checkers.d
healthcheckers_SCRIPTS = check_agents_running.py

postgressetupdir=/usr/lib/metacarta/postgres-setup.d
postgressetup_SCRIPTS = 30_create-agents-cluster

javalibdir = /usr/share/java
javalib_DATA = metacarta-agents.jar $(JAR_FILES)

metacartabindir = /usr/lib/metacarta
metacartabin_SCRIPTS = $(JAVACORE_SCRIPT_FILES) check_configuration_allowed agents-log-clean

etcdir = /etc/init.d
etc_SCRIPTS = metacarta-agents metacarta-upgrade-agents-database metacarta-register-agents

sysconf_DATA = agents.conf agent-logging.conf

JAVA_FILES = com/metacarta/agents/AgentRun.java \
	com/metacarta/agents/AgentStop.java \
	com/metacarta/agents/Install.java \
	com/metacarta/agents/Register.java \
	com/metacarta/agents/Uninstall.java \
	com/metacarta/agents/UnRegister.java \
	com/metacarta/agents/UnRegisterAll.java \
	com/metacarta/agents/SynchronizeAll.java \
	com/metacarta/agents/RegisterOutput.java \
	com/metacarta/agents/UnRegisterOutput.java \
	com/metacarta/agents/UnRegisterAllOutputs.java \
	com/metacarta/agents/SynchronizeOutputs.java \
	com/metacarta/agents/SetupAgentConnection.java \
	com/metacarta/agents/interfaces/CacheKeyFactory.java \
	com/metacarta/agents/interfaces/ServiceInterruption.java \
	com/metacarta/agents/interfaces/AgentFactory.java \
	com/metacarta/agents/interfaces/AgentManagerFactory.java \
	com/metacarta/agents/interfaces/IAgent.java \
	com/metacarta/agents/interfaces/IAgentManager.java \
	com/metacarta/agents/interfaces/IIngestLogger.java \
	com/metacarta/agents/interfaces/IIncrementalIngester.java \
	com/metacarta/agents/interfaces/IncrementalIngesterFactory.java \
	com/metacarta/agents/interfaces/RepositoryDocument.java \
	com/metacarta/agents/interfaces/DocumentIngestStatus.java \
	com/metacarta/agents/interfaces/IOutputConnector.java \
	com/metacarta/agents/interfaces/OutputSpecification.java \
	com/metacarta/agents/interfaces/IOutputHistoryActivity.java \
	com/metacarta/agents/interfaces/IOutputAddActivity.java \
	com/metacarta/agents/interfaces/IOutputRemoveActivity.java \
	com/metacarta/agents/interfaces/IOutputActivity.java \
	com/metacarta/agents/interfaces/IOutputConnection.java \
	com/metacarta/agents/interfaces/IOutputConnectionManager.java \
	com/metacarta/agents/interfaces/OutputConnectorFactory.java \
	com/metacarta/agents/interfaces/OutputConnectionManagerFactory.java \
	com/metacarta/agents/interfaces/IOutputConnectorManager.java \
	com/metacarta/agents/interfaces/OutputConnectorManagerFactory.java \
	com/metacarta/agents/output/BaseOutputConnector.java \
	com/metacarta/agents/outputconnmgr/OutputConnectorManager.java \
	com/metacarta/agents/outputconnection/OutputConnection.java \
	com/metacarta/agents/outputconnection/OutputConnectionManager.java \
	com/metacarta/agents/agentmanager/AgentManager.java \
	com/metacarta/agents/incrementalingest/IncrementalIngester.java \
	com/metacarta/agents/system/Metacarta.java \
	com/metacarta/agents/system/Logging.java \
	com/metacarta/agents/common/XMLContext.java \
	com/metacarta/agents/common/XMLFileContext.java \
	com/metacarta/agents/common/XMLOutputStreamContext.java \
	com/metacarta/agents/common/XMLStream.java \
	com/metacarta/agents/common/XMLStringContext.java \
	com/metacarta/agents/common/XMLWriterContext.java


JAVACORE_SCRIPT_FILES = core-dbcreate \
	core-dbdrop \
	core-lockclean

JAVACORE_JAR_FILES = jdbcpool-0.99.jar

JAVACORE_SCRIPT_SOURCE_FILES = $(addprefix ../java-common/core/commands/,$(JAVACORE_SCRIPT_FILES))
JAVACORE_JAR_SOURCE_FILES = $(addprefix ../java-common/core/lib/,$(JAVACORE_JAR_FILES))


$(JAVACORE_SCRIPT_FILES) : $(JAVACORE_SCRIPT_SOURCE_FILES)
	cp ../java-common/core/commands/$@ $@

$(JAVACORE_JAR_FILES) : $(JAVACORE_JAR_SOURCE_FILES)
	cp ../java-common/core/lib/$@ $@

metacarta-core.jar : ../java-common/core/metacarta-core.jar
	cp $< $@

CLASS_FILES = $(addprefix classtemp/, $(subst .java,.class, $(JAVA_FILES)))

JAR_FILES = $(JAVACORE_JAR_FILES) metacarta-core.jar

CLASSPATH_JAR_FILES = $(JAR_FILES) /usr/share/java/log4j-1.2.jar

space:= $(empty) $(empty)

JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(CLASSPATH_JAR_FILES)))

$(CLASS_FILES) : $(JAVA_FILES) $(JAR_FILES)
	-rm -rf classtemp/
	mkdir -p classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(JAR_CLASSPATH) -d classtemp/ $(JAVA_FILES)


metacarta-agents.jar : $(CLASS_FILES)
	-rm -f $@
	$(JAR) cvf $@ -C classtemp com/metacarta/agents/


clean-local: 
	-rm -f metacarta-agents.jar
	-rm -f $(JAVACORE_SCRIPT_FILES)
	-rm -f $(JAR_FILES)
	-rm -rf classtemp/
