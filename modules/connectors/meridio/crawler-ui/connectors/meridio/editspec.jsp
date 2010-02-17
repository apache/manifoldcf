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
	// This file is included by every place that the specification information for the file system connector
	// needs to be edited.  When it is called, the DocumentSpecification object is placed in the thread context
	// under the name "DocumentSpecification".  The IRepositoryConnection object is also in the thread context,
	// under the name "RepositoryConnection".

	// The coder can presume that this jsp is executed within a body section, and within a form.

	DocumentSpecification ds = (DocumentSpecification)threadContext.get("DocumentSpecification");
	IRepositoryConnection repositoryConnection = (IRepositoryConnection)threadContext.get("RepositoryConnection");
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

	// Search Paths tab
	if (tabName.equals("Search Paths"))
	{
%>
<table class="displaytable">
    	<tr><td class="separator" colspan="2"><hr/></td></tr>
<%
		i = 0;
		k = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i++);
			if (sn.getType().equals("SearchPath"))
			{
				// Found a search path.  Not clear from the spec what the attribute is, or whether this is
				// body data, so I'm going to presume it's a path attribute.
				String pathString = sn.getAttributeValue("path");
%>
	<tr>
		<td class="description">
			<input type="hidden" name='<%="specpathop_"+Integer.toString(k)%>' value="Continue"/>
			<input type="hidden" name='<%="specpath_"+Integer.toString(k)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(pathString)%>'/>
			<a name='<%="SpecPath_"+Integer.toString(k)%>'>
				<input type="button" value="Delete" onclick='<%="javascript:SpecDeletePath("+Integer.toString(k)+");"%>' alt='<%="Delete path #"+Integer.toString(k)%>'/>
			</a>
		</td>
		<td class="value"><nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(pathString)%></nobr></td>
	</tr>
<%
				k++;
			}
		}
		if (k == 0)
		{
%>
	<tr><td class="message" colspan="2"><nobr>No paths specified.</nobr></td></tr>
<%
		}
%>
	<tr><td class="lightseparator" colspan="2"><input type="hidden" name="specpath_total" value='<%=Integer.toString(k)%>'/><hr/></td>
	</tr>
	<tr>
<%
		// The path, and the corresponding IDs
		String pathSoFar = (String)threadContext.get("specpath");
		String idsSoFar = (String)threadContext.get("specpathids");

		// The type of the object described by the path
		Integer containerType = (Integer)threadContext.get("specpathtype");

		if (pathSoFar == null)
			pathSoFar = "/";
		if (idsSoFar == null)
			idsSoFar = "0";
		if (containerType == null)
			containerType = new Integer(org.apache.lcf.crawler.connectors.meridio.MeridioClassContents.CLASS);

		int currentInt = 0;
		if (idsSoFar.length() > 0)
		{
			String[] ids = idsSoFar.split(",");
			currentInt = Integer.parseInt(ids[ids.length-1]);
		}

		// Grab next folder/project list
		try
		{
	  	    org.apache.lcf.crawler.connectors.meridio.MeridioClassContents[] childList;
		    if (containerType.intValue() == org.apache.lcf.crawler.connectors.meridio.MeridioClassContents.CLASS)
		    {
	    		IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext,
				repositoryConnection.getClassName(),
				repositoryConnection.getConfigParams(),
				repositoryConnection.getMaxConnections());
	    		try
	    		{
				org.apache.lcf.crawler.connectors.meridio.MeridioConnector c = (org.apache.lcf.crawler.connectors.meridio.MeridioConnector)connector;
				childList = c.getClassOrFolderContents(currentInt);
			}
			finally
			{
				RepositoryConnectorFactory.release(connector);
			}
		    }
		    else
			childList = new org.apache.lcf.crawler.connectors.meridio.MeridioClassContents[0];
%>
		<td class="description">
			<input type="hidden" name="specpathop" value="Continue"/>
			<input type="hidden" name="specpathbase" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(pathSoFar)%>'/>
			<input type="hidden" name="specidsbase" value='<%=idsSoFar%>'/>
			<input type="hidden" name="spectype" value='<%=containerType.toString()%>'/>
			<a name="SpecPathAdd"><input type="button" value="Add" onclick="javascript:SpecAddPath();" alt="Add path"/></a>
		</td>
		<td class="value">
			<nobr>
				<%=org.apache.lcf.ui.util.Encoder.bodyEscape(pathSoFar)%>
