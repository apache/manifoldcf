<%@include file="../../adminDefaults.jsp"%>

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
// This file is included by every place that the specification information for the memex connector
// needs to be edited.  When it is called, the DocumentSpecification object is placed in the thread context
// under the name "DocumentSpecification".  The IRepositoryConnection object is also in the thread context,
// under the name "RepositoryConnection".
// The coder can presume that this jsp is executed within a body section, and within a form.
DocumentSpecification ds = (DocumentSpecification) threadContext.get("DocumentSpecification");
IRepositoryConnection repositoryConnection = (IRepositoryConnection) threadContext.get("RepositoryConnection");
String scheduleCount = (String)threadContext.get("ScheduleCount");
String tabName = (String)threadContext.get("TabName");

if (ds == null)
	out.println("Hey!  No document specification came in!!!");
if (repositoryConnection == null)
	out.println("No repository connection!!!");
if (tabName == null)
	out.println("No tab name!");

int i;
int k;

// Entities tab

// Always build a hash map containing all the currently selected entities, primary fields, and corresponding metafields first
// Primary fields are ordered.  This map is keyed by entity name, and has an arraylist of primary fields as contents.
HashMap entityMap = new HashMap();
// Metadata fields are not ordered.  This map is keyed by entity name, and contains a hashmap of metadata field names.
HashMap entityMetadataMap = new HashMap();
// Finally, a map describing *all* the fields that have been selected for one purpose or another
HashMap overallEntityFieldMap = new HashMap();
// Descriptions are used to make the 'view' look interpretable
HashMap descriptionMap = new HashMap();
i = 0;
while (i < ds.getChildCount())
{
	SpecificationNode sn = ds.getChild(i++);
	if (sn.getType().equals(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_NODE_ENTITY))
	{
		String entityName = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_NAME);
		String entityDescription = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_DESCRIPTION);
		if (entityDescription == null)
			entityDescription = entityName;

		HashMap attrMap = new HashMap();
		ArrayList primaryList = new ArrayList();
		HashMap overallMap = new HashMap();
		// Go through the children and look for metafield records
		int kk = 0;
		while (kk < sn.getChildCount())
		{
			SpecificationNode dsn = sn.getChild(kk++);
			if (dsn.getType().equals(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_NODE_PRIMARYFIELD))
			{
				String fieldName = dsn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_NAME);
				primaryList.add(fieldName);
				overallMap.put(fieldName,fieldName);
			}
			else if (dsn.getType().equals(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_NODE_METAFIELD))
			{
				String fieldName = dsn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_NAME);
				attrMap.put(fieldName,fieldName);
				overallMap.put(fieldName,fieldName);
			}
		}
		
		entityMap.put(entityName,primaryList);
		entityMetadataMap.put(entityName,attrMap);
		overallEntityFieldMap.put(entityName,overallMap);
		descriptionMap.put(entityName,entityDescription);
	}
}

