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

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.xerces.parsers.DOMParser;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Andy Clark
 * @version $Id$
 */
public class DesignDoc {

    //
    // MAIN
    //

    public static void main(String argv[]) {
        if (argv.length != 2) {
            System.err.println("usage: DesignDoc xml_file zip_file");
            System.exit(1);
        }
        Document document = readDesign(argv[0]);
        if (document == null) {
            System.err.println("error: Unable to read design.");
            System.exit(1);
        }
        Element root = document.getDocumentElement();
        if (root == null || !root.getNodeName().equals("design")) {
            System.err.println("error: Design not found.");
            System.exit(1);
        }
        DesignDoc design = new DesignDoc();
        try {
            design.generateDesign(argv[1], root);
        }
        catch (Exception e) {
            System.err.println("error: Error building stubs.");
            e.printStackTrace(System.err);
            System.exit(1);
        }
        System.exit(0);
    }

    //
    // Constants
    //

    public static final String GENERATOR_NAME = "DesignDoc";
    
    private static final String GENERATION_TIMESTAMP = new java.util.Date().toString();

    //
    // Static data
    //

    private static DOMParser parser;

    //
    // Data
    //

    private IndentingWriter out;
    private ZipOutputStream zip;

    //
    // Public static methods
    //

    // reading

    public static Document readDesign(String systemId) {
        if (parser == null) {
            parser = new DOMParser();
            try {
                parser.setFeature("http://xml.org/sax/features/validation", true);
                parser.setFeature("http://apache.org/xml/features/dom/defer-node-expansion", false);
            }
            catch (Exception e) {
                throw new RuntimeException("unable to set parser features");
            }
        }
        try {
            parser.parse(systemId);
        }
        catch (Exception e) {
            return null;
        }
        return parser.getDocument();
    }

    //
    // Public methods
    //

    // generation

    public void generateDesign(String filename, Element design) throws IOException {
        /***
        int index = filename.lastIndexOf('.');
        String basename = index != -1 ? filename.substring(0, index) : filename;
        zip = new ZipOutputStream(new FileOutputStream(basename+".zip"));
        /***/
        zip = new ZipOutputStream(new FileOutputStream(filename));
        /***/
        out = new IndentingWriter(new PrintWriter(zip, true));
        Element child = getFirstChildElement(design);
        while (child != null) {
            if (child.getNodeName().equals("category")) {
                generateCategory(child);
            }
            child = getNextSiblingElement(child);
        }
        zip.finish();
        zip.close();
    }

    public void generateCategory(Element category) throws IOException {
        Element child = getFirstChildElement(category);
        while (child != null) {
            String name = child.getNodeName();
            if (name.equals("class")) {
                generateClass(child);
            }
            else if (name.equals("interface")) {
                generateInterface(child);
            }
            child = getNextSiblingElement(child);
        }
    }

    public void generateClass(Element cls) throws IOException {
        zip.putNextEntry(new ZipEntry(makeFilename(cls)));
        printCopyright();
        /***
        printClassProlog(cls);
        /***/
        printObjectProlog(cls);
        /***/
        printClassHeader(cls);
        out.indent();
        printConstants(cls);
        printFields(cls);
        printConstructors(cls);
        printMethods(cls, true);
        printImplementedMethods(cls);
        out.outdent();
        printClassFooter(cls);
        zip.closeEntry();
    }

    public void generateInterface(Element inter) throws IOException {
        zip.putNextEntry(new ZipEntry(makeFilename(inter)));
        printCopyright();
        /***
        printInterfaceProlog(inter);
        /***/
        printObjectProlog(inter);
        /***/
        printInterfaceHeader(inter);
        out.indent();
        printConstants(inter);
        printMethods(inter, false);
        out.outdent();
        printInterfaceFooter(inter);
        zip.closeEntry();
    }

    // print: general

