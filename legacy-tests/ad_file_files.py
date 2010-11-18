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

import sys
import os
import SQAhelpers

from wintools import filetools
from wintools import spew_files

branch_name = "File Tests"
file_sizes = [1048576, 10485760, 31457280, 52428800, 104857600, 314572800,
    524288000]
filenames = ["a" * 200, "With Spaces", "CaseDiff", "casediff", "Casediff"]

def print_file_list_to_log(samba_version):
    spew_file_structure(log_only=1, samba_version=samba_version)

def spew_file_structure(log_only, samba_version):
    # put together the file structure.  All the variously-sized files
    # will be in one directory.  The contents of each will consist of a 
    # different geographical reference, but the same string
    location_id = 0
    template_list = []
    for size in file_sizes:
        template_list.append(spew_files.Template(
            size=size,
            contents=(SQAhelpers.LocationList[location_id] + " : " +
                branch_name + "\n")))
        location_id += 1

    # Create the top directory
    spew_files.walk(filetools.SambaMediumLivedDirPath,
        [[spew_files.One(branch_name)]],
        log_prefix=filetools.SambaParameters[samba_version]["SambaMediumLivedIngestionPath"],
        toplevel=filetools.SambaMediumLivedDirPath,
        log_only=log_only)
    # Create each of the specially-named files
    files_created = 0
    for filename in filenames:
        spew_files.walk(
            os.path.join(filetools.SambaMediumLivedDirPath, branch_name),
            [[spew_files.OneFile(filename, 
                template=template_list[files_created])]],
            log_prefix=filetools.SambaParameters[samba_version]["SambaMediumLivedIngestionPath"],
            toplevel=filetools.SambaMediumLivedDirPath,
            log_only=log_only)
        files_created += 1
    # Create the rest of the appropriately-sized files, giving them
    # default names
    spew_files.walk(
        os.path.join(filetools.SambaMediumLivedDirPath, branch_name),
        [[spew_files.ManyFiles(1, len(file_sizes[files_created:]),
            templates=template_list[files_created:])]],
        log_prefix=filetools.SambaParameters[samba_version]["SambaMediumLivedIngestionPath"],
        toplevel=filetools.SambaMediumLivedDirPath,
        log_only=log_only)

if __name__ == '__main__':

    log_only = 0
    if (len(sys.argv) > 2):
        if (sys.argv[2] == "-l"):
            log_only = 1
        else:
            sys.stderr.write("usage:  file_files.py {3.0|3.2} [-l]\n")
            sys.exit(1)

    spew_file_structure(log_only,sys.argv[1])