if (tabName.equals("Entities"))
{
%>
	<table class="formtable">
		<tr class="formheaderrow">
			<td class="formcolumnheader"><nobr>Entity name</nobr></td>
			<td class="formcolumnheader"><nobr>Tagged fields</nobr></td>
			<td class="formcolumnheader"></td>
			<td class="formcolumnheader"><nobr>Available fields</nobr></td>
			<td class="formcolumnheader"></td>
			<td class="formcolumnheader"><nobr>Metadata fields</nobr></td>
		</tr>

<%
	// Need to catch a potential license exception here
	try
	{
		// Now, go through the list of entities from the connector, and preselect those that are selected
		IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext, repositoryConnection.getClassName(), repositoryConnection.getConfigParams(),
			repositoryConnection.getMaxConnections());
		// We'll need to release the connection without fail
		try
		{
			org.apache.lcf.crawler.connectors.memex.MemexConnector memexConnector = (org.apache.lcf.crawler.connectors.memex.MemexConnector) connector;
			org.apache.lcf.crawler.connectors.memex.NameDescription[] entityTypes = memexConnector.listEntityTypes();
%>
		<input type="hidden" name="entitytypecount" value="<%=Integer.toString(entityTypes.length)%>"/>
<%
			int ii = 0;
			while (ii < entityTypes.length)
			{
				org.apache.lcf.crawler.connectors.memex.NameDescription entityType = entityTypes[ii];
				String entityPrefix = entityType.getSymbolicName();
				String entityDisplayName = entityType.getDisplayName();
				ArrayList primaryFields = (ArrayList)entityMap.get(entityPrefix);
				HashMap attrMap = (HashMap)entityMetadataMap.get(entityPrefix);
				HashMap overallMap = (HashMap)overallEntityFieldMap.get(entityPrefix);
				
				// For this entity type, grab a complete list of metadata fields
				String[] legalFields = memexConnector.listFieldNames(entityPrefix);
%>
		<tr class='<%=((ii % 2)==0)?"evenformrow":"oddformrow"%>'>
			<td class="formcolumncell">
				<input type="hidden" name='<%="entitytype_"+Integer.toString(ii)%>' value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(entityPrefix)%>"/>
				<input type="hidden" name='<%="entitydesc_"+Integer.toString(ii)%>' value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(entityDisplayName)%>"/>
				<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(entityDisplayName)%>:</nobr>
			</td>
			<td class="formcolumncell">
				<select name='<%="primaryfields_"+Integer.toString(ii)%>' size="5" multiple="true">
					<option value="">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</option>
<%
				int jj;
				if (primaryFields != null)
				{
					jj = 0;
					while (jj < primaryFields.size())
					{
						String primaryField = (String)primaryFields.get(jj++);
%>
					<option value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(primaryField)%>"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(primaryField)%></option>
<%
					}
				}
%>
				</select>
			</td>
			<td class="formcolumncell">
				<input type="button" value="&lt;--" alt='<%="Add "+Integer.toString(ii)+" to tagged fields"%>' onclick='<%="javascript:addToPrimary("+Integer.toString(ii)+");"%>'/><br/>
				<input type="button" value="--&gt;" alt='<%="Move "+Integer.toString(ii)+" from tagged fields"%>' onclick='<%="javascript:moveFromPrimary("+Integer.toString(ii)+");"%>'/><br/>
			</td>
			<td class="formcolumncell">
				<select name='<%="availablefields_"+Integer.toString(ii)%>' size="5" multiple="true">
					<option value="">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</option>
<%
				jj = 0;
				while (jj < legalFields.length)
				{
					String legalFieldValue = legalFields[jj++];
					if (overallMap == null || overallMap.get(legalFieldValue) == null)
					{
%>
					<option value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(legalFieldValue)%>"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(legalFieldValue)%></option>
<%
					}
				}
%>
				</select>
			</td>
			<td class="formcolumncell">
				<input type="button" value="&lt;--" alt='<%="Move "+Integer.toString(ii)+" from metadata fields"%>' onclick='<%="javascript:moveFromMetadata("+Integer.toString(ii)+");"%>'/><br/>
				<input type="button" value="--&gt;" alt='<%="Move "+Integer.toString(ii)+" to metadata fields"%>' onclick='<%="javascript:moveToMetadata("+Integer.toString(ii)+");"%>'/><br/>
			</td>
			<td class="formcolumncell">
				<select name='<%="metadatafields_"+Integer.toString(ii)%>' size="5" multiple="true">
					<option value="">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</option>
<%
				if (attrMap != null)
				{
					jj = 0;
					while (jj < legalFields.length)
					{
						String legalFieldValue = legalFields[jj++];
						if (attrMap.get(legalFieldValue) != null)
						{
%>
					<option value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(legalFieldValue)%>"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(legalFieldValue)%></option>
<%
						}
					}
				}
%>
				</select>
			</td>
		</tr>
<%
				ii++;
			}
		}
		finally
		{
			RepositoryConnectorFactory.release(connector);
		}
	}
	catch (MetacartaException e)
	{
%>
		<tr>
			<td class="message" colspan="6">
				<%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%>
			</td>
		</tr>
<%
	}
	catch (ServiceInterruption e)
	{
%>
		<tr>
			<td class="message" colspan="6">
				<nobr>Service interruption - check your repository connection</nobr>
			</td>
		</tr>
<%
	}
