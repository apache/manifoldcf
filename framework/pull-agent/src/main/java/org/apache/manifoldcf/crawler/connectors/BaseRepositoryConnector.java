/* $Id: BaseRepositoryConnector.java 996524 2010-09-13 13:38:01Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;

import java.io.*;
import java.util.*;

/** This base class describes an instance of a connection between a repository and ManifoldCF's
* standard "pull" ingestion agent.
*
* Each instance of this interface is used in only one thread at a time.  Connection Pooling
* on these kinds of objects is performed by the factory which instantiates repository connectors
* from symbolic names and config parameters, and is pooled by these parameters.  That is, a pooled connector
* handle is used only if all the connection parameters for the handle match.
*
* Implementers of this interface should provide a default constructor which has this signature:
*
* xxx();
*
* Connectors are either configured or not.  If configured, they will persist in a pool, and be
* reused multiple times.  Certain methods of a connector may be called before the connector is
* configured.  This includes basically all methods that permit inspection of the connector's
* capabilities.  The complete list is:
*
*
* The purpose of the repository connector is to allow documents to be fetched from the repository.
*
* Each repository connector describes a set of documents that are known only to that connector.
* It therefore establishes a space of document identifiers.  Each connector will only ever be
* asked to deal with identifiers that have in some way originated from the connector.
*
* Documents are fetched in three stages.  First, the getDocuments() method is called in the connector
* implementation.  This returns a set of document identifiers.  The document identifiers are used to
* obtain the current document version strings in the second stage, using the getDocumentVersions() method.
* The last stage is processDocuments(), which queues up any additional documents needed, and also ingests.
* This method will not be called if the document version seems to indicate that no document change took
* place.
*/
public abstract class BaseRepositoryConnector extends org.apache.manifoldcf.core.connector.BaseConnector implements IRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id: BaseRepositoryConnector.java 996524 2010-09-13 13:38:01Z kwright $";

  /** Tell the world what model this connector uses for getDocumentIdentifiers().
  * This must return a model value as specified above.
  *@return the model type value.
  */
  @Override
  public int getConnectorModel()
  {
    // Return the simplest model - full everything
    return MODEL_ALL;
  }

  /** Return the list of activities that this connector supports (i.e. writes into the log).
  *@return the list.
  */
  @Override
  public String[] getActivitiesList()
  {
    return new String[0];
  }

  /** Return the list of relationship types that this connector recognizes.
  *@return the list.
  */
  @Override
  public String[] getRelationshipTypes()
  {
    // The base situation is that there are no relationships.
    return new String[0];
  }

  /** Get the bin name strings for a document identifier.  The bin name describes the queue to which the
  * document will be assigned for throttling purposes.  Throttling controls the rate at which items in a
  * given queue are fetched; it does not say anything about the overall fetch rate, which may operate on
  * multiple queues or bins.
  * For example, if you implement a web crawler, a good choice of bin name would be the server name, since
  * that is likely to correspond to a real resource that will need real throttle protection.
  *@param documentIdentifier is the document identifier.
  *@return the set of bin names.  If an empty array is returned, it is equivalent to there being no request
  * rate throttling available for this identifier.
  */
  @Override
  public String[] getBinNames(String documentIdentifier)
  {
    // Base version has one bin for all documents.  Use empty string for this since "*" would make
    // regexps be difficult to write.
    return new String[]{""};
  }

  /** Request arbitrary connector information.
  * This method is called directly from the API in order to allow API users to perform any one of several connector-specific
  * queries.
  *@param output is the response object, to be filled in by this method.
  *@param command is the command, which is taken directly from the API request.
  *@return true if the resource is found, false if not.  In either case, output may be filled in.
  */
  @Override
  public boolean requestInfo(Configuration output, String command)
    throws ManifoldCFException
  {
    return false;
  }

  /** Queue "seed" documents.  Seed documents are the starting places for crawling activity.  Documents
  * are seeded when this method calls appropriate methods in the passed in ISeedingActivity object.
  *
  * This method can choose to find repository changes that happen only during the specified time interval.
  * The seeds recorded by this method will be viewed by the framework based on what the
  * getConnectorModel() method returns.
  *
  * It is not a big problem if the connector chooses to create more seeds than are
  * strictly necessary; it is merely a question of overall work required.
  *
  * The end time and seeding version string passed to this method may be interpreted for greatest efficiency.
  * For continuous crawling jobs, this method will
  * be called once, when the job starts, and at various periodic intervals as the job executes.
  *
  * When a job's specification is changed, the framework automatically resets the seeding version string to null.  The
  * seeding version string may also be set to null on each job run, depending on the connector model returned by
  * getConnectorModel().
  *
  * Note that it is always ok to send MORE documents rather than less to this method.
  * The connector will be connected before this method can be called.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is a document specification (that comes from the job).
  *@param seedTime is the end of the time range of documents to consider, exclusive.
  *@param lastSeedVersionString is the last seeding version string for this job, or null if the job has no previous seeding version string.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@return an updated seeding version string, to be stored with the job.
  */
  @Override
  public String addSeedDocuments(ISeedingActivity activities, Specification spec,
    String lastSeedVersion, long seedTime, int jobMode)
    throws ManifoldCFException, ServiceInterruption
  {
    return "";
  }

  /** Process a set of documents.
  * This is the method that should cause each document to be fetched, processed, and the results either added
  * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
  * The document specification allows this class to filter what is done based on the job.
  * The connector will be connected before this method can be called.
  *@param documentIdentifiers is the set of document identifiers to process.
  *@param statuses are the currently-stored document versions for each document in the set of document identifiers
  * passed in above.
  *@param activities is the interface this method should use to queue up new document references
  * and ingest documents.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
  */
  @Override
  public void processDocuments(String[] documentIdentifiers, IExistingVersions statuses, Specification spec,
    IProcessActivity activities, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption
  {
    // The backwards-compatible base implementation does the following:
    // (1) Uses the deprecated methods to obtain a set of version information
    // (2) Based on the provided version information, determines whether processing is required
    // (3) Uses deprecated methods to process documents
    // (4) Releases document versions

    // We need to get the old version strings together in order to use the deprecated methods
    String[] oldVersions = new String[documentIdentifiers.length];
    for (int i = 0; i < oldVersions.length; i++)
    {
      oldVersions[i] = statuses.getIndexedVersionString(documentIdentifiers[i]);
    }
    DocumentVersions dv = new DocumentVersions();
    getDocumentVersions(dv,documentIdentifiers,oldVersions,activities,spec,jobMode,usesDefaultAuthority);
    try
    {
      // Next, we determine what part of the set of documents were unchanged, and what part we need to refetch.
      Set<String> fetchDocuments = new HashSet<String>();
      Set<String> scanDocuments = new HashSet<String>();
      for (int i = 0; i < documentIdentifiers.length; i++)
      {
        String documentIdentifier = documentIdentifiers[i];
        VersionContext vc = dv.getDocumentVersion(documentIdentifier);
        if (vc != null)
        {
          if (dv.isAlwaysRefetch(documentIdentifier) || activities.checkDocumentNeedsReindexing(documentIdentifier,vc.getVersionString()))
          {
            // These documents need reprocessing
            fetchDocuments.add(documentIdentifier);
          }
          else
          {
            // Document is unchanged.  We leave it up to the framework to decide what that means.
          }
          scanDocuments.add(documentIdentifier);
        }
        else
        {
          // These documents must go away permanently
          activities.deleteDocument(documentIdentifier);
        }
      }

      // Construct the appropriate data to call processDocuments() with
      String[] processIDs = new String[scanDocuments.size()];
      boolean[] scanOnly = new boolean[scanDocuments.size()];
      int index = 0;
      for (int i = 0; i < documentIdentifiers.length; i++)
      {
        String documentIdentifier = documentIdentifiers[i];
        if (scanDocuments.contains(documentIdentifier))
        {
          processIDs[index] = documentIdentifier;
          scanOnly[index] = !fetchDocuments.contains(documentIdentifier);
          index++;
        }
      }
      processDocuments(processIDs,dv,activities,scanOnly,jobMode);
      
    }
    finally
    {
      // Release document versions
      releaseDocumentVersions(documentIdentifiers,dv);
    }
  }

  /** Get document versions given an array of document identifiers.
  * This method is called for EVERY document that is considered. It is therefore important to perform
  * as little work as possible here.
  * The connector will be connected before this method can be called.
  *@param documentVersions is the versions object, to be filled in by this method.
  *@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
  *@param oldVersions is the corresponding array of version strings that have been saved for the document identifiers.
  *   A null value indicates that this is a first-time fetch, while an empty string indicates that the previous document
  *   had an empty version string.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is the current document specification for the current job.  If there is a dependency on this
  * specification, then the version string should include the pertinent data, so that reingestion will occur
  * when the specification changes.  This is primarily useful for metadata.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
  */
  public void getDocumentVersions(
    DocumentVersions documentVersions,
    String[] documentIdentifiers, String[] oldVersions,
    IVersionActivity activities,
    Specification spec, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption
  {
    String[] rval = getDocumentVersions(documentIdentifiers,oldVersions,activities,
      spec,jobMode,usesDefaultAuthority);
    for (int i = 0; i < rval.length; i++)
    {
      if (rval[i] != null)
      {
        documentVersions.setDocumentVersion(documentIdentifiers[i],new VersionContext(rval[i],params,spec));
        if (rval[i].length() == 0)
          documentVersions.alwaysRefetch(documentIdentifiers[i]);
      }
    }
  }

  /** Get document versions given an array of document identifiers.
  * This method is called for EVERY document that is considered. It is
  * therefore important to perform as little work as possible here.
  *@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
  *@param oldVersions is the corresponding array of version strings that have been saved for the document identifiers.
  *   A null value indicates that this is a first-time fetch, while an empty string indicates that the previous document
  *   had an empty version string.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is the current document specification for the current job.  If there is a dependency on this
  * specification, then the version string should include the pertinent data, so that reingestion will occur
  * when the specification changes.  This is primarily useful for metadata.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
  *@return the corresponding version strings, with null in the places where the document no longer exists.
  * Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
  * will always be processed.
  */
  public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activities,
    Specification spec, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption
  {
    return getDocumentVersions(documentIdentifiers,oldVersions,activities,(DocumentSpecification)spec,jobMode,usesDefaultAuthority);
  }

  public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activities,
    DocumentSpecification spec, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption
  {
    return getDocumentVersions(documentIdentifiers,oldVersions,activities,spec,jobMode);
  }

  /** Get document versions given an array of document identifiers.
  * This method is called for EVERY document that is considered. It is
  * therefore important to perform as little work as possible here.
  *@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
  *@param oldVersions is the corresponding array of version strings that have been saved for the document identifiers.
  *   A null value indicates that this is a first-time fetch, while an empty string indicates that the previous document
  *   had an empty version string.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is the current document specification for the current job.  If there is a dependency on this
  * specification, then the version string should include the pertinent data, so that reingestion will occur
  * when the specification changes.  This is primarily useful for metadata.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@return the corresponding version strings, with null in the places where the document no longer exists.
  * Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
  * will always be processed.
  */
  public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activities,
    DocumentSpecification spec, int jobMode)
    throws ManifoldCFException, ServiceInterruption
  {
    return getDocumentVersions(documentIdentifiers,oldVersions,activities,spec);
  }

  /** Get document versions given an array of document identifiers.
  * This method is called for EVERY document that is considered. It is
  * therefore important to perform as little work as possible here.
  *@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
  *@param oldVersions is the corresponding array of version strings that have been saved for the document identifiers.
  *   A null value indicates that this is a first-time fetch, while an empty string indicates that the previous document
  *   had an empty version string.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is the current document specification for the current job.  If there is a dependency on this
  * specification, then the version string should include the pertinent data, so that reingestion will occur
  * when the specification changes.  This is primarily useful for metadata.
  *@return the corresponding version strings, with null in the places where the document no longer exists.
  * Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
  * will always be processed.
  */
  public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activities, DocumentSpecification spec)
    throws ManifoldCFException, ServiceInterruption
  {
    return getDocumentVersions(documentIdentifiers,activities,spec);
  }

  /** The long version of getDocumentIdentifiers.
  * Get document versions given an array of document identifiers.
  * This method is called for EVERY document that is considered. It is
  * therefore important to perform as little work as possible here.
  *@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is the current document specification for the current job.  If there is a dependency on this
  * specification, then the version string should include the pertinent data, so that reingestion will occur
  * when the specification changes.  This is primarily useful for metadata.
  *@return the corresponding version strings, with null in the places where the document no longer exists.
  * Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
  * will always be processed.
  */
  public String[] getDocumentVersions(String[] documentIdentifiers, IVersionActivity activities, DocumentSpecification spec)
    throws ManifoldCFException, ServiceInterruption
  {
    return getDocumentVersions(documentIdentifiers,spec);
  }

  /** The short version of getDocumentVersions.
  * Get document versions given an array of document identifiers.
  * This method is called for EVERY document that is considered. It is
  * therefore important to perform as little work as possible here.
  *@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
  *@param spec is the current document specification for the current job.  If there is a dependency on this
  * specification, then the version string should include the pertinent data, so that reingestion will occur
  * when the specification changes.  This is primarily useful for metadata.
  *@return the corresponding version strings, with null in the places where the document no longer exists.
  * Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
  * will always be processed.
  */
  public String[] getDocumentVersions(String[] documentIdentifiers, DocumentSpecification spec)
    throws ManifoldCFException, ServiceInterruption
  {
    // Return unknown versions
    String[] rval = new String[documentIdentifiers.length];
    int i = 0;
    while (i < rval.length)
    {
      rval[i++] = "";
    }
    return rval;
  }

  /** Free a set of documents.  This method is called for all documents whose versions have been fetched using
  * the getDocumentVersions() method, including those that returned null versions.  It may be used to free resources
  * committed during the getDocumentVersions() method.  It is guaranteed to be called AFTER any calls to
  * processDocuments() for the documents in question.
  * The connector will be connected before this method can be called.
  *@param documentIdentifiers is the set of document identifiers.
  *@param versions is the corresponding set of version strings (individual identifiers may have no version).
  */
  public void releaseDocumentVersions(String[] documentIdentifiers, DocumentVersions versions)
    throws ManifoldCFException
  {
    String[] versionStrings = new String[documentIdentifiers.length];
    for (int i = 0; i < versionStrings.length; i++)
    {
      VersionContext vc = versions.getDocumentVersion(documentIdentifiers[i]);
      boolean alwaysFetch = versions.isAlwaysRefetch(documentIdentifiers[i]);
      if (alwaysFetch)
        versionStrings[i] = "";
      else if (vc == null)
        versionStrings[i] = null;
      else
        versionStrings[i] = vc.getVersionString();
    }
    releaseDocumentVersions(documentIdentifiers,versionStrings);
  }

  /** Free a set of documents.  This method is called for all documents whose versions have been fetched using
  * the getDocumentVersions() method, including those that returned null versions.  It may be used to free resources
  * committed during the getDocumentVersions() method.  It is guaranteed to be called AFTER any calls to
  * processDocuments() for the documents in question.
  *@param documentIdentifiers is the set of document identifiers.
  *@param versions is the corresponding set of version identifiers (individual identifiers may be null).
  */
  public void releaseDocumentVersions(String[] documentIdentifiers, String[] versions)
    throws ManifoldCFException
  {
    // Base implementation does nothing
  }

  /** Get the maximum number of documents to amalgamate together into one batch, for this connector.
  *@return the maximum number. 0 indicates "unlimited".
  */
  @Override
  public int getMaxDocumentRequest()
  {
    // Base implementation does one at a time.
    return 1;
  }

  /** Process a set of documents.
  * This is the method that should cause each document to be fetched, processed, and the results either added
  * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
  * The document specification allows this class to filter what is done based on the job.
  * The connector will be connected before this method can be called.
  *@param documentIdentifiers is the set of document identifiers to process.
  *@param versions are the version strings returned by getDocumentVersions() above.
  *@param activities is the interface this method should use to queue up new document references
  * and ingest documents.
  *@param scanOnly is an array corresponding to the document identifiers.  It is set to true to indicate when the processing
  * should only find other references, and should not actually call the ingestion methods.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  */
  public void processDocuments(String[] documentIdentifiers, DocumentVersions versions, IProcessActivity activities,
    boolean[] scanOnly, int jobMode)
    throws ManifoldCFException, ServiceInterruption
  {
    Specification spec = null;
    String[] versionStrings = new String[documentIdentifiers.length];
    for (int i = 0; i < versionStrings.length; i++)
    {
      VersionContext vc = versions.getDocumentVersion(documentIdentifiers[i]);
      if (vc != null)
        spec = vc.getSpecification();
      boolean alwaysFetch = versions.isAlwaysRefetch(documentIdentifiers[i]);
      if (alwaysFetch)
        versionStrings[i] = "";
      else if (vc == null)
        versionStrings[i] = null;
      else
        versionStrings[i] = vc.getVersionString();
    }
    if (spec != null)
      processDocuments(documentIdentifiers,versionStrings,activities,spec,scanOnly,jobMode);
  }

  /** Process a set of documents.
  * This is the method that should cause each document to be fetched, processed, and the results either added
  * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
  * The document specification allows this class to filter what is done based on the job.
  *@param documentIdentifiers is the set of document identifiers to process.
  *@param versions is the corresponding document versions to process, as returned by getDocumentVersions() above.
  *       The implementation may choose to ignore this parameter and always process the current version.
  *@param activities is the interface this method should use to queue up new document references
  * and ingest documents.
  *@param spec is the document specification.
  *@param scanOnly is an array corresponding to the document identifiers.  It is set to true to indicate when the processing
  * should only find other references, and should not actually call the ingestion methods.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  */
  public void processDocuments(String[] documentIdentifiers, String[] versions, IProcessActivity activities,
    Specification spec, boolean[] scanOnly, int jobMode)
    throws ManifoldCFException, ServiceInterruption
  {
    processDocuments(documentIdentifiers,versions,activities,(DocumentSpecification)spec,scanOnly,jobMode);
  }
  
  public void processDocuments(String[] documentIdentifiers, String[] versions, IProcessActivity activities,
    DocumentSpecification spec, boolean[] scanOnly, int jobMode)
    throws ManifoldCFException, ServiceInterruption
  {
    processDocuments(documentIdentifiers,versions,activities,spec,scanOnly);
  }

  /** Process a set of documents.
  * This is the method that should cause each document to be fetched, processed, and the results either added
  * to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
  * The document specification allows this class to filter what is done based on the job.
  *@param documentIdentifiers is the set of document identifiers to process.
  *@param versions is the corresponding document versions to process, as returned by getDocumentVersions() above.
  *       The implementation may choose to ignore this parameter and always process the current version.
  *@param activities is the interface this method should use to queue up new document references
  * and ingest documents.
  *@param spec is the document specification.
  *@param scanOnly is an array corresponding to the document identifiers.  It is set to true to indicate when the processing
  * should only find other references, and should not actually call the ingestion methods.
  */
  public void processDocuments(String[] documentIdentifiers, String[] versions, IProcessActivity activities,
    DocumentSpecification spec, boolean[] scanOnly)
    throws ManifoldCFException, ServiceInterruption
  {
    // Does nothing; override to make something happen
  }

  // UI support methods.
  //
  // These support methods come in two varieties.  The first bunch is involved in setting up connection configuration information.  The second bunch
  // is involved in presenting and editing document specification information for a job.  The two kinds of methods are accordingly treated differently,
  // in that the first bunch cannot assume that the current connector object is connected, while the second bunch can.  That is why the first bunch
  // receives a thread context argument for all UI methods, while the second bunch does not need one (since it has already been applied via the connect()
  // method, above).

  /** Obtain the name of the form check javascript method to call.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return the name of the form check javascript method.
  */
  public String getFormCheckJavascriptMethodName(int connectionSequenceNumber)
  {
    return "checkSpecification";
  }

  /** Obtain the name of the form presave check javascript method to call.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return the name of the form presave check javascript method.
  */
  public String getFormPresaveCheckJavascriptMethodName(int connectionSequenceNumber)
  {
    return "checkSpecificationForSave";
  }

  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a repository connection of the
  * current type.  Its purpose is to add the required tabs to the list, and to output any javascript methods
  * that might be needed by the job editing HTML.
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    outputSpecificationHeader(out,locale,(DocumentSpecification)ds,tabsArray);
  }
  
  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a repository connection of the
  * current type.  Its purpose is to add the required tabs to the list, and to output any javascript methods
  * that might be needed by the job editing HTML.
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, DocumentSpecification ds, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    outputSpecificationHeader(out,ds,tabsArray);
  }


  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a repository connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  public void outputSpecificationHeader(IHTTPOutput out, DocumentSpecification ds, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    // Call the old method signature, for backwards compatibility
    ArrayList<Object> localTabsArray = new ArrayList<Object>();
    outputSpecificationHeader(out,ds,localTabsArray);
    for (Object o : localTabsArray)
    {
      tabsArray.add((String)o);
    }
  }
  
  public void outputSpecificationHeader(IHTTPOutput out, DocumentSpecification ds, ArrayList<Object> tabsArray)
    throws ManifoldCFException, IOException
  {
  }

  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a repository connection of the
  * current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate
  *  <html>, <body>, and <form> tags.  The name of the form is always "editjob".
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@param actualSequenceNumber is the connection within the job that has currently been selected.
  *@param tabName is the current tab name.  (actualSequenceNumber, tabName) form a unique tuple within
  *  the job.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName)
    throws ManifoldCFException, IOException
  {
    outputSpecificationBody(out,locale,(DocumentSpecification)ds,tabName);
  }

  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a repository connection of the
  * current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate
  *  <html>, <body>, and <form> tags.  The name of the form is always "editjob".
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param tabName is the current tab name.
  */
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, DocumentSpecification ds, String tabName)
    throws ManifoldCFException, IOException
  {
    outputSpecificationBody(out,ds,tabName);
  }

  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a repository connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  *@param tabName is the current tab name.
  */
  public void outputSpecificationBody(IHTTPOutput out, DocumentSpecification ds, String tabName)
    throws ManifoldCFException, IOException
  {
  }
  
  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form
  * data for a connection has been posted.  Its purpose is to gather form information and modify the
  * document specification accordingly.  The name of the posted form is always "editjob".
  * The connector will be connected before this method can be called.
  *@param variableContext contains the post data, including binary file-upload information.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of
  * the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException
  {
    return processSpecificationPost(variableContext,locale,(DocumentSpecification)ds);
  }

  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form
  * data for a connection has been posted.  Its purpose is to gather form information and modify the
  * document specification accordingly.  The name of the posted form is always "editjob".
  * The connector will be connected before this method can be called.
  *@param variableContext contains the post data, including binary file-upload information.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of
  * the job (and cause a redirection to an error page).
  */
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, DocumentSpecification ds)
    throws ManifoldCFException
  {
    return processSpecificationPost(variableContext,ds);
  }

  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the document specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param ds is the current document specification for this job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  public String processSpecificationPost(IPostParameters variableContext, DocumentSpecification ds)
    throws ManifoldCFException
  {
    return null;
  }

  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the document
  * specification information to the user.  The coder can presume that the HTML that is output from
  * this configuration will be within appropriate <html> and <body> tags.
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException, IOException
  {
    viewSpecification(out,locale,(DocumentSpecification)ds);
  }

  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the document
  * specification information to the user.  The coder can presume that the HTML that is output from
  * this configuration will be within appropriate <html> and <body> tags.
  * The connector will be connected before this method can be called.
  *@param out is the output to which any HTML should be sent.
  *@param locale is the locale the output is preferred to be in.
  *@param ds is the current document specification for this job.
  */
  public void viewSpecification(IHTTPOutput out, Locale locale, DocumentSpecification ds)
    throws ManifoldCFException, IOException
  {
    viewSpecification(out,ds);
  }

  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the document specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  */
  public void viewSpecification(IHTTPOutput out, DocumentSpecification ds)
    throws ManifoldCFException, IOException
  {
  }

}


