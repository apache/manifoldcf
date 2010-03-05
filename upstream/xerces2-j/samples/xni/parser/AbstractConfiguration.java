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

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Vector;

import org.apache.xerces.xni.XMLDocumentHandler;
import org.apache.xerces.xni.XMLDTDHandler;
import org.apache.xerces.xni.XMLDTDContentModelHandler;
import org.apache.xerces.xni.XNIException;

import org.apache.xerces.xni.parser.XMLComponent;
import org.apache.xerces.xni.parser.XMLConfigurationException;
import org.apache.xerces.xni.parser.XMLEntityResolver;
import org.apache.xerces.xni.parser.XMLErrorHandler;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xni.parser.XMLParserConfiguration;

/**
 * This abstract parser configuration simply helps manage components, 
 * features and properties, and other tasks common to all parser
 * configurations. In order to subclass this configuration and use
 * it effectively, the subclass is required to do the following:
 * <ul>
 * <li>
 *  Add all configurable components using the <code>addComponent</code>
 *  method,</li>
 * <li>Implement the <code>parse</code> method, and</li>
 * <li>Call the <code>resetComponents</code> before parsing.</li>
 * </ul>
 *
 * @author Andy Clark, IBM
 *
 * @version $Id$
 */
public abstract class AbstractConfiguration 
    implements XMLParserConfiguration {

    //
    // Data
    //

    // features and properties

    /** Recognized features. */
    protected final Vector fRecognizedFeatures = new Vector();

    /** Recognized properties. */
    protected final Vector fRecognizedProperties = new Vector();

    /** Features. */
    protected final Hashtable fFeatures = new Hashtable();

    /** Properties. */
    protected final Hashtable fProperties = new Hashtable();

    // other parser configuration fields

    /** The registered entity resolver. */
    protected XMLEntityResolver fEntityResolver;

    /** The registered error handler. */
    protected XMLErrorHandler fErrorHandler;

    /** The registered document handler. */
    protected XMLDocumentHandler fDocumentHandler;

    /** The registered DTD handler. */
    protected XMLDTDHandler fDTDHandler;

    /** The registered DTD content model handler. */
    protected XMLDTDContentModelHandler fDTDContentModelHandler;
    
    /** Locale for error messages. */
    protected Locale fLocale;

    // components

    /** List of configurable components. */
    protected final Vector fComponents = new Vector();

    //
    // XMLParserConfiguration methods
    //

    /**
     * Allows a parser to add parser specific features to be recognized
     * and managed by the parser configuration.
     *
     * @param featureIds An array of the additional feature identifiers 
     *                   to be recognized.
     */
    public void addRecognizedFeatures(String[] featureIds) {
        int length = featureIds != null ? featureIds.length : 0;
        for (int i = 0; i < length; i++) {
            String featureId = featureIds[i];
            if (!fRecognizedFeatures.contains(featureId)) {
                fRecognizedFeatures.addElement(featureId);
            }
        }
    } // addRecognizedFeatures(String[])
    
    /**
     * Sets the state of a feature. This method is called by the parser
     * and gets propagated to components in this parser configuration.
     * 
     * @param featureId The feature identifier.
     * @param state     The state of the feature.
     *
     * @throws XMLConfigurationException Thrown if there is a configuration
     *                                   error.
     */
    public void setFeature(String featureId, boolean state)
        throws XMLConfigurationException {
        if (!fRecognizedFeatures.contains(featureId)) {
            short type = XMLConfigurationException.NOT_RECOGNIZED;
            throw new XMLConfigurationException(type, featureId);
        }
        fFeatures.put(featureId, state ? Boolean.TRUE : Boolean.FALSE);
        int length = fComponents.size();
        for (int i = 0; i < length; i++) {
            XMLComponent component = (XMLComponent)fComponents.elementAt(i);
            component.setFeature(featureId, state);
        }
    } // setFeature(String,boolean)

    /**
     * Returns the state of a feature.
     * 
     * @param featureId The feature identifier.
     * 
     * @throws XMLConfigurationException Thrown if there is a configuration
     *                                   error.
     */
    public boolean getFeature(String featureId) 
        throws XMLConfigurationException {
        if (!fRecognizedFeatures.contains(featureId)) {
            short type = XMLConfigurationException.NOT_RECOGNIZED;
            throw new XMLConfigurationException(type, featureId);
        }
        Boolean state = (Boolean)fFeatures.get(featureId);
        return state != null ? state.booleanValue() : false;
    } // getFeature(String):boolean
    
    /**
     * Allows a parser to add parser specific properties to be recognized
     * and managed by the parser configuration.
     *
     * @param propertyIds An array of the additional property identifiers 
     *                    to be recognized.
     */
    public void addRecognizedProperties(String[] propertyIds) {
        int length = propertyIds != null ? propertyIds.length : 0;
        for (int i = 0; i < length; i++) {
            String propertyId = propertyIds[i];
            if (!fRecognizedProperties.contains(propertyId)) {
                fRecognizedProperties.addElement(propertyId);
            }
        }
    } // addRecognizedProperties(String[])

    /**
     * Sets the value of a property. This method is called by the parser
     * and gets propagated to components in this parser configuration.
     * 
     * @param propertyId The property identifier.
     * @param value      The value of the property.
     *
     * @throws XMLConfigurationException Thrown if there is a configuration
     *                                   error.
     */
    public void setProperty(String propertyId, Object value) 
        throws XMLConfigurationException {
        if (!fRecognizedProperties.contains(propertyId)) {
            short type = XMLConfigurationException.NOT_RECOGNIZED;
            throw new XMLConfigurationException(type, propertyId);
        }
        if (value != null) {
            fProperties.put(propertyId, value);
        }
        else {
            fProperties.remove(propertyId);
        }
        int length = fComponents.size();
        for (int i = 0; i < length; i++) {
            XMLComponent component = (XMLComponent)fComponents.elementAt(i);
            component.setProperty(propertyId, value);
        }
    } // setProperty(String,Object)

    /**
     * Returns the value of a property.
     * 
     * @param propertyId The property identifier.
     * 
     * @throws XMLConfigurationException Thrown if there is a configuration
     *                                   error.
     */
    public Object getProperty(String propertyId) 
        throws XMLConfigurationException {
        if (!fRecognizedProperties.contains(propertyId)) {
            short type = XMLConfigurationException.NOT_RECOGNIZED;
            throw new XMLConfigurationException(type, propertyId);
        }
        Object value = fProperties.get(propertyId);
        return value;
    } // getProperty(String):Object

    /**
     * Sets the entity resolver.
     *
     * @param resolver The new entity resolver.
     */
    public void setEntityResolver(XMLEntityResolver resolver) {
        fEntityResolver = resolver;
    } // setEntityResolver(XMLEntityResolver)

    /** Returns the registered entity resolver. */
    public XMLEntityResolver getEntityResolver() {
        return fEntityResolver;
    } // getEntityResolver():XMLEntityResolver

    /**
     * Sets the error handler.
     *
     * @param handler The error resolver.
     */
    public void setErrorHandler(XMLErrorHandler handler) {
        fErrorHandler = handler;
    } // setErrorHandler(XMLErrorHandler)

    /** Returns the registered error handler. */
    public XMLErrorHandler getErrorHandler() {
        return fErrorHandler;
    } // getErrorHandler():XMLErrorHandler

    /**
     * Sets the document handler to receive information about the document.
     * 
     * @param handler The document handler.
     */
    public void setDocumentHandler(XMLDocumentHandler handler) {
        fDocumentHandler = handler;
    } // setDocumentHandler(XMLDocumentHandler)

    /** Returns the registered document handler. */
    public XMLDocumentHandler getDocumentHandler() {
        return fDocumentHandler;
    } // getDocumentHandler():XMLDocumentHandler

    /**
     * Sets the DTD handler.
     * 
     * @param handler The DTD handler.
     */
    public void setDTDHandler(XMLDTDHandler handler) {
        fDTDHandler = handler;
    } // setDTDHandler(XMLDTDHandler)

    /** Returns the registered DTD handler. */
    public XMLDTDHandler getDTDHandler() {
        return fDTDHandler;
    } // getDTDHandler():XMLDTDHandler

    /**
     * Sets the DTD content model handler.
     * 
     * @param handler The DTD content model handler.
     */
    public void setDTDContentModelHandler(XMLDTDContentModelHandler handler) {
        fDTDContentModelHandler = handler;
    } // setDTDContentModelHandler(XMLDTDContentModelHandler)

    /** Returns the registered DTD content model handler. */
    public XMLDTDContentModelHandler getDTDContentModelHandler() {
        return fDTDContentModelHandler;
    } // getDTDContentModelHandler():XMLDTDContentModelHandler 

    /**
     * Parse an XML document.
     * <p>
     * The parser can use this method to instruct this configuration
     * to begin parsing an XML document from any valid input source
     * (a character stream, a byte stream, or a URI).
     * <p>
     * Parsers may not invoke this method while a parse is in progress.
     * Once a parse is complete, the parser may then parse another XML
     * document.
     * <p>
     * This method is synchronous: it will not return until parsing
     * has ended.  If a client application wants to terminate 
     * parsing early, it should throw an exception.
     * <p>
     * <strong>Note:</strong> This method needs to be implemented
     * by the subclass.
     *
     * @param source The input source for the top-level of the
     *               XML document.
     *
     * @exception XNIException Any XNI exception, possibly wrapping 
     *                         another exception.
     * @exception IOException  An IO exception from the parser, possibly
     *                         from a byte stream or character stream
     *                         supplied by the parser.
     */
    public abstract void parse(XMLInputSource inputSource) 
        throws IOException, XNIException;
    
    /**
     * Set the locale to use for messages.
     *
     * @param locale The locale object to use for localization of messages.
     *
     * @exception XNIException Thrown if the parser does not support the
     *                         specified locale.
     */
    public void setLocale(Locale locale) {
        fLocale = locale;
    } // setLocale(Locale)


    /** Returns the locale. */
    public Locale getLocale() {
        return fLocale;
    } // getLocale():Locale

    //
    // Protected methods
    //

    /** 
     * Adds a component to list of configurable components. If the
     * same component is added multiple times, the component is
     * added only the first time. 
     * <p>
     * This method helps manage the components in the configuration.
     * Therefore, all subclasses should call this method to add the
     * components specific to the configuration.
     *
     * @param component The component to add.
     *
     * @see #resetComponents
     */
    protected void addComponent(XMLComponent component) {
        if (!fComponents.contains(component)) {
            fComponents.addElement(component);
            addRecognizedFeatures(component.getRecognizedFeatures());
            addRecognizedProperties(component.getRecognizedProperties());
        }
    } // addComponent(XMLComponent)

    /**
     * Resets all of the registered components. Before the subclassed
     * configuration begins parsing, it should call this method to
     * reset the components.
     *
     * @see #addComponent
     */
    protected void resetComponents() 
        throws XMLConfigurationException {
        int length = fComponents.size();
        for (int i = 0; i < length; i++) {
            XMLComponent component = (XMLComponent)fComponents.elementAt(i);
            component.reset(this);
        }
    } // resetComponents()

    /**
     * This method tries to open the necessary stream for the given
     * XMLInputSource. If the input source already has a character
     * stream (java.io.Reader) or a byte stream (java.io.InputStream)
     * set, this method returns immediately. However, if no character
     * or byte stream is already open, this method attempts to open
     * an input stream using the source's system identifier.
     *
     * @param source The input source to open.
     */
    protected void openInputSourceStream(XMLInputSource source)
        throws IOException {
        if (source.getCharacterStream() != null) {
            return;
        }
        InputStream stream = source.getByteStream();
        if (stream == null) {
            String systemId = source.getSystemId();
            try {
                URL url = new URL(systemId);
                stream = url.openStream();
            }
            catch (MalformedURLException e) {
                stream = new FileInputStream(systemId);
            }
            source.setByteStream(stream);
        }
    } // openInputSourceStream(XMLInputSource)

} // class AbstractConfiguration
