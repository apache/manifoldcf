<script type="text/javascript">
<!--
function checkConfig()
{
  if (editconnection.nuxeo_port.value != "" && !isInteger(editconnection.nuxeo_port.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.PortMustBeAnInteger'))");
    editconnection.nuxeo_port.focus();
    return false;
  }

  if (editconnection.nuxeo_host.value != "" && editconnection.nuxeo_host.value.indexOf("/") != -1)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.HostMustNotIncludeSlash'))");
    editconnection.nuxeo_host.focus();
    return false;
  }

 if (editconnection.nuxeo_path.value != "" &&
 !(editconnection.nuxeo_path.value.indexOf("/") == 0))
 {
 alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.PathMustBeginWithASlash'))");
 editconnection.nuxeo_path.focus();
 return false;
 }

  return true;
}
 
function checkConfigForSave()
{
    
  if (editconnection.nuxeo_host.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.HostMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.Server'))");
    editconnection.nuxeo_host.focus();
    return false;
  }
  
  if (editconnection.nuxeo_host.value != "" && editconnection.nuxeo_host.value.indexOf("/") != -1)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.HostMustNotIncludeSlash'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.Server'))");
    editconnection.nuxeo_host.focus();
    return false;
  }

  if (editconnection.nuxeo_port.value != "" && !isInteger(editconnection.nuxeo_port.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.PortMustBeAnInteger'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.Server'))");
    editconnection.nuxeo_port.focus();
    return false;
  }
  
  if (editconnection.nuxeo_username.value != "" && editconnection.nuxeo_password.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.PasswordMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.Server'))");
    editconnection.nuxeo_password.focus();
    return false;
  }

 if (editconnection.nuxeo_path.value == "")
 {
 alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.PathMustNotBeNull'))");
 SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.Server'))");
 editconnection.nuxeo_path.focus();
 return false;
 }
  
 if (editconnection.nuxeo_path.value != "" &&
 !(editconnection.nuxeo_path.value.indexOf("/") == 0))
 {
 alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.PathMustBeginWithASlash'))");
 SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoAuthorityConnector.Server'))");
 editconnection.nuxeo_path.focus();
 return false;
 }
  
  return true;
}
// -->
</script>
