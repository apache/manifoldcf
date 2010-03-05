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

package schema.annotations;

import org.apache.xerces.xs.XSAnnotation;
import org.apache.xerces.xs.XSAttributeUse;
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSImplementation;
import org.apache.xerces.xs.XSLoader;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSObjectList;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;

/**
 * Tests methods getAnnotations on XSAttributeUse XSModel components.
 * 
 * @author Neil Delima, IBM
 * @version $Id$
 */
public class XSAttributeUseAnnotationsTest extends TestCase {

    /**
     * Members that are initialized by setUp() and cleaned up by tearDown().
     * <p>
     * 
     * Note that setUp() and tearDown() are called for <em>each</em> test.
     * Different tests do <em>not</em> share the same instance member.
     */
    private XSLoader fSchemaLoader;

    private DOMConfiguration fConfig;

    /**
     * This method is called before every test case method, to set up the test
     * fixture.
     */
    protected void setUp() {
        try {
            // get DOM Implementation using DOM Registry
            System.setProperty(DOMImplementationRegistry.PROPERTY,
                    "org.apache.xerces.dom.DOMXSImplementationSourceImpl");
            DOMImplementationRegistry registry = DOMImplementationRegistry
                    .newInstance();

            XSImplementation impl = (XSImplementation) registry
                    .getDOMImplementation("XS-Loader");

            fSchemaLoader = impl.createXSLoader(null);

            fConfig = fSchemaLoader.getConfig();

            // set validation feature
            fConfig.setParameter("validate", Boolean.TRUE);

        } catch (Exception e) {
            fail("Expecting a NullPointerException");
            System.err.println("SETUP FAILED: XSAttributeUseAnnotationsTest");
        }
    }

