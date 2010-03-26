/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.lcf.crawler.connectors.meridio.meridiowrapper;

import java.net.*;
import java.rmi.RemoteException;
import java.io.*;
import java.util.HashMap;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.xml.namespace.QName;
import javax.xml.parsers.*;
import javax.xml.rpc.ServiceException;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;

import org.w3c.dom.*;
import org.w3c.dom.ls.*;
import org.xml.sax.*;
import org.apache.xerces.dom.*;
import org.apache.axis.types.*;
import org.apache.axis.attachments.AttachmentPart;
import org.apache.axis.client.Call;
import org.apache.axis.holders.*;
import org.apache.axis.message.*;
import org.apache.log4j.Logger;

import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolFactory;
import org.apache.axis.EngineConfiguration;
import org.apache.axis.configuration.FileProvider;

import org.exolab.castor.xml.MarshalException;
import org.exolab.castor.xml.ValidationException;

import org.apache.lcf.crawler.connectors.meridio.DMDataSet.*;
import com.meridio.www.MeridioDMWS.*;
import com.meridio.www.MeridioDMWS.holders.*;

import org.apache.lcf.crawler.connectors.meridio.RMDataSet.*;
import com.meridio.www.MeridioRMWS.*;
import com.meridio.www.MeridioRMWS.ApplyChangesWTResponseApplyChangesWTResult;

public class MeridioTestWrapper extends MeridioWrapper
{

        protected URL meridioDmwsUrl;
        protected URL meridioRmwsUrl;
        protected String userName;
        protected String password;

        /** The Meridio Wrapper constructor that calls the Meridio login method
        * 
        *@param meridioDmwsUrl          the URL to the Meridio Document Management Web Service
        *@param meridioRmwsUrl          the URL to the Meridio Records Management Web Service
        *@param userName                        the username of the user to log in as, must include the Windows, e.g. domain\\user
        *@param password                        the password of the user who is logging in
        *@param clientWorkstation       an identifier for the client workstation, could be the IP address, for auditing purposes
        *@param httpsProtocol           the https protocol object to use for https communication
        *@param engineConfigurationFile the engine configuration object to use to communicate with the web services
        *
        *@throws RemoteException        if an error is encountered logging into Meridio
        */
        public MeridioTestWrapper
        (
                        String meridioDmwsUrlString,
                        String meridioRmwsUrlString,
                        String userName,
                        String password
        )
        throws RemoteException, MalformedURLException, UnknownHostException
        {
                super(null,new URL(meridioDmwsUrlString),new URL(meridioRmwsUrlString),null,null,null,null,null,null,null,userName,password,
                      InetAddress.getLocalHost().getHostName(),new ProtocolFactory(),LCF.getProperty("org.apache.lcf.meridio.wsddpath"));
                this.meridioDmwsUrl = new URL(meridioDmwsUrlString);
                this.meridioRmwsUrl = new URL(meridioRmwsUrlString);
                this.userName = userName;
                this.password = password;
        }

        public URL getMeridioDmwsUrl ()
        {
                return meridioDmwsUrl;
        }
        
        public URL getMeridioRmwsUrl ()
        {
                return meridioRmwsUrl;
        }

