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

import org.apache.xerces.xs.ItemPSVI;

/**
 * @author Peter McCracken, IBM
 * @version $Id$
 */
public class UseGrammarPoolOnly_True_Test extends BaseTest {
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(UseGrammarPoolOnly_True_Test.class);
    }
    
    protected String getXMLDocument() {
        return "otherNamespace.xml";
    }
    
    protected String getSchemaFile() {
        return "base.xsd";
    }
    
    protected boolean getUseGrammarPoolOnly() {
        return true;
    }
    
    public UseGrammarPoolOnly_True_Test(String name) {
        super(name);
    }
    
    /**
     * The purpose of this test is to check if setting the USE_GRAMMAR_POOL_ONLY
     * feature to true causes external schemas to not be read. This
     * functionality already existed prior to adding the new schema features
     * for Xerces 2.8.0; however, because the class that controlled it changed, 
     * this test simply ensures that the existing functionality did not disappear.
     * -PM
     */
    public void testUsingOnlyGrammarPool() {
        try {
            validateDocument();
        } 
        catch (Exception e) {
            Assert.fail("Validation failed: " + e.getMessage());
        }
        
        assertValidity(ItemPSVI.VALIDITY_NOTKNOWN, fRootNode.getValidity());
        assertValidationAttempted(ItemPSVI.VALIDATION_NONE, fRootNode
                .getValidationAttempted());
        assertElementNull(fRootNode.getElementDeclaration());
        assertAnyType(fRootNode.getTypeDefinition());
    }
}
