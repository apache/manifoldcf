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

	int i;
	int k;

	// Paths tab
	if (tabName.equals("Paths"))
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
		if (sn.getType().equals("startpoint"))
		{
		    String pathDescription = "_"+Integer.toString(k);
		    String pathOpName = "specop"+pathDescription;
%>
		<tr>
		    <td class="description">
			<input type="hidden" name='<%=pathOpName%>' value=""/>
			<input type="hidden" name='<%="specpath"+pathDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(sn.getAttributeValue("path"))%>'/>
			<%=org.apache.lcf.ui.util.Encoder.bodyEscape(sn.getAttributeValue("path"))%> 
			<a name='<%="path_"+Integer.toString(k)%>'><input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+
				pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")"%>' alt='<%="Delete path #"+Integer.toString(k)%>'/></a>
		    </td>
		    <td class="boxcell">
		        <input type="hidden" name='<%="specchildcount"+pathDescription%>' value='<%=Integer.toString(sn.getChildCount())%>'/>
		        <table class="displaytable">
<%

		    int j = 0;
		    while (j < sn.getChildCount())
		    {
			SpecificationNode excludeNode = sn.getChild(j);
			String instanceDescription = "_"+Integer.toString(k)+"_"+Integer.toString(j);
			String instanceOpName = "specop" + instanceDescription;

			String nodeFlavor = excludeNode.getType();
			String nodeType = excludeNode.getAttributeValue("type");
			String nodeMatch = excludeNode.getAttributeValue("match");
%>
			    <tr>
				<td class="description">
				    <nobr>
					<input type="hidden" name='<%="specop"+instanceDescription%>' value=""/>
					<input type="hidden" name='<%="specfl"+instanceDescription%>' value='<%=nodeFlavor%>'/>
					<input type="hidden" name='<%="specty"+instanceDescription%>' value='<%=nodeType%>'/>
					<input type="hidden" name='<%="specma"+instanceDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(nodeMatch)%>'/>
					<%=(nodeFlavor.equals("include"))?"Include ":""%>
					<%=(nodeFlavor.equals("exclude"))?"Exclude ":""%>
					<%=(nodeType.equals("file"))?"file ":""%>
					<%=(nodeType.equals("directory"))?"directory ":""%>
					<%=org.apache.lcf.ui.util.Encoder.bodyEscape(nodeMatch)%>:
					<a name='<%="match_"+Integer.toString(k)+"_"+Integer.toString(j)%>'><input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+
						"specop"+instanceDescription+"\",\"Delete\",\"match_"+Integer.toString(k)+"_"+Integer.toString(j)+"\")"%>' alt='<%="Delete path #"+Integer.toString(k)+
						", match spec #"+Integer.toString(j)%>'/></a>
				    </nobr>
				</td>
				<td class="value">
				    <nobr>
					Match: <input type="text" size="10" name='<%="specmatch"+instanceDescription%>' value=""/>
					Type: <select name='<%="spectype"+instanceDescription%>'>
						<option value="file">File</option>
						<option value="directory">Directory</option>
					</select>Operation: <select name='<%="specflavor"+instanceDescription%>'>
						<option value="include">Include</option>
						<option value="exclude">Exclude</option>
					</select><input type="button" value="Insert Here" onClick='<%="Javascript:SpecOp(\""+
						"specop"+instanceDescription+"\",\"Insert Here\",\"match_"+Integer.toString(k)+"_"+Integer.toString(j+1)+"\")"%>' alt='<%="Insert new match for path #"+
						Integer.toString(k)+" before position #"+Integer.toString(j)%>'/>
				    </nobr>
				</td>
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
					<a name='<%="match_"+Integer.toString(k)+"_"+Integer.toString(j)%>'><input type="button" value="Add" onClick='<%="Javascript:SpecOp(\""+
						pathOpName+"\",\"Add\",\"match_"+Integer.toString(k)+"_"+Integer.toString(j+1)+"\")"%>' alt='<%="Add new match for path #"+Integer.toString(k)%>'/></a>
				</td>
				<td class="value">
				    <nobr>
					Match:&nbsp;<input type="text" size="10" name='<%="specmatch"+pathDescription%>' value=""/>
					Type:&nbsp;
					<select name='<%="spectype"+pathDescription%>'>
						<option value="file">File</option>
						<option value="directory">Directory</option>
					</select>Operation:&nbsp;
					<select name='<%="specflavor"+pathDescription%>'>
						<option value="include">Include</option>
						<option value="exclude">Exclude</option>
					</select>
				    </nobr>
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
		<tr><td class="message" colspan="2">No documents specified</td></tr>
<%
	    }
%>
		<tr><td class="lightseparator" colspan="2"><hr/></td></tr>
		<tr>
		    <td class="value">
			<a name='<%="path_"+Integer.toString(k)%>'><input type="button" value="Add" onClick='<%="Javascript:SpecOp(\"specop\",\"Add\",\"path_"+Integer.toString(i+1)+"\")"%>' alt="Add new path"/>
			<input type="hidden" name="pathcount" value='<%=Integer.toString(k)%>'/>
			<input type="hidden" name="specop" value=""/></a>
		    </td>
		    <td class="value">
			<input type="text" size="80" name="specpath" value=""/>
		    </td>
		</tr>
	</table>
<%
	}
	else
	{
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
        <input type="hidden" name='<%="specchildcount"+pathDescription%>' value='<%=Integer.toString(sn.getChildCount())%>'/>
<%

		    int j = 0;
		    while (j < sn.getChildCount())
		    {
			SpecificationNode excludeNode = sn.getChild(j);
			String instanceDescription = "_"+Integer.toString(k)+"_"+Integer.toString(j);

			String nodeFlavor = excludeNode.getType();
			String nodeType = excludeNode.getAttributeValue("type");
			String nodeMatch = excludeNode.getAttributeValue("match");
%>
	<input type="hidden" name='<%="specfl"+instanceDescription%>' value='<%=nodeFlavor%>'/>
	<input type="hidden" name='<%="specty"+instanceDescription%>' value='<%=nodeType%>'/>
	<input type="hidden" name='<%="specma"+instanceDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(nodeMatch)%>'/>
<%
			j++;
		    }
		    k++;
		}
	    }
%>
	<input type="hidden" name="pathcount" value='<%=Integer.toString(k)%>'/>
<%
	}
%>

