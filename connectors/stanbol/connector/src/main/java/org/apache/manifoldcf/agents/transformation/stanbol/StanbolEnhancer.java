/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.manifoldcf.agents.transformation.stanbol;

import org.apache.commons.io.IOUtils;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.agents.system.Logging;
import org.apache.stanbol.client.Enhancer;
import org.apache.stanbol.client.StanbolClientFactory;
import org.apache.stanbol.client.enhancer.impl.EnhancerParameters;
import org.apache.stanbol.client.enhancer.model.EnhancementStructure;
import org.apache.stanbol.client.enhancer.model.EntityAnnotation;
import org.apache.stanbol.client.enhancer.model.TextAnnotation;
import org.apache.stanbol.client.entityhub.model.Entity;
import org.apache.stanbol.client.entityhub.model.LDPathProgram;
import org.apache.stanbol.client.exception.StanbolClientException;
import org.apache.stanbol.client.services.exception.StanbolServiceException;

import java.io.*;
import java.util.*;

public class StanbolEnhancer extends org.apache.manifoldcf.agents.transformation.BaseTransformationConnector
{
    public static final String _rcsid = "@(#)$Id: StanbolEnhancer.java 2015-07-17 djayakody $";

    private static final String EDIT_SPECIFICATION_JS = "editSpecification.js";
    private static final String EDIT_SPECIFICATION_FIELDMAPPING_HTML = "editSpecification_FieldMapping.html";
    private static final String VIEW_SPECIFICATION_HTML = "viewSpecification.html";

    private static final String STANBOL_ENDPOINT = "http://localhost:8080/";
    private static final String STANBOL_ENHANCEMENT_CHAIN = "default";
    
    private static final String customLDPathPrefix =  " http://manifoldcf.apache.org/custom/";
    /**
     * DOCUMENTS' FIELDS
     */
    static final String ENTITY_URI_FIELD = "entity_uris";

    protected static final String ACTIVITY_ENHANCE = "enhance";
    public static final double DEFAULT_DISAMBIGUATION_SCORE = 0.7;

    protected static final String[] activitiesList = new String[] { ACTIVITY_ENHANCE };

    private StanbolClientFactory stanbolFactory = null;

    /**
     * Return a list of activities that this connector generates. The connector does NOT need to be connected before
     * this method is called.
     * 
     * @return the set of activities.
     */
    @Override
    public String[] getActivitiesList()
    {
        return activitiesList;
    }

    /**
     * Get an output version string, given an output specification. The output version string is used to uniquely
     * describe the pertinent details of the output specification and the configuration, to allow the Connector
     * Framework to determine whether a document will need to be output again. Note that the contents of the document
     * cannot be considered by this method, and that a different version string (defined in IRepositoryConnector) is
     * used to describe the version of the actual document.
     * 
     * This method presumes that the connector object has been configured, and it is thus able to communicate with the
     * output data store should that be necessary.
     * 
     * @param os is the current output specification for the job that is doing the crawling.
     * @return a string, of unlimited length, which uniquely describes output configuration and specification in such a
     *         way that if two such strings are equal, the document will not need to be sent again to the output data
     *         store.
     */
    @Override
    public VersionContext getPipelineDescription(Specification os) throws ManifoldCFException, ServiceInterruption
    {
        SpecPacker sp = new SpecPacker(os);
        return new VersionContext(sp.toPackedString(), params, os);
    }

    /**
     * Detect if a mime type is acceptable or not. This method is used to determine whether it makes sense to fetch a
     * document in the first place.
     * 
     * @param pipelineDescription is the document's pipeline version string, for this connection.
     * @param mimeType is the mime type of the document.
     * @param checkActivity is an object including the activities that can be performed by this method.
     * @return true if the mime type can be accepted by this connector.
     */
    public boolean checkMimeTypeIndexable(VersionContext pipelineDescription, String mimeType,
            IOutputCheckActivity checkActivity) throws ManifoldCFException, ServiceInterruption
    {
        return checkActivity.checkMimeTypeIndexable("text/plain;charset=utf-8");
    }

    /**
     * Pre-determine whether a document (passed here as a File object) is acceptable or not. This method is used to
     * determine whether a document needs to be actually transferred. This hook is provided mainly to support search
     * engines that only handle a small set of accepted file types.
     * 
     * @param pipelineDescription is the document's pipeline version string, for this connection.
     * @param localFile is the local file to check.
     * @param checkActivity is an object including the activities that can be done by this method.
     * @return true if the file is acceptable, false if not.
     */
    @Override
    public boolean checkDocumentIndexable(VersionContext pipelineDescription, File localFile,
            IOutputCheckActivity checkActivity) throws ManifoldCFException, ServiceInterruption
    {
        return true;
    }

