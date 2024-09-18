# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#!/bin/sh
gpg_local_user=$1
mcf_version=$2

gpg --local-user "$1" --armor --output apache-manifoldcf-$2-src.zip.asc --detach-sig apache-manifoldcf-$2-src.zip
gpg --local-user "$1" --armor --output apache-manifoldcf-$2-src.tar.gz.asc --detach-sig apache-manifoldcf-$2-src.tar.gz
gpg --local-user "$1" --armor --output apache-manifoldcf-$2-lib.zip.asc --detach-sig apache-manifoldcf-$2-lib.zip
gpg --local-user "$1" --armor --output apache-manifoldcf-$2-lib.tar.gz.asc --detach-sig apache-manifoldcf-$2-lib.tar.gz
gpg --local-user "$1" --armor --output apache-manifoldcf-$2-bin.zip.asc --detach-sig apache-manifoldcf-$2-bin.zip
gpg --local-user "$1" --armor --output apache-manifoldcf-$2-bin.tar.gz.asc --detach-sig apache-manifoldcf-$2-bin.tar.gz

gpg --print-md MD5 apache-manifoldcf-$2-src.zip > apache-manifoldcf-$2-src.zip.md5
gpg --print-md MD5 apache-manifoldcf-$2-src.tar.gz > apache-manifoldcf-$2-src.tar.gz.md5
gpg --print-md MD5 apache-manifoldcf-$2-lib.zip > apache-manifoldcf-$2-lib.zip.md5
gpg --print-md MD5 apache-manifoldcf-$2-lib.tar.gz > apache-manifoldcf-$2-lib.tar.gz.md5
gpg --print-md MD5 apache-manifoldcf-$2-bin.zip > apache-manifoldcf-$2-bin.zip.md5
gpg --print-md MD5 apache-manifoldcf-$2-bin.tar.gz > apache-manifoldcf-$2-bin.tar.gz.md5

gpg --print-md SHA512 apache-manifoldcf-$2-src.zip > apache-manifoldcf-$2-src.zip.sha512
gpg --print-md SHA512 apache-manifoldcf-$2-src.tar.gz > apache-manifoldcf-$2-src.tar.gz.sha512
gpg --print-md SHA512 apache-manifoldcf-$2-lib.zip > apache-manifoldcf-$2-lib.zip.sha512
gpg --print-md SHA512 apache-manifoldcf-$2-lib.tar.gz > apache-manifoldcf-$2-lib.tar.gz.sha512
gpg --print-md SHA512 apache-manifoldcf-$2-bin.zip > apache-manifoldcf-$2-bin.zip.sha512
gpg --print-md SHA512 apache-manifoldcf-$2-bin.tar.gz > apache-manifoldcf-$2-bin.tar.gz.sha512