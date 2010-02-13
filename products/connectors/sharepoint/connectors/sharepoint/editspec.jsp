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
	int l;

	// Paths tab


	if (tabName.equals("Paths"))
	{
%>
	<table class="displaytable">
		<tr><td class="separator" colspan="2"><hr/></td></tr>
		<tr>
			<td class="description"><nobr>Path rules:</nobr></td>
			<td class="boxcell">
				<table class="formtable">
					<tr class="formheaderrow">
						<td class="formcolumnheader"></td>
						<td class="formcolumnheader"><nobr>Path match</nobr></td>
						<td class="formcolumnheader"><nobr>Type</nobr></td>
						<td class="formcolumnheader"><nobr>Action</nobr></td>
					</tr>
<%
	  i = 0;
	  l = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
	    SpecificationNode sn = ds.getChild(i++);
	    if (sn.getType().equals("startpoint"))
	    {
		String site = sn.getAttributeValue("site");
		String lib = sn.getAttributeValue("lib");
		String siteLib = site + "/" + lib + "/";

		// Go through all the file/folder rules for the startpoint, and generate new "rules" corresponding to each.
		int j = 0;
		while (j < sn.getChildCount())
		{
			SpecificationNode node = sn.getChild(j++);
			if (node.getType().equals("include") || node.getType().equals("exclude"))
			{
				String matchPart = node.getAttributeValue("match");
				String ruleType = node.getAttributeValue("type");
				
				String theFlavor = node.getType();

				String pathDescription = "_"+Integer.toString(k);
				String pathOpName = "specop"+pathDescription;
				String thePath = siteLib + matchPart;

%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr>
							<a name='<%="path_"+Integer.toString(k)%>'/>
							<input type="hidden" name='<%=pathOpName%>' value=""/>
							<input type="button" value="Insert New Rule" onClick='<%="Javascript:SpecOp(\""+
								pathOpName+"\",\"Insert Here\",\"path_"+Integer.toString(k)+"\")"%>' alt='<%="Insert new rule before rule #"+
								Integer.toString(k)%>'/></nobr>
						</td>
						<td class="formcolumncell" colspan="3"></td>
					</tr>
<%
				l++;
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr>
							<input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+
								pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")"%>' alt='<%="Delete rule #"+Integer.toString(k)%>'/></nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="specpath"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(thePath)%>'/>
							<%=com.metacarta.ui.util.Encoder.bodyEscape(thePath)%></nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="spectype"+pathDescription%>' value="file"/>
							file</nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="specflav"+pathDescription%>' value='<%=theFlavor%>'/>
							<%=theFlavor%></nobr>
						</td>
					</tr>
<%
				l++;
				k++;
				if (ruleType.equals("file") && !matchPart.startsWith("*"))
				{
					// Generate another rule corresponding to all matching paths.
					pathDescription = "_"+Integer.toString(k);
					pathOpName = "specop"+pathDescription;

					thePath = siteLib + "*/" + matchPart;
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr>
							<a name='<%="path_"+Integer.toString(k)%>'/>
							<input type="hidden" name='<%=pathOpName%>' value=""/>
							<input type="button" value="Insert New Rule" onClick='<%="Javascript:SpecOp(\""+
								pathOpName+"\",\"Insert Here\",\"path_"+Integer.toString(k)+"\")"%>' alt='<%="Insert new rule before rule #"+
								Integer.toString(k)%>'/></nobr>
						</td>
						<td class="formcolumncell" colspan="3"></td>
					</tr>
<%
					l++;
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr>
							<input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+
								pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")"%>' alt='<%="Delete rule #"+Integer.toString(k)%>'/></nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="specpath"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(thePath)%>'/>
							<%=com.metacarta.ui.util.Encoder.bodyEscape(thePath)%></nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="spectype"+pathDescription%>' value="file"/>
							file</nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="specflav"+pathDescription%>' value='<%=theFlavor%>'/>
							<%=theFlavor%></nobr>
						</td>
					</tr>
<%
					l++;
						
					k++;
				}
			}
		}
	    }
	    else if (sn.getType().equals("pathrule"))
	    {
		String match = sn.getAttributeValue("match");
		String type = sn.getAttributeValue("type");
		String action = sn.getAttributeValue("action");
		
		String pathDescription = "_"+Integer.toString(k);
		String pathOpName = "specop"+pathDescription;

%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr>
							<a name='<%="path_"+Integer.toString(k)%>'/>
							<input type="hidden" name='<%=pathOpName%>' value=""/>
							<input type="button" value="Insert New Rule" onClick='<%="Javascript:SpecOp(\""+
								pathOpName+"\",\"Insert Here\",\"path_"+Integer.toString(k)+"\")"%>' alt='<%="Insert new rule before rule #"+
								Integer.toString(k)%>'/></nobr>
						</td>
						<td class="formcolumncell" colspan="3"></td>
					</tr>
<%
		l++;
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr>
							<input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+
								pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")"%>' alt='<%="Delete rule #"+Integer.toString(k)%>'/></nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="specpath"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(match)%>'/>
							<%=com.metacarta.ui.util.Encoder.bodyEscape(match)%></nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="spectype"+pathDescription%>' value='<%=type%>'/>
							<%=type%></nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="specflav"+pathDescription%>' value='<%=action%>'/>
							<%=action%></nobr>
						</td>
					</tr>
<%
		l++;
		k++;
	    }
	  }
	  if (k == 0)
	  {
%>
					<tr class="formrow"><td colspan="4" class="formmessage">No documents currently included</td></tr>
<%
	  }
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr>
							<a name='<%="path_"+Integer.toString(k)%>'/>
							<input type="hidden" name="specop" value=""/>
							<input type="hidden" name="specpathcount" value='<%=Integer.toString(k)%>'/>
							<input type="button" value="Add New Rule" onClick='<%="Javascript:SpecRuleAddPath(\"path_"+Integer.toString(k)+"\")"%>' alt="Add rule"/></nobr>
						</td>
						<td class="formcolumncell" colspan="3"></td>
					</tr>
					<tr class="formrow"><td colspan="4" class="formseparator"><hr/></td></tr>
					<tr class="formrow">
						<td class="formcolumncell"><nobr>New rule:</nobr>