    public void printCopyright() {
        out.println("/*");
        out.println(" * The Apache Software License, Version 1.1");
        out.println(" *");
        out.println(" *");
        out.println(" * Copyright (c) 1999,2000 The Apache Software Foundation.  All rights ");
        out.println(" * reserved.");
        out.println(" *");
        out.println(" * Redistribution and use in source and binary forms, with or without");
        out.println(" * modification, are permitted provided that the following conditions");
        out.println(" * are met:");
        out.println(" *");
        out.println(" * 1. Redistributions of source code must retain the above copyright");
        out.println(" *    notice, this list of conditions and the following disclaimer. ");
        out.println(" *");
        out.println(" * 2. Redistributions in binary form must reproduce the above copyright");
        out.println(" *    notice, this list of conditions and the following disclaimer in");
        out.println(" *    the documentation and/or other materials provided with the");
        out.println(" *    distribution.");
        out.println(" *");
        out.println(" * 3. The end-user documentation included with the redistribution,");
        out.println(" *    if any, must include the following acknowledgment:  ");
        out.println(" *       \"This product includes software developed by the");
        out.println(" *        Apache Software Foundation (http://www.apache.org/).\"");
        out.println(" *    Alternately, this acknowledgment may appear in the software itself,");
        out.println(" *    if and wherever such third-party acknowledgments normally appear.");
        out.println(" *");
        out.println(" * 4. The names \"Xerces\" and \"Apache Software Foundation\" must");
        out.println(" *    not be used to endorse or promote products derived from this");
        out.println(" *    software without prior written permission. For written ");
        out.println(" *    permission, please contact apache@apache.org.");
        out.println(" *");
        out.println(" * 5. Products derived from this software may not be called \"Apache\",");
        out.println(" *    nor may \"Apache\" appear in their name, without prior written");
        out.println(" *    permission of the Apache Software Foundation.");
        out.println(" *");
        out.println(" * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED");
        out.println(" * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES");
        out.println(" * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE");
        out.println(" * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR");
        out.println(" * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,");
        out.println(" * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT");
        out.println(" * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF");
        out.println(" * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND");
        out.println(" * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,");
        out.println(" * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT");
        out.println(" * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF");
        out.println(" * SUCH DAMAGE.");
        out.println(" * ====================================================================");
        out.println(" *");
        out.println(" * This software consists of voluntary contributions made by many");
        out.println(" * individuals on behalf of the Apache Software Foundation and was");
        out.println(" * originally based on software copyright (c) 1999, International");
        out.println(" * Business Machines, Inc., http://www.apache.org.  For more");
        out.println(" * information on the Apache Software Foundation, please see");
        out.println(" * <http://www.apache.org/>.");
        out.println(" */");
        out.println();
    }

    public void printObjectProlog(Element object) {
        Element category = getParentNodeElement(object, "category");
        String objectPackageName = "";
        if (category != null) {
            objectPackageName = category.getAttribute("package");
            if (objectPackageName.length() > 0) {
                out.print("package ");
                out.print(objectPackageName);
                out.print(';');
                out.println();
                out.println();
            }
        }
        Vector references = new Vector();
        collectImports(object, objectPackageName, references);
        if (object.getNodeName().equals("class")) {
            Element implementsElement = getFirstChildElement(object, "implements");
            while (implementsElement != null) {
                Element referenceElement = getLastChildElement(implementsElement, "reference");
                String referenceIdref = referenceElement.getAttribute("idref");
                Element interfaceElement = object.getOwnerDocument().getElementById(referenceIdref);
                collectImports(interfaceElement, objectPackageName, references);
                implementsElement = getNextSiblingElement(implementsElement, "implements");
            }
            int referenceCount = references.size();
            if (referenceCount > 0) {
                for (int i = 0; i < referenceCount; i++) {
                    out.print("import ");
                    out.print(String.valueOf(references.elementAt(i)));
                    out.print(';');
                    out.println();
                }
                out.println();
            }
        }
    }

