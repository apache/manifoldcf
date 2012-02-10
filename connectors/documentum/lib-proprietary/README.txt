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

Portions of this package (namely, the documentum-server process) depend on the
Documentum Foundation Classes.  DFC does not in general change their API much
between revs, but the underlying software may change quite a bit, and the versions
of Documentum that will work with each revision also change (as would be expected).

In order to build all the classes and processes for this connector, you must install a
version of Documentum's DFC on your system, and then locate the pertinent jars.
The jars are described in the included Makefile.  Bear in mind that DFC has a JNI
component as well, so the actual documentum-server process must include access
to the appropriate dll's or so's in order to be functional.  This is accomplished by setting
an environment variable before running the scripts that start the ManifoldCF
documentum connector server process.  Read the "how-to-build-and-deploy.html"
document for details.

Copy all the jars that are needed to run your version of DFC into this directory, and
the normal connector build should create all the artifacts you need to run the connector.

The code that's included here was tested against DFC 5.3.5 SP2.







