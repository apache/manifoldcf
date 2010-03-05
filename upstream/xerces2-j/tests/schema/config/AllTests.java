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

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * @author Peter McCracken, IBM
 * @version $Id$
 */
public class AllTests {
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(AllTests.suite());
    }
    
    public static Test suite() {
        TestSuite suite = new TestSuite("Tests for various schema validation configurations.");
        suite.addTestSuite(BasicTest.class);
        suite.addTestSuite(RootTypeDefinitionTest.class);
        suite.addTestSuite(RootSimpleTypeDefinitionTest.class);
        suite.addTestSuite(IgnoreXSIType_C_A_Test.class);
        suite.addTestSuite(IgnoreXSIType_C_C_Test.class);
        suite.addTestSuite(IgnoreXSIType_A_A_Test.class);
        suite.addTestSuite(IgnoreXSIType_A_C_Test.class);
        suite.addTestSuite(IgnoreXSIType_C_AC_Test.class);
        suite.addTestSuite(IgnoreXSIType_C_CA_Test.class);
        suite.addTestSuite(IdIdrefCheckingTest.class);
        suite.addTestSuite(UnparsedEntityCheckingTest.class);
        suite.addTestSuite(IdentityConstraintCheckingTest.class);
        suite.addTestSuite(UseGrammarPoolOnly_True_Test.class);
        suite.addTestSuite(UseGrammarPoolOnly_False_Test.class);
        suite.addTestSuite(FixedAttrTest.class);
        suite.addTestSuite(FeaturePropagationTest.class);
        return suite;
    }
}
