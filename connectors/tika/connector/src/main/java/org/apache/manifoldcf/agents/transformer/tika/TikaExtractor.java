/* $Id$ */

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
package org.apache.manifoldcf.agents.transformation.tika;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.agents.system.Logging;

import java.io.*;
import java.util.*;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/** This connector works as a transformation connector, but does nothing other than logging.
*
*/
public class TikaExtractor extends org.apache.manifoldcf.agents.transformation.BaseTransformationConnector
{
  public static final String _rcsid = "@(#)$Id$";

  protected static final String ACTIVITY_EXTRACT = "extract";

  protected static final String[] activitiesList = new String[]{ACTIVITY_EXTRACT};
  
  /** We handle up to a megabyte in memory; after that we go to disk. */
  protected static final long inMemoryMaximumFile = 1000000;
  
  /** Return a list of activities that this connector generates.
  * The connector does NOT need to be connected before this method is called.
  *@return the set of activities.
  */
  @Override
  public String[] getActivitiesList()
  {
    return activitiesList;
  }

  /** Add (or replace) a document in the output data store using the connector.
  * This method presumes that the connector object has been configured, and it is thus able to communicate with the output data store should that be
  * necessary.
  * The OutputSpecification is *not* provided to this method, because the goal is consistency, and if output is done it must be consistent with the
  * output description, since that was what was partly used to determine if output should be taking place.  So it may be necessary for this method to decode
  * an output description string in order to determine what should be done.
  *@param documentURI is the URI of the document.  The URI is presumed to be the unique identifier which the output data store will use to process
  * and serve the document.  This URI is constructed by the repository connector which fetches the document, and is thus universal across all output connectors.
  *@param outputDescription is the description string that was constructed for this document by the getOutputDescription() method.
  *@param document is the document data to be processed (handed to the output data store).
  *@param authorityNameString is the name of the authority responsible for authorizing any access tokens passed in with the repository document.  May be null.
  *@param activities is the handle to an object that the implementer of a pipeline connector may use to perform operations, such as logging processing activity,
  * or sending a modified document to the next stage in the pipeline.
  *@return the document status (accepted or permanently rejected).
  *@throws IOException only if there's a stream error reading the document data.
  */
  @Override
  public int addOrReplaceDocumentWithException(String documentURI, String pipelineDescription, RepositoryDocument document, String authorityNameString, IOutputAddActivity activities)
    throws ManifoldCFException, ServiceInterruption, IOException
  {
    // Tika's API reads from an input stream and writes to an output Writer.
    // Since a RepositoryDocument includes readers and inputstreams exclusively, AND all downstream
    // processing needs to occur in a ManifoldCF thread, we have some constraints on the architecture we need to get this done:
    // (1) The principle worker thread must call the downstream pipeline send() method.
    // (2) The callee of the send() method must call a reader in the Repository Document.
    // (3) The Reader, if its databuffer is empty, must pull more data from the original input stream and hand it to Tika, which populates the Reader's databuffer.
    // So all this can be done in one thread, with some work, and the creation of a special InputStream or Reader implementation.  Where it fails, though, is the
    // requirement that tika-extracted metadata be included in the RepositoryDocument right from the beginning.  Effectively this means that the entire document
    // must be parsed before it is handed downstream -- so basically a temporary file (or in-memory buffer if small enough) must be created.
    // Instead of the elegant flow above, we have the following:
    // (1) Create a temporary file (or in-memory buffer if file is small enough)
    // (2) Run Tika to completion, streaming content output to temporary file
    // (3) Modify RepositoryDocument to read from temporary file, and include Tika-extracted metadata
    // (4) Call downstream document processing
      
    DestinationStorage ds;
      
    if (document.getBinaryLength() <= inMemoryMaximumFile)
    {
      ds = new MemoryDestinationStorage((int)document.getBinaryLength());
    }
    else
    {
      ds = new FileDestinationStorage();
    }
    try
    {
      Metadata metadata = new Metadata();
      // We only log the extraction
      long startTime = System.currentTimeMillis();
      String resultCode = "OK";
      String description = null;
      Long length = null;
      try
      {
        OutputStream os = ds.getOutputStream();
        try
        {
          Writer w = new OutputStreamWriter(os,"utf-8");
          try
          {
            // Use tika to parse stuff
            Parser parser = new AutoDetectParser();
            ContentHandler handler = new BodyContentHandler(w);
            ParseContext pc = new ParseContext();
            try
            {
              parser.parse(document.getBinaryStream(), handler, metadata, pc);
            }
            catch (TikaException e)
            {
              resultCode = "TIKAEXCEPTION";
              description = e.getMessage();
              return handleTikaException(e);
            }
            catch (SAXException e)
            {
              resultCode = "SAXEXCEPTION";
              description = e.getMessage();
              return handleSaxException(e);
            }
            catch (IOException e)
            {
              resultCode = "IOEXCEPTION";
              description = e.getMessage();
              throw e;
            }
          }
          finally
          {
            w.flush();
          }
        }
        finally
        {
          os.close();
          length = new Long(ds.getBinaryLength());
        }
      }
      finally
      {
        // Log the extraction processing
        activities.recordActivity(new Long(startTime), ACTIVITY_EXTRACT, length, documentURI,
          resultCode, description);
      }
        
      // Parsing complete!
      // Create a copy of Repository Document
      RepositoryDocument docCopy = document.duplicate();
        
      // Get new stream length
      long newBinaryLength = ds.getBinaryLength();
      // Open new input stream
      InputStream is = ds.getInputStream();
      try
      {
        docCopy.setBinary(is,newBinaryLength);

        // Set up all metadata from Tika.  We may want to run this through a mapper eventually...
        String[] metaNames = metadata.names();
        for(String mName : metaNames){
          String value = metadata.get(mName);
          docCopy.addField(mName,value);
        }

        // Send new document downstream
        int rval = activities.sendDocument(documentURI,docCopy,authorityNameString);
        length =  new Long(newBinaryLength);
        resultCode = (rval == DOCUMENTSTATUS_ACCEPTED)?"ACCEPTED":"REJECTED";
        return rval;
      }
      finally
      {
        is.close();
      }
    }
    finally
    {
      ds.close();
    }

  }

