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

Instructions for locating wsdls needed for building SharePoint Connector:

(1) Install Microsoft Visual Studio.
(2) Install SharePoint 3.0 (2007).
(3) In the directory c:\Program Files\Microsoft SDKs\Windows\V6.x\bin, locate the utility "disco.exe".
(4) You will need the following Microsoft SharePoint wsdls:

Permissions.wsdl
DspSts.wsdl
usergroup.wsdl
webs.wsdl
Lists.wsdl
versions.wsdl

Obtain these as follows:

disco /out:<output_directory> "http://<server_name>/_vti_bin/Permissions.asmx"
disco /out:<output_directory> "http://<server_name>/_vti_bin/DspSts.asmx"
disco /out:<output_directory> "http://<server_name>/_vti_bin/UserGroup.asmx"
disco /out:<output_directory> "http://<server_name>/_vti_bin/Webs.asmx"
disco /out:<output_directory> "http://<server_name>/_vti_bin/Lists.asmx"
disco /out:<output_directory> "http://<server_name>/_vti_bin/versions.asmx"

The Permissions.wsdl file should be placed in TWO places: under this directory,
and also under connectors/sharepoint/webservice/Web References/SPPermissionsService.
All other wsdls should go just in this directory.  The Permissions.disco file also
needs to be placed in connectors/sharepoint/webservice/Web References/SPPermissionsService.