<%
		    if (pathSoFar.length() > 1)
		    {
%>
				<input type="button" value="-" onclick="javascript:SpecDeleteFromPath();" alt="Delete from path"/>
<%
		    }
		    if (childList.length > 0)
		    {
%>
				<input type="button" value="+" onclick="javascript:SpecAddToPath();" alt="Add to path"/>
				<select name="specpath" size="10">
					<option value="" selected="">-- Pick a folder --</option>
<%
			int j = 0;
			while (j < childList.length)
			{
				// The option selected needs to include both the id and the name, since I have no way
				// to get to the name from the id.  So, put the id first, then a semicolon, then the name.
%>
					<option value='<%=Integer.toString(childList[j].classOrFolderId)+";"+Integer.toString(childList[j].containerType)+";"+org.apache.lcf.ui.util.Encoder.attributeEscape(childList[j].classOrFolderName)%>'>
						<%=org.apache.lcf.ui.util.Encoder.bodyEscape(childList[j].classOrFolderName)%>
					</option>
<%
				j++;
			}
%>
				</select>
<%
		    }
%>
			</nobr>
		</td>
<%

		}
		catch (ServiceInterruption e)
		{
			e.printStackTrace();
%>
		<td class="message" colspan="2">Service interruption: <%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%></td>
<%
		}
		catch (LCFException e)
		{
			e.printStackTrace();
%>
		<td class="message" colspan="2"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%></td>
<%
		}
%>
	</tr>
