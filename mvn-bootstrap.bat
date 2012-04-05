@echo off
rem Licensed to the Apache Software Foundation (ASF) under one or more
rem contributor license agreements.  See the NOTICE file distributed with
rem this work for additional information regarding copyright ownership.
rem The ASF licenses this file to You under the Apache License, Version 2.0
rem (the "License"); you may not use this file except in compliance with
rem the License.  You may obtain a copy of the License at
rem
rem     http://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.

cmd /c ant make-deps

cmd /c mvn install:install-file -Dfile=lib/jdbcpool.jar -DgroupId=com.bitmechanic -DartifactId=jdbcpool -Dversion=0.99 -Dpackaging=jar

cmd /c mvn install:install-file -Dfile=lib/commons-httpclient.jar -DgroupId=commons-httpclient -DartifactId=commons-httpclient -Dversion=3.1-mcf-1  -Dpackaging=jar
cmd /c mvn install:install-file -Dfile=lib/xercesImpl.jar -DgroupId=xerces -DartifactId=xercesImpl -Dversion=2.9.1-mcf-1  -Dpackaging=jar
cmd /c mvn install:install-file -Dfile=connectors/jcifs/lib-proprietary/jcifs.jar -DgroupId=org.samba.jcifs -DartifactId=jcifs -Dversion=1.3.17  -Dpackaging=jar

cmd /c mvn install:install-file -Dfile=lib/opensaml.jar -DgroupId=org.opensaml -DartifactId=opensaml -Dversion=1.0.1 -Dpackaging=jar
cmd /c mvn install:install-file -Dfile=lib/xmlsec.jar -DgroupId=xml-security -DartifactId=xmlsec -Dversion=1.4.1 -Dpackaging=jar

echo Dependencies installed
