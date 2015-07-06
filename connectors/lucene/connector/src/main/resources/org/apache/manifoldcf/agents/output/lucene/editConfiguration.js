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
  if (editconnection.path) {
    if (editconnection.path.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidPath'))");
      editconnection.path.focus();
      return false;
    }
  }
  if (editconnection.charfilters) {
    if (editconnection.charfilters.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidCharfilters'))");
      editconnection.charfilters.focus();
      return false;
    }
  }
  if (editconnection.tokenizers) {
    if (editconnection.tokenizers.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidTokenizers'))");
      editconnection.tokenizers.focus();
      return false;
    }
  }
  if (editconnection.filters) {
    if (editconnection.filters.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidFilters'))");
      editconnection.filters.focus();
      return false;
    }
  }
  if (editconnection.analyzers) {
    if (editconnection.analyzers.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidAnalyzers'))");
      editconnection.analyzers.focus();
      return false;
    }
  }
  if (editconnection.fields) {
    if (editconnection.fields.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidFields'))");
      editconnection.fields.focus();
      return false;
    }
  }
  if (editconnection.idfield) {
    if (editconnection.idfield.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidIdfield'))");
      editconnection.idfield.focus();
      return false;
    }
  }
  if (editconnection.contentfield) {
    if (editconnection.contentfield.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidContentfield'))");
      editconnection.contentfield.focus();
      return false;
    }
  }
  if (editconnection.maximumdocumentlength) {
    if (editconnection.maximumdocumentlength.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidMaximumdocumentlength'))");
      editconnection.maximumdocumentlength.focus();
      return false;
    }
    if (editconnection.maximumdocumentlength.value != "" && !isInteger(editconnection.maximumdocumentlength.value)) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.MaximumDocumentLengthMustBeAnInteger'))");
      editconnection.maximumdocumentlength.focus();
      return false;
    }
  }
  return true;
}

function checkConfigForSave() {
  if (editconnection.path) {
    if (editconnection.path.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidPath'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('LuceneConnector.Parameters'))");
      editconnection.path.focus();
      return false;
    }
  }
  if (editconnection.charfilters) {
    if (editconnection.charfilters.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidCharfilters'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('LuceneConnector.Parameters'))");
      editconnection.charfilters.focus();
      return false;
    }
  }
  if (editconnection.tokenizers) {
    if (editconnection.tokenizers.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidTokenizers'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('LuceneConnector.Parameters'))");
      editconnection.tokenizers.focus();
      return false;
    }
  }
  if (editconnection.filters) {
    if (editconnection.filters.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidFilters'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('LuceneConnector.Parameters'))");
      editconnection.filters.focus();
      return false;
    }
  }
  if (editconnection.analyzers) {
    if (editconnection.analyzers.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidAnalyzers'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('LuceneConnector.Parameters'))");
      editconnection.analyzers.focus();
      return false;
    }
  }
  if (editconnection.fields) {
    if (editconnection.fields.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidFields'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('LuceneConnector.Parameters'))");
      editconnection.fields.focus();
      return false;
    }
  }
  if (editconnection.idfield) {
    if (editconnection.idfield.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidIdfield'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('LuceneConnector.Parameters'))");
      editconnection.idfield.focus();
      return false;
    }
  }
  if (editconnection.contentfield) {
    if (editconnection.contentfield.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidContentfield'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('LuceneConnector.Parameters'))");
      editconnection.contentfield.focus();
      return false;
    }
  }
  if (editconnection.maximumdocumentlength) {
    if (editconnection.maximumdocumentlength.value == "") {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.PleaseSupplyValidMaximumdocumentlength'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('LuceneConnector.Parameters'))");
      editconnection.maximumdocumentlength.focus();
      return false;
    }
    if (editconnection.maximumdocumentlength.value != "" && !isInteger(editconnection.maximumdocumentlength.value)) {
      alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('LuceneConnector.MaximumDocumentLengthMustBeAnInteger'))");
      SelectTab("$Encoder.javascriptBodyEscape($ResourceBundle.getString('LuceneConnector.Parameters'))");
      editconnection.maximumdocumentlength.focus();
      return false;
    }
  }
  return true;
}
//-->
</script>
