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

The LiveLink connector requires a client library from OpenText in order
to function.  The client jar is call lapi.jar, and the version we have
tested against can be found in the LAPI 9.7.1 package from OpenText.
For some modes of operation, the jar llssl.jar from the same package
may also be required.  Copy these jars to this directory, and build the
connector using the standard ant build process.  More can be found in the
"how-to-build-and-deploy.html" documentation page.


