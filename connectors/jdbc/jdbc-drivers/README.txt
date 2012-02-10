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

The jdbc connector can, in theory, work with any JDBC driver that has been
added into the list it knows about.  The code as granted knows about the following:

Mssql/Sybase (via open-source jtds)
Postgresql (via an appropriate open-source postgresql JDBC driver)
Oracle (via a proprietary, but freely downloadable Oracle JDBC driver)

The connector was tested against the following versions of the above:

Oracle: oracle ojdbc5 jdk1.5 and ojdbc6 jdk1.6
Jtds: version 1.2.2, downloadable from SourceForge
Postgresql: the debian postgresql driver package for postgresql 8.3.7, aka libpg-java, version 8.2-504-2.

None of these required custom changes.

To build this connector with Oracle support, copy the Oracle JDBC
driver into the root "lib-proprietary" directory, calling it "ojdbc.jar", before building.
To build with Mssql/Sybase, copy a version of the jtds driver into that same directory,
and call it "jtds.jar".  DO NOT COPY JDBC DRIVERS TO THIS DIRECTORY;
they will not be picked up by ManifoldCF.

Then, build the connector using the normal ant build script.  Further information can be
found on the "how-to-build-and-deploy.html" documentation page.


