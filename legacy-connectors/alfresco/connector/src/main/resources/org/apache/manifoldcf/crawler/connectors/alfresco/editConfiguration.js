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
  if (editconnection.username.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.TheUsernameMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.Server'))");
    editconnection.username.focus();
    return false;
  }
  if (editconnection.password.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.ThePasswordMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.Server'))");
    editconnection.password.focus();
    return false;
  }
  if (editconnection.server.value =="")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.ServerNameMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.Server'))");
    editconnection.server.focus();
    return false;
  }
  else if(editconnection.server.value.indexOf('/')!=-1)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.ServerNameCantContainSlash'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.Server'))");
    editconnection.server.focus();
    return false;
  }
  if (editconnection.port.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.ThePortMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.Server'))");
    editconnection.port.focus();
    return false;
  }
  else if (!isInteger(editconnection.port.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.TheServerPortMustBeAValidInteger'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.Server'))");
    editconnection.port.focus();
    return false;
  }
  if(editconnection.path.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.PathMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.Server'))");
    editconnection.path.focus();
    return false;
  }
  if (editconnection.socketTimeout.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.TheSocketTimeoutMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.Server'))");
    editconnection.socketTimeout.focus();
    return false;
  } 
  else if (!isInteger(editconnection.socketTimeout.value))
  {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.TheSocketTimeoutMustBeAValidInteger'))");
      SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AlfrescoConnector.Server'))");
      editconnection.socketTimeout.focus();
      return false;
  }
  return true;
}
// -->
</script>