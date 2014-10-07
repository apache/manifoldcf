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
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('Alfresco.HostNameMustNotBeEmpty'))");
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
  return true;
}
// -->
</script>
