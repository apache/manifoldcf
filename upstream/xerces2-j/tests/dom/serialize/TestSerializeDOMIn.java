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

import java.io.FileInputStream;
import java.io.ObjectInputStream;

import org.apache.xerces.dom.DocumentImpl;
import org.apache.xerces.dom.NodeImpl;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import dom.Writer;


/**
 * This testcase tests the Java Serialization
 * of the DOM.
 * I wrote this to test this capability for
 * regresion
 * 
 * @author Jeffrey Rodriguez
 * @version $Id$
 * @see                      TestSerializeDOMIn
 */
public class TestSerializeDOMIn {

    public TestSerializeDOMIn() {
    }


    /**
     * Serializes Java DOM Object 
     * 
     * @param nameSerializeFile
     * @return 
     */
    public DocumentImpl deserializeDOM( String nameSerializedFile ){
        ObjectInputStream in   = null;
        DocumentImpl      doc  = null;
        try {

            FileInputStream fileIn = new FileInputStream( nameSerializedFile );
            in                     = new ObjectInputStream(fileIn);
            doc                    = (DocumentImpl) in.readObject();//Deserialize object
        } catch ( Exception ex ) {
            ex.printStackTrace();
        }
        return doc;
    }


    public static void main( String argv[]  ){
        if ( argv.length != 2 ) {
            System.out.println("Error - Usage: java TestSerializeDOMIn yourFile.ser elementName" );
            System.exit(1);
        }

        String    xmlFilename = argv[0];

        TestSerializeDOMIn         tst  = new TestSerializeDOMIn();
        DocumentImpl   doc  = tst.deserializeDOM( xmlFilename );

        NodeList nl         = doc.getElementsByTagName( argv[1]);


        int length      = nl.getLength();

        if ( length == 0 )
            System.out.println(argv[1] + ": is not in the document!");

        NodeImpl node = null;
        for ( int i = 0;i<length;i++ ){
            node                = (NodeImpl) nl.item(i);
            Node childOfElement = node.getFirstChild();
            if ( childOfElement != null ){
                System.out.println( node.getNodeName() + ": " +
                                    childOfElement.getNodeValue() );
            }
        }
        try {
           Writer prettyWriter = new Writer( false );
           System.out.println( "Here is the whole Document" );
           prettyWriter.write(  doc.getDocumentElement() );
        } catch( Exception ex ){
        }
    }
}
