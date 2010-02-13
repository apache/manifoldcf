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
// This file is included by every place that the specification information for the file system connector
// needs to be edited.  When it is called, the DocumentSpecification object is placed in the thread context
// under the name "DocumentSpecification".  The IRepositoryConnection object is also in the thread context,
// under the name "RepositoryConnection".
// The coder can presume that this jsp is executed within a body section, and within a form.
DocumentSpecification ds = (DocumentSpecification) threadContext.get("DocumentSpecification");
IRepositoryConnection repositoryConnection = (IRepositoryConnection) threadContext.get("RepositoryConnection");
String tabName = (String)threadContext.get("TabName");

if (ds == null)
    out.println("Hey!  No document specification came in!!!");
if (repositoryConnection == null)
    out.println("No repository connection!!!");
if (tabName == null)
    out.println("No tab name!");

int i;
Iterator iter;

// "Document Classes" tab
// Look for document classes
HashMap documentClasses = new HashMap();
i = 0;
while (i < ds.getChildCount())
{
	SpecificationNode sn = ds.getChild(i++);
	if (sn.getType().equals(com.metacarta.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_DOCUMENTCLASS))
	{
		String value = sn.getAttributeValue(com.metacarta.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE);
		// Now, scan for metadata etc.
		com.metacarta.crawler.connectors.filenet.DocClassSpec spec = new com.metacarta.crawler.connectors.filenet.DocClassSpec(sn);
		documentClasses.put(value,spec);
	}
}

