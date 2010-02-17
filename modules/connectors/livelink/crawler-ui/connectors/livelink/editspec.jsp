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
	String tabName = (String)threadContext.get("TabName");

	if (ds == null)
		out.println("Hey!  No document specification came in!!!");
	if (repositoryConnection == null)
		out.println("No repository connection!!!");
	if (tabName == null)
		out.println("No tab name!");

	int i;
	int k;

	// Paths tab
	if (tabName.equals("Paths"))
	{
%>
	<table class="displaytable">
	    		<tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	  // Now, loop through paths
	  i = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("startpoint"))
		{
			String pathDescription = "_"+Integer.toString(k);
			String pathOpName = "pathop"+pathDescription;
%>
			<tr>
				<td class="description">
					<input type="hidden" name='<%="specpath"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(sn.getAttributeValue("path"))%>'/>
					<input type="hidden" name='<%=pathOpName%>' value=""/>
					<a name='<%="path_"+Integer.toString(k)%>'><input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")"%>' alt='<%="Delete path #"+Integer.toString(k)%>'/></a>
				</td>
				<td class="value">
					<%=(sn.getAttributeValue("path").length() == 0)?"(root)":org.apache.lcf.ui.util.Encoder.bodyEscape(sn.getAttributeValue("path"))%>
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
				<td class="message" colspan="2">No starting points defined</td>
			</tr>
<%
	  }
%>
	    		<tr><td class="lightseparator" colspan="2"><hr/></td></tr>
	    		<tr>
			    <td class="description">
				<input type="hidden" name="pathcount" value='<%=Integer.toString(k)%>'/>
<%
	
	  String pathSoFar = (String)threadContext.get("specpath");
	  if (pathSoFar == null)
		pathSoFar = "";

	  // Grab next folder/project list
	  try
	  {
	    String[] childList;
	    IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext,
		repositoryConnection.getClassName(),
		repositoryConnection.getConfigParams(),
		repositoryConnection.getMaxConnections());
	    try
	    {
		org.apache.lcf.crawler.connectors.livelink.LivelinkConnector c = (org.apache.lcf.crawler.connectors.livelink.LivelinkConnector)connector;
		childList = c.getChildFolderNames(pathSoFar);
		if (childList == null)
		{
			// Illegal path - set it back
			pathSoFar = "";
			childList = c.getChildFolderNames("");
			if (childList == null)
				throw new LCFException("Can't find any children for root folder");
		}
	    }
	    finally
	    {
		RepositoryConnectorFactory.release(connector);
	    }
	
%>
				<input type="hidden" name="specpath" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(pathSoFar)%>'/>
				<input type="hidden" name="pathop" value=""/>
				<a name='<%="path_"+Integer.toString(k)%>'><input type="button" value="Add" onClick='<%="Javascript:SpecOp(\"pathop\",\"Add\",\"path_"+Integer.toString(k+1)+"\")"%>' alt="Add path"/></a>&nbsp;
			    </td>
			    <td class="value">
				<%=(pathSoFar.length()==0)?"(root)":org.apache.lcf.ui.util.Encoder.bodyEscape(pathSoFar)%>
<%
	    if (pathSoFar.length() > 0)
	    {
%>
				<input type="button" value="-" onClick='<%="Javascript:SpecOp(\"pathop\",\"Up\",\"path_"+Integer.toString(k)+"\")"%>' alt="Back up path"/>
<%
	    }
	    if (childList.length > 0)
	    {
%>
				<input type="button" value="+" onClick='<%="Javascript:SpecAddToPath(\"path_"+Integer.toString(k)+"\")"%>' alt="Add to path"/>&nbsp;
				<select multiple="false" name="pathaddon" size="2">
					<option value="" selected="selected">-- Pick a folder --</option>
<%
		int j = 0;
		while (j < childList.length)
		{
%>
					<option value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(childList[j])%>'><%=org.apache.lcf.ui.util.Encoder.bodyEscape(childList[j])%></option>
<%
			j++;
		}
%>

				</select>
<%
	    }
	  }
	  catch (LCFException e)
	  {
		e.printStackTrace();
		out.println(org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage()));
	  }
%>
			    </td>
	    		</tr>
	</table>
<%
	}
	else
	{
	  // Now, loop through paths
	  i = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("startpoint"))
		{
			String pathDescription = "_"+Integer.toString(k);
%>
	<input type="hidden" name='<%="specpath"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(sn.getAttributeValue("path"))%>'/>
<%
			k++;
		}
	  }
