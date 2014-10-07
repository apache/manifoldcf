<script type="text/javascript">
<!--
function checkConfig() {
  return true;
}

function checkConfigForSave() {
  if (editconnection.protocol.value == "") {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.ProtocolMustNotBeEmpty'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.Server'))");
    editconnection.protocol.focus();
    return false;
  }
  if (editconnection.hostname.value == "") {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.HostnameMustNotBeEmpty'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.Server'))");
    editconnection.hostname.focus();
    return false;
  }
  if (editconnection.endpoint.value == "") {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.EndpointMustNotBeEmpty'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.Server'))");
    editconnection.endpoint.focus();
    return false;
  }
  if (editconnection.storeprotocol.value == "") {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.StoreProtocolMustNotBeEmpty'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.Server'))");
    editconnection.storeprotocol.focus();
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
