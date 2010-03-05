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

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;

import org.apache.xerces.dom.DocumentImpl;
import org.apache.xerces.parsers.DOMParser;
import org.w3c.dom.Document;

import dom.Writer;


/**
 * This testcase tests the Java Serialization
 * of the DOM.
 * I wrote this to test this capability for
 * regresion
 * 
 * @author Jeffrey Rodriguez
 * @version $Id$
 * @see                      TestSerializeDOMOut
 */


public class TestSerializeDOMOut
{          

    public TestSerializeDOMOut(){
    }

      /**
     * Deserializes Java DOM Object 
     * 
     * @param nameSerializeFile
     * @return 
     */
    public void serializeDOM( Document doc, String nameSerializedFile ){
        try {
            ObjectOutputStream out               =
                              new ObjectOutputStream( new FileOutputStream( nameSerializedFile ) );
            out.writeObject(doc);
            out.close();

        } catch ( Exception ex ) {
            ex.printStackTrace();
        }
    }


    public static void main (String[] argv) 
    { 

        if ( argv.length != 1 ) {
            System.out.println("Error - Usage: java TestOut yourFile.xml" );
            System.exit(1);
        }

        String    xmlFilename = argv[0];


        try {
            DOMParser parser     = new DOMParser();

            parser.parse( xmlFilename ); 

            DocumentImpl doc     = (DocumentImpl) parser.getDocument();

            int indexOfextension = xmlFilename.indexOf("." );



            String nameOfSerializedFile = null;

            if ( indexOfextension == -1 ) {
                nameOfSerializedFile = xmlFilename +".ser" ;
            } else {
                nameOfSerializedFile = 
                xmlFilename.substring(0,indexOfextension) + ".ser";
            }

            System.out.println( "Writing Serialize DOM  to file = " + nameOfSerializedFile ); 


            FileOutputStream fileOut =  new FileOutputStream( nameOfSerializedFile );


            TestSerializeDOMOut  tstOut = new TestSerializeDOMOut();

            tstOut.serializeDOM( doc, nameOfSerializedFile );


            System.out.println( "Reading Serialize DOM from " + nameOfSerializedFile );


            TestSerializeDOMIn    tstIn  = new TestSerializeDOMIn();
            doc           = tstIn.deserializeDOM( nameOfSerializedFile );

            Writer prettyWriter = new Writer( false );
            prettyWriter.setOutput(System.out, "UTF8");
            System.out.println( "Here is the whole Document" );
            prettyWriter.write(  doc.getDocumentElement() );
        } catch ( Exception ex ){
            ex.printStackTrace();
        }
    } 
}

