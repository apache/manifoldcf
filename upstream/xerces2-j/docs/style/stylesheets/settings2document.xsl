<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>

 <!-- Top-Level Templates -->

 <xsl:template match='/features'>
  <s1 title='Features'>
   <xsl:apply-templates select='desc' mode='header'/>
   <xsl:apply-templates select='fcategory'/>
  </s1>
 </xsl:template>
 
 <xsl:template match='/properties'>
  <s1 title='Properties'>
   <xsl:apply-templates select='desc' mode='header'/>
   <xsl:apply-templates select='pcategory'/>
  </s1>
 </xsl:template>
 
 <xsl:template match='fcategory|pcategory'>
  <s2 title='{@name}'>
   <xsl:apply-templates select='desc'/>
   <xsl:apply-templates/>
  </s2>
 </xsl:template>

 <xsl:template match='feature'>
  <anchor name='{@id}'/>
  <s3 title='{@name}'>
   <table>
    <xsl:apply-templates select='desc' mode='table'/>
    <tr>
     <th>True:</th>
     <td><xsl:apply-templates select='true'/></td>
    </tr>
    <tr>
     <th>False:</th>
     <td><xsl:apply-templates select='false'/></td>
    </tr>
    <xsl:apply-templates select='default' mode='table'/>
    <xsl:apply-templates select='access' mode='table'/>
    <xsl:apply-templates select='since' mode='table'/>
    <xsl:apply-templates select='note' mode='table'/>
    <xsl:apply-templates select='see' mode='table'/>
   </table>
  </s3>
 </xsl:template>

 <xsl:template match='property'>
  <anchor name='{@id}'/>
  <s3 title='{@name}'>
   <table>
    <xsl:apply-templates select='desc' mode='table'/>
    <tr>
     <th>Type:</th>
     <td><xsl:value-of select='type'/></td>
    </tr>
    <xsl:apply-templates select='default' mode='table'/>
    <xsl:apply-templates select='access' mode='table'/>
    <xsl:apply-templates select='since' mode='table'/>
    <xsl:apply-templates select='note' mode='table'/>
    <xsl:apply-templates select='see' mode='table'/>
   </table>
  </s3>
 </xsl:template>
 
 <!-- Table Contents Templates -->
 
 <xsl:template match='desc' mode='table'>
  <tr>
   <th>Desc:</th>
   <td><xsl:apply-templates/></td>
  </tr>
 </xsl:template>

 <xsl:template match='access' mode='table'>
  <tr>
   <th>Access:</th>
   <td>
    <xsl:choose>
     <xsl:when test='@general'>
      <xsl:value-of select='@general'/>
     </xsl:when>
     <xsl:otherwise>
      <xsl:if test='@parsing'>
       (parsing) <xsl:value-of select='@parsing'/>;
      </xsl:if>
      <xsl:if test='@not-parsing'>
       (not parsing) <xsl:value-of select='@not-parsing'/>;
      </xsl:if>
     </xsl:otherwise>
    </xsl:choose>
   </td>
  </tr>
 </xsl:template>

 <xsl:template match='default' mode='table'>
  <tr>
   <th>Default:</th>
   <td><xsl:value-of select='@value'/></td>
  </tr>
 </xsl:template>
 
 <xsl:template match='since' mode='table'>
  <tr>
   <th>Since:</th>
   <td><xsl:value-of select='@value'/></td>
  </tr>
 </xsl:template>
 
 <xsl:template match='note' mode='table'>
  <tr>
   <th>Note:</th>
   <td><xsl:call-template name='markup'/></td>
  </tr>
 </xsl:template>

 <xsl:template match='see' mode='table'>
  <tr>
   <th>See:</th>
   <td>
    <xsl:variable name='idref'><xsl:value-of select='@idref'/></xsl:variable>
    <jump href='#{$idref}'><xsl:value-of select='//*[@id=$idref]/@name'/></jump>
   </td>
  </tr>
 </xsl:template>

 <!-- General Templates -->

 <xsl:template match='desc'>
  <xsl:call-template name='markup'/>
 </xsl:template>

 <xsl:template match='desc' mode='header'>
  <s2 title='{@name}'>
   <xsl:call-template name='markup'/>
  </s2>
 </xsl:template>

 <xsl:template name='markup'>
  <xsl:copy-of select='*|text()'/>
 </xsl:template>

</xsl:stylesheet>