%>
	</table>
<%
  }
  else
  {
	// Do the hiddens for the Entities tab
%>
	<input type="hidden" name="entitytypecount" value="<%=Integer.toString(entityMap.size())%>"/>
<%
	Iterator iter = entityMap.keySet().iterator();
	int ii = 0;
	while (iter.hasNext())
	{
		String entityType = (String)iter.next();
		String displayName = (String)descriptionMap.get(entityType);
		ArrayList primaryFields = (ArrayList)entityMap.get(entityType);
		HashMap attrMap = (HashMap)entityMetadataMap.get(entityType);
%>
	<input type="hidden" name='<%="entitytype_"+Integer.toString(ii)%>' value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(entityType)%>"/>
	<input type="hidden" name='<%="entitydesc_"+Integer.toString(ii)%>' value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(displayName)%>"/>
<%
		int jj = 0;
		while (jj < primaryFields.size())
		{
			String primaryField = (String)primaryFields.get(jj++);
%>
	<input type="hidden" name='<%="primaryfields_"+Integer.toString(ii)%>' value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(primaryField)%>"/>
<%
		}
		Iterator iter2 = attrMap.keySet().iterator();
		while (iter2.hasNext())
		{
			String attrName = (String)iter2.next();
%>
	<input type="hidden" name='<%="metadatafields_"+Integer.toString(ii)%>' value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(attrName)%>"/>
<%
		}
		ii++;
	}
}

// Record Criteria tab

// This tab displays a sequence of rules.  Rules are constructed from (in order) virtual server, entity, and a (fieldname,operation,fieldvalue) tuple

if (tabName.equals("Record Criteria"))
{
%>
	<table class="displaytable">
		<tr><td class="separator" colspan="2"><hr/></td></tr>
		<tr>
			<td class="description"><nobr>Record inclusion rules:</nobr></td>
			<td class="boxcell">
				<table class="formtable">
					<tr class="formheaderrow">
						<td class="formcolumnheader"></td>
						<td class="formcolumnheader"><nobr>Virtual Server</nobr></td>
						<td class="formcolumnheader"><nobr>Entity Name</nobr></td>
						<td class="formcolumnheader"><nobr>Match Criteria</nobr></td>
					</tr>
<%
	// Loop through the existing rules
	int q = 0;
	int l = 0;
	while (q < ds.getChildCount())
	{
	    SpecificationNode sn = ds.getChild(q++);
	    if (sn.getType().equals(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_NODE_SPECIFICATIONRULE))
	    {
		// Grab the appropriate rule data
		String virtualServer = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_VIRTUALSERVER);
		if (virtualServer == null)
			virtualServer = "";
		String entityPrefix = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_ENTITY);
		if (entityPrefix == null)
			entityPrefix = "";
		String entityDescription = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_DESCRIPTION);
		if (entityDescription == null)
			entityDescription = entityPrefix;
		String fieldName = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_FIELDNAME);
		if (fieldName == null)
			fieldName = "";
		String operation = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_OPERATION);
		if (operation == null)
			operation = "";
		String fieldValue = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_FIELDVALUE);
		if (fieldValue == null)
			fieldValue = "";

		String pathDescription = "_"+Integer.toString(l);
		String pathOpName = "specop"+pathDescription;