    public void printObjectComment(Element object) {
        out.println("/**");
        Element note = getFirstChildElement(object, "note");
        if (note != null) {
            while (note != null) {
                out.print(" * ");
                out.println(getElementText(note));
                note = getNextSiblingElement(note, "note");
                if (note != null) {
                    out.println(" * <p>");
                }
            }
            out.println();
        }
        out.print(" * @author Stubs generated by ");
        out.print(GENERATOR_NAME);
        out.print(" on ");
        out.println(GENERATION_TIMESTAMP);
        out.println(" * @version $Id$");
        out.println(" */");
    }

    // print: constants

    public void printConstants(Element object) {
        Element constant = getFirstChildElement(object, "constant");
        if (constant != null) {
            out.println("//");
            out.println("// Constants");
            out.println("//");
            out.println();
            while (constant != null) {
                printConstant(constant);
                constant = getNextSiblingElement(constant, "constant");
            }
        }
    }

    public void printConstant(Element constant) {
        printConstantComment(constant);
        out.print(constant.getAttribute("visibility"));
        out.print(" static final ");
        String defaultValue = printType(out, getLastChildElement(constant));
        out.print(' ');
        out.print(constant.getAttribute("name"));
        out.print(" = ");
        out.print(defaultValue);
        out.println(';');
        out.println();
    }

    public void printConstantComment(Element constant) {
        out.print("/** ");
        out.print(constant.getAttribute("name"));
        out.print(" */");
        out.println();
    }

    // print: fields

    public void printFields(Element object) {
        Element field = getFirstChildElement(object, "field");
        if (field != null) {
            out.println("//");
            out.println("// Data");
            out.println("//");
            out.println();
            while (field != null) {
                printField(field);
                field = getNextSiblingElement(field, "field");
            }
        }
    }

    public void printField(Element field) {
        printFieldComment(field);
        out.print(field.getAttribute("visibility"));
        out.print(' ');
        printType(out, getLastChildElement(field));
        out.print(' ');
        out.print(field.getAttribute("name"));
        out.println(';');
        out.println();
    }

    public void printFieldComment(Element field) {
        out.print("/** ");
        out.print(field.getAttribute("name"));
        out.print(" */");
        out.println();
    }

    // print: constructors

    public void printConstructors(Element cls) {
        Element constructor = getFirstChildElement(cls, "constructor");
        if (constructor != null) {
            out.println("//");
            out.println("// Constructors");
            out.println("//");
            out.println();
            while (constructor != null) {
                printConstructor(constructor);
                constructor = getNextSiblingElement(constructor, "constructor");
            }
        }
    }

    public void printConstructor(Element constructor) {
        printConstructorComment(constructor);
        out.print(constructor.getAttribute("visibility"));
        out.print(' ');
        String name = getParentNodeElement(constructor, "class").getAttribute("name");
        out.print(name);
        out.print('(');
        Element param = getFirstChildElement(constructor, "param");
        while (param != null) {
            printType(out, getLastChildElement(param));
            out.print(' ');
            out.print(param.getAttribute("name"));
            param = getNextSiblingElement(param, "param");
            if (param != null) {
                out.print(", ");
            }
        }
        out.print(')');
        Element throwsElement = getFirstChildElement(constructor, "throws");
        if (throwsElement != null) {
            Element packageElement = getParentNodeElement(constructor, "category");
            String packageName = packageElement != null ? packageElement.getAttribute("package") : "";
            out.println();
            out.indent();
            out.print("throws ");
            while (throwsElement != null) {
                String throwsIdref = getFirstChildElement(throwsElement, "reference").getAttribute("idref");
                Element throwsClass = constructor.getOwnerDocument().getElementById(throwsIdref);
                String throwsClassName = throwsClass.getAttribute("name");
                /***
                Element throwsCategory = getParentNodeElement(throwsClass);
                String throwsPackageName = throwsCategory != null ? throwsCategory.getAttribute("package") : "";
                if (throwsPackageName.length() == 0) {
                    throwsPackageName = null;
                }
                if (packageName != null && throwsPackageName != null) {
                    if (!packageName.equals(throwsPackageName)) {
                        out.print(throwsPackageName);
                        out.print('.');
                    }
                }
                /***/
                out.print(throwsClassName);
                throwsElement = getNextSiblingElement(throwsElement, "throws");
                if (throwsElement != null) {
                    out.print(", ");
                }
            }
            out.outdent();
        }
        out.println(" {");
        out.println('}');
        out.println();
    }

