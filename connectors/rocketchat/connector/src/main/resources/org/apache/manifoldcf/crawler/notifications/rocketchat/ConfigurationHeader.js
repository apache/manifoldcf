<!--
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements. See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License. You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->

<script type="text/javascript">
function checkConfig()
{
  if (editconnection.proxyPort.value != "" && !isInteger(editconnection.proxyPort.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('RocketChatConnector.ProxyPortMustBeAnInteger'))");
    editconnection.proxyPort.focus();
    return false;
  }
  return true;
}

function checkConfigForSave()
{
  if (editconnection.serverUrl.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('RocketChatConnector.EnterAServerUrl'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('RocketChatConnector.RestAPI'))");
    editconnection.serverUrl.focus();
    return false;
  }
  if (editconnection.user.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('RocketChatConnector.EnterAUser'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('RocketChatConnector.RestAPI'))");
    editconnection.user.focus();
    return false;
  }
  if (editconnection.password.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('RocketChatConnector.EnterAPasswordUrl'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('RocketChatConnector.RestAPI'))");
    editconnection.password.focus();
    return false;
  }
  if (editconnection.proxyHost.value != "" && editconnection.proxyPort.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('RocketChatConnector.ProxyPortMustBeGivenWithHost'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('RocketChatConnector.RestAPI'))");
    editconnection.proxyPort.focus();
    return false;
  }
  if (editconnection.proxyPort.value != "" && !isInteger(editconnection.proxyPort.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('RocketChatConnector.ProxyPortMustBeAnInteger'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('RocketChatConnector.RestAPI'))");
    editconnection.proxyPort.focus();
    return false;
  }
  return true;
}

//-->
</script>