<%
	  // The following variables may be in the thread context because postspec.jsp put them there:
	  // (1) "specpath", which contains the rule path as it currently stands;
	  // (2) "specpathstate", which describes what the current path represents.  Values are "unknown", "site", "library".
	  // Once the widget is in the state "unknown", it can only be reset, and cannot be further modified
	  // specsitepath may be in the thread context, put there by postspec.jsp 
	  String pathSoFar = (String)threadContext.get("specpath");
	  String pathState = (String)threadContext.get("specpathstate");
	  String pathLibrary = (String)threadContext.get("specpathlibrary");
	  if (pathState == null)
	  {
		pathState = "unknown";
		pathLibrary = null;
	  }
	  if (pathSoFar == null)
	  {
		pathSoFar = "/";
		pathState = "site";
		pathLibrary = null;
	  }

	  // Grab next site list and lib list
	  ArrayList childSiteList = null;
	  ArrayList childLibList = null;
	  String message = null;
	  if (pathState.equals("site"))
	  {
		try
		{
			IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext,
				repositoryConnection.getClassName(),
				repositoryConnection.getConfigParams(),
				repositoryConnection.getMaxConnections());
			try
			{
				com.metacarta.crawler.connectors.sharepoint.SharePointRepository c = (com.metacarta.crawler.connectors.sharepoint.SharePointRepository)connector;
				String queryPath = pathSoFar;
				if (queryPath.equals("/"))
					queryPath = "";
				childSiteList = c.getSites(queryPath);
				if (childSiteList == null)
				{
					// Illegal path - state becomes "unknown".
					pathState = "unknown";
					pathLibrary = null;
				}
				childLibList = c.getDocLibsBySite(queryPath);
				if (childLibList == null)
				{
					// Illegal path - state becomes "unknown"
					pathState = "unknown";
					pathLibrary = null;
				}
			}
			finally
			{
				RepositoryConnectorFactory.release(connector);
			}
		}
		catch (MetacartaException e)
		{
			e.printStackTrace();
			message = e.getMessage();
		}
		catch (ServiceInterruption e)
		{
			message = "SharePoint unavailable: "+e.getMessage();
		}
	  }
	  
%>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name="specpathop" value=""/>
							<input type="hidden" name="specpath" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(pathSoFar)%>'/>
							<input type="hidden" name="specpathstate" value='<%=pathState%>'/>
<%
	  if (pathLibrary != null)
	  {
%>
							<input type="hidden" name="specpathlibrary" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(pathLibrary)%>'/>
<%
	  }
%>
							<%=com.metacarta.ui.util.Encoder.bodyEscape(pathSoFar)%></nobr>
						</td>
						<td class="formcolumncell">
						    <nobr>