    public void printMethodComment(Element method) {
        out.println("/**");
        Element note = getFirstChildElement(method, "note");
        if (note == null) {
            out.print(" * ");
            out.print(method.getAttribute("name"));
            out.println();
        }
        else {
            while (note != null) {
                out.print(" * ");
                out.println(getElementText(note));
                note = getNextSiblingElement(note, "note");
                if (note != null) {
                    out.println(" * <p>");
                }
            }
        }
        Element param = getFirstChildElement(method, "param");
        Element returns = getFirstChildElement(method, "returns");
        if (param != null || returns != null) {
            out.println(" * ");
        }
        if (param != null) {
            while (param != null) {
                printParamComment(param);
                param = getNextSiblingElement(param, "param");
            }
        }
        if (returns != null) {
            if (getFirstChildElement(method, "param") != null) {
                out.println(" * ");
            }
            printReturnsComment(returns);
        }
        out.println(" */");
    }

    // print: methods

    public void printMethods(Element object, boolean body) {
        Element method = getFirstChildElement(object, "method");
        if (method != null) {
            out.println("//");
            out.println("// Methods");
            out.println("//");
            out.println();
            while (method != null) {
                printMethod(method, body);
                method = getNextSiblingElement(method, "method");
            }
        }
    }

    public void printImplementedMethods(Element cls) {
        Element implementsElement = getFirstChildElement(cls, "implements");
        while (implementsElement != null) {
            Element referenceElement = getLastChildElement(implementsElement, "reference");
            String referenceIdref = referenceElement.getAttribute("idref");
            Element interfaceElement = cls.getOwnerDocument().getElementById(referenceIdref);
            Element method = getFirstChildElement(interfaceElement, "method");
            if (method != null) {
                out.println("//");
                out.print("// ");
                out.print(interfaceElement.getAttribute("name"));
                out.println(" methods");
                out.println("//");
                out.println();
                while (method != null) {
                    printMethod(method, true);
                    method = getNextSiblingElement(method, "method");
                }
            }
            implementsElement = getNextSiblingElement(implementsElement, "implements");
        }
    }

    public void printMethod(Element method, boolean body) {
        printMethodComment(method);
        out.print(method.getAttribute("visibility"));
        out.print(' ');
        Element returns = getFirstChildElement(method, "returns");
        String defaultValue = null;
        if (returns != null) {
            defaultValue = printType(out, getLastChildElement(returns));
        }
        else {
            out.print("void");
        }
        out.print(' ');
        String name = method.getAttribute("name");
        out.print(name);
        out.print('(');
        Element param = getFirstChildElement(method, "param");
        while (param != null) {
            printType(out, getLastChildElement(param));
            out.print(' ');
            out.print(param.getAttribute("name"));
            param = getNextSiblingElement(param, "param");
            if (param != null) {
                out.print(", ");
            }
        }
        out.print(')');
        Element throwsElement = getFirstChildElement(method, "throws");
        if (throwsElement != null) {
            Element packageElement = getParentNodeElement(method, "category");
            String packageName = packageElement != null ? packageElement.getAttribute("package") : "";
            out.println();
            out.indent();
            out.print("throws ");
            while (throwsElement != null) {
                String throwsIdref = getFirstChildElement(throwsElement, "reference").getAttribute("idref");
                Element throwsClass = method.getOwnerDocument().getElementById(throwsIdref);
                String throwsClassName = throwsClass.getAttribute("name");
                /***
                Element throwsCategory = getParentNodeElement(throwsClass);
                String throwsPackageName = throwsCategory != null ? throwsCategory.getAttribute("package") : "";
                if (throwsPackageName.length() == 0) {
                    throwsPackageName = null;
                }
                if (packageName != null && throwsPackageName != null) {
                    if (!packageName.equals(throwsPackageName)) {
                        out.print(throwsPackageName);
                        out.print('.');
                    }
                }
                /***/
                out.print(throwsClassName);
                throwsElement = getNextSiblingElement(throwsElement, "throws");
                if (throwsElement != null) {
                    out.print(", ");
                }
            }
            out.outdent();
        }
        if (body) {
            out.println(" {");
            if (defaultValue != null) {
                out.indent();
                out.print("return ");
                out.print(defaultValue);
                out.println(';');
                out.outdent();
            }
            out.print("} // ");
            out.println(name);
        }
        else {
            out.println(';');
        }
        out.println();
    }

