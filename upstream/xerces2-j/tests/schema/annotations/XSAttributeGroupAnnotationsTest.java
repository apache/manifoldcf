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
import org.apache.xerces.xs.XSAttributeGroupDefinition;
import org.apache.xerces.xs.XSImplementation;
import org.apache.xerces.xs.XSLoader;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSObjectList;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;

/**
 * Tests methods getAnnotation and getAnnotations of XSAttributeGroupDefinition
 * XSModel components
 * 
 * @author Neil Delima, IBM
 * @version $Id$
 */
public class XSAttributeGroupAnnotationsTest extends TestCase {

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
            System.err.println("SETUP FAILED: XSAttributeGroupAnnotationsTest");
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
    public void testNoAnnotation() {
        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeGroupAnnotationsTest01.xsd"));

        XSAttributeGroupDefinition AG = model.getAttributeGroup("AG",
                "XSAttributeGroupAnnotationsTest");
        XSAnnotation annotation = AG.getAnnotation();

        assertNull(annotation);
    }

    /**
     * Test #2.
     */
    public void testNoAnnotations() {
        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeGroupAnnotationsTest01.xsd"));

        XSAttributeGroupDefinition AG = model.getAttributeGroup("AG",
                "XSAttributeGroupAnnotationsTest");
        XSObjectList annotations = AG.getAnnotations();

        assertEquals(0, annotations.getLength());
    }

    /**
     * Test #3.
     */
    public void testAnnotation() {
        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeGroupAnnotationsTest02.xsd"));

        XSAttributeGroupDefinition AG = model.getAttributeGroup("AG",
                "XSAttributeGroupAnnotationsTest");
        XSAnnotation annotation = AG.getAnnotation();

        String expectedResult = "<annotation id=\"ANNOT1\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSAttributeGroupAnnotationsTest\" >"
                + "<appinfo source=\"None\">"
                + "<!-- No Appinfo -->"
                + "</appinfo><documentation>ANNOT1 should be seen</documentation>"
                + "</annotation>";

        String actual = annotation.getAnnotationString();

        assertEquals(trim(expectedResult), trim(actual));
    }

    /**
     * Test #4.
     */
    public void testAnnotations() {
        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeGroupAnnotationsTest02.xsd"));

        XSAttributeGroupDefinition AG = model.getAttributeGroup("AG",
                "XSAttributeGroupAnnotationsTest");
        XSObjectList annotations = AG.getAnnotations();

        String expectedResult = "<annotation id=\"ANNOT1\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSAttributeGroupAnnotationsTest\" >"
                + "<appinfo source=\"None\">"
                + "<!-- No Appinfo -->"
                + "</appinfo><documentation>ANNOT1 should be seen</documentation>"
                + "</annotation>";

        for (int i = 0; i < annotations.getLength(); i++) {
            XSAnnotation annotation = (XSAnnotation) annotations.item(i);
            String actual = annotation.getAnnotationString();
            assertEquals(trim(expectedResult), trim(actual));
        }

        AG = model.getAttributeGroup("AG2", "XSAttributeGroupAnnotationsTest");
        annotations = AG.getAnnotations();

        expectedResult = "<annotation id=\"ANNOT2\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSAttributeGroupAnnotationsTest\" >"
                + "</annotation>";

        for (int i = 0; i < annotations.getLength(); i++) {
            XSAnnotation annotation = (XSAnnotation) annotations.item(i);
            String actual = annotation.getAnnotationString();
            assertEquals(trim(expectedResult), trim(actual));
        }
    }

    /**
     * Test #5.
     */
    public void testSyntheticAnnotation() {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.TRUE);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeGroupAnnotationsTest03.xsd"));

        XSAttributeGroupDefinition AG = model.getAttributeGroup("AG",
                "XSAttributeGroupAnnotationsTest");
        XSAnnotation annotation = AG.getAnnotation();

        assertNotNull("Synthetic Annotation Null", annotation);
    }

    /**
     * Test #6.
     */
    public void testSyntheticAnnotation6() {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.TRUE);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeGroupAnnotationsTest03.xsd"));

        XSAttributeGroupDefinition AG = model.getAttributeGroup("AG",
                "XSAttributeGroupAnnotationsTest");
        XSObjectList annotations = AG.getAnnotations();

        assertEquals("Synthetic Annotation Empty", 1, annotations.getLength());
    }

    /**
     * Test #7
     */
    public void testNoSyntheticAnnotation() {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.FALSE);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeGroupAnnotationsTest03.xsd"));

        XSAttributeGroupDefinition AG = model.getAttributeGroup("AG",
                "XSAttributeGroupAnnotationsTest");
        XSAnnotation annotation = AG.getAnnotation();

        assertNull("Synthetic Annotation Not Null", annotation);
    }

    /**
     * Test #8
     */
    public void testSyntheticAnnotationsAbsent() {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.TRUE);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeGroupAnnotationsTest03.xsd"));

        XSAttributeGroupDefinition AG = model.getAttributeGroup("AG1",
                "XSAttributeGroupAnnotationsTest");
        XSObjectList annotations = AG.getAnnotations();

        assertEquals("Synthetic Annotation Empty", 0, annotations.getLength());
    }

    /**
     * Test #9.
     */
    public void testAnnotationsInGroup() {
        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSAttributeGroupAnnotationsTest04.xsd"));

        XSAttributeGroupDefinition AG = model.getAttributeGroup("AG",
                "XSAttributeGroupAnnotationsTest");

        XSObjectList annotations = AG.getAnnotations();

        String expectedResult = "<annotation id=\"ANNOT1\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSAttributeGroupAnnotationsTest\" >"
                + "<appinfo source=\"None\">"
                + "<!-- No Appinfo -->"
                + "</appinfo><documentation>ANNOT1 should be seen</documentation>"
                + "</annotation>";

        for (int i = 0; i < annotations.getLength(); i++) {
            XSAnnotation annotation = (XSAnnotation) annotations.item(i);
            String actual = annotation.getAnnotationString();
            assertEquals(trim(expectedResult), trim(actual));
        }
    }

    public static void main(String args[]) {
        junit.textui.TestRunner.run(XSAttributeGroupAnnotationsTest.class);
    }
}
