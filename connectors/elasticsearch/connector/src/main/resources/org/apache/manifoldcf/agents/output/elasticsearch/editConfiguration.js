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
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.PleaseSupplyValidElasticSearchLocation'))");
      editconnection.serverlocation.focus();
      return false;
    }
  }
  if (editconnection.indexname) {
    if (editconnection.indexname.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.PleaseSupplyValidIndexName'))");
      editconnection.indexname.focus();
      return false;
    }
  }
  if (editconnection.indextype) {
    if (editconnection.indextype.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.PleaseSupplyValidIndexType'))");
      editconnection.indextype.focus();
      return false;
    }
  }
  if (editconnection.pipelinename) {
    if (editconnection.pipelinename.value == "" && !((editconnection.useingestattachment_checkbox.value == "true" && editconnection.useingestattachment.checked == false) || (editconnection.useingestattachment_checkbox.value != "true" && editconnection.useingestattachment.value != "true"))) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.PipelineNameRequiredIfIngestAttachment'))");
      editconnection.pipelinename.focus();
      return false;
    }
  }
  if (editconnection.contentattributename) {
    if (editconnection.contentattributename.value == "" && ((editconnection.usemapperattachments_checkbox.value == "true" && editconnection. usemapperattachments.checked == false) || (editconnection.usemapperattachments_checkbox.value != "true" && editconnection.usemapperattachments.value != "true"))) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.ContentAttributeNameRequiredUnlessMapperAttachments'))");
      editconnection.contentattributename.focus();
      return false;
    }
  }
  if (editconnection.useingestattachment && editconnection.usemapperattachments) {
    if (!((editconnection.useingestattachment_checkbox.value == "true" && editconnection.useingestattachment.checked == false) || (editconnection.useingestattachment_checkbox.value != "true" && editconnection.useingestattachment.value != "true")) && !((editconnection.usemapperattachments_checkbox.value == "true" && editconnection.usemapperattachments.checked == false) || (editconnection.usemapperattachments_checkbox.value != "true" && editconnection.usemapperattachments.value != "true"))) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.CannotUseBothOfIngestAttachmentAndMapperAttachments'))");
      editconnection.useingestattachment.focus();
      return false;
    }
  }
  if (editconnection.elasticsearch_socket_timeout.value == "" || !isInteger(editconnection.elasticsearch_socket_timeout.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.SocketTimeoutMustBeAnInteger'))");
    editconnection.elasticsearch_socket_timeout.focus();
    return false;
  }
  if (editconnection.elasticsearch_connection_timeout.value == "" || !isInteger(editconnection.elasticsearch_connection_timeout.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.ConnectionTimeoutMustBeAnInteger'))");
    editconnection.elasticsearch_connection_timeout.focus();
    return false;
  }
  return true;
}

function checkConfigForSave() {
  if (editconnection.serverlocation) {
    if (editconnection.serverlocation.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.PleaseSupplyValidElasticSearchLocation'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('ElasticSearchConnector.Server'))");
      editconnection.serverlocation.focus();
      return false;
    }
  }
  if (editconnection.indexname) {
    if (editconnection.indexname.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.PleaseSupplyValidIndexName'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('ElasticSearchConnector.Parameters'))");
      editconnection.indexname.focus();
      return false;
    }
  }
  if (editconnection.indextype) {
    if (editconnection.indextype.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.PleaseSupplyValidIndexType'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('ElasticSearchConnector.Parameters'))");
      editconnection.indextype.focus();
      return false;
    }
  }
  if (editconnection.pipelinename) {
    if (editconnection.pipelinename.value == "" && !((editconnection.useingestattachment_checkbox.value == "true" && editconnection.useingestattachment.checked == false) || (editconnection.useingestattachment_checkbox.value != "true" && editconnection.useingestattachment.value != "true"))) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.PipelineNameRequiredIfIngestAttachment'))");
      editconnection.pipelinename.focus();
      return false;
    }
  }
  if (editconnection.contentattributename) {
    if (editconnection.contentattributename.value == "" && ((editconnection.usemapperattachments_checkbox.value == "true" && editconnection. usemapperattachments.checked == false) || (editconnection.usemapperattachments_checkbox.value != "true" && editconnection.usemapperattachments.value != "true"))) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.ContentAttributeNameRequiredUnlessMapperAttachments'))");
      editconnection.contentattributename.focus();
      return false;
    }
  }
  if (editconnection.useingestattachment && editconnection.usemapperattachments) {
    if (!((editconnection.useingestattachment_checkbox.value == "true" && editconnection.useingestattachment.checked == false) || (editconnection.useingestattachment_checkbox.value != "true" && editconnection.useingestattachment.value != "true")) && !((editconnection.usemapperattachments_checkbox.value == "true" && editconnection.usemapperattachments.checked == false) || (editconnection.usemapperattachments_checkbox.value != "true" && editconnection.usemapperattachments.value != "true"))) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.CannotUseBothOfIngestAttachmentAndMapperAttachments'))");
      editconnection.useingestattachment.focus();
      return false;
    }
  }
  if (editconnection.elasticsearch_socket_timeout.value == "" || !isInteger(editconnection.elasticsearch_socket_timeout.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.SocketTimeoutMustBeAnInteger'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.Server'))");
    editconnection.elasticsearch_socket_timeout.focus();
    return false;
  }
  if (editconnection.elasticsearch_connection_timeout.value == "" || !isInteger(editconnection.elasticsearch_connection_timeout.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.ConnectionTimeoutMustBeAnInteger'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.Server'))");
    editconnection.elasticsearch_connection_timeout.focus();
    return false;
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
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ElasticSearchConnector.ChooseACertificateFile'))");
    editconnection.serverkeystore_certificate.focus();
  }
  else
  {
    editconnection.serverkeystore_op.value = "Add";
    postForm();
  }
}

//-->
</script>