<%
	  if (pathState.equals("unknown"))
	  {
		if (pathLibrary == null)
		{
%>
							<select name="spectype" size="3">
								<option value="file" selected="true">File</option>
								<option value="library">Library</option>
								<option value="site">Site</option>
							</select>
<%
		}
		else
		{
%>
							<input type="hidden" name="spectype" value="file"/>
							file
<%
		}
	  }
	  else
	  {
%>
							<input type="hidden" name="spectype" value='<%=pathState%>'/>
							<%=pathState%>
<%
	  }
%>
						    </nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<select name="specflavor" size="2">
								<option value="include" selected="true">Include</option>
								<option value="exclude">Exclude</option>
							</select></nobr>
						</td>
					</tr>
					<tr class="formrow"><td colspan="4" class="formseparator"><hr/></td></tr>
					<tr class="formrow">
<%
	  if (message != null)
	  {
		// Display the error message, with no widgets
%>
						<td class="formmessage" colspan="4"><%=com.metacarta.ui.util.Encoder.bodyEscape(message)%></td>
<%
	  }
	  else
	  {
		// What we display depends on the determined state of the path.  If the path is a library or is unknown, all we can do is allow a type-in to append
		// to it, or allow a reset.  If the path is a site, then we can optionally display libraries, sites, OR allow a type-in.
		// The path buttons are on the left; they consist of "Reset" (to reset the path), "+" (to add to the path), and "-" (to remove from the path).
%>
						<td class="formcolumncell">
						    <nobr>
							<a name="pathwidget"/>
							<input type="button" value="Reset Path" onClick='Javascript:SpecPathReset("pathwidget")' alt="Reset Rule Path"/>
<%
		if (pathSoFar.length() > 1 && (pathState.equals("site") || pathState.equals("library")))
		{
%>
							<input type="button" value="-" onClick='Javascript:SpecPathRemove("pathwidget")' alt="Remove from Rule Path"/>
<%
		}
%>
						    </nobr>
						</td>
						<td class="formcolumncell" colspan="3">
						    <nobr>
<%
		if (pathState.equals("site") && childSiteList != null && childSiteList.size() > 0)
		{
%>
							<input type="button" value="Add Site" onClick='Javascript:SpecPathAppendSite("pathwidget")' alt="Add Site to Rule Path"/>
							<select name="specsite" size="5">
								<option value="" selected="true">-- Select site --</option>
<%
			int q = 0;
			while (q < childSiteList.size())
			{
				com.metacarta.crawler.connectors.sharepoint.NameValue childSite = (com.metacarta.crawler.connectors.sharepoint.NameValue)childSiteList.get(q++);
%>
								<option value='<%=com.metacarta.ui.util.Encoder.attributeEscape(childSite.getValue())%>'><%=com.metacarta.ui.util.Encoder.bodyEscape(childSite.getPrettyName())%></option>
<%
			}
%>
							</select>
<%
		}
		
		if (pathState.equals("site") && childLibList != null && childLibList.size() > 0)
		{
%>
							<input type="button" value="Add Library" onClick='Javascript:SpecPathAppendLibrary("pathwidget")' alt="Add Library to Rule Path"/>
							<select name="speclibrary" size="5">
								<option value="" selected="true">-- Select library --</option>
<%
			int q = 0;
			while (q < childLibList.size())
			{
				com.metacarta.crawler.connectors.sharepoint.NameValue childLib = (com.metacarta.crawler.connectors.sharepoint.NameValue)childLibList.get(q++);
%>
								<option value='<%=com.metacarta.ui.util.Encoder.attributeEscape(childLib.getValue())%>'><%=com.metacarta.ui.util.Encoder.bodyEscape(childLib.getPrettyName())%></option>
<%
			}
%>
							</select>
<%
		}
%>
							<input type="button" value="Add Text" onClick='Javascript:SpecPathAppendText("pathwidget")' alt="Add Text to Rule Path"/>
							<input type="text" name="specmatch" size="32" value=""/>
						    </nobr>
						</td>
<%
	  }
%>
					</tr>
				</table>
			</td>
		</tr>
	</table>
