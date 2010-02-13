# $Id$

SUBDIRS = 

noinst_DATA = metacarta-core.jar

JAVA_FILES = com/metacarta/core/DBCreate.java \
	com/metacarta/core/DBDrop.java \
	com/metacarta/core/LockClean.java \
	com/metacarta/core/Obfuscate.java \
	com/metacarta/core/interfaces/UTF8Stdout.java \
	com/metacarta/core/interfaces/UTF8Stderr.java \
	com/metacarta/core/interfaces/BinaryInput.java \
	com/metacarta/core/interfaces/CharacterInput.java \
	com/metacarta/core/interfaces/ConfigParams.java \
	com/metacarta/core/interfaces/ConfigNode.java \
	com/metacarta/core/interfaces/Specification.java \
	com/metacarta/core/interfaces/SpecificationNode.java \
	com/metacarta/core/interfaces/CacheKeyFactory.java \
	com/metacarta/core/interfaces/CacheManagerFactory.java \
	com/metacarta/core/interfaces/ColumnDescription.java \
	com/metacarta/core/interfaces/IndexDescription.java \
	com/metacarta/core/interfaces/ResultSpecification.java \
	com/metacarta/core/interfaces/DatabaseFactory.java \
	com/metacarta/core/interfaces/DBInterfaceFactory.java \
	com/metacarta/core/interfaces/ICacheClass.java \
	com/metacarta/core/interfaces/ICacheCreateHandle.java \
	com/metacarta/core/interfaces/ICacheDescription.java \
	com/metacarta/core/interfaces/ICacheExecutor.java \
	com/metacarta/core/interfaces/ICacheHandle.java \
	com/metacarta/core/interfaces/ICacheManager.java \
	com/metacarta/core/interfaces/IDatabase.java \
	com/metacarta/core/interfaces/IDBInterface.java \
	com/metacarta/core/interfaces/IDFactory.java \
	com/metacarta/core/interfaces/ILockManager.java \
	com/metacarta/core/interfaces/IResultRow.java \
	com/metacarta/core/interfaces/IResultSet.java \
	com/metacarta/core/interfaces/IThreadContext.java \
	com/metacarta/core/interfaces/LockException.java \
	com/metacarta/core/interfaces/LockManagerFactory.java \
	com/metacarta/core/interfaces/MetacartaException.java \
	com/metacarta/core/interfaces/StringSet.java \
	com/metacarta/core/interfaces/StringSetBuffer.java \
	com/metacarta/core/interfaces/TempFileInput.java \
	com/metacarta/core/interfaces/TempFileCharacterInput.java \
	com/metacarta/core/interfaces/ThreadContextFactory.java \
	com/metacarta/core/interfaces/TimeMarker.java \
	com/metacarta/core/interfaces/ILimitChecker.java \
	com/metacarta/core/interfaces/IKeystoreManager.java \
	com/metacarta/core/interfaces/KeystoreManagerFactory.java \
	com/metacarta/core/keystore/KeystoreManager.java \
	com/metacarta/core/cachemanager/BaseDescription.java \
	com/metacarta/core/cachemanager/CacheManager.java \
	com/metacarta/core/cachemanager/ExecutorBase.java \
	com/metacarta/core/cachemanager/GeneralCache.java \
	com/metacarta/core/common/XMLDoc.java \
	com/metacarta/core/common/Base64.java \
	com/metacarta/core/database/BaseObject.java \
	com/metacarta/core/database/BaseTable.java \
	com/metacarta/core/database/ConnectionFactory.java \
	com/metacarta/core/database/Database.java \
	com/metacarta/core/database/DBInterfaceMySQL.java \
	com/metacarta/core/database/DBInterfacePostgreSQL.java \
	com/metacarta/core/database/MergedResultSet.java \
	com/metacarta/core/database/QueryDescription.java \
	com/metacarta/core/database/RRow.java \
	com/metacarta/core/database/RSet.java \
	com/metacarta/core/database/TransactionHandle.java \
	com/metacarta/core/lockmanager/LocalLockException.java \
	com/metacarta/core/lockmanager/ExpiredObjectException.java \
	com/metacarta/core/lockmanager/LockManager.java \
	com/metacarta/core/lockmanager/LockObject.java \
	com/metacarta/core/lockmanager/LockPool.java \
	com/metacarta/core/system/Logging.java \
	com/metacarta/core/system/Metacarta.java \
	com/metacarta/core/threadcontext/ThreadContext.java \
	com/metacarta/core/MetaCartaVersion.java

CLASS_FILES = $(addprefix classtemp/, $(subst .java,.class, $(JAVA_FILES)))

JAR_FILES = lib/jdbcpool-0.99.jar \
	/usr/share/java/log4j-1.2.jar

space:= $(empty) $(empty)

JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(JAR_FILES)))

$(CLASS_FILES) : $(JAVA_FILES) $(JAR_FILES)
	-rm -rf classtemp/
	mkdir -p classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(JAR_CLASSPATH) -d classtemp/ $(JAVA_FILES)


metacarta-core.jar : $(CLASS_FILES)
	echo $(JAVA_FILES)
	echo $(CLASS_FILES)
	-rm -f $@
	$(JAR) cvf $@ -C classtemp com/metacarta/core/

com/metacarta/core/MetaCartaVersion.java : MetaCartaVersion.java.in Makefile
	@PATH_TO_PROGRAM_PERL@ -p -e 'use POSIX; s{__VERSION__}{$(VERSION)}; s{__VERSION_MAJOR__}{$(VERSION_MAJOR)}; s{__VERSION_MINOR__}{$(VERSION_MINOR)}; s{__VERSION_PATCH__}{$(VERSION_PATCH)}; $$now = strftime("%b %d %Y %T", localtime(time())); s{__BUILD_DATE__}{$$now};' $< >$@

clean-local: 
	-rm -f com/metacarta/core/MetaCartaVersion.java
	-rm -f metacarta-core.jar
	-rm -rf classtemp/
