#!/bin/sh
releasecandidatetag=$1

#Get Maven POM version
export mcfVersion=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)

#Built Branch Version
export currentMavenVersion="$mcfVersion"
export suffixToRemove="SNAPSHOT"
export branchVersion=${currentMavenVersion%$suffixToRemove}
branchTag=$branchVersion$releasecandidatetag

#Create new release candidate branch
git branch release-$branchTag
git push --set-upstream origin release-$branchTag
git checkout release-$branchTag

#Update all the Maven modules with the new version
mvn versions:set -DnewVersion=$mcfVersion -DremoveSnapshot -DgenerateBackupPoms=false

#Update Ant script with the new RC version
sed -i -e 's/"$mcfVersion"-dev/"$mcfVersion"/g' build.xml;

#Update CHANGES.txt
sed -i -e 's/"$mcfVersion"-dev/Release "$mcfVersion"/g' CHANGES.txt;

#Ant Build
ant make-core-deps make-deps image

#Maven Build
mvn clean install -B -DskipTests -DskipITs

#Update MCF version in the properties.xml files
sed -i -e 's/"$mcfVersion"-dev/"$mcfVersion"/g' dist/example/properties.xml;
sed -i -e 's/"$mcfVersion"-dev/"$mcfVersion"/g' dist/example-proprietary/properties.xml;
sed -i -e 's/"$mcfVersion"-dev/"$mcfVersion"/g' dist/multiprocess-file-example/properties.xml;
sed -i -e 's/"$mcfVersion"-dev/"$mcfVersion"/g' dist/multiprocess-file-example-proprietary/properties.xml;

#RAT licence checks
mvn -pl . apache-rat:check

#Generate RAT License Report
echo -e "Printing RAT report\n"
cat target/rat.txt || true

#Commit and Push
find . -name 'pom.xml' -exec git add {} \;
git add CHANGES.txt build.xml
git commit -am "Create $releasecandidatetag tag for MCF $mcfVersion"
git push

#Artifact version
export artifactVersion=$branchVersion"dev"

#Rename KEYS and CHANGES.txt artifacts
cp KEYS apache-manifoldcf-$artifactVersion.KEYS
cp CHANGES.txt apache-manifoldcf-$artifactVersion.CHANGES.txt