if (tabName.equals("Document Classes"))
{
%>
<input type="hidden" name="hasdocumentclasses" value="true"/>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	// Fetch the list of valid document classes from the connector
	com.metacarta.crawler.common.filenet.DocumentClassDefinition[] documentClassArray = null;
	HashMap documentClassFields = new HashMap();
	String message = null;
	try
	{
		IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext,
			repositoryConnection.getClassName(),
			repositoryConnection.getConfigParams(),
			repositoryConnection.getMaxConnections());
		try
		{
			com.metacarta.crawler.connectors.filenet.FilenetConnector c = (com.metacarta.crawler.connectors.filenet.FilenetConnector)connector;
			documentClassArray = c.getDocumentClassesDetails();
			int j = 0;
			while (j < documentClassArray.length)
			{
				String documentClass = documentClassArray[j++].getSymbolicName();
				com.metacarta.crawler.common.filenet.MetadataFieldDefinition[] metaFields = c.getDocumentClassMetadataFieldsDetails(documentClass);
				documentClassFields.put(documentClass,metaFields);
			}
		}
		finally
		{
			RepositoryConnectorFactory.release(connector);
		}
	}
	catch (MetacartaException e)
	{
		message = e.getMessage();
	}
	catch (ServiceInterruption e)
	{
		message = "FileNet server temporarily unavailable: "+e.getMessage();
	}

	if (message != null)
	{
%>
	<tr><td class="message" colspan="2"><%=com.metacarta.ui.util.Encoder.bodyEscape(message)%></td></tr>
<%
	}
	else
	{
		i = 0;
		while (i < documentClassArray.length)
		{
			com.metacarta.crawler.common.filenet.DocumentClassDefinition def = documentClassArray[i++];
			String documentClass = def.getSymbolicName();
			String displayName = def.getDisplayName();
			com.metacarta.crawler.connectors.filenet.DocClassSpec spec = (com.metacarta.crawler.connectors.filenet.DocClassSpec)documentClasses.get(documentClass);
%>
	<tr>
		<td class="description">
			<nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(documentClass+" ("+displayName+")")%>:</nobr>
		</td>
		<td class="boxcell">
			<table class="displaytable">
				<tr>
					<td class="description">
						<nobr>Include?</nobr>
					</td>
					<td class="value">
						<nobr><input type="checkbox" name="documentclasses" <%=((spec != null)?"checked=\"true\"":"")%> value="<%=com.metacarta.ui.util.Encoder.attributeEscape(documentClass)%>"></input></nobr>
					</td>
				</tr>
				<tr>
					<td class="description">
						<nobr>Document criteria:</nobr>
					</td>
					<td class="boxcell">
						<table class="displaytable">
<%
			com.metacarta.crawler.common.filenet.MetadataFieldDefinition[] fields = (com.metacarta.crawler.common.filenet.MetadataFieldDefinition[])documentClassFields.get(documentClass);
			String[] fieldArray = new String[fields.length];
			HashMap fieldMap = new HashMap();
			int j = 0;
			while (j < fieldArray.length)
			{
				com.metacarta.crawler.common.filenet.MetadataFieldDefinition field = fields[j];
				fieldArray[j++] = field.getSymbolicName();
				fieldMap.put(field.getSymbolicName(),field.getDisplayName());
			}
			java.util.Arrays.sort(fieldArray);

			int q = 0;
			int matchCount = ((spec==null)?0:spec.getMatchCount());
			while (q < matchCount)
			{
				String matchType = spec.getMatchType(q);
				String matchField = spec.getMatchField(q);
				String matchValue = spec.getMatchValue(q);
				String opName = "matchop_" + com.metacarta.ui.util.Encoder.attributeEscape(documentClass) + "_" +Integer.toString(q);
				String labelName = "match_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q);
%>
							<tr>
								<td class="description">
									<input type="hidden" name='<%=opName%>' value=""/>
									<a name='<%=labelName%>'>
										<input type="button" value="Delete" alt='<%="Delete "+documentClass+" match # "+Integer.toString(q)%>' onClick='<%="Javascript:SpecOp(\""+opName+"\",\"Delete\",\""+labelName+"\")"%>'/>
									</a>
								</td>
								<td class="value">
									<input type="hidden" name='<%="matchfield_" + com.metacarta.ui.util.Encoder.attributeEscape(documentClass) + "_" + Integer.toString(q)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(matchField)%>'/>
									<nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(matchField)%></nobr>
								</td>
								<td class="value">
									<input type="hidden" name='<%="matchtype_" + com.metacarta.ui.util.Encoder.attributeEscape(documentClass) + "_" + Integer.toString(q)%>' value='<%=matchType%>'/>
									<nobr><%=matchType%></nobr>
								</td>
								<td class="value">
									<input type="hidden" name='<%="matchvalue_" + com.metacarta.ui.util.Encoder.attributeEscape(documentClass) + "_" + Integer.toString(q)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(matchValue)%>'/>
									<nobr>'<%=com.metacarta.ui.util.Encoder.bodyEscape(matchValue)%>'</nobr>
								</td>
							</tr>
<%
				q++;
			}
			if (q == 0)
			{
%>
							<tr><td class="message" colspan="4"><nobr>(No criteria specified - all documents will be taken)</nobr></td></tr>
<%
			}
			String addLabelName = "match_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q);
			String addOpName = "matchop_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass);
%>
							<tr><td class="lightseparator" colspan="4"><hr/></td></tr>
							<tr>
								<td class="description">
									<input type="hidden" name='<%="matchcount_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)%>' value='<%=Integer.toString(matchCount)%>'/>
									<input type="hidden" name='<%=addOpName%>' value=""/>
									<a name='<%=addLabelName%>'>
										<input type="button" value="Add" alt='<%="Add match for "+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)%>'
											onClick='<%="Javascript:SpecAddMatch(\""+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)+"\",\"match_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q+1)+"\")"%>'/>
									</a>
								</td>
								<td class="value">
									<select name='<%="matchfield_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)%>' size="5">
<%
			q = 0;
			while (q < fieldArray.length)
			{
				String field = fieldArray[q++];
				String dName = (String)fieldMap.get(field);
%>
										<option value='<%=com.metacarta.ui.util.Encoder.attributeEscape(field)%>'><%=com.metacarta.ui.util.Encoder.bodyEscape(field+" ("+dName+")")%></option>
<%
			}
%>
									</select>
								</td>
								<td class="value">
									<select name='<%="matchtype_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)%>'>
										<option value="=">Equals</option>
										<option value="!=">Not equals</option>
										<option value="LIKE">'Like' (with % wildcards)</option>
									</select>
								</td>
								<td class="value">
									<input name='<%="matchvalue_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)%>' type="text" size="32" value=""/>
								</td>
							</tr>
						</table>
					</td>
				</tr>
				<tr>
					<td class="description">
						<nobr>Ingest all metadata fields?</nobr>
					</td>
					<td class="value">
						<nobr><input type="checkbox" name='<%="allmetadata_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)%>' value="true" <%=((spec != null && spec.getAllMetadata())?"checked=\"\"":"")%>></input></nobr><br/>
					</td>
				</tr>
				<tr>
					<td class="description">
						<nobr>Metadata fields:</nobr>
					</td>
					<td class="value">
						<nobr>
							<select name='<%="metadatafield_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)%>' multiple="true" size="5">
<%
			j = 0;
			while (j < fieldArray.length)
			{
				String field = fieldArray[j++];
				String dName = (String)fieldMap.get(field);
%>
								<option value='<%=com.metacarta.ui.util.Encoder.attributeEscape(field)%>' <%=((spec!=null && spec.getAllMetadata() == false && spec.checkMetadataIncluded(field))?"selected=\"true\"":"")%>><%=com.metacarta.ui.util.Encoder.bodyEscape(field+" ("+dName+")")%></option>
<%
			}
%>
							</select>
						</nobr>

					</td>
				</tr>
			</table>
		</td>
	</tr>
<%
		}
	}
