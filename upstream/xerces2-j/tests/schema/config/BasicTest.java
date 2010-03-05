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

import junit.framework.Assert;

import org.apache.xerces.dom.PSVIElementNSImpl;
import org.apache.xerces.xs.ItemPSVI;

/**
 * @author Peter McCracken, IBM
 * @version $Id$
 */
public class BasicTest extends BaseTest {
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(BasicTest.class);
    }
    
    protected String getXMLDocument() {
        return "base.xml";
    }
    
    protected String getSchemaFile() {
        return "base.xsd";
    }
    
    public BasicTest(String name) {
        super(name);
    }
    
    public void testSimpleValidation() {
        try {
            validateDocument();
        } catch (Exception e) {
            Assert.fail("Validation failed: " + e.getMessage());
        }
        doValidityAsserts();
    }
    
    public void testSimpleValidationWithTrivialXSIType() {
        ((PSVIElementNSImpl) fRootNode).setAttributeNS(
                "http://www.w3.org/2001/XMLSchema-instance", "type", "X");
        try {
            validateDocument();
        } catch (Exception e) {
            Assert.fail("Validation failed: " + e.getMessage());
        }
        doValidityAsserts();
    }
    
    private void doValidityAsserts() {
        assertValidity(ItemPSVI.VALIDITY_VALID, fRootNode.getValidity());
        assertValidationAttempted(ItemPSVI.VALIDATION_FULL, fRootNode
                .getValidationAttempted());
        assertElementName("A", fRootNode.getElementDeclaration().getName());
        assertElementNamespaceNull(fRootNode.getElementDeclaration()
                .getNamespace());
        assertTypeName("X", fRootNode.getTypeDefinition().getName());
        assertTypeNamespaceNull(fRootNode.getTypeDefinition().getNamespace());
    }
}
