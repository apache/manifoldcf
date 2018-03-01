<?xml version="1.0"?>
<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
<xsl:stylesheet
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:prop="http://apache.org/forrest/properties/1.0"
    version="1.0">
  <xsl:variable
        name="config"
        select="//skinconfig" />
  <xsl:variable
        name="properties"
        select="//prop:properties" />
<!-- left, justify, right -->
  <xsl:variable
        name="text-align"
        select="string($config/pdf/page/@text-align)" />
<!-- prefix which turns relative URLs into absolute ones, empty by default -->
  <xsl:variable
        name="url-prefix"
        select="string($config/pdf/url-prefix)" />
<!-- print URL of external links -->
  <xsl:variable
        name="show-external-urls"
        select="$config/pdf/show-external-urls" />
<!-- disable the table of content (enabled by default) -->
  <xsl:variable
        name="disable-toc"
        select="string($config/pdf/disable-toc)" />
<!-- Get the section depth to use when generating the minitoc (default is 2) -->
  <xsl:variable
        name="toc-max-depth"
        select="number($config/toc/@max-depth)" />
<!-- The page size to be used -->
  <xsl:variable
        name="pagesize"
        select="string($config/pdf/page/@size)" />
<!-- The page orientation ("portrait" or "landscape") -->
  <xsl:variable
        name="pageorientation"
        select="string($config/pdf/page/@orientation)" />
<!-- Double-sided printing toggle -->
  <xsl:variable
        name="doublesided"
        select="string($config/pdf/margins/@double-sided)" />
<!-- The top page margin -->
  <xsl:variable
        name="topmargin"
        select="string($config/pdf/margins/top)" />
<!-- The bottom page margin -->
  <xsl:variable
        name="bottommargin"
        select="string($config/pdf/margins/bottom)" />
<!-- The inner page margin (always the left margin if
  double-sided printing is off, alternating between left and right if
  it's on) -->
  <xsl:variable
        name="innermargin"
        select="string($config/pdf/margins/inner)" />
<!-- The outer page margin (always the right margin if
  double-sided printing is off, alternating between right and left if
  it's on)-->
  <xsl:variable
        name="outermargin"
        select="string($config/pdf/margins/outer)" />
  <xsl:param
        name="numbersections"
        select="'true'" />
<!-- page breaks after TOC and each page if an aggregate document -->
  <xsl:variable
        name="page-break-top-sections"
        select="'true'" />
<!-- page numbering format -->
  <xsl:variable
        name="page-numbering-format"
        select="string($config/pdf/page-numbering-format)" />
  <xsl:variable
        name="background-color"
        select="$config/colors/color[@name='body']/@value" />
  <xsl:variable
        name="heading-color"
        select="$config/colors/color[@name='subheading']/@value" />
  <xsl:variable
        name="heading-type"
        select="$config/headings/@type" />
  <xsl:variable
        name="path-language"
        select="substring-before($path,'/')"/>
<!-- Section depth at which we stop numbering and just indent -->
  <xsl:param
        name="numbering-max-depth"
        select="'3'" />
<!-- Generic font-family parameters defined here. Default values specified,
     but usually the values should be passed in from the calling sitemap. -->
  <xsl:param name="serif">
    <xsl:choose>
      <xsl:when test="$properties/*[@name=concat('output.pdf.fontFamily.serif.',$path-language)]">
        <xsl:value-of select="$properties/*[@name=concat('output.pdf.fontFamily.serif.',$path-language)]/@value"/>
      </xsl:when>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.serif']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.serif']/@value"/>
      </xsl:when>
      <xsl:otherwise>serif</xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="sans-serif">
    <xsl:choose>
      <xsl:when test="$properties/*[@name=concat('output.pdf.fontFamily.sansSerif.',$path-language)]">
        <xsl:value-of select="$properties/*[@name=concat('output.pdf.fontFamily.sansSerif.',$path-language)]/@value"/>
      </xsl:when>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.sansSerif']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.sansSerif']/@value"/>
      </xsl:when>
      <xsl:otherwise>sans-serif</xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="monospace">
    <xsl:choose>
      <xsl:when test="$properties/*[@name=concat('output.pdf.fontFamily.monospace.',$path-language)]">
        <xsl:value-of select="$properties/*[@name=concat('output.pdf.fontFamily.monospace.',$path-language)]/@value"/>
      </xsl:when>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.monospace']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.monospace']/@value"/>
      </xsl:when>
      <xsl:otherwise>monospace</xsl:otherwise>
    </xsl:choose>
  </xsl:param>
