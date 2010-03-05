<?xml version='1.0' encoding='UTF-8'?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>

 <xsl:template match='/releases'>
  <s1 title='Releases'>
   <xsl:apply-templates/>
  </s1>
 </xsl:template>
 
 <xsl:template match='release'>
  <s2>
   <xsl:choose>
    <xsl:when test='@date'>
     <xsl:attribute name='title'>
      <xsl:value-of select='@version'/>
      - 
      <xsl:value-of select='@date'/>
     </xsl:attribute>
    </xsl:when>
    <xsl:otherwise>
     <xsl:attribute name='title'>
      <xsl:value-of select='@version'/>
     </xsl:attribute>
    </xsl:otherwise>
   </xsl:choose>
   <xsl:apply-templates select='desc'/>
   <xsl:if test='changes'>
    <ul>
     <xsl:apply-templates select='changes/*'/>
    </ul>
   </xsl:if>
  </s2>
 </xsl:template>
 
 <xsl:template match='desc'>
  <xsl:copy-of select='*|text()'/>
 </xsl:template>

 <xsl:template match='add|remove|fix|update'>
  <xsl:variable name='name'><xsl:value-of select='name()'/></xsl:variable>
  <li>
   <img alt='{$name}:' src='sbk:/resources/changes-{$name}.jpg' border='0'/>
   <xsl:copy-of select='note/*|note/text()'/>
   <xsl:if test='submitter'>
    <xsl:apply-templates select='submitter'/>
   </xsl:if>
  </li>
 </xsl:template>
 
 <xsl:template match='submitter'>
  <code>
   <xsl:choose>
    <xsl:when test='@mailto'>
     [<jump href='mailto:{@mailto}'><xsl:value-of select='@name'/></jump>]
    </xsl:when>
    <xsl:otherwise>
     [<xsl:value-of select='@name'/>]
    </xsl:otherwise>
   </xsl:choose>
  </code>
 </xsl:template>

</xsl:stylesheet>