%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr>
							<a name='<%="rule_"+Integer.toString(l)%>'/>
							<input type="hidden" name='<%=pathOpName%>' value=""/>
							<input type="hidden" name='<%="virtualserver"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(virtualServer)%>'/>
							<input type="hidden" name='<%="entityprefix"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(entityPrefix)%>'/>
							<input type="hidden" name='<%="entitydescription"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(entityDescription)%>'/>
							<input type="hidden" name='<%="fieldname"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(fieldName)%>'/>
							<input type="hidden" name='<%="operation"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(operation)%>'/>
							<input type="hidden" name='<%="fieldvalue"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(fieldValue)%>'/>
							<input type="button" value="Delete" onClick='<%="Javascript:SpecDeleteRule("+Integer.toString(l)+")"%>' alt='<%="Delete rule #"+Integer.toString(l)%>'/></nobr>
						</td>
						<td class="formcolumncell">
							<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape((virtualServer.length()==0)?"(All)":virtualServer)%></nobr>
						</td>
						<td class="formcolumncell">
							<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape((entityPrefix.length()==0)?"(All)":(entityDescription==null)?entityPrefix:entityDescription)%></nobr>
						</td>
						<td class="formcolumncell">
							<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape((fieldName.length()==0)?"(All)":(fieldName+" "+operation+" "+fieldValue))%></nobr>
						</td>
					</tr>
<%
		l++;
	    }
	}
	if (l == 0)
	{
%>
					<tr class="formrow"><td colspan="4" class="formmessage"><nobr>No records currently included</nobr></td></tr>
<%
	}

	// Now, present the rule building area
	
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr>
							<a name='<%="rule_"+Integer.toString(l)%>'/>
							<input type="hidden" name="specop" value=""/>
							<input type="hidden" name="specrulecount" value='<%=Integer.toString(l)%>'/>
							<input type="button" value="Add New Rule" onClick='<%="Javascript:SpecAddRule("+Integer.toString(l)+")"%>' alt="Add rule"/></nobr>
						</td>
						<td class="formcolumncell" colspan="3"></td>
					</tr>
					<tr class="formrow"><td colspan="4" class="formseparator"><hr/></td></tr>
					<tr class="formrow">
						<td class="formcolumncell"><nobr>New rule:
							<a name="rule"/>
<%
	// These represent the state of the rule building widget, which is maintained in the current thread context from the post element
	String ruleVirtualServer = (String)threadContext.get("rulevirtualserver");
	if (ruleVirtualServer == null)
		ruleVirtualServer = "";
	String ruleEntityPrefix = (String)threadContext.get("ruleentityprefix");
	if (ruleEntityPrefix == null)
		ruleEntityPrefix = "";
	String ruleEntityDescription = (String)threadContext.get("ruleentitydescription");
	if (ruleEntityDescription == null)
		ruleEntityDescription = ruleEntityPrefix;
	String ruleFieldName = (String)threadContext.get("rulefieldname");
	if (ruleFieldName == null)
		ruleFieldName = "";
	String ruleOperation = (String)threadContext.get("ruleoperation");
	if (ruleOperation == null)
		ruleOperation = "";
	String ruleFieldValue = (String)threadContext.get("rulefieldvalue");
	if (ruleFieldValue == null)
		ruleFieldValue = "";

%>
							<input type="hidden" name="rulevirtualserver" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(ruleVirtualServer)%>'/>
							<input type="hidden" name="ruleentityprefix" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(ruleEntityPrefix)%>'/>
							<input type="hidden" name="ruleentitydescription" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(ruleEntityDescription)%>'/>
							<input type="hidden" name="rulefieldname" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(ruleFieldName)%>'/>
							<input type="hidden" name="ruleoperation" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(ruleOperation)%>'/>
							<input type="hidden" name="rulefieldvalue" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(ruleFieldValue)%>'/>
							</nobr>
							<br/>
							<nobr>
							<input type="button" value="Reset Path" onClick='Javascript:SpecRuleReset()' alt="Reset rule values"/>
<%
	if (ruleFieldName.length() > 0)
	{
%>
							<input type="button" value="-" onClick='Javascript:SpecRuleRemoveFieldMatch()' alt="Remove field match"/>
<%
	}
	else
	{
		if (ruleEntityPrefix.length() > 0)
		{
%>
							<input type="button" value="-" onClick='Javascript:SpecRuleRemoveEntity()' alt="Remove entity"/>
<%
		}
		else
		{
			if (ruleVirtualServer.length() > 0)
			{
%>
							<input type="button" value="-" onClick='Javascript:SpecRuleRemoveVirtualServer()' alt="Remove virtual server"/>
<%
			}
		}
	}
