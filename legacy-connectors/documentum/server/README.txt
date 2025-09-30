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

The documentum-server process depends on the Documentum Foundation Classes.

In order to run the documentum-server process, you must install a
version of Documentum's DFC on your system, and then locate the pertinent jars.
Copy all the jars that are needed to run your version of DFC into this directory, and
start the process using the supplied "run" scripts.

Remember that DFC has a JNI component as well, so the process must include access
to the appropriate dll's or so's in order to be functional.  This is accomplished by setting
an environment variable before running the scripts that start the ManifoldCF
documentum connector server process.

If you find there are incompatibilities between your version of DFC and the one your
ManifoldCF release was built against, you must build ManifoldCF yourself, and include
a copy of DFC prior to building, in the directory connectors/documentum/lib-proprietary.

Read the "how-to-build-and-deploy.html" document for details.