<%
	}
	else
	{
	  // Hiddens for path rules
	  i = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
	    SpecificationNode sn = ds.getChild(i++);
	    if (sn.getType().equals("startpoint"))
	    {
		String site = sn.getAttributeValue("site");
		String lib = sn.getAttributeValue("lib");
		String siteLib = site + "/" + lib + "/";

		// Go through all the file/folder rules for the startpoint, and generate new "rules" corresponding to each.
		int j = 0;
		while (j < sn.getChildCount())
		{
			SpecificationNode node = sn.getChild(j++);
			if (node.getType().equals("include") || node.getType().equals("exclude"))
			{
				String matchPart = node.getAttributeValue("match");
				String ruleType = node.getAttributeValue("type");
				
				String theFlavor = node.getType();

				String pathDescription = "_"+Integer.toString(k);
				
				String thePath = siteLib + matchPart;
%>
	<input type="hidden" name='<%="specpath"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(thePath)%>'/>
	<input type="hidden" name='<%="spectype"+pathDescription%>' value="file"/>
	<input type="hidden" name='<%="specflav"+pathDescription%>' value='<%=theFlavor%>'/>
<%
				k++;

				if (ruleType.equals("file") && !matchPart.startsWith("*"))
				{
					// Generate another rule corresponding to all matching paths.
					pathDescription = "_"+Integer.toString(k);

					thePath = siteLib + "*/" + matchPart;
%>
	<input type="hidden" name='<%="specpath"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(thePath)%>'/>
	<input type="hidden" name='<%="spectype"+pathDescription%>' value="file"/>
	<input type="hidden" name='<%="specflav"+pathDescription%>' value='<%=theFlavor%>'/>
<%
					k++;
				}
			}
		}
	    }
	    else if (sn.getType().equals("pathrule"))
	    {
		String match = sn.getAttributeValue("match");
		String type = sn.getAttributeValue("type");
		String action = sn.getAttributeValue("action");
		
		String pathDescription = "_"+Integer.toString(k);

%>
	<input type="hidden" name='<%="specpath"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(match)%>'/>
	<input type="hidden" name='<%="spectype"+pathDescription%>' value='<%=type%>'/>
	<input type="hidden" name='<%="specflav"+pathDescription%>' value='<%=action%>'/>
<%
		k++;
	    }
	  }
