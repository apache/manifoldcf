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

This connector requires someone obtain correct versions of the appropriate wsdls
and xsds before it can be built.  This will require two utilities from Microsoft.  The
first is "disco.exe", which is a web-service discovery utility.  The second is a
Microsoft xml difference utility called xmldiffpatch.

disco.exe can be found under c:\Program Files\Microsoft SDKs\Windows\V6.x\bin,
after Visual Studio is installed.  The xml diff utility used is downloadable from:

http://msdn.microsoft.com/en-us/library/aa302294.aspx

Patches are meant to be applied using the xmlpatch.exe also included in the same package.

The Meridio version this comparision applies to is Meridio 5.0 SR 1.  With that version
of Meridio correctly installed, run the following disco commands, as modified with the
correct server name, credentials, and output directory:

disco /out:<output_directory> /username:<user_name> /password:<password> /domain:<domain> "http://<server_name>/DMWS/MeridioDMWS.asmx"
disco /out:<output_directory> /username:<user_name> /password:<password> /domain:<domain> "http://<server_name>/RMWS/MeridioRMWS.asmx"

Next, apply the patches:

xmlpatch MeridioDMWS.wsdl MeridioDMWS_axis.wsdl.xmldiff <outputdir>\MeridioDMWS_axis.wsdl
xmlpatch MeridioRMWS.wsdl MeridioRMWS_axis.wsdl.xmldiff <outputdir>\MeridioRMWS_axis.wsdl
xmlpatch DMDataSet.xsd DMDataSet.xsd.xmldiff <outputdir>\DMDataSet.xsd
xmlpatch RMDataSet.xsd RMDataSet.xsd.xmldiff <outputdir>\RMDataSet.xsd
xmlpatch RMClassificationDataSet.xsd RMClassificationDataSet.xsd.xmldiff <outputdir>\RMClassificationDataSet.xsd

The output files should then be ready for axis and castor, respectively.
