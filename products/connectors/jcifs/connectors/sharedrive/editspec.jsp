<%@ include file="../../adminDefaults.jsp" %>

<%
/* SharedDrive */
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

	// "Content Length" tab
	i = 0;
	String maxLength = null;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_MAXLENGTH))
			maxLength = sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_VALUE);
	}
	if (maxLength == null)
		maxLength = "";

	if (tabName.equals("Content Length"))
	{
%>
	<table class="displaytable">
			<tr><td class="separator" colspan="2"><hr/></td></tr>
			<tr>
				<td class="description"><nobr>Maximum document length:</nobr></td>
				<td class="value"><input type="text" name="specmaxlength" size="10" value='<%=maxLength%>'/></td>
			</tr>
	</table>
<%
	}
	else
	{
%>
	<input type="hidden" name="specmaxlength" value='<%=maxLength%>'/>
<%
	}

	// Check for Paths tab
	if (tabName.equals("Paths"))
	{
%>
	<table class="displaytable">
			<tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	  // Now, loop through paths.  There will be a row in the current table for each one.
	  // The row will contain a delete button on the left.  On the right will be the startpoint itself at the top,
	  // and underneath it the table where the filter criteria are edited.
	  i = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_STARTPOINT))
		{
			String pathDescription = "_"+Integer.toString(k);
			String pathOpName = "pathop"+pathDescription;
			String startPath = sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_PATH);
%>
			<tr>
				<td class="value">
					<a name='<%="path_"+Integer.toString(k)%>'><input type="button" value="Delete" alt='<%="Delete path #"+Integer.toString(k)%>' onClick='<%="Javascript:SpecOp(\""+pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")"%>'/></a>&nbsp;
				</td>
				<td class="value">
				    <table class="displaytable">
				    <tr>
					<td class="value">
					    <input type="hidden" name='<%="specpath"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_PATH))%>'/>
					    <input type="hidden" name='<%=pathOpName%>' value=""/>
					    <nobr><%=(startPath.length() == 0)?"(root)":com.metacarta.ui.util.Encoder.bodyEscape(startPath)%></nobr>
					</td>
				    </tr>
				    <tr>
					<td class="boxcell">
					    <table class="displaytable">
<%
			// Now go through the include/exclude children of this node, and display one line per node, followed
			// an "add" line.
			int j = 0;
			while (j < sn.getChildCount())
			{
				SpecificationNode excludeNode = sn.getChild(j);
				String instanceDescription = "_"+Integer.toString(k)+"_"+Integer.toString(j);
				String instanceOpName = "specop" + instanceDescription;

				String nodeFlavor = excludeNode.getType();
				String nodeType = excludeNode.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_TYPE);
				if (nodeType == null)
					nodeType = "";
				String filespec = excludeNode.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_FILESPEC);
				String indexable = excludeNode.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_INDEXABLE);
				if (indexable == null)
					indexable = "";