%>
	<input type="hidden" name="specpathcount" value='<%=Integer.toString(k)%>'/>
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
		    <td class="value" colspan="1">
			<nobr><input type="radio" name="specsecurity" value="on" <%=(securityOn)?"checked=\"true\"":""%> />Enabled&nbsp;
			<input type="radio" name="specsecurity" value="off" <%=(securityOn==false)?"checked=\"true\"":""%> />Disabled</nobr>
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
				<a name='<%="token_"+Integer.toString(k)%>'><input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+accessOpName+"\",\"Delete\",\"token_"+Integer.toString(k)+"\")"%>' alt='<%="Delete token #"+Integer.toString(k)%>'/></a>
			</td>
			<td class="value">
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
				<a name='<%="token_"+Integer.toString(k)%>'><input type="button" value="Add" onClick='<%="Javascript:SpecAddAccessToken(\"token_"+Integer.toString(k+1)+"\")"%>' alt="Add access token"/></a>
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
	com.metacarta.crawler.connectors.sharepoint.MatchMap matchMap = new com.metacarta.crawler.connectors.sharepoint.MatchMap();
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

	if (tabName.equals("Metadata"))
	{
%>
	<input type="hidden" name="specmappingcount" value='<%=Integer.toString(matchMap.getMatchCount())%>'/>
	<input type="hidden" name="specmappingop" value=""/>

	<table class="displaytable">
		<tr><td class="separator" colspan="4"><hr/></td></tr>
		<tr>
			<td class="description" colspan="1"><nobr>Metadata rules:</nobr></td>
			<td class="boxcell" colspan="3">
				<table class="formtable">
					<tr class="formheaderrow">
						<td class="formcolumnheader"></td>
						<td class="formcolumnheader"><nobr>Path match</nobr></td>
						<td class="formcolumnheader"><nobr>Action</nobr></td>
						<td class="formcolumnheader"><nobr>All metadata?</nobr></td>
						<td class="formcolumnheader"><nobr>Fields</nobr></td>
					</tr>
<%
	  i = 0;
	  l = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
	    SpecificationNode sn = ds.getChild(i++);
	    if (sn.getType().equals("startpoint"))
	    {
		String site = sn.getAttributeValue("site");
		String lib = sn.getAttributeValue("lib");
		String path = site + "/" + lib + "/*";
		String allmetadata = sn.getAttributeValue("allmetadata");
		StringBuffer metadataFieldList = new StringBuffer();
		ArrayList metadataFieldArray = new ArrayList();
		if (allmetadata == null || !allmetadata.equals("true"))
		{
			int j = 0;
			while (j < sn.getChildCount())
			{
				SpecificationNode node = sn.getChild(j++);
				if (node.getType().equals("metafield"))
				{
					if (metadataFieldList.length() > 0)
						metadataFieldList.append(", ");
					String val = node.getAttributeValue("value");
					metadataFieldList.append(val);
					metadataFieldArray.add(val);
				}
			}
			allmetadata = "false";
		}
		
		if (allmetadata.equals("true") || metadataFieldList.length() > 0)
		{
			String pathDescription = "_"+Integer.toString(k);
			String pathOpName = "metaop"+pathDescription;

%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr>
							<a name='<%="meta_"+Integer.toString(k)%>'/>
							<input type="hidden" name='<%=pathOpName%>' value=""/>
							<input type="button" value="Insert New Rule" onClick='<%="Javascript:SpecOp(\""+
								pathOpName+"\",\"Insert Here\",\"meta_"+Integer.toString(k)+"\")"%>' alt='<%="Insert new metadata rule before rule #"+
								Integer.toString(k)%>'/></nobr>
						</td>
						<td class="formcolumncell" colspan="4"></td>
					</tr>
<%
			l++;
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr>
							<input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+
								pathOpName+"\",\"Delete\",\"meta_"+Integer.toString(k)+"\")"%>' alt='<%="Delete metadata rule #"+Integer.toString(k)%>'/></nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="metapath"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(path)%>'/>
							<%=com.metacarta.ui.util.Encoder.bodyEscape(path)%></nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="metaflav"+pathDescription%>' value="include"/>
							include</nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="metaall"+pathDescription%>' value='<%=allmetadata%>'/>
							<%=allmetadata%></nobr>
						</td>
						<td class="formcolumncell">
<%
			int q = 0;
			while (q < metadataFieldArray.size())
			{
				String field = (String)metadataFieldArray.get(q++);
%>
							<input type="hidden" name='<%="metafields"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(field)%>'/>
<%
			}
%>
							<%=com.metacarta.ui.util.Encoder.bodyEscape(metadataFieldList.toString())%>
						</td>
					</tr>
<%
			l++;
			k++;
		}
	    }
	    else if (sn.getType().equals("metadatarule"))
	    {
		String path = sn.getAttributeValue("match");
		String action = sn.getAttributeValue("action");
		String allmetadata = sn.getAttributeValue("allmetadata");
		StringBuffer metadataFieldList = new StringBuffer();
		ArrayList metadataFieldArray = new ArrayList();
		if (action.equals("include"))
		{
			if (allmetadata == null || !allmetadata.equals("true"))
			{
				int j = 0;
				while (j < sn.getChildCount())
				{
					SpecificationNode node = sn.getChild(j++);
					if (node.getType().equals("metafield"))
					{
						String val = node.getAttributeValue("value");
						if (metadataFieldList.length() > 0)
							metadataFieldList.append(", ");
						metadataFieldList.append(val);
						metadataFieldArray.add(val);
					}
				}
				allmetadata="false";
			}
		}
		else
			allmetadata = "";
		
		String pathDescription = "_"+Integer.toString(k);
		String pathOpName = "metaop"+pathDescription;

%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr>
							<a name='<%="meta_"+Integer.toString(k)%>'/>
							<input type="hidden" name='<%=pathOpName%>' value=""/>
							<input type="button" value="Insert New Rule" onClick='<%="Javascript:SpecOp(\""+
								pathOpName+"\",\"Insert Here\",\"meta_"+Integer.toString(k)+"\")"%>' alt='<%="Insert new metadata rule before rule #"+
								Integer.toString(k)%>'/></nobr>
						</td>
						<td class="formcolumncell" colspan="4"></td>
					</tr>
<%
		l++;
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr>
							<input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+
								pathOpName+"\",\"Delete\",\"meta_"+Integer.toString(k)+"\")"%>' alt='<%="Delete metadata rule #"+Integer.toString(k)%>'/></nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="metapath"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(path)%>'/>
							<%=com.metacarta.ui.util.Encoder.bodyEscape(path)%></nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="metaflav"+pathDescription%>' value='<%=action%>'/>
							<%=action%></nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name='<%="metaall"+pathDescription%>' value='<%=allmetadata%>'/>
							<%=allmetadata%></nobr>
						</td>
						<td class="formcolumncell">
<%
			int q = 0;
			while (q < metadataFieldArray.size())
			{
				String field = (String)metadataFieldArray.get(q++);
%>
							<input type="hidden" name='<%="metafields"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(field)%>'/>
<%
			}
%>
							<%=com.metacarta.ui.util.Encoder.bodyEscape(metadataFieldList.toString())%>
						</td>
					</tr>
<%
		l++;
		k++;

	    }
	  }
	  
	  if (k == 0)
	  {
%>
					<tr class="formrow"><td class="formmessage" colspan="5">No metadata included</td></tr>
<%
	  }
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell"><nobr>
							<a name='<%="meta_"+Integer.toString(k)%>'/>
							<input type="hidden" name="metaop" value=""/>
							<input type="hidden" name="metapathcount" value='<%=Integer.toString(k)%>'/>
							<input type="button" value="Add New Rule" onClick='<%="Javascript:MetaRuleAddPath(\"meta_"+Integer.toString(k)+"\")"%>' alt="Add rule"/></nobr>
						</td>
						<td class="formcolumncell" colspan="4"></td>
					</tr>
					<tr class="formrow"><td colspan="5" class="formseparator"><hr/></td></tr>
					<tr class="formrow">
						<td class="formcolumncell"><nobr>New rule:</nobr>
