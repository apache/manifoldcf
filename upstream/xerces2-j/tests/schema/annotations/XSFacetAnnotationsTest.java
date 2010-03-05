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
import org.apache.xerces.xs.XSAttributeDeclaration;
import org.apache.xerces.xs.XSAttributeUse;
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSFacet;
import org.apache.xerces.xs.XSImplementation;
import org.apache.xerces.xs.XSLoader;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSSimpleTypeDefinition;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;

/**
 * Tests methods getAnnotation and getAnnotations on XSFacet XSModel components.
 * 
 * @author Neil Delima, IBM
 * @version $Id$
 */
public class XSFacetAnnotationsTest extends TestCase {

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
            System.err.println("SETUP FAILED: XSFacetTest");
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
    public void testST1Annotation() {
        st1AnnotationTest(Boolean.FALSE);
        st1AnnotationTest(Boolean.TRUE);
    }

    private void st1AnnotationTest(Boolean synth) {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) model
                .getTypeDefinition("ST1", "XSFacetTest");
        // Item 0 is whitespace
        XSFacet length = (XSFacet) st.getFacets().item(1);

        XSAnnotation annotation = length.getAnnotation();
        assertNull("TEST1_NO_ANNOTATION_" + synth, annotation);
        XSObjectList annotations = length.getAnnotations();
        assertEquals("TEST1_NO_ANNOTATIONS_" + synth, 0, annotations
                .getLength());
    }

    /**
     * Test #2.
     */
    public void testST2Annotation() {
        st2AnnotationTest(Boolean.FALSE);
        st2AnnotationTest(Boolean.TRUE);
    }

    private void st2AnnotationTest(Boolean synth) {
        String expected = trim("<annotation id=\"ANNOT3\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\" xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");
        String expected2 = trim("<annotation id=\"ANNOT4\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\" xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) model
                .getTypeDefinition("ST2", "XSFacetTest");
        // Item 0 is whitespace
        XSFacet minLength = (XSFacet) st.getFacets().item(1);

        XSAnnotation annotation = minLength.getAnnotation();
        assertEquals("TEST2_NO_ANNOTATION_" + synth, expected, trim(annotation
                .getAnnotationString()));
        XSObjectList annotations = minLength.getAnnotations();
        assertEquals(
                "TEST2_NO_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        XSFacet maxLength = (XSFacet) st.getFacets().item(2);
        annotation = maxLength.getAnnotation();
        assertEquals("TEST2_NO_ANNOTATION_" + synth, expected2, trim(annotation
                .getAnnotationString()));
        annotations = maxLength.getAnnotations();
        assertEquals(
                "TEST2_NO_ANNOTATIONS_" + synth,
                expected2,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #3.
     */
    public void testST3Annotation() {
        st3AnnotationTest(Boolean.FALSE);
        st3AnnotationTest(Boolean.TRUE);
    }

    private void st3AnnotationTest(Boolean synth) {
        String expected = trim("<annotation id=\"ANNOT5\" sn:attr=\"SYNTH2\""
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\" xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) model
                .getTypeDefinition("ST3", "XSFacetTest");
        XSFacet whitespace = (XSFacet) st.getFacets().item(0);

        XSAnnotation annotation = whitespace.getAnnotation();
        assertEquals("TEST3_ANNOTATION_" + synth, expected, trim(annotation
                .getAnnotationString()));
        XSObjectList annotations = whitespace.getAnnotations();
        assertEquals(
                "TES3_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #4.
     */
    public void testST4Annotation() {
        st4AnnotationTest(Boolean.FALSE);
        st4AnnotationTest(Boolean.TRUE);
    }

    private void st4AnnotationTest(Boolean synth) {
        String expected = trim("<annotation id=\"ANNOT6\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\" xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) model
                .getTypeDefinition("ST4", "XSFacetTest");
        // Item 0 is minInclusive
        XSFacet minInclusive = (XSFacet) st.getFacets().item(1);

        XSAnnotation annotation = minInclusive.getAnnotation();
        assertEquals("TEST4_ANNOTATION_" + synth, expected, trim(annotation
                .getAnnotationString()));
        XSObjectList annotations = minInclusive.getAnnotations();
        assertEquals(
                "TES4_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #5.
     */
    public void testST5Annotation() {
        st5AnnotationTest(Boolean.FALSE);
        st5AnnotationTest(Boolean.TRUE);
    }

    private void st5AnnotationTest(Boolean synth) {
        String expected = trim("<annotation id=\"ANNOT7\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\" xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) model
                .getTypeDefinition("ST5", "XSFacetTest");
        // Item 0 is minInclusive
        XSFacet maxInclusive = (XSFacet) st.getFacets().item(1);

        XSAnnotation annotation = maxInclusive.getAnnotation();
        assertEquals("TEST5_ANNOTATION_" + synth, expected, trim(annotation
                .getAnnotationString()));
        XSObjectList annotations = maxInclusive.getAnnotations();
        assertEquals(
                "TES5_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #6.
     */
    public void testST6Annotation() {
        st6AnnotationTest(Boolean.FALSE);
        st6AnnotationTest(Boolean.TRUE);
    }

    private void st6AnnotationTest(Boolean synth) {
        String expected = trim("<annotation id=\"ANNOT8\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\" xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) model
                .getTypeDefinition("ST6", "XSFacetTest");
        // Item 0 is minInclusive
        XSFacet fractionDigits = (XSFacet) st.getFacets().item(1);

        XSAnnotation annotation = fractionDigits.getAnnotation();
        assertEquals("TEST6_ANNOTATION_" + synth, expected, trim(annotation
                .getAnnotationString()));
        XSObjectList annotations = fractionDigits.getAnnotations();
        assertEquals(
                "TES6_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #7.
     */
    public void testST7Annotation() {
        st7AnnotationTest(Boolean.FALSE);
        st7AnnotationTest(Boolean.TRUE);
    }

    private void st7AnnotationTest(Boolean synth) {
        String expected = trim("<annotation sn:attr=\"SYNTH2\" id=\"ANNOT9\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\" xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSElementDeclaration elem = model.getElementDeclaration("elem1",
                "XSFacetTest");

        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) elem
                .getTypeDefinition();
        // Item 0 is minInclusive
        XSFacet fractionDigits = (XSFacet) st.getFacets().item(1);

        XSAnnotation annotation = fractionDigits.getAnnotation();
        assertEquals("TEST7_ANNOTATION_" + synth, expected, trim(annotation
                .getAnnotationString()));
        XSObjectList annotations = fractionDigits.getAnnotations();
        assertEquals(
                "TES7_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #8.
     */
    public void testST8Annotation() {
        st8AnnotationTest(Boolean.FALSE);
        st8AnnotationTest(Boolean.TRUE);
    }

    private void st8AnnotationTest(Boolean synth) {
        String expected = trim("<annotation id=\"ANNOT10\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\" xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSElementDeclaration elem = model.getElementDeclaration("elem2",
                "XSFacetTest");

        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) elem
                .getTypeDefinition();
        // Item 0 is minInclusive
        XSFacet fractionDigits = (XSFacet) st.getFacets().item(1);

        XSAnnotation annotation = fractionDigits.getAnnotation();
        assertEquals("TEST8_ANNOTATION_" + synth, expected, trim(annotation
                .getAnnotationString()));
        XSObjectList annotations = fractionDigits.getAnnotations();
        assertEquals(
                "TES8_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #9.
     */
    public void testS9Annotation() {
        st9AnnotationTest(Boolean.FALSE);
        st9AnnotationTest(Boolean.TRUE);
    }

    private void st9AnnotationTest(Boolean synth) {
        String expected = trim("<annotation sn:attr=\"SYNTH2\" id=\"ANNOT11\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\" xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSElementDeclaration elem = model.getElementDeclaration("elem3",
                "XSFacetTest");

        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) elem
                .getTypeDefinition();
        // Item 0 is minInclusive
        XSFacet fractionDigits = (XSFacet) st.getFacets().item(1);

        XSAnnotation annotation = fractionDigits.getAnnotation();
        assertEquals("TEST9_ANNOTATION_" + synth, expected, trim(annotation
                .getAnnotationString()));
        XSObjectList annotations = fractionDigits.getAnnotations();
        assertEquals(
                "TES79_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #10.
     */
    public void testST10Annotation() {
        st10AnnotationTest(Boolean.FALSE);
        st10AnnotationTest(Boolean.TRUE);
    }

    private void st10AnnotationTest(Boolean synth) {
        String expected = trim("<annotation sn:attr=\"SYNTH2\" id=\"ANNOT12\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\" xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSElementDeclaration elem = model.getElementDeclaration("elem4",
                "XSFacetTest");

        XSComplexTypeDefinition ct = (XSComplexTypeDefinition) elem
                .getTypeDefinition();
        XSAttributeUse attr = (XSAttributeUse) ct.getAttributeUses().item(0);
        XSSimpleTypeDefinition st = attr.getAttrDeclaration()
                .getTypeDefinition();
        // Item 0 is minInclusive
        XSFacet fractionDigits = (XSFacet) st.getFacets().item(1);

        XSAnnotation annotation = fractionDigits.getAnnotation();
        assertEquals("TEST10_ANNOTATION_" + synth, expected, trim(annotation
                .getAnnotationString()));
        XSObjectList annotations = fractionDigits.getAnnotations();
        assertEquals(
                "TES10_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #11.
     */
    public void testST11Annotation() {
        st11AnnotationTest(Boolean.FALSE);
        st11AnnotationTest(Boolean.TRUE);
    }

    private void st11AnnotationTest(Boolean synth) {
        String expected = trim("<annotation id=\"ANNOT14\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\"  xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) model
                .getTypeDefinition("ST7", "XSFacetTest");
        // Item 0 is minInclusive
        XSFacet maxInclusive = (XSFacet) st.getFacets().item(1);

        XSAnnotation annotation = maxInclusive.getAnnotation();
        assertEquals("TEST11_ANNOTATION_" + synth, expected, trim(annotation
                .getAnnotationString()));
        XSObjectList annotations = maxInclusive.getAnnotations();
        assertEquals(
                "TES11_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #12.
     */
    public void testST12Annotation() {
        st12AnnotationTest(Boolean.FALSE);
        st12AnnotationTest(Boolean.TRUE);
    }

    private void st12AnnotationTest(Boolean synth) {
        String expected = trim("<annotation id=\"ANNOT15\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\"  xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) model
                .getTypeDefinition("ST8", "XSFacetTest");
        // Item 0 is minInclusive
        XSFacet length = (XSFacet) st.getFacets().item(1);

        XSAnnotation annotation = length.getAnnotation();
        assertEquals("TEST12_ANNOTATION_" + synth, expected, trim(annotation
                .getAnnotationString()));
        XSObjectList annotations = length.getAnnotations();
        assertEquals(
                "TES12_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #13.
     */
    public void testST13Annotation() {
        st13AnnotationTest(Boolean.FALSE);
        st13AnnotationTest(Boolean.TRUE);
    }

    private void st13AnnotationTest(Boolean synth) {
        String expected = trim("<annotation id=\"ANNOT16\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\"  xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSSimpleTypeDefinition stu = (XSSimpleTypeDefinition) model
                .getTypeDefinition("ST9", "XSFacetTest");
        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) stu
                .getMemberTypes().item(0);
        // Item 0 is minInclusive
        XSFacet length = (XSFacet) st.getFacets().item(1);

        XSAnnotation annotation = length.getAnnotation();
        assertEquals("TEST12_ANNOTATION_" + synth, expected, trim(annotation
                .getAnnotationString()));
        XSObjectList annotations = length.getAnnotations();
        assertEquals(
                "TES13_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #14. REVISIT: Bug if there's a synthetic annotation on a facet we
     * don't generate it.
     */
    public void testST14Annotation() {
        st14AnnotationTest(Boolean.FALSE);
        st14AnnotationTest(Boolean.TRUE);
    }

    private void st14AnnotationTest(Boolean synth) {
        String expected = trim("<annotation id=\"ANNOT17\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\"  xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");

        String expected1 = trim("<annotation sn:attr=\"SYNTH1\" " +
                "xmlns=\"http://www.w3.org/2001/XMLSchema\" " +
                "xmlns:sv=\"XSFacetTest\" xmlns:sn=\"SyntheticAnnotation\"> " +
                "<documentation>SYNTHETIC_ANNOTATION</documentation></annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) model
                .getTypeDefinition("ST10", "XSFacetTest");
        // Item 0 is minInclusive
        XSFacet minLength = (XSFacet) st.getFacets().item(1);

        XSAnnotation annotation = minLength.getAnnotation();
        assertEquals("TEST14_ANNOTATION_" + synth, expected, trim(annotation
                .getAnnotationString()));
        XSObjectList annotations = minLength.getAnnotations();
        assertEquals(
                "TES14_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        if (synth.booleanValue() == true) {
            // Item 0 is minInclusive
            XSFacet maxLength = (XSFacet) st.getFacets().item(2);

            annotation = maxLength.getAnnotation();
            assertEquals("TEST14_ANNOTATION_" + synth, expected1,
                    trim(annotation.getAnnotationString()));
            annotations = maxLength.getAnnotations();
            assertEquals("TES14_ANNOTATIONS_" + synth, expected1,
                    trim(((XSAnnotation) annotations.item(0))
                            .getAnnotationString()));
        }
    }

    /**
     * Test #15. REVISIT: Looks like a bug here...Notations do not generate
     * synthetic annotations REVISIT: Bug if there's a synthetic annotation on a
     * facet we don't generate it.
     */
    public void testST15Annotation() {
        st15AnnotationTest(Boolean.FALSE);
        st15AnnotationTest(Boolean.TRUE);
    }

    private void st15AnnotationTest(Boolean synth) {
        String expected = trim("<annotation sn:attr=\"SYNTH1\" " +
                "xmlns=\"http://www.w3.org/2001/XMLSchema\" " +
                "xmlns:sv=\"XSFacetTest\" xmlns:sn=\"SyntheticAnnotation\"> " +
                "<documentation>SYNTHETIC_ANNOTATION</documentation></annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) model
                .getTypeDefinition("ST11", "XSFacetTest");
        // Item 0 is minInclusive
        XSFacet maxLength = (XSFacet) st.getFacets().item(1);

        if (synth.booleanValue() == true) {
            XSAnnotation annotation = maxLength.getAnnotation();
            assertEquals("TEST15_ANNOTATION_" + synth, expected,
                    trim(annotation.getAnnotationString()));
            XSObjectList annotations = maxLength.getAnnotations();
            assertEquals("TES15_ANNOTATIONS_" + synth, expected,
                    trim(((XSAnnotation) annotations.item(0))
                            .getAnnotationString()));
        } else {
            XSAnnotation annotation = maxLength.getAnnotation();
            assertNull("TEST15_ANNOTATION_" + synth, annotation);
            XSObjectList annotations = maxLength.getAnnotations();
            assertEquals("TES15_ANNOTATIONS_" + synth, 0, annotations
                    .getLength());
        }
    }

    /**
     * Test #16.
     * 
     */
    public void testST16Annotation() {
        st16AnnotationTest(Boolean.FALSE);
        st16AnnotationTest(Boolean.TRUE);
    }

    private void st16AnnotationTest(Boolean synth) {
        String expected = trim("<annotation id=\"ANNOT18\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSFacetTest\"  xmlns:sn=\"SyntheticAnnotation\" >"
                + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSFacetTest01.xsd"));

        XSAttributeDeclaration attr = model.getAttributeDeclaration("attr",
                "XSFacetTest");

        XSSimpleTypeDefinition st = (XSSimpleTypeDefinition) attr
                .getTypeDefinition();
        // Item 0 is minInclusive
        XSFacet maxLength = (XSFacet) st.getFacets().item(1);

        XSAnnotation annotation = maxLength.getAnnotation();
        assertEquals("TEST16_ANNOTATION_" + synth, expected, trim(annotation
                .getAnnotationString()));
        XSObjectList annotations = maxLength.getAnnotations();
        assertEquals(
                "TES16_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * 
     * @param args
     */
    public static void main(String args[]) {
        junit.textui.TestRunner.run(XSFacetAnnotationsTest.class);
    }

}