    /**
     * This method is called before every test case method, to tears down the
     * test fixture.
     */
    protected void tearDown() {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.FALSE);
    }

    /**
     * Test #1.
     */
    public void testAttrUseNoAnnotations() {
        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeUseAnnotationsTest01.xsd"));

        XSComplexTypeDefinition ct = (XSComplexTypeDefinition) model
                .getTypeDefinition("CT", "XSAttributeUseAnnotationsTest");

        XSObjectList attrUses = ct.getAttributeUses();

        // Attr ref
        XSAttributeUse attr = (XSAttributeUse) attrUses.item(0);
        XSObjectList annotations = attr.getAnnotations();
        assertEquals("REF", 0, annotations.getLength());

        // local attr
        attr = (XSAttributeUse) attrUses.item(1);
        annotations = attr.getAnnotations();
        assertEquals("LOCAL", 0, annotations.getLength());

        // attr grp ref
        attr = (XSAttributeUse) attrUses.item(2);
        annotations = attr.getAnnotations();
        assertEquals("GROUP", 0, annotations.getLength());
    }

    /**
     * Test #2.
     */
    public void testAttrUseNoSynthAnnotations() {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.FALSE);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeUseAnnotationsTest01.xsd"));

        XSComplexTypeDefinition ct = (XSComplexTypeDefinition) model
                .getTypeDefinition("CT", "XSAttributeUseAnnotationsTest");

        XSObjectList attrUses = ct.getAttributeUses();

        // Attr ref
        XSAttributeUse attr = (XSAttributeUse) attrUses.item(0);
        XSObjectList annotations = attr.getAnnotations();
        assertEquals("REF", 0, annotations.getLength());

        // local attr
        attr = (XSAttributeUse) attrUses.item(1);
        annotations = attr.getAnnotations();
        assertEquals("LOCAL", 0, annotations.getLength());

        // attr grp ref
        attr = (XSAttributeUse) attrUses.item(2);
        annotations = attr.getAnnotations();
        assertEquals("GROUP", 0, annotations.getLength());
    }

    /**
     * Test #3
     */
    public void testAttrUseSynthAnnotations() {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.TRUE);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeUseAnnotationsTest01.xsd"));

        XSComplexTypeDefinition ct = (XSComplexTypeDefinition) model
                .getTypeDefinition("CT", "XSAttributeUseAnnotationsTest");

        XSObjectList attrUses = ct.getAttributeUses();

        // Attr ref
        XSAttributeUse attr = (XSAttributeUse) attrUses.item(0);
        XSObjectList annotations = attr.getAnnotations();
        assertEquals("REF", 1, annotations.getLength());

        // local attr
        attr = (XSAttributeUse) attrUses.item(1);
        annotations = attr.getAnnotations();
        assertEquals("LOCAL", 1, annotations.getLength());

        // attr grp ref
        attr = (XSAttributeUse) attrUses.item(2);
        annotations = attr.getAnnotations();
        assertEquals("GROUP", 0, annotations.getLength());

    }

    /**
     * Test #4.
     */
    public void testAttrUseAnnotations() {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.FALSE);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeUseAnnotationsTest02.xsd"));

        XSComplexTypeDefinition ct = (XSComplexTypeDefinition) model
                .getTypeDefinition("CT", "XSAttributeUseAnnotationsTest");

        XSObjectList attrUses = ct.getAttributeUses();

        // Attr ref
        XSAttributeUse attr = (XSAttributeUse) attrUses.item(0);
        XSObjectList annotations = attr.getAnnotations();
        assertEquals("REF", 1, annotations.getLength());

        // local attr
        attr = (XSAttributeUse) attrUses.item(1);
        annotations = attr.getAnnotations();
        assertEquals("LOCAL", 1, annotations.getLength());

        // attr grp ref
        // The attribute in the group has an annotation element
        attr = (XSAttributeUse) attrUses.item(2);
        annotations = attr.getAnnotations();
        assertEquals("GROUP", 1, annotations.getLength());
    }

    /**
     * Test #5.
     */
    public void testAttrUseAnnotationsSynthetic() {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.TRUE);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeUseAnnotationsTest02.xsd"));

        XSComplexTypeDefinition ct = (XSComplexTypeDefinition) model
                .getTypeDefinition("CT", "XSAttributeUseAnnotationsTest");

        XSObjectList attrUses = ct.getAttributeUses();

        // Attr ref
        XSAttributeUse attr = (XSAttributeUse) attrUses.item(0);
        XSObjectList annotations = attr.getAnnotations();
        assertEquals("REF", 1, annotations.getLength());

        // local attr
        attr = (XSAttributeUse) attrUses.item(1);
        annotations = attr.getAnnotations();
        assertEquals("LOCAL", 1, annotations.getLength());

        // attr grp ref
        // The attribute in the group has an annotation element
        attr = (XSAttributeUse) attrUses.item(2);
        annotations = attr.getAnnotations();
        assertEquals("GROUP", 1, annotations.getLength());
    }

    /**
     * Test #6.
     */
    public void testWildAttrUseAnnotationsSynthetic() {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.TRUE);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeUseAnnotationsTest03.xsd"));

        XSComplexTypeDefinition ct = (XSComplexTypeDefinition) model
                .getTypeDefinition("CT", "XSAttributeUseAnnotationsTest");

        XSObjectList attrUses = ct.getAttributeUses();

        // Attr ref
        XSAttributeUse attr = (XSAttributeUse) attrUses.item(0);
        XSObjectList annotations = attr.getAnnotations();
        assertEquals("REF", 1, annotations.getLength());

        XSAnnotation annotation = attr.getAttrDeclaration().getAnnotation();
        String expected = "<annotation sn:att=\"ATT1\"  id=\"ANNOT1\" xmlns=\"http://www.w3.org/2001/XMLSchema\" xmlns:sv=\"XSAttributeUseAnnotationsTest\" xmlns:sn=\"SyntheticAnnotationNS\" > "
                + "<appinfo>APPINFO1</appinfo>" + "</annotation>";
        assertEquals("REF_STRING", trim(expected), trim(annotation
                .getAnnotationString()));

        annotations = attr.getAttrDeclaration().getAnnotations();
        assertEquals(
                "REF_STRING_ANNOTATIONS",
                trim(expected),
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        // local attr
        attr = (XSAttributeUse) attrUses.item(1);
        annotations = attr.getAnnotations();
        assertEquals("LOCAL", 1, annotations.getLength());

        annotation = attr.getAttrDeclaration().getAnnotation();
        expected = "<annotation sn:att=\"ATT11\"  id=\"ANNOT6\" xmlns=\"http://www.w3.org/2001/XMLSchema\" xmlns:sv=\"XSAttributeUseAnnotationsTest\" xmlns:sn=\"SyntheticAnnotationNS\" > "
                + "<appinfo>APPINFO6</appinfo>" + "</annotation>";
        assertEquals("LOCAL_STRING", trim(expected), trim(annotation
                .getAnnotationString()));

        annotations = attr.getAttrDeclaration().getAnnotations();
        assertEquals(
                "LOCAL_STRING_ANNOTATIONS",
                trim(expected),
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        // attr grp ref
        // The attribute in the group has an annotation element
        attr = (XSAttributeUse) attrUses.item(2);
        annotations = attr.getAnnotations();
        assertEquals("GROUP", 1, annotations.getLength());

        annotation = attr.getAttrDeclaration().getAnnotation();
        expected = "<annotation id=\"ANNOT3\" xmlns=\"http://www.w3.org/2001/XMLSchema\" xmlns:sv=\"XSAttributeUseAnnotationsTest\" xmlns:sn=\"SyntheticAnnotationNS\" > "
                + "<appinfo>APPINFO3</appinfo>" + "</annotation>";
        assertEquals("GROUP_STRING", trim(expected), trim(annotation
                .getAnnotationString()));

        annotations = attr.getAttrDeclaration().getAnnotations();
        assertEquals(
                "GROUP_STRING_ANNOTATIONS",
                trim(expected),
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

    }

    public static void main(String args[]) {
        junit.textui.TestRunner.run(XSAttributeUseAnnotationsTest.class);
    }
}