<%
	  // The following variables may be in the thread context because postspec.jsp put them there:
	  // (1) "metapath", which contains the rule path as it currently stands;
	  // (2) "metapathstate", which describes what the current path represents.  Values are "unknown", "site", "library".
	  // (3) "metapathlibrary" is the library path (if this is known yet).
	  // Once the widget is in the state "unknown", it can only be reset, and cannot be further modified
	  String metaPathSoFar = (String)threadContext.get("metapath");
	  String metaPathState = (String)threadContext.get("metapathstate");
	  String metaPathLibrary = (String)threadContext.get("metapathlibrary");
	  if (metaPathState == null)
		metaPathState = "unknown";
	  if (metaPathSoFar == null)
	  {
		metaPathSoFar = "/";
		metaPathState = "site";
	  }

	  String message = null;
	  String[] fields = null;
	  if (metaPathLibrary != null)
	  {
		// Look up metadata fields
		int index = metaPathLibrary.lastIndexOf("/");
		String site = metaPathLibrary.substring(0,index);
		String lib = metaPathLibrary.substring(index+1);
		Map metaFieldList = null;
	  	try
	  	{
		    IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext,
			repositoryConnection.getClassName(),
			repositoryConnection.getConfigParams(),
			repositoryConnection.getMaxConnections());
		    try
		    {
			com.metacarta.crawler.connectors.sharepoint.SharePointRepository c = (com.metacarta.crawler.connectors.sharepoint.SharePointRepository)connector;
			metaFieldList = c.getFieldList(site,lib);
		    }
		    finally
		    {
			RepositoryConnectorFactory.release(connector);
		    }
		}
		catch (MetacartaException e)
		{
			e.printStackTrace();
			message = e.getMessage();
		}
		catch (ServiceInterruption e)
		{
			message = "SharePoint unavailable: "+e.getMessage();
		}
		if (metaFieldList != null)
		{
			fields = new String[metaFieldList.size()];
			int j = 0;
			Iterator iter = metaFieldList.keySet().iterator();
			while (iter.hasNext())
			{
				fields[j++] = (String)iter.next();
			}
			java.util.Arrays.sort(fields);
		}
	  }
	  
	  // Grab next site list and lib list
	  ArrayList childSiteList = null;
	  ArrayList childLibList = null;

	  if (message == null && metaPathState.equals("site"))
	  {
		try
		{
			IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext,
				repositoryConnection.getClassName(),
				repositoryConnection.getConfigParams(),
				repositoryConnection.getMaxConnections());
			try
			{
				com.metacarta.crawler.connectors.sharepoint.SharePointRepository c = (com.metacarta.crawler.connectors.sharepoint.SharePointRepository)connector;
				String queryPath = metaPathSoFar;
				if (queryPath.equals("/"))
					queryPath = "";
				childSiteList = c.getSites(queryPath);
				if (childSiteList == null)
				{
					// Illegal path - state becomes "unknown".
					metaPathState = "unknown";
				}
				childLibList = c.getDocLibsBySite(queryPath);
				if (childLibList == null)
				{
					// Illegal path - state becomes "unknown"
					metaPathState = "unknown";
				}
			}
			finally
			{
				RepositoryConnectorFactory.release(connector);
			}
		}
		catch (MetacartaException e)
		{
			e.printStackTrace();
			message = e.getMessage();
		}
		catch (ServiceInterruption e)
		{
			message = "SharePoint unavailable: "+e.getMessage();
		}
	  }

%>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="hidden" name="metapathop" value=""/>
							<input type="hidden" name="metapath" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(metaPathSoFar)%>'/>
							<input type="hidden" name="metapathstate" value='<%=metaPathState%>'/>
<%
	  if (metaPathLibrary != null)
	  {
%>
							<input type="hidden" name="metapathlibrary" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(metaPathLibrary)%>'/>
<%
	  }
