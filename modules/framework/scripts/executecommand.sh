#!/bin/bash -e

#Make sure environment variables are properly set
if [ -e "$JAVA_HOME"/bin/java ] ; then
    if [ -f "$LCF_HOME"/properties.ini ] ; then
    
        # Build the classpath
        CLASSPATH=""
        for filename in $(ls -1 "$LCF_HOME"/processes/jar) ; do
            if [ -n "$CLASSPATH" ] ; then
                CLASSPATH="$CLASSPATH":"$LCF_HOME"/processes/jar/"$filename"
            else
                CLASSPATH="$LCF_HOME"/processes/jar/"$filename"
            fi
        done
        
        # Build the defines
        DEFINES="-Dorg.apache.lcf.configfile=$LCF_HOME/properties.ini"
        if [ -e "$LCF_HOME/processes/define" ] ; then
            for filename in $(ls -1 "$LCF_HOME"/processes/define) ; do
                DEFINEVAR=-D"$filename"=$(cat "$LCF_HOME"/processes/define/"$filename")
                DEFINES="$DEFINES $DEFINEVAR"
            done
        fi
        
        "$JAVA_HOME/bin/java" "$DEFINES" --classpath "$CLASSPATH" "$@"
        exit $?
        
    else
        echo "Environment variable LCF_HOME is not properly set." 1>&2
        exit 1
    fi
    
else
    echo "Environment variable JAVA_HOME is not properly set." 1>&2
    exit 1
fi