    public void printConstructorComment(Element constructor) {
        out.println("/**");
        Element note = getFirstChildElement(constructor, "note");
        if (note == null) {
            out.print(" * ");
            out.print(constructor.getAttribute("name"));
            out.println();
        }
        else {
            while (note != null) {
                out.print(" * ");
                out.println(getElementText(note));
                note = getNextSiblingElement(note, "note");
                if (note != null) {
                    out.println(" * <p>");
                }
            }
        }
        Element param = getFirstChildElement(constructor, "param");
        if (param != null) {
            out.println(" * ");
        }
        if (param != null) {
            while (param != null) {
                printParamComment(param);
                param = getNextSiblingElement(param, "param");
            }
        }
        out.println(" */");
    }

    public void printParamComment(Element param) {
        out.print(" * @param ");
        out.print(param.getAttribute("name"));
        out.print(' ');
        Element note = getFirstChildElement(param, "note");
        while (note != null) {
            out.print(getElementText(note));
            note = getNextSiblingElement(note, "note");
            if (note != null) {
                out.println();
                out.println("<p>");
                out.print(" * ");
            }
        }
        out.println();
    }

    public void printReturnsComment(Element returns) {
        out.print(" * @return ");
        Element note = getFirstChildElement(returns, "note");
        while (note != null) {
            out.print(getElementText(note));
            note = getNextSiblingElement(returns, "note");
            if (note != null) {
                out.println();
                out.println(" * <p>");
                out.print(" * ");
            }
        }
        out.println();
    }

    // print: class

    /***
    public void printClassProlog(Element cls) {
        Element category = getParentNodeElement(cls, "category");
        String classPackageName = "";
        if (category != null) {
            classPackageName = category.getAttribute("package");
            if (classPackageName.length() > 0) {
                out.print("package ");
                out.print(classPackageName);
                out.print(';');
                out.println();
                out.println();
            }
        }
        Vector references = new Vector();
        collectImports(cls, classPackageName, references);
        Element implementsElement = getFirstChildElement(cls, "implements");
        while (implementsElement != null) {
            Element referenceElement = getLastChildElement(implementsElement, "reference");
            String referenceIdref = referenceElement.getAttribute("idref");
            Element interfaceElement = cls.getOwnerDocument().getElementById(referenceIdref);
            collectImports(interfaceElement, classPackageName, references);
            implementsElement = getNextSiblingElement(implementsElement, "implements");
        }
        int referenceCount = references.size();
        if (referenceCount > 0) {
            for (int i = 0; i < referenceCount; i++) {
                out.print("import ");
                out.print(String.valueOf(references.elementAt(i)));
                out.print(';');
                out.println();
            }
            out.println();
        }
    }
    /***/

