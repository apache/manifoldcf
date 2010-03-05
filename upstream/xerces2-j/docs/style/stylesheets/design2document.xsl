<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>

 <!--<xsl:strip-space elements='p th td li strong em'/>-->

 <xsl:template match='/design'>
  <s1 title='{@name}'>
   <xsl:apply-templates select='category'/>
  </s1>
 </xsl:template>
 
 <xsl:template match='category'>
  <s2 title='{@name}'>
   <s3 title='Package {@package}'>
    <xsl:apply-templates select='interface|class'/>
   </s3>
  </s2>
 </xsl:template>
 
 <xsl:template match='interface|class'>
  <anchor name='{@id}'/>
  <table>
  <tr>
   <th>
    <xsl:choose>
     <xsl:when test='name()="interface"'>
      interface <em><xsl:value-of select='@name'/></em>
     </xsl:when>
     <xsl:otherwise>
      class <strong><xsl:value-of select='@name'/></strong>
     </xsl:otherwise>
    </xsl:choose>
   </th>
  </tr>
  <xsl:if test='extends'>
   <tr>
    <td>
     extends
     <xsl:for-each select='extends'>
      <xsl:call-template name='type'/>
      <xsl:if test='not(position()=last())'>
       <xsl:text>, </xsl:text>
      </xsl:if>
     </xsl:for-each>
    </td>
   </tr>
  </xsl:if>
  <xsl:if test='implements'>
   <tr>
    <td>
     implements
     <xsl:for-each select='implements'>
      <xsl:call-template name='type'/>
      <xsl:if test='not(position()=last())'>
       <xsl:text>, </xsl:text>
      </xsl:if>
     </xsl:for-each>
    </td>
   </tr>
  </xsl:if>
  <xsl:if test='constant'>
   <tr>
    <td>
     constants:
     <ul>
      <xsl:for-each select='constant'>
       <li>
        <xsl:value-of select='@visibility'/>
	<xsl:text> </xsl:text>
        <xsl:apply-templates/>
	<xsl:text> </xsl:text>
        <xsl:value-of select='@name'/>
       </li>
      </xsl:for-each>
     </ul>
    </td>
   </tr>
  </xsl:if>
  <xsl:if test='method'>
   <tr>
    <td>
     methods:
     <ul>
      <xsl:for-each select='method'>
       <li>
        <xsl:value-of select='@visibility'/>
	<xsl:text> </xsl:text>
	<xsl:choose>
	 <xsl:when test='returns'>
          <xsl:apply-templates/>
          <xsl:text> </xsl:text>
	 </xsl:when>
	 <xsl:otherwise>void </xsl:otherwise>
	</xsl:choose>
        <xsl:value-of select='@name'/>
	<xsl:text>(</xsl:text>
	<xsl:for-each select='param'>
	 <xsl:apply-templates/>
	 <xsl:if test='not(position()=last())'>, </xsl:if>
	</xsl:for-each>
	<xsl:text>)</xsl:text>
       </li>
      </xsl:for-each>
     </ul>
    </td>
   </tr>
  </xsl:if>
  </table>
 </xsl:template>
 
 <xsl:template match='constant|field|param'>
  <xsl:call-template name='type'/>
  <xsl:text> </xsl:text>
  <xsl:value-of select='@name'/>
 </xsl:template>
 
 <xsl:template name='type'>
  <xsl:apply-templates select='primitive|array|reference|collection'/>
 </xsl:template>

 <xsl:template match='primitive'>
  <xsl:value-of select='@type'/>
 </xsl:template>
 
 <xsl:template match='array'>
  <xsl:call-template name='type'/>[]
 </xsl:template>

 <xsl:template match='reference'>
  <xsl:variable name='idref'><xsl:value-of select='@idref'/></xsl:variable>
  <xsl:choose>
   <xsl:when test='//class[@id=$idref]'>
    <strong><xsl:value-of select='//*[@id=$idref]/@name'/></strong>
   </xsl:when>
   <xsl:otherwise>
    <em><xsl:value-of select='//*[@id=$idref]/@name'/></em>
   </xsl:otherwise>
  </xsl:choose>
 </xsl:template>

 <xsl:template match='collection'>
  COLLECTION
 </xsl:template>

</xsl:stylesheet>
