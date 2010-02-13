#!/usr/bin/python

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

import jcifsconnector_load as jcl

# Server name to talk to
jcl.jcifsServerName = "w2k3-ad-92-3.QA-AD-92.METACARTA.COM"
# Domain
jcl.jcifsDomain = "QA-AD-92.METACARTA.COM"
# User
jcl.jcifsUser = "Administrator@QA-AD-92.METACARTA.COM"
# Password
jcl.jcifsPassword = "password"
# Share name
jcl.jcifsShare = "qashare"

jcl.SHARE_ROOT = "R:\\"

main=jcl.main

if __name__ == '__main__':
    main()
