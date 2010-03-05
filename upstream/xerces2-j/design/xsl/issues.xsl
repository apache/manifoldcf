<?xml version='1.0' encoding='US-ASCII'?>
<!-- $Id$ -->
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>
 <xsl:template match='issues'>
  <HTML>
   <HEAD>
    <TITLE>Xerces 2 | Issues</TITLE>
    <LINK rel='stylesheet' type='text/css' href='css/site.css'/>
   </HEAD>
   <BODY>
    <SPAN class='netscape'>
    <A name='TOP'/>
    <H1>Implementation Issues</H1>
    <xsl:choose>
     <xsl:when test='issue'>
      <xsl:if test='issue[@status="open"]'>
       <A name='{@status}'/>
       <H2>Open Issues</H2>
       <xsl:for-each select='issue[@status="open"]'>
        <xsl:apply-templates select='.'/>
       </xsl:for-each>
      </xsl:if>
      <xsl:if test='issue[@status="deferred"]'>
       <A name='{@status}'/>
       <H2>Deferred Issues</H2>
       <xsl:for-each select='issue[@status="deferred"]'>
        <xsl:apply-templates select='.'/>
       </xsl:for-each>
      </xsl:if>
      <xsl:if test='issue[@status="closed"]'>
       <A name='{@status}'/>
       <H2>Closed Issues</H2>
       <xsl:for-each select='issue[@status="closed"]'>
        <xsl:apply-templates select='.'/>
       </xsl:for-each>
      </xsl:if>
      <xsl:if test='issue[@status="rejected"]'>
       <A name='{@status}'/>
       <H2>Rejected Issues</H2>
       <xsl:for-each select='issue[@status="rejected"]'>
        <xsl:apply-templates select='.'/>
       </xsl:for-each>
      </xsl:if>
     </xsl:when>
     <xsl:otherwise>
      <H2><EM>There are currently no issues.</EM></H2>
     </xsl:otherwise>
    </xsl:choose>
    </SPAN>
    <A name='BOTTOM'/>
    <HR/>
    <SPAN class='netscape'>
     Last updated on <xsl:value-of select='@date'/>
    </SPAN>
   </BODY>
  </HTML>
 </xsl:template>

 <xsl:template match='issue'>
  <A name='{@id}'/>
  <H3>
   <xsl:value-of select='title'/>
   (<xsl:value-of select='@id'/>)
  </H3>
  <P>
   <TABLE border='0' cellspacing='5'>
    <TR>
     <TH>Originator:</TH>
     <TD><xsl:apply-templates select='@originator'/></TD>
    </TR>
    <xsl:if test='@owner'>
     <TH>Owner:</TH>
     <TD><xsl:apply-templates select='@owner'/></TD>
    </xsl:if>
    <xsl:if test='detail'>
     <TR>
      <TH>Details:</TH>
      <TD><xsl:apply-templates select='detail'/></TD>
     </TR>
    </xsl:if>
    <xsl:for-each select='problem'>
     <TR>
      <TH>Problem:</TH>
      <TD>
       <xsl:apply-templates select='detail'/>
       <xsl:for-each select='comment'>
        <BR/>
        <STRONG>Comment: </STRONG>
        <xsl:value-of select='.'/>
       </xsl:for-each>
       <xsl:if test='resolution'>
        <BR/>
        <STRONG>Resolution: </STRONG>
        <xsl:value-of select='resolution'/>
       </xsl:if>
      </TD>
     </TR>
    </xsl:for-each>
    <xsl:for-each select='comment'>
     <TR>
      <TH>Comment:</TH>
      <TD>
       <STRONG><xsl:apply-templates select='@author'/>: </STRONG>
       <xsl:if test='@link'>
        [<A href='{@link}'>link</A>]
       </xsl:if>
       <BLOCKQUOTE>
        <xsl:apply-templates select='.'/>
       </BLOCKQUOTE>
      </TD>
     </TR>
    </xsl:for-each>
   </TABLE>
  </P>
 </xsl:template>

 <xsl:template match='@author|@originator|@owner'>
  <xsl:choose>
   <xsl:when test='id(.)/@email'>
    <A href='mailto:{id(.)/@email}'>
     <xsl:value-of select='id(.)'/>
    </A>
   </xsl:when>
   <xsl:otherwise>
    <xsl:value-of select='id(.)'/>
   </xsl:otherwise>
  </xsl:choose>
 </xsl:template>

</xsl:stylesheet>