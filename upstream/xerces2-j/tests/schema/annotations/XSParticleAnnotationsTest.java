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
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSImplementation;
import org.apache.xerces.xs.XSLoader;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSModelGroup;
import org.apache.xerces.xs.XSModelGroupDefinition;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSParticle;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;

/**
 * Tests methods getAnnotation and getAnnotations on XSParticle XSModel
 * components.
 * 
 * @author Neil Delima, IBM
 * @version $Id$
 */
public class XSParticleAnnotationsTest extends TestCase {

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
            System.err.println("SETUP FAILED: XSParticleTest");
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
    public void testNoAnnotations() {
        noAnnotationsTest(Boolean.FALSE);
        noAnnotationsTest(Boolean.TRUE);
    }

    private void noAnnotationsTest(Boolean synth) {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSParticleTest01.xsd"));

        XSComplexTypeDefinition ct = (XSComplexTypeDefinition) model
                .getTypeDefinition("CT1", "XSParticleTest");

        XSModelGroup sequence = (XSModelGroup) ct.getParticle().getTerm();
        XSParticle element = (XSParticle) sequence.getParticles().item(0);
        XSObjectList annotations = element.getAnnotations();
        assertEquals("TEST1_NO_ANNOTATIONS_" + synth, 0, annotations
                .getLength());

        XSParticle any = (XSParticle) sequence.getParticles().item(1);
        annotations = any.getAnnotations();
        assertEquals("TEST1_NO_ANNOTATIONS_" + synth, 0, annotations
                .getLength());

        XSParticle choice = (XSParticle) sequence.getParticles().item(1);
        annotations = choice.getAnnotations();
        assertEquals("TEST1_NO_ANNOTATIONS_" + synth, 0, annotations
                .getLength());
    }

    /**
     * Test #2.
     */
    public void testSynthAnnotations() {
        synthAnnotationsTest(Boolean.FALSE);
        synthAnnotationsTest(Boolean.TRUE);
    }

    private void synthAnnotationsTest(Boolean synth) {
        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSParticleTest01.xsd"));

        XSComplexTypeDefinition ct = (XSComplexTypeDefinition) model
                .getTypeDefinition("CT2", "XSParticleTest");

        XSModelGroup sequence = (XSModelGroup) ct.getParticle().getTerm();
        XSParticle element = (XSParticle) sequence.getParticles().item(0);
        XSObjectList annotations = element.getAnnotations();
        assertEquals("TEST2_NO_ANNOTATIONS_" + synth, (synth.booleanValue() == true) ? 1 : 0,
                annotations.getLength());

        XSParticle any = (XSParticle) sequence.getParticles().item(1);
        annotations = any.getAnnotations();
        assertEquals("TEST2_NO_ANNOTATIONS_" + synth, (synth.booleanValue() == true) ? 1 : 0,
                annotations.getLength());

        XSParticle choice = (XSParticle) sequence.getParticles().item(1);
        annotations = choice.getAnnotations();
        assertEquals("TEST2_NO_ANNOTATIONS_" + synth, (synth.booleanValue() == true) ? 1 : 0,
                annotations.getLength());
    }

    /**
     * Test #3.
     */
    public void testAllSynthAnnotations() {
        synthAllAnnotationsTest(Boolean.FALSE);
        synthAllAnnotationsTest(Boolean.TRUE);
    }

