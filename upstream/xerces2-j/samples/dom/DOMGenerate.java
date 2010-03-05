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

package dom;

import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

/**
 * Simple Sample that:
 * - Generates a DOM from scratch.
 * - Writes the DOM to a String using an LSSerializer
 * @author Jeffrey Rodriguez
 * @version $Id$
 */
public class DOMGenerate {
    
    public static void main( String[] argv ) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();
            
            Element root = doc.createElementNS(null, "person"); // Create Root Element
            Element item = doc.createElementNS(null, "name");   // Create element
            item.appendChild( doc.createTextNode("Jeff") );
            root.appendChild( item );                           // Attach element to Root element
            item = doc.createElementNS(null, "age");            // Create another Element
            item.appendChild( doc.createTextNode("28" ) );       
            root.appendChild( item );                           // Attach Element to previous element down tree
            item = doc.createElementNS(null, "height");            
            item.appendChild( doc.createTextNode("1.80" ) );
            root.appendChild( item );                           // Attach another Element - grandaugther
            doc.appendChild( root );                            // Add Root to Document

            DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
            DOMImplementationLS domImplLS = (DOMImplementationLS)registry.getDOMImplementation("LS");
            
            LSSerializer ser = domImplLS.createLSSerializer();  // Create a serializer for the DOM
            LSOutput out = domImplLS.createLSOutput();
            StringWriter stringOut = new StringWriter();        // Writer will be a String
            out.setCharacterStream(stringOut);
            ser.write(doc, out);                                // Serialize the DOM

            System.out.println( "STRXML = " 
                    + stringOut.toString() );                   // Spit out the DOM as a String
        } catch ( Exception ex ) {
            ex.printStackTrace();
        }
    }
}

