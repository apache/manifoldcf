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
  //org.apache.lcf.crawler.connectors.DCTM.DCTM repositoryConnection = (org.apache.lcf.crawler.connectors.DCTM.DCTM)threadContext.get("RepositoryConnection");
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
    if (sn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_LOCATION))
    {
      String pathDescription = "_" + Integer.toString(k);
      String pathOpName = "pathop" + pathDescription;
%>
	  <tr>
		<td class="description">
			<input type="hidden" name='<%="specpath"+pathDescription%>' value='<%=sn.getAttributeValue("path")%>'/>
			<input type="hidden" name='<%=pathOpName%>' value=""/>
			<a name='<%="path_"+Integer.toString(k)%>'><input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")"%>' alt='<%="Delete path #"+Integer.toString(k)%>'/></a>&nbsp;
		</td>
		<td class="value">
			<%=org.apache.lcf.ui.util.Encoder.bodyEscape(sn.getAttributeValue("path"))%>
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
		<td class="message" colspan="2">No specific cabinet/folder paths given (everything in docbase will be scanned)</td>
	  </tr>
<%
   }
%>
	  <tr>
		<td class="description">
		    <input type="hidden" name="pathcount" value='<%=Integer.toString(k)%>'/>
<%
	
	String pathSoFar = (String)threadContext.get("specpath");
	if (pathSoFar == null)
		pathSoFar = "/";

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
		org.apache.lcf.crawler.connectors.DCTM.DCTM c = (org.apache.lcf.crawler.connectors.DCTM.DCTM)connector;
		childList = c.getChildFolderNames(pathSoFar);
		if (childList == null)
		{
			// Illegal path - set it back
			pathSoFar = "/";
			childList = c.getChildFolderNames(pathSoFar);
			if (childList == null)
				throw new LCFException("Can't find any children for root folder");
		}
	    }
	    finally
	    {
		RepositoryConnectorFactory.release(connector);
	    }
	
%>
		    <a name='<%="path_"+Integer.toString(k)%>'>
			<input type="hidden" name="specpath" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(pathSoFar)%>'/>
			<input type="hidden" name="pathop" value=""/>
			<input type="button" value="Add" alt="Add path" onClick='<%="Javascript:SpecOp(\"pathop\",\"Add\",\"path_"+Integer.toString(k+1)+"\")"%>'/>&nbsp;
		    </a>
		</td>
		<td class="value">
			<%=org.apache.lcf.ui.util.Encoder.bodyEscape(pathSoFar)%>
<%
	    if (pathSoFar.length() > 1)
	    {
%>
			<input type="button" value="-" alt="Remove from path" onClick='<%="Javascript:SpecOp(\"pathop\",\"Up\",\"path_"+Integer.toString(k)+"\")"%>'/>
<%
	    }
	    if (childList.length > 0)
	    {
%>
			<input type="button" value="+" alt="Add to path" onClick='<%="Javascript:SpecAddToPath(\"path_"+Integer.toString(k)+"\")"%>'/>&nbsp;
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
		out.println(org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage()));
	}
	catch (ServiceInterruption e)
	{
		out.println("Service interruption or invalid credentials - check your repository connection: "+e.getMessage());
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
    if (sn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_LOCATION))
    {
      String pathDescription = "_" + Integer.toString(k);
%>
	<input type="hidden" name='<%="specpath"+pathDescription%>' value='<%=sn.getAttributeValue("path")%>'/>
<%
      k++;
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
			<input type="hidden" name='<%="spectoken"+accessDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(token)%>'/>
			<a name='<%="token_"+Integer.toString(k)%>'><input type="button" value="Delete" alt='<%="Delete access token #"+Integer.toString(k)%>' onClick='<%="Javascript:SpecOp(\""+accessOpName+"\",\"Delete\",\"token_"+Integer.toString(k)+"\")"%>'/></a>
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
	<input type="hidden" name='<%="spectoken"+accessDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(token)%>'/>
<%
			k++;
		}
	  }
%>
	<input type="hidden" name="tokencount" value='<%=Integer.toString(k)%>'/>
