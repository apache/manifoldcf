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

The Memex connector is no longer distributed at the request of Memex, who
is now owned by SAS.  If you have the Memex connector sources and an agreement
from Memex, note that the Memex connector requires compilation against a Memex
client library, usually called JavaMXIELIB.jar.  Copy that jar into this directory,
and the sources for the connector into the directory above, and the normal
connector ant build should build the connector.

The API for Memex Patriarch changes pretty dramatically between revisions.
The revision number this connector was written against was 1.3.0.
  