    private void synthAllAnnotationsTest(Boolean synth) {
        String expected = trim("<annotation sn:attr1=\"SYNTH1\" ns2:attr2=\"SYNTH2\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" xmlns:sn=\"SyntheticAnnotation\" "
                + "xmlns:ns2=\"TEST\" >"
                + "<documentation>SYNTHETIC_ANNOTATION</documentation>"
                + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSParticleTest01.xsd"));

        XSComplexTypeDefinition ct = (XSComplexTypeDefinition) model
                .getTypeDefinition("CT3", "XSParticleTest");

        XSParticle all = ct.getParticle();
        XSObjectList annotations = all.getAnnotations();
        assertEquals("TEST3_ANNOTATIONS_" + synth, (synth.booleanValue() == true) ? 1 : 0, annotations
                .getLength());
        if ((synth.booleanValue() == true))
            assertEquals("TEST3_ANNOTATIONS_" + synth, expected,
                    trim(((XSAnnotation) annotations.item(0))
                            .getAnnotationString()));

        XSParticle element = (XSParticle) ((XSModelGroup) all.getTerm())
                .getParticles().item(0);
        annotations = element.getAnnotations();
        assertEquals("TEST3_ANNOTATIONS_" + synth, (synth.booleanValue() == true) ? 1 : 0, annotations
                .getLength());
    }

    /**
     * Test #4.
     */
    public void test4Annotations() {
        annotationsTest4(Boolean.FALSE);
        annotationsTest4(Boolean.TRUE);
    }

    private void annotationsTest4(Boolean synth) {
        String expected = trim("<annotation sn:attr=\"SYNTH1\"  "
                + "id=\"ANNOT1\" xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" >" + "</annotation>");
        String expected2 = trim("<annotation sn:attr=\"SYNTH1\"  "
                + "id=\"ANNOT2\" xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" >" + "</annotation>");
        String expected3 = trim("<annotation "
                + "id=\"ANNOT3\" xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" >" + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSParticleTest01.xsd"));

        XSModelGroupDefinition mgd = model.getModelGroupDefinition("GRP4",
                "XSParticleTest");
        XSModelGroup all = mgd.getModelGroup();

        XSObjectList annotations = all.getAnnotations();
        assertEquals(
                "TEST4_ANNOTATIONS_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        XSParticle element = (XSParticle) all.getParticles().item(0);
        annotations = element.getAnnotations();
        assertEquals(
                "TEST4_ANNOTATIONS_2_" + synth,
                expected2,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        XSElementDeclaration elementDecl = (XSElementDeclaration) element
                .getTerm();
        XSComplexTypeDefinition ct = (XSComplexTypeDefinition) elementDecl
                .getTypeDefinition();
        XSParticle choice = ct.getParticle();
        annotations = choice.getAnnotations();
        assertEquals(
                "TEST4_ANNOTATIONS_3_" + synth,
                expected3,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #5.
     */
    public void test5Annotations() {
        annotationsTest5(Boolean.FALSE);
        annotationsTest5(Boolean.TRUE);
    }

    private void annotationsTest5(Boolean synth) {
        String expected = trim("<annotation id=\"ANNOT5\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" ></annotation>");
        String expected2 = trim("<annotation id=\"ANNOT4\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" ></annotation>");
        String expected3 = trim("<annotation sn:attr=\"SYNTH\"  "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" xmlns:sn=\"SyntheticAnnotation\" >"
                + "<documentation>SYNTHETIC_ANNOTATION</documentation></annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSParticleTest01.xsd"));

        XSElementDeclaration elem1 = model.getElementDeclaration("elem1",
                "XSParticleTest");
        XSComplexTypeDefinition ct = (XSComplexTypeDefinition) elem1
                .getTypeDefinition();
        XSComplexTypeDefinition ctb = (XSComplexTypeDefinition) ct
                .getBaseType();
        XSParticle sequence = ctb.getParticle();
        XSModelGroup sequencegrp = (XSModelGroup) sequence.getTerm();
        XSParticle elem2 = (XSParticle) sequencegrp.getParticles().item(0);
        XSElementDeclaration elemDecl2 = (XSElementDeclaration) elem2.getTerm();

        XSObjectList annotations = elem2.getAnnotations();
        assertEquals(
                "TEST5_ANNOTATIONS_1_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        annotations = elemDecl2.getAnnotations();
        assertEquals(
                "TEST5_ANNOTATIONS_2_" + synth,
                expected2,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        if ((synth.booleanValue() == true)) {
            XSParticle elem3 = (XSParticle) sequencegrp.getParticles().item(1);
            XSElementDeclaration elemDecl3 = (XSElementDeclaration) elem3
                    .getTerm();

            annotations = elem3.getAnnotations();
            assertEquals("TEST5_ANNOTATIONS_3_" + synth, 0, annotations
                    .getLength());

            annotations = elemDecl3.getAnnotations();
            assertEquals("TEST5_ANNOTATIONS_4_" + synth, expected3,
                    trim(((XSAnnotation) annotations.item(0))
                            .getAnnotationString()));
        }
    }

    /**
     * Test #6.
     */
    public void test6Annotations() {
        annotationsTest6(Boolean.FALSE);
        annotationsTest6(Boolean.TRUE);
    }

    private void annotationsTest6(Boolean synth) {
        String expected = trim("<annotation id=\"ANNOT7\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" ></annotation>");
        String expected2 = trim("<annotation id=\"ANNOT6\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" ></annotation>");
        String expected3 = trim("<annotation sn:attr=\"SYNTH\"  "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" xmlns:sn=\"SyntheticAnnotation\" >"
                + "<documentation>SYNTHETIC_ANNOTATION</documentation></annotation>");
        String expected4 = trim("<annotation id=\"ANNOT8\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" ></annotation>");
        String expected5 = trim("<annotation id=\"ANNOT8\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" ></annotation>");
        String expected6 = trim("<annotation sn:attr=\"SYNTH\"  "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" xmlns:sn=\"SyntheticAnnotation\" >"
                + "<documentation>SYNTHETIC_ANNOTATION</documentation></annotation>");
        String expected7 = trim("<annotation sn:attr=\"SYNTH\" id=\"ANNOT9\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" ></annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSParticleTest01.xsd"));

        XSElementDeclaration elem1 = model.getElementDeclaration("elem2",
                "XSParticleTest");
        XSComplexTypeDefinition ct = (XSComplexTypeDefinition) elem1
                .getTypeDefinition();
        XSParticle sequence = ct.getParticle();
        XSModelGroup sequencegrp = (XSModelGroup) sequence.getTerm();

        XSParticle elem2 = (XSParticle) sequencegrp.getParticles().item(0);
        XSElementDeclaration elemDecl2 = (XSElementDeclaration) elem2.getTerm();
        XSObjectList annotations = elem2.getAnnotations();
        assertEquals(
                "TEST6_ANNOTATIONS_1_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        annotations = elemDecl2.getAnnotations();
        assertEquals(
                "TEST6_ANNOTATIONS_2_" + synth,
                expected2,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        if (synth.booleanValue() == true) {
            XSParticle elem3 = (XSParticle) sequencegrp.getParticles().item(1);
            XSElementDeclaration elemDecl3 = (XSElementDeclaration) elem3
                    .getTerm();

            annotations = elem3.getAnnotations();
            assertEquals("TEST6_ANNOTATIONS_3_" + synth, 0, annotations
                    .getLength());

            annotations = elemDecl3.getAnnotations();
            assertEquals("TEST6_ANNOTATIONS_4_" + synth, expected3,
                    trim(((XSAnnotation) annotations.item(0))
                            .getAnnotationString()));
        }

        XSParticle elem3 = (XSParticle) sequencegrp.getParticles().item(2);
        XSElementDeclaration elemDecl3 = (XSElementDeclaration) elem3.getTerm();
        annotations = elem3.getAnnotations();
        assertEquals(
                "TEST6_ANNOTATIONS_1_" + synth,
                expected4,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        annotations = elemDecl3.getAnnotations();
        assertEquals(
                "TEST6_ANNOTATIONS_2_" + synth,
                expected5,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        if (synth.booleanValue() == true) {
            XSParticle elem4 = (XSParticle) sequencegrp.getParticles().item(3);
            XSElementDeclaration elemDecl4 = (XSElementDeclaration) elem4
                    .getTerm();

            annotations = elem4.getAnnotations();
            assertEquals("TEST6_ANNOTATIONS_3_" + synth, 1, annotations
                    .getLength());

            annotations = elemDecl4.getAnnotations();
            assertEquals("TEST6_ANNOTATIONS_4_" + synth, expected6,
                    trim(((XSAnnotation) annotations.item(0))
                            .getAnnotationString()));
        }

        XSParticle any = (XSParticle) sequencegrp.getParticles().item(4);
        annotations = any.getAnnotations();
        assertEquals(
                "TEST6_ANNOTATIONS_1_" + synth,
                expected7,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
    }

    /**
     * Test #7.
     */
    public void test7Annotations() {
        annotationsTest7(Boolean.FALSE);
        annotationsTest7(Boolean.TRUE);
    }

    private void annotationsTest7(Boolean synth) {
        String expected = trim("<annotation id=\"ANNOT10\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" >" + "</annotation>");
        String expected1 = trim("<annotation id=\"ANNOT11\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" >" + "</annotation>");
        String expected2 = trim("<annotation id=\"ANNOT12\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" >" + "</annotation>");
        String expected3 = trim("<annotation id=\"ANNOT14\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" >" + "</annotation>");
        String expected4 = trim("<annotation id=\"ANNOT15\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" >" + "</annotation>");
        String expected5 = trim("<annotation id=\"ANNOT13\" "
                + "xmlns=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:sv=\"XSParticleTest\" "
                + "xmlns:sn=\"SyntheticAnnotation\" >" + "</annotation>");

        fConfig
                .setParameter(
                        "http://apache.org/xml/features/generate-synthetic-annotations",
                        synth);

        XSModel model = fSchemaLoader
                .loadURI(getResourceURL("XSParticleTest01.xsd"));

        XSComplexTypeDefinition ct = (XSComplexTypeDefinition) model
                .getTypeDefinition("CT4", "XSParticleTest");
        XSParticle choice = ct.getParticle();

        XSObjectList annotations = choice.getAnnotations();
        assertEquals(
                "TEST7_ANNOTATIONS_1_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        XSModelGroup mg = (XSModelGroup) choice.getTerm();
        annotations = mg.getAnnotations();
        assertEquals(
                "TEST7_ANNOTATIONS_2_" + synth,
                expected,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        XSParticle seq = (XSParticle) mg.getParticles().item(0);
        annotations = seq.getAnnotations();
        assertEquals(
                "TEST7_ANNOTATIONS_3_" + synth,
                expected1,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
        mg = (XSModelGroup) seq.getTerm();
        annotations = mg.getAnnotations();
        assertEquals(
                "TEST7_ANNOTATIONS_4_" + synth,
                expected1,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        XSParticle elem1 = (XSParticle) mg.getParticles().item(0);
        annotations = elem1.getAnnotations();
        assertEquals(
                "TEST7_ANNOTATIONS_5_" + synth,
                expected2,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
        XSElementDeclaration elem = (XSElementDeclaration) elem1.getTerm();
        XSComplexTypeDefinition ct2 = (XSComplexTypeDefinition) elem
                .getTypeDefinition();
        XSParticle all = ct2.getParticle();
        annotations = all.getAnnotations();
        assertEquals(
                "TEST7_ANNOTATIONS_6_" + synth,
                expected3,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));
        mg = (XSModelGroup) all.getTerm();
        annotations = mg.getAnnotations();
        assertEquals(
                "TEST7_ANNOTATIONS_7_" + synth,
                expected3,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        XSParticle seq2 = (XSParticle) mg.getParticles().item(0);
        annotations = seq2.getAnnotations();
        assertEquals(
                "TEST7_ANNOTATIONS_8_" + synth,
                expected4,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

        mg = (XSModelGroup) seq.getTerm();
        XSParticle any = (XSParticle) mg.getParticles().item(1);
        annotations = any.getAnnotations();
        assertEquals(
                "TEST7_ANNOTATIONS_9_" + synth,
                expected5,
                trim(((XSAnnotation) annotations.item(0)).getAnnotationString()));

    }

    /**
     * 
     * @param args
     */
    public static void main(String args[]) {
        junit.textui.TestRunner.run(XSParticleAnnotationsTest.class);
    }

}
