# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License. You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

This directory is meant to contain root-level proprietary jars, usually JDBC drivers such as
Oracle or MySQL, which cannot be redistributed under the Apache license.

The following JDBC drivers are of interest:

Oracle: oracle ojdbc5 jdk1.5 and ojdbc6 jdk1.6
MSSQL/Sybase: jtds 1.2
MySQL: mysql 5.1

To build with Oracle JDBC support, copy the Oracle JDBC
driver into this directory, calling it "ojdbc.jar", before building.
To build with Mssql/Sybase, copy a version of the jtds driver here,
and call it "jtds.jar".
To build with MySQL support, copy a version of the MySQL driver
here, and call it "mysql-connector-java.jar".

Then, build ManifoldCF using the normal ant build script.  Further information can be
found on the "how-to-build-and-deploy.html" documentation page.


