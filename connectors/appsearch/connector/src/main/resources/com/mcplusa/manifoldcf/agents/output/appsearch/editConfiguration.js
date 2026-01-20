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
  if (editconnection.serverlocation) {
    if (editconnection.serverlocation.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.PleaseSupplyValidAppSearchLocation'))");
      editconnection.serverlocation.focus();
      return false;
    }
  }
  if (editconnection.enginename) {
    if (editconnection.enginename.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.PleaseSupplyValidEngineName'))");
      editconnection.enginename.focus();
      return false;
    } else if (!isEngineNameValid(editconnection.enginename.value)) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.PleaseSupplyValidEngineName'))");
      editconnection.enginename.focus();
      return false;
    }
  }
  if (editconnection.contentattributename) {
    if (editconnection.contentattributename.value == "" && ((editconnection.usemapperattachments_checkbox.value == "true" && editconnection. usemapperattachments.checked == false) || (editconnection.usemapperattachments_checkbox.value != "true" && editconnection.usemapperattachments.value != "true"))) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.ContentAttributeNameRequiredUnlessMapperAttachments'))");
      editconnection.contentattributename.focus();
      return false;
    } else if (!isFieldNameValid(editconnection.contentattributename.value)) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.PleaseSupplyValidFieldName'))");
      editconnection.contentattributename.focus();
      return false;
    }
  }
  if (editconnection.createddateattributename) {
    if (editconnection.createddateattributename.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.ContentAttributeNameRequiredUnlessMapperAttachments'))");
      editconnection.createddateattributename.focus();
      return false;
    } else if (!isFieldNameValid(editconnection.createddateattributename.value)) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.PleaseSupplyValidFieldName'))");
      editconnection.createddateattributename.focus();
      return false;
    }
  }
  if (editconnection.modifieddateattributename) {
    if (editconnection.modifieddateattributename.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.ContentAttributeNameRequiredUnlessMapperAttachments'))");
      editconnection.modifieddateattributename.focus();
      return false;
    } else if (!isFieldNameValid(editconnection.modifieddateattributename.value)) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.PleaseSupplyValidFieldName'))");
      editconnection.modifieddateattributename.focus();
      return false;
    }
  }
  if (editconnection.indexingdateattributename) {
    if (editconnection.indexingdateattributename.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.ContentAttributeNameRequiredUnlessMapperAttachments'))");
      editconnection.indexingdateattributename.focus();
      return false;
    } else if (!isFieldNameValid(editconnection.indexingdateattributename.value)) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.PleaseSupplyValidFieldName'))");
      editconnection.indexingdateattributename.focus();
      return false;
    }
  }
  if (editconnection.mimetypeattributename) {
    if (editconnection.mimetypeattributename.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.ContentAttributeNameRequiredUnlessMapperAttachments'))");
      editconnection.mimetypeattributename.focus();
      return false;
    } else if (!isFieldNameValid(editconnection.mimetypeattributename.value)) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.PleaseSupplyValidFieldName'))");
      editconnection.mimetypeattributename.focus();
      return false;
    }
  }
  return true;
}

function checkConfigForSave() {
  if (editconnection.serverlocation) {
    if (editconnection.serverlocation.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.PleaseSupplyValidAppSearchLocation'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('AppSearchConnector.Server'))");
      editconnection.serverlocation.focus();
      return false;
    }
  }
  if (editconnection.enginename) {
    if (editconnection.enginename.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.PleaseSupplyValidEngineName'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('AppSearchConnector.Parameters'))");
      editconnection.enginename.focus();
      return false;
    }
  }
  if (editconnection.contentattributename) {
    if (editconnection.contentattributename.value == "" || !isFieldNameValid(editconnection.contentattributename.value)) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.PleaseSupplyValidFieldName'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('AppSearchConnector.Parameters'))");
      editconnection.contentattributename.focus();
      return false;
    }
  }
  return true;
}

function ServerKeystoreDeleteCertificate(aliasName)
{
  editconnection.serverkeystore_alias.value = aliasName;
  editconnection.serverkeystore_op.value = "Delete";
  postForm();
}

function ServerKeystoreAddCertificate()
{
  if (editconnection.serverkeystore_certificate.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('AppSearchConnector.ChooseACertificateFile'))");
    editconnection.serverkeystore_certificate.focus();
  }
  else
  {
    editconnection.serverkeystore_op.value = "Add";
    postForm();
  }
}

function isFieldNameValid(value) {
  if (!value) return false;
  var specialChar = /\W/
  var isLowerCase = value === value.toLowerCase();
  var hasValidCharacters = !specialChar.test(value)

  return isLowerCase && hasValidCharacters;
}

function isEngineNameValid(value) {
  if (!value) return false;
  // it check if the string contains only numbers, lowercase letters and hyphens
  return /^[0-9a-z\\-]+$/.test(value);
}

//-->
</script>