<!-- Classes of text types that typically go together, and share font family
     properties; this can be used to specify the font family for all related
     text types at once: -->
  <xsl:param name="headerFooterFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.headerFooterFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.headerFooterFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$sans-serif"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
<!-- Specific font-family parameters defined here, to allow overrides of
     specific content elements; default values taken from the generic ones
     above: -->
  <xsl:param name="rootFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.rootFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.rootFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$serif"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="firstFooterFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.firstFooterFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.firstFooterFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$headerFooterFontFamily"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="evenHeaderFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.evenHeaderFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.evenHeaderFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$headerFooterFontFamily"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="evenFooterFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.evenFooterFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.evenFooterFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$headerFooterFontFamily"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="oddHeaderFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.oddHeaderFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.oddHeaderFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$headerFooterFontFamily"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="oddFooterFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.oddFooterFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.oddFooterFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$headerFooterFontFamily"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="documentTitleFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.documentTitleFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.documentTitleFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$sans-serif"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="versionFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.versionFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.versionFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$sans-serif"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="authorsFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.authorsFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.authorsFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$sans-serif"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="abstractFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.abstractFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.abstractFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$serif"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="noticeFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.noticeFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.noticeFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$serif"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="TOCTitleFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.TOCTitleFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.TOCTitleFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$sans-serif"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="TOCFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.TOCFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.TOCFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$serif"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <!-- helper-commonElements.xsl font-family definitions: -->
  <xsl:param name="sectionTitleFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.sectionTitleFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.sectionTitleFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$sans-serif"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="sourceFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.sourceFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.sourceFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$monospace"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="codeFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.codeFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.codeFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$monospace"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="warningTitleFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.warningTitleFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.warningTitleFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$sans-serif"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="noteTitleFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.noteTitleFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.noteTitleFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$sans-serif"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
  <xsl:param name="fixmeTitleFontFamily">
    <xsl:choose>
      <xsl:when test="$properties/*[@name='output.pdf.fontFamily.fixmeTitleFontFamily']">
        <xsl:value-of select="$properties/*[@name='output.pdf.fontFamily.fixmeTitleFontFamily']/@value"/>
      </xsl:when>
      <xsl:otherwise><xsl:value-of select="$sans-serif"/></xsl:otherwise>
    </xsl:choose>
  </xsl:param>
<!-- Other external parameters: -->
  <xsl:param
        name="imagesdir"
        select="." />
  <xsl:param
        name="xmlbasedir" />
  <xsl:param
        name="path"
        select="." />

