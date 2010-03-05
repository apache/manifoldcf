<?xml version="1.0" encoding="UTF-8"?>
<!-- define nbsp char entity for output -->
<!DOCTYPE menu [
	<!ENTITY nbsp "&#160;">
]>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fo="http://www.w3.org/1999/XSL/Format">
	<xsl:output media-type="text/html" encoding="UTF-8"/>
	<xsl:template match="/">
		<html>
			<head>
				<title>Proposed Xerces2 Requirements -  <xsl:value-of select="/requirementCatalog/@date"/>
				</title>
			</head>
			<body bgcolor="#FFFFFF">
				<h1>Proposed Xerces2 Requirements</h1>
				<h4>Date: <xsl:value-of select="/requirementCatalog/@date"/>
					<br/>
  Editors: &nbsp;&nbsp;<a href="mailto:estaub@mediaone.net">Ed Staub</a>&nbsp;&nbsp;<a href="mailto:twleung@sauria.com">Ted Leung</a>
				</h4>
				<hr/>
				<h2>Schema</h2>
				<p>Proposed requirements are organized into categories. &nbsp;Some requirements 
  occur in more than one category in the future.</p>
				<p>Each requirement has a number. Underneath the number is a &quot;hardness&quot; 
  and &quot;status&quot;.</p>
				<dl>
					<dt>Possible &quot;hardness&quot; values are:</dt>
					<dd>
						<b>hard</b> - Xerces2 must and shall meet this requirement<br/>
						<b>soft</b> - Xerces2 should meet this requirement, but it may be dropped 
    because of conflicting requirements or time pressures</dd>
					<dt>
						<br/>
    Possible &quot;status&quot; values are:</dt>
					<dd>
						<span style="background-color:aquamarine"><b>approved</b> - there appears to be a clear consensus</span><br/>
						<b>tooQuiet</b> - there may be a consensus, but there hasn't been enough 
    input to be sure <br/>
						<b>hardnessConflict</b> - there is conflict over whether this is a hard or 
    soft requirement<b>
							<br/>
    vetoConflict</b> - there is a conflict over whether this proposed requirement 
    is a requirement at all<br/>
						<span style="background-color:silver"><b>rejected</b> - the proposed requirement appears to be dead</span><br/>
						<b>unevaluated</b> - editor hasn't finished reviewing input
					</dd>
				</dl>
				<hr/>
				<xsl:apply-templates/>
			</body>
		</html>
	</xsl:template>
	<xsl:template match="requirementCatalog">
		<xsl:call-template name="indexByNumber"/>
		<xsl:apply-templates select="categories" mode="toc"/>
		<xsl:apply-templates select="categories"/>
	</xsl:template>
	<!-- Table of contents -->
	<xsl:template match="categories" mode="toc">
		<h2>Requirement Categories</h2>
		<xsl:for-each select="cat">
			<a>
				<xsl:attribute name="href">#cat.{.}</xsl:attribute>
				<xsl:value-of select="."/>
			</a>
			<br/>
		</xsl:for-each>
		<hr/>
	</xsl:template>
	<xsl:template name="indexByNumber">
		<h2>Requirements by Number</h2>
		<xsl:for-each select="/requirementCatalog/requirements/req">
			<a href="#req.{@id}">
				<xsl:attribute name="style">
				<xsl:choose>
					<xsl:when test="@status='rejected'">background-color:silver</xsl:when>
					<xsl:when test="@status='approved'">background-color:aquamarine</xsl:when>
					<xsl:otherwise>background-color:snow</xsl:otherwise>
				</xsl:choose>
				</xsl:attribute>
				<xsl:value-of select="@id"/>
			</a>&nbsp;	&nbsp;
			<xsl:value-of select="substring(def,1,120)"/>
			<xsl:if test="string-length(def)>120">...</xsl:if>
			<br/>
			<xsl:if test="@id mod 5 = 0">
				<br/>
			</xsl:if>
		</xsl:for-each>
		<hr/>
	</xsl:template>
	<xsl:template match="categories">
		<h2>Requirements by Category</h2>
		<xsl:for-each select="cat">
			<h3>
				<a>
					<xsl:attribute name="name">#cat.{.}</xsl:attribute>
				</a>
				<xsl:value-of select="."/>
			</h3>
			<table width="95%" border="1">
				<xsl:for-each select="/requirementCatalog/requirements/req[@cat=current()]">
					<xsl:apply-templates select="."/>
				</xsl:for-each>
			</table>
		</xsl:for-each>
	</xsl:template>
	<xsl:template match="req">
		<xsl:variable name="reqId" select="@id"/>
		<tr>
			<xsl:attribute name="bgcolor">
				<xsl:choose>
					<xsl:when test="@status='rejected'">silver</xsl:when>
					<xsl:when test="@status='approved'">aquamarine</xsl:when>
					<xsl:otherwise>snow</xsl:otherwise>
				</xsl:choose>
			</xsl:attribute>
			<td valign="top">
				<p>
					<a>
						<xsl:attribute name="name">req.<xsl:value-of select="$reqId"/></xsl:attribute>
					</a>
					<b>
						<!--						<xsl:value-of select="@id"/>.</b> -->
						<xsl:value-of select="$reqId"/>.</b>
					<br/>
        &nbsp;&nbsp;<xsl:value-of select="@strength"/>
					<br/>
        &nbsp;&nbsp;<xsl:value-of select="@status"/>
				</p>
			</td>
			<td valign="top">
				<p>
					<b>
						<xsl:copy-of select="def"/>
					</b>
				</p>
				<blockquote>
					<xsl:if test="seeAlso">
						<p><xsl:apply-templates select="seeAlso"/></p>
					</xsl:if>
					<xsl:apply-templates select="voteSet"/>
					<xsl:apply-templates select="edReqNote"/>
					<xsl:if test="refs/ref">
						<b><i>References</i></b>
						<br/>
					</xsl:if>
					<xsl:apply-templates select="refs/ref"/>
				</blockquote>
			</td>
		</tr>
	</xsl:template>
	<xsl:template match="voteSet">
		<b>
			<i>Voted</i>
		</b> on from <xsl:value-of select="@opened"/> to <xsl:value-of select="@closed"/>.  Votes:
	<ul>
			<xsl:apply-templates select="vote"/>
		</ul>
	</xsl:template>
	<xsl:template match="vote">
		<li>
			<a href="mailto:{@email}">
				<xsl:value-of select="@voter"/>
			</a>: <xsl:value-of select="@vote"/>
			<xsl:apply-templates select="voteComment"/>
		</li>
	</xsl:template>
	<xsl:template match="voteComment">
		<blockquote>
			<xsl:copy-of select="."/>
		</blockquote>
	</xsl:template>
	<xsl:template match="seeAlso">
		<xsl:if test="@type">
			<xsl:value-of select="concat(@type,':')"/>
		</xsl:if>
		<xsl:if test="not(@type)">
			See also:
		</xsl:if>
		<a href="#req.{@id}">
			<xsl:value-of select="@id"/>
		</a>&nbsp;&nbsp;
	</xsl:template>
	<xsl:template match="edReqNote">
		<blockquote>
			<i>Ed:</i>
			<xsl:value-of select="."/>
		</blockquote>
	</xsl:template>
	<xsl:template match="ref">
		<p>
			<xsl:if test="string(.)">"<xsl:copy-of select="."/>"<br/>
			</xsl:if>
			<xsl:apply-templates select="/requirementCatalog/mailHeaderSets/mailHeaderSet[@id=current()/@set]/li[a/@name=current()/@id]"/>
		</p>
	</xsl:template>
	<xsl:template match="li">
		<xsl:copy-of select="."/>
	</xsl:template>
	<xsl:template match="br">
		<br/>
	</xsl:template>
	<xsl:template match="mailHeaderSets"/>
</xsl:stylesheet>