%>
					    <tr>
						<td class="value">
							<input type="button" value="Insert" onClick='<%="Javascript:SpecInsertSpec(\""+
								instanceDescription+"\",\"filespec_"+Integer.toString(k)+"_"+Integer.toString(j+1)+"\")"%>' alt='<%="Insert new match for path #"+
								Integer.toString(k)+" before position #"+Integer.toString(j)%>'/>
						</td>
						<td class="value"><nobr>
							<select name='<%="specfl_i"+instanceDescription%>'>
								<option value="include">Include</option>
								<option value="exclude">Exclude</option>
							</select>&nbsp;
							<select name='<%="spectin_i"+instanceDescription%>'>
								<option value="" selected="selected">-- Any file or directory --</option>
								<option value="file">file(s)</option>
								<option value="indexable-file">indexable file(s)</option>
								<option value="unindexable-file">un-indexable file(s)</option>
								<option value="directory">directory(s)</option>
							</select>&nbsp;matching&nbsp;<input type="text" size="20" name='<%="specfile_i"+instanceDescription%>' value=""/>
						</nobr></td>

					    </tr>
					    <tr>
						<td class="value">
							<a name='<%="filespec_"+Integer.toString(k)+"_"+Integer.toString(j)%>'><input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+
								"specop"+instanceDescription+"\",\"Delete\",\"filespec_"+Integer.toString(k)+"_"+Integer.toString(j)+"\")"%>' alt='<%="Delete path #"+Integer.toString(k)+
								", match spec #"+Integer.toString(j)%>'/></a>
						</td>
						<td class="value"><nobr>
							<input type="hidden" name='<%="specop"+instanceDescription%>' value=""/>
							<input type="hidden" name='<%="specfl"+instanceDescription%>' value='<%=nodeFlavor%>'/>
							<input type="hidden" name='<%="specty"+instanceDescription%>' value='<%=nodeType%>'/>
							<input type="hidden" name='<%="specin"+instanceDescription%>' value='<%=indexable%>'/>
							<input type="hidden" name='<%="specfile"+instanceDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(filespec)%>'/>
							<%=Integer.toString(j+1)%>.&nbsp;<%=(nodeFlavor.equals("include"))?"Include":""%><%=(nodeFlavor.equals("exclude"))?"Exclude":""%><%=(indexable.equals("yes"))?"&nbsp;indexable":""%><%=(indexable.equals("no"))?"&nbsp;un-indexable":""%><%=(nodeType.equals("file"))?"&nbsp;file(s)":""%><%=(nodeType.equals("directory"))?"&nbsp;directory(s)":""%><%=(nodeType.equals(""))?"&nbsp;file(s)&nbsp;or&nbsp;directory(s)":""%>&nbsp;matching&nbsp;<%=com.metacarta.ui.util.Encoder.bodyEscape(filespec)%>
						</nobr></td>
					    </tr>
<%
				j++;
			}
			if (j == 0)
			{
%>
					    <tr><td class="message" colspan="2">No rules defined</td></tr>
<%
			}
%>
					    <tr><td class="lightseparator" colspan="2"><hr/></td></tr>
					    <tr>
					      <td class="value">
						<input type="hidden" name='<%="specchildcount"+pathDescription%>' value='<%=Integer.toString(j)%>'/>
						<a name='<%="filespec_"+Integer.toString(k)+"_"+Integer.toString(j)%>'><input type="button" value="Add" onClick='<%="Javascript:SpecAddSpec(\""+pathDescription+"\",\"filespec_"+Integer.toString(k)+"_"+Integer.toString(j+1)+"\")"%>'
							alt='<%="Add new match for path #"+Integer.toString(k)%>'/></a>
					      </td>
					      <td class="value"><nobr>
						<select name='<%="specfl"+pathDescription%>'>
							<option value="include">Include</option>
							<option value="exclude">Exclude</option>
						</select>&nbsp;
						<select name='<%="spectin"+pathDescription%>'>
							<option value="">-- Any file or directory --</option>
							<option value="file">file(s)</option>
							<option value="indexable-file">indexable file(s)</option>
							<option value="unindexable-file">un-indexable file(s)</option>
							<option value="directory">directory(s)</option>
						</select>&nbsp;matching&nbsp;
						<input type="text" size="20" name='<%="specfile"+pathDescription%>' value=""/>
					      </nobr></td>
					    </tr>
					    </table>
					</td>
				    </tr>
				    </table>
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
			    <td class="value" colspan="2"><nobr>
				<input type="hidden" name="pathcount" value='<%=Integer.toString(k)%>'/>
				<a name='<%="path_"+Integer.toString(k)%>'>
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
			com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector c = (com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector)connector;
			childList = c.getChildFolderNames(pathSoFar);
			if (childList == null)
			{
				// Illegal path - set it back
				pathSoFar = "";
				childList = c.getChildFolderNames("");
				if (childList == null)
					throw new MetacartaException("Can't find any children for root folder");
			}
	    } finally {
			RepositoryConnectorFactory.release(connector);
	    }
%>
				<input type="hidden" name="specpath" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(pathSoFar)%>'/>
				<input type="hidden" name="pathop" value=""/>
				<input type="button" value="Add" alt="Add path" onClick='<%="Javascript:SpecOp(\"pathop\",\"Add\",\"path_"+Integer.toString(k+1)+"\")"%>'/>&nbsp;
				<%=(pathSoFar.length()==0)?"(root)":pathSoFar%>	
