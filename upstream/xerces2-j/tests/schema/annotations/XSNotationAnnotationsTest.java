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
import org.apache.xerces.xs.XSImplementation;
import org.apache.xerces.xs.XSLoader;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSNotationDeclaration;
import org.apache.xerces.xs.XSObjectList;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;

/**
 * Tests methods getAnnotation and getAnnotations on XSNotationDeclaration
 * XSModel components.
 * 
 * @author Neil Delima, IBM
 * @version $Id$
 */
public class XSNotationAnnotationsTest extends TestCase {

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
            System.err.println("SETUP FAILED: XSNotationAnnotationsTest");
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
                .loadURI(getResourceURL("XSNotationAnnotationsTest01.xsd"));

        XSNotationDeclaration notation = model.getNotationDeclaration(
                "notation1", "XSNotationAnnotationsTest");
        XSAnnotation annotation = notation.getAnnotation();
        assertNull("TEST1_NO_ANNOTATION", annotation);

        XSObjectList annotations = notation.getAnnotations();
        assertEquals("TEST1_NO_ANNOTATIONS", 0, annotations.getLength());

    }

    /**
     * Test #2.
     */
    public void testSyntheticAnnotations() {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.TRUE);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSNotationAnnotationsTest01.xsd"));

        XSNotationDeclaration notation = model.getNotationDeclaration(
                "notation2", "XSNotationAnnotationsTest");
        XSAnnotation annotation = notation.getAnnotation();
        assertNotNull("TEST2_ANNOTATION", annotation);

        XSObjectList annotations = notation.getAnnotations();
        assertEquals("TEST2_ANNOTATIONS", 1, annotations.getLength());
    }

    /**
     * Test #3.
     */
    public void testNoSyntheticAnnotations() {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.FALSE);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSNotationAnnotationsTest01.xsd"));

        XSNotationDeclaration notation = model.getNotationDeclaration(
                "notation2", "XSNotationAnnotationsTest");
        XSAnnotation annotation = notation.getAnnotation();
        assertNull("TEST3_NO_ANNOTATION", annotation);

        XSObjectList annotations = notation.getAnnotations();
        assertEquals("TEST3_NO_ANNOTATIONS", 0, annotations.getLength());

    }

    /**
     * Test #4.
     */
    public void testAnnotations() {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.FALSE);
        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSNotationAnnotationsTest01.xsd"));

        annotationsTest4(model);

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.TRUE);
        model = fSchemaLoader
                .loadURI(getResourceURL("XSNotationAnnotationsTest01.xsd"));

        annotationsTest4(model);
    }

    private void annotationsTest4(XSModel model) {
        String expected = "<annotation id=\"ANNOT1\" xmlns=\"http://www.w3.org/2001/XMLSchema\" xmlns:sv=\"XSNotationAnnotationsTest\" xmlns:sn=\"SyntheticAnnotation\" > "
                + "<appinfo>APPINFO1</appinfo>" + "</annotation>";

        XSNotationDeclaration notation = model.getNotationDeclaration(
                "notation3", "XSNotationAnnotationsTest");
        XSAnnotation annotation = notation.getAnnotation();
        assertNotNull("TEST4_ANNOTATION", annotation);
        assertEquals("TEST4_ANNOTATION_EQ", trim(expected), trim(annotation
                .getAnnotationString()));

        XSObjectList annotations = notation.getAnnotations();
        assertEquals("TEST4_ANNOTATIONS", 1, annotations.getLength());
        assertEquals(
                "TEST4_ANNOTATIONS_EQ",
                trim(expected),
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #5.
     */
    public void testAnnotationsWithSynth() {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.FALSE);
        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSNotationAnnotationsTest01.xsd"));

        annotationsTest5(model);

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        Boolean.TRUE);
        model = fSchemaLoader
                .loadURI(getResourceURL("XSNotationAnnotationsTest01.xsd"));

        annotationsTest5(model);
    }

    private void annotationsTest5(XSModel model) {
        String expected = "<annotation sn:att=\"synth\" id=\"ANNOT2\" xmlns=\"http://www.w3.org/2001/XMLSchema\" xmlns:sv=\"XSNotationAnnotationsTest\" xmlns:sn=\"SyntheticAnnotation\" > "
                + "<documentation>DOC1</documentation>" + "</annotation>";

        XSNotationDeclaration notation = model.getNotationDeclaration(
                "notation4", "XSNotationAnnotationsTest");
        XSAnnotation annotation = notation.getAnnotation();
        assertNotNull("TEST5_ANNOTATION", annotation);
        assertEquals("TEST5_ANNOTATION_EQ", trim(expected), trim(annotation
                .getAnnotationString()));

        XSObjectList annotations = notation.getAnnotations();
        assertEquals("TEST5_ANNOTATIONS", 1, annotations.getLength());
        assertEquals(
                "TEST5_ANNOTATIONS_EQ",
                trim(expected),
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    public static void main(String args[]) {
        junit.textui.TestRunner.run(XSNotationAnnotationsTest.class);
    }
}
