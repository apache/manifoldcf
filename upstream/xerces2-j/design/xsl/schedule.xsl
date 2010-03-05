<?xml version='1.0' encoding='US-ASCII'?>
<!-- $Id$ -->
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>

 <xsl:template match='schedule'>
  <HTML>
   <HEAD>
    <TITLE>Xerces 2 | Schedule</TITLE>
    <LINK rel='stylesheet' type='text/css' href='css/site.css'/>
    <STYLE type='text/css'>
     .note { font-size: smaller }
    </STYLE>
   </HEAD>
   <BODY>
    <SPAN class='netscape'>
    <H1>Xerces 2 Schedule</H1>
    <xsl:if test='milestone[task/@status="working" or task/@status="verify"]'>
     <H2>Current Milestones</H2>
     <xsl:for-each select='milestone[task/@status="working" or task/@status="verify"]'>
      <xsl:apply-templates select='.'/>
     </xsl:for-each>
    </xsl:if>
    <xsl:if test='milestone[not(task)]'>
     <H2>Future Milestones</H2>
     <xsl:for-each select='milestone[not(task)]'>
      <xsl:apply-templates select='.'/>
     </xsl:for-each>
    </xsl:if>
    <xsl:if test='milestone[task and not(task/@status="working" or task/@status="verify")]'>
     <H2>Completed Milestones</H2>
     <xsl:for-each select='milestone[task and not(task/@status="working" or task/@status="verify")]'>
      <xsl:apply-templates select='.'/>
     </xsl:for-each>
    </xsl:if>
    </SPAN>
    <HR/>
    <SPAN class='netscape'>
     Last modified: <xsl:value-of select='@date'/>
    </SPAN>
   </BODY>
  </HTML>
 </xsl:template>

 <xsl:template match='milestone'>
  <A name='{@id}'/>
  <H3>
   <xsl:value-of select='title'/>
   (<xsl:value-of select='@id'/>)
  </H3>
  <P>
   <TABLE border='0'>
    <xsl:if test='@date'>
     <TR>
      <TH>Date:</TH>
      <TD><xsl:value-of select='@date'/></TD>
     </TR>
    </xsl:if>
    <xsl:if test='depends'>
     <TR>
      <TH>Depends:</TH>
      <TD>
       <xsl:for-each select='depends'>
        <A href='#{@idref}'><xsl:value-of select='@idref'/></A>
        <xsl:if test='not(position()=last())'>, </xsl:if>
       </xsl:for-each>
      </TD>
     </TR>
    </xsl:if>
    <xsl:for-each select='task'>
     <TR>
      <TH>Task:</TH>
      <TD>
       <xsl:value-of select='title'/>
       <SPAN class='note'>
       <xsl:if test='detail'>
        <BR/>
        <xsl:value-of select='detail'/>
       </xsl:if>
       <!--
       <xsl:if test='not(@status="working")'>
        <BR/>
        <STRONG>Status:</STRONG>
        <xsl:text> </xsl:text>
        <xsl:value-of select='@status'/>
       </xsl:if>
       -->
       <xsl:if test='@driver'>
	<BR/>
        <STRONG>Driver:</STRONG> 
	<xsl:choose>
	 <xsl:when test='id(@driver)/@email'>
	  <A href='mailto:{id(@driver)/@email}'><xsl:value-of select='id(@driver)'/></A>
	 </xsl:when>
	 <xsl:otherwise>
	  <xsl:value-of select='id(@driver)'/>
	 </xsl:otherwise>
	</xsl:choose>
       </xsl:if>
       <BR/>
       <STRONG>Contributors:</STRONG>
       <xsl:choose>
        <xsl:when test='contributor'>
         <xsl:for-each select='contributor'>
          <xsl:choose>
           <xsl:when test='id(@idref)/@email'>
            <A href='mailto:{id(@idref)/@email}'><xsl:value-of select='id(@idref)'/></A>
           </xsl:when>
           <xsl:otherwise>
            <xsl:value-of select='id(@idref)'/>
           </xsl:otherwise>
          </xsl:choose>
          <xsl:if test='not(position()=last())'>, </xsl:if>
         </xsl:for-each>
	 - <EM>Contact task driver to contribute.</EM>
        </xsl:when>
        <xsl:otherwise>
         <EM>Contributors wanted! Contact task driver to contribute.</EM>
        </xsl:otherwise>
       </xsl:choose>
       </SPAN>
      </TD>
     </TR>
    </xsl:for-each>
   </TABLE>
  </P>
 </xsl:template>

</xsl:stylesheet>