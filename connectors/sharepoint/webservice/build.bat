@echo off
REM Licensed to the Apache Software Foundation (ASF) under one or more
REM contributor license agreements. See the NOTICE file distributed with
REM this work for additional information regarding copyright ownership.
REM The ASF licenses this file to You under the Apache License, Version 2.0
REM (the "License"); you may not use this file except in compliance with
REM the License. You may obtain a copy of the License at
REM
REM http://www.apache.org/licenses/LICENSE-2.0
REM
REM Unless required by applicable law or agreed to in writing, software
REM distributed under the License is distributed on an "AS IS" BASIS,
REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
REM See the License for the specific language governing permissions and
REM limitations under the License.

REM Command-line build script for the ManifoldCF SharePoint web service extension.
REM $Id$

REM This build script handles only changes to the code itself; changing other things (like the form of the WSDL) requires more work.
REM The following article describes how to create and modify the static discovery and WSDL files:  http://msdn.microsoft.com/en-us/library/ms464040.aspx
REM The following article describes how to change the payload specified in manifest.xml and MCPermissionsService.ddf: http://msdn.microsoft.com/en-us/library/ms916839.aspx
REM The following article describes the overall packaging needed to build a SharePoint web extension: http://msdn.microsoft.com/en-us/library/ms916839.aspx#sharepoint_deployingwebparts_topic2

REM Get rid of old stuff, in case it is there
del bin\*.dll
del Packages\*.wsp

REM Build the dll
c:\windows\Microsoft.NET\Framework\v2.0.50727\MSBuild.exe MCPermissionsService.csproj
REM Build the wsp
c:\windows\system32\makecab.exe /f MCPermissionsService.ddf