<%
  }

  // Document Types tab

  // First, build a hash map containing all the currently selected document types
  HashMap dtMap = new HashMap();
  i = 0;
  while (i < ds.getChildCount())
  {
    SpecificationNode sn = ds.getChild(i++);
    if (sn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_OBJECTTYPE))
    {
      String token = sn.getAttributeValue("token");
      if (token != null && token.length() > 0)
      {
       String isAllString = sn.getAttributeValue("all");
       if (isAllString != null && isAllString.equals("true"))
	dtMap.put(token,new Boolean(true));
       else
       {
	HashMap attrMap = new HashMap();
	// Go through the children and look for attribute records
	int kk = 0;
	while (kk < sn.getChildCount())
	{
	  SpecificationNode dsn = sn.getChild(kk++);
	  if (dsn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_ATTRIBUTENAME))
	  {
	    String attr = dsn.getAttributeValue("attrname");
	    attrMap.put(attr,attr);
	  }
	}
	dtMap.put(token,attrMap);
       }
      }
    }
  }

  if (tabName.equals("Document Types"))
  {
%>
	<table class="displaytable">
	  <tr><td class="separator" colspan="2"><hr/></td></tr>

<%
   // Need to catch potential license exception here
   try
   {
    // Now, go through the list and preselect those that are selected
    IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext, repositoryConnection.getClassName(), repositoryConnection.getConfigParams(),
		repositoryConnection.getMaxConnections());
    try
    {
        org.apache.lcf.crawler.connectors.DCTM.DCTM DctmConnector = (org.apache.lcf.crawler.connectors.DCTM.DCTM) connector;
        String[] strarrObjTypes = DctmConnector.getObjectTypes();
	int ii = 0;
        while (ii < strarrObjTypes.length)
        {
          String strObjectType = strarrObjTypes[ii++];
          if (strObjectType != null && strObjectType.length() > 0)
	  {
%>
	  <tr>
	    <td class="value">
<%
		Object o = dtMap.get(strObjectType);
		if (o == null)
		{
%>
      		<input type="checkbox" name="specfiletype" value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(strObjectType)%>"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(strObjectType)%></input>
<%
		}
		else
		{
%>
     		<input type="checkbox" name="specfiletype" checked="" value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(strObjectType)%>"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(strObjectType)%></input>
<%

		}
%>
	    </td>
	    <td class="value">
<%
		boolean isAll = false;
		HashMap attrMap = null;
		if (o instanceof Boolean)
		{
			isAll = ((Boolean)o).booleanValue();
			attrMap = new HashMap();
		}
		else
		{
			isAll = false;
			attrMap = (HashMap)o;
		}
%>
		<input type="checkbox" name='<%=org.apache.lcf.ui.util.Encoder.attributeEscape("specfileallattrs_"+strObjectType)%>' value="true" <%=(isAll?"checked=\"\"":"")%>/>&nbsp;All metadata<br/>
		<select multiple="true" name='<%=org.apache.lcf.ui.util.Encoder.attributeEscape("specfileattrs_"+strObjectType)%>' size="3">
<%
		// Get the attributes for this data type
		String[] values = DctmConnector.getIngestableAttributes(strObjectType);
		int iii = 0;
		while (iii < values.length)
		{
			String option = values[iii++];
			if (attrMap != null && attrMap.get(option) != null)
			{
				// Selected
%>
			<option selected="" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(option)%>'><%=org.apache.lcf.ui.util.Encoder.bodyEscape(option)%></option>
<%
			}
			else
			{
				// Unselected
%>
			<option value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(option)%>'><%=org.apache.lcf.ui.util.Encoder.bodyEscape(option)%></option>
<%
			}
		}
%>
		</select>
	    </td>
	  </tr>
<%
      	  }
	}
    }
    finally
    {
      RepositoryConnectorFactory.release(connector);
    }
   }
   catch (LCFException e)
   {
%>
	  <tr><td class="message" colspan="2">
		<%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%>
	  </td></tr>
<%
   }
   catch (ServiceInterruption e)
   {
%>
	  <tr><td class="message" colspan="2">
		Service interruption or invalid credentials - check your repository connection
	  </td></tr>
<%
   }
%>
	</table>
