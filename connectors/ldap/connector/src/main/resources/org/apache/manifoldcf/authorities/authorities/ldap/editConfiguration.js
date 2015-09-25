<!DOCTYPE html>
<!--
Copyright 2014 The Apache Software Foundation.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
<script type="text/javascript">
    <!--
    function SSLDeleteCertificate(aliasName)
    {
      editconnection.sslkeystorealias.value = aliasName;
      editconnection.sslconfigop.value = "Delete";
      postForm();
    }

    function SSLAddCertificate()
    {
      if (editconnection.sslcertificate.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.ChooseACertificateFile'))");
        editconnection.sslcertificate.focus();
      }
      else
      {
        editconnection.sslconfigop.value = "Add";
        postForm();
      }
    }

    function checkConfig() {
      if (editconnection.ldapServerName.value.indexOf("/") != -1) {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.ServerNameCannotIncludeSlash'))");
        editconnection.ldapServerName.focus();
        return false;
      }
      if (editconnection.ldapServerPort.value != "" && !isInteger(editconnection.ldapServerPort.value)) {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.ServerPortMustBeAnInteger'))");
        editconnection.ldapServerPort.focus();
        return false;
      }
      if (editconnection.ldapServerBase.value.indexOf("/") != -1) {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.ServerBaseCannotIncludeSlash'))");
        editconnection.ldapServerBase.focus();
        return false;
      }
      if (editconnection.ldapUserSearch.value != "" && editconnection.ldapUserSearch.value.indexOf("{0}") == -1) {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.UserSearchMustIncludeSubstitution'))");
        editconnection.ldapUserSearch.focus();
        return false;
      }
      if (editconnection.ldapGroupSearch.value != "" && editconnection.ldapGroupSearch.value.indexOf("{0}") == -1) {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.GroupSearchMustIncludeSubstitution'))");
        editconnection.ldapGroupSearch.focus();
        return false;
      }
      return true;
    }

    function checkConfigForSave() {
      if (editconnection.ldapServerName.value == "") {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.ServerNameCannotBeBlank'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.LDAP'))");
        editconnection.ldapServerName.focus();
        return false;
      }
      if (editconnection.ldapServerPort.value == "") {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.ServerPortCannotBeBlank'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.LDAP'))");
        editconnection.ldapServerPort.focus();
        return false;
      }
      if (editconnection.ldapUserSearch.value == "") {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.UserSearchCannotBeBlank'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.LDAP'))");
        editconnection.ldapUserSearch.focus();
        return false;
      }
      if (editconnection.ldapGroupSearch.value == "") {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.GroupSearchCannotBeBlank'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.LDAP'))");
        editconnection.ldapGroupSearch.focus();
        return false;
      }
      if (editconnection.ldapGroupNameAttr.value == "") {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.GroupNameAttrCannotBeBlank'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.LDAP'))");
        editconnection.ldapGroupNameAttr.focus();
        return false;
      }
      if (editconnection.ldapUserSearch.value != "" && editconnection.ldapUserSearch.value.indexOf("{0}") == -1) {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.UserSearchMustIncludeSubstitution'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.LDAP'))");
        editconnection.ldapUserSearch.focus();
        return false;
      }
      if (editconnection.ldapGroupSearch.value != "" && editconnection.ldapGroupSearch.value.indexOf("{0}") == -1) {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.GroupSearchMustIncludeSubstitution'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.LDAP'))");
        editconnection.ldapGroupSearch.focus();
        return false;
      }
      if (editconnection.ldapServerPort.value != "" && !isInteger(editconnection.ldapServerPort.value)) {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.ServerPortMustBeAnInteger'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.LDAP'))");
        editconnection.ldapServerPort.focus();
        return false;
      }
      if (editconnection.ldapServerName.value.indexOf("/") != -1) {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.ServerNameCannotIncludeSlash'))");
        SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.LDAP'))");
        editconnection.ldapServerName.focus();
        return false;
      }
      if (editconnection.ldapServerBase.value.indexOf("/") != -1) {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.ServerBaseCannotIncludeSlash'))");
        editconnection.ldapServerBase.focus();
        return false;
      }
      return true;
    }
    
    function SpecOp(n, opValue, anchorvalue) {
      eval("editconnection."+n+".value = \""+opValue+"\"");
      postFormSetAnchor(anchorvalue);
    }
    
    function SpecAddToken(anchorvalue) {
      if (editconnection.spectoken.value == "")
      {
        alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LDAP.TypeInToken'))");
        editconnection.spectoken.focus();
        return;
      }
      SpecOp("accessop","Add",anchorvalue);
    }
    //-->
</script>
