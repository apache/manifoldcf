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

package xni;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.apache.xerces.parsers.XMLDocumentParser;
import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.NamespaceContext;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLAttributes;
import org.apache.xerces.xni.XMLDTDContentModelHandler;
import org.apache.xerces.xni.XMLDTDHandler;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XMLString;
import org.apache.xerces.xni.XNIException;
import org.apache.xerces.xni.parser.XMLConfigurationException;
import org.apache.xerces.xni.parser.XMLErrorHandler;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xni.parser.XMLParseException;
import org.apache.xerces.xni.parser.XMLParserConfiguration;

/**
 * Provides a complete trace of XNI document and DTD events for
 * files parsed.
 *
 * @author Andy Clark, IBM
 * @author Arnaud Le Hors, IBM
 *
 * @version $Id$
 */
public class DocumentTracer
    extends XMLDocumentParser
    implements XMLErrorHandler {

    //
    // Constants
    //

    // feature ids

    /** Namespaces feature id (http://xml.org/sax/features/namespaces). */
    protected static final String NAMESPACES_FEATURE_ID =
        "http://xml.org/sax/features/namespaces";

    /** Validation feature id (http://xml.org/sax/features/validation). */
    protected static final String VALIDATION_FEATURE_ID =
        "http://xml.org/sax/features/validation";

    /** Schema validation feature id (http://apache.org/xml/features/validation/schema). */
    protected static final String SCHEMA_VALIDATION_FEATURE_ID =
        "http://apache.org/xml/features/validation/schema";

    /** Schema full checking feature id (http://apache.org/xml/features/validation/schema-full-checking). */
    protected static final String SCHEMA_FULL_CHECKING_FEATURE_ID =
        "http://apache.org/xml/features/validation/schema-full-checking";
    
    /** Honour all schema locations feature id (http://apache.org/xml/features/honour-all-schemaLocations). */
    protected static final String HONOUR_ALL_SCHEMA_LOCATIONS_ID = 
        "http://apache.org/xml/features/honour-all-schemaLocations";

    /** Character ref notification feature id (http://apache.org/xml/features/scanner/notify-char-refs). */
    protected static final String NOTIFY_CHAR_REFS_FEATURE_ID =
        "http://apache.org/xml/features/scanner/notify-char-refs";

    // default settings

    /** Default parser configuration (org.apache.xerces.parsers.XIncludeAwareParserConfiguration). */
    protected static final String DEFAULT_PARSER_CONFIG =
        "org.apache.xerces.parsers.XIncludeAwareParserConfiguration";

    /** Default namespaces support (true). */
    protected static final boolean DEFAULT_NAMESPACES = true;

    /** Default validation support (false). */
    protected static final boolean DEFAULT_VALIDATION = false;

    /** Default Schema validation support (false). */
    protected static final boolean DEFAULT_SCHEMA_VALIDATION = false;

    /** Default Schema full checking support (false). */
    protected static final boolean DEFAULT_SCHEMA_FULL_CHECKING = false;
    
    /** Default honour all schema locations (false). */
    protected static final boolean DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS = false;

    /** Default character notifications (false). */
    protected static final boolean DEFAULT_NOTIFY_CHAR_REFS = false;

    //
    // Data
    //

    /** Temporary QName. */
    private QName fQName = new QName();

    /** Print writer. */
    protected PrintWriter fOut;

    /** Indent level. */
    protected int fIndent;

    protected NamespaceContext fNamespaceContext;

    //
    // Constructors
    //

    /** Default constructor. */
    public DocumentTracer() {
        this(null);
    } // <init>()

    /** Default constructor. */
    public DocumentTracer(XMLParserConfiguration config) {
        super(config);
        setOutput(new PrintWriter(System.out));
        fConfiguration.setErrorHandler(this);
    } // <init>(XMLParserConfiguration)

    //
    // Public methods
    //

    /** Sets the output stream for printing. */
    public void setOutput(OutputStream stream, String encoding)
        throws UnsupportedEncodingException {

        if (encoding == null) {
            encoding = "UTF8";
        }

        Writer writer = new OutputStreamWriter(stream, encoding);
        fOut = new PrintWriter(writer);

    } // setOutput(OutputStream,String)

    /** Sets the output writer. */
    public void setOutput(Writer writer) {

        fOut = writer instanceof PrintWriter
             ? (PrintWriter)writer : new PrintWriter(writer);

    } // setOutput(Writer)

    //
    // XMLDocumentHandler methods
    //

    /**
     * The start of the document.
     *
     * @param systemId The system identifier of the entity if the entity
     *                 is external, null otherwise.
     * @param encoding The auto-detected IANA encoding name of the entity
     *                 stream. This value will be null in those situations
     *                 where the entity encoding is not auto-detected (e.g.
     *                 internal entities or a document entity that is
     *                 parsed from a java.io.Reader).
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startDocument(XMLLocator locator, String encoding,
                              NamespaceContext namespaceContext, Augmentations augs)
        throws XNIException {
        fNamespaceContext = namespaceContext;
        fIndent = 0;
        printIndent();
        fOut.print("startDocument(");
        fOut.print("locator=");
        if (locator == null) {
            fOut.print("null");
        }
        else {
            fOut.print('{');
            fOut.print("publicId=");
            printQuotedString(locator.getPublicId());
            fOut.print(',');
            fOut.print("literal systemId=");
            printQuotedString(locator.getLiteralSystemId());
            fOut.print(',');
            fOut.print("baseSystemId=");
            printQuotedString(locator.getBaseSystemId());
            fOut.print(',');
            fOut.print("expanded systemId=");
            printQuotedString(locator.getExpandedSystemId());
            fOut.print(',');
            fOut.print("lineNumber=");
            fOut.print(locator.getLineNumber());
            fOut.print(',');
            fOut.print("columnNumber=");
            fOut.print(locator.getColumnNumber());
            fOut.print('}');
        }
        fOut.print(',');
        fOut.print("encoding=");
        printQuotedString(encoding);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // XMLLocator()

    /** XML Declaration. */
    public void xmlDecl(String version, String encoding,
                        String standalone, Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("xmlDecl(");
        fOut.print("version=");
        printQuotedString(version);
        fOut.print(',');
        fOut.print("encoding=");
        printQuotedString(encoding);
        fOut.print(',');
        fOut.print("standalone=");
        printQuotedString(standalone);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');

    } // xmlDecl(String,String,String,String)

    /** Doctype declaration. */
    public void doctypeDecl(String rootElement, String publicId,
                            String systemId, Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("doctypeDecl(");
        fOut.print("rootElement=");
        printQuotedString(rootElement);
        fOut.print(',');
        fOut.print("publicId=");
        printQuotedString(publicId);
        fOut.print(',');
        fOut.print("systemId=");
        printQuotedString(systemId);
        fOut.println(')');
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.flush();

    } // doctypeDecl(String,String,String)

    /** Start element. */
    public void startElement(QName element, XMLAttributes attributes, Augmentations augs)
        throws XNIException {

        printInScopeNamespaces();

        printIndent();
        fOut.print("startElement(");
        printElement(element, attributes);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // startElement(QName,XMLAttributes)

    /** Empty element. */
    public void emptyElement(QName element, XMLAttributes attributes, Augmentations augs)
        throws XNIException {
        printInScopeNamespaces();
        printIndent();
        fOut.print("emptyElement(");
        printElement(element, attributes);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();
        printEndNamespaceMapping();
    } // emptyElement(QName,XMLAttributes)


    public void characters(XMLString text, Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("characters(");
        fOut.print("text=");
        printQuotedString(text.ch, text.offset, text.length);
        /***
        if (augs != null) {
            ElementPSVI element = (ElementPSVI)augs.getItem(Constants.ELEMENT_PSVI);
            fOut.print(",schemaNormalized=");
            printQuotedString(element.getSchemaNormalizedValue());
        }
        /***/
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // characters(XMLString)



    /** Ignorable whitespace. */
    public void ignorableWhitespace(XMLString text, Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("ignorableWhitespace(");
        fOut.print("text=");
        printQuotedString(text.ch, text.offset, text.length);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // ignorableWhitespace(XMLString)

    /** End element. */
    public void endElement(QName element, Augmentations augs) throws XNIException {

        fIndent--;
        printIndent();
        fOut.print("endElement(");
        fOut.print("element=");
        fOut.print('{');
        fOut.print("prefix=");
        printQuotedString(element.prefix);
        fOut.print(',');
        fOut.print("localpart=");
        printQuotedString(element.localpart);
        fOut.print(',');
        fOut.print("rawname=");
        printQuotedString(element.rawname);
        fOut.print(',');
        fOut.print("uri=");
        printQuotedString(element.uri);
        fOut.print('}');
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
		fOut.flush();

        printEndNamespaceMapping();

    } // endElement(QName)


    /** Start CDATA section. */
    public void startCDATA(Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("startCDATA(");
        if (augs != null) {
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // startCDATA()

    /** End CDATA section. */
    public void endCDATA(Augmentations augs) throws XNIException {

        fIndent--;
        printIndent();
        fOut.print("endCDATA(");
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } //  endCDATA()

    /** Start entity. */
    public void startGeneralEntity(String name,
                                   XMLResourceIdentifier identifier,
                                   String encoding, Augmentations augs)
        throws XNIException {

        printIndent();
        fOut.print("startGeneralEntity(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.print(',');
        fOut.print("identifier=");
        fOut.print(identifier);
        fOut.print(',');
        fOut.print("encoding=");
        printQuotedString(encoding);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // startEntity(String,String,String,String)

    /** Text declaration. */
    public void textDecl(String version, String encoding, Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("textDecl(");
        fOut.print("version=");
        printQuotedString(version);
        fOut.print(',');
        fOut.print("encoding=");
        printQuotedString(encoding);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // textDecl(String,String)

    /** Comment. */
    public void comment(XMLString text, Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("comment(");
        fOut.print("text=");
        printQuotedString(text.ch, text.offset, text.length);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // comment(XMLText)

    /** Processing instruction. */
    public void processingInstruction(String target, XMLString data, Augmentations augs)
        throws XNIException {

        printIndent();
        fOut.print("processingInstruction(");
        fOut.print("target=");
        printQuotedString(target);
        fOut.print(',');
        fOut.print("data=");
        printQuotedString(data.ch, data.offset, data.length);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // processingInstruction(String,XMLString)

    /** End entity. */
    public void endGeneralEntity(String name, Augmentations augs) throws XNIException {

        fIndent--;
        printIndent();
        fOut.print("endGeneralEntity(");
        fOut.print("name=");
        printQuotedString(name);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // endEntity(String)

    /** End document. */
    public void endDocument(Augmentations augs) throws XNIException {

        fIndent--;
        printIndent();
        fOut.print("endDocument(");
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // endDocument();

    //
    // XMLDTDHandler
    //

    /** Start DTD. */
    public void startDTD(XMLLocator locator, Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("startDTD(");
        fOut.print("locator=");
        if (locator == null) {
            fOut.print("null");
        }
        else {
            fOut.print('{');
            fOut.print("publicId=");
            printQuotedString(locator.getPublicId());
            fOut.print(',');
            fOut.print("literal systemId=");
            printQuotedString(locator.getLiteralSystemId());
            fOut.print(',');
            fOut.print("baseSystemId=");
            printQuotedString(locator.getBaseSystemId());
            fOut.print(',');
            fOut.print("expanded systemId=");
            printQuotedString(locator.getExpandedSystemId());
            fOut.print(',');
            fOut.print("lineNumber=");
            fOut.print(locator.getLineNumber());
            fOut.print(',');
            fOut.print("columnNumber=");
            fOut.print(locator.getColumnNumber());
            fOut.print('}');
        }
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // startDTD(XMLLocator)

    /** Start external subset. */
    public void startExternalSubset(XMLResourceIdentifier identifier,
                                    Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("startExternalSubset(");
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // startExternalSubset(Augmentations)

    /** End external subset. */
    public void endExternalSubset(Augmentations augs) throws XNIException {

        fIndent--;
        printIndent();
        fOut.print("endExternalSubset(");
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // endExternalSubset(Augmentations)

    /** Characters.*/
    public void ignoredCharacters(XMLString text, Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("ignoredCharacters(");
        fOut.print("text=");
        printQuotedString(text.ch, text.offset, text.length);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // characters(XMLString)

    /** Start entity. */
    public void startParameterEntity(String name,
                                     XMLResourceIdentifier identifier,
                                     String encoding, Augmentations augs)
        throws XNIException {

        printIndent();
        fOut.print("startParameterEntity(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.print(',');
        fOut.print("identifier=");
        fOut.print(identifier);
        fOut.print(',');
        fOut.print("encoding=");
        printQuotedString(encoding);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // startEntity(String,String,String,String)

    /** End entity. */
    public void endParameterEntity(String name, Augmentations augs) throws XNIException {

        fIndent--;
        printIndent();
        fOut.print("endParameterEntity(");
        fOut.print("name=");
        printQuotedString(name);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // endEntity(String)

    /** Element declaration. */
    public void elementDecl(String name, String contentModel, Augmentations augs)
        throws XNIException {

        printIndent();
        fOut.print("elementDecl(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.print(',');
        fOut.print("contentModel=");
        printQuotedString(contentModel);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // elementDecl(String,String)

    /** Start attribute list. */
    public void startAttlist(String elementName, Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("startAttlist(");
        fOut.print("elementName=");
        printQuotedString(elementName);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // startAttlist(String)

    /** Attribute declaration. */
    public void attributeDecl(String elementName, String attributeName,
                              String type, String[] enumeration,
                              String defaultType, XMLString defaultValue,
                              XMLString nonNormalizedDefaultValue,
                              Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("attributeDecl(");
        fOut.print("elementName=");
        printQuotedString(elementName);
        fOut.print(',');
        fOut.print("attributeName=");
        printQuotedString(attributeName);
        fOut.print(',');
        fOut.print("type=");
        printQuotedString(type);
        fOut.print(',');
        fOut.print("enumeration=");
        if (enumeration == null) {
            fOut.print("null");
        }
        else {
            fOut.print('{');
            for (int i = 0; i < enumeration.length; i++) {
                printQuotedString(enumeration[i]);
                if (i < enumeration.length - 1) {
                    fOut.print(',');
                }
            }
            fOut.print('}');
        }
        fOut.print(',');
        fOut.print("defaultType=");
        printQuotedString(defaultType);
        fOut.print(',');
        fOut.print("defaultValue=");
        if (defaultValue == null) {
            fOut.print("null");
        }
        else {
            printQuotedString(defaultValue.ch, defaultValue.offset,
                              defaultValue.length);
        }
        fOut.print(',');
        fOut.print("nonNormalizedDefaultValue=");
        if (nonNormalizedDefaultValue == null) {
            fOut.print("null");
        }
        else {
            printQuotedString(nonNormalizedDefaultValue.ch, nonNormalizedDefaultValue.offset,
                              nonNormalizedDefaultValue.length);
        }
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // attributeDecl(String,String,String,String[],String,XMLString)

    /** End attribute list. */
    public void endAttlist(Augmentations augs) throws XNIException {

        fIndent--;
        printIndent();
        fOut.print("endAttlist(");
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // endAttlist()

    /** Internal entity declaration. */
    public void internalEntityDecl(String name, XMLString text,
                                   XMLString nonNormalizedText,
                                   Augmentations augs)
        throws XNIException {

        printIndent();
        fOut.print("internalEntityDecl(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.print(',');
        fOut.print("text=");
        printQuotedString(text.ch, text.offset, text.length);
        fOut.print(',');
        fOut.print("nonNormalizedText=");
        printQuotedString(nonNormalizedText.ch, nonNormalizedText.offset,
                          nonNormalizedText.length);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // internalEntityDecl(String,XMLString)

    /** External entity declaration. */
    public void externalEntityDecl(String name,XMLResourceIdentifier identifier, Augmentations augs)
        throws XNIException {

        printIndent();
        fOut.print("externalEntityDecl(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.print(',');
        fOut.print("publicId=");
        printQuotedString(identifier.getPublicId());
        fOut.print(',');
        fOut.print("systemId=");
        printQuotedString(identifier.getLiteralSystemId());
        fOut.print(',');
        fOut.print("baseSystemId=");
        printQuotedString(identifier.getBaseSystemId());
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // externalEntityDecl(String,String,String)

    /** Unparsed entity declaration. */
    public void unparsedEntityDecl(String name, XMLResourceIdentifier identifier,
                                   String notation, Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("unparsedEntityDecl(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.print(',');
        fOut.print("publicId=");
        printQuotedString(identifier.getPublicId());
        fOut.print(',');
        fOut.print("systemId=");
        printQuotedString(identifier.getLiteralSystemId());
        fOut.print(',');
        fOut.print("baseSystemId=");
        printQuotedString(identifier.getBaseSystemId());
        fOut.print(',');
        fOut.print("notation=");
        printQuotedString(notation);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // unparsedEntityDecl(String,String,String,String)

    /** Notation declaration. */
    public void notationDecl(String name, XMLResourceIdentifier identifier,
                             Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("notationDecl(");
        fOut.print("name=");
        printQuotedString(name);
        fOut.print(',');
        fOut.print("publicId=");
        printQuotedString(identifier.getPublicId());
        fOut.print(',');
        fOut.print("systemId=");
        printQuotedString(identifier.getLiteralSystemId());
        fOut.print(',');
        fOut.print("baseSystemId=");
        printQuotedString(identifier.getBaseSystemId());
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // notationDecl(String,String,String)

    /** Start conditional section. */
    public void startConditional(short type, Augmentations augs)
        throws XNIException {

        printIndent();
        fOut.print("startConditional(");
        fOut.print("type=");
        switch (type) {
            case XMLDTDHandler.CONDITIONAL_IGNORE: {
                fOut.print("CONDITIONAL_IGNORE");
                break;
            }
            case XMLDTDHandler.CONDITIONAL_INCLUDE: {
                fOut.print("CONDITIONAL_INCLUDE");
                break;
            }
            default: {
                fOut.print("??? ("+type+')');
            }
        }
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // startConditional(short)

    /** End conditional section. */
    public void endConditional(Augmentations augs) throws XNIException {

        fIndent--;
        printIndent();
        fOut.print("endConditional(");
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // endConditional()

    /** End DTD. */
    public void endDTD(Augmentations augs) throws XNIException {

        fIndent--;
        printIndent();
        fOut.print("endDTD(");
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // endDTD()

    //
    // XMLDTDContentModelHandler methods
    //

    /** Start content model. */
    public void startContentModel(String elementName, Augmentations augs)
        throws XNIException {

        printIndent();
        fOut.print("startContentModel(");
        fOut.print("elementName=");
        printQuotedString(elementName);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // startContentModel(String)

    /** Any. */
    public void any(Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("any(");
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // any()

    /** Empty. */
    public void empty(Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("empty(");
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // empty()

    /** Start group. */
    public void startGroup(Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("startGroup(");
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();
        fIndent++;

    } // childrenStartGroup()

    /** #PCDATA. */
    public void pcdata(Augmentations augs) throws XNIException {

        printIndent();
        fOut.print("pcdata(");
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // pcdata()

    /** Element. */
    public void element(String elementName, Augmentations augs)
        throws XNIException {

        printIndent();
        fOut.print("element(");
        fOut.print("elementName=");
        printQuotedString(elementName);
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // element(String)

    /** separator. */
    public void separator(short separator, Augmentations augs)
        throws XNIException {

        printIndent();
        fOut.print("separator(");
        fOut.print("separator=");
        switch (separator) {
            case XMLDTDContentModelHandler.SEPARATOR_CHOICE: {
                fOut.print("SEPARATOR_CHOICE");
                break;
            }
            case XMLDTDContentModelHandler.SEPARATOR_SEQUENCE: {
                fOut.print("SEPARATOR_SEQUENCE");
                break;
            }
            default: {
                fOut.print("??? ("+separator+')');
            }
        }
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // separator(short)

    /** Occurrence. */
    public void occurrence(short occurrence, Augmentations augs)
        throws XNIException {

        printIndent();
        fOut.print("occurrence(");
        fOut.print("occurrence=");
        switch (occurrence) {
            case XMLDTDContentModelHandler.OCCURS_ONE_OR_MORE: {
                fOut.print("OCCURS_ONE_OR_MORE");
                break;
            }
            case XMLDTDContentModelHandler.OCCURS_ZERO_OR_MORE: {
                fOut.print("OCCURS_ZERO_OR_MORE");
                break;
            }
            case XMLDTDContentModelHandler.OCCURS_ZERO_OR_ONE: {
                fOut.print("OCCURS_ZERO_OR_ONE");
                break;
            }
            default: {
                fOut.print("??? ("+occurrence+')');
            }
        }
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // occurrence(short)

    /** End group. */
    public void endGroup(Augmentations augs) throws XNIException {

        fIndent--;
        printIndent();
        fOut.print("endGroup(");
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // childrenEndGroup()

    /** End content model. */
    public void endContentModel(Augmentations augs) throws XNIException {

        fIndent--;
        printIndent();
        fOut.print("endContentModel(");
        if (augs != null) {
            fOut.print(',');
            printAugmentations(augs);
        }
        fOut.println(')');
        fOut.flush();

    } // endContentModel()

    //
    // XMLErrorHandler methods
    //

    /** Warning. */
    public void warning(String domain, String key, XMLParseException ex)
        throws XNIException {
        printError("Warning", ex);
    } // warning(String,String,XMLParseException)

    /** Error. */
    public void error(String domain, String key, XMLParseException ex)
        throws XNIException {
        printError("Error", ex);
    } // error(String,String,XMLParseException)

    /** Fatal error. */
    public void fatalError(String domain, String key, XMLParseException ex)
        throws XNIException {
        printError("Fatal Error", ex);
        throw ex;
    } // fatalError(String,String,XMLParseException)

    //
    // Protected methods
    //
   protected void printInScopeNamespaces(){
        int count = fNamespaceContext.getDeclaredPrefixCount();
        if (count>0){
            for (int i = 0; i < count; i++) {
                printIndent();
                fOut.print("declaredPrefix(");
                fOut.print("prefix=");
                String prefix = fNamespaceContext.getDeclaredPrefixAt(i);
                printQuotedString(prefix);
                fOut.print(',');
                fOut.print("uri=");
                printQuotedString(fNamespaceContext.getURI(prefix));
                fOut.println(')');
                fOut.flush();
            }
        }
   }

   protected void printEndNamespaceMapping(){
        int count = fNamespaceContext.getDeclaredPrefixCount();
        if (count > 0) {
            for (int i = 0; i < count; i++) {
                printIndent();
                fOut.print("endPrefix(");
                fOut.print("prefix=");
                String prefix = fNamespaceContext.getDeclaredPrefixAt(i);
                printQuotedString(prefix);
                fOut.println(')');
                fOut.flush();
            }
        }

   }
    /** Prints an element. */
    protected void printElement(QName element, XMLAttributes attributes) {

        fOut.print("element=");
        fOut.print('{');
        fOut.print("prefix=");
        printQuotedString(element.prefix);
        fOut.print(',');
        fOut.print("localpart=");
        printQuotedString(element.localpart);
        fOut.print(',');
        fOut.print("rawname=");
        printQuotedString(element.rawname);
        fOut.print(',');
        fOut.print("uri=");
        printQuotedString(element.uri);
        fOut.print('}');
        fOut.print(',');
        fOut.print("attributes=");
        if (attributes == null) {
            fOut.println("null");
        }
        else {
            fOut.print('{');
            int length = attributes.getLength();
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    fOut.print(',');
                }
                attributes.getName(i, fQName);
                String attrType = attributes.getType(i);
                String attrValue = attributes.getValue(i);
                String attrNonNormalizedValue = attributes.getNonNormalizedValue(i);
                Augmentations augs = attributes.getAugmentations(i);
                fOut.print("name=");
                fOut.print('{');
                fOut.print("prefix=");
                printQuotedString(fQName.prefix);
                fOut.print(',');
                fOut.print("localpart=");
                printQuotedString(fQName.localpart);
                fOut.print(',');
                fOut.print("rawname=");
                printQuotedString(fQName.rawname);
                fOut.print(',');
                fOut.print("uri=");
                printQuotedString(fQName.uri);
                fOut.print('}');
                fOut.print(',');
                fOut.print("type=");
                printQuotedString(attrType);
                fOut.print(',');
                fOut.print("value=");
                printQuotedString(attrValue);
                fOut.print(',');
                fOut.print("nonNormalizedValue=");
                printQuotedString(attrNonNormalizedValue);
                if (attributes.isSpecified(i) == false ) {
                   fOut.print("(default)");
                }
                if (augs != null) {
                    fOut.print(',');
                    printAugmentations(augs);
                }
                fOut.print('}');
            }
            fOut.print('}');
        }

    } // printElement(QName,XMLAttributes)

    /** Prints augmentations. */
    protected void printAugmentations(Augmentations augs) {
        fOut.print("augs={");
        java.util.Enumeration keys = augs.keys();
        while (keys.hasMoreElements()) {
            String key = (String)keys.nextElement();
            Object value = augs.getItem(key);
            fOut.print(key);
            fOut.print('#');
            fOut.print(String.valueOf(value));
        }
        fOut.print('}');
    } // printAugmentations(Augmentations)

    /** Print quoted string. */
    protected void printQuotedString(String s) {

        if (s == null) {
            fOut.print("null");
            return;
        }

        fOut.print('"');
        int length = s.length();
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            normalizeAndPrint(c);
        }
        fOut.print('"');

    } // printQuotedString(String)

    /** Print quoted string. */
    protected void printQuotedString(char[] ch, int offset, int length) {

        fOut.print('"');
        for (int i = 0; i < length; i++) {
            normalizeAndPrint(ch[offset + i]);
        }
        fOut.print('"');

    } // printQuotedString(char[],int,int)

    /** Normalize and print. */
    protected void normalizeAndPrint(char c) {

        switch (c) {
            case '\n': {
                fOut.print("\\n");
                break;
            }
            case '\r': {
                fOut.print("\\r");
                break;
            }
            case '\t': {
                fOut.print("\\t");
                break;
            }
            case '\\': {
                fOut.print("\\\\");
                break;
            }
            case '"': {
                fOut.print("\\\"");
                break;
            }
            default: {
                fOut.print(c);
            }
        }

    } // normalizeAndPrint(char)

    /** Prints the error message. */
    protected void printError(String type, XMLParseException ex) {

        System.err.print("[");
        System.err.print(type);
        System.err.print("] ");
        String systemId = ex.getExpandedSystemId();
        if (systemId != null) {
            int index = systemId.lastIndexOf('/');
            if (index != -1)
                systemId = systemId.substring(index + 1);
            System.err.print(systemId);
        }
        System.err.print(':');
        System.err.print(ex.getLineNumber());
        System.err.print(':');
        System.err.print(ex.getColumnNumber());
        System.err.print(": ");
        System.err.print(ex.getMessage());
        System.err.println();
        System.err.flush();

    } // printError(String,XMLParseException)

    /** Prints the indent. */
    protected void printIndent() {

        for (int i = 0; i < fIndent; i++) {
            fOut.print(' ');
        }

    } // printIndent()

    //
    // MAIN
    //

    /** Main. */
    public static void main(String[] argv) throws Exception {

        // is there anything to do?
        if (argv.length == 0) {
            printUsage();
            System.exit(1);
        }

        // variables
        XMLDocumentParser parser = null;
        XMLParserConfiguration parserConfig = null;
        boolean namespaces = DEFAULT_NAMESPACES;
        boolean validation = DEFAULT_VALIDATION;
        boolean schemaValidation = DEFAULT_SCHEMA_VALIDATION;
        boolean schemaFullChecking = DEFAULT_SCHEMA_FULL_CHECKING;
        boolean honourAllSchemaLocations = DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS;
        boolean notifyCharRefs = DEFAULT_NOTIFY_CHAR_REFS;

        // process arguments
        for (int i = 0; i < argv.length; i++) {
            String arg = argv[i];
            if (arg.startsWith("-")) {
                String option = arg.substring(1);
                if (option.equals("p")) {
                    // get parser name
                    if (++i == argv.length) {
                        System.err.println("error: Missing argument to -p option.");
                        continue;
                    }
                    String parserName = argv[i];

                    // create parser
                    try {
                        parserConfig = (XMLParserConfiguration)ObjectFactory.newInstance(parserName,
                            ObjectFactory.findClassLoader(), true);
                        parser = null;
                    }
                    catch (Exception e) {
                        parserConfig = null;
                        System.err.println("error: Unable to instantiate parser configuration ("+parserName+")");
                    }
                    continue;
                }
                if (option.equalsIgnoreCase("n")) {
                    namespaces = option.equals("n");
                    continue;
                }
                if (option.equalsIgnoreCase("v")) {
                    validation = option.equals("v");
                    continue;
                }
                if (option.equalsIgnoreCase("s")) {
                    schemaValidation = option.equals("s");
                    continue;
                }
                if (option.equalsIgnoreCase("f")) {
                    schemaFullChecking = option.equals("f");
                    continue;
                }
                if (option.equalsIgnoreCase("hs")) {
                    honourAllSchemaLocations = option.equals("hs");
                    continue;
                }
                if (option.equalsIgnoreCase("c")) {
                    notifyCharRefs = option.equals("c");
                    continue;
                }
                if (option.equals("h")) {
                    printUsage();
                    continue;
                }
            }

            // use default parser?
            if (parserConfig == null) {

                // create parser
                try {
                    parserConfig = (XMLParserConfiguration)ObjectFactory.newInstance(DEFAULT_PARSER_CONFIG,
                        ObjectFactory.findClassLoader(), true);
                }
                catch (Exception e) {
                    System.err.println("error: Unable to instantiate parser configuration ("+DEFAULT_PARSER_CONFIG+")");
                    continue;
                }
            }

            // set parser features
            if (parser == null) {
                parser = new DocumentTracer(parserConfig);
            }
            try {
                parserConfig.setFeature(NAMESPACES_FEATURE_ID, namespaces);
            }
            catch (XMLConfigurationException e) {
                System.err.println("warning: Parser does not support feature ("+NAMESPACES_FEATURE_ID+")");
            }
            try {
                parserConfig.setFeature(VALIDATION_FEATURE_ID, validation);
            }
            catch (XMLConfigurationException e) {
                System.err.println("warning: Parser does not support feature ("+VALIDATION_FEATURE_ID+")");
            }
            try {
                parserConfig.setFeature(SCHEMA_VALIDATION_FEATURE_ID, schemaValidation);
            }
            catch (XMLConfigurationException e) {
                if (e.getType() == XMLConfigurationException.NOT_SUPPORTED) {
                    System.err.println("warning: Parser does not support feature ("+SCHEMA_VALIDATION_FEATURE_ID+")");
                }
            }
            try {
                parserConfig.setFeature(SCHEMA_FULL_CHECKING_FEATURE_ID, schemaFullChecking);
            }
            catch (XMLConfigurationException e) {
                if (e.getType() == XMLConfigurationException.NOT_SUPPORTED) {
                    System.err.println("warning: Parser does not support feature ("+SCHEMA_FULL_CHECKING_FEATURE_ID+")");
                }
            }
            try {
                parserConfig.setFeature(HONOUR_ALL_SCHEMA_LOCATIONS_ID, honourAllSchemaLocations);
            }
            catch (XMLConfigurationException e) {
                if (e.getType() == XMLConfigurationException.NOT_SUPPORTED) {
                    System.err.println("warning: Parser does not support feature ("+HONOUR_ALL_SCHEMA_LOCATIONS_ID+")");
                }
            }
            try {
                parserConfig.setFeature(NOTIFY_CHAR_REFS_FEATURE_ID, notifyCharRefs);
            }
            catch (XMLConfigurationException e) {
                if (e.getType() == XMLConfigurationException.NOT_RECOGNIZED) {
                    //e.printStackTrace();
                    System.err.println("warning: Parser does not recognize feature ("+NOTIFY_CHAR_REFS_FEATURE_ID+")");
                }
                else {
                    System.err.println("warning: Parser does not support feature ("+NOTIFY_CHAR_REFS_FEATURE_ID+")");
                }
            }

            // parse file
            try {
                parser.parse(new XMLInputSource(null, arg, null));
            }
            catch (XMLParseException e) {
                // ignore
            }
            catch (Exception e) {
                System.err.println("error: Parse error occurred - "+e.getMessage());
                if (e instanceof XNIException) {
                    e = ((XNIException)e).getException();
                }
                e.printStackTrace(System.err);
            }
        }

    } // main(String[])

    //
    // Private static methods
    //

    /** Prints the usage. */
    private static void printUsage() {

        System.err.println("usage: java xni.DocumentTracer (options) uri ...");
        System.err.println();

        System.err.println("options:");
        System.err.println("  -p name     Specify parser configuration by name.");
        System.err.println("  -n | -N     Turn on/off namespace processing.");
        System.err.println("  -v | -V     Turn on/off validation.");
        System.err.println("  -s | -S     Turn on/off Schema validation support.");
        System.err.println("              NOTE: Not supported by all parser configurations.");
        System.err.println("  -f  | -F    Turn on/off Schema full checking.");
        System.err.println("              NOTE: Requires use of -s and not supported by all parsers.");
        System.err.println("  -hs | -HS   Turn on/off honouring of all schema locations.");
        System.err.println("              NOTE: Requires use of -s and not supported by all parsers.");
        System.err.println("  -c | -C     Turn on/off character notifications");
        System.err.println("  -h          This help screen.");
        System.err.println();

        System.err.println("defaults:");
        System.err.println("  Config:     "+DEFAULT_PARSER_CONFIG);
        System.err.print("  Namespaces: ");
        System.err.println(DEFAULT_NAMESPACES ? "on" : "off");
        System.err.print("  Validation: ");
        System.err.println(DEFAULT_VALIDATION ? "on" : "off");
        System.err.print("  Schema:     ");
        System.err.println(DEFAULT_SCHEMA_VALIDATION ? "on" : "off");
        System.err.print("  Schema full checking:     ");
        System.err.println(DEFAULT_SCHEMA_FULL_CHECKING ? "on" : "off");
        System.err.print("  Honour all schema locations:     ");
        System.err.println(DEFAULT_HONOUR_ALL_SCHEMA_LOCATIONS ? "on" : "off");
        System.err.print("  Char refs:  ");
        System.err.println(DEFAULT_NOTIFY_CHAR_REFS ? "on" : "off" );

    } // printUsage()

} // class DocumentTracer