%>
	<input type="hidden" name="pathcount" value='<%=Integer.toString(k)%>'/>
<%
	}

	// Filter tab
	if (tabName.equals("Filters"))
	{
%>
	<table class="displaytable">
	    		<tr><td class="separator" colspan="2"><hr/></td></tr>

<%
	  // Next, go through include/exclude filespecs
	  i = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("include") || sn.getType().equals("exclude"))
		{
			String fileSpecDescription = "_"+Integer.toString(k);
			String fileOpName = "fileop"+fileSpecDescription;
			String filespec = sn.getAttributeValue("filespec");
%>
			<tr>
				<td class="description">
					<input type="hidden" name='<%="specfiletype"+fileSpecDescription%>' value='<%=sn.getType()%>'/>
					<input type="hidden" name='<%=fileOpName%>' value=""/>
					<a name='<%="filespec_"+Integer.toString(k)%>'><input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+fileOpName+"\",\"Delete\",\"filespec_"+Integer.toString(k)+"\")"%>' alt='<%="Delete filespec #"+Integer.toString(k)%>'/></a>
				</td>
				<td class="value">
					<%=sn.getType().equals("include")?"Include:":""%>
					<%=sn.getType().equals("exclude")?"Exclude:":""%>
					&nbsp;<input type="hidden" name='<%="specfile"+fileSpecDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(filespec)%>'/>
					<%=org.apache.lcf.ui.util.Encoder.bodyEscape(filespec)%>
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
				<td class="message" colspan="2">No include/exclude files defined</td>
			</tr>
<%
	  }
%>
	    		<tr><td class="lightseparator" colspan="2"><hr/></td></tr>
			<tr>
			    <td class="description">
				<input type="hidden" name="filecount" value='<%=Integer.toString(k)%>'/>
				<input type="hidden" name="fileop" value=""/>
				<a name='<%="filespec_"+Integer.toString(k)%>'><input type="button" value="Add" onClick='<%="Javascript:SpecAddFilespec(\"filespec_"+Integer.toString(k+1)+"\")"%>' alt="Add file specification"/></a>&nbsp;
			    </td>
			    <td class="value">
				<select name="specfiletype" size="1">
					<option value="include" selected="selected">Include</option>
					<option value="exclude">Exclude</option>
				</select>&nbsp;
				<input type="text" size="30" name="specfile" value=""/>
			    </td>
		        </tr>
	</table>
<%
	}
	else
	{
	  // Next, go through include/exclude filespecs
	  i = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("include") || sn.getType().equals("exclude"))
		{
			String fileSpecDescription = "_"+Integer.toString(k);
			String filespec = sn.getAttributeValue("filespec");
%>
	<input type="hidden" name='<%="specfiletype"+fileSpecDescription%>' value='<%=sn.getType()%>'/>
	<input type="hidden" name='<%="specfile"+fileSpecDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(filespec)%>'/>
<%
			k++;
		}
	  }
%>
	<input type="hidden" name="filecount" value='<%=Integer.toString(k)%>'/>
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
	org.apache.lcf.crawler.connectors.livelink.MatchMap matchMap = new org.apache.lcf.crawler.connectors.livelink.MatchMap();
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


	i = 0;
	String ingestAllMetadata = "false";
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("allmetadata"))
		{
			ingestAllMetadata = sn.getAttributeValue("all");
			if (ingestAllMetadata == null)
				ingestAllMetadata = "false";
		}
 	}

	if (tabName.equals("Metadata"))
	{
%>
	<input type="hidden" name="specmappingcount" value='<%=Integer.toString(matchMap.getMatchCount())%>'/>
	<input type="hidden" name="specmappingop" value=""/>

	<table class="displaytable">
	    		<tr><td class="separator" colspan="4"><hr/></td></tr>
			<tr><td class="description" colspan="1"><nobr>Ingest ALL metadata?</nobr></td>
			    <td class="value" colspan="3"><nobr><input type="radio" name="specallmetadata" value="true" <%=(ingestAllMetadata.equals("true")?"checked=\"true\"":"")%>/>Yes</nobr>&nbsp;
				<nobr><input type="radio" name="specallmetadata" value="false" <%=(ingestAllMetadata.equals("false")?"checked=\"true\"":"")%>/>No</nobr>
			    </td>
			</tr>
	    		<tr><td class="separator" colspan="4"><hr/></td></tr>
<%
	  // Go through the selected metadata attributes
	  i = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("metadata"))
		{
			String accessDescription = "_"+Integer.toString(k);
			String accessOpName = "metadataop"+accessDescription;
			String categoryPath = sn.getAttributeValue("category");
			String isAll = sn.getAttributeValue("all");
			if (isAll == null)
				isAll = "false";
			String attributeName = sn.getAttributeValue("attribute");
			if (attributeName == null)
				attributeName = "";
%>
			<tr>
				<td class="description" colspan="1">
					<input type="hidden" name='<%=accessOpName%>' value=""/>
					<input type="hidden" name='<%="speccategory"+accessDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(categoryPath)%>'/>
					<input type="hidden" name='<%="specattributeall"+accessDescription%>' value='<%=isAll%>'/>
					<input type="hidden" name='<%="specattribute"+accessDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(attributeName)%>'/>
					<a name='<%="metadata_"+Integer.toString(k)%>'><input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+accessOpName+"\",\"Delete\",\"metadata_"+Integer.toString(k)+"\")"%>' alt='<%="Delete metadata #"+Integer.toString(k)%>'/></a>&nbsp;
				</td>
				<td class="value" colspan="3">
					<%=org.apache.lcf.ui.util.Encoder.bodyEscape(categoryPath)%>:<%=(isAll!=null&&isAll.equals("true"))?"(All metadata attributes)":org.apache.lcf.ui.util.Encoder.bodyEscape(attributeName)%>
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
				<td class="message" colspan="4">No metadata specified</td>
			</tr>
<%
	  }
