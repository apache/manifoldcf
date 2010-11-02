#!/bin/bash -e

if [[ $OSTYPE == "cygwin" ]] ; then
    PATHSEP=";"
else
    PATHSEP=":"
fi

#Make sure environment variables are properly set
if [ -e "$JAVA_HOME"/bin/java ] ; then
    if [ -f "$MCF_HOME"/properties.xml ] ; then
    
        # Build the classpath
        CLASSPATH=""
        for filename in $(ls -1 "$MCF_HOME"/filenet-server-process/jar) ; do
            if [ -n "$CLASSPATH" ] ; then
                CLASSPATH="$CLASSPATH""$PATHSEP""$MCF_HOME"/filenet-server-process/jar/"$filename"
            else
                CLASSPATH="$MCF_HOME"/filenet-server-process/jar/"$filename"
            fi
        done
        
        WASP_STATEMENT=""
        if [[ $WASP_HOME != "" ]] ; then
            WASP_STATEMENT=-Dwasp.location="WASP_HOME"
        LIB_STATEMENT=""
        if [[ $JAVA_LIB_PATH != "" ]] ; then
            LIB_STATEMENT=-Djava.library.path="$JAVA_LIB_PATH"
        "$JAVA_HOME/bin/java" -Xmx=512m -Xms=32m $WASP_STATEMENT $LIB_STATEMENT -cp "$CLASSPATH" org.apache.manifoldcf.crawler.server.filenet.Filenet
        exit $?
        
    else
        echo "Environment variable MCF_HOME is not properly set." 1>&2
        exit 1
    fi
    
else
    echo "Environment variable JAVA_HOME is not properly set." 1>&2
    exit 1
fi
