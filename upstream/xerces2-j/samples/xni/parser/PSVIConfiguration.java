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

import org.apache.xerces.parsers.XIncludeAwareParserConfiguration;
import org.apache.xerces.util.SymbolTable;
import org.apache.xerces.xni.grammars.XMLGrammarPool;
import org.apache.xerces.xni.parser.XMLComponentManager;

import xni.PSVIWriter;

/**
 * This is the DTD/ XML Schema parser configuration that includes PSVIWriter component.
 * The document will be fully assessed and will produce PSVI as required by XML Schema specification
 * configuration including XML Schema Validator in the pipeline.
 * 
 * @author Elena Litani, IBM
 * @version $Id$
 */
public class PSVIConfiguration extends XIncludeAwareParserConfiguration {

    /** PSVI Writer */
    protected PSVIWriter fPSVIWriter;
    
    //
    // Constructors
    //

    /**
     * Constructs a document parser using the default symbol table and grammar
     * pool or the ones specified by the application (through the properties).
     */
    public PSVIConfiguration() {
        this(null, null);
    } // <init>()

    /**
     * Constructs a document parser using the specified symbol table.
     *
     * @param symbolTable    The symbol table to use.
     */
    public PSVIConfiguration(SymbolTable symbolTable) {
        this(symbolTable, null);
    } // <init>(SymbolTable)

    /**
     * Constructs a document parser using the specified symbol table and
     * grammar pool.
     * <p>
     * <strong>REVISIT:</strong>
     * Grammar pool will be updated when the new validation engine is
     * implemented.
     *
     * @param symbolTable    The symbol table to use.
     * @param grammarPool    The grammar pool to use.
     */
    public PSVIConfiguration(SymbolTable symbolTable,
                                     XMLGrammarPool grammarPool) {
        this(symbolTable, grammarPool, null);
    } // <init>(SymbolTable,XMLGrammarPool)

    /**
     * Constructs a parser configuration using the specified symbol table,
     * grammar pool, and parent settings.
     * <p>
     * <strong>REVISIT:</strong>
     * Grammar pool will be updated when the new validation engine is
     * implemented.
     *
     * @param symbolTable    The symbol table to use.
     * @param grammarPool    The grammar pool to use.
     * @param parentSettings The parent settings.
     */
    public PSVIConfiguration(SymbolTable symbolTable,
                                    XMLGrammarPool grammarPool,
                                    XMLComponentManager parentSettings) {
        super(symbolTable, grammarPool, parentSettings);
        fPSVIWriter = createPSVIWriter();
        if (fPSVIWriter != null) {
            addCommonComponent(fPSVIWriter);
        }

    } // <init>(SymbolTable,XMLGrammarPool)

    /** Configures the pipeline. */
    protected void configurePipeline() {
        super.configurePipeline();
        addPSVIWriterToPipeline();
    } // configurePipeline()
    
    /** Configures the XML 1.1 pipeline. */
    protected void configureXML11Pipeline() {
        super.configureXML11Pipeline();
        addPSVIWriterToPipeline();
    } // configureXML11Pipeline()
    
    /** Adds PSVI writer to the pipeline. */
    protected void addPSVIWriterToPipeline() {
        if (fSchemaValidator != null) {
            fSchemaValidator.setDocumentHandler(fPSVIWriter);
            fPSVIWriter.setDocumentSource(fSchemaValidator);
            fPSVIWriter.setDocumentHandler(fDocumentHandler);
            if (fDocumentHandler != null) {
                fDocumentHandler.setDocumentSource(fPSVIWriter);
            }
        }
    } // addPSVIWriterToPipeline()

    /** Create a PSVIWriter */
    protected PSVIWriter createPSVIWriter(){
        return new PSVIWriter();
    }

} // class PSVIConfiguration
