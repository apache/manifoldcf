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

To test this connector, download the Testcontainers dependencies using "ant download-dependencies"
or the global "ant make-deps" target, and use the "ant test" target for the standard ant build.
The integration tests start MongoDB 9.0.2 as a Docker container, so a running Docker daemon is required.
You can read more about the process on the "how-to-build-and-deploy.html" documentation page.
