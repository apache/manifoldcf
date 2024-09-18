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

#!/bin/sh

#Get Maven POM version
export mcfVersion=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

#Built Branch Version
export currentMavenVersion="$mcfVersion"
export suffixToRemove="SNAPSHOT"
export branchVersion=${currentMavenVersion%$suffixToRemove}

#Update all the Maven modules with the new version
mvn versions:set -DnewVersion=$mcfVersion -DremoveSnapshot -DgenerateBackupPoms=false

#Update Ant script with the new RC version
sed -i -e 's/"$mcfVersion"-dev/"$mcfVersion"/g' build.xml;

#Update CHANGES.txt
sed -i -e 's/"$mcfVersion"-dev/Release "$mcfVersion"/g' CHANGES.txt;

#Ant Build
ant make-core-deps make-deps image

#Artifact version
export artifactVersion=$branchVersion"dev"

#Rename KEYS and CHANGES.txt artifacts
cp KEYS apache-manifoldcf-$artifactVersion.KEYS
cp CHANGES.txt apache-manifoldcf-$artifactVersion.CHANGES.txt