</table>
<%
	}
	else
	{
		// The path tab is hidden; just preserve the contents
		i = 0;
		k = 0;
		while (i < ds.getChildCount())
		{
			SpecificationNode sn = ds.getChild(i++);
			if (sn.getType().equals("SearchPath"))
			{
				// Found a search path.  Not clear from the spec what the attribute is, or whether this is
				// body data, so I'm going to presume it's a value attribute.
				String pathString = sn.getAttributeValue("path");
%>
<input type="hidden" name='<%="specpath_"+Integer.toString(k)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(pathString)%>'/>
<%
				k++;
			}
		}
%>
<input type="hidden" name="specpath_total" value='<%=Integer.toString(k)%>'/>
<%
	}

	// Content Types tab


	// The allowed mime types, which are those that the ingestion API understands
	String[] allowedMimeTypes = new String[]
	{
                	"application/excel",
                	"application/powerpoint",
                	"application/ppt",
                	"application/rtf",
                	"application/xls",
                	"text/html",
                	"text/rtf",
                	"text/pdf",
                	"application/x-excel",
                	"application/x-msexcel",
                	"application/x-mspowerpoint",
                	"application/x-msword-doc",
                	"application/x-msword",
                	"application/x-word",
                	"Application/pdf",
                	"text/xml",
                	"no-type",
                	"text/plain",
                	"application/pdf",
                	"application/x-rtf",
                	"application/vnd.ms-excel",
                	"application/vnd.ms-pps",
                	"application/vnd.ms-powerpoint",
                	"application/vnd.ms-word",
                	"application/msword",
                	"application/msexcel",
                	"application/mspowerpoint",
                	"application/ms-powerpoint",
                	"application/ms-word",
                	"application/ms-excel",
                	"Adobe",
                	"application/Vnd.Ms-Excel",
                	"vnd.ms-powerpoint",
                	"application/x-pdf",
                	"winword",
                	"text/richtext",
                	"Text",
                	"Text/html",
                	"application/MSWORD",
                	"application/PDF",
                	"application/MSEXCEL",
                	"application/MSPOWERPOINT"
	};
	java.util.Arrays.sort(allowedMimeTypes);

	HashMap mimeTypeMap = new HashMap();
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("MIMEType"))
		{
			String type = sn.getAttributeValue("type");
			mimeTypeMap.put(type,type);
		}
	}
	// If there are none selected, then check them all, since no mime types would be nonsensical.
	if (mimeTypeMap.size() == 0)
	{
		i = 0;
		while (i < allowedMimeTypes.length)
		{
			String allowedMimeType = allowedMimeTypes[i++];
			mimeTypeMap.put(allowedMimeType,allowedMimeType);
		}
	}

	if (tabName.equals("Content Types"))
	{
%>
<table class="displaytable">
    	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Mime types:</nobr></td>
		<td class="value">
<%
		i = 0;
		while (i < allowedMimeTypes.length)
		{
			String mimeType = allowedMimeTypes[i++];
%>
			<nobr><input type="checkbox" name="specmimetypes" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(mimeType)%>' <%=((mimeTypeMap.get(mimeType)!=null)?"checked=\"true\"":"")%>>
				<%=org.apache.lcf.ui.util.Encoder.bodyEscape(mimeType)%>
			</input></nobr>
			<br/>
<%
		}
%>
		</td>
	</tr>
</table>
<%
	}
	else
	{
		// Tab is not selected.  Submit a separate hidden for each value that was selected before.
		Iterator iter = mimeTypeMap.keySet().iterator();
		while (iter.hasNext())
		{
			String mimeType = (String)iter.next();
%>
<input type="hidden" name="specmimetypes" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(mimeType)%>'/>
<%
		}
	}

	// Categories tab

	HashMap categoriesMap = new HashMap();
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("SearchCategory"))
		{
			String category = sn.getAttributeValue("category");
			categoriesMap.put(category,category);
		}
	}

	if (tabName.equals("Categories"))
	{
%>
<table class="displaytable">
    	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
<%
		// Grab the list of available categories from Meridio
		try
		{
	  		String[] categoryList;
	    		IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext,
				repositoryConnection.getClassName(),
				repositoryConnection.getConfigParams(),
				repositoryConnection.getMaxConnections());
	    		try
	    		{
				org.apache.lcf.crawler.connectors.meridio.MeridioConnector c = (org.apache.lcf.crawler.connectors.meridio.MeridioConnector)connector;
				categoryList = c.getMeridioCategories();
			}
			finally
			{
				RepositoryConnectorFactory.release(connector);
			}
%>
		<td class="description"><nobr>Categories:</nobr></td>
		<td class="value">
<%
			k = 0;
			while (k < categoryList.length)
			{
				String category = categoryList[k++];
%>
			<nobr><input type="checkbox" name="speccategories" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(category)%>' <%=((categoriesMap.get(category)!=null)?"checked=\"true\"":"")%>>
				<%=org.apache.lcf.ui.util.Encoder.bodyEscape(category)%>
			</input></nobr>
			<br/>
<%
			}
%>
		</td>
<%
		}
		catch (ServiceInterruption e)
		{
			e.printStackTrace();
%>
		<td class="message" colspan="2">Service interruption: <%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%></td>
<%
		}
		catch (LCFException e)
		{
			e.printStackTrace();
%>
		<td class="message" colspan="2"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%></td>
<%
		}

%>
	</tr>
</table>
<%
	}
	else
	{
		// Tab is not selected.  Submit a separate hidden for each value that was selected before.
		Iterator iter = categoriesMap.keySet().iterator();
		while (iter.hasNext())
		{
			String category = (String)iter.next();
%>
<input type="hidden" name="speccategories" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(category)%>'/>
<%
		}
	}

	// Data Types tab
	String mode = "DOCUMENTS_AND_RECORDS";
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("SearchOn"))
			mode = sn.getAttributeValue("value");
	}

	if (tabName.equals("Data Types"))
	{
%>
<table class="displaytable">
    	<tr><td class="separator" colspan="2"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Data types to ingest:</nobr></td>
		<td class="value">
			<nobr><input type="radio" name="specsearchon" value="DOCUMENTS" <%=mode.equals("DOCUMENTS")?"checked=\"true\"":""%>/>Documents</nobr><br/>
			<nobr><input type="radio" name="specsearchon" value="RECORDS" <%=mode.equals("RECORDS")?"checked=\"true\"":""%>/>Records</nobr><br/>
			<nobr><input type="radio" name="specsearchon" value="DOCUMENTS_AND_RECORDS" <%=mode.equals("DOCUMENTS_AND_RECORDS")?"checked=\"true\"":""%>/>Documents and records</nobr><br/>
		</td>
	</tr>
</table>
<%
	}
	else
	{
%>
<input type="hidden" name="specsearchon" value='<%=mode%>'/>
<%
	}

	// Security tab

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
		if (sn.getType().equals("access"))
		{
			String accessDescription = "_"+Integer.toString(k);
			String accessOpName = "accessop"+accessDescription;
			String token = sn.getAttributeValue("token");
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
			<a name='<%="token_"+Integer.toString(k)%>'><input type="button" value="Add" onClick='<%="Javascript:SpecAddAccessToken(\"token_"+Integer.toString(k+1)+"\")"%>' alt="Add access token"/></a>&nbsp;
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
<input type="hidden" name='<%="spectoken"+accessDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(token)%>'/>
<%
			k++;
		}
	  }
