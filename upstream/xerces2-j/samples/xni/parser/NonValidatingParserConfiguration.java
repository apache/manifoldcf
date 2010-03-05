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

package xni.parser;

import org.apache.xerces.impl.XMLNamespaceBinder;
import org.apache.xerces.impl.dtd.XMLDTDValidator;
import org.apache.xerces.parsers.StandardParserConfiguration;
import org.apache.xerces.xni.parser.XMLComponent;
import org.apache.xerces.xni.parser.XMLParserConfiguration;

/**
 * Non-validating parser configuration.
 *
 * @see XMLComponent
 * @see XMLParserConfiguration
 *
 * @author Andy Clark, IBM
 *
 * @version $Id$
 */
public class NonValidatingParserConfiguration 
    extends StandardParserConfiguration {

    //
    // Data
    //

    // components (configurable)

    /** Namespace binder. */
    protected XMLNamespaceBinder fNamespaceBinder;

    //
    // Constructors
    //

    /**
     * Constructs a document parser using the default symbol table and grammar
     * pool or the ones specified by the application (through the properties).
     */
    public NonValidatingParserConfiguration() {

        // create and register missing components
        fNamespaceBinder = new XMLNamespaceBinder();
        addComponent(fNamespaceBinder);

    } // <init>()

    //
    // Protected methods
    //
    
    /** Configures the pipeline. */
    protected void configurePipeline() {

        // REVISIT: This should be better designed. In other words, we
        //          need to figure out what is the best way for people to
        //          re-use *most* of the standard configuration but do 
        //          common things such as remove a component (e.g.the
        //          validator), insert a new component (e.g. XInclude), 
        //          etc... -Ac

        // setup document pipeline
        fScanner.setDocumentHandler(fNamespaceBinder);
        fNamespaceBinder.setDocumentHandler(fDocumentHandler);
        fNamespaceBinder.setDocumentSource(fScanner);

    } // configurePipeline()

    // factory methods

    /** Create a null validator. */
    protected XMLDTDValidator createDTDValidator() {
        return null;
    } // createDTDValidator():XMLDTDValidator

} // class NonValidatingParserConfiguration
