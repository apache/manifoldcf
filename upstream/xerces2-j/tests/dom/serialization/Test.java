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

package dom.serialization;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import dom.ParserWrapper;

/**
 * A java serialization test. This sample program parses a
 * document, then serializes out to a file, then reloads
 * it from the file.  The intent is to have zero exceptions
 * in the process.
 *
 * @author <a href="mailto:sanders@apache.org">Scott Sanders</a>
 * @version $Id$
 */
public class Test {

    protected static final String NAMESPACES_FEATURE_ID = "http://xml.org/sax/features/namespaces";

    protected static final String DEFAULT_PARSER_NAME = "dom.wrappers.Xerces";

    public static void main(String args[]) {

        if (args.length != 2) {
            System.out.println("Usage: serialize.Test input.xml output.xml");
            System.exit(1);
        }

        ParserWrapper parser = null;

        try {
            parser = (ParserWrapper) Class.forName(DEFAULT_PARSER_NAME).newInstance();
        } catch (Exception e) {
            System.err.println("error: Unable to instantiate parser (" + DEFAULT_PARSER_NAME + ")");
        }

        try {
            parser.setFeature(NAMESPACES_FEATURE_ID, true);
        } catch (SAXException e) {
            System.err.println("warning: Parser does not support feature (" + NAMESPACES_FEATURE_ID + ")");
        }

        try {
            Document document = null;
            parser.setFeature("http://xml.org/sax/features/validation", true);
            parser.setFeature("http://apache.org/xml/features/validation/schema", true);
            document = parser.parse(args[0]);
            document.getDocumentElement().setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns:foo", "boo");
            serialize(document, args[1]);
            Document newDocument = deserialize(args[1]);
            Document emptyDoc = new org.apache.xerces.dom.DocumentImpl();
            emptyDoc.importNode(newDocument.getDocumentElement(), true);

            System.out.println("done.");
        } catch (Exception e) {
            System.err.println("error: Error occurred - " + e.getMessage());
            Exception se = e;
            if (e instanceof SAXException) {
                se = ((SAXException) e).getException();
            }
            if (se != null)
                se.printStackTrace(System.err);
            else
                e.printStackTrace(System.err);
        }

    }

    public static void serialize(Document document, String filename) throws Exception {
        System.out.println("Serializing parsed document");
        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(filename));
        out.writeObject(document);
        out.close();
    }

    public static Document deserialize(String filename) throws Exception {
        System.out.println("De-Serializing parsed document");
        ObjectInputStream in = new ObjectInputStream(new FileInputStream(filename));
        Document result = (Document) in.readObject();
        return result;
    }

}
