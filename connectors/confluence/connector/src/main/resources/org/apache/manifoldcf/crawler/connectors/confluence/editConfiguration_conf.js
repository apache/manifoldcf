
<script type="text/javascript">
<!--
function checkConfig()
{
  if (editconnection.confluence_port.value != "" && !isInteger(editconnection.confluence_port.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceRepositoryConnector.ConfPortMustBeAnInteger'))");
    editconnection.confluence_port.focus();
    return false;
  }

  if (editconnection.confluence_host.value != "" && editconnection.confluence_host.value.indexOf("/") != -1)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceRepositoryConnector.HostMustNotIncludeSlash'))");
    editconnection.confluence_host.focus();
    return false;
  }

//  if (editconnection.confluence_path.value != "" && !(editconnection.confluence_path.value.indexOf("/") == 0))
//  {
//    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceRepositoryConnector.PathMustBeginWithASlash'))");
//    editconnection.confluence_path.focus();
//    return false;
//  }

  return true;
}
 
function checkConfigForSave()
{
    
  if (editconnection.confluence_host.value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceRepositoryConnector.HostMustNotBeNull'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceRepositoryConnector.Server'))");
    editconnection.confluence_host.focus();
    return false;
  }
  
  if (editconnection.confluence_host.value != "" && editconnection.confluence_host.value.indexOf("/") != -1)
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceRepositoryConnector.HostMustNotIncludeSlash'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceRepositoryConnector.Server'))");
    editconnection.confluence_host.focus();
    return false;
  }

  if (editconnection.confluence_port.value != "" && !isInteger(editconnection.confluence_port.value))
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceRepositoryConnector.PortMustBeAnInteger'))");
    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceRepositoryConnector.Server'))");
    editconnection.confluence_port.focus();
    return false;
  }

//  if (editconnection.confluence_path.value == "")
//  {
//    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceRepositoryConnector.PathMustNotBeNull'))");
//    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceRepositoryConnector.Server'))");
//    editconnection.confluence_path.focus();
//    return false;
//  }
//  
//  if (editconnection.confluence_path.value != "" && !(editconnection.confluence_path.value.indexOf("/") == 0))
//  {
//    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceRepositoryConnector.PathMustBeginWithASlash'))");
//    SelectTab("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceRepositoryConnector.Server'))");
//    editconnection.confluence_path.focus();
//    return false;
//  }
  
  return true;
}
//-->
</script>