%>
	    		<tr><td class="lightseparator" colspan="4"><hr/></td></tr>
			<tr>
			    <td class="description" colspan="1">
			        <a name='<%="metadata_"+Integer.toString(k)%>'></a>
				<input type="hidden" name="metadatacount" value='<%=Integer.toString(k)%>'/>
<%
	  String categorySoFar = (String)threadContext.get("speccategory");
	  if (categorySoFar == null)
		categorySoFar = "";
	  // Grab next folder/project list, and the appropriate category list
	  try
	  {
	    String[] childList = null;
	    String[] workspaceList = null;
	    String[] categoryList = null;
	    String[] attributeList = null;
	    IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext,
		repositoryConnection.getClassName(),
		repositoryConnection.getConfigParams(),
		repositoryConnection.getMaxConnections());
	    try
	    {
		org.apache.lcf.crawler.connectors.livelink.LivelinkConnector c = (org.apache.lcf.crawler.connectors.livelink.LivelinkConnector)connector;
		if (categorySoFar.length() == 0)
		{
			workspaceList = c.getWorkspaceNames();
		}
		else
		{
		    attributeList = c.getCategoryAttributes(categorySoFar);
		    if (attributeList == null)
		    {
			childList = c.getChildFolderNames(categorySoFar);
			if (childList == null)
			{
				// Illegal path - set it back
				categorySoFar = "";
				childList = c.getChildFolderNames("");
				if (childList == null)
					throw new LCFException("Can't find any children for root folder");
			}
			categoryList = c.getChildCategoryNames(categorySoFar);
			if (categoryList == null)
				throw new LCFException("Can't find any categories for root folder folder");
		    }
		}
	    }
	    finally
	    {
		RepositoryConnectorFactory.release(connector);
	    }

%>
				<input type="hidden" name="speccategory" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(categorySoFar)%>'/>
				<input type="hidden" name="metadataop" value=""/>
