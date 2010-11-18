/* $Id$ */

/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
* 
* http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
#ifndef CONSTANTS_H
#define CONSTANTS_H

#define STATIC_STRLEN(x)        (sizeof((x)) - 1) 

#define GROUP_HEADER_KEY 	"AAAGRP"
#define USER_HEADER_KEY 	"AAAUSR"
#define USER_NAME_HEADER_KEY    "AAAUSR_NAME"
#define WARNING_HEADER_KEY  "AAAWARNING"

#define FAKE_USER_HEADER_KEY   "AAA_FAKE_USER"

#define DEFAULT_NUM_GROUPS 	11
#define DEFAULT_NUM_USERS 	1
#define DEFAULT_NUM_WARNING	1

// apache header line separator length in bytes ": "
#define HEADER_SEPARATOR_LEN          (2)

#endif