%>
							</nobr>
						</td>
						<td class="formcolumncell"><nobr>
<%
	// If we've already selected the virtual server, display it, otherwise create a pulldown for selection
	if (ruleVirtualServer.length() > 0)
	{
		// Display what was chosen
%>
							<%=org.apache.lcf.ui.util.Encoder.bodyEscape(ruleVirtualServer)%>
<%
	}
	else
	{
		// Generate a selection list, and display that
		// Need to catch potential license exception here
		try
		{
			// Now, go through the list and preselect those that are selected
			IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext, repositoryConnection.getClassName(), repositoryConnection.getConfigParams(),
				repositoryConnection.getMaxConnections());
			try
			{
				org.apache.lcf.crawler.connectors.memex.MemexConnector memexConnector = (org.apache.lcf.crawler.connectors.memex.MemexConnector) connector;
				// Fetch the legal virtual servers
				String[] virtualServers = memexConnector.listVirtualServers();
%>
							<input type="button" value="Set Virtual Server" onClick='Javascript:SpecRuleSetVirtualServer()' alt="Set virtual server in rule"/>
							<select name="rulevirtualserverselect" size="5">
								<option value="" selected="true">-- Select Virtual Server --</option>
<%
				int ii = 0;
				while (ii < virtualServers.length)
				{
%>
								<option value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(virtualServers[ii])%>'><%=org.apache.lcf.ui.util.Encoder.bodyEscape(virtualServers[ii])%></option>
<%
					ii++;
				}
%>
							</select>
<%
			}
			finally
			{
				RepositoryConnectorFactory.release(connector);
			}
		}
		catch (MetacartaException e)
		{
%>
							<%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%>
<%
		}
		catch (ServiceInterruption e)
		{
%>
							Transient service interruption - <%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%>
<%
		}

	}
%>
							</nobr>
						</td>
						<td class="formcolumncell"><nobr>
<%
	// If we've already selected the entity, display it, otherwise create a pulldown for selection
	if (ruleEntityPrefix.length() > 0)
	{
		// Display what was chosen
%>
							<%=org.apache.lcf.ui.util.Encoder.bodyEscape(ruleEntityDescription)%>
<%
	}
	else
	{
		// It's either too early, or we need to display the available entities
		if (ruleVirtualServer.length() > 0)
		{
			// Generate list of available entities
			// Need to catch potential license exception here
			try
			{
				// Now, go through the list and preselect those that are selected
				IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext, repositoryConnection.getClassName(), repositoryConnection.getConfigParams(),
					repositoryConnection.getMaxConnections());
				try
				{
					org.apache.lcf.crawler.connectors.memex.MemexConnector memexConnector = (org.apache.lcf.crawler.connectors.memex.MemexConnector) connector;
					// Fetch the legal entity prefixes and descriptions
					org.apache.lcf.crawler.connectors.memex.NameDescription[] allowedEntities = memexConnector.listDatabasesForVirtualServer(ruleVirtualServer);
%>
							<input type="button" value="Set Entity" onClick='Javascript:SpecRuleSetEntity()' alt="Set entity in rule"/>
							<select name="ruleentityselect" size="5">
<%
					int ii = 0;
					while (ii < allowedEntities.length)
					{
%>
								<option value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(allowedEntities[ii].getSymbolicName()+":"+allowedEntities[ii].getDisplayName())%>'><%=org.apache.lcf.ui.util.Encoder.bodyEscape(allowedEntities[ii].getDisplayName())%></option>
<%
						ii++;
					}
%>
							</select>
<%
				}
				finally
				{
					RepositoryConnectorFactory.release(connector);
				}
			}
			catch (MetacartaException e)
			{
%>
							<%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%>
<%
			}
			catch (ServiceInterruption e)
			{
%>
							Transient service interruption - <%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%>
<%
			}
		}
		else
		{
			// Display nothing!
		}
	}