<%
	    if (attributeList != null)
	    {
		// We have a valid category!
	
%>
				<input type="button" value="Add" onClick='<%="Javascript:SpecAddMetadata(\"metadata_"+Integer.toString(k+1)+"\")"%>' alt="Add metadata item"/>&nbsp;
			    </td>
			    <td class="value" colspan="3">
				<%=org.apache.lcf.ui.util.Encoder.bodyEscape(categorySoFar)%>:<input type="button" value="-" onClick='<%="Javascript:SpecOp(\"metadataop\",\"Up\",\"metadata_"+Integer.toString(k)+"\")"%>' alt="Back up metadata path"/>&nbsp;
				<table class="displaytable">
				<tr>
				  <td class="value">
				      <input type="checkbox" name="attributeall" value="true"/>&nbsp;All attributes in this category<br/>
				      <select multiple="true" name="attributeselect" size="2">
					<option value="" selected="selected">-- Pick attributes --</option>
<%
		int l = 0;
		while (l < attributeList.length)
		{
			String attributeName = attributeList[l++];
%>
					<option value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(attributeName)%>'><%=org.apache.lcf.ui.util.Encoder.bodyEscape(attributeName)%></option>
<%
		}
%>
				      </select>
				  </td>
				</tr>
				</table>
<%
	    }
	    else if (workspaceList != null)
	    {
%>
			    </td>
			    <td class="value" colspan="3">
				<input type="button" value="+" onClick='<%="Javascript:SpecSetWorkspace(\"metadata_"+Integer.toString(k)+"\")"%>' alt="Add to metadata path"/>&nbsp;
			        <select multiple="false" name="metadataaddon" size="2">
					<option value="" selected="selected">-- Pick workspace --</option>
<%
			int j = 0;
			while (j < workspaceList.length)
			{
%>
					<option value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(workspaceList[j])%>'><%=org.apache.lcf.ui.util.Encoder.bodyEscape(workspaceList[j])%></option>
<%
				j++;
			}
%>

				</select>
<%
	    }
	    else
	    {
%>
			    </td>
			    <td class="value" colspan="3">
				<%=(categorySoFar.length()==0)?"(root)":org.apache.lcf.ui.util.Encoder.bodyEscape(categorySoFar)%>&nbsp;
<%
		if (categorySoFar.length() > 0)
		{
%>
				<input type="button" value="-" onClick='<%="Javascript:SpecOp(\"metadataop\",\"Up\",\"metadata_"+Integer.toString(k)+"\")"%>' alt="Back up metadata path"/>&nbsp;
<%
		}
		if (childList.length > 0)
		{
%>
				<input type="button" value="+" onClick='<%="Javascript:SpecAddToMetadata(\"metadata_"+Integer.toString(k)+"\")"%>' alt="Add to metadata path"/>&nbsp;
				<select multiple="false" name="metadataaddon" size="2">
					<option value="" selected="selected">-- Pick a folder --</option>
<%
			int j = 0;
			while (j < childList.length)
			{
%>
					<option value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(childList[j])%>'><%=org.apache.lcf.ui.util.Encoder.bodyEscape(childList[j])%></option>
<%
				j++;
			}
%>

				</select>
<%
		}
		if (categoryList.length > 0)
		{
%>
				<input type="button" value="+" onClick='<%="Javascript:SpecAddCategory(\"metadata_"+Integer.toString(k)+"\")"%>' alt="Add category"/>&nbsp;
				<select multiple="false" name="categoryaddon" size="2">
					<option value="" selected="selected">-- Pick a category --</option>
<%
			int j = 0;
			while (j < categoryList.length)
			{
%>
					<option value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(categoryList[j])%>'><%=org.apache.lcf.ui.util.Encoder.bodyEscape(categoryList[j])%></option>
<%
				j++;
			}
%>

				</select>
<%
		}
	    }
	  }
	  catch (LCFException e)
	  {
		e.printStackTrace();
		out.println(org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage()));
	  }
%>
			    </td>
			</tr>
	    		<tr><td class="separator" colspan="4"><hr/></td></tr>
			<tr>
			  <td class="description" colspan="1"><nobr>Path attribute name:</nobr></td>
			  <td class="value" colspan="3">
				<input type="text" name="specpathnameattribute" size="20" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(pathNameAttribute)%>'/>
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
				<td class="description"><input type="hidden" name='<%="specmappingop_"+Integer.toString(i)%>' value=""/><a name='<%="mapping_"+Integer.toString(i)%>'><input type="button" onClick='<%="Javascript:SpecOp(\"specmappingop_"+Integer.toString(i)+"\",\"Delete\",\"mapping_"+Integer.toString(i)+"\")"%>' alt='<%="Delete mapping #"+Integer.toString(i)%>' value="Delete"/></a></td>
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
	<input type="hidden" name="specallmetadata" value='<%=ingestAllMetadata%>'/>
<%
	  // Go through the selected metadata attributes
	  i = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("metadata"))
		{
			String accessDescription = "_"+Integer.toString(k);
			String categoryPath = sn.getAttributeValue("category");
			String isAll = sn.getAttributeValue("all");
			if (isAll == null)
				isAll = "false";
			String attributeName = sn.getAttributeValue("attribute");
			if (attributeName == null)
				attributeName = "";
%>
	<input type="hidden" name='<%="speccategory"+accessDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(categoryPath)%>'/>
	<input type="hidden" name='<%="specattributeall"+accessDescription%>' value='<%=isAll%>'/>
	<input type="hidden" name='<%="specattribute"+accessDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(attributeName)%>'/>
<%
			k++;
		}
	  }
%>
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