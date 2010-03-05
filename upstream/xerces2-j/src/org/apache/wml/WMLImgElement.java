/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wml;

/**
 * <p>The interface is modeled after DOM1 Spec for HTML from W3C.
 * The DTD used in this DOM model is from 
 * <a href="http://www.wapforum.org/DTD/wml_1.1.xml">
 * http://www.wapforum.org/DTD/wml_1.1.xml</a></p>
 *
 * <p>'img' specifies an image in a text flow
 * (Section 11.9, WAP WML Version 16-Jun-1999)</p>
 *
 * @version $Id$
 * @author <a href="mailto:david@topware.com.tw">David Li</a>
 */
public interface WMLImgElement extends WMLElement {

    /**
     * 'alt' specifies an alternative text for the image
     * (Section 11.9, WAP WML Version 16-Jun-1999)
     */
    public void setAlt(String newValue);
    public String getAlt();

    /**
     * 'src' specifies URI for the source images
     * (Section 11.9, WAP WML Version 16-Jun-1999)
     */
    public void setSrc(String newValue);
    public String getSrc();

    /**
     * 'localsrc' specifies an alternative internal representation of
     * the image.
     * (Section 11.9, WAP WML Version 16-Jun-1999) 
     */
    public void setLocalSrc(String newValue);
    public String getLocalSrc();

    /**
     * 'vspace' specifies the abount of white space to be inserted
     * above and below
     * (Section 11.9, WAP WML Version 16-Jun-1999) 
     */
    public void setVspace(String newValue);
    public String getVspace();

    /**
     * 'hspace' specifies the abount of white space to be inserted
     * left and right
     * (Section 11.9, WAP WML Version 16-Jun-1999) 
     */
    public void setHspace(String newValue);
    public String getHspace();

    /**
     * 'align' specifies the alignment of the image within the text
     * flow.
     * (Section 11.8, WAP WML Version 16-Jun-1999)
     */
    public void setAlign(String newValue);
    public String getAlign();

    /**
     * 'width' specifies the width of an image.
     * (Section 11.9, WAP WML Version 16-Jun-1999)
     */
    public void setWidth(String newValue);
    public String getWidth();

    /**
     * 'height' specifies the height of an image.
     * (Section 11.9, WAP WML Version 16-Jun-1999)
     */
    public void setHeight(String newValue);
    public String getHeight();

    /**
     * The xml:lang that specifics the natural or formal language in
     * which the document is written.
     * (Section 8.8, WAP WML Version 16-Jun-1999)
     */
    public void setXmlLang(String newValue);
    public String getXmlLang();
}
