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