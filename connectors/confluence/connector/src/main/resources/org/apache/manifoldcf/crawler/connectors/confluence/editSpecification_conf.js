<script type="text/javascript">
<!--
function checkSpecificationForSave()
{
  return true;
}
 
function s${SeqNum}_SpecOp(n, opValue, anchorvalue)
{
  eval("editjob."+n+".value = \""+opValue+"\"");
  postFormSetAnchor(anchorvalue);
}

function s${SeqNum}_SpecDeleteSpace(i)
{
	s${SeqNum}_SpecOp("s${SeqNum}_spaceop_"+i,"Delete","space_"+i);
}

function s${SeqNum}_SpecAddSpace(i)
{
  var x = i-1;
  if (editjob["s${SeqNum}_space"].value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('ConfluenceRepositoryConnector.TypeInASpace'))");
    editjob.s${SeqNum}_space.focus();
    return;
  }
  s${SeqNum}_SpecOp("s${SeqNum}_spaceop","Add","space_"+i);
}

//-->
</script>