%>
<input type="hidden" name="tokencount" value='<%=Integer.toString(k)%>'/>
<%
	}

	// Metadata tab

	// Find the path-value metadata attribute name
	i = 0;
	String pathNameAttribute = "";
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("pathnameattribute"))
		{
			pathNameAttribute = sn.getAttributeValue("value");
		}
	}

	// Find the path-value mapping data
	i = 0;
	org.apache.lcf.crawler.connectors.meridio.MatchMap matchMap = new org.apache.lcf.crawler.connectors.meridio.MatchMap();
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("pathmap"))
		{
			String pathMatch = sn.getAttributeValue("match");
			String pathReplace = sn.getAttributeValue("replace");
			matchMap.appendMatchPair(pathMatch,pathReplace);
		}
	}

	boolean allMetadata = false;
	HashMap metadataSelected = new HashMap();
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("ReturnedMetadata"))
		{
			String category = sn.getAttributeValue("category");
			String property = sn.getAttributeValue("property");
			String descriptor;
			if (category == null || category.length() == 0)
				descriptor = property;
			else
				descriptor = category + "." + property;
			metadataSelected.put(descriptor,descriptor);
		}
		else if (sn.getType().equals("AllMetadata"))
		{
			String value = sn.getAttributeValue("value");
			if (value != null && value.equals("true"))
			{
				allMetadata = true;
			}
			else
				allMetadata = false;
		}
	}
	if (tabName.equals("Metadata"))
	{
%>

<input type="hidden" name="specmappingcount" value='<%=Integer.toString(matchMap.getMatchCount())%>'/>
<input type="hidden" name="specmappingop" value=""/>

<table class="displaytable">
    <tr><td class="separator" colspan="4"><hr/></td></tr>
    <tr>
        <td class="description" colspan="1">
            <nobr>Include all metadata:</nobr>
        </td>
        <td class="value" colspan="3">
            <nobr>
		<input type="radio" name="allmetadata" value="false" <%=(allMetadata==false)?"checked=\"true\"":""%>>Include specified</input>
		<input type="radio" name="allmetadata" value="true" <%=(allMetadata)?"checked=\"true\"":""%>>Include all</input>
	  </nobr>
        </td>
    </tr>
    <tr><td class="separator" colspan="4"><hr/></td></tr>
	<tr>
<%
		// get the list of properties from the repository
		try
		{
	  		String[] propertyList;
	    		IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext,
				repositoryConnection.getClassName(),
				repositoryConnection.getConfigParams(),
				repositoryConnection.getMaxConnections());
	    		try
	    		{
				org.apache.lcf.crawler.connectors.meridio.MeridioConnector c = (org.apache.lcf.crawler.connectors.meridio.MeridioConnector)connector;
				propertyList = c.getMeridioDocumentProperties();
			}
			finally
			{
				RepositoryConnectorFactory.release(connector);
			}
%>
		<td class="description" colspan="1"><nobr>Metadata:</nobr></td>
		<td class="value" colspan="3">
			<input type="hidden" name="specproperties_edit" value="true"/>
<%
			k = 0;
			while (k < propertyList.length)
			{
				String descriptor = propertyList[k++];
%>
			<nobr><input type="checkbox" name="specproperties" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(descriptor)%>' <%=((metadataSelected.get(descriptor)!=null)?"checked=\"true\"":"")%>>
				<%=org.apache.lcf.ui.util.Encoder.bodyEscape(descriptor)%>
			</input></nobr>
			<br/>
<%
			}
%>
		</td>
<%
		}
		catch (ServiceInterruption e)
		{
			e.printStackTrace();
%>
		<td class="message" colspan="4">Service interruption: <%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%></td>
<%
		}
		catch (LCFException e)
		{
			e.printStackTrace();
%>
		<td class="message" colspan="4"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%></td>
<%
		}

