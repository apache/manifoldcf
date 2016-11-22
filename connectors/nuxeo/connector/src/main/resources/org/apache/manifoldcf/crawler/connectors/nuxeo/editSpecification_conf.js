
<script type="text/javascript">

function checkSpecificationForSave(){
	return true;
}

function s${SeqNum}_SpecOp(n, opValue, anchorvalue)
{
  eval("editjob."+n+".value = \""+opValue+"\"");
  postFormSetAnchor(anchorvalue);
}

//Domains
function s${SeqNum}_SpecDeleteDomain(i)
{
	s${SeqNum}_SpecOp("s${SeqNum}_domainop_"+i,"Delete","domain_"+i);
}

function s${SeqNum}_SpecAddDomain(i)
{
  var x = i-1;
  if (editjob["s${SeqNum}_domain"].value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.TypeInADomain'))");
    editjob.s${SeqNum}_domain.focus();
    return;
  }
  s${SeqNum}_SpecOp("s${SeqNum}_domainop","Add","domain_"+i);
}

//Documents
function s${SeqNum}_SpecDeleteDocumentType(i)
{
	s${SeqNum}_SpecOp("s${SeqNum}_documentTypeop_"+i,"Delete","documentType_"+i);
}

function s${SeqNum}_SpecAddDocumentType(i)
{
  var x = i-1;
  if (editjob["s${SeqNum}_documentType"].value == "")
  {
    alert("$Encoder.bodyJavascriptEscape($ResourceBundle.getString('NuxeoRepositoryConnector.TypeInADocumentType'))");
    editjob.s${SeqNum}_documentType.focus();
    return;
  }
  s${SeqNum}_SpecOp("s${SeqNum}_documentTypeop","Add","documentType_"+i);
}

</script>