<%
	    if (pathSoFar.length() > 0)
	    {
%>
				<input type="button" value="-" alt="Remove from path" onClick='<%="Javascript:SpecOp(\"pathop\",\"Up\",\"path_"+Integer.toString(k)+"\")"%>'/>
<%
	    }
	    if (childList.length > 0)
	    {
%>
				<nobr><input type="button" value="+" alt="Add to path" onClick='<%="Javascript:SpecAddToPath(\"path_"+Integer.toString(k)+"\")"%>'/>&nbsp;
				<select multiple="false" name="pathaddon" size="4">
					<option value="" selected="selected">-- Pick a folder --</option>
<%
		int j = 0;
		while (j < childList.length)
		{
			String folder = com.metacarta.ui.util.Encoder.attributeEscape(childList[j]);
%>
					<option value='<%=folder%>'><%=folder%></option>
<%
			j++;
		}
%>

				</select> or type a path:<input type="text" name="pathtypein" size="16" value=""/></nobr>
<%
	    }
	  }
	  catch (MetacartaException e)
	  {
		e.printStackTrace();
		out.println(com.metacarta.ui.util.Encoder.bodyEscape(e.getMessage()));
	  }
%>
				</a>
			    </nobr></td>
	    		</tr>
	</table>
<%
	}
	else
	{
	  // Generate hiddens for the pathspec tab
	  i = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_STARTPOINT))
		{
			String pathDescription = "_"+Integer.toString(k);
			String startPath = sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_PATH);
%>
	<input type="hidden" name='<%="specpath"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(startPath)%>'/>
<%
			// Now go through the include/exclude children of this node.
			int j = 0;
			while (j < sn.getChildCount())
			{
				SpecificationNode excludeNode = sn.getChild(j);
				String instanceDescription = "_"+Integer.toString(k)+"_"+Integer.toString(j);

				String nodeFlavor = excludeNode.getType();
				String nodeType = excludeNode.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_TYPE);
				if (nodeType == null)
					nodeType = "";
				String filespec = excludeNode.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_FILESPEC);
				String indexable = excludeNode.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_INDEXABLE);
				if (indexable == null)
					indexable = "";
%>
	<input type="hidden" name='<%="specfl"+instanceDescription%>' value='<%=nodeFlavor%>'/>
	<input type="hidden" name='<%="specty"+instanceDescription%>' value='<%=nodeType%>'/>
	<input type="hidden" name='<%="specin"+instanceDescription%>' value='<%=indexable%>'/>
	<input type="hidden" name='<%="specfile"+instanceDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(filespec)%>'/>
<%
				j++;
			}
			k++;
%>
	<input type="hidden" name='<%="specchildcount"+pathDescription%>' value='<%=Integer.toString(j)%>'/>
<%
		}
	  }
%>
	<input type="hidden" name="pathcount" value='<%=Integer.toString(k)%>'/>
<%
	}


	// Security tab

	// Find whether security is on or off
	i = 0;
	boolean securityOn = true;
	boolean shareSecurityOn = true;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_SECURITY))
		{
			String securityValue = sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_VALUE);
			if (securityValue.equals("off"))
				securityOn = false;
			else if (securityValue.equals("on"))
				securityOn = true;
		}
		if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_SHARESECURITY))
		{
			String securityValue = sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_VALUE);
			if (securityValue.equals("off"))
				shareSecurityOn = false;
			else if (securityValue.equals("on"))
				shareSecurityOn = true;
		}
	}

	if (tabName.equals("Security"))
	{
%>
	<table class="displaytable">
			<tr><td class="separator" colspan="4"><hr/></td></tr>

			<tr>
			    <td class="description"><nobr>File security:</nobr></td>
			    <td colspan="3" class="value"><nobr>
				<input type="radio" name="specsecurity" value="on" <%=(securityOn)?"checked=\"true\"":""%> />Enabled&nbsp;
				<input type="radio" name="specsecurity" value="off" <%=(securityOn==false)?"checked=\"true\"":""%> />Disabled
			    </nobr></td>
		        </tr>

	    		<tr><td class="separator" colspan="4"><hr/></td></tr>

<%
	  // Finally, go through forced ACL
	  i = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_ACCESS))
		{
			String accessDescription = "_"+Integer.toString(k);
			String accessOpName = "accessop"+accessDescription;
			String token = sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_TOKEN);
