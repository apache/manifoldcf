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
  if (editconnection.nuxeo_port.value != "" && !isInteger(editconnection.nuxeo_port.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.PortMustBeAnInteger'))");
    editconnection.nuxeo_port.focus();
    return false;
  }

  if (editconnection.nuxeo_host.value != "" && editconnection.nuxeo_host.value.indexOf("/") != -1)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.HostMustNotIncludeSlash'))");
    editconnection.nuxeo_host.focus();
    return false;
  }

 if (editconnection.nuxeo_path.value != "" &&
 !(editconnection.nuxeo_path.value.indexOf("/") == 0))
 {
 alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.PathMustBeginWithASlash'))");
 editconnection.nuxeo_path.focus();
 return false;
 }

  return true;
}
 
function checkConfigForSave()
{
    
  if (editconnection.nuxeo_host.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.HostMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.Server'))");
    editconnection.nuxeo_host.focus();
    return false;
  }
  
  if (editconnection.nuxeo_host.value != "" && editconnection.nuxeo_host.value.indexOf("/") != -1)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.HostMustNotIncludeSlash'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.Server'))");
    editconnection.nuxeo_host.focus();
    return false;
  }

  if (editconnection.nuxeo_port.value != "" && !isInteger(editconnection.nuxeo_port.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.PortMustBeAnInteger'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.Server'))");
    editconnection.nuxeo_port.focus();
    return false;
  }

  if (editconnection.nuxeo_username.value != "" && editconnection.nuxeo_password.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.PasswordMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.Server'))");
    editconnection.nuxeo_password.focus();
    return false;
  }
  
  if (editconnection.nuxeo_path.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.PathMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.Server'))");
    editconnection.nuxeo_path.focus();
    return false;
  }
  
  if (editconnection.nuxeo_path.value != "" &&
    !(editconnection.nuxeo_path.value.indexOf("/") == 0))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.PathMustBeginWithASlash'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.Server'))");
    editconnection.nuxeo_path.focus();
    return false;
  }
  
  return true;
}
// -->
</script>
