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

"""
    Program to create files for the qashare we use for large crawls in the
    Share{Crawler,Connector} tests.
    
    Run without any arguments it will recreate every file in the share.
    
    Run with one argument it will create just that toplevel directory.
        (Run with -h to get help)
        
    It can be run to only write a log file with the -l switch.

    This is imported into ad_tree.py with some members and methods used directly.
    
    Usually run on cadillac as such:
        * Check out mcsqa/tests (you need wintools/)
        * Run:
            $ su qashare -c "time python ad_tree_files.py smoketest"
"""

import sys

from wintools import spew_files
from wintools import filetools

user = "treeTestUser"

ex_file_sizes = [
                4096, 4096, 4096, 4096, 4096, 4096, 4096, 4096, 7641,
                7910, 7976, 8019, 8042, 8152, 8204, 8390, 168117, 186855,
                188871, 193191, 243483, 248025, 268567, 272976, 278791, 279838,
                284954, 301270, 368415, 374629, 400896, 434561, 442382, 466247,
                522732, 535052, 550151, 561436, 563697, 605347, 635289, 642118,
                655380, 671724, 697808, 711836, 737450, 797255, 802226, 822175,
                830311, 845487, 873789, 888061, 943154, 1001140, 1038339,
                1052979, 1055240, 1057142, 1087776, 1099876, 1102036, 1128912,
                1134482, 1143403, 1151797, 1158515, 1164645, 1180117, 1196291,
                1201358, 1202204, 1205248, 1212163, 1213372, 1237239, 1239768,
                1250890, 1252958, 1294032, 1297281, 1364475, 1461526, 1471135,
                1547282, 1583294, 1587977, 1618752, 1651179, 1680273, 1737968,
                1760709, 1765642, 1770114, 1825676, 1844948, 1913175, 1927950,
                2006821, 2014857, 2050726, 2075008, 2081073, 2133826, 2145881,
                2248126, 2268190, 2309181, 2340304, 2445836, 2447269, 2548500,
                2560820, 2565882, 2617662, 2786905, 2939453, 2995331, 2995916,
                3131024, 3171000, 3246355, 3266196, 3357184, 3416930, 3442956,
                3511231, 3524973, 3607008, 3639410, 3642928, 3675352, 3737779,
                3752605, 3816832, 3823798, 3857162, 3920126, 3950298, 3951530,
                3975813, 3990095, 3997095, 4150102, 4178104, 4221439, 4280379,
                4303161, 4361529, 4379900, 4454607, 4536395, 4545910, 4563372,
                4727313, 4748124, 4783787, 4853343, 5574133, 5623214, 5938112,
                5980159, 6109206, 6200114, 6535509, 6726844, 6850112, 6980599,
                7174212, 7452693, 7624021, 7682409, 7737895, 8007378, 8066414,
                8182072, 8321208, 8684092, 9059497, 9536101, 9703414, 9711368,
                9868230, 10168040, 10395947, 11489744, 11625736, 11738747,
                12261367, 12307847, 12779088, 13246031, 13436108, 13478792,
                13782472, 14497005, 14837178, 15059946, 15634450, 16458729,
                16619174, 18124109, 18336922, 19263707, 19507934, 19671239,
                20889082, 21928673, 22556849, 23056664, 23373705, 23520997,
                24841628, 27432028, 27500515, 27865699, 28917290, 29154635,
                31672659, 32661875, 33008136, 33847133, 34246816, 35787505,
                39827869, 41386730, 45245507, 51867430, 54599563, 61765911,
                65225422, 79477727, 91290583, 91842019, 106891980, 124917534,
                155442171, 196809518, 212872057, 294521275]

