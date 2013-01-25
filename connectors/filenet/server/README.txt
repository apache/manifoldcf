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

The filenet-server process depends on the FileNet API.

In order to run the filenet-server process, you must install a
version of the FileNet API on your system, and then locate the pertinent jars.
Copy all the jars that are needed to run your version of the API into this directory, and
start the process using the supplied "run" scripts.

If you find there are incompatibilities between your version of DFC and the one your
ManifoldCF release was built against, you must build ManifoldCF yourself, and include
a copy of the API prior to building, in the directory connectors/filenet/lib-proprietary.

Read the "how-to-build-and-deploy.html" document for details.







