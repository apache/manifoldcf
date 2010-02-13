<%@ include file="../../adminDefaults.jsp" %>

<%

/* $Id$ */

/**
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements. See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
%>

<%
	// This file is included in the head section by every place that the specification information for the memex connector
	// needs to be edited.  When it is called, the DocumentSpecification object is placed in the thread context
	// under the name "DocumentSpecification".  The IRepositoryConnection object is also in the thread context,
	// under the name "RepositoryConnection".

	// The coder can presume that this jsp is executed within a head section.  The purpose would be to provide javascript
	// functions needed by the editspec.jsp for this connector.
	//
	// The function checkSpecificationOnSave() is called prior to the save operation, and serves to allow the
	// connector code to check any form data.  This should return a boolean false value if the form should NOT be submitted.

	DocumentSpecification ds = (DocumentSpecification)threadContext.get("DocumentSpecification");
	IRepositoryConnection repositoryConnection = (IRepositoryConnection)threadContext.get("RepositoryConnection");
	String scheduleCount = (String)threadContext.get("ScheduleCount");
	ArrayList tabsArray = (ArrayList)threadContext.get("Tabs");
%>

<%
	if (ds == null)
		out.println("Hey!  No document specification came in!!!");
	if (repositoryConnection == null)
		out.println("No repository connection!!!");
	if (tabsArray == null)
		out.println("No tabs array!");

	tabsArray.add("Record Criteria");
	tabsArray.add("Entities");
	tabsArray.add("Security");
%>

<script type="text/javascript">
<!--

	function selectValues(formelementName)
	{
		// The problem here is that we don't actually know if the form
		// element is a select or a hidden, so first we have to figure that
		// out.
		if (eval(formelementName) && eval(formelementName+".type") == "select-multiple")
		{
			// It's a multiselect, so we need to select all of them,
			// except for the spacer element
			var selectLength = eval(formelementName+".length");
			var i = 1;
			while (i < selectLength)
			{
				eval(formelementName+".options[i].selected = true");
				i = i+1;
			}
		}
	}
	
	function selectAllValues(formelementName)
	{
		if (!editjob.entitytypecount)
			return;
		var elementCount = editjob.entitytypecount.value;
		var i = 0;
		while (i < elementCount)
		{
			selectValues("editjob."+formelementName+"_"+i);
			i = i+1;
		}
	}
	
	function checkSpecification()
	{
		selectAllValues("primaryfields");
		selectAllValues("metadatafields");
		return true;
	}

	function SpecOp(n, opValue, anchorvalue)
	{
		eval("editjob."+n+".value = \""+opValue+"\"");
		postFormSetAnchor(anchorvalue);
	}

	function deleteSelectedOptions(formelementName)
	{
		// Run through the multiselect, and collapse the entries.  Also clear the selection of the first element, if set.
		eval(formelementName+".options[0].selected=false");
		var formLength = eval(formelementName+".length");
		var i = 1;
		var outputPosition = 1;
		while (i < formLength)
		{
			if (eval(formelementName+".options[i].selected") == false)
			{
				// Do the copy
				eval(formelementName+".options[outputPosition]=new Option("+formelementName+".options[i].text,"+formelementName+".options[i].value)");
				outputPosition = outputPosition + 1;
			}
			i = i+1;
		}
		while (outputPosition < formLength)
		{
			eval(formelementName+".options[outputPosition]=null");
			outputPosition = outputPosition + 1;
		}
	}
	
	function addAtEnd(sourceFormelementName,targetFormelementName,message)
	{
		if (eval(sourceFormelementName+".selectedIndex") == -1)
			alert(message);
		else
		{
			// Always add the fields at the end
			var addIndex = eval(targetFormelementName+".length");
			var sourceLength = eval(sourceFormelementName+".length");
			var i = 1;
			while (i < sourceLength)
			{
				if (eval(sourceFormelementName+".options[i].selected"))
				{
					// Copy text and value
					eval(targetFormelementName+".options[addIndex]=new Option("+sourceFormelementName+".options[i].text,"+sourceFormelementName+".options[i].value)");
					addIndex = addIndex + 1;
				}
				i = i + 1;
			}
			deleteSelectedOptions(sourceFormelementName);
		}
	}

	function insertSorted(sourceFormelementName,targetFormelementName,message)
	{
		if (eval(sourceFormelementName+".selectedIndex") == -1)
			alert(message);
		else
		{
			var sourceLength = eval(sourceFormelementName+".length");
			var i = 1;
			while (i < sourceLength)
			{
				if (eval(sourceFormelementName+".options[i].selected"))
				{
					// Find the right insert point in the target
					var sourceText = eval(sourceFormelementName+".options[i].text");
					var sourceValue = eval(sourceFormelementName+".options[i].value");
					var targetLength = eval(targetFormelementName+".length");
					var j = 1;
					while (j < targetLength)
					{
						var targetText = eval(targetFormelementName+".options[j].text");
						if (targetText > sourceText)
							break;
						j = j+1;
					}
					// Copy text and value, shuffling the rest of the element down
					var existObject = new Option(sourceText,sourceValue);
					while (j < targetLength)
					{
						var nextObject = eval(targetFormelementName+".options[j]");
						eval(targetFormelementName+".options[j]=existObject");
						existObject = nextObject;
						j = j+1;
					}
					eval(targetFormelementName+".options[j]=existObject");
				}
				i = i+1;
			}
			deleteSelectedOptions(sourceFormelementName);

		}
	}
	
	function addToPrimary(indexValue)
	{
		var sourceFormelementName = "editjob.availablefields_"+indexValue;
		var targetFormelementName = "editjob.primaryfields_"+indexValue;
		addAtEnd(sourceFormelementName,targetFormelementName,"Please select the attribute(s) you wish to append to the tagged field list");
	}
	
	
	function moveFromPrimary(indexValue)
	{
		var sourceFormelementName = "editjob.primaryfields_"+indexValue;
		var targetFormelementName = "editjob.availablefields_"+indexValue;
		insertSorted(sourceFormelementName,targetFormelementName,"Please select the attribute(s) you wish to remove from the tagged field list");
	}
	
	function moveToMetadata(indexValue)
	{
		var sourceFormelementName = "editjob.availablefields_"+indexValue;
		var targetFormelementName = "editjob.metadatafields_"+indexValue;
		insertSorted(sourceFormelementName,targetFormelementName,"Please select the attribute(s) you wish to add to the metadata field list");
	}
	
	function moveFromMetadata(indexValue)
	{
		var sourceFormelementName = "editjob.metadatafields_"+indexValue;
		var targetFormelementName = "editjob.availablefields_"+indexValue;
		insertSorted(sourceFormelementName,targetFormelementName,"Please select the attribute(s) you wish to remove from the metadata field list");
	}

	function SpecAddToken(anchorvalue)
	{
		if (editjob.spectoken.value == "")
		{
			alert("Type in an access token");
			editjob.spectoken.focus();
			return;
		}
		SpecOp("accessop","Add",anchorvalue);
	}

	function SpecDeleteRule(ruleIndex)
	{
		SpecOp("specop_"+ruleIndex,"Delete","rule_"+ruleIndex);
	}
        
	function SpecAddRule(ruleIndex)
	{
		SpecOp("specop","Add","rule_"+ruleIndex);
	}
        
	function SpecRuleReset()
	{
		editjob.rulevirtualserver.value = "";
		editjob.ruleentityprefix.value = "";
		editjob.ruleentitydescription.value = "";
		editjob.rulefieldname.value = "";
		editjob.ruleoperation.value = "";
		editjob.rulefieldvalue.value = "";
		SpecOp("specop","Continue","rule");
	}
        
	function SpecRuleRemoveFieldMatch()
	{
		editjob.rulefieldname.value = "";
		editjob.ruleoperation.value = "";
		editjob.rulefieldvalue.value = "";
		SpecOp("specop","Continue","rule");
	}
        
	function SpecRuleRemoveEntity()
	{
		editjob.ruleentityprefix.value = "";
		editjob.ruleentitydescription.value = "";
		SpecOp("specop","Continue","rule");
	}
        
	function SpecRuleRemoveVirtualServer()
	{
		editjob.rulevirtualserver.value = "";
		SpecOp("specop","Continue","rule");
	}
        
	function SpecRuleSetVirtualServer()
	{
		if (editjob.rulevirtualserverselect.value == "")
		{
			alert("Select a virtual server first.");
			editjob.rulevirtualserverselect.focus();
			return;
		}
		editjob.rulevirtualserver.value = editjob.rulevirtualserverselect.value;
		SpecOp("specop","Continue","rule");
	}
        
	function SpecRuleSetEntity()
	{
		if (editjob.ruleentityselect.value == "")
		{
			alert("Select an entity first.");
			editjob.ruleentityselect.focus();
			return;
		}
		var value = editjob.ruleentityselect.value;
		editjob.ruleentityprefix.value = value.substring(0,value.indexOf(":"));
		editjob.ruleentitydescription.value = value.substring(value.indexOf(":")+1);
		SpecOp("specop","Continue","rule");
	}
        
	function SpecRuleSetCriteria()
	{
		if (editjob.rulefieldnameselect.value == "")
		{
			alert("Select a field name first.");
			editjob.rulefieldnameselect.focus();
			return;
		}
		if (editjob.ruleoperationselect.value == "")
		{
			alert("Select an operation first.");
			editjob.ruleoperationselect.focus();
			return;
		}
		editjob.rulefieldname.value = editjob.rulefieldnameselect.value;
		editjob.ruleoperation.value = editjob.ruleoperationselect.value;
		editjob.rulefieldvalue.value = editjob.rulefieldvalueselect.value;
		SpecOp("specop","Continue","rule");
	}

//-->
</script>