<!-- Included stylesheets: -->
  <xsl:include
        href="lm://pdf.transform.helper.pdfoutline" />
  <xsl:include
        href="lm://pdf.transform.helper.footerinfo" />
  <xsl:include
        href="lm://pdf.transform.helper.pageBreaks" />
  <xsl:include
        href="lm://pdf.transform.helper.pageNumber" />
  <xsl:include
        href="lm://pdf.transform.helper.commonElements" />
  <xsl:include
        href="lm://pdf.transform.helper.layout" />
  <xsl:include
        href="lm://pdf.transform.helper.xmpMetadata" />

  <xsl:template
        match="/">
    <fo:root
            xmlns:fo="http://www.w3.org/1999/XSL/Format"
            font-family="{$rootFontFamily}"
            font-size="12pt">
      <fo:layout-master-set>
        <fo:simple-page-master
                    master-name="first-page"
                    page-height="{$pageheight}"
                    page-width="{$pagewidth}"
                    margin-top="{$topmargin}"
                    margin-bottom="{$bottommargin}"
                    margin-left="{$innermargin}"
                    margin-right="{$outermargin}">
          <fo:region-body
                        margin-top="0.5in"
                        margin-bottom=".5in" />
          <fo:region-after
                        region-name="first-footer"
                        extent=".5in"
                        display-align="before" />
        </fo:simple-page-master>
        <fo:simple-page-master
                    master-name="even-page"
                    page-height="{$pageheight}"
                    page-width="{$pagewidth}"
                    margin-top="{$topmargin}"
                    margin-bottom="{$bottommargin}">
          <xsl:choose>
            <xsl:when
                            test="$doublesided = 'true'">
              <xsl:attribute
                                name="margin-left">
                <xsl:value-of
                                    select="$outermargin" />
              </xsl:attribute>
              <xsl:attribute
                                name="margin-right">
                <xsl:value-of
                                    select="$innermargin" />
              </xsl:attribute>
            </xsl:when>
            <xsl:otherwise>
              <xsl:attribute
                                name="margin-left">
                <xsl:value-of
                                    select="$innermargin" />
              </xsl:attribute>
              <xsl:attribute
                                name="margin-right">
                <xsl:value-of
                                    select="$outermargin" />
              </xsl:attribute>
            </xsl:otherwise>
          </xsl:choose>
          <fo:region-body
                        margin-top="0.5in"
                        margin-bottom=".5in" />
          <fo:region-before
                        region-name="even-header"
                        extent="0.5in" />
<!-- commented out from region-body
                           because it is not standard conforming
                        border-bottom="0.5pt solid"  -->
          <fo:region-after
                        region-name="even-footer"
                        extent=".5in"
                        display-align="before" />
        </fo:simple-page-master>
        <fo:simple-page-master
                    master-name="odd-page"
                    page-height="{$pageheight}"
                    page-width="{$pagewidth}"
                    margin-top="{$topmargin}"
                    margin-bottom="{$bottommargin}"
                    margin-left="{$innermargin}"
                    margin-right="{$outermargin}">
          <fo:region-body
                        margin-top="0.5in"
                        margin-bottom=".5in" />
          <fo:region-before
                        region-name="odd-header"
                        extent="0.5in" />
<!-- commented out from region-body
                        because it is not standard conforming
                        border-bottom="0.5pt solid"  -->
          <fo:region-after
                        region-name="odd-footer"
                        extent=".5in"
                        display-align="before" />
        </fo:simple-page-master>
        <fo:page-sequence-master
                    master-name="book">
          <fo:repeatable-page-master-alternatives>
            <fo:conditional-page-master-reference
                            page-position="first"
                            master-reference="first-page" />
            <fo:conditional-page-master-reference
                            odd-or-even="odd"
                            master-reference="odd-page" />
            <fo:conditional-page-master-reference
                            odd-or-even="even"
                            master-reference="even-page" />
          </fo:repeatable-page-master-alternatives>
        </fo:page-sequence-master>
      </fo:layout-master-set>
      <xsl:call-template name="createXMPMetadata"/>
      <xsl:apply-templates
                select="/site/document"
                mode="outline" />
      <fo:page-sequence
                master-reference="book">
        <xsl:apply-templates
                    select="/site/document" />
      </fo:page-sequence>
    </fo:root>
  </xsl:template>
  
  <xsl:template match="document">
    <fo:title>
      <xsl:value-of select="header/title" />
    </fo:title>
    
    <fo:static-content
            flow-name="first-footer"
            font-family="{$firstFooterFontFamily}">
      <fo:block
                border-top="0.25pt solid"
                padding-before="6pt"
                text-align="center">
        <xsl:apply-templates
                    select="footer" />
      </fo:block>