top_level_dirs = {
    "smoketest": {
        "dirs_deep": 2,
        "dirs_wide": 3,
        "files_per_dir": 3,
        "files_in_deepest_dir": 10,
        "file_sizes": [128, 1024, 10*1024]},
    "EXLike": {
        "dirs_deep": 0,
        "dirs_wide": 0,
        "files_per_dir": 0,
        "files_in_deepest_dir": len(ex_file_sizes),
        "file_sizes": ex_file_sizes},
    "TenEXLike": {
        "dirs_deep": 0,
        "dirs_wide": 0,
        "files_per_dir": 0,
        "files_in_deepest_dir": (10 * len(ex_file_sizes)),
        "file_sizes": ex_file_sizes},
    "ManyDir": {
        "dirs_deep": 8,
        "dirs_wide": 2,
        "files_per_dir": 4,
        "files_in_deepest_dir": 4,
        "file_sizes": [52428800, 52428800, 26214400, 26214400] \
                    + [15728640 for i in range(0,4)] \
                    + [10485760 for i in range(0,4)] \
                    + [1024 for i in range(0,4)] \
                    + [5242880 for i in range(0,4)] \
                    + [10240 for i in range(0,4)] \
                    + [3145728 for i in range(0,4)] \
                    + [102400 for i in range(0,4)] \
                    + [1048576, 1048576, 512000, 512000]},
    "LargeDir": {
        "dirs_deep": 0,
        "dirs_wide": 0,
        "files_per_dir": 0,
        "files_in_deepest_dir": 100000,
        "file_sizes": [1024]},
    "DeepDir": {
        "dirs_deep": 50,
        "dirs_wide": 0,
        "files_per_dir": 0,
        "files_in_deepest_dir": 10,
        "file_sizes": [1024]}}

def make_templates(num_of_files, file_sizes, size_index):
    """Convenience function to create a spew_files.Template from some counts."""
    templates = []
    for file_num in range(0, num_of_files):
        templates.append(spew_files.Template(size=(file_sizes[size_index])))
        size_index = ((size_index + 1) % len(file_sizes))
    return [templates, size_index]

def print_file_list_to_log(log_prefix):
    """Print the full list of spewed files to a log file in log_prefix."""
    spew_file_structure(top_level_dirs, log_only=1, log_prefix=log_prefix)

def spew_file_structure(dirs, log_only, log_prefix):
    """Spew the files defined in the dirs subset of top_level_dirs to the appropriate place,
       optionally only logging what files it would in fact spew.
       
       In either case the log file is placed in the log_prefix directory."""
    for dir_name in dirs:
        size_index = 0
        dir_struct_list = [[spew_files.One(dir_name)]]
        if top_level_dirs[dir_name]["dirs_wide"] == 0 and \
           top_level_dirs[dir_name]["dirs_deep"] > 1:
            dir_struct_list.append([spew_files.ManyDeep(1,
                    top_level_dirs[dir_name]["dirs_deep"])])
        elif top_level_dirs[dir_name]["dirs_wide"] > 0:
            for dir_level in range(0, top_level_dirs[dir_name]["dirs_deep"]):
                dir_level_list = [spew_files.ManyWide(1,
                    top_level_dirs[dir_name]["dirs_wide"])]
                if top_level_dirs[dir_name]["files_per_dir"] > 0:
                    (templates, size_index) = make_templates(
                            top_level_dirs[dir_name]["files_per_dir"],
                            top_level_dirs[dir_name]["file_sizes"],
                            size_index)
                    dir_level_list.append(
                        spew_files.ManyFiles(1,
                            top_level_dirs[dir_name]["files_per_dir"],
                            "%d.txt", templates=templates))
                dir_struct_list.append(dir_level_list)
        if top_level_dirs[dir_name].has_key("files_in_deepest_dir"):
            num_of_files = top_level_dirs[dir_name]["files_in_deepest_dir"]
        else:
            num_of_files = len(top_level_dirs[dir_name]["file_sizes"])
        (templates, size_index) = make_templates(
                            num_of_files,
                            top_level_dirs[dir_name]["file_sizes"],
                            size_index)
        dir_struct_list.append([spew_files.ManyFiles(1,
            top_level_dirs[dir_name]["files_in_deepest_dir"],
            "%d.txt", templates=templates)])
        spew_files.walk(filetools.SambaMediumLivedDirPath,
            dir_struct_list,
            log_prefix=log_prefix,
            toplevel=filetools.SambaMediumLivedDirPath,
            log_only=log_only)

def main(args):
    """Main functionality and argument parsing."""
    LOG_ONLY_SWITCH = "-l"
    log_only = 0
    if LOG_ONLY_SWITCH in args:
        log_only = 1
        args.remove(LOG_ONLY_SWITCH)
        
    dirs = top_level_dirs
    if len(args) > 2:
        if args[2] in top_level_dirs:
            dirs = [args[2]]
        else:
            sys.exit("usage: %s {3.0|3.2} [-l] [%s]\n" % (sys.argv[0], "|".join(top_level_dirs)))

    spew_file_structure(dirs, log_only, filetools.SambaParameters[args[1]]["SambaMediumLivedIngestionPath"], args[1])
    
if __name__ == '__main__':
    main(sys.argv)
