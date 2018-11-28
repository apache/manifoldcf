#!/bin/sh

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

#
# File: mvn-bootstrap.sh
#
# Created: Wednesday, September 21 2011 by Alex Ott
#

mvn install:install-file -Dfile=lib/opensaml.jar -DgroupId=org.opensaml -DartifactId=opensaml -Dversion=1.0.1 -Dpackaging=jar
mvn install:install-file -Dfile=lib/xmlsec.jar -DgroupId=xml-security -DartifactId=xmlsec -Dversion=1.4.1 -Dpackaging=jar

echo "Dependencies installed"