%>
</table>
<%
}
else
{
%>
<input type="hidden" name="hasdocumentclasses" value="true"/>
<%
	iter = documentClasses.keySet().iterator();
	while (iter.hasNext())
	{
		String documentClass = (String)iter.next();
		com.metacarta.crawler.connectors.filenet.DocClassSpec spec = (com.metacarta.crawler.connectors.filenet.DocClassSpec)documentClasses.get(documentClass);
		if (spec.getAllMetadata())
		{
%>
<input type="hidden" name='<%="allmetadata_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)%>' value="true"/>
<%
		}
		else
		{
			String[] metadataFields = spec.getMetadataFields();
			int q = 0;
			while (q < metadataFields.length)
			{
				String field = metadataFields[q++];
%>
<input type="hidden" name='<%="metadatafield_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(field)%>'/>
<%
			}
		}
		
		// Do matches
		int matchCount = spec.getMatchCount();
		int q = 0;
		while (q < matchCount)
		{
			String matchType = spec.getMatchType(q);
			String matchField = spec.getMatchField(q);
			String matchValue = spec.getMatchValue(q);
%>
<input type="hidden" name='<%="matchfield_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(matchField)%>'/>
<input type="hidden" name='<%="matchtype_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q)%>' value='<%=matchType%>'/>
<input type="hidden" name='<%="matchvalue_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)+"_"+Integer.toString(q)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(matchValue)%>'/>
<%
			q++;
		}
%>
<input type="hidden" name='<%="matchcount_"+com.metacarta.ui.util.Encoder.attributeEscape(documentClass)%>' value='<%=Integer.toString(matchCount)%>'/>
<input type="hidden" name="documentclasses" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(documentClass)%>'/>
<%
	}
}

// "Mime Types" tab
HashMap mimeTypes = null;
i = 0;
while (i < ds.getChildCount())
{
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals(com.metacarta.crawler.connectors.filenet.FilenetConnector.SPEC_NODE_MIMETYPE))
        {
                String value = sn.getAttributeValue(com.metacarta.crawler.connectors.filenet.FilenetConnector.SPEC_ATTRIBUTE_VALUE);
                if (mimeTypes == null)
			mimeTypes = new HashMap();
		mimeTypes.put(value,value);
        }
}

if (tabName.equals("Mime Types"))
{
%>
<input type="hidden" name="hasmimetypes" value="true"/>
<table class="displaytable">
	<tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	// Fetch the list of valid document classes from the connector
	String[] mimeTypesArray = null;
	String message = null;
	try
	{
		IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext,
			repositoryConnection.getClassName(),
			repositoryConnection.getConfigParams(),
			repositoryConnection.getMaxConnections());
		try
		{
			com.metacarta.crawler.connectors.filenet.FilenetConnector c = (com.metacarta.crawler.connectors.filenet.FilenetConnector)connector;
			mimeTypesArray = c.getMimeTypes();
		}
		finally
		{
			RepositoryConnectorFactory.release(connector);
		}
	}
	catch (MetacartaException e)
	{
		message = e.getMessage();
	}
	catch (ServiceInterruption e)
	{
		message = "FileNet server temporarily unavailable: "+e.getMessage();
	}
