/* $Id: IRepositoryConnector.java 996524 2010-09-13 13:38:01Z kwright $ */

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
package org.apache.manifoldcf.crawler.interfaces;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;

import java.io.*;
import java.util.*;

/** This interface describes an instance of a connection between a repository and ManifoldCF's
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
* Documents are fetched by ManifoldCF in two stages.  First, the addSeedDocuments() method is called in the connector
* implementation.  This method is meant to add a set of document identifiers to the queue.  When ManifoldCF is ready
* to process a document, the document identifier is used to build a version string for the document and check whether
* the document needs to be indexed, and index it if needed (the second stage).  The second stage
* consists of the processDocuments() method.
*
* All of these methods interact with ManifoldCF by means of an "activity" interface.
*
* A note on connector models:
*
* These values describe what the connector returns for the addSeedDocuments() method.  The framework
* uses these to figure out how to most efficiently use the connector.  It is desirable to pick a model that
* is the most restrictive that is still accurate.  For example, if MODEL_ADD_CHANGE_DELETE applies, you would
* return that value rather than MODEL_ADD.
*
* For the CHAINED models, what the connector is describing are the documents that will be processed IF the seeded
* documents are followed to their leaves.  For instance, imagine a hierarchy where the root document is the only one ever
* seeded, but if that document is processed, and its discovered changed children are processed as well, then all documents
* that have been added, changed, or deleted will eventually be discovered.  In that case, model
* MODEL_CHAINED_ADD_CHANGE_DELETE would be appropriate.  But, if a changed node can only discover child
* additions and changes, then MODEL_CHAINED_ADD_CHANGE would be the right choice.
*	
* A CHAINED model also requires cooperation on the part of the connector for processing.  Specifically,
* a document may be unchanged but its references are expected to still be extracted in order for a CHAINED
* model to do the right thing.  For non-CHAINED models, re-extraction of references if there are no reference changes
* for a document is NOT required.
*/
public interface IRepositoryConnector extends IConnector
{
  public static final String _rcsid = "@(#)$Id: IRepositoryConnector.java 996524 2010-09-13 13:38:01Z kwright $";

  /** This is the legacy ManifoldCF catch-all crawling model.  All existing documents will be rechecked when a crawl
  * is done, every time.  This model was typically used for models where seeds were essentially fixed and all
  * real documents were discovered during crawling. */
  public static final int MODEL_ALL = 0;
  /** This indicates that the seeds are never complete; the previous seeds are lost and cannot be retrieved. */
  public static final int MODEL_PARTIAL = 4;

  /** Supply at least the documents that have been added since the specified start time.  Connector is
  * aware of the start time and end time of the request, and supplies at least the documents that have been
  * added within the specified time range. */
  public static final int MODEL_ADD = 1;
  /** Supply at least the documents that have been added or changed within the specified time range. */
  public static final int MODEL_ADD_CHANGE = 2;
  /** Supply at least the documents that have been added, changed, or deleted within the specified time range. */
  public static final int MODEL_ADD_CHANGE_DELETE = 3;

  /** Like MODEL_ADD, except considering document discovery */
  public static final int MODEL_CHAINED_ADD = 9;
  /** Like MODEL_ADD_CHANGE, except considering document discovery */
  public static final int MODEL_CHAINED_ADD_CHANGE = 10;
  /** Like MODEL_ADD_CHANGE_DELETE, except considering document discovery */
  public static final int MODEL_CHAINED_ADD_CHANGE_DELETE = 11;
  
  // These are the job modes the connector may want to know about.
  // For a once-only job, it is essential that documents that are processed by processDocuments() always queue up their child links,
  // This is not true for continuous jobs, which never delete unreachable links because they never terminate.
  public static final int JOBMODE_ONCEONLY = IJobDescription.TYPE_SPECIFIED;
  public static final int JOBMODE_CONTINUOUS = IJobDescription.TYPE_CONTINUOUS;

  /** This is the global deny token.  This should be ingested with all documents. */
  public static final String GLOBAL_DENY_TOKEN = "DEAD_AUTHORITY";

  /** Tell the world what model this connector uses for addSeedDocuments().
  * This must return a model value as specified above.  The connector does not have to be connected
  * for this method to be called.
  *@return the model type value.
  */
  public int getConnectorModel();

  /** Return the list of activities that this connector supports (i.e. writes into the log).
  * The connector does not have to be connected for this method to be called.
  *@return the list.
  */
  public String[] getActivitiesList();

  /** Return the list of relationship types that this connector recognizes.
  * The connector does not need to be connected for this method to be called.
  *@return the list.
  */
  public String[] getRelationshipTypes();

  /** Get the bin name strings for a document identifier.  The bin name describes the queue to which the
  * document will be assigned for throttling purposes.  Throttling controls the rate at which items in a
  * given queue are fetched; it does not say anything about the overall fetch rate, which may operate on
  * multiple queues or bins.
  * For example, if you implement a web crawler, a good choice of bin name would be the server name, since
  * that is likely to correspond to a real resource that will need real throttle protection.
  * The connector must be connected for this method to be called.
  *@param documentIdentifier is the document identifier.
  *@return the set of bin names.  If an empty array is returned, it is equivalent to there being no request
  * rate throttling available for this identifier.
  */
  public String[] getBinNames(String documentIdentifier);

  /** Request arbitrary connector information.
  * This method is called directly from the API in order to allow API users to perform any one of several
  * connector-specific queries.  These are usually used to create external UI's.  The connector will be
  * connected before this method is called.
  *@param output is the response object, to be filled in by this method.
  *@param command is the command, which is taken directly from the API request.
  *@return true if the resource is found, false if not.  In either case, output may be filled in.
  */
  public boolean requestInfo(Configuration output, String command)
    throws ManifoldCFException;

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
  public String addSeedDocuments(ISeedingActivity activities, Specification spec,
    String lastSeedVersion, long seedTime, int jobMode)
    throws ManifoldCFException, ServiceInterruption;

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
  public void processDocuments(String[] documentIdentifiers, IExistingVersions statuses, Specification spec,
    IProcessActivity activities, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption;

  /** Get the maximum number of documents to amalgamate together into one batch, for this connector.
  * The connector does not need to be connected for this method to be called.
  *@return the maximum number. 0 indicates "unlimited".
  */
  public int getMaxDocumentRequest();

  // UI support methods.
  //
  // The UI support methods come in two varieties.  The first group (inherited from IConnector) is involved
  //  in setting up connection configuration information.
  //
  // The second group is listed here.  These methods are is involved in presenting and editing document specification
  //  information for a job.
  //
  // The two kinds of methods are accordingly treated differently, in that the first group cannot assume that
  // the current connector object is connected, while the second group can.  That is why the first group
  // receives a thread context argument for all UI methods, while the second group does not need one
  // (since it has already been applied via the connect() method).

  /** Obtain the name of the form check javascript method to call.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return the name of the form check javascript method.
  */
  public String getFormCheckJavascriptMethodName(int connectionSequenceNumber);

  /** Obtain the name of the form presave check javascript method to call.
  *@param connectionSequenceNumber is the unique number of this connection within the job.
  *@return the name of the form presave check javascript method.
  */
  public String getFormPresaveCheckJavascriptMethodName(int connectionSequenceNumber);

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
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber, List<String> tabsArray)
    throws ManifoldCFException, IOException;
  
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
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber, int actualSequenceNumber, String tabName)
    throws ManifoldCFException, IOException;
  
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
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException;
  
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
  public void viewSpecification(IHTTPOutput out, Locale locale, Specification ds,
    int connectionSequenceNumber)
    throws ManifoldCFException, IOException;


}
