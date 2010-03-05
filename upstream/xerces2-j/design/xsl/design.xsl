<?xml version='1.0' encoding='US-ASCII'?>
<!-- $Id$ -->
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>

 <xsl:template match='design'>
  <HTML>
   <HEAD>
    <TITLE>Xerces 2 | Design</TITLE>
    <LINK rel='stylesheet' type='text/css' href='css/index.css'/>
    <LINK rel='stylesheet' type='text/css' href='css/design.css'/>
   </HEAD>
   <BODY>
    <H1 align='center'>
     <xsl:value-of select='@name'/>
     <xsl:text> </xsl:text>
     Design
    </H1>
    <H2>Classes and Interfaces</H2>
    <xsl:for-each select='category'>
     <xsl:apply-templates select='.'/>
    </xsl:for-each>
    Last modified: <xsl:value-of select='@date'/>
   </BODY>
  </HTML>
 </xsl:template>

 <xsl:template match='category'>
  <H3><xsl:value-of select='@name'/></H3>
  <xsl:for-each select='class|interface'>
   <BLOCKQUOTE>
    <xsl:apply-templates select='.'/>
   </BLOCKQUOTE>
  </xsl:for-each>
 </xsl:template>

 <xsl:template match='class|interface'>
  <A name='{@name}'>
  <DL class='{name()}'>
   <DT>
    <xsl:value-of select='name()'/>
    <xsl:text> </xsl:text>
    <SPAN class='{name()}-title'>
     <xsl:value-of select='@name'/>
    </SPAN>
   </DT>
   <xsl:if test='extends'>
    <DD class='extends'>
     extends
     <xsl:for-each select='extends'>
      <xsl:call-template name='type'/>
     </xsl:for-each>
    </DD>
   </xsl:if>
   <xsl:if test='implements'>
    <DD class='implements'>
     implements
     <xsl:for-each select='implements'>
      <xsl:call-template name='type'/>
      <xsl:if test='not(position()=last())'>
       <xsl:text>, </xsl:text>
      </xsl:if>
     </xsl:for-each>
    </DD>
   </xsl:if>
   <xsl:if test='constant'>
    <DD class='constants'>
     constants:
     <UL>
      <xsl:for-each select='constant'>
       <LI>
        <xsl:apply-templates select='.'/>
       </LI>
      </xsl:for-each>
     </UL>
    </DD>
   </xsl:if>
   <xsl:if test='field'>
    <DD class='fields'>
     fields:
     <UL>
      <xsl:for-each select='field'>
       <LI>
        <xsl:apply-templates select='.'/>
       </LI>
      </xsl:for-each>
     </UL>
    </DD>
   </xsl:if>
   <xsl:if test='constructor'>
    <DD class='constructors'>
     constructors:
     <UL>
      <xsl:for-each select='constructor'>
       <LI>
        <xsl:apply-templates select='.'/>
       </LI>
      </xsl:for-each>
     </UL>
    </DD>
   </xsl:if>
   <xsl:if test='method'>
    <DD class='methods'>
     methods:
     <UL>
      <xsl:for-each select='method'>
       <LI>
        <xsl:apply-templates select='.'/>
       </LI>
      </xsl:for-each>
     </UL>
    </DD>
   </xsl:if>
  </DL>
  </A>
 </xsl:template>

 <xsl:template match='constant|field|param'>
  <!--
  <xsl:if test='not(name()="param") and not(@visibility="public")'>
   <IMG alt='' src='{@visibility}.gif'/>
  </xsl:if>
  -->
  <xsl:call-template name='type'/>
  <xsl:text> </xsl:text>
  <SPAN class='{name()}-title'>
   <xsl:value-of select='@name'/>
  </SPAN>
 </xsl:template>

 <xsl:template match='constructor'>
  <!--
  <xsl:if test='not(@visibility="public")'>
   <IMG alt='' src='{@visibility}.gif'/>
  </xsl:if>
  -->
  <SPAN class='constructor-title'>
   <xsl:value-of select='../@name'/>
  </SPAN>
  (
  <xsl:for-each select='param'>
   <xsl:apply-templates select='.'/>
   <xsl:if test='not(position()=last())'>
    <xsl:text>, </xsl:text>
   </xsl:if>
  </xsl:for-each>
  )
 </xsl:template>

 <xsl:template match='method'>
  <!--
  <xsl:if test='not(@visibility="public")'>
   <IMG alt='' src='{@visibility}.gif'/>
  </xsl:if>
  -->
  <SPAN class='method-title'>
   <xsl:value-of select='@name'/>
  </SPAN>
  (
  <xsl:for-each select='param'>
   <xsl:apply-templates select='.'/>
   <xsl:if test='not(position()=last())'>
    <xsl:text>, </xsl:text>
   </xsl:if>
  </xsl:for-each>
  )
  <xsl:for-each select='returns'>
   :
   <xsl:call-template name='type'/>
  </xsl:for-each>
 </xsl:template>

 <xsl:template name='type'>
  <xsl:apply-templates select='array|primitive|reference|collection'/>
 </xsl:template>

 <xsl:template match='array'>
  <xsl:call-template name='type'/>[]
 </xsl:template>

 <xsl:template match='primitive'>
  <xsl:value-of select='@type'/>
 </xsl:template>

 <xsl:template match='union'>
  <xsl:for-each select='part'>
   <xsl:apply-templates select='.'/>
  </xsl:for-each>
 </xsl:template>

 <xsl:template match='reference'>
  <xsl:choose>
   <xsl:when test='id(@idref)[name()="interface"]'>
    <SPAN class='interface-title'>
     <xsl:value-of select='id(@idref)/@name'/>
    </SPAN>
   </xsl:when>
   <xsl:otherwise>
    <SPAN class='class-title'>
     <xsl:value-of select='id(@idref)/@name'/>
    </SPAN>
   </xsl:otherwise>
  </xsl:choose>
 </xsl:template>

 <xsl:template match='collection'>
  <xsl:for-each select='collector'>
   <xsl:call-template name='type'/>
  </xsl:for-each>
  &lt;
  <xsl:for-each select='items'>
   <xsl:call-template name='type'/>
  </xsl:for-each>
  &gt;
 </xsl:template>

</xsl:stylesheet>