%>
	</tr>
	<tr><td class="separator" colspan="4"><hr/></td></tr>
	<tr>
		<td class="description"><nobr>Path attribute metadata name:</nobr></td>
		<td class="value" colspan="3"><nobr>
			<input type="text" size="16" name="specpathnameattribute" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(pathNameAttribute)%>'/></nobr>
		</td>
	</tr>
	<tr><td class="separator" colspan="4"><hr/></td></tr>
<%
		i = 0;
		while (i < matchMap.getMatchCount())
		{
			String matchString = matchMap.getMatchString(i);
			String replaceString = matchMap.getReplaceString(i);
%>
	<tr>
		<td class="description"><input type="hidden" name='<%="specmappingop_"+Integer.toString(i)%>' value=""/><a name='<%="mapping_"+Integer.toString(i)%>'><input type="button" onClick='<%="Javascript:SpecDeleteMapping(Integer.toString(i),\"mapping_"+Integer.toString(i)+"\")"%>' alt='<%="Delete mapping #"+Integer.toString(i)%>' value="Delete"/></a></td>
		<td class="value"><input type="hidden" name='<%="specmatch_"+Integer.toString(i)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(matchString)%>'/><%=org.apache.lcf.ui.util.Encoder.bodyEscape(matchString)%></td>
		<td class="value">==></td>
		<td class="value"><input type="hidden" name='<%="specreplace_"+Integer.toString(i)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(replaceString)%>'/><%=org.apache.lcf.ui.util.Encoder.bodyEscape(replaceString)%></td>
	</tr>
<%
		i++;
		}
		if (i == 0)
		{
%>
	<tr><td colspan="4" class="message">No mappings specified</td></tr>
<%
		}
%>
	<tr><td class="lightseparator" colspan="4"><hr/></td></tr>
	<tr>
		<td class="description"><a name='<%="mapping_"+Integer.toString(i)%>'><input type="button" onClick='<%="Javascript:SpecAddMapping(\"mapping_"+Integer.toString(i+1)+"\")"%>' alt="Add to mappings" value="Add"/></a></td>
		<td class="value">Match regexp:&nbsp;<input type="text" name="specmatch" size="32" value=""/></td>
		<td class="value">==></td>
		<td class="value">Replace string:&nbsp;<input type="text" name="specreplace" size="32" value=""/></td>
	</tr>
</table>
<%
	}
	else
	{
%>
<input type="hidden" name="specproperties_edit" value="true"/>
<%
		Iterator iter = metadataSelected.keySet().iterator();
		while (iter.hasNext())
		{
			String descriptor = (String)iter.next();
%>
<input type="hidden" name="specproperties" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(descriptor)%>'/>
<%
		}
%>
<input type="hidden" name="allmetadata" value='<%=(allMetadata?"true":"false")%>'/>
<input type="hidden" name="specpathnameattribute" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(pathNameAttribute)%>'/>
<input type="hidden" name="specmappingcount" value='<%=Integer.toString(matchMap.getMatchCount())%>'/>
<%
		i = 0;
		while (i < matchMap.getMatchCount())
		{
			String matchString = matchMap.getMatchString(i);
			String replaceString = matchMap.getReplaceString(i);
%>
<input type="hidden" name='<%="specmatch_"+Integer.toString(i)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(matchString)%>'/>
<input type="hidden" name='<%="specreplace_"+Integer.toString(i)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(replaceString)%>'/>
<%
			i++;
		}
	}

%>