%>
			<tr>
				<td class="description" colspan="1">
					<input type="hidden" name='<%=accessOpName%>' value=""/>
					<input type="hidden" name='<%="spectoken"+accessDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(token)%>'/>
					<a name='<%="token_"+Integer.toString(k)%>'><input type="button" value="Delete" alt='<%="Delete token #"+Integer.toString(k)%>' onClick='<%="Javascript:SpecOp(\""+accessOpName+"\",\"Delete\",\"token_"+Integer.toString(k)+"\")"%>'/></a>
				</td>
				<td class="value" colspan="3">
					<nobr><%=com.metacarta.ui.util.Encoder.bodyEscape(token)%></nobr>
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
				<td class="message" colspan="4">No file access tokens present</td>
			</tr>
<%
	  }
%>
			<tr><td class="lightseparator" colspan="4"><hr/></td></tr>
			<tr>
				<td class="description" colspan="1">
					<input type="hidden" name="tokencount" value='<%=Integer.toString(k)%>'/>
					<input type="hidden" name="accessop" value=""/>
					<a name='<%="token_"+Integer.toString(k)%>'><input type="button" value="Add" alt="Add token" onClick='<%="Javascript:SpecAddToken(\"token_"+Integer.toString(k+1)+"\")"%>'/></a>
				</td>
				<td class="value" colspan="3">
					<nobr><input type="text" size="30" name="spectoken" value=""/></nobr>
				</td>
			</tr>

			<tr><td class="separator" colspan="4"><hr/></td></tr>

			<tr>
			    <td class="description"><nobr>Share security:</nobr></td>
			    <td colspan="3" class="value"><nobr>
				<input type="radio" name="specsharesecurity" value="on" <%=(shareSecurityOn)?"checked=\"true\"":""%> />Enabled&nbsp;
				<input type="radio" name="specsharesecurity" value="off" <%=(shareSecurityOn==false)?"checked=\"true\"":""%> />Disabled
			    </nobr></td>
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
		if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_ACCESS))
		{
			String accessDescription = "_"+Integer.toString(k);
			String token = sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_TOKEN);
%>
	<input type="hidden" name='<%="spectoken"+accessDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(token)%>'/>
<%
			k++;
		}
	  }
%>
	<input type="hidden" name="tokencount" value='<%=Integer.toString(k)%>'/>
	<input type="hidden" name="specsharesecurity" value='<%=(shareSecurityOn?"on":"off")%>'/>
<%
	}



	// Metadata tab

	// Find the path-value metadata attribute name
	// Find the path-value mapping data
	i = 0;
	String pathNameAttribute = "";
	com.metacarta.crawler.connectors.sharedrive.MatchMap matchMap = new com.metacarta.crawler.connectors.sharedrive.MatchMap();
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_PATHNAMEATTRIBUTE))
		{
			pathNameAttribute = sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_VALUE);
		}
		else if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_PATHMAP))
		{
			String pathMatch = sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_MATCH);
			String pathReplace = sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_REPLACE);
			matchMap.appendMatchPair(pathMatch,pathReplace);
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
			<td class="description" colspan="1"><nobr>Path attribute name:</nobr></td>
			<td class="value" colspan="3">
				<input type="text" name="specpathnameattribute" size="20" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(pathNameAttribute)%>'/>
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
			<td class="value"><input type="hidden" name='<%="specmappingop_"+Integer.toString(i)%>' value=""/><a name='<%="mapping_"+Integer.toString(i)%>'><input type="button" onClick='<%="Javascript:SpecOp(\"specmappingop_"+Integer.toString(i)+"\",\"Delete\",\"mapping_"+Integer.toString(i)+"\")"%>' alt='<%="Delete mapping #"+Integer.toString(i)%>' value="Delete"/></a></td>
			<td class="value"><input type="hidden" name='<%="specmatch_"+Integer.toString(i)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(matchString)%>'/><%=com.metacarta.ui.util.Encoder.bodyEscape(matchString)%></td>
			<td class="value">==></td>
			<td class="value"><input type="hidden" name='<%="specreplace_"+Integer.toString(i)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(replaceString)%>'/><%=com.metacarta.ui.util.Encoder.bodyEscape(replaceString)%></td>
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
			<td class="value"><a name='<%="mapping_"+Integer.toString(i)%>'><input type="button" onClick='<%="Javascript:SpecAddMapping(\"mapping_"+Integer.toString(i+1)+"\")"%>' alt="Add to mappings" value="Add"/></a></td>
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
	<input type="hidden" name="specpathnameattribute" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(pathNameAttribute)%>'/>
	<input type="hidden" name="specmappingcount" value='<%=Integer.toString(matchMap.getMatchCount())%>'/>
