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

The ManifoldCF Meridio Plugin is required for the Meridio authority connector
to properly work with a Meridio system.  Its function is to provide lookup of user
ACLs that is fast enough to actually use.  Unfortunately, although work on
the plugin was directed and paid-for by MetaCarta, Inc., under terms where
MetaCarta, Inc. technically owned the source code, the source code was
never transferred to MetaCarta, Inc., and the compiled plugin installation package is
all that is currently available.  Although MetaCarta, Inc. granted the compiled
plugin installation package to the Apache Software Foundation, this does not
meet Apache's guidelines for what "open source" should mean.

The Apache ManifoldCF project has thus chosen to move this installation package for this plugin
to the Google Code package called "manifoldcf-meridio-plugin", until such
time as we can obtain the source files or redevelop them.  You may obtain
the plugin and its applicable licenses by checking out a URL using
Subversion (svn), as follows:

svn co https://github.com/DaddyWri/manifoldcf-meridio-plugin.git/trunk manifoldcf-meridio-plugin

License and building instructions are to be found in this package, and installation instructions
can be found right here in the file "Installation readme.txt".
