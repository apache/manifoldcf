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
  if (editconnection.serverPort.value != "" && !isInteger(editconnection.serverPort.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.PleaseSupplyAValidNumber'))");
    editconnection.serverPort.focus();
    return false;
  }
  if (editconnection.proxyport.value != "" && !isInteger(editconnection.proxyport.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.PleaseSupplyAValidNumber'))");
    editconnection.proxyport.focus();
    return false;
  }
  if (editconnection.serverName.value.indexOf("/") >= 0)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.PleaseSpecifyAnyServerPathInformation'))");
    editconnection.serverName.focus();
    return false;
  }
  var svrloc = editconnection.serverLocation.value;
  if (svrloc != "" && svrloc.charAt(0) != "/")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.SitePathMustBeginWithWCharacter'))");
    editconnection.serverLocation.focus();
    return false;
  }
  if (svrloc != "" && svrloc.charAt(svrloc.length - 1) == "/")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.SitePathCannotEndWithACharacter'))");
    editconnection.serverLocation.focus();
    return false;
  }
  if (editconnection.serverUserName.value != "" && editconnection.serverUserName.value.indexOf("\\") <= 0)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.AValidSharePointUserNameHasTheForm'))");
    editconnection.serverUserName.focus();
    return false;
  }
  return true;
}

function checkConfigForSave()
{
  if (editconnection.cachelifetime.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.CacheLifetimeCannotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.Cache'))");
    editconnection.cachelifetime.focus();
    return false;
  }
  if (editconnection.cachelifetime.value != "" && !isInteger(editconnection.cachelifetime.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.CacheLifetimeMustBeAnInteger'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.Cache'))");
    editconnection.cachelifetime.focus();
    return false;
  }
  if (editconnection.cachelrusize.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.CacheLRUSizeCannotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.Cache'))");
    editconnection.cachelrusize.focus();
    return false;
  }
  if (editconnection.cachelrusize.value != "" && !isInteger(editconnection.cachelrusize.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.CacheLRUSizeMustBeAnInteger'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.Cache'))");
    editconnection.cachelrusize.focus();
    return false;
  }
  if (editconnection.serverName.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.PleaseFillInASharePointServerName'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.Server'))");
    editconnection.serverName.focus();
    return false;
  }
  if (editconnection.serverName.value.indexOf("/") >= 0)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.PleaseSpecifyAnyServerPathInformationInTheSitePathField'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.Server'))");
    editconnection.serverName.focus();
    return false;
  }
  var svrloc = editconnection.serverLocation.value;
  if (svrloc != "" && svrloc.charAt(0) != "/")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.SitePathMustBeginWithWCharacter'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.Server'))");
    editconnection.serverLocation.focus();
    return false;
  }
  if (svrloc != "" && svrloc.charAt(svrloc.length - 1) == "/")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.SitePathCannotEndWithACharacter'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.Server'))");
    editconnection.serverLocation.focus();
    return false;
  }
  if (editconnection.serverPort.value != "" && !isInteger(editconnection.serverPort.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.PleaseSupplyASharePointPortNumber'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.Server'))");
    editconnection.serverPort.focus();
    return false;
  }
  if (editconnection.serverUserName.value != "" && editconnection.serverUserName.value.indexOf("\\") <= 0)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.TheConnectionRequiresAValidSharePointUserName'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.Server'))");
    editconnection.serverUserName.focus();
    return false;
  }
  return true;
}

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
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('SharePointAuthority.ChooseACertificateFile'))");
    editconnection.shpcertificate.focus();
  }
  else
  {
    editconnection.configop.value = "Add";
    postForm();
  }
}

//-->
</script>

