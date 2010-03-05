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

package dom.serialize;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.apache.xerces.dom.DOMImplementationImpl;
import org.apache.xerces.dom.DOMOutputImpl;
import org.apache.xerces.dom.DocumentImpl;
import org.w3c.dom.DOMConfiguration;
import org.w3c.dom.DOMError;
import org.w3c.dom.DOMErrorHandler;
import org.apache.xerces.parsers.DOMParser;
import org.apache.xml.serialize.DOMSerializerImpl;
import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.Serializer;
import org.apache.xml.serialize.SerializerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

/**
 * Tests that original behavior of XMLSerializer is not broken.
 * The namespace fixup is only performed with DOMWriter.
 * 
 * @author Elena Litani, IBM
 * @version $Id$
 */
public class TestXmlns implements DOMErrorHandler{

      public static void main(String[] args) {

            // Create a document.
            DocumentImpl document = new DocumentImpl();
            document.setXmlEncoding("utf-8");
            // Create an element with a default namespace declaration.
            Element outerNode = document.createElement("outer");
            outerNode.setAttribute("xmlns", "myuri:");
            document.appendChild(outerNode);

            // Create an inner element with no further namespace declaration.
            Element innerNode = document.createElement("inner");
            outerNode.appendChild(innerNode);

            // DOM is complete, now serialize it.
            Writer writer = new StringWriter();
            OutputFormat format = new OutputFormat();
            format.setEncoding("utf-8");
            Serializer serializer = SerializerFactory.getSerializerFactory("xml").makeSerializer(writer, format);
            try {
                  serializer.asDOMSerializer().serialize(document);
            } catch (IOException exception) {
                  exception.printStackTrace();
                  System.exit(1);
            }

            // Show the results on the console.
            System.out.println("\n---XMLSerializer output---");
            System.out.println(writer.toString());

          DOMSerializerImpl s = new DOMSerializerImpl();
                  DOMParser p = new DOMParser();
                  try {
       
                      p.parse(args[0]);
                  } catch (Exception e){
                  }
                  Document doc = p.getDocument();


            // create DOM Serializer

            System.out.println("\n---DOMWriter output---");
            LSSerializer domWriter = ((DOMImplementationLS)DOMImplementationImpl.getDOMImplementation()).createLSSerializer();
            DOMConfiguration config = domWriter.getDomConfig();
            config.setParameter("error-handler", new TestXmlns());
            config.setParameter("namespaces", Boolean.FALSE);
            try {
                LSOutput dOut = new DOMOutputImpl();
                dOut.setByteStream(System.out);
                domWriter.write(document,dOut);
            } catch (Exception e){
                e.printStackTrace();
            }

            
      }
    /* (non-Javadoc)
     * @see org.apache.xerces.dom3.DOMErrorHandler#handleError(org.apache.xerces.dom3.DOMError)
     */
    public boolean handleError(DOMError error){
        short severity = error.getSeverity();
        if (severity == DOMError.SEVERITY_ERROR) {
            System.out.println("[dom3-error]: "+error.getMessage());
        }
        
        if (severity == DOMError.SEVERITY_FATAL_ERROR) {
                   System.out.println("[dom3-fatal-error]: "+error.getMessage());
               }

        if (severity == DOMError.SEVERITY_WARNING) {
            System.out.println("[dom3-warning]: "+error.getMessage());
        }
        return true;

    }


}
