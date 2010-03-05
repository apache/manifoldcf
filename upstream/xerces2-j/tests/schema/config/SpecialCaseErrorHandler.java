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

package schema.config;

import java.util.HashMap;
import java.util.Iterator;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * @author Peter McCracken, IBM
 * @version $Id$
 */
public class SpecialCaseErrorHandler implements ErrorHandler {
    
    public static final boolean DEBUG = false;
    
    private HashMap errors;
    
    public SpecialCaseErrorHandler(String[] specialCases) {
        errors = new HashMap();
        for (int i = 0; i < specialCases.length; ++i) {
            errors.put(specialCases[i], Boolean.FALSE);
        }
    }
    
    public void reset() {
        for (Iterator iter = errors.keySet().iterator(); iter.hasNext();) {
            String error = (String) iter.next();
            errors.put(error, Boolean.FALSE);
        }
    }
    
    public void warning(SAXParseException arg0) throws SAXException {
        if (DEBUG) {
            System.err.println(arg0.getMessage());
        }
    }
    
    public void error(SAXParseException arg0) throws SAXException {
        if (DEBUG) {
            System.err.println(arg0.getMessage());
        }
        for (Iterator iter = errors.keySet().iterator(); iter.hasNext();) {
            String error = (String) iter.next();
            if (arg0.getMessage().startsWith(error)) {
                errors.put(error, Boolean.TRUE);
            }
        }
    }
    
    public void fatalError(SAXParseException arg0) throws SAXException {
        throw arg0;
    }
    
    public boolean specialCaseFound(String key) {
        return ((Boolean) errors.get(key)).booleanValue();
    }
}
