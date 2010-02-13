# $Id$

SUBDIRS = 

noinst_DATA = metacarta-uicore.jar

JAVA_FILES = com/metacarta/ui/beans/AdminProfile.java \
	com/metacarta/ui/beans/ThreadContext.java \
	com/metacarta/ui/multipart/MultipartWrapper.java \
	com/metacarta/ui/util/Encoder.java \
	com/metacarta/ui/util/Formatter.java \
	com/metacarta/ui/util/MultilineParser.java

CLASS_FILES = $(addprefix classtemp/, $(subst .java,.class, $(JAVA_FILES)))

JAR_FILES = /usr/share/java/commons-fileupload.jar \
	../core/lib/jdbcpool-0.99.jar \
	/usr/share/java/log4j-1.2.jar \
	../core/metacarta-core.jar

space:= $(empty) $(empty)

JAR_CLASSPATH = $(subst jar$(space),jar:,$(strip $(JAR_FILES)))

$(CLASS_FILES) : $(JAVA_FILES) $(JAR_FILES)
	-rm -rf classtemp/
	mkdir -p classtemp/
	$(JAVAC) -O -deprecation -classpath $(JAVA_CLASSPATH):$(JAR_CLASSPATH) -d classtemp/ $(JAVA_FILES)


metacarta-uicore.jar : $(CLASS_FILES)
	-rm -f $@
	$(JAR) cvf $@ -C classtemp com/metacarta/ui/


clean-local: 
	-rm -f metacarta-uicore.jar
	-rm -rf classtemp/
