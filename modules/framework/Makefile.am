# $Id$

## Licensed to the Apache Software Foundation (ASF) under one or more
## contributor license agreements. See the NOTICE file distributed with
## this work for additional information regarding copyright ownership.
## The ASF licenses this file to You under the Apache License, Version 2.0
## (the "License"); you may not use this file except in compliance with
## the License. You may obtain a copy of the License at
##
## http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing, software
## distributed under the License is distributed on an "AS IS" BASIS,
## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
## See the License for the specific language governing permissions and
## limitations under the License.

## top-level.  Run automake to produce Makefile.in.

## do not adhere to GNU standards, e.g. texi and NEWS files
AUTOMAKE_OPTIONS = foreign

## all subdirectories - common should be first, so common libraries
## are built before anything else
## Which means this list should NOT be alphabetized like we are doing
## with other like it.
SUBDIRS = \
	java-common \
	java-agents \
	crawler