<%
  }
  else
  {
	Iterator iter = dtMap.keySet().iterator();
	while (iter.hasNext())
	{
		String strObjectType = (String)iter.next();
		Object o = dtMap.get(strObjectType);
%>
	<input type="hidden" name="specfiletype" value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(strObjectType)%>"/>
<%
		if (o instanceof Boolean)
		{
			Boolean b = (Boolean)o;
%>
	<input type="hidden" name='<%=org.apache.lcf.ui.util.Encoder.attributeEscape("specfileallattrs_"+strObjectType)%>' value='<%=b.booleanValue()?"true":"false"%>'/>
<%
		}
		else
		{
			HashMap map = (HashMap)o;
			Iterator iter2 = map.keySet().iterator();
			while (iter2.hasNext())
			{
				String attrName = (String)iter2.next();
%>
	<input type="hidden" name='<%=org.apache.lcf.ui.util.Encoder.attributeEscape("specfileattrs_"+strObjectType)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(attrName)%>'/>
<%
			}
		}
	}
  }

  // Content types tab

  // First, build a hash map containing all the currently selected document types
  HashMap ctMap = null;
  i = 0;
  while (i < ds.getChildCount())
  {
    SpecificationNode sn = ds.getChild(i++);
    if (sn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_FORMAT))
    {
      String token = sn.getAttributeValue("value");
      if (token != null && token.length() > 0)
      {
	if (ctMap == null)
		ctMap = new HashMap();
	ctMap.put(token,token);
      }
    }
  }

  if (tabName.equals("Content Types"))
  {
%>
	<table class="displaytable">
	  <tr><td class="separator" colspan="2"><hr/></td></tr>
<%
   // Need to catch potential license exception here
   try
   {
    // Now, go through the list and preselect those that are selected
    IRepositoryConnector connector = RepositoryConnectorFactory.grab(threadContext, repositoryConnection.getClassName(), repositoryConnection.getConfigParams(),
		repositoryConnection.getMaxConnections());
    try
    {
        org.apache.lcf.crawler.connectors.DCTM.DCTM DctmConnector = (org.apache.lcf.crawler.connectors.DCTM.DCTM) connector;
        String[] strarrMimeTypes = DctmConnector.getContentTypes();
	int ii = 0;
        while (ii < strarrMimeTypes.length)
        {
          String strMimeType = strarrMimeTypes[ii++];
          if (strMimeType != null && strMimeType.length() > 0)
	  {
%>
     	  <tr>
		<td class="description">
<%
		if (ctMap == null || ctMap.get(strMimeType) != null)
		{
%>
			<input type="checkbox" name="specmimetype" checked="" value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(strMimeType)%>"></input>
<%
		}
		else
		{
%>
			<input type="checkbox" name="specmimetype" value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(strMimeType)%>"></input>
<%
		}
%>
		</td>
		<td class="value">
			<%=org.apache.lcf.ui.util.Encoder.bodyEscape(strMimeType)%>
		</td>
	  </tr>
<%
      	  }
	}
    }
    finally
    {
      RepositoryConnectorFactory.release(connector);
    }
   }
   catch (LCFException e)
   {
%>
	  <tr><td class="message" colspan="2">
		<%=org.apache.lcf.ui.util.Encoder.bodyEscape(e.getMessage())%>
	  </td></tr>
<%
   }
   catch (ServiceInterruption e)
   {
%>
	  <tr><td class="message" colspan="2">
		Service interruption or invalid credentials - check your repository connection
	  </td></tr>
<%
   }
%>
	</table>
<%
  }
  else
  {
    if (ctMap != null)
    {
	Iterator iter = ctMap.keySet().iterator();
	while (iter.hasNext())
	{
		String strMimeType = (String)iter.next();
%>
	<input type="hidden" name="specmimetype" value="<%=org.apache.lcf.ui.util.Encoder.attributeEscape(strMimeType)%>"/>
<%
	}
    }
  }


  // The Content Length tab

  // Search for max document size
  String maxDocLength = "";
  i = 0;
  while (i < ds.getChildCount())
  {
    SpecificationNode sn = ds.getChild(i++);
    if (sn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_MAXLENGTH))
    {
      maxDocLength = sn.getAttributeValue("value");
    }
  }

  if (tabName.equals("Content Length"))
  {
%>
	<table class="displaytable">
	  <tr><td class="separator" colspan="2"><hr/></td></tr>
	  <tr>
		<td class="description"><nobr>Content length:</nobr></td>
		<td class="value">
			<input name="specmaxdoclength" type="text" size="10" value='<%=maxDocLength%>'/>
		</td>
	  </tr>
	</table>
<%
  }
  else
  {
%>
	<input type="hidden" name="specmaxdoclength" value='<%=maxDocLength%>'/>
<%
  }


  // Path metadata tab

  // Find the path-value metadata attribute name
  i = 0;
  String pathNameAttribute = "";
  while (i < ds.getChildCount())
  {
	SpecificationNode sn = ds.getChild(i++);
	if (sn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_PATHNAMEATTRIBUTE))
	{
		pathNameAttribute = sn.getAttributeValue("value");
	}
  }

  // Find the path-value mapping data
  i = 0;
  org.apache.lcf.crawler.connectors.DCTM.MatchMap matchMap = new org.apache.lcf.crawler.connectors.DCTM.MatchMap();
  while (i < ds.getChildCount())
  {
	SpecificationNode sn = ds.getChild(i++);
	if (sn.getType().equals(org.apache.lcf.crawler.connectors.DCTM.DCTM.CONFIG_PARAM_PATHMAP))
	{
		String pathMatch = sn.getAttributeValue("match");
		String pathReplace = sn.getAttributeValue("replace");
		matchMap.appendMatchPair(pathMatch,pathReplace);
	}
  }

  if (tabName.equals("Path Metadata"))
  {
%>
	<input type="hidden" name="specmappingcount" value='<%=Integer.toString(matchMap.getMatchCount())%>'/>
	<input type="hidden" name="specmappingop" value=""/>

	<table class="displaytable">
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
	<input type="hidden" name="specmappingcount" value='<%=Integer.toString(matchMap.getMatchCount())%>'/>
	<input type="hidden" name="specpathnameattribute" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(pathNameAttribute)%>'/>
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



