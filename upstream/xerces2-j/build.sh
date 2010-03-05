#!/bin/sh
#
#=========================================================================
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#=========================================================================
#

echo
echo "Xerces-Java Build System"
echo "------------------------"

if [ "$JAVA_HOME" = "" ] ; then
    echo "ERROR: JAVA_HOME not found in your environment."
    echo
    echo "Please, set the JAVA_HOME variable in your environment to match the"
    echo "location of the Java Virtual Machine you want to use."
    exit 1
fi

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false;
case "`uname`" in
    CYGWIN*) cygwin=true ;;
esac

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin ; then
    [ -n "$JAVA_HOME" ] &&
    JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
fi

LIBDIR=./tools
ANT_HOME="$LIBDIR"
LOCALCLASSPATH="$JAVA_HOME/lib/tools.jar:$JAVA_HOME/lib/classes.zip"
LOCALCLASSPATH="$LOCALCLASSPATH:$LIBDIR/ant.jar"
LOCALCLASSPATH="$LOCALCLASSPATH:$LIBDIR/ant-nodeps.jar"
LOCALCLASSPATH="$LOCALCLASSPATH:$LIBDIR/xml-apis.jar"
LOCALCLASSPATH="$LOCALCLASSPATH:$LIBDIR/xercesImpl.jar"
LOCALCLASSPATH="$LOCALCLASSPATH:$LIBDIR/bin/xjavac.jar"


# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
    JAVA_HOME=`cygpath --path --windows "$JAVA_HOME"`
    LOCALCLASSPATH=`cygpath --path --windows "$LOCALCLASSPATH"`
fi

echo
echo Building with classpath $LOCALCLASSPATH
echo Starting Ant...
echo

"$JAVA_HOME"/bin/java -Dant.home="$ANT_HOME" -classpath "$LOCALCLASSPATH" org.apache.tools.ant.Main $@ 
