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

======================================================================
 MetaCartaWS
 Installation Guide
 1st September 2007
======================================================================

PREREQUISITES
==============

The .NET framework version 1.1 should be installed


INSTALL INSTRUCTIONS
====================

1. Web Service Installation

To install the MetaCarta web service navigate to the 'Web Service\Installation files' folder and run the 'setup.exe' file
The installer will guide you through the rest of the process, We recommend you do not change the default settings.

IMPORTANT
=========
When the installation is complete navigate to the virtual directory chosen for installation (default: C:\Inetpub\wwwroot\MetaCartaWebService)
Find the following text at the bottom of the 'Web.config' document

<appSettings>
	<add key="ConnectionInfo" value=..."
	<add key="MetaCartaWebService.DMWebRef.MeridioDM" value=..."
 </appSettings>

amend these values to be a valid connection string to the Meridio DMSDB and a valid location to the Meridio DM web service respectively


2. Test Harness Installation
To install the test harness navigate to the 'Test Harness\Installation files' folder and run the 'setup.exe' file
The installer will guide you through the rest of the process. We recommend you do not change the default settings.

IMPORTANT
=========
When the installation is complete navigate to your chosen installation path (default: C:\Program Files\Meridio\MetaCarta Test Harness\)
If the MetaCarta webservice has been installed on a different machine to the test harness and/or you have chosen a virtual directory other than the default
then the 'MetaCartaTestHarness.exe.config' file will require the following changes:

Amend the key value '<add key="MetaCartaTestHarness.MetaCartaWebService.MetaCarta value=...' to point to the correct location where the MetaCarta web service is installed.
Amend the key value '<add key="MetaCartaTestHarness.DMWebRef.MeridioDM" value=...' to point to the correct location where the meridio DM web service is installed.

The test harness can be executed by running the file 'MetaCartaTestHarness.exe'




UNINSTALL INSTRUCTIONS
======================

1. MetaCarta web service uninstall
Go to: control panel --> add/remove programs --> MetaCarta WebService --> Remove
The uninstaller will guide you through the rest of the process


2. Test Harness uninstall
Go to: control panel --> add/remove programs --> MetaCarta Test Harness --> Remove
The uninstaller will guide you through the rest of the process