    public void printClassHeader(Element cls) {
        printObjectComment(cls);
        out.print(cls.getAttribute("visibility"));
        out.print(" class ");
        out.print(cls.getAttribute("name"));
        Element extendsElement = getFirstChildElement(cls, "extends");
        Element implementsElement = getFirstChildElement(cls, "implements");
        if (extendsElement != null || implementsElement != null) {
            /***
            Element category = getParentNodeElement(cls, "category");
            String packageName = category != null ? category.getAttribute("package") : "";
            if (packageName.length() == 0) {
                packageName = null;
            }
            /***/
            if (extendsElement != null) {
                out.println();
                out.indent();
                out.print("extends ");
                String extendsIdref = getFirstChildElement(extendsElement, "reference").getAttribute("idref");
                Element extendsClass = cls.getOwnerDocument().getElementById(extendsIdref);
                String extendsClassName = extendsClass.getAttribute("name");
                /***
                Element extendsCategory = getParentNodeElement(extendsClass);
                String extendsPackageName = extendsCategory != null ? extendsCategory.getAttribute("package") : "";
                if (extendsPackageName.length() == 0) {
                    extendsPackageName = null;
                }
                if (packageName != null && extendsPackageName != null) {
                    if (!packageName.equals(extendsPackageName)) {
                        out.print(extendsPackageName);
                        out.print('.');
                    }
                }
                /***/
                out.print(extendsClassName);
                out.outdent();
            }
            if (implementsElement != null) {
                out.println();
                out.indent();
                out.print("implements ");
                while (implementsElement != null) {
                    String implementsIdref = getFirstChildElement(implementsElement, "reference").getAttribute("idref");
                    Element implementsInterface = cls.getOwnerDocument().getElementById(implementsIdref);
                    String implementsInterfaceName = implementsInterface.getAttribute("name");
                    /***
                    Element implementsPackage = getParentNodeElement(implementsInterface, "category");
                    String implementsPackageName = implementsPackage != null ? implementsPackage.getAttribute("package") : "";
                    if (implementsPackageName.length() == 0) {
                        implementsPackageName = null;
                    }
                    if (packageName != null && implementsPackageName != null) {
                        if (!packageName.equals(implementsPackageName)) {
                            out.print(implementsPackageName);
                            out.print('.');
                        }
                    }
                    /***/
                    out.print(implementsInterfaceName);
                    implementsElement = getNextSiblingElement(implementsElement, "implements");
                    if (implementsElement != null) {
                        out.print(", ");
                    }
                }
                out.outdent();
            }
        }
        out.println(" {");
        out.println();
    }

    public void printClassFooter(Element cls) {
        out.print("} // class ");
        out.println(cls.getAttribute("name"));
    }

    // print: interface

    /***
    public void printInterfaceProlog(Element inter) {
        Element category = getParentNodeElement(inter, "category");
        if (category != null) {
            String packageName = category.getAttribute("package");
            if (packageName.length() > 0) {
                out.print("package ");
                out.print(packageName);
                out.print(';');
                out.println();
                out.println();
            }
        }
        // REVISIT: How about adding the imports here?
    }
    /***/

    public void printInterfaceHeader(Element inter) {
        printObjectComment(inter);
        out.print(inter.getAttribute("visibility"));
        out.print(" interface ");
        out.print(inter.getAttribute("name"));
        Element extendsElement = getFirstChildElement(inter, "extends");
        if (extendsElement != null) {
            out.println();
            out.indent();
            out.print("extends ");
            String extendsIdref = getFirstChildElement(extendsElement, "reference").getAttribute("idref");
            Element extendsClass = inter.getOwnerDocument().getElementById(extendsIdref);
            String extendsClassName = extendsClass.getAttribute("name");
            /***
            Element extendsCategory = getParentNodeElement(extendsClass);
            String extendsPackageName = extendsCategory != null ? extendsCategory.getAttribute("package") : "";
            if (extendsPackageName.length() == 0) {
                extendsPackageName = null;
            }
            Element category = getParentNodeElement(inter, "category");
            String packageName = category != null ? category.getAttribute("package") : "";
            if (packageName.length() == 0) {
                packageName = null;
            }
            if (packageName != null && extendsPackageName != null) {
                if (!packageName.equals(extendsPackageName)) {
                    out.print(extendsPackageName);
                    out.print('.');
                }
            }
            /***/
            out.print(extendsClassName);
            out.outdent();
        }
        out.println(" {");
        out.println();
    }

