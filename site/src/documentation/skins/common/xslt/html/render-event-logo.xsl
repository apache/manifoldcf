<?xml version="1.0" encoding="utf-8"?>
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
<!--
A simple callable template that renders a logo for an entity. The logo will 
be a hyperlink and may include an image (with width and height if specified)
or else it will just include the specified text.

Note that text and image are mandatory parts of the template.
-->
<xsl:stylesheet
  version="1.0"
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:template name="render-event-logo">
    <xsl:param name="url"/>
    <xsl:param name="logo"/>
    <a href="{$url}">
    <xsl:choose>
      <xsl:when test="$logo and not($logo = '')">
        <img class="logoImage">
          <xsl:attribute name="src">
            <xsl:value-of select="$logo"/>
          </xsl:attribute>
        </img>
      </xsl:when>
    </xsl:choose></a>
  </xsl:template>
</xsl:stylesheet>
