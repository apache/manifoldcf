
<script type="text/javascript">
<!--
function checkConfig()
{
  if (editconnection.confluence_port.value != "" && !isInteger(editconnection.confluence_port.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceAuthorityConnector.ConfPortMustBeAnInteger'))");
    editconnection.confluence_port.focus();
    return false;
  }

  if (editconnection.confluence_host.value != "" && editconnection.confluence_host.value.indexOf("/") != -1)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceAuthorityConnector.HostMustNotIncludeSlash'))");
    editconnection.confluence_host.focus();
    return false;
  }

  if (editconnection.confluence_path.value != "" && !(editconnection.confluence_path.value.indexOf("/") == 0))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceAuthorityConnector.PathMustBeginWithASlash'))");
    editconnection.confluence_path.focus();
    return false;
  }

  return true;
}
 
function checkConfigForSave()
{
    
  if (editconnection.confluence_host.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceAuthorityConnector.HostMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceAuthorityConnector.Server'))");
    editconnection.confluence_host.focus();
    return false;
  }
  
  if (editconnection.confluence_host.value != "" && editconnection.confluence_host.value.indexOf("/") != -1)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceAuthorityConnector.HostMustNotIncludeSlash'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceAuthorityConnector.Server'))");
    editconnection.confluence_host.focus();
    return false;
  }

  if (editconnection.confluence_port.value != "" && !isInteger(editconnection.confluence_port.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceAuthorityConnector.PortMustBeAnInteger'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceAuthorityConnector.Server'))");
    editconnection.confluence_port.focus();
    return false;
  }

  if (editconnection.confluence_path.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceAuthorityConnector.PathMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceAuthorityConnector.Server'))");
    editconnection.confluence_path.focus();
    return false;
  }
  
  if (editconnection.confluence_path.value != "" && !(editconnection.confluence_path.value.indexOf("/") == 0))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceAuthorityConnector.PathMustBeginWithASlash'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceAuthorityConnector.Server'))");
    editconnection.confluence_path.focus();
    return false;
  }
  
  return true;
}
//-->
</script>
