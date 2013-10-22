<!--
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

<script type="text/javascript">
<!--
function checkConfig()
{
  return true;
}
 
function checkConfigForSave()
{
    
    if (editconnection.app_key.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('DropboxRepositoryConnector.TheAppKeyMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('DropboxRepositoryConnector.Server'))");
    editconnection.app_key.focus();
    return false;
  }
  
    if (editconnection.app_secret.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('DropboxRepositoryConnector.TheAppSecretMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('DropboxRepositoryConnector.Server'))");
    editconnection.app_secret.focus();
    return false;
  }
    
  if (editconnection.key.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('DropboxRepositoryConnector.TheKeyMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('DropboxRepositoryConnector.Server'))");
    editconnection.key.focus();
    return false;
  }
  if (editconnection.secret.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('DropboxRepositoryConnector.TheSecretMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('DropboxRepositoryConnector.Server'))");
    editconnection.secret.focus();
    return false;
  }
  return true;
}
//-->
</script>