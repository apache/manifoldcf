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

import java.io.IOException;

import org.apache.xalan.xslt.XSLTInputSource;
import org.apache.xalan.xslt.XSLTProcessor;
import org.apache.xalan.xslt.XSLTProcessorFactory;
import org.apache.xalan.xslt.XSLTResultTarget;
import org.apache.xerces.dom.DocumentImpl;
import org.apache.xml.serialize.HTMLSerializer;
import org.apache.xml.serialize.OutputFormat;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * @author Andy Clark
 * @version $Id$
 */
public class Stylesheet {

    public static void main(String argv[]) {

        if (argv.length != 2) {
            System.err.println("usage: java Stylesheet xml_file xsl_file");
            System.exit(1);
        }


        Document doc = new DocumentImpl();
        try {
            XSLTProcessor processor = XSLTProcessorFactory.getProcessor();
            XSLTInputSource  xml  = new XSLTInputSource(argv[0]);
            XSLTInputSource  xslt = new XSLTInputSource(argv[1]);
            XSLTResultTarget target = new XSLTResultTarget(doc);
            processor.process(xml, xslt, target);
        }
        catch (SAXException e) {
            System.err.println("error: Error processing stylesheet.");
            System.exit(1);
        }

        try {
            OutputFormat format = new OutputFormat();
            format.setIndenting(true);
            format.setIndent(1);
            HTMLSerializer serializer = new HTMLSerializer(format);
            serializer.setOutputByteStream(System.out);
            serializer.serialize(doc);
        }
        catch (IOException e) {
            System.err.println("error: Unable to output document.");
            System.exit(1);
        }

        System.exit(0);

    } // main(String[])

} // class Stylesheet