        public int findCategory(String categoryName)
                throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                CATEGORIES [] meridioCategories = getCategories().getCATEGORIES();
                // Create a map from title to category ID
                HashMap categoryMap = new HashMap();
                int i = 0;
                while (i < meridioCategories.length)
                {
                        String title = meridioCategories[i].getPROP_title();
                        long categoryID = meridioCategories[i].getPROP_categoryId();
                        categoryMap.put(title,new Long(categoryID));
                        i++;
                }
                Long value = (Long)categoryMap.get(categoryName);
                if (value == null)
                        return 0;
                return (int)value.longValue();
        }
                
        public DMDataSet addObject
        (
                DMDataSet additionInfo,
                String    uploadInfo,
                boolean   majorVersion
        )       
        throws RemoteException, MeridioDataSetException
        {
                
                if (oLog != null) oLog.debug("Meridio: Entered addObject method.");
                
                try
                {
                        boolean loginTried = false;
                        while (true)
                        {
                                loginUnified();
                                try
                                {
                                        AddObject2WTResponseAddObject2WTResult addObjectResponse = null;
                        
                                        AddObject2WTAdditionInfo addObjectInfo = new AddObject2WTAdditionInfo();
                                        addObjectInfo.set_any(getSOAPMessage(additionInfo));
                
                                        addObjectResponse = meridioDMWebService_.addObject(loginToken_, addObjectInfo, 
                                                        uploadInfo, majorVersion);
                        
                                        DMDataSet ds = getDMDataSet(addObjectResponse.get_any());
                                                                        
                                        return ds;
                                }
                                catch (RemoteException e)
                                {
                                        if (loginTried == false)
                                        {
                                                loginToken_ = null;
                                                loginTried = true;
                                                continue;
                                        }
                                        throw e;
                                }
                        }

                }
                finally
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting addObject method.");
                }
        }
        
        
        
        public void deleteObject
        (
                DmObjectType objectType,
                long objectId
        )       
        throws RemoteException, MeridioDataSetException
        {
                
                if (oLog != null) oLog.debug("Meridio: Entered deleteObject method.");
                
                try
                {
                        boolean loginTried = false;
                        while (true)
                        {
                                loginUnified();
                                try
                                {
                                        meridioDMWebService_.deleteObject(loginToken_, objectType, new UnsignedInt(objectId));
                                        return;
                                }
                                catch (RemoteException e)
                                {
                                        if (loginTried == false)
                                        {
                                                loginToken_ = null;
                                                loginTried = true;
                                                continue;
                                        }
                                        throw e;
                                }
                        }

                }
                finally
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting deleteObject method.");
                }
        }
        
        
        
        public DMDataSet addDocumentDIME
        (
                DMDataSet additionInfo,
                String    filePath,
                boolean   majorVersion
        )       
        throws RemoteException, MeridioDataSetException
        {
                
                if (oLog != null) oLog.debug("Meridio: Entered addDocumentDIME method.");
                
                try
                {
                        boolean loginTried = false;
                        while (true)
                        {
                                loginUnified();
                                try
                                {
                                        meridioDMWebService_._setProperty(
                                                        Call.ATTACHMENT_ENCAPSULATION_FORMAT,
                                                        Call.ATTACHMENT_ENCAPSULATION_FORMAT_DIME);
                                                                
                                        DataHandler dh = new DataHandler(new FileDataSource(new File(filePath)));               
                                        meridioDMWebService_.addAttachment(dh);                 
                        
                                        AddObject2WTResponseAddObject2WTResult addObjectResponse = null;
                        
                                        AddObject2WTAdditionInfo addObjectInfo = new AddObject2WTAdditionInfo();
                                        addObjectInfo.set_any(getSOAPMessage(additionInfo));
                        
                                        addObjectResponse = meridioDMWebService_.addObject(loginToken_, addObjectInfo, 
                                                        null, majorVersion);
                        
                                        DMDataSet ds = getDMDataSet(addObjectResponse.get_any());
                                                                        
                                        return ds;
                                }
                                catch (RemoteException e)
                                {
                                        if (loginTried == false)
                                        {
                                                loginToken_ = null;
                                                loginTried = true;
                                                continue;
                                        }
                                        throw e;
                                }
                        }

                }
                finally
                {
                        if (null != meridioDMWebService_)
                        {
                                meridioDMWebService_.clearAttachments();
                        }
                        if (oLog != null) oLog.debug("Meridio: Exiting addDocumentDIME method.");
                }
        }
        
        
        
        public int addDocumentToFolder
    (                   
        String folderPath,
        String filePath,
        String fileName,
        String fileTitle
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                return addDocumentOrRecordToFolder(folderPath, filePath, fileName, fileTitle, null, null, false);
        }
        
        public int addDocumentToFolder
    (                   
        String folderPath,
        String filePath,
        String fileName,
        String fileTitle,
        DOCUMENTS documentProperties,
        DOCUMENT_CUSTOMPROPS [] documentCustomProperties        
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                return addDocumentOrRecordToFolder(folderPath, filePath, fileName, fileTitle, documentProperties, documentCustomProperties, false);
        }
        
        
        
        public int addRecordToFolder
    (                   
        String folderPath,
        String filePath,
        String fileName,
        String fileTitle        
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                return addDocumentOrRecordToFolder(folderPath, filePath, fileName, fileTitle, null, null, true);
        }
        
        
        
        public int addRecordToFolder
    (                   
        String folderPath,
        String filePath,
        String fileName,
        String fileTitle,
        DOCUMENTS documentProperties,
        DOCUMENT_CUSTOMPROPS [] documentCustomProperties        
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                return addDocumentOrRecordToFolder(folderPath, filePath, fileName, fileTitle, documentProperties, documentCustomProperties, true);
        }
        
        
                
        private int addDocumentOrRecordToFolder
    (                   
        String folderPath,
        String filePath,
        String fileName,
        String fileTitle,
        DOCUMENTS documentProperties,
        DOCUMENT_CUSTOMPROPS [] documentCustomProperties,
        boolean isRecord
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                
                if (oLog != null) oLog.debug("Meridio: Entered addDocumentToFolder method.");
                
                try
                {               
                        /*====================================================================
                        * Find the identifier of the folder into which to add the new
                        * document
                        *===================================================================*/
                        int folderId = findClassOrFolder(folderPath);
                        if (folderId < 1)
                        {
                                throw new MeridioWrapperException("Can't add the document - Failed to find folder <" + folderPath + "> when trying to add document");                   
                        }
                        if (oLog != null) oLog.debug("Meridio: Folder <" + folderPath + "> has id <" + folderId + ">");
                        
                        /*====================================================================
                        * Find the latest open part in the folder
                        *===================================================================*/
                        RMDataSet folderDS = getFolder(folderId, false, false, true, false);
                        if (null == folderDS)
                        {
                                throw new MeridioWrapperException("Can't add the document - could not get details for folder <" + folderPath + "> - are you sure it's a folder and not a class?");                      
                        }
                        Rm2vPart [] parts = folderDS.getRm2vPart();
                        int openPartId = 0;
                        int partName = 0;
                        for (int i = 0; i < parts.length; i++)
                        {
                                if (parts[i].getOpenStatus() == 1) // is it open?
                                {
                                        openPartId = parts[i].getId();   // take the maximum open part
                                        partName = parts[i].getPartNumber();
                                }
                        }
                        if (openPartId == 0)
                        {
                                throw new MeridioWrapperException("Can't add the document - there are no open parts in folder <" + folderPath + ">");                   
                        }
                        if (oLog != null) oLog.debug("Meridio: Found open part - Part Name is <" + partName + "> Part ID is <" + openPartId + ">");
                                                        
                        /*====================================================================
                        * Get the ACLs of the containing folder and add these to the document
                        * being added to the folder (otherwise the document gets no ACLs)
                        *===================================================================*/
                        DMDataSet folderData = getContainerData(folderId, true, true, 
                                        false, false, false, false, false, false, false);
                        if (null == folderData)
                        {
                                throw new MeridioWrapperException("Can't add the document as we failed to retrieve the folder's details");
                        }
                        DMDataSet documentToAdd = new DMDataSet ();
                        CONTAINERS folderProperties = folderData.getCONTAINERS()[0];
                        
                        if (folderProperties.getPROP_W_inherit())
                        {
                                for (int i = 0; i < folderData.getACCESSCONTROLCount(); i++)
                                {
                                        documentToAdd.addACCESSCONTROL(folderData.getACCESSCONTROL(i));
                                        documentToAdd.getACCESSCONTROL(i).deleteObjectId();
                                        documentToAdd.getACCESSCONTROL(i).setObjectType(new Short("1").shortValue());  //DOCUMENT       
                                }
                        }
                                                                
                        /*====================================================================
                        * Set the document and version properties
                        *===================================================================*/
                        DOCUMENTS d = documentProperties;
                        if (null == d)
                        {
                                d = new DOCUMENTS ();
                        }               
                        d.setPROP_W_title(fileTitle);                   
                        d.setPROP_originalFileName(fileName);
                        d.setPROP_W_defaultLockFileName(fileName);
                        d.setPROP_W_indexingType(new Short("1").shortValue());  
                        documentToAdd.addDOCUMENTS(d);  
                        
                        if (null != documentCustomProperties)
                        {
                                for (int i = 0; i < documentCustomProperties.length; i++)
                                {
                                        documentToAdd.addDOCUMENT_CUSTOMPROPS(documentCustomProperties[i]);
                                }
                        }
                                        
                        VERSIONS v = new VERSIONS ();           
                        v.setNewVersionFileName(fileName);
                        v.setPROP_W_keepOnline(true);
                        v.setPROP_W_reclaimPending(false);
                        documentToAdd.addVERSIONS(v);           
                                        
                        /*====================================================================
                        * Add the document to Meridio [to the document pool]
                        *===================================================================*/
                        DMDataSet returnDS = addDocumentDIME(documentToAdd, filePath + "/" + fileName, true);           
                        if (oLog != null) oLog.debug("Meridio: New Doc Id is <" + returnDS.getDOCUMENTS()[0].getPROP_documentId() + ">");                                               
                        
                        /*====================================================================
                        * Use the document ID returned above to create a reference to the
                        * document in the correct part within the folder
                        *===================================================================*/                                          
                        RMDataSet container = new RMDataSet ();
                        if (isRecord)
                        {                                       
                                Rm2Part_Record cdref = new Rm2Part_Record ();
                                cdref.setParentObjectId(openPartId);  
                                cdref.setRecordId(new Long(returnDS.getDOCUMENTS()[0].getPROP_documentId()).intValue());
                                container.addRm2Part_Record(cdref);
                        }
                        else
                        {       
                                Rm2Part_Document cdref = new Rm2Part_Document ();
                                cdref.setParentObjectId(openPartId);  
                                cdref.setDocumentId(new Long(returnDS.getDOCUMENTS()[0].getPROP_documentId()).intValue());
                                container.addRm2Part_Document(cdref);
                        }
                                        
                        applyChanges(container);                        
                        
                        return new Long(returnDS.getDOCUMENTS()[0].getPROP_documentId()).intValue();
                }
                finally
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting addDocumentToFolder method.");
                }                                               
        }
        
        
        
        public void lockDocument
    (   
        int docId,
        String checkOutComment
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                if (oLog != null) oLog.debug("Meridio: Entering lockDocument method.");
                
                try
                {
                        LOCKS lock = new LOCKS ();
                        lock.setDocId(docId);
                        lock.setPROP_W_comment(checkOutComment);
                        
                        Writer writer = new StringWriter();             
                        lock.marshal(writer);
                        writer.close();
                        
                        addDMObject(writer.toString());
                        
                }
                catch (MarshalException marshalException)
                {
                        throw new MeridioDataSetException(
                                        "Castor error in marshalling the XML from the Meridio Dataset", 
                                        marshalException);
                }
                catch (ValidationException validationException)
                {
                        throw new MeridioDataSetException(
                                        "Castor error in validating the XML from the Meridio Dataset", 
                                        validationException);
                }
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }
                finally
                {                       
                        if (oLog != null) oLog.debug("Meridio: Exiting lockDocument method.");
                }               
        }
        
        
        
        public void unlockDocument
    (   
        int docId
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                if (oLog != null) oLog.debug("Meridio: Entering unlockDocument method.");
                
                try
                {
                        LOCKS lock = new LOCKS ();
                        lock.setDocId(docId);           
                        
                        Writer writer = new StringWriter();             
                        lock.marshal(writer);
                        writer.close();
                        
                        deleteDMObject(writer.toString());
                        
                }
                catch (MarshalException marshalException)
                {
                        throw new MeridioDataSetException(
                                        "Castor error in marshalling the XML from the Meridio Dataset", 
                                        marshalException);
                }
                catch (ValidationException validationException)
                {
                        throw new MeridioDataSetException(
                                        "Castor error in validating the XML from the Meridio Dataset", 
                                        validationException);
                }
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }
                finally
                {                       
                        if (oLog != null) oLog.debug("Meridio: Exiting unlockDocument method.");
                }               
        }
        
        
        public void addDocumentVersion
    (   
        int docId,
        String filePath,
        String fileName,
        String versionComment
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                if (oLog != null) oLog.debug("Meridio: Entering addDocumentVersion method.");
                
                try
                {
                        DMDataSet documentToAdd = new DMDataSet ();             
                        VERSIONS v = new VERSIONS ();
                        v.setDocId(docId);
                        v.setNewVersionFileName(fileName);
                        v.setPROP_W_keepOnline(true);
                        v.setPROP_W_reclaimPending(false);
                        v.setPROP_W_comment(versionComment);
                        
                        documentToAdd.addVERSIONS(v);           
                                        
                        /*====================================================================
                        * Add the document version to Meridio 
                        *===================================================================*/
                        addDocumentDIME(documentToAdd, filePath + "/" + fileName, true);
                                                
                }
                finally
                {                       
                        if (oLog != null) oLog.debug("Meridio: Exiting addDocumentVersion method.");
                }               
        }
        
        
                
        public boolean deleteRecord
    (                   
        int recordId
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {               
                if (oLog != null) oLog.debug("Meridio: Entering deleteRecord - Record Id is <" + recordId + ">");
                
                try
                {                               
                        RMDataSet rmds = getRecord(recordId, false, false, false);
                        if (null == rmds)
                        {
                                if (oLog != null) oLog.debug("Meridio: Record <" + recordId + "> does not exist, so cannot delete it");
                                return false;           
                        }

                        boolean loginTried = false;
                        while (true)
                        {
                                loginUnified();
                                try
                                {
                                        /*====================================================================
                                        * Marshall the record's details so we can get at the XML
                                        *===================================================================*/
                                        Writer writer = new StringWriter();             
                                        Rm2vRecord myRecord = rmds.getRm2vRecord()[0];
                                        myRecord.marshal(writer);
                                        writer.close();                                                                                                         
                                        StringReader stringReader = new StringReader(writer.toString());
                                        InputSource recordToDelete   = new InputSource(stringReader);
                                        
                                        /*====================================================================
                                        * Create a new DOM object and start building up the SOAP body
                                        *===================================================================*/
                                        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                                        documentBuilderFactory.setValidating(false);            
                                        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();                                  
                                        Document newDoc = documentBuilder.newDocument();
                                                
                                        /*====================================================================
                                        * Create the top-level element describing what Web Service method
                                        * we are calling: to delete a record in Meridio we call ApplyChanges
                                        *===================================================================*/
                                        Element applyChanges = newDoc.createElement("ApplyChanges");
                                        applyChanges.setAttribute("xmlns", "http://www.meridio.com/MeridioRMWS");
                                        newDoc.appendChild(applyChanges);
                                
                                        /*====================================================================
                                        * Add the "ds" [DataSet] element to the XML
                                        *===================================================================*/
                                        Element ds = newDoc.createElement("ds");                        
                                        applyChanges.appendChild(ds);
                        
                                        /*====================================================================
                                        * Because we are sending a deletion, we must build up the XML for
                                        * a Microsoft DataSet Diffgram. 
                                        *===================================================================*/
                                        Element diffGram = newDoc.createElement("diffgr:diffgram");
                                        diffGram.setAttribute("xmlns:diffgr", "urn:schemas-microsoft-com:xml-diffgram-v1");
                                        diffGram.setAttribute("xmlns:msdata", "urn:schemas-microsoft-com:xml-msdata");          
                                        ds.appendChild(diffGram);                       
                        
                                        Element emptyDataSet = newDoc.createElement("RMDataSet");
                                        emptyDataSet.setAttribute("xmlns", "http://www.meridio.com/RMDataSet.xsd");             
                                        diffGram.appendChild(emptyDataSet);
                                                        
                                        Element diffGramBefore = newDoc.createElement("diffgr:before");
                                        diffGramBefore.setAttribute("xmlns:diffgr", "urn:schemas-microsoft-com:xml-diffgram-v1");
                                        diffGram.appendChild(diffGramBefore);
                        
                                        /*====================================================================
                                        * Add the Rm2vRecord XML to the diffgr:before
                                        *===================================================================*/
                                        Document document = documentBuilder.parse(recordToDelete);              
                                        Element element = document.getDocumentElement();                
                                        element.setAttribute("diffgr:id", "rm2vRecord1");
                                        element.setAttribute("msdata:rowOrder", "0");                           
                                        Node n = newDoc.importNode(element, true);              
                                        diffGramBefore.appendChild(n);                                                          
                        
                                        /*====================================================================
                                        * Create the SOAP Envelope
                                        *===================================================================*/
                                        org.apache.axis.message.SOAPEnvelope env = new org.apache.axis.message.SOAPEnvelope();          
                        
                                        /*====================================================================
                                        * Populate the SOAP Header
                                        *===================================================================*/                                  
                                        SOAPHeader header = env.getHeader();    
                                        Name headerElementName = env.createName("MeridioCredentialHeader", "", "http://www.meridio.com/MeridioRMWS");           
                                        SOAPHeaderElement headerElement = header.addHeaderElement(headerElementName);
                                        headerElement.setMustUnderstand(false);
                                        headerElement.addNamespaceDeclaration("soap", "http://schemas.xmlsoap.org/soap/envelope");                      
                                        SOAPElement token = headerElement.addChildElement("Token");
                                        token.addTextNode(loginToken_);                 
                                        
                                        /*====================================================================
                                        * Populate the SOAP body with the Diffgram XML we built up earlier
                                        *===================================================================*/  
                                        SOAPBody body = env.getBody();
                                        body.addDocument(newDoc);
                                        
                                        /*====================================================================
                                        * Invoke the web service
                                        *===================================================================*/  
                                        callRMApplyChanges(env);
                        
                                        return true;
                                }
                                catch (RemoteException e)
                                {
                                        if (loginTried == false)
                                        {
                                                loginToken_ = null;
                                                loginTried = true;
                                                continue;
                                        }
                                        throw e;
                                }
                        }
                }
                catch (MarshalException marshalException)
                {
                        throw new MeridioDataSetException(
                                        "Castor error in marshalling the XML from the Meridio Dataset", 
                                        marshalException);
                }
                catch (ValidationException validationException)
                {
                        throw new MeridioDataSetException(
                                        "Castor error in validating the XML from the Meridio Dataset", 
                                        validationException);
                }
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }
                catch (SAXException SaxException)
                {
                        throw new MeridioDataSetException(
                                        "XML Error in marshalling the Meridio Dataset", 
                                        SaxException);
                }
                catch (ParserConfigurationException parserConfigurationException)
                {
                        throw new MeridioDataSetException(
                                        "XML Error in parsing the Meridio Dataset", 
                                        parserConfigurationException);
                }
                catch (SOAPException exSOAPException)
                {
                        throw new MeridioWrapperException(
                                        "Encountered a SOAP Exception constructing the SOAP Envelope", 
                                        exSOAPException);
                }               
                finally         
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting deleteRecord - Record Id is <" + recordId + ">");
                }                                                                       
        }
        
        
        
        public void deleteDocument
    (                   
        int docId
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {               
                if (oLog != null) oLog.debug("Meridio: Entering deleteDocument - Document Id is <" + docId + ">");
                
                try
                {                               
                        markDocumentForDelete(docId);
                        removeDocReferencesFromFilePlan (docId);
                }
                finally         
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting deleteDocument - Document Id is <" + docId + ">");
                }                                                                       
        }
        
        
        
        public void markDocumentForDelete
    (                   
        int docId
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {               
                if (oLog != null) oLog.debug("Meridio: Entering markDocumentForDelete - Document Id is <" + docId + ">");
                
                try
                {                               
                        deleteObject(DmObjectType.DOCUMENT, docId);                     
                }
                finally         
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting markDocumentForDelete - Document Id is <" + docId + ">");
                }                                                                       
        }
        
        
        
        public boolean removeDocReferencesFromFilePlan
    (                   
        int docId
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {               
                if (oLog != null) oLog.debug("Meridio: Entering removeDocReferencesFromFilePlan - Document Id is <" + docId + ">");
                
                try
                {                               
                        RMDataSet rmds = getDocumentPartList(docId);
                        if (null == rmds)
                        {
                                if (oLog != null) oLog.debug("Meridio: Document <" + docId + "> does have any references in any part, so cannot remove the references");
                                return false;           
                        }
                        
                        if (rmds.getRm2vPart().length == 0)
                        {
                                if (oLog != null) oLog.debug("Meridio: Document <" + docId + "> does have any references in any part, so cannot remove the references");
                                return false;
                        }                       

                        boolean loginTried = false;
                        while (true)
                        {
                                loginUnified();
                                try
                                {

                                        /*====================================================================
                                        * Remove the document references from the various folders
                                        * 
                                        * Create a new DOM object and start building up the SOAP body
                                        *===================================================================*/
                                        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                                        documentBuilderFactory.setValidating(false);            
                                        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();                                  
                                        Document newDoc = documentBuilder.newDocument();
                                                
                                        /*====================================================================
                                        * Create the top-level element describing what Web Service method
                                        * we are calling: to delete a record in Meridio we call ApplyChanges
                                        *===================================================================*/
                                        Element applyChanges = newDoc.createElement("ApplyChanges");
                                        applyChanges.setAttribute("xmlns", "http://www.meridio.com/MeridioRMWS");
                                        newDoc.appendChild(applyChanges);
                                
                                        /*====================================================================
                                        * Add the "ds" [DataSet] element to the XML
                                        *===================================================================*/
                                        Element ds = newDoc.createElement("ds");                        
                                        applyChanges.appendChild(ds);
                        
                                        /*====================================================================
                                        * Because we are sending a deletion, we must build up the XML for
                                        * a Microsoft DataSet Diffgram. 
                                        *===================================================================*/
                                        Element diffGram = newDoc.createElement("diffgr:diffgram");
                                        diffGram.setAttribute("xmlns:diffgr", "urn:schemas-microsoft-com:xml-diffgram-v1");
                                        diffGram.setAttribute("xmlns:msdata", "urn:schemas-microsoft-com:xml-msdata");          
                                        ds.appendChild(diffGram);                       
                        
                                        Element emptyDataSet = newDoc.createElement("RMDataSet");
                                        emptyDataSet.setAttribute("xmlns", "http://www.meridio.com/RMDataSet.xsd");             
                                        diffGram.appendChild(emptyDataSet);
                                                        
                                        Element diffGramBefore = newDoc.createElement("diffgr:before");
                                        diffGramBefore.setAttribute("xmlns:diffgr", "urn:schemas-microsoft-com:xml-diffgram-v1");
                                        diffGram.appendChild(diffGramBefore);                           
                        
                                        /*====================================================================
                                        * Add the rm2Part_Document XML to the diffgr:before
                                        * 
                                        * There may be multiple entries if the document exists in multiple
                                        * places
                                        *===================================================================*/                  
                                        for (int i = 0; i < rmds.getRm2vPart().length; i++)
                                        {
                                                Element docToDelete = newDoc.createElement("rm2Part_Document");
                                                docToDelete.setAttribute("diffgr:id", "rm2Part_Document" + (i+1));
                                                docToDelete.setAttribute("msdata:rowOrder", new Integer(i).toString());
                                                docToDelete.setAttribute("xmlns", "http://www.meridio.com/RMDataSet.xsd");
                                
                                                Element parentObjectId = newDoc.createElement("parentObjectId");                                                
                                                Node parentObjectIdNode = newDoc.createTextNode(new Integer(rmds.getRm2vPart()[i].getId()).toString());
                                                parentObjectId.appendChild(parentObjectIdNode);
                                                docToDelete.appendChild(parentObjectId);
                                                                
                                                if (oLog != null) oLog.debug("Meridio: Removing document from Part <" + rmds.getRm2vPart()[i].getId() + ">");
                                
                                                Element documentId = newDoc.createElement("documentId");
                                                Node docIdNode = newDoc.createTextNode(new Integer(docId).toString());
                                                documentId.appendChild(docIdNode);
                                
                                                docToDelete.appendChild(documentId);                            
                                                diffGramBefore.appendChild(docToDelete);
                                        }
                                                                                                                                
                                        /*====================================================================
                                        * Create the SOAP Envelope
                                        *===================================================================*/
                                        org.apache.axis.message.SOAPEnvelope env = new org.apache.axis.message.SOAPEnvelope();          
                        
                                        /*====================================================================
                                        * Populate the SOAP Header
                                        *===================================================================*/                                  
                                        SOAPHeader header = env.getHeader();    
                                        Name headerElementName = env.createName("MeridioCredentialHeader", "", "http://www.meridio.com/MeridioRMWS");           
                                        SOAPHeaderElement headerElement = header.addHeaderElement(headerElementName);
                                        headerElement.setMustUnderstand(false);
                                        headerElement.addNamespaceDeclaration("soap", "http://schemas.xmlsoap.org/soap/envelope");                      
                                        SOAPElement token = headerElement.addChildElement("Token");
                                        token.addTextNode(loginToken_);                 
                                        
                                        /*====================================================================
                                        * Populate the SOAP body with the Diffgram XML we built up earlier
                                        *===================================================================*/  
                                        SOAPBody body = env.getBody();
                                        body.addDocument(newDoc);
                                        
                                        /*====================================================================
                                        * Invoke the web service
                                        *===================================================================*/  
                                        callRMApplyChanges(env);
                        
                                        return true;
                                }
                                catch (RemoteException e)
                                {
                                        if (loginTried == false)
                                        {
                                                loginToken_ = null;
                                                loginTried = true;
                                                continue;
                                        }
                                        throw e;
                                }
                        }
                }               
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }               
                catch (ParserConfigurationException parserConfigurationException)
                {
                        throw new MeridioDataSetException(
                                        "XML Error in parsing the Meridio Dataset", 
                                        parserConfigurationException);
                }
                catch (SOAPException exSOAPException)
                {
                        throw new MeridioWrapperException(
                                        "Encountered a SOAP Exception constructing the SOAP Envelope", 
                                        exSOAPException);
                }               
                finally         
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting removeDocReferencesFromFilePlan - Document Id is <" + docId + ">");
                }                                                                       
        }
        
        
        
        public void updateDocumentStandardProperties
        (
                DOCUMENTS beforeDocument,
                DOCUMENTS afterDocument
        )       
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                
                if (oLog != null) oLog.debug("Meridio: Entered updateDocumentStandardProperties method.");
                
                try
                {               
                        Writer beforeWriter = new StringWriter();               
                        beforeDocument.marshal(beforeWriter);
                        beforeWriter.close();
                        
                        Writer afterWriter = new StringWriter();                
                        afterDocument.marshal(afterWriter);
                        afterWriter.close();
                        
                        updateDMObject(beforeWriter.toString(), afterWriter.toString());
                        
                }
                catch (MarshalException marshalException)
                {
                        throw new MeridioDataSetException(
                                        "Castor error in marshalling the XML from the Meridio Dataset", 
                                        marshalException);
                }
                catch (ValidationException validationException)
                {
                        throw new MeridioDataSetException(
                                        "Castor error in validating the XML from the Meridio Dataset", 
                                        validationException);
                }
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }
                finally
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting updateDocumentStandardProperties method.");
                }
        }
        
        
        
        public void updateRecordStandardProperties
        (
                Rm2vRecord beforeRecord,
                Rm2vRecord afterRecord
        )       
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                
                if (oLog != null) oLog.debug("Meridio: Entered updateRecordStandardProperties method.");
                
                try
                {               
                        Writer beforeWriter = new StringWriter();               
                        beforeRecord.marshal(beforeWriter);
                        beforeWriter.close();
                        
                        Writer afterWriter = new StringWriter();                
                        afterRecord.marshal(afterWriter);
                        afterWriter.close();
                        
                        updateRMObject(beforeWriter.toString(), afterWriter.toString());
                        
                }
                catch (MarshalException marshalException)
                {
                        throw new MeridioDataSetException(
                                        "Castor error in marshalling the XML from the Meridio Dataset", 
                                        marshalException);
                }
                catch (ValidationException validationException)
                {
                        throw new MeridioDataSetException(
                                        "Castor error in validating the XML from the Meridio Dataset", 
                                        validationException);
                }
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }
                finally
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting updateRecordStandardProperties method.");
                }
        }
        
        
        
        
        public void addAclToDocumentOrRecord
        (
                ACCESSCONTROL  acl
        )       
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                
                if (oLog != null) oLog.debug("Meridio: Entered addAclToDocumentOrRecord method.");
                
                try
                {               
                        Writer aclWriter = new StringWriter();          
                        acl.marshal(aclWriter);
                        aclWriter.close();                                      
                        
                        addDMObject(aclWriter.toString());
                        
                }
                catch (MarshalException marshalException)
                {
                        throw new MeridioDataSetException(
                                        "Castor error in marshalling the XML from the Meridio Dataset", 
                                        marshalException);
                }
                catch (ValidationException validationException)
                {
                        throw new MeridioDataSetException(
                                        "Castor error in validating the XML from the Meridio Dataset", 
                                        validationException);
                }
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }
                finally
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting addAclToDocumentOrRecord method.");
                }
        }       
        
        
        
        public void removeAclFromDocumentOrRecord
        (
                ACCESSCONTROL  acl
        )       
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                
                if (oLog != null) oLog.debug("Meridio: Entered removeAclFromDocumentOrRecord method.");
                
                try
                {               
                        Writer aclWriter = new StringWriter();          
                        acl.marshal(aclWriter);
                        aclWriter.close();                                      
                        
                        deleteDMObject(aclWriter.toString());
                        
                }
                catch (MarshalException marshalException)
                {
                        throw new MeridioDataSetException(
                                        "Castor error in marshalling the XML from the Meridio Dataset", 
                                        marshalException);
                }
                catch (ValidationException validationException)
                {
                        throw new MeridioDataSetException(
                                        "Castor error in validating the XML from the Meridio Dataset", 
                                        validationException);
                }
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }
                finally
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting removeAclFromDocumentOrRecord method.");
                }
        }
        
        
        protected void addDMObject
    (                   
        String objectXML        
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {               
                if (oLog != null) oLog.debug("Meridio: Entering addDMObject");
                
                try
                {
                        boolean loginTried = false;
                        while (true)
                        {
                                loginUnified();
                                try
                                {
                                        /*====================================================================
                                        * Remove the document references from the various folders
                                        * 
                                        * Create a new DOM object and start building up the SOAP body
                                        *===================================================================*/
                                        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                                        documentBuilderFactory.setValidating(false);            
                                        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();                                  
                                        Document newDoc = documentBuilder.newDocument();
                                                
                                        /*====================================================================
                                        * Create the top-level element describing what Web Service method
                                        * we are calling: to delete a record in Meridio we call ApplyChanges
                                        *===================================================================*/
                                        Element applyChanges = newDoc.createElement("ApplyChanges");
                                        applyChanges.setAttribute("xmlns", "http://www.meridio.com/MeridioDMWS");
                                        newDoc.appendChild(applyChanges);
                                
                                        /*====================================================================
                                        * Add the "ds" [DataSet] element to the XML
                                        *===================================================================*/
                                        Element ds = newDoc.createElement("changes");                   
                                        applyChanges.appendChild(ds);
                        
                                        /*====================================================================
                                        * Because we are sending a deletion, we must build up the XML for
                                        * a Microsoft DataSet Diffgram. 
                                        *===================================================================*/
                                        Element diffGram = newDoc.createElement("diffgr:diffgram");
                                        diffGram.setAttribute("xmlns:diffgr", "urn:schemas-microsoft-com:xml-diffgram-v1");
                                        diffGram.setAttribute("xmlns:msdata", "urn:schemas-microsoft-com:xml-msdata");          
                                        ds.appendChild(diffGram);                       
                        
                                        Element afterDataSet = newDoc.createElement("DMDataSet");
                                        afterDataSet.setAttribute("xmlns", "http://www.meridio.com/DMDataSet.xsd");     
                                        diffGram.appendChild(afterDataSet);
                                                                
                                        StringReader objectXMLStringReader = new StringReader(objectXML);
                                        InputSource objectXMLInputSource   = new InputSource(objectXMLStringReader);                                            
                                        Document objectXMLDocument = documentBuilderFactory.newDocumentBuilder().parse(objectXMLInputSource);
                                        Element objectXMLElement = objectXMLDocument.getDocumentElement();                      
                                        objectXMLElement.setAttribute("diffgr:id", "OBJECT1");
                                        objectXMLElement.setAttribute("msdata:rowOrder", "0");
                                        objectXMLElement.setAttribute("diffgr:hasChanges", "inserted");                                         
                                        Node afterXMLNode = newDoc.importNode(objectXMLElement, true);  
                                        afterDataSet.appendChild(afterXMLNode);                 
                        
                                        /*====================================================================
                                        * Create the SOAP Envelope
                                        *===================================================================*/
                                        org.apache.axis.message.SOAPEnvelope env = new org.apache.axis.message.SOAPEnvelope();          
                        
                                        /*====================================================================
                                        * Populate the SOAP Header
                                        *===================================================================*/                                  
                                        SOAPHeader header = env.getHeader();    
                                        Name headerElementName = env.createName("MeridioCredentialHeader", "", "http://www.meridio.com/MeridioDMWS");           
                                        SOAPHeaderElement headerElement = header.addHeaderElement(headerElementName);
                                        headerElement.setMustUnderstand(false);
                                        headerElement.addNamespaceDeclaration("soap", "http://schemas.xmlsoap.org/soap/envelope");                      
                                        SOAPElement token = headerElement.addChildElement("token");
                                        token.addTextNode(loginToken_);                 
                                        
                                        /*====================================================================
                                        * Populate the SOAP body with the Diffgram XML we built up earlier
                                        *===================================================================*/  
                                        SOAPBody body = env.getBody();
                                        body.addDocument(newDoc);
                                        
                                        /*====================================================================
                                        * Invoke the web service
                                        *===================================================================*/  
                                        callDMApplyChanges(env);
                                        return;
                                }
                                catch (RemoteException e)
                                {
                                        if (loginTried == false)
                                        {
                                                loginToken_ = null;
                                                loginTried = true;
                                                continue;
                                        }
                                        throw e;
                                }
                        }
                }               
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }               
                catch (SAXException SaxException)
                {
                        throw new MeridioDataSetException(
                                        "XML Error in marshalling the 'before' of 'after' XML", 
                                        SaxException);
                }
                catch (ParserConfigurationException parserConfigurationException)
                {
                        throw new MeridioDataSetException(
                                        "XML Error in parsing the Meridio Dataset", 
                                        parserConfigurationException);
                }
                catch (SOAPException exSOAPException)
                {
                        throw new MeridioWrapperException(
                                        "Encountered a SOAP Exception constructing the SOAP Envelope", 
                                        exSOAPException);
                }               
                finally         
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting addDMObject");
                }                                                                       
        }
        
        /** Lookup a document with a specified name in a specified
         *  folder */
        public Long findDocumentInFolder(String folder, String filename)
                throws MeridioDataSetException, RemoteException, MeridioWrapperException
        {
                // First map folder to a class ID
                int classID = findClassOrFolder(folder);
                if (classID == 0)
                        return null;

                DMDataSet dsSearchCriteria = new DMDataSet();
                int currentSearchTerm = 1;

                // Exclude things marked for delete
                PROPERTY_TERMS drDeleteSearch = new PROPERTY_TERMS();
                drDeleteSearch.setId(currentSearchTerm++);                              
                drDeleteSearch.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
                drDeleteSearch.setPropertyName("PROP_markedForDelete");
                drDeleteSearch.setCategoryId(4);                               //Global Standard/Fixed Property
                drDeleteSearch.setNum_relation(new Short("0").shortValue());   //dmNumRelation.EQUAL
                drDeleteSearch.setNum_value(0);
                drDeleteSearch.setParentId(1);
                drDeleteSearch.setIsVersionProperty(false);
                dsSearchCriteria.addPROPERTY_TERMS(drDeleteSearch);

                // Documents only
                PROPERTY_TERMS drDocsOrRecsSearch = new PROPERTY_TERMS();
                drDocsOrRecsSearch.setId(currentSearchTerm++);                                                                  
                drDocsOrRecsSearch.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
                drDocsOrRecsSearch.setPropertyName("PROP_recordType");
                drDocsOrRecsSearch.setCategoryId(4);                               //Global Standard/Fixed Property
                drDocsOrRecsSearch.setNum_relation(new Short("1").shortValue());   //dmNumberRelation.NOTEQUAL=1
                drDocsOrRecsSearch.setNum_value(0);
                drDocsOrRecsSearch.setParentId(1);
                drDocsOrRecsSearch.setIsVersionProperty(false);
                dsSearchCriteria.addPROPERTY_TERMS(drDocsOrRecsSearch);
                                
                PROPERTY_TERMS drDocsOrRecsSearch2 = new PROPERTY_TERMS();
                drDocsOrRecsSearch2.setId(currentSearchTerm++);                                                                 
                drDocsOrRecsSearch2.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
                drDocsOrRecsSearch2.setPropertyName("PROP_recordType");
                drDocsOrRecsSearch2.setCategoryId(4);                               //Global Standard/Fixed Property
                drDocsOrRecsSearch2.setNum_relation(new Short("1").shortValue());   //dmNumberRelation.NOTEQUAL=1
                drDocsOrRecsSearch2.setNum_value(4);
                drDocsOrRecsSearch2.setParentId(1);
                drDocsOrRecsSearch2.setIsVersionProperty(false);
                dsSearchCriteria.addPROPERTY_TERMS(drDocsOrRecsSearch2);
                                
                PROPERTY_TERMS drDocsOrRecsSearch3 = new PROPERTY_TERMS();
                drDocsOrRecsSearch3.setId(currentSearchTerm++);                                                                 
                drDocsOrRecsSearch3.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
                drDocsOrRecsSearch3.setPropertyName("PROP_recordType");
                drDocsOrRecsSearch3.setCategoryId(4);                               //Global Standard/Fixed Property
                drDocsOrRecsSearch3.setNum_relation(new Short("1").shortValue());   //dmNumberRelation.NOTEQUAL=1
                drDocsOrRecsSearch3.setNum_value(19);
                drDocsOrRecsSearch3.setParentId(1);
                drDocsOrRecsSearch3.setIsVersionProperty(false);
                dsSearchCriteria.addPROPERTY_TERMS(drDocsOrRecsSearch3);

                // Include only things in the specified folder
                SEARCH_CONTAINERS drSearchContainers = new SEARCH_CONTAINERS();
                drSearchContainers.setContainerId(classID);                                     
                dsSearchCriteria.addSEARCH_CONTAINERS(drSearchContainers);

                // Match the title
                PROPERTY_TERMS drTitleSearch = new PROPERTY_TERMS();
                drTitleSearch.setId(currentSearchTerm++);
                drTitleSearch.setTermType(new Short("0").shortValue());
                drTitleSearch.setPropertyName("PROP_W_title");
                drTitleSearch.setCategoryId(4);
                drTitleSearch.setStr_relation(new Short("0").shortValue());   //dmNumRelation.EQUAL
                drTitleSearch.setStr_value(filename);
                drTitleSearch.setParentId(1);
                drTitleSearch.setIsVersionProperty(false);
                dsSearchCriteria.addPROPERTY_TERMS(drTitleSearch);

                PROPERTY_OPS drPropertyOps = new PROPERTY_OPS();
                drPropertyOps.setId(1);
                drPropertyOps.setOperator(new Short("0").shortValue()); // AND
                dsSearchCriteria.addPROPERTY_OPS(drPropertyOps);

                // Specify that we want the document id back, and that's all
                RESULTDEFS drResultDefs = new RESULTDEFS();
                drResultDefs.setPropertyName("PROP_lastModifiedDate");
                drResultDefs.setIsVersionProperty(false);
                drResultDefs.setCategoryId(4);
                dsSearchCriteria.addRESULTDEFS(drResultDefs);

                DMSearchResults searchResults = searchDocuments(dsSearchCriteria, 
                        100, 0, DmPermission.READ, false,
                        DmSearchScope.BOTH, false, true, false, DmLogicalOp.AND);                                       

                // Look at results
                if (searchResults.dsDM != null)
                {
                        SEARCHRESULTS_DOCUMENTS [] srd = searchResults.dsDM.getSEARCHRESULTS_DOCUMENTS();
                        if (srd.length != 1)
                                return null;
                        return new Long(srd[0].getDocId());
                }
                return null;
        }

        /** Lookup a document with a specified name in a specified
         *  folder */
        public Long findRecordInFolder(String folder, String filename)
                throws MeridioDataSetException, RemoteException, MeridioWrapperException
        {
                // First map folder to a class ID
                int classID = findClassOrFolder(folder);
                if (classID == 0)
                        return null;

                DMDataSet dsSearchCriteria = new DMDataSet();
                int currentSearchTerm = 1;

                // Exclude things marked for delete
                PROPERTY_TERMS drDeleteSearch = new PROPERTY_TERMS();
                drDeleteSearch.setId(currentSearchTerm++);                              
                drDeleteSearch.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
                drDeleteSearch.setPropertyName("PROP_markedForDelete");
                drDeleteSearch.setCategoryId(4);                               //Global Standard/Fixed Property
                drDeleteSearch.setNum_relation(new Short("0").shortValue());   //dmNumRelation.EQUAL
                drDeleteSearch.setNum_value(0);
                drDeleteSearch.setParentId(1);
                drDeleteSearch.setIsVersionProperty(false);
                dsSearchCriteria.addPROPERTY_TERMS(drDeleteSearch);

                // Records only
                PROPERTY_TERMS drDocsOrRecsSearch = new PROPERTY_TERMS();
                drDocsOrRecsSearch.setId(currentSearchTerm++);                                                                  
                drDocsOrRecsSearch.setTermType(new Short("1").shortValue());       //0=STRING, 1=NUMBER, 2=DATE
                drDocsOrRecsSearch.setPropertyName("PROP_recordType");
                drDocsOrRecsSearch.setCategoryId(4);                               //Global Standard/Fixed Property
                drDocsOrRecsSearch.setNum_relation(new Short("5").shortValue());   //dmNumberRelation.GREATEROREQUAL=5
                drDocsOrRecsSearch.setNum_value(4);
                drDocsOrRecsSearch.setParentId(1);
                drDocsOrRecsSearch.setIsVersionProperty(false);
                dsSearchCriteria.addPROPERTY_TERMS(drDocsOrRecsSearch);

                // Match the title
                PROPERTY_TERMS drTitleSearch = new PROPERTY_TERMS();
                drTitleSearch.setId(currentSearchTerm++);
                drTitleSearch.setTermType(new Short("0").shortValue());
                drTitleSearch.setPropertyName("PROP_W_title");
                drTitleSearch.setCategoryId(4);
                drTitleSearch.setStr_relation(new Short("0").shortValue());   //dmNumRelation.EQUAL
                drTitleSearch.setStr_value(filename);
                drTitleSearch.setParentId(1);
                drTitleSearch.setIsVersionProperty(false);
                dsSearchCriteria.addPROPERTY_TERMS(drTitleSearch);

                PROPERTY_OPS drPropertyOps = new PROPERTY_OPS();
                drPropertyOps.setId(1);
                drPropertyOps.setOperator(new Short("0").shortValue()); // AND
                dsSearchCriteria.addPROPERTY_OPS(drPropertyOps);

                // Include only things in the specified folder
                SEARCH_CONTAINERS drSearchContainers = new SEARCH_CONTAINERS();
                drSearchContainers.setContainerId(classID);                                     
                dsSearchCriteria.addSEARCH_CONTAINERS(drSearchContainers);

                // Specify that we want the document id, and that's all
                RESULTDEFS drResultDefs = new RESULTDEFS();
                drResultDefs.setPropertyName("PROP_lastModifiedDate");
                drResultDefs.setIsVersionProperty(false);
                drResultDefs.setCategoryId(4);
                dsSearchCriteria.addRESULTDEFS(drResultDefs);

                DMSearchResults searchResults = searchDocuments(dsSearchCriteria, 
                        100, 0, DmPermission.READ, false,
                        DmSearchScope.BOTH, false, true, false, DmLogicalOp.AND);                                       

                // Look at results
                if (searchResults.dsDM != null)
                {
                        SEARCHRESULTS_DOCUMENTS [] srd = searchResults.dsDM.getSEARCHRESULTS_DOCUMENTS();
                        if (srd.length != 1)
                                return null;
                        return new Long(srd[0].getDocId());
                }
                return null;
        }

        protected void addRMObject
    (                   
        String objectXML
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {               
                if (oLog != null) oLog.debug("Meridio: Entering addRMObject");
                
                try
                {                                                                                                       
                        boolean loginTried = false;
                        while (true)
                        {
                                loginUnified();
                                try
                                {

                                        /*====================================================================
                                        * Remove the document references from the various folders
                                        * 
                                        * Create a new DOM object and start building up the SOAP body
                                        *===================================================================*/
                                        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                                        documentBuilderFactory.setValidating(false);            
                                        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();                                  
                                        Document newDoc = documentBuilder.newDocument();
                                                
                                        /*====================================================================
                                        * Create the top-level element describing what Web Service method
                                        * we are calling: to delete a record in Meridio we call ApplyChanges
                                        *===================================================================*/
                                        Element applyChanges = newDoc.createElement("ApplyChanges");
                                        applyChanges.setAttribute("xmlns", "http://www.meridio.com/MeridioRMWS");
                                        newDoc.appendChild(applyChanges);
                                
                                        /*====================================================================
                                        * Add the "ds" [DataSet] element to the XML
                                        *===================================================================*/
                                        Element ds = newDoc.createElement("ds");                        
                                        applyChanges.appendChild(ds);
                        
                                        /*====================================================================
                                        * Because we are sending a deletion, we must build up the XML for
                                        * a Microsoft DataSet Diffgram. 
                                        *===================================================================*/
                                        Element diffGram = newDoc.createElement("diffgr:diffgram");
                                        diffGram.setAttribute("xmlns:diffgr", "urn:schemas-microsoft-com:xml-diffgram-v1");
                                        diffGram.setAttribute("xmlns:msdata", "urn:schemas-microsoft-com:xml-msdata");          
                                        ds.appendChild(diffGram);                       
                        
                                        Element afterDataSet = newDoc.createElement("RMDataSet");
                                        afterDataSet.setAttribute("xmlns", "http://www.meridio.com/RMDataSet.xsd");     
                                        diffGram.appendChild(afterDataSet);
                                                                
                                        StringReader objectXMLStringReader = new StringReader(objectXML);
                                        InputSource objectXMLInputSource   = new InputSource(objectXMLStringReader);                                            
                                        Document objectXMLDocument = documentBuilderFactory.newDocumentBuilder().parse(objectXMLInputSource);
                                        Element objectXMLElement = objectXMLDocument.getDocumentElement();                      
                                        objectXMLElement.setAttribute("diffgr:id", "OBJECT1");
                                        objectXMLElement.setAttribute("msdata:rowOrder", "0");
                                        objectXMLElement.setAttribute("diffgr:hasChanges", "inserted");                                         
                                        Node objectXMLNode = newDoc.importNode(objectXMLElement, true); 
                                        afterDataSet.appendChild(objectXMLNode);
                                                                                                                                
                                        /*====================================================================
                                        * Create the SOAP Envelope
                                        *===================================================================*/
                                        org.apache.axis.message.SOAPEnvelope env = new org.apache.axis.message.SOAPEnvelope();          
                        
                                        /*====================================================================
                                        * Populate the SOAP Header
                                        *===================================================================*/                                  
                                        SOAPHeader header = env.getHeader();    
                                        Name headerElementName = env.createName("MeridioCredentialHeader", "", "http://www.meridio.com/MeridioRMWS");           
                                        SOAPHeaderElement headerElement = header.addHeaderElement(headerElementName);
                                        headerElement.setMustUnderstand(false);
                                        headerElement.addNamespaceDeclaration("soap", "http://schemas.xmlsoap.org/soap/envelope");                      
                                        SOAPElement token = headerElement.addChildElement("Token");
                                        token.addTextNode(loginToken_);                 
                                        
                                        /*====================================================================
                                        * Populate the SOAP body with the Diffgram XML we built up earlier
                                        *===================================================================*/  
                                        SOAPBody body = env.getBody();
                                        body.addDocument(newDoc);
                                        
                                        /*====================================================================
                                        * Invoke the web service
                                        *===================================================================*/  
                                        callRMApplyChanges(env);
                                        return;                                         
                                }
                                catch (RemoteException e)
                                {
                                        if (loginTried == false)
                                        {
                                                loginToken_ = null;
                                                loginTried = true;
                                                continue;
                                        }
                                        throw e;
                                }
                        }
                }               
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }               
                catch (SAXException SaxException)
                {
                        throw new MeridioDataSetException(
                                        "XML Error in marshalling the 'before' of 'after' XML", 
                                        SaxException);
                }
                catch (ParserConfigurationException parserConfigurationException)
                {
                        throw new MeridioDataSetException(
                                        "XML Error in parsing the Meridio Dataset", 
                                        parserConfigurationException);
                }
                catch (SOAPException exSOAPException)
                {
                        throw new MeridioWrapperException(
                                        "Encountered a SOAP Exception constructing the SOAP Envelope", 
                                        exSOAPException);
                }               
                finally         
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting updateRMObject");
                }                                                                       
        }

        
        
        
        
        protected void updateDMObject
    (                   
        String beforeXML,
        String afterXML
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {               
                if (oLog != null) oLog.debug("Meridio: Entering updateDMObject");
                
                try
                {

                        boolean loginTried = false;
                        while (true)
                        {
                                loginUnified();
                                try
                                {

                                        /*====================================================================
                                        * Remove the document references from the various folders
                                        * 
                                        * Create a new DOM object and start building up the SOAP body
                                        *===================================================================*/
                                        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                                        documentBuilderFactory.setValidating(false);            
                                        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();                                  
                                        Document newDoc = documentBuilder.newDocument();
                                                
                                        /*====================================================================
                                        * Create the top-level element describing what Web Service method
                                        * we are calling: to delete a record in Meridio we call ApplyChanges
                                        *===================================================================*/
                                        Element applyChanges = newDoc.createElement("ApplyChanges");
                                        applyChanges.setAttribute("xmlns", "http://www.meridio.com/MeridioDMWS");
                                        newDoc.appendChild(applyChanges);
                                
                                        /*====================================================================
                                        * Add the "ds" [DataSet] element to the XML
                                        *===================================================================*/
                                        Element ds = newDoc.createElement("changes");                   
                                        applyChanges.appendChild(ds);
                        
                                        /*====================================================================
                                        * Because we are sending a deletion, we must build up the XML for
                                        * a Microsoft DataSet Diffgram. 
                                        *===================================================================*/
                                        Element diffGram = newDoc.createElement("diffgr:diffgram");
                                        diffGram.setAttribute("xmlns:diffgr", "urn:schemas-microsoft-com:xml-diffgram-v1");
                                        diffGram.setAttribute("xmlns:msdata", "urn:schemas-microsoft-com:xml-msdata");          
                                        ds.appendChild(diffGram);                       
                        
                                        Element afterDataSet = newDoc.createElement("DMDataSet");
                                        afterDataSet.setAttribute("xmlns", "http://www.meridio.com/DMDataSet.xsd");     
                                        diffGram.appendChild(afterDataSet);
                                                                
                                        StringReader afterXMLStringReader = new StringReader(afterXML);
                                        InputSource afterXMLInputSource   = new InputSource(afterXMLStringReader);                                              
                                        Document afterXMLDocument = documentBuilderFactory.newDocumentBuilder().parse(afterXMLInputSource);
                                        Element afterXMLElement = afterXMLDocument.getDocumentElement();                        
                                        afterXMLElement.setAttribute("diffgr:id", "OBJECT1");
                                        afterXMLElement.setAttribute("msdata:rowOrder", "0");
                                        afterXMLElement.setAttribute("diffgr:hasChanges", "modified");                                          
                                        Node afterXMLNode = newDoc.importNode(afterXMLElement, true);   
                                        afterDataSet.appendChild(afterXMLNode);
                                                                                                
                                        Element diffGramBefore = newDoc.createElement("diffgr:before");
                                        diffGramBefore.setAttribute("xmlns:diffgr", "urn:schemas-microsoft-com:xml-diffgram-v1");
                                        diffGram.appendChild(diffGramBefore);                           
                        
                                        StringReader beforeXMLStringReader = new StringReader(beforeXML);
                                        InputSource beforeXMLInputSource   = new InputSource(beforeXMLStringReader);            
                                        Document beforeXMLDocument = documentBuilderFactory.newDocumentBuilder().parse(beforeXMLInputSource);
                                        Element beforeXMLElement = beforeXMLDocument.getDocumentElement();
                        
                                        beforeXMLElement.setAttribute("diffgr:id", "OBJECT1");
                                        beforeXMLElement.setAttribute("msdata:rowOrder", "0");
                                        beforeXMLElement.setAttribute("xmlns", "http://www.meridio.com/DMDataSet.xsd");
                        
                                        Node beforeXMLNode = newDoc.importNode(beforeXMLElement, true);
                                        diffGramBefore.appendChild(beforeXMLNode);
                        
                                        /*====================================================================
                                        * Create the SOAP Envelope
                                        *===================================================================*/
                                        org.apache.axis.message.SOAPEnvelope env = new org.apache.axis.message.SOAPEnvelope();          
                        
                                        /*====================================================================
                                        * Populate the SOAP Header
                                        *===================================================================*/                                  
                                        SOAPHeader header = env.getHeader();    
                                        Name headerElementName = env.createName("MeridioCredentialHeader", "", "http://www.meridio.com/MeridioDMWS");           
                                        SOAPHeaderElement headerElement = header.addHeaderElement(headerElementName);
                                        headerElement.setMustUnderstand(false);
                                        headerElement.addNamespaceDeclaration("soap", "http://schemas.xmlsoap.org/soap/envelope");                      
                                        SOAPElement token = headerElement.addChildElement("token");
                                        token.addTextNode(loginToken_);                 
                                        
                                        /*====================================================================
                                        * Populate the SOAP body with the Diffgram XML we built up earlier
                                        *===================================================================*/  
                                        SOAPBody body = env.getBody();
                                        body.addDocument(newDoc);
                                        
                                        /*====================================================================
                                        * Invoke the web service
                                        *===================================================================*/  
                                        callDMApplyChanges(env);
                                        return;                 
                                }
                                catch (RemoteException e)
                                {
                                        if (loginTried == false)
                                        {
                                                loginToken_ = null;
                                                loginTried = true;
                                                continue;
                                        }
                                        throw e;
                                }
                        }
                }               
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }               
                catch (SAXException SaxException)
                {
                        throw new MeridioDataSetException(
                                        "XML Error in marshalling the 'before' of 'after' XML", 
                                        SaxException);
                }
                catch (ParserConfigurationException parserConfigurationException)
                {
                        throw new MeridioDataSetException(
                                        "XML Error in parsing the Meridio Dataset", 
                                        parserConfigurationException);
                }
                catch (SOAPException exSOAPException)
                {
                        throw new MeridioWrapperException(
                                        "Encountered a SOAP Exception constructing the SOAP Envelope", 
                                        exSOAPException);
                }               
                finally         
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting updateDMObject");
                }                                                                       
        }
        
        
        
        protected void updateRMObject
    (                   
        String beforeXML,
        String afterXML
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {               
                if (oLog != null) oLog.debug("Meridio: Entering updateRMObject");
                
                try
                {
                        boolean loginTried = false;
                        while (true)
                        {
                                loginUnified();
                                try
                                {
                                        /*====================================================================
                                        * Remove the document references from the various folders
                                        * 
                                        * Create a new DOM object and start building up the SOAP body
                                        *===================================================================*/
                                        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                                        documentBuilderFactory.setValidating(false);            
                                        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();                                  
                                        Document newDoc = documentBuilder.newDocument();
                                                
                                        /*====================================================================
                                        * Create the top-level element describing what Web Service method
                                        * we are calling: to delete a record in Meridio we call ApplyChanges
                                        *===================================================================*/
                                        Element applyChanges = newDoc.createElement("ApplyChanges");
                                        applyChanges.setAttribute("xmlns", "http://www.meridio.com/MeridioRMWS");
                                        newDoc.appendChild(applyChanges);
                                
                                        /*====================================================================
                                        * Add the "ds" [DataSet] element to the XML
                                        *===================================================================*/
                                        Element ds = newDoc.createElement("ds");                        
                                        applyChanges.appendChild(ds);
                        
                                        /*====================================================================
                                        * Because we are sending a deletion, we must build up the XML for
                                        * a Microsoft DataSet Diffgram. 
                                        *===================================================================*/
                                        Element diffGram = newDoc.createElement("diffgr:diffgram");
                                        diffGram.setAttribute("xmlns:diffgr", "urn:schemas-microsoft-com:xml-diffgram-v1");
                                        diffGram.setAttribute("xmlns:msdata", "urn:schemas-microsoft-com:xml-msdata");          
                                        ds.appendChild(diffGram);                       
                        
                                        Element afterDataSet = newDoc.createElement("RMDataSet");
                                        afterDataSet.setAttribute("xmlns", "http://www.meridio.com/RMDataSet.xsd");     
                                        diffGram.appendChild(afterDataSet);
                                                                
                                        StringReader afterXMLStringReader = new StringReader(afterXML);
                                        InputSource afterXMLInputSource   = new InputSource(afterXMLStringReader);                                              
                                        Document afterXMLDocument = documentBuilderFactory.newDocumentBuilder().parse(afterXMLInputSource);
                                        Element afterXMLElement = afterXMLDocument.getDocumentElement();                        
                                        afterXMLElement.setAttribute("diffgr:id", "OBJECT1");
                                        afterXMLElement.setAttribute("msdata:rowOrder", "0");
                                        afterXMLElement.setAttribute("diffgr:hasChanges", "modified");                                          
                                        Node afterXMLNode = newDoc.importNode(afterXMLElement, true);   
                                        afterDataSet.appendChild(afterXMLNode);
                                                                                                
                                        Element diffGramBefore = newDoc.createElement("diffgr:before");
                                        diffGramBefore.setAttribute("xmlns:diffgr", "urn:schemas-microsoft-com:xml-diffgram-v1");
                                        diffGram.appendChild(diffGramBefore);                           
                        
                                        StringReader beforeXMLStringReader = new StringReader(beforeXML);
                                        InputSource beforeXMLInputSource   = new InputSource(beforeXMLStringReader);            
                                        Document beforeXMLDocument = documentBuilderFactory.newDocumentBuilder().parse(beforeXMLInputSource);
                                        Element beforeXMLElement = beforeXMLDocument.getDocumentElement();
                        
                                        beforeXMLElement.setAttribute("diffgr:id", "OBJECT1");
                                        beforeXMLElement.setAttribute("msdata:rowOrder", "0");
                                        beforeXMLElement.setAttribute("xmlns", "http://www.meridio.com/RMDataSet.xsd");
                        
                                        Node beforeXMLNode = newDoc.importNode(beforeXMLElement, true);
                                        diffGramBefore.appendChild(beforeXMLNode);
                        
                                        /*====================================================================
                                        * Create the SOAP Envelope
                                        *===================================================================*/
                                        org.apache.axis.message.SOAPEnvelope env = new org.apache.axis.message.SOAPEnvelope();          
                        
                                        /*====================================================================
                                        * Populate the SOAP Header
                                        *===================================================================*/                                  
                                        SOAPHeader header = env.getHeader();    
                                        Name headerElementName = env.createName("MeridioCredentialHeader", "", "http://www.meridio.com/MeridioRMWS");           
                                        SOAPHeaderElement headerElement = header.addHeaderElement(headerElementName);
                                        headerElement.setMustUnderstand(false);
                                        headerElement.addNamespaceDeclaration("soap", "http://schemas.xmlsoap.org/soap/envelope");                      
                                        SOAPElement token = headerElement.addChildElement("Token");
                                        token.addTextNode(loginToken_);                 
                                        
                                        /*====================================================================
                                        * Populate the SOAP body with the Diffgram XML we built up earlier
                                        *===================================================================*/  
                                        SOAPBody body = env.getBody();
                                        body.addDocument(newDoc);
                                        
                                        /*====================================================================
                                        * Invoke the web service
                                        *===================================================================*/  
                                        callRMApplyChanges(env);
                                        return;                                         
                                }
                                catch (RemoteException e)
                                {
                                        if (loginTried == false)
                                        {
                                                loginToken_ = null;
                                                loginTried = true;
                                                continue;
                                        }
                                        throw e;
                                }
                        }
                }               
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }               
                catch (SAXException SaxException)
                {
                        throw new MeridioDataSetException(
                                        "XML Error in marshalling the 'before' of 'after' XML", 
                                        SaxException);
                }
                catch (ParserConfigurationException parserConfigurationException)
                {
                        throw new MeridioDataSetException(
                                        "XML Error in parsing the Meridio Dataset", 
                                        parserConfigurationException);
                }
                catch (SOAPException exSOAPException)
                {
                        throw new MeridioWrapperException(
                                        "Encountered a SOAP Exception constructing the SOAP Envelope", 
                                        exSOAPException);
                }               
                finally         
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting updateRMObject");
                }                                                                       
        }
        
        
        
        
        protected void deleteDMObject
    (                   
        String objectXML
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {               
                if (oLog != null) oLog.debug("Meridio: Entering deleteDMObject");
                
                try
                {                                                                                                       
                        boolean loginTried = false;
                        while (true)
                        {
                                loginUnified();
                                try
                                {
                                        /*====================================================================                  
                                        * Create a new DOM object and start building up the SOAP body
                                        *===================================================================*/
                                        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
                                        documentBuilderFactory.setValidating(false);            
                                        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();                                  
                                        Document newDoc = documentBuilder.newDocument();
                                                
                                        /*====================================================================
                                        * Create the top-level element describing what Web Service method
                                        * we are calling: to delete a record in Meridio we call ApplyChanges
                                        *===================================================================*/
                                        Element applyChanges = newDoc.createElement("ApplyChanges");
                                        applyChanges.setAttribute("xmlns", "http://www.meridio.com/MeridioDMWS");
                                        newDoc.appendChild(applyChanges);
                                
                                        /*====================================================================
                                        * Add the "ds" [DataSet] element to the XML
                                        *===================================================================*/
                                        Element ds = newDoc.createElement("changes");                   
                                        applyChanges.appendChild(ds);
                        
                                        /*====================================================================
                                        * Because we are sending a deletion, we must build up the XML for
                                        * a Microsoft DataSet Diffgram. 
                                        *===================================================================*/
                                        Element diffGram = newDoc.createElement("diffgr:diffgram");
                                        diffGram.setAttribute("xmlns:diffgr", "urn:schemas-microsoft-com:xml-diffgram-v1");
                                        diffGram.setAttribute("xmlns:msdata", "urn:schemas-microsoft-com:xml-msdata");          
                                        ds.appendChild(diffGram);                       
                        
                                        Element emptyDataSet = newDoc.createElement("DMDataSet");
                                        emptyDataSet.setAttribute("xmlns", "http://www.meridio.com/DMDataSet.xsd");             
                                        diffGram.appendChild(emptyDataSet);
                                                        
                                        Element diffGramBefore = newDoc.createElement("diffgr:before");
                                        diffGramBefore.setAttribute("xmlns:diffgr", "urn:schemas-microsoft-com:xml-diffgram-v1");
                                        diffGram.appendChild(diffGramBefore);                           
                        
                                        /*====================================================================
                                        * Add the object to be deleted
                                        *===================================================================*/
                                        StringReader objectXMLStringReader = new StringReader(objectXML);
                                        InputSource objectXMLInputSource   = new InputSource(objectXMLStringReader);                                            
                                        Document objectXMLDocument = documentBuilderFactory.newDocumentBuilder().parse(objectXMLInputSource);
                                        Element objectXMLElement = objectXMLDocument.getDocumentElement();                      
                                        objectXMLElement.setAttribute("diffgr:id", "OBJECT1");
                                        objectXMLElement.setAttribute("msdata:rowOrder", "0");                                  
                                        Node objectXMLNode = newDoc.importNode(objectXMLElement, true); 
                                        diffGramBefore.appendChild(objectXMLNode);                                                              
                                                                                                                                
                                        /*====================================================================
                                        * Create the SOAP Envelope
                                        *===================================================================*/
                                        org.apache.axis.message.SOAPEnvelope env = new org.apache.axis.message.SOAPEnvelope();          
                        
                                        /*====================================================================
                                        * Populate the SOAP Header
                                        *===================================================================*/                                  
                                        SOAPHeader header = env.getHeader();    
                                        Name headerElementName = env.createName("MeridioCredentialHeader", "", 
                                                                                "http://www.meridio.com/MeridioDMWS");          
                                        SOAPHeaderElement headerElement = header.addHeaderElement(headerElementName);
                                        headerElement.setMustUnderstand(false);
                                        headerElement.addNamespaceDeclaration("soap", "http://schemas.xmlsoap.org/soap/envelope");                      
                                        SOAPElement token = headerElement.addChildElement("token");
                                        token.addTextNode(loginToken_);                 
                                        
                                        /*====================================================================
                                        * Populate the SOAP body with the Diffgram XML we built up earlier
                                        *===================================================================*/  
                                        SOAPBody body = env.getBody();
                                        body.addDocument(newDoc);
                                        
                                        /*====================================================================
                                        * Invoke the web service
                                        *===================================================================*/  
                                        callDMApplyChanges(env);
                                        return; 
                                }
                                catch (RemoteException e)
                                {
                                        if (loginTried == false)
                                        {
                                                loginToken_ = null;
                                                loginTried = true;
                                                continue;
                                        }
                                        throw e;
                                }
                        }
                }               
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }       
                catch (SAXException SaxException)
                {
                        throw new MeridioDataSetException(
                                        "XML Error in marshalling the object XML", 
                                        SaxException);
                }
                catch (ParserConfigurationException parserConfigurationException)
                {
                        throw new MeridioDataSetException(
                                        "XML Error in parsing the Meridio Dataset", 
                                        parserConfigurationException);
                }
                catch (SOAPException exSOAPException)
                {
                        throw new MeridioWrapperException(
                                        "Encountered a SOAP Exception constructing the SOAP Envelope", 
                                        exSOAPException);
                }               
                finally         
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting deleteDMObject");
                }                                                                       
        }
        
        
        
        protected SOAPEnvelope callDMApplyChanges
        (
                        SOAPEnvelope env
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                
                if (oLog != null) oLog.debug("Meridio: Entering callDMApplyChanges");
                
                try
                {
                        Call call = getDMWebService()._createCall();

                        call.setUsername(userName);
                        call.setPassword(password);     
                        call.setUseSOAPAction(true);
                        call.setProperty(Call.SOAPACTION_USE_PROPERTY, new Boolean(true));
                        call.setProperty(Call.SOAPACTION_URI_PROPERTY, "http://www.meridio.com/MeridioDMWS/ApplyChanges");                              
                        call.setProperty(CONFIGURATION_PROPERTY,dmwsConfig);
                        
                        QName portName = new QName("http://www.meridio.com/MeridioDMWS/", "ApplyChanges");
                        call.setPortName(portName);             
                        call.setTargetEndpointAddress(getMeridioDmwsUrl());
                                                        
                        SOAPEnvelope result = call.invoke(env);
                        
                        Element resultElement = null;
                        try
                        {
                                resultElement = result.getAsDOM();
                        }
                        catch (Exception ex) 
                        {                       
                                throw new MeridioWrapperException(
                                                "Error retrieving the XML Document from the Web Service response", 
                                                ex);
                        }
                        
                        NodeList errors         = resultElement.getElementsByTagName("diffgr:errors");
                        if (errors.getLength() != 0)
                        {
                                String errorXML = "";
                                
                                if (oLog != null)
                                        oLog.error("Found <" + errors.getLength() + "> errors in returned data set");   
                                for (int i = 0; i < errors.getLength(); i++)
                                {                                       
                                        Element e                 = (Element) errors.item(i);                           
                                                                                
                                        Document resultDocument   = new DocumentImpl();
                                        Node node                 = resultDocument.importNode(e, true);
                                        
                                        resultDocument.appendChild(node);
                                        
                                        DOMImplementation domImpl = DOMImplementationImpl.getDOMImplementation();                       
                                        DOMImplementationLS implLS = (DOMImplementationLS) domImpl;
                                        LSSerializer errorWriter = implLS.createLSSerializer();                 
                                        errorXML += errorWriter.writeToString(resultDocument) + "\n";                                                                                                                   
                                }
                                throw new MeridioDataSetException("An error occurred when deleting the object: " + errorXML);
                        }
                        
                        return result;
                        
                }
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }                                               
                catch (ServiceException serviceException)
                {
                        throw new MeridioWrapperException("Encountered an exception when trying to instantiate a SOAP call",
                                        serviceException);
                }       
                finally         
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting callDMApplyChanges");
                }                               
        }
        
        
        
        
        protected SOAPEnvelope callRMApplyChanges
        (
                        SOAPEnvelope env
        )
        throws RemoteException, MeridioDataSetException, MeridioWrapperException
        {
                
                if (oLog != null) oLog.debug("Meridio: Entering callRMApplyChanges");
                
                try
                {
                        /*====================================================================
                        * Invoke the web service
                        *===================================================================*/  
                        Call call = getRMWebService()._createCall();
                        call.setUsername(userName);
                        call.setPassword(password);             
                        call.setUseSOAPAction(true);
                        call.setProperty(Call.SOAPACTION_USE_PROPERTY, new Boolean(true));
                        call.setProperty(Call.SOAPACTION_URI_PROPERTY, "http://www.meridio.com/MeridioRMWS/ApplyChanges");                              
                        call.setProperty(CONFIGURATION_PROPERTY,rmwsConfig);

                        QName portName = new QName("http://www.meridio.com/MeridioRMWS/", "ApplyChanges");
                        call.setPortName(portName);             
                        call.setTargetEndpointAddress(getMeridioRmwsUrl());
                                                        
                        SOAPEnvelope result = call.invoke(env);
                        
                        Element resultElement = null;
                        try
                        {
                                resultElement = result.getAsDOM();
                        }
                        catch (Exception ex) 
                        {                       
                                throw new MeridioWrapperException(
                                                "Error retrieving the XML Document from the Web Service response", 
                                                ex);
                        }
                        
                        NodeList errors         = resultElement.getElementsByTagName("diffgr:errors");
                        if (errors.getLength() != 0)
                        {
                                String errorXML = "";

                                if (oLog != null)
                                        oLog.error("Found <" + errors.getLength() + "> errors in returned data set");   
                                for (int i = 0; i < errors.getLength(); i++)
                                {                                       
                                        Element e                 = (Element) errors.item(i);                           
                                                                                
                                        Document resultDocument   = new DocumentImpl();
                                        Node node                 = resultDocument.importNode(e, true);
                                        
                                        resultDocument.appendChild(node);
                                        
                                        DOMImplementation domImpl = DOMImplementationImpl.getDOMImplementation();                       
                                        DOMImplementationLS implLS = (DOMImplementationLS) domImpl;
                                        LSSerializer errorWriter = implLS.createLSSerializer();                 
                                        errorXML += errorWriter.writeToString(resultDocument) + "\n";                                                                                                                   
                                }
                                throw new MeridioDataSetException("An error occurred when deleting the object: " + errorXML);
                        }
                        
                        return result;
                        
                }
                catch (IOException IoException)
                {
                        throw new MeridioDataSetException(
                                        "IO Error in marshalling the Meridio Dataset", 
                                        IoException);
                }                                               
                catch (ServiceException serviceException)
                {
                        throw new MeridioWrapperException("Encountered an exception when trying to instantiate a SOAP call",
                                        serviceException);
                }       
                finally         
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting callRMApplyChanges");
                }                               
        }
        
        
        
        
        
        /*
         * The Meridio Web Service method throws an error when we call ApplyChanges
         * as the XML currently passed down is not a valid MS diffgram.  
         */
        protected DMDataSet applyChanges
        (
                        DMDataSet dsDM
        )
        throws RemoteException, MeridioDataSetException
        {
                if (oLog != null) oLog.debug("Meridio: Entered applyChanges method for DM.");
                
                try
                {
                    boolean loginTried = false;
                    while (true)
                    {
                            loginUnified();
                            try
                            {
                                com.meridio.www.MeridioDMWS.ApplyChangesWTResponseApplyChangesWTResult applyChangesResponse = 
                                    new com.meridio.www.MeridioDMWS.ApplyChangesWTResponseApplyChangesWTResult();
                                                
                                com.meridio.www.MeridioDMWS.ApplyChangesWTChanges applyChangesWTChanges = 
                                    new com.meridio.www.MeridioDMWS.ApplyChangesWTChanges();
                                applyChangesWTChanges.set_any(getSOAPMessage(dsDM));
                        
                                applyChangesResponse = meridioDMWebService_.applyChanges(loginToken_, applyChangesWTChanges);
                                        
                                DMDataSet ds = getDMDataSet(applyChangesResponse.get_any());
                        
                                return ds;
                            }
                            catch (RemoteException e)
                            {
                                    if (loginTried == false)
                                    {
                                            loginToken_ = null;
                                            loginTried = true;
                                            continue;
                                    }
                                    throw e;
                            }
                    }
                }
                finally
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting applyChanges method for DM.");
                }               
        }       
        
        
        
        private RMDataSet applyChanges
        (
                        RMDataSet dsRM
        )
        throws RemoteException, MeridioDataSetException
        {
                if (oLog != null) oLog.debug("Meridio: Entered applyChanges method for RM.");
                
                try
                {
                    boolean loginTried = false;
                    while (true)
                    {
                            loginUnified();
                            try
                            {
                                ApplyChangesWTResponseApplyChangesWTResult applyChangesResponse = 
                                    new ApplyChangesWTResponseApplyChangesWTResult();
                                                
                                ApplyChangesWTDs applyChangesWTChanges = new ApplyChangesWTDs();
                                applyChangesWTChanges.set_any(getSOAPMessage(dsRM));
                        
                                applyChangesResponse = meridioRMWebService_.applyChanges(loginToken_, applyChangesWTChanges);                                           
                                        
                                RMDataSet ds = getRMDataSet(applyChangesResponse.get_any());
                        
                                return ds;                      
                            }
                            catch (RemoteException e)
                            {
                                    if (loginTried == false)
                                    {
                                            loginToken_ = null;
                                            loginTried = true;
                                            continue;
                                    }
                                    throw e;
                            }
                    }
                }
                finally
                {
                        if (oLog != null) oLog.debug("Meridio: Exiting applyChanges method for RM.");
                }               
        }
                
}