%>
							<%=com.metacarta.ui.util.Encoder.bodyEscape(metaPathSoFar)%></nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<select name="metaflavor" size="2">
								<option value="include" selected="true">Include</option>
								<option value="exclude">Exclude</option>
							</select></nobr>
						</td>
						<td class="formcolumncell"><nobr>
							<input type="checkbox" name="metaall" value="true"/> Include all metadata
							</nobr>
						</td>
						<td class="formcolumncell">
						    <nobr>
<%
	  if (fields != null && fields.length > 0)
	  {
%>
							<select name="metafields" multiple="true" size="5">
<%
		int q = 0;
		while (q < fields.length)
		{
			String field = fields[q++];
%>
								<option value='<%=com.metacarta.ui.util.Encoder.attributeEscape(field)%>'/><%=com.metacarta.ui.util.Encoder.bodyEscape(field)%></option>
<%
		}
%>
							</select>
<%
	  }
%>
						    </nobr>
						</td>
					</tr>
					<tr class="formrow"><td colspan="5" class="formseparator"><hr/></td></tr>
					<tr class="formrow">
<%
	  if (message != null)
	  {
%>
						<td class="formmessage" colspan="5"><%=com.metacarta.ui.util.Encoder.bodyEscape(message)%></td></tr>
<%
	  }
	  else
	  {
		// What we display depends on the determined state of the path.  If the path is a library or is unknown, all we can do is allow a type-in to append
		// to it, or allow a reset.  If the path is a site, then we can optionally display libraries, sites, OR allow a type-in.
		// The path buttons are on the left; they consist of "Reset" (to reset the path), "+" (to add to the path), and "-" (to remove from the path).
%>
						<td class="formcolumncell">
						    <nobr>
							<a name="metapathwidget"/>
							<input type="button" value="Reset Path" onClick='Javascript:MetaPathReset("metapathwidget")' alt="Reset Metadata Rule Path"/>
<%
		if (metaPathSoFar.length() > 1 && (metaPathState.equals("site") || metaPathState.equals("library")))
		{
%>
							<input type="button" value="-" onClick='Javascript:MetaPathRemove("metapathwidget")' alt="Remove from Metadata Rule Path"/>
<%
		}
%>
						    </nobr>
						</td>
						<td class="formcolumncell" colspan="4">
						    <nobr>
<%
		if (metaPathState.equals("site") && childSiteList != null && childSiteList.size() > 0)
		{
%>
							<input type="button" value="Add Site" onClick='Javascript:MetaPathAppendSite("metapathwidget")' alt="Add Site to Metadata Rule Path"/>
							<select name="metasite" size="5">
								<option value="" selected="true">-- Select site --</option>
<%
			int q = 0;
			while (q < childSiteList.size())
			{
				com.metacarta.crawler.connectors.sharepoint.NameValue childSite = (com.metacarta.crawler.connectors.sharepoint.NameValue)childSiteList.get(q++);
%>
								<option value='<%=com.metacarta.ui.util.Encoder.attributeEscape(childSite.getValue())%>'><%=com.metacarta.ui.util.Encoder.bodyEscape(childSite.getPrettyName())%></option>
<%
			}
%>
							</select>
<%
		}
		
		if (metaPathState.equals("site") && childLibList != null && childLibList.size() > 0)
		{
%>
							<input type="button" value="Add Library" onClick='Javascript:MetaPathAppendLibrary("metapathwidget")' alt="Add Library to Metadata Rule Path"/>
							<select name="metalibrary" size="5">
								<option value="" selected="true">-- Select library --</option>
<%
			int q = 0;
			while (q < childLibList.size())
			{
				com.metacarta.crawler.connectors.sharepoint.NameValue childLib = (com.metacarta.crawler.connectors.sharepoint.NameValue)childLibList.get(q++);
%>
								<option value='<%=com.metacarta.ui.util.Encoder.attributeEscape(childLib.getValue())%>'><%=com.metacarta.ui.util.Encoder.bodyEscape(childLib.getPrettyName())%></option>
<%
			}
%>
							</select>
<%
		}
%>
							<input type="button" value="Add Text" onClick='Javascript:MetaPathAppendText("metapathwidget")' alt="Add Text to Metadata Rule Path"/>
							<input type="text" name="metamatch" size="32" value=""/>
						    </nobr>
						</td>
<%
	  }
