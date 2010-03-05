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

package org.apache.xerces.util;

/**
 * <p>A utility for generating efficient code for initializing 
 * the character flags array of org.apache.xerces.util.XML11Char.
 * This class should be updated if the array changes, followed
 * by regeneration of the code for XML11Char.</p>
 *
 * @author Michael Glavassevich, IBM 
 * @author Glenn Marcy, IBM
 * @author Andy Clark, IBM
 * @author Arnaud Le Hors, IBM
 * @author Neil Graham, IBM
 * 
 * @version $Id$
 */
public class XML11CharGenerator {

    //
    // Constants
    //

    /** Character flags for XML 1.1. */
    private static final byte XML11CHARS [] = new byte [1 << 16];

    /** XML 1.1 Valid character mask. */
    public static final int MASK_XML11_VALID = 0x01;

    /** XML 1.1 Space character mask. */
    public static final int MASK_XML11_SPACE = 0x02;

    /** XML 1.1 Name start character mask. */
    public static final int MASK_XML11_NAME_START = 0x04;

    /** XML 1.1 Name character mask. */
    public static final int MASK_XML11_NAME = 0x08;

    /** XML 1.1 control character mask */
    public static final int MASK_XML11_CONTROL = 0x10;

    /** XML 1.1 content for external entities (valid - "special" chars - control chars) */
    public static final int MASK_XML11_CONTENT = 0x20;

    /** XML namespaces 1.1 NCNameStart */
    public static final int MASK_XML11_NCNAME_START = 0x40;

    /** XML namespaces 1.1 NCName */
    public static final int MASK_XML11_NCNAME = 0x80;
    
    /** XML 1.1 content for internal entities (valid - "special" chars) */
    public static final int MASK_XML11_CONTENT_INTERNAL = MASK_XML11_CONTROL | MASK_XML11_CONTENT; 

    //
    // Static initialization
    //

    static {
        
        /****
         * XML 1.1 initialization.
         */

        // [2]: Char ::= [#x1-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
        // 
        // NOTE: This range is Char - (RestrictedChar | S | #x85 | #x2028).
        int xml11NonWhitespaceRange  [] = {
                0x21, 0x7E, 0xA0, 0x2027, 0x2029, 0xD7FF, 0xE000, 0xFFFD, 
        };

        // NOTE:  this does *NOT* correspond to the S production
        // from XML 1.0.  Rather, it corresponds to S+chars that are
        // involved in whitespace normalization.  It's handy
        // in a few places in the entity scanner where we need to detect the
        // presence of all characters to be considered whitespace.
        int xml11WhitespaceChars [] = {
            0x9, 0xA, 0xD, 0x20, 0x85, 0x2028,
        };
        
        // [2a]: RestrictedChar ::= [#x1-#x8] | [#xB-#xC] | [#xE-#x1F] | 
        //                          [#x7F-#x84] | [#x86-#x9F] 
        int xml11ControlCharRange [] = {
            0x1, 0x8, 0xB, 0xC, 0xE, 0x1F, 0x7f, 0x84, 0x86, 0x9f,
        };
        
        // [4]: NameStartChar ::= ":" | [A-Z] | "_" | [a-z] | 
        //                        [#xC0-#xD6] | [#xD8-#xF6] | [#xF8-#x2FF] | 
        //                        [#x370-#x37D] | [#x37F-#x1FFF] | [#x200C-#x200D] | 
        //                        [#x2070-#x218F] | [#x2C00-#x2FEF] | [#x3001-#xD7FF] | 
        //                        [#xF900-#xFDCF] | [#xFDF0-#xFFFD] | [#x10000-#xEFFFF]
        int xml11NameStartCharRange [] = {
            ':', ':', 'A', 'Z', '_', '_', 'a', 'z', 
            0xC0, 0xD6, 0xD8, 0xF6, 0xF8, 0x2FF,
            0x370, 0x37D, 0x37F, 0x1FFF, 0x200C, 0x200D,
            0x2070, 0x218F, 0x2C00, 0x2FEF, 0x3001, 0xD7FF,
            0xF900, 0xFDCF, 0xFDF0, 0xFFFD,
        };
        
        // [4a]:  NameChar ::= NameStartChar | "-" | "." | [0-9] | #xB7 | 
        //                     [#x0300-#x036F] | [#x203F-#x2040] 
        int xml11NameCharRange [] = {
            '-', '-', '.', '.', '0', '9', 0xB7, 0xB7, 
            0x0300, 0x036F, 0x203F, 0x2040,
        };

        //
        // SpecialChar ::= '<', '&', '\n', '\r', ']'
        //

        int xml11SpecialChars[] = {
            '<', '&', '\n', '\r', ']',
        };

        // initialize XML11CHARS
        for(int i=0; i<xml11NonWhitespaceRange.length; i+=2) {
            for(int j=xml11NonWhitespaceRange[i]; j<=xml11NonWhitespaceRange[i+1]; j++) {
                XML11CHARS[j] |= MASK_XML11_VALID | MASK_XML11_CONTENT;
            }
        }
        for(int i=0; i<xml11WhitespaceChars.length; i++) {
            XML11CHARS[xml11WhitespaceChars[i]] |= MASK_XML11_VALID | MASK_XML11_SPACE | MASK_XML11_CONTENT;
        }
        for(int i=0; i<xml11ControlCharRange.length; i+=2) {
            for(int j=xml11ControlCharRange[i]; j<=xml11ControlCharRange[i+1]; j++) {
                XML11CHARS[j] |= MASK_XML11_VALID | MASK_XML11_CONTROL;
            }
        }
        for (int i = 0; i < xml11NameStartCharRange.length; i+=2) {
            for(int j=xml11NameStartCharRange[i]; j<=xml11NameStartCharRange[i+1]; j++) {
                XML11CHARS[j] |= MASK_XML11_NAME_START | MASK_XML11_NAME |
                        MASK_XML11_NCNAME_START | MASK_XML11_NCNAME;
            }
        }
        for (int i=0; i<xml11NameCharRange.length; i+=2) {
            for(int j=xml11NameCharRange[i]; j<=xml11NameCharRange[i+1]; j++) {
                XML11CHARS[j] |= MASK_XML11_NAME | MASK_XML11_NCNAME;
            }
        }

        // remove ':' from allowable MASK_NCNAME_START and MASK_NCNAME chars
        XML11CHARS[':'] &= ~(MASK_XML11_NCNAME_START | MASK_XML11_NCNAME);

        for(int i=0;i<xml11SpecialChars.length; i++) {
            XML11CHARS[xml11SpecialChars[i]] &= (~MASK_XML11_CONTENT);
        }
    } // <clinit>()

    /**
     * Writes the code for initializing the character flags
     * array for XML11Char to System.out.
     */
    public static void main(String[] args) {
        ArrayFillingCodeGenerator.generateByteArray("XML11CHARS", XML11CHARS, System.out);
    }
}