    public void printInterfaceFooter(Element inter) {
        out.print("} // interface ");
        out.println(inter.getAttribute("name"));
    }

    //
    // Private static methods
    //

    // other

    private void collectImports(Element object, String objectPackageName,
                                Vector references) {
        Element place = getFirstChildElement(object);
        while (place != null) {
            if (place.getNodeName().equals("reference")) {
                String idref = place.getAttribute("idref");
                Element idrefElement = place.getOwnerDocument().getElementById(idref);
                Element idrefCategoryElement = getParentNodeElement(idrefElement, "category");
                String packageName = idrefCategoryElement.getAttribute("package");
                if (packageName.length() > 0 && !packageName.equals(objectPackageName)) {
                    String reference = packageName + '.' + idrefElement.getAttribute("name");
                    if (!references.contains(reference)) {
                        int index = references.size();
                        while (index > 0) {
                            if (reference.compareTo((String)references.elementAt(index - 1)) >= 0) {
                                break;
                            }
                            index--;
                        }
                        references.insertElementAt(reference, index);
                    }
                }
            }
            Element next = getFirstChildElement(place);
            while (next == null) {
                next = getNextSiblingElement(place);
                if (next == null) {
                    place = getParentNodeElement(place);
                    if (place == object) {
                        break;
                    }
                }
            }
            place = next;
        }
    }

    // file name generation

    private static String makeFilename(Element object) {
        String name = object.getAttribute("name");
        Element packageElement = getParentNodeElement(object, "category");
        String packageName = packageElement != null ? packageElement.getAttribute("package") : "";
        int packageNameLen = packageName.length();
        StringBuffer path = new StringBuffer(packageNameLen+1+name.length()+5);
        if (packageNameLen > 0) {
            path.append(packageName.replace('.', '/'));
            path.append('/');
        }
        path.append(name);
        path.append(".java");
        return path.toString();
    }

    // printing

    private static String printType(IndentingWriter out, Element type) {
        String name = type.getNodeName();
        if (name.equals("array")) {
            printType(out, getLastChildElement(type));
            String dimensionString = type.getAttribute("dimension");
            int dimension = Integer.parseInt(dimensionString);
            for (int i = 0; i < dimension; i++) {
                out.print("[]");
            }
            return "null";
        }
        if (name.equals("primitive")) {
            String typeName = type.getAttribute("type");
            out.print(typeName);
            if (typeName.equals("long") || typeName.equals("int") || typeName.equals("short")) {
                return "-1";
            }
            if (typeName.equals("char")) {
                return "'\\uFFFE'"; // 0xFFFE == Not a character
            }
            if (typeName.equals("boolean")) {
                return "false";
            }
            return "???";
        }
        if (name.equals("reference")) {
            String idref = type.getAttribute("idref");
            type = type.getOwnerDocument().getElementById(idref);
            String typeName = type.getAttribute("name");
            /***
            String typePackageName = ((Element)type.getParentNode()).getAttribute("package");
            if (typePackageName.length() == 0) {
                typePackageName = null;
            }
            Element category = (Element)type.getParentNode();
            while (!category.getNodeName().equals("category")) {
                category = (Element)category.getParentNode();
            }
            String packageName = category.getAttribute("package");
            if (packageName.length() == 0) {
                packageName = null;
            }
            if (packageName != null && typePackageName != null) {
                if (!packageName.equals(typePackageName)) {
                    out.print(typePackageName);
                    out.print('.');
                }
            }
            /***/
            out.print(typeName);
            return "null";
        }
        if (name.equals("collection")) {
            Element child = getFirstChildElement(type);
            while (!child.getNodeName().equals("collector")) {
                child = getNextSiblingElement(type);
            }
            printType(out, getLastChildElement(child));
            return "null";
        }
        out.print("???");
        return "???";
    }