<!-- don't list page number on first page if its content is just the TOC -->
      <xsl:if
                test="$disable-toc = 'true' or not($toc-max-depth > 0 and $page-break-top-sections)">
        <xsl:call-template
                    name="insertPageNumber">
          <xsl:with-param
                        name="text-align">end</xsl:with-param>
        </xsl:call-template>
      </xsl:if>
      <xsl:call-template
                name="info" />
    </fo:static-content>
    
    <fo:static-content
            flow-name="even-header"
            font-family="{$evenHeaderFontFamily}">
      <fo:block
                font-size="70%"
                text-align="end"
                font-style="italic">
        <xsl:value-of
                    select="header/title" />
      </fo:block>
    </fo:static-content>
    <fo:static-content
            flow-name="even-footer"
            font-family="{$evenFooterFontFamily}">
      <fo:block
                border-top="0.25pt solid"
                padding-before="6pt"
                text-align="center">
        <xsl:apply-templates
                    select="footer" />
      </fo:block>
      <xsl:choose>
        <xsl:when test="$doublesided = 'true'">
          <xsl:call-template name="insertPageNumber">
            <xsl:with-param name="text-align">start</xsl:with-param>
          </xsl:call-template>
        </xsl:when>
        <xsl:otherwise>
          <xsl:call-template name="insertPageNumber">
            <xsl:with-param name="text-align">end</xsl:with-param>
          </xsl:call-template>
        </xsl:otherwise>
      </xsl:choose>
      <xsl:call-template
                name="info" />
    </fo:static-content>
    
    <fo:static-content
            flow-name="odd-header"
            font-family="{$oddHeaderFontFamily}">
      <fo:block
                font-size="70%"
                font-style="italic">
        <xsl:choose>
          <xsl:when test="$doublesided = 'true'">
            <xsl:attribute name="text-align">start</xsl:attribute>
          </xsl:when>
          <xsl:otherwise>
            <xsl:attribute name="text-align">end</xsl:attribute>
          </xsl:otherwise>
        </xsl:choose>
        <xsl:value-of
                    select="header/title" />
      </fo:block>
    </fo:static-content>
    
    <fo:static-content
            flow-name="odd-footer"
            font-family="{$oddFooterFontFamily}">
      <fo:block
                border-top="0.25pt solid"
                padding-before="6pt"
                text-align="center">
        <xsl:apply-templates
                    select="footer" />
      </fo:block>
      <xsl:call-template
        name="insertPageNumber">
        <xsl:with-param
          name="text-align">end</xsl:with-param>
      </xsl:call-template>
      <xsl:call-template
                name="info" />
    </fo:static-content>
    
    <fo:flow
            flow-name="xsl-region-body">
      <fo:block
              padding-before="24pt"
              padding-after="24pt"
              font-family="{$documentTitleFontFamily}"
              font-size="24pt"
              font-weight="bold"
              id="{generate-id()}">
        <xsl:value-of
          select="header/title" />
      </fo:block>
      <fo:block
                text-align="{$text-align}"
                padding-before="18pt"
                padding-after="18pt">
        <xsl:apply-templates />
      </fo:block>
<!-- Total number of pages calculation... -->
      <fo:block
                id="term" />
    </fo:flow>
  </xsl:template>
  <xsl:template
        match="abstract">
    <fo:block
            font-size="12pt"
            text-align="center"
            space-before="20pt"
            space-after="25pt"
            width="7.5in"
            font-family="{$abstractFontFamily}"
            font-style="italic">
      <xsl:call-template
                name="insertPageBreaks" />
      <xsl:apply-templates />
    </fo:block>
  </xsl:template>
  <xsl:template
        match="notice">
    <fo:block
            font-size="10pt"
            font-family="{$noticeFontFamily}"
            text-align="left"
            space-before="20pt"
            width="7.5in"
            border-top="0.25pt solid"
            border-bottom="0.25pt solid"
            padding-before="6pt"
            padding-after="6pt">
      <xsl:copy-of
                select="@id" />
      <xsl:call-template
                name="insertPageBreaks" />
