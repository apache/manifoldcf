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
	// This file is included by every place that the specification information for the rss connector
	// needs to be edited.  When it is called, the DocumentSpecification object is placed in the thread context
	// under the name "DocumentSpecification".  The IRepositoryConnection object is also in the thread context,
	// under the name "RepositoryConnection".

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


	// Build the url seed string, and the url regexp match and map
	StringBuffer sb = new StringBuffer();
	ArrayList regexp = new ArrayList();
	ArrayList matchStrings = new ArrayList();
	int feedTimeoutValue = 60;
	int feedRefetchValue = 60;
	int minFeedRefetchValue = 15;
	Integer badFeedRefetchValue = null;
	
	// Now, loop through paths
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("feed"))
		{
			String rssURL = sn.getAttributeValue("url");
			if (rssURL != null)
			{
				sb.append(rssURL).append("\n");
			}
		}
		else if (sn.getType().equals("map"))
		{
			String match = sn.getAttributeValue("match");
			String map = sn.getAttributeValue("map");
			if (match != null)
			{
				regexp.add(match);
				if (map == null)
					map = "";
				matchStrings.add(map);
			}
		}
		else if (sn.getType().equals("feedtimeout"))
		{
			String value = sn.getAttributeValue("value");
			feedTimeoutValue = Integer.parseInt(value);
		}
		else if (sn.getType().equals("feedrescan"))
		{
			String value = sn.getAttributeValue("value");
			feedRefetchValue = Integer.parseInt(value);
		}
		else if (sn.getType().equals("minfeedrescan"))
		{
			String value = sn.getAttributeValue("value");
			minFeedRefetchValue = Integer.parseInt(value);
		}
		else if (sn.getType().equals("badfeedrescan"))
		{
			String value = sn.getAttributeValue("value");
			badFeedRefetchValue = new Integer(value);
		}
	}

	// URLs tab

	if (tabName.equals("URLs"))
	{
%>
	<table class="displaytable">
		<tr><td class="separator" colspan="2"><hr/></td></tr>
		<tr>
			<td class="value" colspan="2">
				<textarea rows="25" cols="80" name="rssurls"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(sb.toString())%></textarea>
			</td>
		</tr>
	</table>
<%
	}
	else
	{
%>
	<input type="hidden" name="rssurls" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(sb.toString())%>'/>
<%
	}

	// Canonicalization tab
	if (tabName.equals("Canonicalization"))
	{
%>
	<table class="displaytable">
		<tr><td class="separator" colspan="2"><hr/></td></tr>
		<tr>
			<td class="boxcell" colspan="2">
				<input type="hidden" name="urlregexpop" value="Continue"/>
				<input type="hidden" name="urlregexpnumber" value=""/>
				<table class="formtable">
					<tr class="formheaderrow">
						<td class="formcolumnheader"></td>
						<td class="formcolumnheader"><nobr>URL regular expression</nobr></td>
						<td class="formcolumnheader"><nobr>Description</nobr></td>
						<td class="formcolumnheader"><nobr>Reorder?</nobr></td>
						<td class="formcolumnheader"><nobr>Remove JSP sessions?</nobr></td>
						<td class="formcolumnheader"><nobr>Remove ASP sessions?</nobr></td>
						<td class="formcolumnheader"><nobr>Remove PHP sessions?</nobr></td>
						<td class="formcolumnheader"><nobr>Remove BV sessions?</nobr></td>
					</tr>
<%
		int q = 0;
		int l = 0;
		while (q < ds.getChildCount())
		{
			SpecificationNode specNode = ds.getChild(q++);
			if (specNode.getType().equals("urlspec"))
			{
				// Ok, this node matters to us
				String regexpString = specNode.getAttributeValue("regexp");
				String description = specNode.getAttributeValue("description");
				if (description == null)
					description = "";
				String allowReorder = specNode.getAttributeValue("reorder");
				if (allowReorder == null || allowReorder.length() == 0)
					allowReorder = "no";
				String allowJavaSessionRemoval = specNode.getAttributeValue("javasessionremoval");
				if (allowJavaSessionRemoval == null || allowJavaSessionRemoval.length() == 0)
					allowJavaSessionRemoval = "no";
				String allowASPSessionRemoval = specNode.getAttributeValue("aspsessionremoval");
				if (allowASPSessionRemoval == null || allowASPSessionRemoval.length() == 0)
					allowASPSessionRemoval = "no";
				String allowPHPSessionRemoval = specNode.getAttributeValue("phpsessionremoval");
				if (allowPHPSessionRemoval == null || allowPHPSessionRemoval.length() == 0)
					allowPHPSessionRemoval = "no";
				String allowBVSessionRemoval = specNode.getAttributeValue("bvsessionremoval");
				if (allowBVSessionRemoval == null || allowBVSessionRemoval.length() == 0)
					allowBVSessionRemoval = "no";
%>
					<tr class='<%=((l % 2)==0)?"evenformrow":"oddformrow"%>'>
						<td class="formcolumncell">
							<a name='<%="urlregexp_"+Integer.toString(l)%>'>
								<input type="button" value="Delete" alt='<%="Delete url regexp "+org.apache.lcf.ui.util.Encoder.attributeEscape(regexpString)%>' onclick='<%="javascript:URLRegexpDelete("+Integer.toString(l)+",\"urlregexp_"+Integer.toString(l)+"\");"%>'/>
							</a>
						</td>
						<td class="formcolumncell">
							<input type="hidden" name='<%="urlregexp_"+Integer.toString(l)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(regexpString)%>'/>
							<input type="hidden" name='<%="urlregexpdesc_"+Integer.toString(l)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(description)%>'/>
							<input type="hidden" name='<%="urlregexpreorder_"+Integer.toString(l)%>' value='<%=allowReorder%>'/>
							<input type="hidden" name='<%="urlregexpjava_"+Integer.toString(l)%>' value='<%=allowJavaSessionRemoval%>'/>
							<input type="hidden" name='<%="urlregexpasp_"+Integer.toString(l)%>' value='<%=allowASPSessionRemoval%>'/>
							<input type="hidden" name='<%="urlregexpphp_"+Integer.toString(l)%>' value='<%=allowPHPSessionRemoval%>'/>
							<input type="hidden" name='<%="urlregexpbv_"+Integer.toString(l)%>' value='<%=allowBVSessionRemoval%>'/>
							<nobr><%=org.apache.lcf.ui.util.Encoder.bodyEscape(regexpString)%></nobr>
						</td>
						<td class="formcolumncell"><%=org.apache.lcf.ui.util.Encoder.bodyEscape(description)%></td>
						<td class="formcolumncell"><%=allowReorder%></td>
						<td class="formcolumncell"><%=allowJavaSessionRemoval%></td>
						<td class="formcolumncell"><%=allowASPSessionRemoval%></td>
						<td class="formcolumncell"><%=allowPHPSessionRemoval%></td>
						<td class="formcolumncell"><%=allowBVSessionRemoval%></td>
					</tr>
<%

				l++;
			}
		}
		if (l == 0)
		{
%>
					<tr class="formrow"><td colspan="8" class="formcolumnmessage"><nobr>No canonicalization specified - all URLs will be reordered and have all sessions removed</nobr></td></tr>
<%
		}
%>
					<tr class="formrow"><td colspan="8" class="formseparator"><hr/></td></tr>
					<tr class="formrow">
						<td class="formcolumncell">
							<a name='<%="urlregexp_"+Integer.toString(l)%>'>
								<input type="button" value="Add" alt="Add url regexp" onclick='<%="javascript:URLRegexpAdd(\"urlregexp_"+Integer.toString(l+1)+"\");"%>'/>
								<input type="hidden" name="urlregexpcount" value='<%=Integer.toString(l)%>'/>
							</a>
						</td>
						<td class="formcolumncell"><input type="text" name="urlregexp" size="30" value=""/></td>
						<td class="formcolumncell"><input type="text" name="urlregexpdesc" size="30" value=""/></td>
						<td class="formcolumncell"><input type="checkbox" name="urlregexpreorder" value="yes"/></td>
						<td class="formcolumncell"><input type="checkbox" name="urlregexpjava" value="yes" checked="true"/></td>
						<td class="formcolumncell"><input type="checkbox" name="urlregexpasp" value="yes" checked="true"/></td>
						<td class="formcolumncell"><input type="checkbox" name="urlregexpphp" value="yes" checked="true"/></td>
						<td class="formcolumncell"><input type="checkbox" name="urlregexpbv" value="yes" checked="true"/></td>
					</tr>
				</table>
			</td>
		</tr>
	</table>
<%
	}
	else
	{
		// Post the canonicalization specification
		int q = 0;
		int l = 0;
		while (q < ds.getChildCount())
		{
			SpecificationNode specNode = ds.getChild(q++);
			if (specNode.getType().equals("urlspec"))
			{
				// Ok, this node matters to us
				String regexpString = specNode.getAttributeValue("regexp");
				String description = specNode.getAttributeValue("description");
				if (description == null)
					description = "";
				String allowReorder = specNode.getAttributeValue("reorder");
				if (allowReorder == null || allowReorder.length() == 0)
					allowReorder = "no";
				String allowJavaSessionRemoval = specNode.getAttributeValue("javasessionremoval");
				if (allowJavaSessionRemoval == null || allowJavaSessionRemoval.length() == 0)
					allowJavaSessionRemoval = "no";
				String allowASPSessionRemoval = specNode.getAttributeValue("aspsessionremoval");
				if (allowASPSessionRemoval == null || allowASPSessionRemoval.length() == 0)
					allowASPSessionRemoval = "no";
				String allowPHPSessionRemoval = specNode.getAttributeValue("phpsessionremoval");
				if (allowPHPSessionRemoval == null || allowPHPSessionRemoval.length() == 0)
					allowPHPSessionRemoval = "no";
				String allowBVSessionRemoval = specNode.getAttributeValue("bvsessionremoval");
				if (allowBVSessionRemoval == null || allowBVSessionRemoval.length() == 0)
					allowBVSessionRemoval = "no";
%>
	<input type="hidden" name='<%="urlregexp_"+Integer.toString(l)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(regexpString)%>'/>
	<input type="hidden" name='<%="urlregexpdesc_"+Integer.toString(l)%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(description)%>'/>
	<input type="hidden" name='<%="urlregexpreorder_"+Integer.toString(l)%>' value='<%=allowReorder%>'/>
	<input type="hidden" name='<%="urlregexpjava_"+Integer.toString(l)%>' value='<%=allowJavaSessionRemoval%>'/>
	<input type="hidden" name='<%="urlregexpasp_"+Integer.toString(l)%>' value='<%=allowASPSessionRemoval%>'/>
	<input type="hidden" name='<%="urlregexpphp_"+Integer.toString(l)%>' value='<%=allowPHPSessionRemoval%>'/>
	<input type="hidden" name='<%="urlregexpbv_"+Integer.toString(l)%>' value='<%=allowBVSessionRemoval%>'/>
<%
				l++;
			}
		}
%>
	<input type="hidden" name="urlregexpcount" value='<%=Integer.toString(l)%>'/>
<%
	}
	
	// Mappings tab

	if (tabName.equals("Mappings"))
	{
%>
	<input type="hidden" name="rssop" value=""/>
	<input type="hidden" name="rssindex" value=""/>
	<input type="hidden" name="rssmapcount" value='<%=Integer.toString(regexp.size())%>'/>

	<table class="displaytable">
		<tr><td class="separator" colspan="4"><hr/></td></tr>

<%
	    i = 0;
	    while (i < regexp.size())
	    {
		String prefix = "rssregexp_"+Integer.toString(i)+"_";
%>
		<tr>
			    <td class="value"><a name='<%="regexp_"+Integer.toString(i)%>'><input type="button" value="Remove" onclick='<%="javascript:RemoveRegexp("+Integer.toString(i)+",\"regexp_"+Integer.toString(i)+"\")"%>' alt='<%="Remove regexp #"+Integer.toString(i)%>'/></a></td>
			    <td class="value"><input type="hidden" name='<%=prefix+"match"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape((String)regexp.get(i))%>'/><%=org.apache.lcf.ui.util.Encoder.bodyEscape((String)regexp.get(i))%></td>
			    <td class="value">==></td>
			    <td class="value">
<%
		String match = (String)matchStrings.get(i);
%>
				<input type="hidden" name='<%=prefix+"map"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(match)%>'/>
<%
		if (match.length() == 0)
		{
%>
				&lt;as is&gt;
<%
		}
		else
		{
%>
				<%=org.apache.lcf.ui.util.Encoder.bodyEscape(match)%>
<%
		}
%>
			    </td>
		</tr>
<%
		i++;
	    }
%>
		<tr>
			    <td class="value"><a name='<%="regexp_"+Integer.toString(i)%>'><input type="button" value="Add" onclick='<%="javascript:AddRegexp(\"regexp_"+Integer.toString(i+1)+"\")"%>' alt="Add regexp"/></a></td>
			    <td class="value"><input type="text" name="rssmatch" size="16" value=""/></td>
			    <td class="value">==></td>
			    <td class="value"><input type="text" name="rssmap" size="16" value=""/></td>
		</tr>
	</table>
<%
	}
	else
	{
%>
	<input type="hidden" name="rssmapcount" value='<%=Integer.toString(regexp.size())%>'/>
<%
	    i = 0;
	    while (i < regexp.size())
	    {
		String prefix = "rssregexp_"+Integer.toString(i)+"_";
		String match = (String)matchStrings.get(i);

%>
	<input type="hidden" name='<%=prefix+"match"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape((String)regexp.get(i))%>'/>
	<input type="hidden" name='<%=prefix+"map"%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(match)%>'/>
<%
	    i++;
	    }
	}

	// Timeout Value tab
	if (tabName.equals("Time Values"))
	{
%>
	<table class="displaytable">
		<tr><td class="separator" colspan="2"><hr/></td></tr>
		<tr>
			<td class="description"><nobr>Feed connect timeout (seconds):</nobr></td>
			<td class="value"><input type="text" size="5" name="feedtimeout" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(Integer.toString(feedTimeoutValue))%>'/></td>
		</tr>
		<tr>
			<td class="description"><nobr>Default feed refetch time (minutes)</nobr></td>
			<td class="value"><input type="text" size="5" name="feedrefetch" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(Integer.toString(feedRefetchValue))%>'/></td>
		</tr>
		<tr>
			<td class="description"><nobr>Minimum feed refetch time (minutes)</nobr></td>
			<td class="value"><input type="text" size="5" name="minfeedrefetch" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(Integer.toString(minFeedRefetchValue))%>'/></td>
		</tr>
		<tr>
			<td class="description"><nobr>Bad feed refetch time (minutes)</nobr></td>
			<td class="value">
				<input type="hidden" name="badfeedrefetch_present" value="true"/>
				<input type="text" size="5" name="badfeedrefetch" value='<%=((badFeedRefetchValue==null)?"":org.apache.lcf.ui.util.Encoder.attributeEscape(badFeedRefetchValue.toString()))%>'/>
			</td>
		</tr>

	</table>
<%
	}
	else
	{
%>
	<input type="hidden" name="feedtimeout" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(Integer.toString(feedTimeoutValue))%>'/>
	<input type="hidden" name="feedrefetch" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(Integer.toString(feedRefetchValue))%>'/>
	<input type="hidden" name="minfeedrefetch" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(Integer.toString(minFeedRefetchValue))%>'/>
	<input type="hidden" name="badfeedrefetch_present" value="true"/>
	<input type="hidden" name="badfeedrefetch" value='<%=((badFeedRefetchValue==null)?"":org.apache.lcf.ui.util.Encoder.attributeEscape(badFeedRefetchValue.toString()))%>'/>
<%
	}

	// Dechromed content tab
	String dechromedMode = "none";
	String chromedMode = "use";
	i = 0;
	while (i < ds.getChildCount())
	{
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("dechromedmode"))
			dechromedMode = sn.getAttributeValue("mode");
		else if (sn.getType().equals("chromedmode"))
			chromedMode = sn.getAttributeValue("mode");
	}
	if (tabName.equals("Dechromed Content"))
	{
%>
	<table class="displaytable">
		<tr><td class="separator" colspan="1"><hr/></td></tr>
		<tr>
			<td class="value"><nobr><input type="radio" name="dechromedmode" value="none" <%=dechromedMode.equals("none")?"checked=\"true\"":""%>/>&nbsp;No dechromed content</nobr></td>
		</tr>
		<tr>
			<td class="value"><nobr><input type="radio" name="dechromedmode" value="description" <%=dechromedMode.equals("description")?"checked=\"true\"":""%>/>&nbsp;Dechromed content, if present, in 'description' field</nobr></td>
		</tr>
		<tr>
			<td class="value"><nobr><input type="radio" name="dechromedmode" value="content" <%=dechromedMode.equals("content")?"checked=\"true\"":""%>/>&nbsp;Dechromed content, if present, in 'content' field</nobr></td>
		</tr>
		<tr>
			<td class="separator"><hr/></td>
		</tr>
		<tr>
			<td class="value"><nobr><input type="radio" name="chromedmode" value="none" <%=chromedMode.equals("use")?"checked=\"true\"":""%>/>&nbsp;Use chromed content if no dechromed content found</nobr></td>
		</tr>
		<tr>
			<td class="value"><nobr><input type="radio" name="chromedmode" value="skip" <%=chromedMode.equals("skip")?"checked=\"true\"":""%>/>&nbsp;Never use chromed content</nobr></td>
		</tr>
	</table>
<%
	}
	else
	{
%>
	<input type="hidden" name="dechromedmode" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(dechromedMode)%>'/>
	<input type="hidden" name="chromedmode" value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(chromedMode)%>'/>
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

	// "Metadata" tab
	if (tabName.equals("Metadata"))
	{
%>
	<table class="displaytable">
	    		<tr><td class="separator" colspan="4"><hr/></td></tr>
<%
	  // Go through metadata
	  i = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("metadata"))
		{
			String metadataDescription = "_"+Integer.toString(k);
			String metadataOpName = "metadataop"+metadataDescription;
			String name = sn.getAttributeValue("name");
			String value = sn.getAttributeValue("value");
%>
			<tr>
				<td class="description">
					<input type="hidden" name='<%=metadataOpName%>' value=""/>
					<input type="hidden" name='<%="specmetaname"+metadataDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(name)%>'/>
					<input type="hidden" name='<%="specmetavalue"+metadataDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(value)%>'/>
					<a name='<%="metadata_"+Integer.toString(k)%>'><input type="button" value="Delete" onClick='<%="Javascript:SpecOp(\""+metadataOpName+"\",\"Delete\",\"metadata_"+Integer.toString(k)+"\")"%>' alt='<%="Delete metadata #"+Integer.toString(k)%>'/></a>&nbsp;
				</td>
				<td class="value">
					<%=org.apache.lcf.ui.util.Encoder.bodyEscape(name)%>
				</td>
				<td class="value">=</td>
				<td class="value">
					<%=org.apache.lcf.ui.util.Encoder.bodyEscape(value)%>
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
				<td class="message" colspan="4">No metadata present</td>
			</tr>
<%
	  }
%>
	    		<tr><td class="lightseparator" colspan="4"><hr/></td></tr>
			<tr>
				<td class="description">
					<input type="hidden" name="metadatacount" value='<%=Integer.toString(k)%>'/>
					<input type="hidden" name="metadataop" value=""/>
					<a name='<%="metadata_"+Integer.toString(k)%>'><input type="button" value="Add" onClick='<%="Javascript:SpecAddMetadata(\"metadata_"+Integer.toString(k+1)+"\")"%>' alt="Add metadata"/></a>&nbsp;
				</td>
				<td class="value">
					<input type="text" size="30" name="specmetaname" value=""/>
				</td>
				<td class="value">=</td>
				<td class="value">
					<input type="text" size="80" name="specmetavalue" value=""/>
				</td>
			</tr>
	</table>
<%

	}
	else
	{
	  // Finally, go through metadata
	  i = 0;
	  k = 0;
	  while (i < ds.getChildCount())
	  {
		SpecificationNode sn = ds.getChild(i++);
		if (sn.getType().equals("metadata"))
		{
			String metadataDescription = "_"+Integer.toString(k);
			String name = sn.getAttributeValue("name");
			String value = sn.getAttributeValue("value");
%>
	<input type="hidden" name='<%="specmetaname"+metadataDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(name)%>'/>
	<input type="hidden" name='<%="specmetavalue"+metadataDescription%>' value='<%=org.apache.lcf.ui.util.Encoder.attributeEscape(value)%>'/>
<%
			k++;
		}
	  }
%>
	<input type="hidden" name="metadatacount" value='<%=Integer.toString(k)%>'/>
<%
	
	}
%>