%>
					</tr>
				</table>
			</td>
		</tr>
		<tr><td class="separator" colspan="4"><hr/></td></tr>
		<tr>
			<td class="description" colspan="1"><nobr>Path metadata:</nobr></td>
			<td class="boxcell" colspan="3">
				<table class="displaytable">
					<tr>
						<td class="description"><nobr>Attribute name:</nobr></td>
						<td class="value" colspan="3"><nobr>
							<input type="text" name="specpathnameattribute" size="20" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(pathNameAttribute)%>'/></nobr>
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
						<td class="description"><input type="hidden" name='<%="specmappingop_"+Integer.toString(i)%>' value=""/><a name='<%="mapping_"+Integer.toString(i)%>'><input type="button" 
							onClick='<%="Javascript:SpecOp(\"specmappingop_"+Integer.toString(i)+"\",\"Delete\",\"mapping_"+Integer.toString(i)+"\")"%>' alt='<%="Delete mapping #"+Integer.toString(i)%>' value="Delete Path Mapping"/></a></td>
						<td class="value"><nobr><input type="hidden" name='<%="specmatch_"+Integer.toString(i)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(matchString)%>'/><%=com.metacarta.ui.util.Encoder.bodyEscape(matchString)%></nobr></td>
						<td class="value">==></td>
						<td class="value"><nobr><input type="hidden" name='<%="specreplace_"+Integer.toString(i)%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(replaceString)%>'/><%=com.metacarta.ui.util.Encoder.bodyEscape(replaceString)%></nobr></td>
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
						<td class="description"><a name='<%="mapping_"+Integer.toString(i)%>'><input type="button" onClick='<%="Javascript:SpecAddMapping(\"mapping_"+Integer.toString(i+1)+"\")"%>' alt="Add to mappings" value="Add Path Mapping"/></a></td>
						<td class="value"><nobr>Match regexp:&nbsp;<input type="text" name="specmatch" size="32" value=""/></nobr></td>
						<td class="value">==></td>
						<td class="value"><nobr>Replace string:&nbsp;<input type="text" name="specreplace" size="32" value=""/></nobr></td>
					</tr>
				</table>
			</td>
		</tr>
	</table>
<%
	}
	else
	{
	  // Hiddens for metadata rules
	  i = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
	    SpecificationNode sn = ds.getChild(i++);
	    if (sn.getType().equals("startpoint"))
	    {
		String site = sn.getAttributeValue("site");
		String lib = sn.getAttributeValue("lib");
		String path = site + "/" + lib + "/*";
		
		String allmetadata = sn.getAttributeValue("allmetadata");
		ArrayList metadataFieldArray = new ArrayList();
		if (allmetadata == null || !allmetadata.equals("true"))
		{
			int j = 0;
			while (j < sn.getChildCount())
			{
				SpecificationNode node = sn.getChild(j++);
				if (node.getType().equals("metafield"))
				{
					String val = node.getAttributeValue("value");
					metadataFieldArray.add(val);
				}
			}
			allmetadata = "false";
		}
		
		if (allmetadata.equals("true") || metadataFieldArray.size() > 0)
		{
			String pathDescription = "_"+Integer.toString(k);

%>
	<input type="hidden" name='<%="metapath"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(path)%>'/>
	<input type="hidden" name='<%="metaflav"+pathDescription%>' value="include"/>
	<input type="hidden" name='<%="metaall"+pathDescription%>' value='<%=allmetadata%>'/>
<%
			int q = 0;
			while (q < metadataFieldArray.size())
			{
				String field = (String)metadataFieldArray.get(q++);
%>
	<input type="hidden" name='<%="metafields"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(field)%>'/>
<%
			}
			k++;
		}
	    }
	    else if (sn.getType().equals("metadatarule"))
	    {
		String match = sn.getAttributeValue("match");
		String action = sn.getAttributeValue("action");
		String allmetadata = sn.getAttributeValue("allmetadata");
		
		String pathDescription = "_"+Integer.toString(k);

%>
	<input type="hidden" name='<%="metapath"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(match)%>'/>
	<input type="hidden" name='<%="metaflav"+pathDescription%>' value='<%=action%>'/>
<%
		if (action.equals("include"))
		{
			if (allmetadata == null || allmetadata.length() == 0)
				allmetadata = "false";
%>
	<input type="hidden" name='<%="metaall"+pathDescription%>' value='<%=allmetadata%>'/>
<%
			int j = 0;
			while (j < sn.getChildCount())
			{
				SpecificationNode node = sn.getChild(j++);
				if (node.getType().equals("metafield"))
				{
					String value = node.getAttributeValue("value");
%>
	<input type="hidden" name='<%="metafields"+pathDescription%>' value='<%=com.metacarta.ui.util.Encoder.attributeEscape(value)%>'/>
<%
					
				}
			}
		}
		k++;
	    }
	  }
%>
	<input type="hidden" name="metapathcount" value='<%=Integer.toString(k)%>'/>

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
%>
