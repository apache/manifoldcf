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

Portions of the FileNet connector need to be compiled against a set of jars
delivered along with the FileNet API.  The version of the API seems to matter;
bugs are pretty rampant, and small point releases seem to be needed to correct them.

Copy the filenet API jars into this directory, and build the connector using the
provided ant build, and all processes should be built properly.  For some versions
of P8, there is a dependency on WASP, whose location you will need to specify
as an environment variable when you start the filenet connector server process.
See the "how-to-build-and-deploy.html" documentation page for details.

The version of the jars this connector was tested against code from Filenet
P8 API 4.0.0 (P8CE-4.0.0-002-Win), and later with P8 4.5.0.  The jars were re-labeled
with the prefix "ibm-" to avoid collision with the names of other jars, since many
of them were common with other packages such as xerces, but had been modified
in some way.