  protected static int handleTikaException(TikaException e)
    throws IOException, ManifoldCFException, ServiceInterruption
  {
    // MHL - what does Tika throw if it gets an IOException reading the stream??
    Logging.ingest.warn("Tika: Tika exception extracting: "+e.getMessage(),e);
    return DOCUMENTSTATUS_REJECTED;
  }
  
  protected static int handleSaxException(SAXException e)
    throws IOException, ManifoldCFException, ServiceInterruption
  {
    // MHL - what does this mean?
    Logging.ingest.warn("Tika: SAX exception extracting: "+e.getMessage(),e);
    return DOCUMENTSTATUS_REJECTED;
  }
  
  protected static int handleIOException(IOException e)
    throws ManifoldCFException
  {
    // IOException reading from our local storage...
    if (e instanceof InterruptedIOException)
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    throw new ManifoldCFException(e.getMessage(),e);
  }
  
  protected static interface DestinationStorage
  {
    /** Get the output stream to write to.  Caller should explicitly close this stream when done writing.
    */
    public OutputStream getOutputStream()
      throws ManifoldCFException;
    
    /** Get new binary length.
    */
    public long getBinaryLength()
      throws ManifoldCFException;

    /** Get the input stream to read from.  Caller should explicitly close this stream when done reading.
    */
    public InputStream getInputStream()
      throws ManifoldCFException;
    
    /** Close the object and clean up everything.
    * This should be called when the data is no longer needed.
    */
    public void close()
      throws ManifoldCFException;
  }
  
  protected static class FileDestinationStorage implements DestinationStorage
  {
    protected final File outputFile;
    protected final OutputStream outputStream;

    public FileDestinationStorage()
      throws ManifoldCFException
    {
      File outputFile;
      OutputStream outputStream;
      try
      {
        outputFile = File.createTempFile("mcftika","tmp");
        outputStream = new FileOutputStream(outputFile);
      }
      catch (IOException e)
      {
        handleIOException(e);
        outputFile = null;
        outputStream = null;
      }
      this.outputFile = outputFile;
      this.outputStream = outputStream;
    }
    
    @Override
    public OutputStream getOutputStream()
      throws ManifoldCFException
    {
      return outputStream;
    }
    
    /** Get new binary length.
    */
    @Override
    public long getBinaryLength()
      throws ManifoldCFException
    {
      return outputFile.length();
    }

    /** Get the input stream to read from.  Caller should explicitly close this stream when done reading.
    */
    @Override
    public InputStream getInputStream()
      throws ManifoldCFException
    {
      try
      {
        return new FileInputStream(outputFile);
      }
      catch (IOException e)
      {
        handleIOException(e);
        return null;
      }
    }
    
    /** Close the object and clean up everything.
    * This should be called when the data is no longer needed.
    */
    @Override
    public void close()
      throws ManifoldCFException
    {
      outputFile.delete();
    }

  }
  
  protected static class MemoryDestinationStorage implements DestinationStorage
  {
    protected final ByteArrayOutputStream outputStream;
    
    public MemoryDestinationStorage(int sizeHint)
    {
      outputStream = new ByteArrayOutputStream(sizeHint);
    }
    
    @Override
    public OutputStream getOutputStream()
      throws ManifoldCFException
    {
      return outputStream;
    }

    /** Get new binary length.
    */
    @Override
    public long getBinaryLength()
      throws ManifoldCFException
    {
      return outputStream.size();
    }
    
    /** Get the input stream to read from.  Caller should explicitly close this stream when done reading.
    */
    @Override
    public InputStream getInputStream()
      throws ManifoldCFException
    {
      return new ByteArrayInputStream(outputStream.toByteArray());
    }
    
    /** Close the object and clean up everything.
    * This should be called when the data is no longer needed.
    */
    public void close()
      throws ManifoldCFException
    {
    }

  }
  
}