<!-- insert i18n stuff here --> NOTICE: <xsl:apply-templates />
    </fo:block>
  </xsl:template>
  <xsl:template
        match="anchor">
    <fo:block>
      <xsl:copy-of
                select="@id" />
    </fo:block>
    <xsl:apply-templates />
  </xsl:template>
  <xsl:template match="version">
    <fo:block
            font-family="{$versionFontFamily}"
            font-weight="bold"
            font-size="smaller">
      <xsl:call-template name="insertPageBreaks"/>
      <xsl:apply-templates select="@major"/>
      <xsl:apply-templates select="@minor"/>
      <xsl:apply-templates select="@fix"/>
      <xsl:apply-templates select="@tag"/>
      <xsl:choose>
        <xsl:when test="starts-with(., '$Revision: ')">
<!-- insert i18n stuff here --> Version <xsl:value-of select="substring(., 12, string-length(.) -11-2)"/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:value-of select="."/>
        </xsl:otherwise>
      </xsl:choose>
    </fo:block>
  </xsl:template>
  <xsl:template match="authors">
    <fo:block
            space-before="2em"
            font-family="{$authorsFontFamily}"
            font-weight="bold"
            font-size="smaller">
      <xsl:call-template
                name="insertPageBreaks" />
<!-- insert i18n stuff here --> by <xsl:for-each
                select="person">
        <xsl:value-of
                    select="@name" />
        <xsl:if
                    test="not(position() = last())">, </xsl:if>
      </xsl:for-each>
    </fo:block>
  </xsl:template>
  <xsl:template match="body[count(//section) != 0]">
    <xsl:if test="$disable-toc != 'true' and $toc-max-depth > 0">
      <fo:block
              font-family="{$TOCTitleFontFamily}"
              font-size="12pt"
              font-weight="bold"
              space-after="0.5em"
              space-before="1em"
              text-align="justify"
              width="7.5in"
              id="__toc__">
        <xsl:call-template name="insertPageBreaks"/>
        <!-- insert i18n stuff here -->
        <xsl:text>Table of contents</xsl:text>
      </fo:block>
      <fo:block
        font-family="{$TOCFontFamily}"
        font-size="12pt"
        space-after="5pt"
        space-before="0pt"
        text-align="justify"
        width="7.5in">
        <xsl:if test="$page-break-top-sections">
          <xsl:attribute name="break-after">page</xsl:attribute>
        </xsl:if>
        <xsl:apply-templates select="section" mode="toc"/>
      </fo:block>
    </xsl:if>
    <xsl:apply-templates/>
  </xsl:template>
  <xsl:template match="section" mode="toc">
    <!-- FIXME: see bug FOR-640 -->
    <xsl:param name="depth" select="'1'"/>
    <fo:block space-before="5pt" text-align-last="justify" start-indent=".5em"
      text-indent=".5em">
      <fo:inline>
        <xsl:variable name="id">
          <xsl:choose>
            <xsl:when test="normalize-space(@id)!=''">
              <xsl:value-of select="@id"/>
            </xsl:when>
            <xsl:otherwise>
              <xsl:value-of select="generate-id()"/>
            </xsl:otherwise>
          </xsl:choose>
        </xsl:variable>
        <fo:basic-link internal-destination="{$id}">
          <xsl:value-of
          select="substring('&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;', 0, 2 * $depth - 1)"/>
          <xsl:variable name="section-nr">
            <xsl:number count="section" format="1.1.1.1.1.1.1" level="multiple"/>
          </xsl:variable>
          <xsl:if test="not(starts-with(title, $section-nr))">
            <fo:inline font-size="11pt">
              <xsl:value-of select="$section-nr"/>
            </fo:inline>
            <xsl:text> </xsl:text>
          </xsl:if>
          <xsl:value-of select="title"/>
          <fo:leader leader-pattern="dots"/>
          <fo:page-number-citation ref-id="{$id}"/>
        </fo:basic-link>
      </fo:inline>
      <xsl:if test="$toc-max-depth > $depth">
        <xsl:apply-templates select="section" mode="toc">
          <xsl:with-param name="depth" select="$depth + 1"/>
        </xsl:apply-templates>
      </xsl:if>
    </fo:block>
  </xsl:template>
</xsl:stylesheet>
