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

How to install and upgrade the MCPermissions SharePoint Web Service
=================================================

This web service is deployed using the SharePoint stsadm utility.  There are several included batch files: setup.bat (for first-time installation),
and upgrade.bat (for subsequent installations).  There is also a remove.bat, in case there is a need to uninstall the web service.

Note: This web service is only functional on SharePoint 3.0 (2007) systems.  Do not install on SharePoint 2.0 (2003) systems.


First-time installation
================

1) Copy the contents of the Package directory into a directory on your SharePoint server.
2) Open a "Command" prompt, and cd to the directory in which the files were copied.
3) If your SharePoint has been installed with its default paths, enter the command "setup.bat".  Otherwise, you may need to
   modify the setup.bat file first for it to be able to locate stsadm.exe.
4) Test your installation by browsing to the following SharePoint URL:

http://<your server name>:<server port>/<server location>/_vti_bin/MCPermissions.asmx

If everything was properly deployed, you should see a web page that describes the "Permissions" web service.


Repeat or upgrade installations
=======================

1) Copy the contents of the Package directory into a directory on your SharePoint server.
2) Open a "Command" prompt, and cd to the directory in which the files were copied.
3) If your SharePoint has been installed with its default paths, enter the command "upgrade.bat".  Otherwise, you may need to
   modify the upgrade.bat file first for it to be able to locate stsadm.exe.
   

Remove an installation
=================

1) Copy the contents of the Package directory into a directory on your SharePoint server.
2) Open a "Command" prompt, and cd to the directory in which the files were copied.
3) If your SharePoint has been installed with its default paths, enter the command "remove.bat".  Otherwise, you may need to
   modify the remove.bat file first for it to be able to locate stsadm.exe.

