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
function checkConfig() {
  return true;
}

function checkConfigForSave() {
  if (editconnection.hostname.value == "") {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.HostnameMustNotBeEmpty'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.Server'))");
    editconnection.hostname.focus();
    return false;
  }
  if (editconnection.hostname.value.indexOf(":") != -1 || editconnection.hostname.value.indexOf("/") != -1) {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.HostNameCannotContainColonOrSlash'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.Server'))");
    editconnection.hostname.focus();
    return false;
  }
  if (editconnection.port.value == "") {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.PortMustNotBeEmpty'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.Server'))");
    editconnection.port.focus();
    return false;
  }
  if (!isInteger(editconnection.port.value)) {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.PortMustBeAnInteger'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.Server'))");
    editconnection.port.focus();
    return false;
  }
  if (editconnection.endpoint.value == "") {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.EndpointMustNotBeEmpty'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.Server'))");
    editconnection.endpoint.focus();
    return false;
  }
  if (editconnection.endpoint.value.substring(0,1) != "/") {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.EndpointMustStartWithSlash'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.Server'))");
    editconnection.endpoint.focus();
    return false;
  }
  if (editconnection.storeid.value == "") {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.StoreIDMustNotBeEmpty'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.Server'))");
    editconnection.storeid.focus();
    return false;
  }
  return true;
}
// -->
</script>
