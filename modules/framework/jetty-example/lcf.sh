#!/bin/bash -e

if [[ $OSTYPE == "cygwin" ]] ; then
    PATHSEP=";"
else
    PATHSEP=":"
fi

#Make sure environment variables are properly set
if [ -e "$JAVA_HOME"/bin/java ] ; then
    if [ -f ./properties.xml ] ; then
    
        # Build the classpath
        CLASSPATH=""
        for filename in $(ls -1 ./lib) ; do
            if [ -n "$CLASSPATH" ] ; then
                CLASSPATH="$CLASSPATH""$PATHSEP"./lib/"$filename"
            else
                CLASSPATH=./lib/"$filename"
            fi
        done
        
        # Build the defines
        DEFINES="-Dorg.apache.lcf.configfile=./properties.xml"

        "$JAVA_HOME/bin/java" $DEFINES -cp "$CLASSPATH" org.apache.lcf.jettyrunner.LCFJettyRunner 8888 war/lcf-crawler-ui.war war/lcf-authority-service.war
        exit $?
        
    else
        echo "Cannot locate properties.xml file." 1>&2
        exit 1
    fi
    
else
    echo "Environment variable JAVA_HOME is not properly set." 1>&2
    exit 1
fi