    /**
     * Pre-determine whether a document's length is acceptable. This method is used to determine whether to fetch a
     * document in the first place.
     * 
     * @param pipelineDescription is the document's pipeline version string, for this connection.
     * @param length is the length of the document.
     * @param checkActivity is an object including the activities that can be done by this method.
     * @return true if the file is acceptable, false if not.
     */
    @Override
    public boolean checkLengthIndexable(VersionContext pipelineDescription, long length,
            IOutputCheckActivity checkActivity) throws ManifoldCFException, ServiceInterruption
    {
        // Always true
        return true;
    }

    /**
     * Add (or replace) a document in the output data store using the connector. This method presumes that the connector
     * object has been configured, and it is thus able to communicate with the output data store should that be
     * necessary. The OutputSpecification is *not* provided to this method, because the goal is consistency, and if
     * output is done it must be consistent with the output description, since that was what was partly used to
     * determine if output should be taking place. So it may be necessary for this method to decode an output
     * description string in order to determine what should be done.
     * 
     * @param documentURI is the URI of the document. The URI is presumed to be the unique identifier which the output
     *            data store will use to process and serve the document. This URI is constructed by the repository
     *            connector which fetches the document, and is thus universal across all output connectors.
     * @param outputDescription is the description string that was constructed for this document by the
     *            getOutputDescription() method.
     * @param document is the document data to be processed (handed to the output data store).
     * @param authorityNameString is the name of the authority responsible for authorizing any access tokens passed in
     *            with the repository document. May be null.
     * @param activities is the handle to an object that the implementer of a pipeline connector may use to perform
     *            operations, such as logging processing activity, or sending a modified document to the next stage in
     *            the pipeline.
     * @return the document status (accepted or permanently rejected).
     * @throws IOException only if there's a stream error reading the document data.
     */
    @Override
    public int addOrReplaceDocumentWithException(String documentURI, VersionContext pipelineDescription,
            RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
            throws ManifoldCFException, ServiceInterruption, IOException
    {
        long startTime = System.currentTimeMillis();
        Logging.agents.info("Starting to enhance document content in Stanbol connector ");

        String resultCode = "OK";
        String description = null;

        SpecPacker sp = new SpecPacker(pipelineDescription.getSpecification());
        // stanbol server url
        String stanbolServer = sp.getStanbolServer();
        String chain = sp.getStanbolChain();
       
        // Stanbol entity dereference fields
        List<String> derefFields = sp.getDereferenceFields();       

        //ldpath prefix: Namespace URI
        Map<String,String> prefixMap = sp.getLdPathPrefixes();
        //ldpath fieldName:field definition 
        Map<String,String> ldpathFieldMap = sp.getLdPathFields();
        //entity property URI: document field
        Map<String,String> docFieldMap = sp.getDocumentFieldMapinngs();
        boolean isKeepAllEntityMetadata = sp.keepAllMetadata();
      
        stanbolFactory = new StanbolClientFactory(stanbolServer);

        // Extracting Content.
        long length = document.getBinaryLength();
        byte[] copy = IOUtils.toByteArray(document.getBinaryStream());
        String content = new String(copy);

        Set<String> uris = new HashSet<String>();
        //field URI: field values set
        Map<String, Set<String>> entityPropertyMap = new HashMap<String, Set<String>>();
        Enhancer enhancerClient = null;
        EnhancementStructure eRes = null;
        // Create a copy of Repository Document
        RepositoryDocument docCopy = document.duplicate();
        docCopy.setBinary(new ByteArrayInputStream(copy), length);
        
        LDPathProgram ldpathProgram = null;

        try
        {
            enhancerClient = stanbolFactory.createEnhancerClient();
            EnhancerParameters parameters = null;
            // ldpath program is given priority if it's set          
            if((ldpathFieldMap != null && !ldpathFieldMap.isEmpty()))
            {
                ldpathProgram = new LDPathProgram(); 
                for(String prefix: prefixMap.keySet())
                {
                    ldpathProgram.addNamespace(prefix, prefixMap.get(prefix));                
                }
                for(String ldpathField: ldpathFieldMap.keySet())
                {
                    if(ldpathField.indexOf(":") != -1)
                    {
                        int prefixIndex = ldpathField.indexOf(":");
                        String prefix = ldpathField.substring(0, prefixIndex);
                        //retrieving namespace for the field
                        String fieldName = ldpathField.substring(prefixIndex+1); 
                        ldpathProgram.addFieldDefinition(prefix, fieldName, ldpathFieldMap.get(ldpathField));
                    }
                    else
                    {       
                        ldpathProgram.addFieldDefinition(customLDPathPrefix,ldpathField, ldpathFieldMap.get(ldpathField));
                    }
                }  
                parameters = EnhancerParameters.builder().setChain(chain).setContent(content).setLDpathProgram(ldpathProgram.toString()).build();
            }
            else if (!derefFields.isEmpty())
            {
                parameters = EnhancerParameters.builder().setChain(chain).setContent(content).setDereferencingFields(
                        derefFields).build();
            }
            else
            {
                parameters = EnhancerParameters.builder().setChain(chain).setContent(content).build();
            }
            eRes = enhancerClient.enhance(parameters);

        }
        catch (StanbolServiceException | StanbolClientException e)
        {
            Logging.agents.error("Error occurred while performing Stanbol enhancement for document : " + documentURI, e);
            resultCode = "STANBOL_ENHANCEMENT_FAIL";
            description = e.getMessage();
        }
        finally
        {
            activities.recordActivity(new Long(startTime), ACTIVITY_ENHANCE, length, documentURI, resultCode,
                    description);

        }

        // processing the text annotations extracted by Stanbol
        for (TextAnnotation ta : eRes.getTextAnnotations())
        {
            Logging.agents.debug("Processing text annotation for content : " + ta.getUri());
            // need to disambiguate the entity-annotations returned
            for (EntityAnnotation ea : eRes.getEntityAnnotations(ta))
            {
                double confidence = ea.getConfidence();
                Logging.agents.debug("Processing entity annotation for content : " + ea.getUri() + " confidence : "
                        + confidence);
                if (confidence < DEFAULT_DISAMBIGUATION_SCORE)
                {
                    Logging.agents.debug("Confidence for the entity annotation is below the threshold, hence not processing the entity annotation");
                }
                else
                {
                    Entity entity = null;
                    uris.add(ea.getEntityReference());
                    // dereference the entity
                    entity = ea.getDereferencedEntity();
                    if (entity != null)
                    {                                              
                        //process LDPath fields  
                        if(ldpathProgram != null)
                        {
                            //process the ldpath fields in order to retrieve it from the entity
                            for(String ldpathField : ldpathFieldMap.keySet())
                            {
                                if(ldpathField.indexOf(":") != -1)
                                {                                   
                                    int prefixIndex = ldpathField.indexOf(":");
                                    String prefix = ldpathField.substring(0, prefixIndex);
                                    //retrieving namespace for the field
                                    String namespace =  prefixMap.get(prefix);
                                    String fieldName = ldpathField.substring(prefixIndex+1); 
                                    if(namespace != null)
                                    {
                                        if(!namespace.endsWith("/"))
                                        {
                                            ldpathField = namespace + "/" + fieldName;
                                        }
                                        else 
                                        {
                                            ldpathField = namespace + fieldName;
                                        }                                        
                                    }
                                    else 
                                    {
                                        ldpathField = customLDPathPrefix + "/" + fieldName;
                                    }                                   
                                }
                                else
                                {
                                    //no name space defined, hence adding the custom namespace as default namespace
                                    ldpathField = customLDPathPrefix + "/" + ldpathField;
                                }
                                //extracting field properties from the entity
                                Collection<String> entityPropValues = entity.getPropertyValues(ldpathField);
                                Set<String> propValues = entityPropertyMap.get(ldpathField);
                                if(propValues == null)
                                {
                                    propValues = new HashSet<String>();
                                }
                                propValues.addAll(entityPropValues);
                                entityPropertyMap.put(ldpathField, propValues);   
                            }
                            
                        }
                        
                        //processing dereference fields
                        if(derefFields != null && !derefFields.isEmpty())
                        {
                            for(String field : derefFields)
                            {                              
                                Collection<String> entityPropValues = entity.getPropertyValues(field);
                                Set<String> propValues = entityPropertyMap.get(field);
                                if(propValues == null)
                                {
                                    propValues = new HashSet<String>();
                                }
                                propValues.addAll(entityPropValues);
                                entityPropertyMap.put(field, propValues);                           
                            }    
                        }
                        else if(isKeepAllEntityMetadata)
                        {
                            //adding all entity properties to the entityPropertyMap 
                            Collection<String> entityProperties = entity.getProperties();
                            for(String field : entityProperties)
                            {
                                Collection<String> entityPropValues = entity.getPropertyValues(field);
                                Set<String> propValues = entityPropertyMap.get(field);
                                if(propValues == null)
                                {
                                    propValues = new HashSet<String>();
                                }
                                propValues.addAll(entityPropValues);
                                entityPropertyMap.put(field, propValues);  
                            }                                                       
                        }
                    
                    }
                }
            }
        }

        // Enrichment complete!
        docCopy.addField(ENTITY_URI_FIELD, uris.toArray(new String[uris.size()]));
        // adding all entity properties and values into the document for the defined destination field 
        for (String propertyURI : entityPropertyMap.keySet())
        {
            Set<String> propertyValues = entityPropertyMap.get(propertyURI);          
            String destFieldName = null;  
          
            if(docFieldMap!= null)
            {
                destFieldName = docFieldMap.get(propertyURI);                  
            }
            //if no destination field is defined, the entity property URI is added as the field   
            if(destFieldName == null)
            {
                destFieldName = propertyURI;
            }
            docCopy.addField(destFieldName, propertyValues.toArray(new String[propertyValues.size()]));
        }

        // Send new document downstream
        int rval = activities.sendDocument(documentURI, docCopy);
        resultCode = (rval == DOCUMENTSTATUS_ACCEPTED) ? "ACCEPTED" : "REJECTED";
        return rval;

    }

    /**
     * Obtain the name of the form check javascript method to call.
     * 
     * @param connectionSequenceNumber is the unique number of this connection within the job.
     * @return the name of the form check javascript method.
     */
    @Override
    public String getFormCheckJavascriptMethodName(int connectionSequenceNumber)
    {
        return "s" + connectionSequenceNumber + "_checkSpecification";
    }

    /**
     * Obtain the name of the form presave check javascript method to call.
     * 
     * @param connectionSequenceNumber is the unique number of this connection within the job.
     * @return the name of the form presave check javascript method.
     */
    @Override
    public String getFormPresaveCheckJavascriptMethodName(int connectionSequenceNumber)
    {
        return "s" + connectionSequenceNumber + "_checkSpecificationForSave";
    }

    /**
     * Output the specification header section. This method is called in the head section of a job page which has
     * selected a pipeline connection of the current type. Its purpose is to add the required tabs to the list, and to
     * output any javascript methods that might be needed by the job editing HTML.
     * 
     * @param out is the output to which any HTML should be sent.
     * @param locale is the preferred local of the output.
     * @param os is the current pipeline specification for this connection.
     * @param connectionSequenceNumber is the unique number of this connection within the job.
     * @param tabsArray is an array of tab names. Add to this array any tab names that are specific to the connector.
     */
    @Override
    public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification os,
            int connectionSequenceNumber, List<String> tabsArray) throws ManifoldCFException, IOException
    {
        Map<String, Object> paramMap = new HashMap<String, Object>();
        paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));

        tabsArray.add(Messages.getString(locale, "StanbolEnhancer.FieldMappingTabName"));
        fillInFieldMappingSpecificationMap(paramMap, os);
        Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_JS, paramMap);
    }

    /**
     * Output the specification body section. This method is called in the body section of a job page which has selected
     * a pipeline connection of the current type. Its purpose is to present the required form elements for editing. The
     * coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>,
     * and <form> tags. The name of the form is "editjob".
     * 
     * @param out is the output to which any HTML should be sent.
     * @param locale is the preferred local of the output.
     * @param os is the current pipeline specification for this job.
     * @param connectionSequenceNumber is the unique number of this connection within the job.
     * @param actualSequenceNumber is the connection within the job that has currently been selected.
     * @param tabName is the current tab name.
     */
    @Override
    public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification os, int connectionSequenceNumber,
            int actualSequenceNumber, String tabName) throws ManifoldCFException, IOException
    {
        Map<String, Object> paramMap = new HashMap<String, Object>();

        // Set the tab name
        paramMap.put("TABNAME", tabName);
        paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));
        paramMap.put("SELECTEDNUM", Integer.toString(actualSequenceNumber));

        // Fill in the field mapping tab data
        fillInFieldMappingSpecificationMap(paramMap, os);

        Messages.outputResourceWithVelocity(out, locale, EDIT_SPECIFICATION_FIELDMAPPING_HTML, paramMap);
    }

    /**
     * Process a specification post. This method is called at the start of job's edit or view page, whenever there is a
     * possibility that form data for a connection has been posted. Its purpose is to gather form information and modify
     * the transformation specification accordingly. The name of the posted form is "editjob".
     * 
     * @param variableContext contains the post data, including binary file-upload information.
     * @param locale is the preferred local of the output.
     * @param os is the current pipeline specification for this job.
     * @param connectionSequenceNumber is the unique number of this connection within the job.
     * @return null if all is well, or a string error message if there is an error that should prevent saving of the job
     *         (and cause a redirection to an error page).
     */
    @Override
    public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification os,
            int connectionSequenceNumber) throws ManifoldCFException
    {
        String seqPrefix = "s" + connectionSequenceNumber + "_";

        //added for attrib mappings
        String x;
        x = variableContext.getParameter(seqPrefix + "fieldmapping_count");
        if (x != null && x.length() > 0)
        {
            // About to gather the fieldmapping nodes, so get rid of the old ones.
            int i = 0;
            while (i < os.getChildCount())
            {
                SpecificationNode node = os.getChild(i);
                if (node.getType().equals(StanbolConfig.NODE_FIELDMAP))
//                        || node.getType().equals(StanbolConfig.NODE_KEEPMETADATA))
                    os.removeChild(i);
                else
                    i++;
            }
            int count = Integer.parseInt(x);
            i = 0;
            while (i < count)
            {
                String prefix = seqPrefix + "fieldmapping_";
                String suffix = "_" + Integer.toString(i);
                String op = variableContext.getParameter(prefix + "op" + suffix);
                if (op == null || !op.equals("Delete"))
                {
                    // Gather the fieldmap etc.
                    String source = variableContext.getParameter(prefix + "source" + suffix);                  
                    SpecificationNode node = new SpecificationNode(StanbolConfig.NODE_FIELDMAP);
                    node.setAttribute(StanbolConfig.ATTRIBUTE_SOURCE, source);
                    os.addChild(os.getChildCount(), node);
                }
                i++;
            }

            String addop = variableContext.getParameter(seqPrefix + "fieldmapping_op");
            if (addop != null && addop.equals("Add"))
            {
                String source = variableContext.getParameter(seqPrefix + "fieldmapping_source");               
                SpecificationNode node = new SpecificationNode(StanbolConfig.NODE_FIELDMAP);
                node.setAttribute(StanbolConfig.ATTRIBUTE_SOURCE, source);             
                os.addChild(os.getChildCount(), node);
            }
        }
        
        //ldpath prefix mappings
        String y;
        y = variableContext.getParameter(seqPrefix + "prefixmapping_count");
        if (y != null && y.length() > 0)
        {
            // About to gather the fieldmapping nodes, so get rid of the old ones.
            int i = 0;
            while (i < os.getChildCount())
            {
                SpecificationNode node = os.getChild(i);
                if (node.getType().equals(StanbolConfig.PREFIX_MAP))
                    os.removeChild(i);
                else
                    i++;
            }
            int count = Integer.parseInt(y);
            i = 0;
            while (i < count)
            {
                String prefix = seqPrefix + "prefixmapping_";
                String suffix = "_" + Integer.toString(i);
                String op = variableContext.getParameter(prefix + "op" + suffix);
                if (op == null || !op.equals("Delete"))
                {
                    // Gather the fieldmap etc.
                    String source = variableContext.getParameter(prefix + "source" + suffix);
                    String target = variableContext.getParameter(prefix + "target" + suffix);
                    if (target == null)
                    {
                        target = source;
                    }

                    SpecificationNode node = new SpecificationNode(StanbolConfig.PREFIX_MAP);
                    node.setAttribute(StanbolConfig.ATTRIBUTE_SOURCE, source);
                    node.setAttribute(StanbolConfig.ATTRIBUTE_TARGET, target);
                    os.addChild(os.getChildCount(), node);
                }
                i++;
            }

            String addop = variableContext.getParameter(seqPrefix + "prefixmapping_op");
            if (addop != null && addop.equals("Add"))
            {
                String source = variableContext.getParameter(seqPrefix + "prefixmapping_source");
                String target = variableContext.getParameter(seqPrefix + "prefixmapping_target");
                if (target == null)
                {
                    target = source;
                }
                SpecificationNode node = new SpecificationNode(StanbolConfig.PREFIX_MAP);
                node.setAttribute(StanbolConfig.ATTRIBUTE_SOURCE, source);
                node.setAttribute(StanbolConfig.ATTRIBUTE_TARGET, target);
                os.addChild(os.getChildCount(), node);
            }
        }
        
        
        //ldpath field mappings
        String z;
        z = variableContext.getParameter(seqPrefix + "ldpathfieldmapping_count");
        if (z != null && z.length() > 0)
        {
            // About to gather the ldpath field mapping nodes, so get rid of the old ones.
            int i = 0;
            while (i < os.getChildCount())
            {
                SpecificationNode node = os.getChild(i);
                if (node.getType().equals(StanbolConfig.LDPATH_FIELD_MAP))
                    os.removeChild(i);
                else
                    i++;
            }
            int count = Integer.parseInt(z);
            i = 0;
            while (i < count)
            {
                String prefix = seqPrefix + "ldpathfieldmapping_";
                String suffix = "_" + Integer.toString(i);
                String op = variableContext.getParameter(prefix + "op" + suffix);
                if (op == null || !op.equals("Delete"))
                {
                    // Gather the fieldmap etc.
                    String source = variableContext.getParameter(prefix + "source" + suffix);
                    String target = variableContext.getParameter(prefix + "target" + suffix);
                    if (target == null)
                    {
                        target = source;
                    }

                    SpecificationNode node = new SpecificationNode(StanbolConfig.LDPATH_FIELD_MAP);
                    node.setAttribute(StanbolConfig.ATTRIBUTE_SOURCE, source);
                    node.setAttribute(StanbolConfig.ATTRIBUTE_TARGET, target);
                    os.addChild(os.getChildCount(), node);
                }
                i++;
            }

            String addop = variableContext.getParameter(seqPrefix + "ldpathfieldmapping_op");
            if (addop != null && addop.equals("Add"))
            {
                String source = variableContext.getParameter(seqPrefix + "ldpathfieldmapping_source");
                String target = variableContext.getParameter(seqPrefix + "ldpathfieldmapping_target");
                if (target == null)
                {
                    target = source;
                }
                SpecificationNode node = new SpecificationNode(StanbolConfig.LDPATH_FIELD_MAP);
                node.setAttribute(StanbolConfig.ATTRIBUTE_SOURCE, source);
                node.setAttribute(StanbolConfig.ATTRIBUTE_TARGET, target);
                os.addChild(os.getChildCount(), node);
            }
        }
        
        //doc field mappings
        String n;
        n = variableContext.getParameter(seqPrefix + "docfieldmapping_count");
        if (n != null && n.length() > 0)
        {
            // About to gather the document field mapping nodes, so get rid of the old ones.
            int i = 0;
            while (i < os.getChildCount())
            {
                SpecificationNode node = os.getChild(i);
                if (node.getType().equals(StanbolConfig.DOC_FIELD_MAP))
                    os.removeChild(i);
                else
                    i++;
            }
            int count = Integer.parseInt(n);
            i = 0;
            while (i < count)
            {
                String prefix = seqPrefix + "docfieldmapping_";
                String suffix = "_" + Integer.toString(i);
                String op = variableContext.getParameter(prefix + "op" + suffix);
                if (op == null || !op.equals("Delete"))
                {
                    // Gather the fieldmap etc.
                    String source = variableContext.getParameter(prefix + "source" + suffix);
                    String target = variableContext.getParameter(prefix + "target" + suffix);
                    if (target == null)
                    {
                        target = source;
                    }
                    
                    SpecificationNode node = new SpecificationNode(StanbolConfig.DOC_FIELD_MAP);
                    node.setAttribute(StanbolConfig.ATTRIBUTE_SOURCE, source);
                    node.setAttribute(StanbolConfig.ATTRIBUTE_TARGET, target);
                    os.addChild(os.getChildCount(), node);
                }
                i++;
            }

            String addop = variableContext.getParameter(seqPrefix + "docfieldmapping_op");
            if (addop != null && addop.equals("Add"))
            {
                String source = variableContext.getParameter(seqPrefix + "docfieldmapping_source");
                String target = variableContext.getParameter(seqPrefix + "docfieldmapping_target");
                if (target == null)
                {
                    target = source;
                }
                               
                SpecificationNode node = new SpecificationNode(StanbolConfig.DOC_FIELD_MAP);
                node.setAttribute(StanbolConfig.ATTRIBUTE_SOURCE, source);
                node.setAttribute(StanbolConfig.ATTRIBUTE_TARGET, target);
                os.addChild(os.getChildCount(), node);
            }
        }
        
        String stanbolURLValue = variableContext.getParameter(seqPrefix + "stanbol_url");
        if (stanbolURLValue == null || stanbolURLValue.equalsIgnoreCase(""))
            stanbolURLValue = STANBOL_ENDPOINT;

        SpecificationNode serverNode = new SpecificationNode(StanbolConfig.STANBOL_SERVER_VALUE);
        serverNode.setAttribute(StanbolConfig.ATTRIBUTE_VALUE, stanbolURLValue);
        os.addChild(os.getChildCount(), serverNode);

        String stanbolChainValue = variableContext.getParameter(seqPrefix + "stanbol_chain");
        if (stanbolChainValue == null || stanbolChainValue.equalsIgnoreCase(""))
            stanbolChainValue = STANBOL_ENHANCEMENT_CHAIN;

        SpecificationNode chainNode = new SpecificationNode(StanbolConfig.STANBOL_CHAIN_VALUE);
        chainNode.setAttribute(StanbolConfig.ATTRIBUTE_VALUE, stanbolChainValue);
        os.addChild(os.getChildCount(), chainNode);
        
        // Gather the keep all metadata parameter to be the last one
        String keepAll = variableContext.getParameter(seqPrefix + "keepallmetadata");
        SpecificationNode keepAllDataNode = new SpecificationNode(StanbolConfig.NODE_KEEPMETADATA);       
        if (keepAll != null)
        {
          keepAllDataNode.setAttribute(StanbolConfig.ATTRIBUTE_VALUE, keepAll);
        }
        else
        {
          keepAllDataNode.setAttribute(StanbolConfig.ATTRIBUTE_VALUE, "false");
        }
        os.addChild(os.getChildCount(), keepAllDataNode);

        return null;
    }

    /**
     * View specification. This method is called in the body section of a job's view page. Its purpose is to present the
     * pipeline specification information to the user. The coder can presume that the HTML that is output from this
     * configuration will be within appropriate <html> and <body> tags.
     * 
     * @param out is the output to which any HTML should be sent.
     * @param locale is the preferred local of the output.
     * @param connectionSequenceNumber is the unique number of this connection within the job.
     * @param os is the current pipeline specification for this job.
     */
    @Override
    public void viewSpecification(IHTTPOutput out, Locale locale, Specification os, int connectionSequenceNumber)
            throws ManifoldCFException, IOException
    {
        Map<String, Object> paramMap = new HashMap<String, Object>();
        paramMap.put("SEQNUM", Integer.toString(connectionSequenceNumber));

        // Fill in the map with data from all tabs
        fillInFieldMappingSpecificationMap(paramMap, os);
        Messages.outputResourceWithVelocity(out, locale, VIEW_SPECIFICATION_HTML, paramMap);

    }

    protected static void fillInFieldMappingSpecificationMap(Map<String, Object> paramMap, Specification os)
    {
        // Prep for field mappings
        List<Map<String,String>> dereferenceFields = new ArrayList<Map<String,String>>();
        //List<prefix:prefixURI>
        List<Map<String,String>> prefixMappings = new ArrayList<Map<String,String>>();
        //List<ldpathField:fieldURI>
        List<Map<String,String>> ldpathFieldsMappings = new ArrayList<Map<String,String>>();
        //List<EntityProperty:Doc Field>
        List<Map<String,String>> docFieldMappings = new ArrayList<Map<String,String>>();


        // adding default Stanbol parameters to the the map
        String server = STANBOL_ENDPOINT;
        String chain = STANBOL_ENHANCEMENT_CHAIN;
        String keepAllMetadataValue = "true";
        
        for (int i = 0; i < os.getChildCount(); i++)
        {
            SpecificationNode sn = os.getChild(i);
            if (sn.getType().equals(StanbolConfig.NODE_FIELDMAP))
            {
                String source = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_SOURCE);

                Map<String,String> fieldMapping = new HashMap<String,String>();
                fieldMapping.put("SOURCE", source);
                dereferenceFields.add(fieldMapping);
            }
            else if (sn.getType().equals(StanbolConfig.STANBOL_SERVER_VALUE))
            {
                server = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_VALUE);
            }
            else if (sn.getType().equals(StanbolConfig.STANBOL_CHAIN_VALUE))
            {
                chain = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_VALUE);

            }            
            else if (sn.getType().equals(StanbolConfig.PREFIX_MAP))
            {
                String source = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_SOURCE);
                String target = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_TARGET);

                Map<String, String> prefixMapping = new HashMap<String, String>();
                prefixMapping.put("SOURCE", source);
                prefixMapping.put("TARGET", target);
                prefixMappings.add(prefixMapping);
            }
            else if (sn.getType().equals(StanbolConfig.LDPATH_FIELD_MAP))
            {
                String source = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_SOURCE);
                String target = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_TARGET);

                Map<String, String> ldpathFieldsMapping = new HashMap<String, String>();
                ldpathFieldsMapping.put("SOURCE", source);
                ldpathFieldsMapping.put("TARGET", target);
                ldpathFieldsMappings.add(ldpathFieldsMapping);
            }
            else if (sn.getType().equals(StanbolConfig.DOC_FIELD_MAP))
            {
                String source = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_SOURCE);
                String target = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_TARGET);

                Map<String, String> docFieldsMapping = new HashMap<String, String>();
                docFieldsMapping.put("SOURCE", source);
                docFieldsMapping.put("TARGET", target);
                docFieldMappings.add(docFieldsMapping);
            }
            else if (sn.getType().equals(StanbolConfig.NODE_KEEPMETADATA))
            {
              keepAllMetadataValue = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_VALUE);
            }
        }

        paramMap.put("STANBOL_SERVER", server);
        paramMap.put("STANBOL_CHAIN", chain);
        paramMap.put("FIELDMAPPINGS", dereferenceFields);
        
        //ldpath related params
        paramMap.put("PREFIXMAPPINGS", prefixMappings);
        paramMap.put("LDPATHFIELDMAPPINGS", ldpathFieldsMappings);
        
        paramMap.put("DOCMAPPINGS", docFieldMappings);
        paramMap.put("KEEPALLMETADATA",keepAllMetadataValue);
    }

    
    protected static class SpecPacker
    {
        private final String stanbolServer;
        private final String stanbolChain;
        private final List<String> dereferenceFields = new ArrayList<String>();
        
        //prefix:prefix URI
        private final Map<String,String> ldPathPrefixes = new HashMap<String, String>();
        //field name:field definition/URI
        private final Map<String, String> ldPathFields = new HashMap<String, String>();
        //document field mappings
        private final Map<String,String> documentFieldMapinngs = new HashMap<String, String>();
        private final boolean keepAllMetadata;

 
        public SpecPacker(Specification os)
        {
            String serverURL = STANBOL_ENDPOINT;
            String stanbolChain = STANBOL_ENHANCEMENT_CHAIN;
            boolean keepAllMetadata = false;
            
            for (int i = 0; i < os.getChildCount(); i++)
            {
                SpecificationNode sn = os.getChild(i);

                if (sn.getType().equals(StanbolConfig.STANBOL_SERVER_VALUE))
                {
                    serverURL = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_VALUE);

                }
                else if (sn.getType().equals(StanbolConfig.STANBOL_CHAIN_VALUE))
                {
                    stanbolChain = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_VALUE);

                }
                // adding deref fields
                else if (sn.getType().equals(StanbolConfig.NODE_FIELDMAP))
                {
                    String derefField = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_SOURCE);
                    dereferenceFields.add(derefField);

                }
                // adding ldpath prefixes
                else if (sn.getType().equals(StanbolConfig.PREFIX_MAP))
                {
                    String source = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_SOURCE);
                    String target = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_TARGET);
                    if (target == null)
                    {
                        target = source;
                    }
                    ldPathPrefixes.put(source, target);
                }
                // adding ldpath fields
                else if (sn.getType().equals(StanbolConfig.LDPATH_FIELD_MAP))
                {
                    String source = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_SOURCE);
                    String target = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_TARGET);
                    if (target == null)
                    {
                        target = source;
                    }
                    ldPathFields.put(source, target);
                }

                // adding document field mappings
                else if (sn.getType().equals(StanbolConfig.DOC_FIELD_MAP))
                {
                    String source = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_SOURCE);
                    String target = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_TARGET);
                    if (target == null)
                    {
                        target = source;
                    }
                    documentFieldMapinngs.put(source, target);
                }
                else if(sn.getType().equals(StanbolConfig.NODE_KEEPMETADATA)) 
                {
                    String value = sn.getAttributeValue(StanbolConfig.ATTRIBUTE_VALUE);
                    keepAllMetadata = Boolean.parseBoolean(value);
                }

            }
            this.stanbolServer = serverURL;
            this.stanbolChain = stanbolChain;
            this.keepAllMetadata = keepAllMetadata;
        }

        public String toPackedString()
        {
            StringBuilder sb = new StringBuilder();

            int i;
            packList(sb, dereferenceFields, '+');

            final String[] prefixArray = new String[ldPathPrefixes.size()];
            i = 0;
            for (String source : ldPathPrefixes.keySet()) 
            {
              prefixArray[i++] = source;
            }
            Arrays.sort(prefixArray);
            
            List<String> prefixMappings = new ArrayList<String>();
            String[] prefixList = new String[2];
            for (String source : prefixArray)
            {
                String target = ldPathPrefixes.get(source);
                StringBuilder localBuffer = new StringBuilder();
                prefixList[0] = source;
                prefixList[1] = target;
                packFixedList(localBuffer, prefixList, ':');
                prefixMappings.add(localBuffer.toString());
            }
            packList(sb, prefixMappings, '+');

            final String[] ldpathFieldArray = new String[ldPathFields.size()];
            i = 0;
            for(String source : ldPathFields.keySet())
            {
                ldpathFieldArray[i++] = source;
            }
            Arrays.sort(ldpathFieldArray);
            
            List<String> ldpathFieldMappings = new ArrayList<String>();
            String[] ldpathFieldList = new String[2];
            for (String source : ldpathFieldArray)
            {
                String target = ldPathFields.get(source);
                StringBuilder localBuffer = new StringBuilder();
                ldpathFieldList[0] = source;
                ldpathFieldList[1] = target;
                packFixedList(localBuffer, ldpathFieldList, ':');
                ldpathFieldMappings.add(localBuffer.toString());
            }
            packList(sb, ldpathFieldMappings, '+');

            //doc field mappings
            final String[] docFieldArray = new String[documentFieldMapinngs.size()];
            i = 0;
            for(String source : documentFieldMapinngs.keySet())
            {
                docFieldArray[i++] = source;
            }
            Arrays.sort(docFieldArray);
            List<String> docFieldMappings = new ArrayList<String>();            
            String[] docFieldList = new String[2];
            for (String source : docFieldArray)
            {
                String target = documentFieldMapinngs.get(source);
                StringBuilder localBuffer = new StringBuilder();
                docFieldList[0] = source;
                docFieldList[1] = target;
                packFixedList(localBuffer, docFieldList, ':');
                docFieldMappings.add(localBuffer.toString());
            }
            packList(sb, docFieldMappings, '+');
            
            if (stanbolServer != null)
            {
                sb.append('+');
                sb.append(stanbolServer);
            }
            else
            {
                sb.append('-');
            }
            if (stanbolServer != null)
            {
                sb.append('+');
                sb.append(stanbolServer);
            }
            else
            {
                sb.append('-');
            }
            if (keepAllMetadata)
            {    
                sb.append('+');
            }    
            else
            {    
                sb.append('-');
            }    
            return sb.toString();
        }

        public String getStanbolServer()
        {
            return stanbolServer;
        }

        public String getStanbolChain()
        {
            return stanbolChain;
        }

        public List<String> getDereferenceFields()
        {
            return dereferenceFields;
        }

        public Map<String,String> getLdPathPrefixes()
        {
            return ldPathPrefixes;
        }

        public Map<String, String> getLdPathFields()
        {
            return ldPathFields;
        }

        public Map<String,String> getDocumentFieldMapinngs()
        {
            return documentFieldMapinngs;
        }

        public boolean keepAllMetadata() 
        {
            return keepAllMetadata;
        }
          
    }
    
} 