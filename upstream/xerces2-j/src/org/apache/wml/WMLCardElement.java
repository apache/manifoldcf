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
 * <p>'card' element is the basic display unit of WML. A WML decks
 * contains a collection of cards.
 * (Section 11.5, WAP WML Version 16-Jun-1999)</p>
 *
 * @version $Id$
 * @author <a href="mailto:david@topware.com.tw">David Li</a>
 */

public interface WMLCardElement extends WMLElement {

    /**
     * 'onenterbackward' specifies the event to occur when a user
     * agent into a card using a 'go' task
     * (Section 11.5.1, WAP WML Version 16-Jun-1999)
     */
    public void setOnEnterBackward(String href);
    public String getOnEnterBackward();

    /**
     * 'onenterforward' specifies the event to occur when a user
     * agent into a card using a 'prev' task
     * (Section 11.5.1, WAP WML Version 16-Jun-1999)
     */
    public void setOnEnterForward(String href);
    public String getOnEnterForward();

    /**
     * 'onenterbackward' specifies the event to occur when a timer expires
     * (Section 11.5.1, WAP WML Version 16-Jun-1999)
     */
    public void setOnTimer(String href);
    public String getOnTimer();

    /**
     * 'title' specifies a advisory info about the card
     * (Section 11.5.2, WAP WML Version 16-Jun-1999)
     */
    public void setTitle(String newValue);
    public String getTitle();

    /**
     * 'newcontext' specifies whether a browser context should be
     * re-initialized upon entering the card. Default to be false.
     * (Section 11.5.2, WAP WML Version 16-Jun-1999)
     */
    public void setNewContext(boolean newValue);
    public boolean getNewContext();
    
    /**
     *  'ordered' attribute specifies a hit to user agent about the
     *  organization of the card's content 
     * (Section 11.5.2, WAP WML Version 16-Jun-1999)
     */
    public void setOrdered(boolean newValue);
    public boolean getOrdered();

    /**
     * 'xml:lang' specifics the natural or formal language in which
     * the document is written.  
     * (Section 8.8, WAP WML Version 16-Jun-1999) 
     */
    public void setXmlLang(String newValue);
    public String getXmlLang();
}