    // dom utils

    private static Element getParentNodeElement(Node node) {
        Node parent = node.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE) {
                return (Element)parent;
            }
            parent = parent.getParentNode();
        }
        return null;
    }

    private static Element getParentNodeElement(Node node, String name) {
        Node parent = node.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.ELEMENT_NODE && parent.getNodeName().equals(name)) {
                return (Element)parent;
            }
            parent = parent.getParentNode();
        }
        return null;
    }

    private static String getElementText(Element element) {
        Node child = element.getFirstChild();
        if (child != null) {
            StringBuffer str = new StringBuffer();
            while (child != null) {
                if (child.getNodeType() == Node.TEXT_NODE) {
                    str.append(child.getNodeValue());
                }
                child = child.getNextSibling();
            }
            return str.toString();
        }
        return "";
    }

    private static Element getFirstChildElement(Node parent) {
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return (Element)child;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static Element getFirstChildElement(Node parent, String name) {
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(name)) {
                return (Element)child;
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static Element getLastChildElement(Node parent) {
        Node child = parent.getLastChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                return (Element)child;
            }
            child = child.getPreviousSibling();
        }
        return null;
    }

    private static Element getLastChildElement(Node parent, String name) {
        Node child = parent.getLastChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(name)) {
                return (Element)child;
            }
            child = child.getPreviousSibling();
        }
        return null;
    }

    private static Element getNextSiblingElement(Node node) {
        Node sibling = node.getNextSibling();
        while (sibling != null) {
            if (sibling.getNodeType() == Node.ELEMENT_NODE) {
                return (Element)sibling;
            }
            sibling = sibling.getNextSibling();
        }
        return null;
    }

    private static Element getNextSiblingElement(Node node, String name) {
        Node sibling = node.getNextSibling();
        while (sibling != null) {
            if (sibling.getNodeType() == Node.ELEMENT_NODE && sibling.getNodeName().equals(name)) {
                return (Element)sibling;
            }
            sibling = sibling.getNextSibling();
        }
        return null;
    }

    private static Element getPreviousSiblingElement(Node node) {
        Node sibling = node.getPreviousSibling();
        while (sibling != null) {
            if (sibling.getNodeType() == Node.ELEMENT_NODE) {
                return (Element)sibling;
            }
            sibling = sibling.getPreviousSibling();
        }
        return null;
    }

    private static Element getPreviousSiblingElement(Node node, String name) {
        Node sibling = node.getPreviousSibling();
        while (sibling != null) {
            if (sibling.getNodeType() == Node.ELEMENT_NODE && sibling.getNodeName().equals(name)) {
                return (Element)sibling;
            }
            sibling = sibling.getPreviousSibling();
        }
        return null;
    }

    //
    // Classes
    //

    public static class IndentingWriter 
        extends FilterWriter {

        //
        // Data
        //

        private PrintWriter out;
        private int space = 4;
        private String spaceStr = "    ";
        private int level;
        private boolean indent = true;

        //
        // Constructors
        //

        public IndentingWriter(PrintWriter out) {
            super(out);
            this.out = out;
        }

        //
        // Public methods
        //

        public void indent() {
            level++;
        }

        public void outdent() {
            level--;
        }

        //
        // PrintWriter methods
        //

        public void print(char ch) {
            if (indent) { printIndent(); }
            out.print(ch);
        }

        public void print(String s) {
            if (indent) { printIndent(); }
            out.print(s);
        }

        public void println(char ch) {
            print(ch);
            println();
        }

        public void println(String s) {
            print(s);
            println();
        }

        public void println() {
            out.println();
            indent = true;
        }

        //
        // Private methods
        //

        private void printIndent() {
            for (int i = 0; i < level; i++) {
                out.print(spaceStr);
            }
            indent = false;
        }

    } // class IndentingWriter

} // class Design
