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
    function ServerDeleteCertificate(aliasName)
    {
      editconnection.serverkeystorealias.value = aliasName;
      editconnection.serverconfigop.value = "Delete";
      postForm();
    }

    function ServerAddCertificate()
    {
      if (editconnection.servercertificate.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.ChooseACertificateFile'))");
        editconnection.servercertificate.focus();
      }
      else
      {
        editconnection.serverconfigop.value = "Add";
        postForm();
      }
    }

    function checkConfig()
    {
      if (editconnection.serverport.value != "" && !isInteger(editconnection.serverport.value))
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.AValidNumberIsRequired'))");
        editconnection.serverport.focus();
        return false;
      }
      return true;
    }

    function checkConfigForSave()
    {
      if (editconnection.servername.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.EnterALivelinkServerName'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.Server'))");
        editconnection.servername.focus();
        return false;
      }
      if (editconnection.serverport.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.AServerPortNumberIsRequired'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.Server'))");
        editconnection.serverport.focus();
        return false;
      }
      if (editconnection.cachelifetime.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.CacheLifetimeCannotBeNull'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.Cache'))");
        editconnection.cachelifetime.focus();
        return false;
      }
      if (editconnection.cachelifetime.value != "" && !isInteger(editconnection.cachelifetime.value))
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.CacheLifetimeMustBeAnInteger'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.Cache'))");
        editconnection.cachelifetime.focus();
        return false;
      }
      if (editconnection.cachelrusize.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.CacheLRUSizeCannotBeNull'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.Cache'))");
        editconnection.cachelrusize.focus();
        return false;
      }
      if (editconnection.cachelrusize.value != "" && !isInteger(editconnection.cachelrusize.value))
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.CacheLRUSizeMustBeAnInteger'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LivelinkConnector.Cache'))");
        editconnection.cachelrusize.focus();
        return false;
      }
      return true;
    }
    //-->
</script>