%>
							</nobr>
						</td>
						<td class="formcolumncell"><nobr>
<%
	// If we've already selected the criteria, display it, otherwise create a pulldown for selection, and a field value to fill in
	if (ruleFieldName.length() > 0)
	{
		// Display the field criteria
%>
							<%=org.apache.lcf.ui.util.Encoder.bodyEscape(ruleFieldName)%> <%=org.apache.lcf.ui.util.Encoder.bodyEscape(ruleOperation)%> <%=org.apache.lcf.ui.util.Encoder.bodyEscape(ruleFieldValue)%>
<%
	}
	else
	{
		// It's either too early, or we need to display the available field names etc.
		if (ruleEntityPrefix.length() > 0)
		{
			// Generate list
			// Need to catch potential license exception here
			try
			{
				// Now, go through the list and preselect those that are selected
				IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext, repositoryConnection.getClassName(), repositoryConnection.getConfigParams(),
					repositoryConnection.getMaxConnections());
				try
				{
					org.apache.lcf.crawler.connectors.memex.MemexConnector memexConnector = (org.apache.lcf.crawler.connectors.memex.MemexConnector) connector;
					// Fetch the legal field names for this entity
					String[] fieldNames = memexConnector.listMatchableFieldNames(ruleEntityPrefix);
%>
							<input type="button" value="Set Criteria" onClick='Javascript:SpecRuleSetCriteria()' alt="Set criteria in rule"/>
							<select name="rulefieldnameselect" size="5">
								<option value="" selected="true">-- Select Field Name --</option>
<%
					int ii = 0;
					while (ii < fieldNames.length)
					{
%>
								<option value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(fieldNames[ii])%>'><%=org.apache.lcf.ui.util.Encoder.bodyEscape(fieldNames[ii])%></option>
<%
						ii++;
					}
%>
							</select>
							<select name="ruleoperationselect" size="5">
								<option value="" selected="true">-- Select operation --</option>
								<option value="=">equals</option>
								<option value="&lt;">less than</option>
								<option value="&gt;">greater than</option>
								<option value="&lt;=">less than or equal to</option>
								<option value="&gt;=">greater than or equal to</option>
							</select>
							<input type="text" name="rulefieldvalueselect" size="32" value=""/>
<%
				}
				finally
				{
					RepositoryConnectorFactory.release(connector);
				}
			}
			catch (MetacartaException e)
			{
%>
							<%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%>
<%
			}
			catch (ServiceInterruption e)
			{
%>
							Transient service interruption - <%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%>
<%
			}
		}
		else
		{
			// Display nothing!
		}
	}
%>
							</nobr>
						</td>
					</tr>
				</table>
			</td>
		</tr>
	</table>
<%
}
else
{
	// Loop through the existing rules
	int q = 0;
	int l = 0;
	while (q < ds.getChildCount())
	{
	    SpecificationNode sn = ds.getChild(q++);
	    if (sn.getType().equals(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_NODE_SPECIFICATIONRULE))
	    {
		// Grab the appropriate rule data
		String virtualServer = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_VIRTUALSERVER);
		if (virtualServer == null)
			virtualServer = "";
		String entityPrefix = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_ENTITY);
		if (entityPrefix == null)
			entityPrefix = "";
		String entityDescription = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_DESCRIPTION);
		if (entityDescription == null)
			entityDescription = entityPrefix;
		String fieldName = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_FIELDNAME);
		if (fieldName == null)
			fieldName = "";
		String operation = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_OPERATION);
		if (operation == null)
			operation = "";
		String fieldValue = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_FIELDVALUE);
		if (fieldValue == null)
			fieldValue = "";
			
		String pathDescription = "_"+Integer.toString(l);

