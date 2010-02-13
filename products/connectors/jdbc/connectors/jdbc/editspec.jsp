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
%>

<%
	if (ds == null)
		out.println("Hey!  No document specification came in!!!");
	if (repositoryConnection == null)
		out.println("No repository connection!!!");
	if (tabName == null)
		out.println("No tab name!");

	String idQuery = null;
	String versionQuery = null;
	String dataQuery = null;

	int i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals(com.metacarta.crawler.connectors.jdbc.JDBCConstants.idQueryNode))
			idQuery = sn.getValue();
		else if (sn.getType().equals(com.metacarta.crawler.connectors.jdbc.JDBCConstants.versionQueryNode))
			versionQuery = sn.getValue();
		else if (sn.getType().equals(com.metacarta.crawler.connectors.jdbc.JDBCConstants.dataQueryNode))
			dataQuery = sn.getValue();
	}

	if (idQuery == null)
		idQuery = "SELECT idfield AS $(IDCOLUMN) FROM documenttable WHERE modifydatefield > $(STARTTIME) AND modifydatefield <= $(ENDTIME)";
	if (versionQuery == null)
		versionQuery = "SELECT idfield AS $(IDCOLUMN), versionfield AS $(VERSIONCOLUMN) FROM documenttable WHERE idfield IN $(IDLIST)";
	if (dataQuery == null)
		dataQuery = "SELECT idfield AS $(IDCOLUMN), urlfield AS $(URLCOLUMN), datafield AS $(DATACOLUMN) FROM documenttable WHERE idfield IN $(IDLIST)";

	// The Queries tab

	if (tabName.equals("Queries"))
	{
%>

	<table class="displaytable">
		<tr><td class="separator" colspan="2"><hr/></td></tr>
		<tr>
			<td class="description"><nobr>Seeding query:</nobr><br/><nobr>(return ids that need to be checked)</nobr></td>
			<td class="value"><textarea name="idquery" cols="64" rows="6"><%=com.metacarta.ui.util.Encoder.bodyEscape(idQuery)%></textarea></td>
		</tr>
		<tr>
			<td class="description"><nobr>Version check query:</nobr><br/><nobr>(return ids and versions for a set of documents;</nobr><br/><nobr>leave blank if no versioning capability)</nobr></td>
			<td class="value"><textarea name="versionquery" cols="64" rows="6"><%=com.metacarta.ui.util.Encoder.bodyEscape(versionQuery)%></textarea></td>
		</tr>
		<tr>
			<td class="description"><nobr>Data query:</nobr><br/><nobr>(return ids, urls, and data for a set of documents)</nobr></td>
			<td class="value"><textarea name="dataquery" cols="64" rows="6"><%=com.metacarta.ui.util.Encoder.bodyEscape(dataQuery)%></textarea></td>
		</tr>
	</table>
<%
	}
	else
	{
%>
	<input type="hidden" name="idquery" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(idQuery)%>'/>
	<input type="hidden" name="versionquery" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(versionQuery)%>'/>
	<input type="hidden" name="dataquery" value='<%=com.metacarta.ui.util.Encoder.attributeEscape(dataQuery)%>'/>
<%
	}
	
	// Security tab
	// There is no native security, so all we care about are the tokens.
	i = 0;

	if (tabName.equals("Security"))
	{
%>
	<table class="displaytable">
	    		<tr><td class="separator" colspan="2"><hr/></td></tr>
<%
	  // Go through forced ACL
	  i = 0;
	  int k = 0;
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
					<a name='<%="token_"+Integer.toString(k)%>'><input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+accessOpName+"\",\"Delete\",\"token_"+Integer.toString(k)+"\")"%>' alt='<%="Delete token #"+Integer.toString(k)%>'/></a>&nbsp;
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
	  // Finally, go through forced ACL
	  i = 0;
	  int k = 0;
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