<%
	  i = 0;
	  while (i < matchMap.getMatchCount())
	  {
		String matchString = matchMap.getMatchString(i);
		String replaceString = matchMap.getReplaceString(i);
%>
	<input type="hidden" name='<%="specmatch_"+Integer.toString(i)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(matchString)%>'/>
	<input type="hidden" name='<%="specreplace_"+Integer.toString(i)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(replaceString)%>'/>
<%
		i++;
	  }
	}
	
	// File and URL Mapping tabs
	
	// Find the filename mapping data
	// Find the URL mapping data
	com.metacarta.crawler.connectors.sharedrive.MatchMap fileMap = new com.metacarta.crawler.connectors.sharedrive.MatchMap();
	com.metacarta.crawler.connectors.sharedrive.MatchMap uriMap = new com.metacarta.crawler.connectors.sharedrive.MatchMap();
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_FILEMAP))
		{
			String pathMatch = sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_MATCH);
			String pathReplace = sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_REPLACE);
			fileMap.appendMatchPair(pathMatch,pathReplace);
		}
		else if (sn.getType().equals(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.NODE_URIMAP))
		{
			String pathMatch = sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_MATCH);
			String pathReplace = sn.getAttributeValue(com.metacarta.crawler.connectors.sharedrive.SharedDriveConnector.ATTRIBUTE_REPLACE);
			uriMap.appendMatchPair(pathMatch,pathReplace);
		}
	}

	if (tabName.equals("File Mapping"))
	{
%>
	<input type="hidden" name="specfmapcount" value='<%=Integer.toString(fileMap.getMatchCount())%>'/>
	<input type="hidden" name="specfmapop" value=""/>
	<table class="displaytable">
		    <tr><td class="separator" colspan="4"><hr/></td></tr>
<%
	  i = 0;
	  while (i < fileMap.getMatchCount())
	  {
		String matchString = fileMap.getMatchString(i);
		String replaceString = fileMap.getReplaceString(i);
%>
		    <tr>
			<td class="value"><input type="hidden" name='<%="specfmapop_"+Integer.toString(i)%>' value=""/><a name='<%="fmap_"+Integer.toString(i)%>'><input type="button" onClick='<%="Javascript:SpecOp(\"specfmapop_"+Integer.toString(i)+"\",\"Delete\",\"fmap_"+Integer.toString(i)+"\")"%>' alt='<%="Delete file mapping #"+Integer.toString(i)%>' value="Delete"/></a></td>
			<td class="value"><input type="hidden" name='<%="specfmapmatch_"+Integer.toString(i)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(matchString)%>'/><%=com.metacarta.ui.util.Encoder.bodyEscape(matchString)%></td>
			<td class="value">==></td>
			<td class="value"><input type="hidden" name='<%="specfmapreplace_"+Integer.toString(i)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(replaceString)%>'/><%=com.metacarta.ui.util.Encoder.bodyEscape(replaceString)%></td>
		    </tr>
<%
		i++;
	  }
	  if (i == 0)
	  {
%>
		    <tr><td colspan="4" class="message">No file mappings specified</td></tr>
<%
	  }
%>
		    <tr><td class="lightseparator" colspan="4"><hr/></td></tr>

		    <tr>
			<td class="value"><a name='<%="fmap_"+Integer.toString(i)%>'><input type="button" onClick='<%="Javascript:SpecAddFMap(\"fmap_"+Integer.toString(i+1)+"\")"%>' alt="Add to file mappings" value="Add"/></a></td>
			<td class="value">Match regexp:&nbsp;<input type="text" name="specfmapmatch" size="32" value=""/></td>
			<td class="value">==></td>
			<td class="value">Replace string:&nbsp;<input type="text" name="specfmapreplace" size="32" value=""/></td>
		    </tr>
	</table>
<%
	}
	else
	{
%>
	<input type="hidden" name="specfmapcount" value='<%=Integer.toString(fileMap.getMatchCount())%>'/>
<%
	  i = 0;
	  while (i < fileMap.getMatchCount())
	  {
		String matchString = fileMap.getMatchString(i);
		String replaceString = fileMap.getReplaceString(i);
%>
	<input type="hidden" name='<%="specfmapmatch_"+Integer.toString(i)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(matchString)%>'/>
	<input type="hidden" name='<%="specfmapreplace_"+Integer.toString(i)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(replaceString)%>'/>
<%
		i++;
	  }
	}
	
	if (tabName.equals("URL Mapping"))
	{
%>
	<input type="hidden" name="specumapcount" value='<%=Integer.toString(uriMap.getMatchCount())%>'/>
	<input type="hidden" name="specumapop" value=""/>
	<table class="displaytable">
		    <tr><td class="separator" colspan="4"><hr/></td></tr>
<%
	  i = 0;
	  while (i < uriMap.getMatchCount())
	  {
		String matchString = uriMap.getMatchString(i);
		String replaceString = uriMap.getReplaceString(i);
%>
		    <tr>
			<td class="value"><input type="hidden" name='<%="specumapop_"+Integer.toString(i)%>' value=""/><a name='<%="umap_"+Integer.toString(i)%>'><input type="button" onClick='<%="Javascript:SpecOp(\"specumapop_"+Integer.toString(i)+"\",\"Delete\",\"umap_"+Integer.toString(i)+"\")"%>' alt='<%="Delete url mapping #"+Integer.toString(i)%>' value="Delete"/></a></td>
			<td class="value"><input type="hidden" name='<%="specumapmatch_"+Integer.toString(i)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(matchString)%>'/><%=com.metacarta.ui.util.Encoder.bodyEscape(matchString)%></td>
			<td class="value">==></td>
			<td class="value"><input type="hidden" name='<%="specumapreplace_"+Integer.toString(i)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(replaceString)%>'/><%=com.metacarta.ui.util.Encoder.bodyEscape(replaceString)%></td>
		    </tr>
<%
		i++;
	  }
	  if (i == 0)
	  {
%>
		    <tr><td colspan="4" class="message">No URL mappings specified; will produce a file IRI</td></tr>
<%
	  }
%>
		    <tr><td class="lightseparator" colspan="4"><hr/></td></tr>

		    <tr>
			<td class="value"><a name='<%="umap_"+Integer.toString(i)%>'><input type="button" onClick='<%="Javascript:SpecAddUMap(\"umap_"+Integer.toString(i+1)+"\")"%>' alt="Add to URL mappings" value="Add"/></a></td>
			<td class="value">Match regexp:&nbsp;<input type="text" name="specumapmatch" size="32" value=""/></td>
			<td class="value">==></td>
			<td class="value">Replace string:&nbsp;<input type="text" name="specumapreplace" size="32" value=""/></td>
		    </tr>
	</table>
<%
	}
	else
	{
%>
	<input type="hidden" name="specumapcount" value='<%=Integer.toString(uriMap.getMatchCount())%>'/>
<%
	  i = 0;
	  while (i < uriMap.getMatchCount())
	  {
		String matchString = uriMap.getMatchString(i);
		String replaceString = uriMap.getReplaceString(i);
%>
	<input type="hidden" name='<%="specumapmatch_"+Integer.toString(i)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(matchString)%>'/>
	<input type="hidden" name='<%="specumapreplace_"+Integer.toString(i)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(replaceString)%>'/>
<%
		i++;
	  }
	}
%>
