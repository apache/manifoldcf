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

set SHPADMINDIR=C:\Program Files\Common Files\Microsoft Shared\web server extensions\12\BIN
set SHPADMINEXE=%SHPADMINDIR%\stsadm.exe
set DEFAULTSERVICENAME=MetaCarta.SharePoint.MCPermissionsService.wsp

echo Installing %DEFAULTSERVICENAME% using %SHPADMINEXE%...

"%SHPADMINEXE%" -o addsolution -filename "%DEFAULTSERVICENAME%"
"%SHPADMINEXE%" -o execadmsvcjobs
"%SHPADMINEXE%" -o deploysolution -name "%DEFAULTSERVICENAME%" -immediate -allowGacDeployment -force
"%SHPADMINEXE%" -o execadmsvcjobs

echo Install complete
