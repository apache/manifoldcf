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
function ShpDeleteCertificate(aliasName)
{
  editconnection.shpkeystorealias.value = aliasName;
  editconnection.configop.value = "Delete";
  postForm();
}

function ShpAddCertificate()
{
  if (editconnection.shpcertificate.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.ChooseACertificateFile'))");
    editconnection.shpcertificate.focus();
  }
  else
  {
    editconnection.configop.value = "Add";
    postForm();
  }
}

function checkConfig()
{
  if (editconnection.serverPort.value != "" && !isInteger(editconnection.serverPort.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSupplyAValidNumber'))");
    editconnection.serverPort.focus();
    return false;
  }
  if (editconnection.proxyport.value != "" && !isInteger(editconnection.proxyport.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSupplyAValidNumber'))");
    editconnection.proxyport.focus();
    return false;
  }
  if (editconnection.serverName.value.indexOf("/") >= 0)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSpecifyAnyServerPathInformation'))");
    editconnection.serverName.focus();
    return false;
  }
  var svrloc = editconnection.serverLocation.value;
  if (svrloc != "" && svrloc.charAt(0) != "/")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.SitePathMustBeginWithWCharacter'))");
    editconnection.serverLocation.focus();
    return false;
  }
  if (svrloc != "" && svrloc.charAt(svrloc.length - 1) == "/")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.SitePathCannotEndWithACharacter'))");
    editconnection.serverLocation.focus();
    return false;
  }
  if (editconnection.serverUserName.value != "" && editconnection.serverUserName.value.indexOf("\\") <= 0)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.AValidSharePointUserNameHasTheForm'))");
    editconnection.serverUserName.focus();
    return false;
  }
  return true;
}

function checkConfigForSave() 
{
  if (editconnection.serverName.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseFillInASharePointServerName'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.Server'))");
    editconnection.serverName.focus();
    return false;
  }
  if (editconnection.serverName.value.indexOf("/") >= 0)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSpecifyAnyServerPathInformationInTheSitePathField'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.Server'))");
    editconnection.serverName.focus();
    return false;
  }
  var svrloc = editconnection.serverLocation.value;
  if (svrloc != "" && svrloc.charAt(0) != "/")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.SitePathMustBeginWithWCharacter'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.Server'))");
    editconnection.serverLocation.focus();
    return false;
  }
  if (svrloc != "" && svrloc.charAt(svrloc.length - 1) == "/")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.SitePathCannotEndWithACharacter'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.Server'))");
    editconnection.serverLocation.focus();
    return false;
  }
  if (editconnection.serverPort.value != "" && !isInteger(editconnection.serverPort.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.PleaseSupplyASharePointPortNumber'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.Server'))");
    editconnection.serverPort.focus();
    return false;
  }
  if (editconnection.serverUserName.value != "" && editconnection.serverUserName.value.indexOf("\\") <= 0)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.TheConnectionRequiresAValidSharePointUserName'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointRepository.Server'))");
    editconnection.serverUserName.focus();
    return false;
  }
  return true;
}

//-->
</script>

