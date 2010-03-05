/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.commons.httpclient;

import junit.framework.*;

/**
 * Simple tests for {@link NameValuePair}.
 *
 * @author Rodney Waldhoff
 * @version $Id$
 */
public class TestNVP extends TestCase {

    // ------------------------------------------------------------ Constructor
    public TestNVP(String testName) {
        super(testName);
    }

    // ------------------------------------------------------------------- Main
    public static void main(String args[]) {
        String[] testCaseName = { TestNVP.class.getName() };
        junit.textui.TestRunner.main(testCaseName);
    }

    // ------------------------------------------------------- TestCase Methods

    public static Test suite() {
        return new TestSuite(TestNVP.class);
    }

    // ------------------------------------------------------ Protected Methods

    protected NameValuePair makePair() {
        return new NameValuePair();
    }

    protected NameValuePair makePair(String name, String value) {
        return new NameValuePair(name,value);
    }


    // ----------------------------------------------------------- Test Methods

    public void testGet() {
        NameValuePair pair = makePair("name 1","value 1");
        assertEquals("name 1",pair.getName());
        assertEquals("value 1",pair.getValue());
    }

    public void testSet() {
        NameValuePair pair = makePair();
        assertTrue(null == pair.getName());
        assertTrue(null == pair.getValue());
        pair.setName("name");
        assertEquals("name",pair.getName());
        pair.setValue("value");
        assertEquals("value",pair.getValue());
    }

    public void testHashCode() {
        NameValuePair param1 = new NameValuePair("name1", "value1");
        NameValuePair param2 = new NameValuePair("name2", "value2");
        NameValuePair param3 = new NameValuePair("name1", "value1");
        assertTrue(param1.hashCode() != param2.hashCode());
        assertTrue(param1.hashCode() == param3.hashCode());
    }
    
    public void testEquals() {
        NameValuePair param1 = new NameValuePair("name1", "value1");
        NameValuePair param2 = new NameValuePair("name2", "value2");
        NameValuePair param3 = new NameValuePair("name1", "value1");
        assertFalse(param1.equals(param2));
        assertFalse(param1.equals(null));
        assertFalse(param1.equals("name1 = value1"));
        assertTrue(param1.equals(param1));
        assertTrue(param2.equals(param2));
        assertTrue(param1.equals(param3));
    }
    
}