%>
	<input type="hidden" name='<%="virtualserver"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(virtualServer)%>'/>
	<input type="hidden" name='<%="entityprefix"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(entityPrefix)%>'/>
	<input type="hidden" name='<%="entitydescription"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(entityDescription)%>'/>
	<input type="hidden" name='<%="fieldname"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(fieldName)%>'/>
	<input type="hidden" name='<%="operation"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(operation)%>'/>
	<input type="hidden" name='<%="fieldvalue"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(fieldValue)%>'/>
<%
		l++;
	    }
	}
%>
	<input type="hidden" name="specrulecount" value='<%=Integer.toString(l)%>'/>
<%
}

// Security tab
// Find whether security is on or off
i = 0;
boolean securityOn = true;
while (i < ds.getChildCount())
{
	SpecificationNode sn = ds.getChild(i++);
	if (sn.getType().equals(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_NODE_SECURITY))
	{
		String securityValue = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_VALUE);
		if (securityValue.equals(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_VALUE_OFF))
			securityOn = false;
		else if (securityValue.equals(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_VALUE_ON))
			securityOn = true;
	}
}

if (tabName.equals("Security"))
{
%>
	<table class="displaytable">
	    		<tr><td class="separator" colspan="2"><hr/></td></tr>
			<tr>
			    <td class="description"><nobr>Security:</nobr></td>
			    <td class="value">
				<input type="radio" name="specsecurity" value="on" <%=(securityOn)?"checked=\"true\"":""%> />Enabled&nbsp;
				<input type="radio" name="specsecurity" value="off" <%=(securityOn==false)?"checked=\"true\"":""%> />Disabled
			    </td>
		        </tr>
	    		<tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	// Go through forced ACL
	i = 0;
	k = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_NODE_ACCESS))
		{
			String accessDescription = "_"+Integer.toString(k);
			String accessOpName = "accessop"+accessDescription;
			String token = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_TOKEN);
%>
			<tr>
				<td class="description">
					<input type="hidden" name='<%=accessOpName%>' value=""/>
					<input type="hidden" name='<%="spectoken"+accessDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(token)%>'/>
					<a name='<%="token_"+Integer.toString(k)%>'><input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+accessOpName+"\",\"Delete\",\"token_"+Integer.toString(k)+"\")"%>' alt='<%="Delete token #"+Integer.toString(k)%>'/></a>&nbsp;
				</td>
				<td class="value">
					<%=org.apache.lcf.ui.util.Encoder.bodyEscape(token)%>
				</td>
			</tr>
<%
			k++;
		}
	}
	if (k == 0)
	{
%>
			<tr>
				<td class="message" colspan="2">No access tokens present</td>
			</tr>
<%
	}
%>
	    		<tr><td class="lightseparator" colspan="2"><hr/></td></tr>
			<tr>
				<td class="description">
					<input type="hidden" name="tokencount" value='<%=Integer.toString(k)%>'/>
					<input type="hidden" name="accessop" value=""/>
					<a name='<%="token_"+Integer.toString(k)%>'><input type="button" value="Add" onClick='<%="Javascript:SpecAddToken(\"token_"+Integer.toString(k+1)+"\")"%>' alt="Add access token"/></a>&nbsp;
				</td>
				<td class="value">
					<input type="text" size="30" name="spectoken" value=""/>
				</td>
			</tr>
	</table>
<%
}
else
{
%>
	<input type="hidden" name="specsecurity" value='<%=(securityOn?org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_VALUE_ON:org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_VALUE_OFF)%>'/>
<%
	// Finally, go through forced ACL
	i = 0;
	k = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_NODE_ACCESS))
		{
			String accessDescription = "_"+Integer.toString(k);
			String token = sn.getAttributeValue(org.apache.lcf.crawler.connectors.memex.MemexConnector.SPEC_ATTRIBUTE_TOKEN);
%>
	<input type="hidden" name='<%="spectoken"+accessDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(token)%>'/>
<%
			k++;
		}
	}
%>
	<input type="hidden" name="tokencount" value='<%=Integer.toString(k)%>'/>
<%
}
%>