%>
	<tr>
<%
	if (message != null)
	{
%>
		<td class="message" colspan="2"><%=com.metacarta.ui.util.Encoder.bodyEscape(message)%></td>
<%
	}
	else
	{
%>
		<td class="description"><nobr>Mime types to include:</nobr></td>
		<td class="value">
			<select name="mimetypes" size="10" multiple="true">
<%
		i = 0;
		while (i < mimeTypesArray.length)
		{
			String mimeType = mimeTypesArray[i++];
			if (mimeTypes == null || mimeTypes.get(mimeType) != null)
			{
%>
				<option value='<%=com.metacarta.ui.util.Encoder.attributeEscape(mimeType)%>' selected="true">
					<%=com.metacarta.ui.util.Encoder.bodyEscape(mimeType)%>
				</option>
<%
			}
			else
			{
%>
				<option value='<%=com.metacarta.ui.util.Encoder.attributeEscape(mimeType)%>'>
					<%=com.metacarta.ui.util.Encoder.bodyEscape(mimeType)%>
				</option>
<%
			}
		}
%>
			</select>
		</td>
<%
	}
%>
	</tr>
</table>
<%
}
else
{
%>
<input type="hidden" name="hasmimetypes" value="true"/>
<%
	if (mimeTypes != null)
	{
		iter = mimeTypes.keySet().iterator();
		while (iter.hasNext())
		{
			String mimeType = (String)iter.next();
%>
<input type="hidden" name="mimetypes" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(mimeType)%>'/>
<%
		}
	}
}

// Security tab
int k;
// Find whether security is on or off
i = 0;
boolean securityOn = true;
while (i < ds.getChildCount())
{
	SpecificationNode sn = ds.getChild(i++);
	if (sn.getType().equals("security"))
	{
		String securityValue = sn.getAttributeValue("value");
		if (securityValue.equals("off"))
			securityOn = false;
		else if (securityValue.equals("on"))
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
	    <td class="value" colspan="1">
		<input type="radio" name="specsecurity" value="on" <%=(securityOn)?"checked=\"true\"":""%> />Enabled&nbsp;
		<input type="radio" name="specsecurity" value="off" <%=(securityOn==false)?"checked=\"true\"":""%> />Disabled
	    </td>
	  </tr>
	  <tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	// Finally, go through forced ACL
	i = 0;
	k = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("access"))
		{
			String accessDescription = "_"+Integer.toString(k);
			String accessOpName = "accessop"+accessDescription;
			String token = sn.getAttributeValue("token");
%>
	  <tr>
		<td class="description">
			<input type="hidden" name='<%=accessOpName%>' value=""/>
			<input type="hidden" name='<%="spectoken"+accessDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(token)%>'/>
			<a name='<%="token_"+Integer.toString(k)%>'><input type="button" value="Delete" alt='<%="Delete access token #"+Integer.toString(k)%>' onClick='<%="Javascript:SpecOp(\""+accessOpName+"\",\"Delete\",\"token_"+Integer.toString(k)+"\")"%>'/></a>
		</td>
		<td class="value">
			<%=com.metacarta.ui.util.Encoder.bodyEscape(token)%>
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
			<a name='<%="token_"+Integer.toString(k)%>'><input type="button" value="Add" alt="Add access token" onClick='<%="Javascript:SpecAddToken(\"token_"+Integer.toString(k+1)+"\")"%>'/></a>
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
	<input type="hidden" name="specsecurity" value='<%=(securityOn?"on":"off")%>'/>
<%
	  // Finally, go through forced ACL
	  i = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("access"))
		{
			String accessDescription = "_"+Integer.toString(k);
			String token = sn.getAttributeValue("token");
%>
	<input type="hidden" name='<%="spectoken"+accessDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(token)%>'/>
<%
			k++;
		}
	  }
%>
	<input type="hidden" name="tokencount" value='<%=Integer.toString(k)%>'/>
<%
}

%>



