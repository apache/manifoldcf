/* $Id: DCTM.java 996524 2010-09-13 13:38:01Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.DCTM;

import org.apache.log4j.*;
import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import java.util.*;
import java.io.*;
import org.apache.manifoldcf.crawler.common.DCTM.*;
import java.rmi.*;

public class DCTM extends org.apache.manifoldcf.crawler.connectors.BaseRepositoryConnector
{
  public static final String _rcsid = "@(#)$Id: DCTM.java 996524 2010-09-13 13:38:01Z kwright $";

  public static String CONFIG_PARAM_DOCBASE = "docbasename";
  public static String CONFIG_PARAM_USERNAME = "docbaseusername";
  public static String CONFIG_PARAM_PASSWORD = "docbasepassword";
  public static String CONFIG_PARAM_WEBTOPBASEURL = "webtopbaseurl";
  public static String CONFIG_PARAM_DOMAIN = "domain";

  public static String CONFIG_PARAM_LOCATION = "docbaselocation";
  public static String CONFIG_PARAM_OBJECTTYPE = "objecttype";
  public static String CONFIG_PARAM_ATTRIBUTENAME = "attrname";
  public static String CONFIG_PARAM_MAXLENGTH = "maxdoclength";
  public static String CONFIG_PARAM_FORMAT = "mimetype";
  public static String CONFIG_PARAM_PATHNAMEATTRIBUTE = "pathnameattribute";
  public static String CONFIG_PARAM_PATHMAP = "pathmap";

  // Activities we log
  public final static String ACTIVITY_FETCH = "fetch";

  protected String docbaseName = null;
  protected String userName = null;
  protected String password = null;
  protected String domain = null;
  protected String webtopBaseURL = null;

  protected boolean hasSessionParameters = false;
  protected IDocumentum session = null;
  protected long lastSessionFetch = -1L;

  protected static final long timeToRelease = 300000L;

  /** Documentum has no "deny" tokens, and its document acls cannot be empty, so no local authority deny token is required.
  * However, it is felt that we need to be suspenders-and-belt, so here is the deny token.
  * The documentum tokens are of the form xxx:yyy, so they cannot collide with the standard deny token. */
  private static final String denyToken = GLOBAL_DENY_TOKEN;

  protected class GetSessionThread extends Thread
  {
    protected Throwable exception = null;

    public GetSessionThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        // Create a session
        IDocumentumFactory df = (IDocumentumFactory)Naming.lookup("rmi://127.0.0.1:8300/documentum_factory");
        IDocumentum newSession = df.make();
        newSession.createSession(docbaseName,userName,password,domain);
        session = newSession;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }
  }

  /** Establish session parameters.
  */
  protected void getSessionParameters()
    throws ManifoldCFException
  {
    if (!hasSessionParameters)
    {
      // Perform basic parameter checking, and debug output.
      if (docbaseName == null || docbaseName.length() < 1)
        throw new ManifoldCFException("Parameter "+CONFIG_PARAM_DOCBASE+" required but not set");

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("DCTM: Docbase = '" + docbaseName + "'");

      if (userName == null || userName.length() < 1)
        throw new ManifoldCFException("Parameter "+CONFIG_PARAM_USERNAME+" required but not set");

      if (Logging.connectors.isDebugEnabled())
        Logging.connectors.debug("DCTM: Username = '" + userName + "'");

      if (password == null || password.length() < 1)
        throw new ManifoldCFException("Parameter "+CONFIG_PARAM_PASSWORD+" required but not set");

      Logging.connectors.debug("DCTM: Password exists");

      if (webtopBaseURL == null || webtopBaseURL.length() < 1)
        throw new ManifoldCFException("Required parameter "+CONFIG_PARAM_WEBTOPBASEURL+" missing");

      if (domain == null)
        // Empty domain is allowed
        Logging.connectors.debug("DCTM: No domain");
      else
        Logging.connectors.debug("DCTM: Domain = '" + domain + "'");

      hasSessionParameters = true;
    }
  }
  
  /** Get a DFC session.  This will be done every time it is needed.
  */
  protected void getSession()
    throws ManifoldCFException, ServiceInterruption
  {
    getSessionParameters();
    if (session == null)
    {
      long currentTime;
      GetSessionThread t = new GetSessionThread();
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof java.net.MalformedURLException)
            throw (java.net.MalformedURLException)thr;
          else if (thr instanceof NotBoundException)
            throw (NotBoundException)thr;
          else if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof DocumentumException)
            throw (DocumentumException)thr;
          else
            throw (Error)thr;
        }
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (java.net.MalformedURLException e)
      {
        throw new ManifoldCFException(e.getMessage(),e);
      }
      catch (NotBoundException e)
      {
        // Transient problem: Server not available at the moment.
        Logging.connectors.warn("DCTM: RMI server not up at the moment: "+e.getMessage(),e);
        currentTime = System.currentTimeMillis();
        throw new ServiceInterruption(e.getMessage(),currentTime + 60000L);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        // Treat this as a transient problem
        Logging.connectors.warn("DCTM: Transient remote exception creating session: "+e.getMessage(),e);
        currentTime = System.currentTimeMillis();
        throw new ServiceInterruption(e.getMessage(),currentTime + 60000L);
      }
      catch (DocumentumException e)
      {
        // Base our treatment on the kind of error it is.
        if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
        {
          Logging.connectors.warn("DCTM: Remote service interruption creating session: "+e.getMessage(),e);
          currentTime = System.currentTimeMillis();
          throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12*60*60000L,
            -1,true);
        }
        throw new ManifoldCFException(e.getMessage(),e);
      }
    }

    // Note that we need the session at this time; this will determine when
    // the session expires.
    lastSessionFetch = System.currentTimeMillis();

  }

  protected class GetListOfValuesThread extends Thread
  {
    protected String query;
    protected String fieldName;
    protected ArrayList list;
    protected Throwable exception = null;

    public GetListOfValuesThread(String query, String fieldName, ArrayList list)
    {
      super();
      setDaemon(true);
      this.query = query;
      this.fieldName = fieldName;
      this.list = list;
    }

    public void run()
    {
      try
      {
        IDocumentumResult result = session.performDQLQuery(query);
        try
        {
          while (result.isValidRow())
          {
            list.add(result.getStringValue(fieldName));
            result.nextRow();
          }
          return;
        }
        finally
        {
          result.close();
        }

      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

  }

  protected void getAttributesForType(ArrayList list, String typeName)
    throws DocumentumException, ManifoldCFException, ServiceInterruption
  {
    String strDQL = "select attr_name FROM dmi_dd_attr_info where type_name = '" + typeName + "'";

    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      GetListOfValuesThread t = new GetListOfValuesThread(strDQL,"attr_name",list);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof DocumentumException)
            throw (DocumentumException)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        return;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        if (noSession)
        {
          long currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
        }
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }
  }

  protected class CheckConnectionThread extends Thread
  {
    protected Throwable exception = null;

    public CheckConnectionThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        session.checkConnection();
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

  }

  /** Check connection, with appropriate retries */
  protected void checkConnection()
    throws DocumentumException, ManifoldCFException, ServiceInterruption
  {
    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      CheckConnectionThread t = new CheckConnectionThread();
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof DocumentumException)
            throw (DocumentumException)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        return;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        if (noSession)
        {
          long currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
        }
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }
  }

  protected class BuildDateStringThread extends Thread
  {
    protected long timevalue;
    protected Throwable exception = null;
    protected String rval = null;

    public BuildDateStringThread(long timevalue)
    {
      super();
      setDaemon(true);
      this.timevalue = timevalue;
    }

    public void run()
    {
      try
      {
        rval = session.buildDateString(timevalue);
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public String getResponse()
    {
      return rval;
    }
  }

  /** Build date string with appropriate reset */
  protected String buildDateString(long timevalue)
    throws DocumentumException, ManifoldCFException, ServiceInterruption
  {
    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      BuildDateStringThread t = new BuildDateStringThread(timevalue);
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof DocumentumException)
            throw (DocumentumException)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        return t.getResponse();
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        if (noSession)
        {
          long currentTime = System.currentTimeMillis();
          throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
        }
        session = null;
        lastSessionFetch = -1L;
        continue;
      }
    }
  }

  protected class DestroySessionThread extends Thread
  {
    protected Throwable exception = null;

    public DestroySessionThread()
    {
      super();
      setDaemon(true);
    }

    public void run()
    {
      try
      {
        session.destroySession();
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

  }

  /** Release the session, if it's time.
  */
  protected void releaseCheck()
    throws ManifoldCFException
  {
    if (lastSessionFetch == -1L)
      return;

    long currentTime = System.currentTimeMillis();
    if (currentTime >= lastSessionFetch + timeToRelease)
    {
      DestroySessionThread t = new DestroySessionThread();
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof DocumentumException)
            throw (DocumentumException)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        session = null;
        lastSessionFetch = -1L;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.connectors.warn("Transient remote exception closing session: "+e.getMessage(),e);
      }
      catch (DocumentumException e)
      {
        // Base our treatment on the kind of error it is.
        if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
        {
          Logging.connectors.warn("Remote service interruption closing session: "+e.getMessage(),e);
        }
        else
          Logging.connectors.warn("Error closing session: "+e.getMessage(),e);
      }
    }
  }

  /** Constructor.
  */
  public DCTM()
  {
    super();
  }

  /** Let the crawler know the completeness of the information we are giving it.
  */
  @Override
  public int getConnectorModel()
  {
    // For documentum, originally we thought it would return the deleted objects when we
    // reseeded.  Later research has shown that documentum simply deletes the whole thing now
    // and doesn't leave a gravemarker around at all.  So we have no choice but to treat this
    // like other stupid repositories and check for deletes by scanning!  UGH.  It also does
    // not accurately provide changes, because the ACL changes are not caught by the query.
    return MODEL_ADD;
  }

  /** Return the list of activities that this connector supports (i.e. writes into the log).
  *@return the list.
  */
  @Override
  public String[] getActivitiesList()
  {
    return new String[]{ACTIVITY_FETCH};
  }

  /** Test the connection.  Returns a string describing the connection integrity.
  *@return the connection's status as a displayable string.
  */
  @Override
  public String check()
    throws ManifoldCFException
  {
    try
    {
      try
      {
        checkConnection();
        return super.check();
      }
      catch (DocumentumException e)
      {
        // Base our treatment on the kind of error it is.
        if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
          throw new ServiceInterruption(e.getMessage(),0L);
        else
          throw new ManifoldCFException(e.getMessage(),e);
      }
    }
    catch (ServiceInterruption e)
    {
      return "Connection temporarily failed: "+e.getMessage();
    }
    catch (ManifoldCFException e)
    {
      return "Connection failed: "+e.getMessage();
    }
  }

  /** Connect.  The configuration parameters are included.
  *@param configParams are the configuration parameters for this connection.
  * Note well: There are no exceptions allowed from this call, since it is expected to mainly establish connection parameters.
  */
  @Override
  public void connect(ConfigParams configParams)
  {
    super.connect(configParams);

    // Set local parameters, for convenience
    docbaseName = params.getParameter(CONFIG_PARAM_DOCBASE);
    userName = params.getParameter(CONFIG_PARAM_USERNAME);
    password = params.getObfuscatedParameter(CONFIG_PARAM_PASSWORD);
    webtopBaseURL = params.getParameter(CONFIG_PARAM_WEBTOPBASEURL);
    domain = params.getParameter(CONFIG_PARAM_DOMAIN);
    if (domain == null || domain.length() < 1)
      domain = null;
  }

  /** This method is periodically called for all connectors that are connected but not
  * in active use.
  */
  @Override
  public void poll()
    throws ManifoldCFException
  {
    releaseCheck();
  }

  /** This method is called to assess whether to count this connector instance should
  * actually be counted as being connected.
  *@return true if the connector instance is actually connected.
  */
  @Override
  public boolean isConnected()
  {
    return session != null;
  }

  /** Disconnect from Documentum.
  */
  @Override
  public void disconnect()
    throws ManifoldCFException
  {
    hasSessionParameters = false;
    if (session != null)
    {
      DestroySessionThread t = new DestroySessionThread();
      try
      {
        t.start();
        t.join();
        Throwable thr = t.getException();
        if (thr != null)
        {
          if (thr instanceof RemoteException)
            throw (RemoteException)thr;
          else if (thr instanceof DocumentumException)
            throw (DocumentumException)thr;
          else if (thr instanceof RuntimeException)
            throw (RuntimeException)thr;
          else
            throw (Error)thr;
        }
        session = null;
        lastSessionFetch = -1L;
      }
      catch (InterruptedException e)
      {
        t.interrupt();
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (RemoteException e)
      {
        Throwable e2 = e.getCause();
        if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
          throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
        session = null;
        lastSessionFetch = -1L;
        // Treat this as a transient problem
        Logging.connectors.warn("DCTM: Transient remote exception closing session: "+e.getMessage(),e);
      }
      catch (DocumentumException e)
      {
        // Base our treatment on the kind of error it is.
        if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
        {
          Logging.connectors.warn("DCTM: Remote service interruption closing session: "+e.getMessage(),e);
        }
        else
          Logging.connectors.warn("DCTM: Error closing session: "+e.getMessage(),e);
      }

    }

    docbaseName = null;
    userName = null;
    password = null;
    domain = null;
    webtopBaseURL = null;

  }

  /** Protected method for calculating the URI
  */
  protected String convertToURI(String strObjectId, String objectType)
    throws ManifoldCFException
  {
    String strWebtopBaseUrl = webtopBaseURL;

    if (!strWebtopBaseUrl.endsWith("/"))
    {
      strWebtopBaseUrl = strWebtopBaseUrl + "/";
    }

    return strWebtopBaseUrl +
      "component/drl?versionLabel=CURRENT&objectId=" + strObjectId;
  }

  /** Get the bin (so throttling makes sense).  We will bin by docbase.
  */
  @Override
  public String[] getBinNames(String documentIdentifier)
  {
    // Previously, this actually established a session and went back-and-forth with
    // documentum.  But we already have the docbase name, so that seems stupid.
    return new String[]{docbaseName};
  }

  protected static class StringQueue
  {
    protected String value = null;
    protected boolean present = false;
    protected boolean abort = false;

    public StringQueue()
    {
    }

    public synchronized String getNext()
      throws InterruptedException
    {
      while (abort == false && present == false)
        wait();
      if (abort)
        return null;
      present = false;
      String rval = value;
      notifyAll();
      return rval;
    }

    public synchronized void add(String value)
      throws InterruptedException
    {
      while (abort == false && present == true)
        wait();
      if (abort)
        return;
      present = true;
      this.value = value;
      notifyAll();
    }

    public synchronized void abort()
    {
      abort = true;
      notifyAll();
    }
  }

  protected class GetDocumentsFromQueryThread extends Thread
  {
    protected String dql;
    protected StringQueue queue;
    protected Throwable exception = null;
    protected boolean abortSignaled = false;

    public GetDocumentsFromQueryThread(String dql, StringQueue queue)
    {
      super();
      setDaemon(true);
      this.dql = dql;
      this.queue = queue;
    }

    public void abort()
    {
      abortSignaled = true;
      queue.abort();
    }

    public void run()
    {
      try
      {
        try
        {
          // This is a bit dicey, because any call to a ISeedingActivities method may well cause locks to be thrown.  The owning thread,
          // however, will try to shut this thread down only once, then it will exit itself.  Since the activities method itself is properly
          // interruptible, cleanup will correctly occur should there be enough time to do it before the process exits - but that is not
          // guaranteed, unfortunately.
          //
          // So, the only way this can work is to build an in-memory queue, where the owning thread does the actual call to the appropriate
          // ISeedingActivities method.  It's yet another complication on an already extremely complex model.
          if (!abortSignaled)
          {
            IDocumentumResult result = session.performDQLQuery(dql);
            try
            {
              while (result.isValidRow())
              {
                if (abortSignaled)
                  break;
                String strObjectId = result.getStringValue("i_chronicle_id");
                result.nextRow();
                queue.add(strObjectId);
              }
            }
            finally
            {
              result.close();
            }
          }
        }
        catch (InterruptedException e)
        {
          // Abort the thread
          throw e;
        }
        catch (Throwable e)
        {
          this.exception = e;
        }
        finally
        {
          // Always signal the end!!  This guarantees that the calling thread will be able to wake up and notice we have finished.
          queue.add(null);
        }
      }
      catch (InterruptedException e)
      {
        // Just end
      }
    }

    public Throwable getException()
    {
      return exception;
    }

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
    if (command.equals("contenttypes"))
    {
      try
      {
        String[] contentTypes = getContentTypes();
        int i = 0;
        while (i < contentTypes.length)
        {
          String contentType = contentTypes[i++];
          ConfigurationNode node = new ConfigurationNode("content_type");
          node.setValue(contentType);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.createServiceInterruptionNode(output,e);
      }
      catch (ManifoldCFException e)
      {
        ManifoldCF.createErrorNode(output,e);
      }
    }
    else if (command.equals("objecttypes"))
    {
      try
      {
        String[] objectTypes = getObjectTypes();
        int i = 0;
        while (i < objectTypes.length)
        {
          String objectType = objectTypes[i++];
          ConfigurationNode node = new ConfigurationNode("object_type");
          node.setValue(objectType);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.createServiceInterruptionNode(output,e);
      }
      catch (ManifoldCFException e)
      {
        ManifoldCF.createErrorNode(output,e);
      }
    }
    else if (command.startsWith("folders/"))
    {
      // I hope folder names in Documentum cannot have "/" characters in them.
      String parentFolder = command.substring("folders/".length());
      try
      {
        String[] folders = getChildFolderNames(parentFolder);
        int i = 0;
        while (i < folders.length)
        {
          String folder = folders[i++];
          ConfigurationNode node = new ConfigurationNode("folder");
          node.setValue(folder);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.createServiceInterruptionNode(output,e);
      }
      catch (ManifoldCFException e)
      {
        ManifoldCF.createErrorNode(output,e);
      }
    }
    else if (command.startsWith("indexableattributes/"))
    {
      // I hope object types can't have "/" characters
      String objectType = command.substring("indexableattributes/".length());
      try
      {
        String[] indexableAttributes = getIngestableAttributes(objectType);
        int i = 0;
        while (i < indexableAttributes.length)
        {
          String indexableAttribute = indexableAttributes[i++];
          ConfigurationNode node = new ConfigurationNode("attribute");
          node.setValue(indexableAttribute);
          output.addChild(output.getChildCount(),node);
        }
      }
      catch (ServiceInterruption e)
      {
        ManifoldCF.createServiceInterruptionNode(output,e);
      }
      catch (ManifoldCFException e)
      {
        ManifoldCF.createErrorNode(output,e);
      }
    }
    else
      return super.requestInfo(output,command);
    return true;
  }
  
  /** Queue "seed" documents.  Seed documents are the starting places for crawling activity.  Documents
  * are seeded when this method calls appropriate methods in the passed in ISeedingActivity object.
  *@param activities is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is a document specification (that comes from the job).
  *@param startTime is the beginning of the time range to consider, inclusive.
  *@param endTime is the end of the time range to consider, exclusive.
  */
  @Override
  public void addSeedDocuments(ISeedingActivity activities, DocumentSpecification spec,
    long startTime, long endTime)
    throws ManifoldCFException, ServiceInterruption
  {
    // First, build the query

    int i = 0;
    StringBuilder strLocationsClause = new StringBuilder();
    ArrayList tokenList = new ArrayList();
    ArrayList contentList = null;
    String maxSize = null;

    while (i < spec.getChildCount())
    {
      SpecificationNode n = spec.getChild(i);
      if (n.getType().equals(CONFIG_PARAM_LOCATION))
      {
        String strLocation = n.getAttributeValue("path");
        if (strLocation != null && strLocation.length() > 0)
        {
          if (strLocationsClause != null && strLocationsClause.length() > 0)
          {
            strLocationsClause.append(" OR Folder('").append(strLocation).append("', DESCEND)");
          }
          else
          {
            strLocationsClause.append("Folder('").append(strLocation).append("', DESCEND)");
          }
        }
      }
      else if (n.getType().equals(CONFIG_PARAM_OBJECTTYPE))
      {
        String objType = n.getAttributeValue("token");
        tokenList.add(objType);
      }
      else if (n.getType().equals(CONFIG_PARAM_FORMAT))
      {
        String docType = n.getAttributeValue("value");
        if (contentList == null)
          contentList = new ArrayList();
        contentList.add(docType);
      }
      else if (n.getType().equals(CONFIG_PARAM_MAXLENGTH))
      {
        maxSize = n.getAttributeValue("value");
      }

      i++;
    }

    if (tokenList.size() == 0)
    {
      Logging.connectors.debug("DCTM: No ObjectType found in Document Spec. Setting it to dm_document");
      tokenList.add("dm_document");
    }

    if (strLocationsClause.length() < 1)
    {
      Logging.connectors.debug("DCTM: No location found in document specification. Search will be across entire docbase");
    }

    try
    {
      String strDQLstart = "select for READ distinct i_chronicle_id from ";
      // There seems to be some unexplained slop in the latest DCTM version.  It misses documents depending on how close to the r_modify_date you happen to be.
      // So, I've decreased the start time by a full five minutes, to insure overlap.
      if (startTime > 300000L)
        startTime = startTime - 300000L;
      else
        startTime = 0L;
      StringBuilder strDQLend = new StringBuilder(" where r_modify_date >= " + buildDateString(startTime) +
        " and r_modify_date<=" + buildDateString(endTime) +
        " AND (i_is_deleted=TRUE Or (i_is_deleted=FALSE AND a_full_text=TRUE AND r_content_size>0");

      // append maxsize if set
      if (maxSize != null && maxSize.length() > 0)
      {
        strDQLend.append(" AND r_content_size<=").append(maxSize);
      }

      String[] dctmTypes = convertToDCTMTypes(contentList);
      if (dctmTypes != null)
      {
        if (dctmTypes.length == 0)
          strDQLend.append(" AND 1<0");
        else
        {
          i = 0;
          strDQLend.append(" AND a_content_type IN (");
          while (i < dctmTypes.length)
          {
            if (i > 0)
              strDQLend.append(",");
            String cType = dctmTypes[i++];
            strDQLend.append("'").append(cType).append("'");
          }
          strDQLend.append(")");
        }
      }

      // End the clause for non-deleted documents
      strDQLend.append("))");

      // append location on if it is provided.  This will apply to both deleted and non-deleted documents.
      if (strLocationsClause.length() > 0)
      {
        strDQLend.append(" AND ( " + strLocationsClause.toString() + " )");
      }

      // Now, loop through the documents and queue them up.
      int tokenIndex = 0;
      while (tokenIndex < tokenList.size())
      {
        activities.checkJobStillActive();
        String tokenValue = (String)tokenList.get(tokenIndex);
        String strDQL = strDQLstart + tokenValue + strDQLend;
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("DCTM: About to execute query= (" + strDQL + ")");
        while (true)
        {
          boolean noSession = (session==null);
          getSession();
          StringQueue stringQueue = new StringQueue();
          GetDocumentsFromQueryThread t = new GetDocumentsFromQueryThread(strDQL,stringQueue);
          try
          {
            t.start();
            try
            {
              int checkIndex = 0;
              // Loop through return values and add them until done is signalled
              while (true)
              {
                if (checkIndex == 10)
                {
                  activities.checkJobStillActive();
                  checkIndex = 0;
                }
                checkIndex++;
                String next = stringQueue.getNext();
                if (next == null)
                  break;
                activities.addSeedDocument(next);
              }
              t.join();
              Throwable thr = t.getException();
              if (thr != null)
              {
                if (thr instanceof RemoteException)
                  throw (RemoteException)thr;
                else if (thr instanceof DocumentumException)
                  throw (DocumentumException)thr;
                else if (thr instanceof InterruptedException)
                  throw (InterruptedException)thr;
                else if (thr instanceof RuntimeException)
                  throw (RuntimeException)thr;
                else
                  throw (Error)thr;
              }
              tokenIndex++;
              // Go on to next document type and repeat
              break;
            }
            catch (InterruptedException e)
            {
              t.abort();
              // This is just a courtesy; the thread will be killed regardless on process exit
              t.interrupt();
              // It's ok to leave the thread still active; we'll be shutting down anyway.
              throw e;
            }
            catch (ManifoldCFException e)
            {
              t.abort();
              // We need the join, because we really don't want this documentum session to be
              // still busy when we leave.
              t.join();
              throw e;
            }
            catch (ServiceInterruption e)
            {
              t.abort();
              // We need the join, because we really don't want this documentum session to be
              // still busy when we leave.
              t.join();
              throw e;
            }
            catch (RemoteException e)
            {
              Throwable e2 = e.getCause();
              if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
                throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
              if (noSession)
              {
                long currentTime = System.currentTimeMillis();
                throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
              }
              session = null;
              lastSessionFetch = -1L;
              // Go back around again
            }
          }
          catch (InterruptedException e)
          {
            throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
          }
        }
      }
    }
    catch (DocumentumException e)
    {
      // Base our treatment on the kind of error it is.
      if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
      {
        long currentTime = System.currentTimeMillis();
        Logging.connectors.warn("DCTM: Remote service interruption getting versions: "+e.getMessage(),e);
        throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L, currentTime + 12 * 60 * 60000L,-1,true);
      }
      throw new ManifoldCFException(e.getMessage(),e);
    }

  }

  /** Do a query and read back the name column */
  protected static String[] convertToDCTMTypes(ArrayList contentList)
    throws ManifoldCFException, ServiceInterruption
  {
    if (contentList != null && contentList.size() > 0)
    {
      // The contentList has type names.
      String[] rval = new String[contentList.size()];
      int i = 0;
      while (i < rval.length)
      {
        rval[i] = (String)contentList.get(i);
        i++;
      }
      return rval;
    }
    return null;

  }

  protected class GetDocumentVersionThread extends Thread
  {
    protected String documentIdentifier;
    protected HashMap typeMap;
    protected String forcedAclString;
    protected String pathNameAttributeVersion;
    protected Throwable exception = null;
    protected String rval = null;

    public GetDocumentVersionThread(String documentIdentifier, HashMap typeMap, String forcedAclString, String pathNameAttributeVersion)
    {
      super();
      setDaemon(true);
      this.documentIdentifier = documentIdentifier;
      this.typeMap = typeMap;
      this.forcedAclString = forcedAclString;
      this.pathNameAttributeVersion = pathNameAttributeVersion;
    }

    public void run()
    {
      try
      {

        IDocumentumObject object = session.getObjectByQualification("dm_document where i_chronicle_id='" + documentIdentifier +
          "' and any r_version_label='CURRENT'");
        try
        {
          if (object.exists() && !object.isDeleted() && !object.isHidden() && object.getPermit() > 1 &&
            object.getContentSize() > 0 && object.getPageCount() > 0)
          {
            // According to Ryck, the version label is not helping us much, so if it's null it's ok
            String versionLabel = object.getVersionLabel();

            // The version string format was reorganized on 11/6/2006.

            StringBuilder strVersionLabel = new StringBuilder();

            // Get the type name; this is what we use to figure out the desired attributes
            String typeName = object.getTypeName();
            // Look for the string to append to the version
            String metadataVersionAddendum = (String)typeMap.get(typeName);
            // If there's no typemap entry, it can only mean that the document type was not selected for in the UI.
            // In that case, we presume no metadata.

            if (metadataVersionAddendum != null)
              strVersionLabel.append(metadataVersionAddendum);
            else
              packList(strVersionLabel,new String[0],'+');

            // Now do the forced acls.  Since this is a reorganization of the version string,
            // I decided to make these parseable, and pass them through to processDocument() in that
            // way, because most connectors seem to be heading in that direction.
            strVersionLabel.append(forcedAclString);

            // The version label passed back will be a concatenation of the implicit version label and the v_stamp
            // This way we can catch any changes to the content
            strVersionLabel.append(versionLabel);
            strVersionLabel.append("_").append(object.getVStamp());

            /* This was removed on 9/5/2006 because Rick indicated that i_vstamp is incremented on every change to a document,
            including change of dynamic acl name.  This is in contrast to r_modifydate, which is NOT changed under such conditions.

            if (acls != null && acls.length == 0)
            {
              // Get the acl for the document, and tack it on to the version if it's dynamic.  This compensates
              // for the fact that changing a dynamic acl on an object doesn't mark it as modified!
              String aclName = object.getACLName();
              if (aclName != null && aclName.startsWith("dm_"))
                strVersionLabel.append("=").append(aclName);
            }
            */

            // Append the path name attribute version
            strVersionLabel.append(pathNameAttributeVersion);

            // Append the Webtop base url.  This was added on 9/7/2007.
            strVersionLabel.append("_").append(webtopBaseURL);

            rval = strVersionLabel.toString();
          }
          else
            rval = null;
        }
        finally
        {
          object.release();
        }

      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public String getResponse()
    {
      return rval;
    }
  }

  /** Get document versions given an array of document identifiers.
  * This method is called for EVERY document that is considered. It is
  * therefore important to perform as little work as possible here.
  *@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
  *@param oldVersions is the corresponding array of version strings that have been saved for the document identifiers.
  *   A null value indicates that this is a first-time fetch, while an empty string indicates that the previous document
  *   had an empty version string.
  *@param activity is the interface this method should use to perform whatever framework actions are desired.
  *@param spec is the current document specification for the current job.  If there is a dependency on this
  * specification, then the version string should include the pertinent data, so that reingestion will occur
  * when the specification changes.  This is primarily useful for metadata.
  *@param jobMode is an integer describing how the job is being run, whether continuous or once-only.
  *@param usesDefaultAuthority will be true only if the authority in use for these documents is the default one.
  *@return the corresponding version strings, with null in the places where the document no longer exists.
  * Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
  * will always be processed.
  */
  @Override
  public String[] getDocumentVersions(String[] documentIdentifiers, String[] oldVersions, IVersionActivity activity,
    DocumentSpecification spec, int jobMode, boolean usesDefaultAuthority)
    throws ManifoldCFException, ServiceInterruption
  {
    Logging.connectors.debug("DCTM: Inside getDocumentVersions");

    String[] strArrayRetVal = new String[documentIdentifiers.length];

    // Get the forced acls (and whether security is on as well)
    String[] acls = getAcls(spec);
    // Build a "forced acl" version string, of the form ";<acl>+<acl>+..."
    StringBuilder forcedAclString = new StringBuilder();
    if (acls != null)
    {
      forcedAclString.append('+');
      java.util.Arrays.sort(acls);
      packList(forcedAclString,acls,'+');
      pack(forcedAclString,denyToken,'+');
    }
    else
      forcedAclString.append('-');

    // Build a map of type name and metadata version string to append
    HashMap typeMap = new HashMap();
    String pathAttributeName = null;
    MatchMap matchMap = new MatchMap();

    int i = 0;
    while (i < spec.getChildCount())
    {
      SpecificationNode n = spec.getChild(i++);
      if (n.getType().equals(CONFIG_PARAM_OBJECTTYPE))
      {
        String typeName = n.getAttributeValue("token");
        String isAll = n.getAttributeValue("all");
        ArrayList list = new ArrayList();
        if (isAll != null && isAll.equals("true"))
        {
          // "All" attributes are specified
          // The current complete list of attributes must be fetched for this document type
          try
          {
            getAttributesForType(list,typeName);
          }
          catch (DocumentumException e)
          {
            // Base our treatment on the kind of error it is.
            long currentTime = System.currentTimeMillis();
            if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
            {
              Logging.connectors.warn("DCTM: Remote service interruption listing attributes: "+e.getMessage(),e);
              throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
            }
            throw new ManifoldCFException(e.getMessage(),e);
          }

        }
        else
        {
          int l = 0;
          while (l < n.getChildCount())
          {
            SpecificationNode sn = n.getChild(l++);
            if (sn.getType().equals(CONFIG_PARAM_ATTRIBUTENAME))
            {
              String attrName = sn.getAttributeValue("attrname");
              list.add(attrName);
            }
          }
        }
        // Sort the attribute names, because we need them to be comparable.
        String[] sortArray = new String[list.size()];
        int j = 0;
        while (j < sortArray.length)
        {
          sortArray[j] = (String)list.get(j);
          j++;
        }
        java.util.Arrays.sort(sortArray);
        StringBuilder sb = new StringBuilder();
        packList(sb,sortArray,'+');
        typeMap.put(typeName,sb.toString());
      }
      else if (n.getType().equals(CONFIG_PARAM_PATHNAMEATTRIBUTE))
        pathAttributeName = n.getAttributeValue("value");
      else if (n.getType().equals(CONFIG_PARAM_PATHMAP))
      {
        // Path mapping info also needs to be looked at, because it affects what is
        // ingested.
        String pathMatch = n.getAttributeValue("match");
        String pathReplace = n.getAttributeValue("replace");
        matchMap.appendMatchPair(pathMatch,pathReplace);
      }
    }


    // Calculate the part of the version string that comes from path name and mapping.
    // This starts with = since ; is used by another optional component (the forced acls)
    StringBuilder pathNameAttributeVersion = new StringBuilder();
    if (pathAttributeName != null)
      pathNameAttributeVersion.append("=").append(pathAttributeName).append(":").append(matchMap);

    int intObjectIdCount = documentIdentifiers.length;

    long currentTime;

    try
    {
      for (int intInc = 0; intInc < intObjectIdCount; intInc++)
      {
        // Since each documentum access is time-consuming, be sure that we abort if the job has gone inactive
        activity.checkJobStillActive();

        String documentIdentifier = documentIdentifiers[intInc];
        while (true)
        {
          boolean noSession = (session==null);
          getSession();
          GetDocumentVersionThread t = new GetDocumentVersionThread(documentIdentifier, typeMap, forcedAclString.toString(), pathNameAttributeVersion.toString());
          try
          {
            t.start();
            t.join();
            Throwable thr = t.getException();
            if (thr != null)
            {
              if (thr instanceof RemoteException)
                throw (RemoteException)thr;
              else if (thr instanceof DocumentumException)
                throw (DocumentumException)thr;
              else if (thr instanceof RuntimeException)
                throw (RuntimeException)thr;
              else
                throw (Error)thr;
            }
            String versionString = t.getResponse();
            strArrayRetVal[intInc] = versionString;

            if (Logging.connectors.isDebugEnabled())
            {
              if (versionString != null)
              {
                Logging.connectors.debug("DCTM: Document " + documentIdentifier+" has version label: " + versionString);
              }
              else
              {
                Logging.connectors.debug("DCTM: Document " + documentIdentifier+" has been removed or is hidden");
              }
            }
            // Leave the retry loop; go on to the next document
            break;
          }
          catch (InterruptedException e)
          {
            t.interrupt();
            throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
          }
          catch (RemoteException e)
          {
            Throwable e2 = e.getCause();
            if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
              throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
            if (noSession)
            {
              currentTime = System.currentTimeMillis();
              throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
            }
            session = null;
            lastSessionFetch = -1L;
            // Go back around again
          }
        }
      }
      return strArrayRetVal;
    }
    catch (DocumentumException e)
    {
      // Base our treatment on the kind of error it is.
      currentTime = System.currentTimeMillis();
      if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
      {
        Logging.connectors.warn("DCTM: Remote service interruption getting versions: "+e.getMessage(),e);
        throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
      }
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  protected class ProcessDocumentThread extends Thread
  {
    protected String documentIdentifier;
    protected String versionString;
    protected File objFileTemp;
    protected SystemMetadataDescription sDesc;
    protected Throwable exception = null;
    protected RepositoryDocument rval = null;
    protected Long activityStartTime = null;
    protected Long activityFileLength = null;
    protected String activityStatus = null;
    protected String activityMessage = null;
    protected String uri = null;
    protected String contentType = null;
    protected Long contentSize = null;


    public ProcessDocumentThread(String documentIdentifier, String versionString, File objFileTemp, SystemMetadataDescription sDesc)
    {
      super();
      setDaemon(true);
      this.documentIdentifier = documentIdentifier;
      this.versionString = versionString;
      this.objFileTemp = objFileTemp;
      this.sDesc = sDesc;
    }

    public void run()
    {
      try
      {
        IDocumentumObject object = session.getObjectByQualification("dm_document where i_chronicle_id='" + documentIdentifier +
          "' and any r_version_label='CURRENT'");
        try
        {
          long contentSizeValue = object.getContentSize();
          if (object.exists() && !object.isDeleted() && !object.isHidden() && object.getPermit() > 1 &&
            contentSizeValue > 0 && object.getPageCount() > 0)
          {
            contentSize = new Long(contentSizeValue);
            
            String objName = object.getObjectName();

            String contentType = object.getContentType();
            
            // This particular way of getting content failed, because DFC loaded the
            // whole object into memory (very very bad DFC!)
            // InputStream is = objIDfSysObject.getContent();
            //
            // Instead, read the file to a disk temporary file, and then stream from there.
            activityStartTime = new Long(System.currentTimeMillis());

            String strFilePath = null;
            try
            {
              strFilePath = object.getFile(objFileTemp.getCanonicalPath());
            }
            catch (DocumentumException dfe)
            {
              // Fetch failed, so log it
              activityStatus = "Did not exist";
              activityMessage = dfe.getMessage();
              if (dfe.getType() != DocumentumException.TYPE_NOTALLOWED)
                throw dfe;
              return;
            }
            long fileLength = objFileTemp.length();
            activityFileLength = new Long(fileLength);

            if (strFilePath == null)
            {
              activityStatus = "Failed";
              activityMessage = "Unknown";
              // We don't know why it won't fetch, but skip it and keep going.
              return;
            }

            activityStatus = "Success";

            rval = new RepositoryDocument();

            if (contentType != null)
              rval.setMimeType(contentType);
            
            // Handle the metadata.
            // The start of the version string contains the names of the metadata.  We parse it out of the
            // version string, because we don't want the chance of somebody changing something after we got
            // the version together and before we actually ingested the metadata.  Plus, it's faster.
            ArrayList attributeDescriptions = new ArrayList();
            int startPosition = unpackList(attributeDescriptions,versionString,0,'+');
            // Unpack forced acls.
            ArrayList acls = null;
            String denyAcl = null;
            if (startPosition < versionString.length() && versionString.charAt(startPosition++) == '+')
            {
              acls = new ArrayList();
              startPosition = unpackList(acls,versionString,startPosition,'+');
              StringBuilder denyAclBuffer = new StringBuilder();
              startPosition = unpack(denyAclBuffer,versionString,startPosition,'+');
              denyAcl = denyAclBuffer.toString();
            }

            int z = 0;
            while (z < attributeDescriptions.size())
            {
              String attrName = (String)attributeDescriptions.get(z++);
              // Fetch the attributes from the object
              String[] values = object.getAttributeValues(attrName);
              // Add the attribute to the rd
              rval.addField(attrName,values);
            }

            // Add the path metadata item into the mix, if enabled
            String pathAttributeName = sDesc.getPathAttributeName();
            if (pathAttributeName != null && pathAttributeName.length() > 0)
            {
              String[] pathString = sDesc.getPathAttributeValue(object);
              rval.addField(pathAttributeName,pathString);
            }

            // Handle the forced acls
            if (acls != null && acls.size() == 0)
            {
              String[] strarrACL = new String[1];
              // This used to go back-and-forth to documentum to get the docbase name, but that seemed stupid, so i just
              // use the one I have already now.
              strarrACL[0] = docbaseName + ":" + object.getACLDomain() + "." + object.getACLName();
              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("DCTM: Processing document (" + objName + ") with ACL=" + strarrACL[0] + " and size=" + object.getContentSize() + " bytes.");
              rval.setACL(strarrACL);
            }
            else if (acls != null)
            {
              String[] forcedAcls = new String[acls.size()];
              z = 0;
              while (z < forcedAcls.length)
              {
                forcedAcls[z] = (String)acls.get(z);
                z++;
              }
              rval.setACL(forcedAcls);


              if (Logging.connectors.isDebugEnabled())
                Logging.connectors.debug("DCTM: Processing document (" + objName + ") with size=" + object.getContentSize() + " bytes.");
            }

            if (denyAcl != null)
            {
              String[] denyAcls = new String[]{denyAcl};
              rval.setDenyACL(denyAcls);
            }

            contentType = object.getContentType();
            uri = convertToURI(object.getObjectId(),contentType);
          }
        }
        finally
        {
          object.release();
        }
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public RepositoryDocument getResponse()
    {
      return rval;
    }

    public Long getContentSize()
    {
      return contentSize;
    }
    
    public String getContentType()
    {
      return contentType;
    }
    
    public Long getActivityStartTime()
    {
      return activityStartTime;
    }

    public Long getActivityFileLength()
    {
      return activityFileLength;
    }

    public String getActivityStatus()
    {
      return activityStatus;
    }

    public String getActivityMessage()
    {
      return activityMessage;
    }

    public String getURI()
    {
      return uri;
    }
  }

  /** Process documents whose versions indicate they need processing.
  */
  @Override
  public void processDocuments(String[] documentIdentifiers, String[] documentVersions,
    IProcessActivity activities, DocumentSpecification spec, boolean[] scanOnly)
    throws ManifoldCFException, ServiceInterruption
  {
    Logging.connectors.debug("DCTM: Inside processDocuments");

    // Build the node/path cache
    SystemMetadataDescription sDesc = new SystemMetadataDescription(spec);

    int intObjectIdCount = documentIdentifiers.length;

    long currentTime;

    try
    {
      for (int intInc = 0; intInc < intObjectIdCount; intInc++)
      {
        // Since each livelink access is time-consuming, be sure that we abort if the job has gone inactive
        activities.checkJobStillActive();

        String documentIdentifier = documentIdentifiers[intInc];
        String versionString =  documentVersions[intInc];

        if (!scanOnly[intInc])
        {
          while (true)
          {
            boolean noSession = (session==null);
            getSession();

            // Create a temporary file for every attempt, because we don't know yet whether we'll need it or not -
            // but probably we will.
            File objFileTemp = File.createTempFile("_mc_dctm_", null);
            try
            {
              ProcessDocumentThread t = new ProcessDocumentThread(documentIdentifier,versionString,objFileTemp,
                sDesc);
              try
              {
                t.start();
                t.join();
                Throwable thr = t.getException();
                if (thr != null)
                {
                  if (thr instanceof RemoteException)
                    throw (RemoteException)thr;
                  else if (thr instanceof DocumentumException)
                    throw (DocumentumException)thr;
                  else if (thr instanceof ManifoldCFException)
                    throw (ManifoldCFException)thr;
                  else if (thr instanceof RuntimeException)
                    throw (RuntimeException)thr;
                  else
                    throw (Error)thr;
                }

                RepositoryDocument rd = t.getResponse();
                if (rd != null)
                {
                  long fileLength = t.getContentSize().longValue();
                  String contentType = t.getContentType();
                  if (activities.checkLengthIndexable(fileLength) && activities.checkMimeTypeIndexable(contentType))
                  {
                    // Log the fetch activity
                    if (t.getActivityStatus() != null)
                      activities.recordActivity(t.getActivityStartTime(),ACTIVITY_FETCH,
                      t.getActivityFileLength(),documentIdentifier,t.getActivityStatus(),t.getActivityMessage(),
                      null);

                    // Stream the data to the ingestion system
                    InputStream is = new FileInputStream(objFileTemp);
                    try
                    {
                      rd.setBinary(is, fileLength);
                      // Do the ingestion
                      activities.ingestDocument(documentIdentifier,versionString,
                        t.getURI(), rd);
                    }
                    finally
                    {
                      is.close();
                    }
                  }
                  else
                  {
                    rd = null;
                    // Log the fetch activity
                    if (t.getActivityStatus() != null)
                      activities.recordActivity(t.getActivityStartTime(),ACTIVITY_FETCH,
                      t.getActivityFileLength(),documentIdentifier,"REJECTED",null,
                      null);
                  }
                }
                
                if (rd == null)
                  activities.deleteDocument(documentIdentifier,versionString);
                
                // Abort the retry loop and go on to the next document
                break;

              }
              catch (InterruptedException e)
              {
                t.interrupt();
                throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
              }
              catch (RemoteException e)
              {
                Throwable e2 = e.getCause();
                if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
                  throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
                if (noSession)
                {
                  currentTime = System.currentTimeMillis();
                  throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
                }
                session = null;
                lastSessionFetch = -1L;
                // Go back around
              }
            }
            finally
            {
              objFileTemp.delete();
            }
          }

        }
      }
    }
    catch (DocumentumException e)
    {
      // Base our treatment on the kind of error it is.
      currentTime = System.currentTimeMillis();
      if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
      {
        Logging.connectors.warn("DCTM: Remote service interruption reading files: "+e.getMessage(),e);
        throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
      }
      throw new ManifoldCFException(e.getMessage(),e);
    }
    catch (java.io.InterruptedIOException e)
    {
      throw new ManifoldCFException("Interrupted IO: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    }
    catch (java.io.IOException e)
    {
      throw new ManifoldCFException("IO exception: "+e.getMessage(),e);
    }
  }

  /** Free a set of documents.  This method is called for all documents whose versions have been fetched using
  * the getDocumentVersions() method, including those that returned null versions.  It may be used to free resources
  * committed during the getDocumentVersions() method.  It is guaranteed to be called AFTER any calls to
  * processDocuments() for the documents in question.
  *@param documentIdentifiers is the set of document identifiers.
  *@param versions is the corresponding set of version identifiers (individual identifiers may be null).
  */
  @Override
  public void releaseDocumentVersions(String[] documentIdentifiers, String[] versions)
    throws ManifoldCFException


  {
    // Nothing to do
  }

  @Override
  public int getMaxDocumentRequest()
  {
    // 1 at a time, since this connector does not deal with documents en masse, but one at a time.
    return 1;
  }

    // UI support methods.
  //
  // These support methods come in two varieties.  The first bunch is involved in setting up connection configuration information.  The second bunch
  // is involved in presenting and editing document specification information for a job.  The two kinds of methods are accordingly treated differently,
  // in that the first bunch cannot assume that the current connector object is connected, while the second bunch can.  That is why the first bunch
  // receives a thread context argument for all UI methods, while the second bunch does not need one (since it has already been applied via the connect()
  // method, above).
    
  /** Output the configuration header section.
  * This method is called in the head section of the connector's configuration page.  Its purpose is to add the required tabs to the list, and to output any
  * javascript methods that might be needed by the configuration editing HTML.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputConfigurationHeader(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"DCTM.Docbase"));
    tabsArray.add(Messages.getString(locale,"DCTM.Webtop"));
    
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"function checkConfigForSave()\n"+
"{\n"+
"  if (editconnection.docbasename.value == \"\")\n"+
"  {\n"+
"    alert(\"Please supply the name of a Docbase\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"DCTM.Docbase") + "\");\n"+
"    editconnection.docbasename.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.docbaseusername.value == \"\")\n"+
"  {\n"+
"    alert(\"The connection requires a valid Documentum user name\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"DCTM.Docbase") + "\");\n"+
"    editconnection.docbaseusername.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.docbasepassword.value == \"\")\n"+
"  {\n"+
"    alert(\"The connection requires the Documentum user's password\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"DCTM.Docbase") + "\");\n"+
"    editconnection.docbasepassword.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.webtopbaseurl.value == \"\")\n"+
"  {\n"+
"    alert(\"Please specify the base url to a webtop to serve selected documents\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"DCTM.Webtop") + "\");\n"+
"    editconnection.webtopbaseurl.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"//-->\n"+
"</script>\n"
    );
  }
  
  /** Output the configuration body section.
  * This method is called in the body section of the connector's configuration page.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editconnection".
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputConfigurationBody(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters, String tabName)
    throws ManifoldCFException, IOException
  {
    String docbaseName = parameters.getParameter(CONFIG_PARAM_DOCBASE);
    if (docbaseName == null)
      docbaseName = "";
    String docbaseUserName = parameters.getParameter(CONFIG_PARAM_USERNAME);
    if (docbaseUserName == null)
      docbaseUserName = "";
    String docbasePassword = parameters.getObfuscatedParameter(CONFIG_PARAM_PASSWORD);
    if (docbasePassword == null)
      docbasePassword = "";
    else
      docbasePassword = out.mapPasswordToKey(docbasePassword);
    String docbaseDomain = parameters.getParameter(CONFIG_PARAM_DOMAIN);
    if (docbaseDomain == null)
      docbaseDomain = "";
    String webtopBaseUrl = parameters.getParameter(CONFIG_PARAM_WEBTOPBASEURL);
    if (webtopBaseUrl == null)
      webtopBaseUrl = "http://localhost/webtop/";

    // "Docbase" tab
    if (tabName.equals(Messages.getString(locale,"DCTM.Docbase")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.DocbaseName") + "</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"docbasename\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbaseName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.DocbaseUserName") + "</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"docbaseusername\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbaseUserName)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.DocbasePassword") + "</nobr></td><td class=\"value\"><input type=\"password\" size=\"32\" name=\"docbasepassword\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbasePassword)+"\"/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.DocbaseDomain") + "</nobr></td><td class=\"value\"><input type=\"text\" size=\"32\" name=\"docbasedomain\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbaseDomain)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Docbase tab
      out.print(
"<input type=\"hidden\" name=\"docbasename\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbaseName)+"\"/>\n"+
"<input type=\"hidden\" name=\"docbaseusername\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbaseUserName)+"\"/>\n"+
"<input type=\"hidden\" name=\"docbasepassword\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbasePassword)+"\"/>\n"+
"<input type=\"hidden\" name=\"docbasedomain\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(docbaseDomain)+"\"/>\n"
      );
    }

    // Webtop tab
    if (tabName.equals(Messages.getString(locale,"DCTM.Webtop")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.WebtopBaseURL") + "</nobr></td><td class=\"value\"><input type=\"text\" size=\"64\" name=\"webtopbaseurl\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(webtopBaseUrl)+"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Hiddens for Webtop tab
      out.print(
"<input type=\"hidden\" name=\"webtopbaseurl\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(webtopBaseUrl)+"\"/>\n"
      );
    }
  }
  
  /** Process a configuration post.
  * This method is called at the start of the connector's configuration page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the configuration parameters accordingly.
  * The name of the posted form is "editconnection".
  *@param threadContext is the local thread context.
  *@param variableContext is the set of variables available from the post, including binary file post information.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the connection (and cause a redirection to an error page).
  */
  @Override
  public String processConfigurationPost(IThreadContext threadContext, IPostParameters variableContext,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException
  {
    String docbaseName = variableContext.getParameter("docbasename");
    if (docbaseName != null)
      parameters.setParameter(CONFIG_PARAM_DOCBASE,docbaseName);

    String docbaseUserName = variableContext.getParameter("docbaseusername");
    if (docbaseUserName != null)
      parameters.setParameter(CONFIG_PARAM_USERNAME,docbaseUserName);

    String docbasePassword = variableContext.getParameter("docbasepassword");
    if (docbasePassword != null)
      parameters.setObfuscatedParameter(CONFIG_PARAM_PASSWORD,variableContext.mapKeyToPassword(docbasePassword));

    String docbaseDomain = variableContext.getParameter("docbasedomain");
    if (docbaseDomain != null)
      parameters.setParameter(CONFIG_PARAM_DOMAIN,docbaseDomain);

    String webtopBaseUrl = variableContext.getParameter("webtopbaseurl");
    if (webtopBaseUrl != null)
      parameters.setParameter(CONFIG_PARAM_WEBTOPBASEURL,webtopBaseUrl);

    return null;
  }
  
  /** View configuration.
  * This method is called in the body section of the connector's view configuration page.  Its purpose is to present the connection information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param threadContext is the local thread context.
  *@param out is the output to which any HTML should be sent.
  *@param parameters are the configuration parameters, as they currently exist, for this connection being configured.
  */
  @Override
  public void viewConfiguration(IThreadContext threadContext, IHTTPOutput out,
    Locale locale, ConfigParams parameters)
    throws ManifoldCFException, IOException
  {
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"DCTM.Parameters") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"
    );
    Iterator iter = parameters.listParameters();
    while (iter.hasNext())
    {
      String param = (String)iter.next();
      String value = parameters.getParameter(param);
      if (param.length() >= "password".length() && param.substring(param.length()-"password".length()).equalsIgnoreCase("password"))
      {
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"=********</nobr><br/>\n"
        );
      }
      else if (param.length() >="keystore".length() && param.substring(param.length()-"keystore".length()).equalsIgnoreCase("keystore"))
      {
        IKeystoreManager kmanager = KeystoreManagerFactory.make("",value);
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"=&lt;"+Integer.toString(kmanager.getContents().length)+" certificate(s)&gt;</nobr><br/>\n"
        );
      }
      else
      {
        out.print(
"      <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(param)+"="+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(value)+"</nobr><br/>\n"
        );
      }
    }
    out.print(
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
    );
  }
  
  /** Output the specification header section.
  * This method is called in the head section of a job page which has selected a repository connection of the current type.  Its purpose is to add the required tabs
  * to the list, and to output any javascript methods that might be needed by the job editing HTML.
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  *@param tabsArray is an array of tab names.  Add to this array any tab names that are specific to the connector.
  */
  @Override
  public void outputSpecificationHeader(IHTTPOutput out, Locale locale, DocumentSpecification ds, List<String> tabsArray)
    throws ManifoldCFException, IOException
  {
    tabsArray.add(Messages.getString(locale,"DCTM.Paths"));
    tabsArray.add(Messages.getString(locale,"DCTM.DocumentTypes"));
    tabsArray.add(Messages.getString(locale,"DCTM.ContentTypes"));
    tabsArray.add(Messages.getString(locale,"DCTM.ContentLength"));
    tabsArray.add(Messages.getString(locale,"DCTM.Security"));
    tabsArray.add(Messages.getString(locale,"DCTM.PathMetadata"));
    out.print(
"<script type=\"text/javascript\">\n"+
"<!--\n"+
"\n"+
"function checkSpecification()\n"+
"{\n"+
"  if (editjob.specmaxdoclength.value != \"\" && !isInteger(editjob.specmaxdoclength.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.MaximumDocumentLengthMustBeNullOrAnInteger") + "\");\n"+
"    editjob.specmaxdoclength.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function SpecOp(n, opValue, anchorvalue)\n"+
"{\n"+
"  eval(\"editjob.\"+n+\".value = \\\"\"+opValue+\"\\\"\");\n"+
"  postFormSetAnchor(anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecAddToPath(anchorvalue)\n"+
"{\n"+
"  if (editjob.pathaddon.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.SelectAFolderFirst") + "\");\n"+
"    editjob.pathaddon.focus();\n"+
"    return;\n"+
"  }\n"+
"\n"+
"  SpecOp(\"pathop\",\"AddToPath\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecAddToken(anchorvalue)\n"+
"{\n"+
"  if (editjob.spectoken.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.NullTokensNotAllowed") + "\");\n"+
"    editjob.spectoken.focus();\n"+
"    return;\n"+
"  }\n"+
"\n"+
"  SpecOp(\"accessop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"function SpecAddMapping(anchorvalue)\n"+
"{\n"+
"  if (editjob.specmatch.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.EnterASpecificationFirst") + "\");\n"+
"    editjob.specmatch.focus();\n"+
"    return;\n"+
"  }\n"+
"  if (!isRegularExpression(editjob.specmatch.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.SpecificationMustBeValidRegularExpression") + "\");\n"+
"    editjob.specmatch.focus();\n"+
"    return false;\n"+
"  }\n"+
"  SpecOp(\"specmappingop\",\"Add\",anchorvalue);\n"+
"  }\n"+
"\n"+
"//-->\n"+
"</script>\n"
    );
  }
  
  /** Output the specification body section.
  * This method is called in the body section of a job page which has selected a repository connection of the current type.  Its purpose is to present the required form elements for editing.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html>, <body>, and <form> tags.  The name of the
  * form is "editjob".
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  *@param tabName is the current tab name.
  */
  @Override
  public void outputSpecificationBody(IHTTPOutput out, Locale locale, DocumentSpecification ds, String tabName)
    throws ManifoldCFException, IOException
  {
    int i;
    int k;

    // Paths tab
    if (tabName.equals(Messages.getString(locale,"DCTM.Paths")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Now, loop through paths
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals(CONFIG_PARAM_LOCATION))
        {
          String pathDescription = "_" + Integer.toString(k);
          String pathOpName = "pathop" + pathDescription;
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+"specpath"+pathDescription+"\" value=\""+sn.getAttributeValue("path")+"\"/>\n"+
"      <input type=\"hidden\" name=\""+pathOpName+"\" value=\"\"/>\n"+
"      <a name=\""+"path_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Delete\" onClick='Javascript:SpecOp(\""+pathOpName+"\",\"Delete\",\"path_"+Integer.toString(k)+"\")' alt=\"" + Messages.getAttributeString(locale,"DCTM.DeletePath")+Integer.toString(k)+"\"/>\n"+
"      </a>&nbsp;\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+

"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(sn.getAttributeValue("path"))+"\n"+
"    </td>\n"+
"  </tr>\n"
          );
          k++;
        }
      }

      if (k == 0)
      {
        out.print(
"  <tr>\n"+
"    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"DCTM.NoSpecificCabinetFolderPathsGiven") + "</td>\n"+
"  </tr>\n"
        );
      }

      out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\"pathcount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
	
      String pathSoFar = (String)currentContext.get("specpath");
      if (pathSoFar == null)
        pathSoFar = "/";

      // Grab next folder/project list
      try
      {
        String[] childList = getChildFolderNames(pathSoFar);
        if (childList == null)
        {
          // Illegal path - set it back
          pathSoFar = "/";
          childList = getChildFolderNames(pathSoFar);
          if (childList == null)
            throw new ManifoldCFException("Can't find any children for root folder");
        }
        
        out.print(
"      <a name=\""+"path_"+Integer.toString(k)+"\">\n"+
"        <input type=\"hidden\" name=\"specpath\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathSoFar)+"\"/>\n"+
"        <input type=\"hidden\" name=\"pathop\" value=\"\"/>\n"+
"        <input type=\"button\" value=\"Add\" alt=\"" + Messages.getAttributeString(locale,"DCTM.AddPath") + "\" onClick='Javascript:SpecOp(\"pathop\",\"Add\",\"path_"+Integer.toString(k+1)+"\")'/>&nbsp;\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(pathSoFar)+"\n"
        );
        if (pathSoFar.length() > 1)
        {
          out.print(
"      <input type=\"button\" value=\"-\" alt=\"" + Messages.getAttributeString(locale,"DCTM.RemoveFromPath") + "\" onClick='Javascript:SpecOp(\"pathop\",\"Up\",\"path_"+Integer.toString(k)+"\")'/>\n"
          );
        }
        if (childList.length > 0)
        {
          out.print(
"      <input type=\"button\" value=\"+\" alt=\"" + Messages.getAttributeString(locale,"DCTM.AddToPath") + "\" onClick='Javascript:SpecAddToPath(\"path_"+Integer.toString(k)+"\")'/>&nbsp;\n"+
"      <select multiple=\"false\" name=\"pathaddon\" size=\"2\">\n"+
"        <option value=\"\" selected=\"selected\">" + Messages.getBodyString(locale,"DCTM.PickAFolder") + "</option>\n"
          );
          int j = 0;
          while (j < childList.length)
          {
            out.print(
"        <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(childList[j])+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(childList[j])+"</option>\n"
            );
            j++;
          }

          out.print(
"      </select>\n"
          );
        }
      }
      catch (ManifoldCFException e)
      {
        out.println(org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage()));
      }
      catch (ServiceInterruption e)
      {
        out.println("Service interruption or invalid credentials - check your repository connection: "+e.getMessage());
      }
      
      out.print(
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      // Now, loop through paths
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals(CONFIG_PARAM_LOCATION))
        {
          String pathDescription = "_" + Integer.toString(k);
          out.print(
"<input type=\"hidden\" name=\""+"specpath"+pathDescription+"\" value=\""+sn.getAttributeValue("path")+"\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\"pathcount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }

    // Security tab
    // Find whether security is on or off
    i = 0;
    boolean securityOn = true;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("security"))
      {
        String securityValue = sn.getAttributeValue("value");
        if (securityValue.equals("off"))
          securityOn = false;
        else if (securityValue.equals("on"))
          securityOn = true;
      }
    }

    if (tabName.equals(Messages.getString(locale,"DCTM.Security")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.Security2") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"1\">\n"+
"      <input type=\"radio\" name=\"specsecurity\" value=\"on\" "+((securityOn)?"checked=\"true\"":"")+" />Enabled&nbsp;\n"+
"      <input type=\"radio\" name=\"specsecurity\" value=\"off\" "+((securityOn==false)?"checked=\"true\"":"")+" />Disabled\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Finally, go through forced ACL
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("access"))
        {
          String accessDescription = "_"+Integer.toString(k);
          String accessOpName = "accessop"+accessDescription;
          String token = sn.getAttributeValue("token");
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+accessOpName+"\" value=\"\"/>\n"+
"      <input type=\"hidden\" name=\""+"spectoken"+accessDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(token)+"\"/>\n"+
"      <a name=\""+"token_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Delete\" alt=\""+Messages.getAttributeString(locale,"DCTM.DeleteAccessToken")+Integer.toString(k)+"\" onClick='Javascript:SpecOp(\""+accessOpName+"\",\"Delete\",\"token_"+Integer.toString(k)+"\")'/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(token)+"\n"+
"    </td>\n"+
"  </tr>\n"
          );
          k++;
        }
      }
      if (k == 0)
      {
        out.print(
"  <tr>\n"+
"    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"DCTM.NoAccessTokensPresent") + "</td>\n"+
"  </tr>\n"
        );
      }
      out.print(
"  <tr><td class=\"lightseparator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\"tokencount\" value=\""+Integer.toString(k)+"\"/>\n"+
"      <input type=\"hidden\" name=\"accessop\" value=\"\"/>\n"+
"      <a name=\""+"token_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Add\" alt=\"" + Messages.getAttributeString(locale,"DCTM.AddAccessToken") + "\" onClick='Javascript:SpecAddToken(\"token_"+Integer.toString(k+1)+"\")'/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"text\" size=\"30\" name=\"spectoken\" value=\"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"specsecurity\" value=\""+(securityOn?"on":"off")+"\"/>\n"
      );
      // Finally, go through forced ACL
      i = 0;
      k = 0;
      while (i < ds.getChildCount())
      {
        SpecificationNode sn = ds.getChild(i++);
        if (sn.getType().equals("access"))
        {
          String accessDescription = "_"+Integer.toString(k);
          String token = sn.getAttributeValue("token");
          out.print(
"<input type=\"hidden\" name=\""+"spectoken"+accessDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(token)+"\"/>\n"
          );
          k++;
        }
      }

      out.print(
"<input type=\"hidden\" name=\"tokencount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }

    // Document Types tab

    // First, build a hash map containing all the currently selected document types
    HashMap dtMap = new HashMap();
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(CONFIG_PARAM_OBJECTTYPE))
      {
        String token = sn.getAttributeValue("token");
        if (token != null && token.length() > 0)
        {
          String isAllString = sn.getAttributeValue("all");
          if (isAllString != null && isAllString.equals("true"))
            dtMap.put(token,new Boolean(true));
          else
          {
            HashMap attrMap = new HashMap();
            // Go through the children and look for attribute records
            int kk = 0;
            while (kk < sn.getChildCount())
            {
              SpecificationNode dsn = sn.getChild(kk++);
              if (dsn.getType().equals(CONFIG_PARAM_ATTRIBUTENAME))
              {
                String attr = dsn.getAttributeValue("attrname");
                attrMap.put(attr,attr);
              }
            }
            dtMap.put(token,attrMap);
          }
        }
      }
    }

    if (tabName.equals(Messages.getString(locale,"DCTM.DocumentTypes")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      
      // Need to catch potential license exception here
      try
      {
        String[] strarrObjTypes = getObjectTypes();
	int ii = 0;
        while (ii < strarrObjTypes.length)
        {
          String strObjectType = strarrObjTypes[ii++];
          if (strObjectType != null && strObjectType.length() > 0)
	  {
            out.print(
"  <tr>\n"+
"    <td class=\"value\">\n"
            );
            Object o = dtMap.get(strObjectType);
            if (o == null)
            {
              out.print(
"      <input type=\"checkbox\" name=\"specfiletype\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(strObjectType)+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(strObjectType)+"</input>\n"
              );
            }
            else
            {
              out.print(
"      <input type=\"checkbox\" name=\"specfiletype\" checked=\"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(strObjectType)+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(strObjectType)+"</input>\n"
              );

            }
            out.print(
"    </td>\n"+
"    <td class=\"value\">\n"
            );
            boolean isAll = false;
            HashMap attrMap = null;
            if (o instanceof Boolean)
            {
              isAll = ((Boolean)o).booleanValue();
              attrMap = new HashMap();
            }
            else
            {
              isAll = false;
              attrMap = (HashMap)o;
            }
            out.print(
"      <input type=\"checkbox\" name=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape("specfileallattrs_"+strObjectType)+"\" value=\"true\" "+(isAll?"checked=\"\"":"")+"/>&nbsp;All metadata<br/>\n"+
"      <select multiple=\"true\" name=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape("specfileattrs_"+strObjectType)+"\" size=\"3\">\n"
            );
            // Get the attributes for this data type
            String[] values = getIngestableAttributes(strObjectType);
            int iii = 0;
            while (iii < values.length)
            {
              String option = values[iii++];
              if (attrMap != null && attrMap.get(option) != null)
              {
                // Selected
                out.print(
"        <option selected=\"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(option)+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(option)+"</option>\n"
                );
              }
              else
              {
                // Unselected
                out.print(
"        <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(option)+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(option)+"</option>\n"
                );
              }
            }
            out.print(
"      </select>\n"+
"    </td>\n"+
"  </tr>\n"
            );
      	  }
	}
      }
      catch (ManifoldCFException e)
      {
        out.print(
"  <tr>\n"+
"    <td class=\"message\" colspan=\"2\">\n"+
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage())+"\n"+
"    </td>\n"+
"  </tr>\n"
        );
      }
      catch (ServiceInterruption e)
      {
        out.print(
"  <tr>\n"+
"    <td class=\"message\" colspan=\"2\">\n"+
"		" + Messages.getBodyString(locale,"DCTM.ServiceInterruptionOrInvalidCredentials")+
"    </td>\n"+
"  </tr>\n"
        );
      }
      out.print(
"</table>\n"
      );
    }
    else
    {
      Iterator iter = dtMap.keySet().iterator();
      while (iter.hasNext())
      {
        String strObjectType = (String)iter.next();
        Object o = dtMap.get(strObjectType);
        out.print(
"<input type=\"hidden\" name=\"specfiletype\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(strObjectType)+"\"/>\n"
        );
        if (o instanceof Boolean)
        {
          Boolean b = (Boolean)o;
          out.print(
"<input type=\"hidden\" name=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape("specfileallattrs_"+strObjectType)+"\" value=\""+(b.booleanValue()?"true":"false")+"\"/>\n"
          );
        }
        else
        {
          HashMap map = (HashMap)o;
          Iterator iter2 = map.keySet().iterator();
          while (iter2.hasNext())
          {
            String attrName = (String)iter2.next();
            out.print(
"<input type=\"hidden\" name=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape("specfileattrs_"+strObjectType)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(attrName)+"\"/>\n"
            );
          }
        }
      }
    }

    // Content types tab

    // First, build a hash map containing all the currently selected document types
    HashMap ctMap = null;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(CONFIG_PARAM_FORMAT))
      {
        String token = sn.getAttributeValue("value");
        if (token != null && token.length() > 0)
        {
          if (ctMap == null)
            ctMap = new HashMap();
          ctMap.put(token,token);
        }
      }
    }

    if (tabName.equals(Messages.getString(locale,"DCTM.ContentTypes")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Need to catch potential license exception here
      try
      {
        String[] strarrMimeTypes = getContentTypes();
	int ii = 0;
        while (ii < strarrMimeTypes.length)
        {
          String strMimeType = strarrMimeTypes[ii++];
          if (strMimeType != null && strMimeType.length() > 0)
	  {
            out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"
            );
            if (ctMap == null || ctMap.get(strMimeType) != null)
            {
              out.print(
"      <input type=\"checkbox\" name=\"specmimetype\" checked=\"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(strMimeType)+"\"></input>\n"
              );
            }
            else
            {
              out.print(
"      <input type=\"checkbox\" name=\"specmimetype\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(strMimeType)+"\"></input>\n"
              );
            }
            out.print(
"    </td>\n"+
"    <td class=\"value\">\n"+
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(strMimeType)+"\n"+
"    </td>\n"+
"  </tr>\n"
            );
      	  }
	}
      }
      catch (ManifoldCFException e)
      {
        out.print(
"  <tr>\n"+
"    <td class=\"message\" colspan=\"2\">\n"+
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(e.getMessage())+"\n"+
"    </td>\n"+
"  </tr>\n"
        );
      }
      catch (ServiceInterruption e)
      {
        out.print(
"  <tr>\n"+
"    <td class=\"message\" colspan=\"2\">\n"+
"		" + Messages.getBodyString(locale,"DCTM.ServiceInterruptionOrInvalidCredentials") +
"    </td>\n"+
"  </tr>\n"
        );
      }
      out.print(
"</table>\n"
      );
    }
    else
    {
      if (ctMap != null)
      {
        Iterator iter = ctMap.keySet().iterator();
        while (iter.hasNext())
        {
          String strMimeType = (String)iter.next();
          out.print(
"<input type=\"hidden\" name=\"specmimetype\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(strMimeType)+"\"/>\n"
          );
        }
      }
    }


    // The Content Length tab

    // Search for max document size
    String maxDocLength = "";
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(CONFIG_PARAM_MAXLENGTH))
      {
        maxDocLength = sn.getAttributeValue("value");
      }
    }

    if (tabName.equals(Messages.getString(locale,"DCTM.ContentLength")))
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"separator\" colspan=\"2\"><hr/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.ContentLength") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\"specmaxdoclength\" type=\"text\" size=\"10\" value=\""+maxDocLength+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"specmaxdoclength\" value=\""+maxDocLength+"\"/>\n"
      );
    }


    // Path metadata tab

    // Find the path-value metadata attribute name
    i = 0;
    String pathNameAttribute = "";
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(CONFIG_PARAM_PATHNAMEATTRIBUTE))
      {
        pathNameAttribute = sn.getAttributeValue("value");
      }
    }

    // Find the path-value mapping data
    i = 0;
    org.apache.manifoldcf.crawler.connectors.DCTM.MatchMap matchMap = new org.apache.manifoldcf.crawler.connectors.DCTM.MatchMap();
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(CONFIG_PARAM_PATHMAP))
      {
        String pathMatch = sn.getAttributeValue("match");
        String pathReplace = sn.getAttributeValue("replace");
        matchMap.appendMatchPair(pathMatch,pathReplace);
      }
    }

    if (tabName.equals(Messages.getString(locale,"DCTM.PathMetadata")))
    {
      out.print(
"<input type=\"hidden\" name=\"specmappingcount\" value=\""+Integer.toString(matchMap.getMatchCount())+"\"/>\n"+
"<input type=\"hidden\" name=\"specmappingop\" value=\"\"/>\n"+
"\n"+
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"DCTM.PathAttributeName") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"+
"      <input type=\"text\" name=\"specpathnameattribute\" size=\"20\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathNameAttribute)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"
      );
      i = 0;
      while (i < matchMap.getMatchCount())
      {
        String matchString = matchMap.getMatchString(i);
        String replaceString = matchMap.getReplaceString(i);
        out.print(
"  <tr>\n"+
"    <td class=\"description\"><input type=\"hidden\" name=\""+"specmappingop_"+Integer.toString(i)+"\" value=\"\"/>\n"+
"      <a name=\""+"mapping_"+Integer.toString(i)+"\">\n"+
"        <input type=\"button\" onClick='Javascript:SpecOp(\"specmappingop_"+Integer.toString(i)+"\",\"" + Messages.getAttributeJavascriptString(locale,"DCTM.Delete") + "\",\"mapping_"+Integer.toString(i)+"\")' alt=\"" + Messages.getAttributeString(locale,"DCTM.DeleteMapping") + Integer.toString(i)+"\" value=\"Delete\"/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\"><input type=\"hidden\" name=\"specmatch_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(matchString)+"\"/>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(matchString)+"</td>\n"+
"    <td class=\"value\">==></td>\n"+
"    <td class=\"value\"><input type=\"hidden\" name=\"specreplace_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(replaceString)+"\"/>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(replaceString)+"</td>\n"+
"  </tr>\n"
        );
        i++;
      }
      if (i == 0)
      {
        out.print(
"  <tr><td colspan=\"4\" class=\"message\">" + Messages.getBodyString(locale,"DCTM.NoMappingsSpecified") + "</td></tr>\n"
        );
      }
      out.print(
"  <tr><td class=\"lightseparator\" colspan=\"4\"><hr/></td></tr>\n"+
"\n"+
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <a name=\""+"mapping_"+Integer.toString(i)+"\">\n"+
"        <input type=\"button\" onClick='Javascript:SpecAddMapping(\"mapping_"+Integer.toString(i+1)+"\")' alt=\"" + Messages.getAttributeString(locale,"DCTM.AddToMappings") + "\" value=\"" + Messages.getAttributeString(locale,"DCTM.Add") + "\"/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">" + Messages.getBodyString(locale,"DCTM.MatchRegexp") + "&nbsp;<input type=\"text\" name=\"specmatch\" size=\"32\" value=\"\"/></td>\n"+
"    <td class=\"value\">==></td>\n"+
"    <td class=\"value\">" + Messages.getBodyString(locale,"DCTM.ReplaceString") + "&nbsp;<input type=\"text\" name=\"specreplace\" size=\"32\" value=\"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\"specmappingcount\" value=\""+Integer.toString(matchMap.getMatchCount())+"\"/>\n"+
"<input type=\"hidden\" name=\"specpathnameattribute\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathNameAttribute)+"\"/>\n"
      );
      i = 0;
      while (i < matchMap.getMatchCount())
      {
        String matchString = matchMap.getMatchString(i);
        String replaceString = matchMap.getReplaceString(i);
        out.print(
"<input type=\"hidden\" name=\""+"specmatch_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(matchString)+"\"/>\n"+
"<input type=\"hidden\" name=\""+"specreplace_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(replaceString)+"\"/>\n"
        );
        i++;
      }
    }
  }
  
  /** Process a specification post.
  * This method is called at the start of job's edit or view page, whenever there is a possibility that form data for a connection has been
  * posted.  Its purpose is to gather form information and modify the document specification accordingly.
  * The name of the posted form is "editjob".
  *@param variableContext contains the post data, including binary file-upload information.
  *@param ds is the current document specification for this job.
  *@return null if all is well, or a string error message if there is an error that should prevent saving of the job (and cause a redirection to an error page).
  */
  @Override
  public String processSpecificationPost(IPostParameters variableContext, Locale locale, DocumentSpecification ds)
    throws ManifoldCFException
  {
    String x = variableContext.getParameter("pathcount");
    if (x != null)
    {
      // Delete all path specs first
      int i = 0;
      while (i < ds.getChildCount())
      {
      	SpecificationNode sn = ds.getChild(i);
      	if (sn.getType().equals(CONFIG_PARAM_LOCATION))
          ds.removeChild(i);
      	else
          i++;
      }

      // Find out how many children were sent
      int pathCount = Integer.parseInt(x);
      // Gather up these
      i = 0;
      while (i < pathCount)
      {
      	String pathDescription = "_"+Integer.toString(i);
      	String pathOpName = "pathop"+pathDescription;
      	x = variableContext.getParameter(pathOpName);
      	if (x != null && x.equals("Delete"))
      	{
          // Skip to the next
          i++;
          continue;
      	}
      	// Path inserts won't happen until the very end
      	String path = variableContext.getParameter("specpath"+pathDescription);
      	SpecificationNode node = new SpecificationNode(CONFIG_PARAM_LOCATION);
      	node.setAttribute("path",path);
      	ds.addChild(ds.getChildCount(),node);
      	i++;
      }

      // See if there's a global add operation
      String op = variableContext.getParameter("pathop");
      if (op != null && op.equals("Add"))
      {
      	String path = variableContext.getParameter("specpath");
      	SpecificationNode node = new SpecificationNode(CONFIG_PARAM_LOCATION);
      	node.setAttribute("path",path);
      	ds.addChild(ds.getChildCount(),node);
      }
      else if (op != null && op.equals("Up"))
      {
      	// Strip off end
      	String path = variableContext.getParameter("specpath");
      	int k = path.lastIndexOf("/");
      	if (k != -1)
          path = path.substring(0,k);
      	if (path.length() == 0)
          path = "/";
      	currentContext.save("specpath",path);
      }
      else if (op != null && op.equals("AddToPath"))
      {
      	String path = variableContext.getParameter("specpath");
      	String addon = variableContext.getParameter("pathaddon");
      	if (addon != null && addon.length() > 0)
      	{
          if (path.length() == 1)
            path = "/" + addon;
          else
            path += "/" + addon;
      	}
      	currentContext.save("specpath",path);
      }
    }

    x = variableContext.getParameter("specsecurity");
    if (x != null)
    {
      // Delete all security entries first
      int i = 0;
      while (i < ds.getChildCount())
      {
      	SpecificationNode sn = ds.getChild(i);
      	if (sn.getType().equals("security"))
          ds.removeChild(i);
      	else
          i++;
      }

      SpecificationNode node = new SpecificationNode("security");
      node.setAttribute("value",x);
      ds.addChild(ds.getChildCount(),node);

    }

    x = variableContext.getParameter("tokencount");
    if (x != null)
    {
      // Delete all file specs first
      int i = 0;
      while (i < ds.getChildCount())
      {
      	SpecificationNode sn = ds.getChild(i);
      	if (sn.getType().equals("access"))
          ds.removeChild(i);
      	else
          i++;
      }

      int accessCount = Integer.parseInt(x);
      i = 0;
      while (i < accessCount)
      {
      	String accessDescription = "_"+Integer.toString(i);
      	String accessOpName = "accessop"+accessDescription;
      	x = variableContext.getParameter(accessOpName);
      	if (x != null && x.equals("Delete"))
      	{
          // Next row
          i++;
          continue;
      	}
      	// Get the stuff we need
      	String accessSpec = variableContext.getParameter("spectoken"+accessDescription);
      	SpecificationNode node = new SpecificationNode("access");
      	node.setAttribute("token",accessSpec);
      	ds.addChild(ds.getChildCount(),node);
      	i++;
      }

      String op = variableContext.getParameter("accessop");
      if (op != null && op.equals("Add"))
      {
      	String accessspec = variableContext.getParameter("spectoken");
      	SpecificationNode node = new SpecificationNode("access");
      	node.setAttribute("token",accessspec);
      	ds.addChild(ds.getChildCount(),node);
      }
    }

    String[] y = variableContext.getParameterValues("specfiletype");
    if (y != null)
    {
      // Delete all file specs first
      int i = 0;
      while (i < ds.getChildCount())
      {
      	SpecificationNode sn = ds.getChild(i);
      	if (sn.getType().equals(CONFIG_PARAM_OBJECTTYPE))
          ds.removeChild(i);
      	else
          i++;
      }

      // Loop through specs
      i = 0;
      while (i < y.length)
      {
      	String fileType = y[i++];
      	SpecificationNode node = new SpecificationNode(CONFIG_PARAM_OBJECTTYPE);
      	node.setAttribute("token",fileType);
      	String isAll = variableContext.getParameter("specfileallattrs_"+fileType);
      	if (isAll != null)
          node.setAttribute("all",isAll);
      	String[] z = variableContext.getParameterValues("specfileattrs_"+fileType);
      	if (z != null)
      	{
          int k = 0;
          while (k < z.length)
          {
            SpecificationNode attrNode = new SpecificationNode(CONFIG_PARAM_ATTRIBUTENAME);
            attrNode.setAttribute("attrname",z[k++]);
            node.addChild(node.getChildCount(),attrNode);
          }
      	}
      	ds.addChild(ds.getChildCount(),node);
      }
    }

    y = variableContext.getParameterValues("specmimetype");
    if (y != null)
    {
      // Delete all file specs first
      int i = 0;
      while (i < ds.getChildCount())
      {
      	SpecificationNode sn = ds.getChild(i);
      	if (sn.getType().equals(CONFIG_PARAM_FORMAT))
          ds.removeChild(i);
      	else
          i++;
      }

      // Loop through specs
      i = 0;
      while (i < y.length)
      {
      	String fileType = y[i++];
      	SpecificationNode node = new SpecificationNode(CONFIG_PARAM_FORMAT);
      	node.setAttribute("value",fileType);
      	ds.addChild(ds.getChildCount(),node);
      }
    }

    x = variableContext.getParameter("specmaxdoclength");
    if (x != null)
    {
      // Delete all security entries first
      int i = 0;
      while (i < ds.getChildCount())
      {
      	SpecificationNode sn = ds.getChild(i);
      	if (sn.getType().equals(CONFIG_PARAM_MAXLENGTH))
          ds.removeChild(i);
      	else
          i++;
      }

      if (x.length() > 0)
      {
      	SpecificationNode node = new SpecificationNode(CONFIG_PARAM_MAXLENGTH);
      	node.setAttribute("value",x);
      	ds.addChild(ds.getChildCount(),node);
      }
    }

    String xc = variableContext.getParameter("specpathnameattribute");
    if (xc != null)
    {
      // Delete old one
      int i = 0;
      while (i < ds.getChildCount())
      {
      	SpecificationNode sn = ds.getChild(i);
      	if (sn.getType().equals(CONFIG_PARAM_PATHNAMEATTRIBUTE))
          ds.removeChild(i);
      	else
          i++;
      }
      if (xc.length() > 0)
      {
      	SpecificationNode node = new SpecificationNode(CONFIG_PARAM_PATHNAMEATTRIBUTE);
      	node.setAttribute("value",xc);
      	ds.addChild(ds.getChildCount(),node);
      }
    }

    xc = variableContext.getParameter("specmappingcount");
    if (xc != null)
    {
      // Delete old spec
      int i = 0;
      while (i < ds.getChildCount())
      {
      	SpecificationNode sn = ds.getChild(i);
      	if (sn.getType().equals(CONFIG_PARAM_PATHMAP))
          ds.removeChild(i);
      	else
          i++;
      }

      // Now, go through the data and assemble a new list.
      int mappingCount = Integer.parseInt(xc);

      // Gather up these
      i = 0;
      while (i < mappingCount)
      {
      	String pathDescription = "_"+Integer.toString(i);
      	String pathOpName = "specmappingop"+pathDescription;
      	xc = variableContext.getParameter(pathOpName);
      	if (xc != null && xc.equals("Delete"))
      	{
          // Skip to the next
          i++;
          continue;
      	}
      	// Inserts won't happen until the very end
      	String match = variableContext.getParameter("specmatch"+pathDescription);
      	String replace = variableContext.getParameter("specreplace"+pathDescription);
      	SpecificationNode node = new SpecificationNode(CONFIG_PARAM_PATHMAP);
      	node.setAttribute("match",match);
      	node.setAttribute("replace",replace);
      	ds.addChild(ds.getChildCount(),node);
      	i++;
      }

      // Check for add
      xc = variableContext.getParameter("specmappingop");
      if (xc != null && xc.equals("Add"))
      {
      	String match = variableContext.getParameter("specmatch");
      	String replace = variableContext.getParameter("specreplace");
      	SpecificationNode node = new SpecificationNode(CONFIG_PARAM_PATHMAP);
      	node.setAttribute("match",match);
      	node.setAttribute("replace",replace);
      	ds.addChild(ds.getChildCount(),node);
      }
    }
    return null;
  }
  
  /** View specification.
  * This method is called in the body section of a job's view page.  Its purpose is to present the document specification information to the user.
  * The coder can presume that the HTML that is output from this configuration will be within appropriate <html> and <body> tags.
  *@param out is the output to which any HTML should be sent.
  *@param ds is the current document specification for this job.
  */
  @Override
  public void viewSpecification(IHTTPOutput out, Locale locale, DocumentSpecification ds)
    throws ManifoldCFException, IOException
  {
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"
    );
    int i = 0;
    boolean seenAny = false;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(CONFIG_PARAM_LOCATION))
      {
        if (seenAny == false)
        {
          seenAny = true;
          out.print(
"    <td class=\"description\">" + Messages.getBodyString(locale,"DCTM.CabinetFolderPaths") + "</td>\n"+
"    <td class=\"value\">\n"
          );
        }
        out.print(
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(sn.getAttributeValue("path"))+"<br/>\n"
        );
      }
    }

    if (seenAny)
    {
      out.print(
"    </td>\n"
      );
    }
    else
    {
      out.print(
"    <td colspan=\"2\" class=\"message\">" + Messages.getBodyString(locale,"DCTM.NoCabinetFolderPathsSpecified") + "</td>\n"
      );
    }
    out.print(
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"
    );
    seenAny = false;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(CONFIG_PARAM_OBJECTTYPE))
      {
        if (seenAny == false)
        {
          out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.ObjectTypes") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <table class=\"displaytable\">\n"
          );
          seenAny = true;
        }
        String strObjectType = sn.getAttributeValue("token");
        String isAll = sn.getAttributeValue("all");
        out.print(
"        <tr>\n"+
"          <td class=\"value\">\n"+
"            "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(strObjectType)+"\n"+
"          </td>\n"+
"          <td class=\"value\">\n"
        );
        if (isAll != null && isAll.equals("true"))
          out.print(
"            <nobr>" + Messages.getBodyString(locale,"DCTM.allMetadataAttributes") + "</nobr>\n"
          );
        else
        {
          int k = 0;
          while (k < sn.getChildCount())
          {
            SpecificationNode dsn = sn.getChild(k++);
            if (dsn.getType().equals(CONFIG_PARAM_ATTRIBUTENAME))
            {
              String attrName = dsn.getAttributeValue("attrname");
              out.print(
"            "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(attrName)+"<br/>\n"
              );
            }
          }
        }
        
        out.print(
"          </td>\n"+
"        </tr>\n"
        );
      }
    }
    if (seenAny)
    {
      out.print(
"      </table>\n"+
"    </td>\n"
      );
    }
    else
    {
      out.print(
"    <td colspan=\"2\" class=\"message\">" + Messages.getBodyString(locale,"DCTM.NoDocumentTypesSpecified") + "</td>\n"
      );
    }
    out.print(
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"
    );
    seenAny = false;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(CONFIG_PARAM_FORMAT))
      {
        if (seenAny == false)
        {
          out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.ContentTypes2") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
          );
          seenAny = true;
        }
        String strContentType = sn.getAttributeValue("value");
        out.print(
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(strContentType)+"<br/>\n"
        );
      }
    }
    if (seenAny)
    {
      out.print(
"    </td>\n"
      );
    }
    else
    {
      out.print(
"    <td colspan=\"2\" class=\"message\">" + Messages.getBodyString(locale,"DCTM.NoMimeTypesSpecified") + "</td>\n"
      );
    }
    out.print(
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    // Find max document length
    i = 0;
    String maxDocumentLength = "unlimited";
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(CONFIG_PARAM_MAXLENGTH))
      {
        maxDocumentLength = sn.getAttributeValue("value");
      }
    }

    out.print(
"\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.MaximumDocumentLength") + "</nobr></td>\n"+
"    <td class=\"value\">"+maxDocumentLength+"</td>\n"+
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    
    // Find whether security is on or off
    i = 0;
    boolean securityOn = true;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("security"))
      {
        String securityValue = sn.getAttributeValue("value");
        if (securityValue.equals("off"))
          securityOn = false;
        else if (securityValue.equals("on"))
          securityOn = true;
      }
    }
    
    out.print(
"\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.Security2") + "</nobr></td>\n"+
"    <td class=\"value\">"+((securityOn)?"Enabled":"Disabled")+"</td>\n"+
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    // Go through looking for access tokens
    seenAny = false;
    i = 0;
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals("access"))
      {
        if (seenAny == false)
        {
          out.print(
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.AccessTokens") + "</nobr></td>\n"+
"    <td class=\"value\">\n"
          );
          seenAny = true;
        }
        String token = sn.getAttributeValue("token");
        out.print(
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(token)+"<br/>\n"
        );
      }
    }

    if (seenAny)
    {
      out.print(
"    </td>\n"+
"  </tr>\n"
      );
    }
    else
    {
      out.print(
"  <tr><td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"DCTM.NoAccessTokensSpecified") + "</td></tr>\n"
      );
    }

    out.print(
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
    );
    
    // Find the path-name metadata attribute name
    i = 0;
    String pathNameAttribute = "";
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(CONFIG_PARAM_PATHNAMEATTRIBUTE))
      {
        pathNameAttribute = sn.getAttributeValue("value");
      }
    }

    out.print(
"  <tr>\n"
    );
    if (pathNameAttribute.length() > 0)
    {
      out.print(
"    <td class=\"description\">" + Messages.getBodyString(locale,"DCTM.PathNameMetadataAttribute") + "</td>\n"+
"    <td class=\"value\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(pathNameAttribute)+"</td>\n"
      );
    }
    else
    {
      out.print(
"    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"DCTM.NoPathNameMetadataAttributeSpecified") + "</td>\n"
      );
    }
    
    out.print(
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"    \n"+
"  <tr>\n"
    );
    
    // Find the path-value mapping data
    i = 0;
    org.apache.manifoldcf.crawler.connectors.DCTM.MatchMap matchMap = new org.apache.manifoldcf.crawler.connectors.DCTM.MatchMap();
    while (i < ds.getChildCount())
    {
      SpecificationNode sn = ds.getChild(i++);
      if (sn.getType().equals(CONFIG_PARAM_PATHMAP))
      {
        String pathMatch = sn.getAttributeValue("match");
        String pathReplace = sn.getAttributeValue("replace");
        matchMap.appendMatchPair(pathMatch,pathReplace);
      }
    }
    if (matchMap.getMatchCount() > 0)
    {
      out.print(
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.PathValueMapping") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <table class=\"displaytable\">\n"
      );
      i = 0;
      while (i < matchMap.getMatchCount())
      {
        String matchString = matchMap.getMatchString(i);
        String replaceString = matchMap.getReplaceString(i);
        out.print(
"        <tr>\n"+
"          <td class=\"value\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(matchString)+"</td>\n"+
"          <td class=\"value\">--></td>\n"+
"          <td class=\"value\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(replaceString)+"</td>\n"+
"        </tr>\n"
        );
        i++;
      }

      out.print(
"      </table>\n"+
"    </td>\n"
      );
    }
    else
    {
      out.print(
"    <td class=\"message\" colspan=\"2\">" + Messages.getBodyString(locale,"DCTM.NoMappingsSpecified") + "</td>\n"
      );
    }

    out.print(
"  </tr>\n"+
"</table>\n"
    );
  }

  /** Documentum-specific method, for UI support.
  * This one returns the supported content types, which will be presented in the UI for selection.
  */
  public String[] getContentTypes()
    throws ManifoldCFException, ServiceInterruption
  {
    try
    {
      String dql = "select name from dm_format where can_index=true order by name asc";
      while (true)
      {
        boolean noSession = (session==null);
        getSession();
        ArrayList contentTypes = new ArrayList();
        GetListOfValuesThread t = new GetListOfValuesThread(dql,"name",contentTypes);
        try
        {
          t.start();
          t.join();
          Throwable thr = t.getException();
          if (thr != null)
          {
            if (thr instanceof RemoteException)
              throw (RemoteException)thr;
            else if (thr instanceof DocumentumException)
              throw (DocumentumException)thr;
            else if (thr instanceof RuntimeException)
              throw (RuntimeException)thr;
            else
              throw (Error)thr;
          }
          String[] rval = new String[contentTypes.size()];
          int i = 0;
          while (i < rval.length)
          {
            rval[i] = (String)contentTypes.get(i);
            i++;
          }
          return rval;
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        catch (RemoteException e)
        {
          Throwable e2 = e.getCause();
          if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
            throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
          if (noSession)
          {
            long currentTime = System.currentTimeMillis();
            throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
          }
          session = null;
          lastSessionFetch = -1L;
          continue;
        }
      }
    }
    catch (DocumentumException e)
    {
      // Base our treatment on the kind of error it is.
      long currentTime = System.currentTimeMillis();
      if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
      {
        Logging.connectors.warn("DCTM: Remote service interruption reading content types: "+e.getMessage(),e);
        throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L, 12 * 60 * 60000L,-1,true);
      }
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  /** Documentum-specific method, for UI support.
  */
  public String[] getObjectTypes()
    throws ManifoldCFException, ServiceInterruption
  {
    try
    {
      String strDQL = "select distinct A.r_type_name from dmi_type_info A, dmi_dd_type_info B  " +
        " where ((not A.r_type_name like 'dm_%' and any A.r_supertype='dm_document' and B.life_cycle <> 3) " +
        "or (A.r_type_name = 'dm_document' and B.life_cycle <> 3)) " +
        " AND A.r_type_name = B.type_name order by A.r_type_name";
      while (true)
      {
        boolean noSession = (session==null);
        getSession();
        ArrayList objectTypes = new ArrayList();
        GetListOfValuesThread t = new GetListOfValuesThread(strDQL,"r_type_name",objectTypes);
        try
        {
          t.start();
          t.join();
          Throwable thr = t.getException();
          if (thr != null)
          {
            if (thr instanceof RemoteException)
              throw (RemoteException)thr;
            else if (thr instanceof DocumentumException)
              throw (DocumentumException)thr;
            else if (thr instanceof RuntimeException)
              throw (RuntimeException)thr;
            else
              throw (Error)thr;
          }
          String[] rval = new String[objectTypes.size()];
          int i = 0;
          while (i < rval.length)
          {
            rval[i] = (String)objectTypes.get(i);
            i++;
          }
          return rval;
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        catch (RemoteException e)
        {
          Throwable e2 = e.getCause();
          if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
            throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
          if (noSession)
          {
            long currentTime = System.currentTimeMillis();
            throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
          }
          session = null;
          lastSessionFetch = -1L;
          continue;
        }
      }
    }
    catch (DocumentumException e)
    {
      // Base our treatment on the kind of error it is.
      long currentTime = System.currentTimeMillis();
      if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
      {
        Logging.connectors.warn("DCTM: Remote service interruption reading object types: "+e.getMessage(),e);
        throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L, currentTime + 12 * 60 * 60000L,-1,true);
      }
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }


  protected class GetChildFolderNamesThread extends Thread
  {
    protected String strTheParentFolderPath;
    protected Throwable exception = null;
    protected String[] rval = null;

    public GetChildFolderNamesThread(String strTheParentFolderPath)
    {
      super();
      setDaemon(true);
      this.strTheParentFolderPath = strTheParentFolderPath;
    }

    public void run()
    {
      try
      {
        IDocumentumResult result;
        ArrayList objFolderNames = new ArrayList();
        if (strTheParentFolderPath.equalsIgnoreCase("/"))
        {
          String strDQLForCabinets = "select object_name, r_object_type, r_object_id from dm_cabinet order by 1";
          result = session.performDQLQuery(strDQLForCabinets);
        }
        else
        {
          result = session.getFolderContents(strTheParentFolderPath);
        }

        try
        {
          Map matchTypes = new HashMap();
          while (result.isValidRow())
          {
            String strObjectName = result.getStringValue("object_name");
            String strObjectType = result.getStringValue("r_object_type").trim();
            Boolean x = (Boolean)matchTypes.get(strObjectType);
            if (x == null)
            {
              // Look up whether this is a type of folder or cabinet
              boolean isMatch = session.isOneOf(strObjectType,new String[]{"dm_folder","dm_cabinet"});
              x = new Boolean(isMatch);
              matchTypes.put(strObjectType,x);
            }

            if (x.booleanValue())
            {
              objFolderNames.add(strObjectName);
            }
            result.nextRow();
          }
        }
        finally
        {
          result.close();
        }

        rval = new String[objFolderNames.size()];
        int i = 0;
        while (i < rval.length)
        {
          rval[i] = (String)objFolderNames.get(i);
          i++;
        }
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
    }

    public Throwable getException()
    {
      return exception;
    }

    public String[] getResponse()
    {
      return rval;
    }
  }

  /** This method returns an ordered set of the "next things" given a folder path, for the UI to
  * use in constructing the starting folder for a job's document specification.
  */
  public String[] getChildFolderNames(String strTheParentFolderPath)
    throws ManifoldCFException, ServiceInterruption
  {
    try
    {
      while (true)
      {
        boolean noSession = (session==null);
        getSession();
        GetChildFolderNamesThread t = new GetChildFolderNamesThread(strTheParentFolderPath);
        try
        {
          t.start();
          t.join();
          Throwable thr = t.getException();
          if (thr != null)
          {
            if (thr instanceof RemoteException)
              throw (RemoteException)thr;
            else if (thr instanceof DocumentumException)
              throw (DocumentumException)thr;
            else if (thr instanceof RuntimeException)
              throw (RuntimeException)thr;
            else
              throw (Error)thr;
          }
          return t.getResponse();
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        catch (RemoteException e)
        {
          Throwable e2 = e.getCause();
          if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
            throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
          if (noSession)
          {
            long currentTime = System.currentTimeMillis();
            throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
          }
          session = null;
          lastSessionFetch = -1L;
          continue;
        }
      }
    }
    catch (DocumentumException e)
    {
      // Base our treatment on the kind of error it is.
      long currentTime = System.currentTimeMillis();
      if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
      {
        Logging.connectors.warn("DCTM: Remote service interruption reading child folders: "+e.getMessage(),e);
        throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
      }
      throw new ManifoldCFException(e.getMessage(),e);
    }

  }

  /** Get the list of attributes for a given data type.  This returns an inclusive list of both pre-defined and extended
  * attributes, to be presented to the user as a select list per data type.  From this list, the metadata attributes will
  * be selected.
  *@param docType is the document type (e.g. "dm_document") for which the attributes are requested.
  *@return the array of data attributes, in alphabetic order.
  */
  public String[] getIngestableAttributes(String docType)
    throws ManifoldCFException, ServiceInterruption
  {
    try
    {
      String strDQL = "select attr_name FROM dmi_dd_attr_info where type_name = '" + docType + "' order by attr_name asc";
      while (true)
      {
        boolean noSession = (session==null);
        getSession();
        ArrayList attributes = new ArrayList();
        GetListOfValuesThread t = new GetListOfValuesThread(strDQL,"attr_name",attributes);
        try
        {
          t.start();
          t.join();
          Throwable thr = t.getException();
          if (thr != null)
          {
            if (thr instanceof RemoteException)
              throw (RemoteException)thr;
            else if (thr instanceof DocumentumException)
              throw (DocumentumException)thr;
            else if (thr instanceof RuntimeException)
              throw (RuntimeException)thr;
            else
              throw (Error)thr;
          }
          String[] rval = new String[attributes.size()];
          int i = 0;
          while (i < rval.length)
          {
            rval[i] = (String)attributes.get(i);
            i++;
          }
          return rval;
        }
        catch (InterruptedException e)
        {
          t.interrupt();
          throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
        }
        catch (RemoteException e)
        {
          Throwable e2 = e.getCause();
          if (e2 instanceof InterruptedException || e2 instanceof InterruptedIOException)
            throw new ManifoldCFException(e2.getMessage(),e2,ManifoldCFException.INTERRUPTED);
          if (noSession)
          {
            long currentTime = System.currentTimeMillis();
            throw new ServiceInterruption("Transient error connecting to documentum service: "+e.getMessage(),currentTime + 60000L);
          }
          session = null;
          lastSessionFetch = -1L;
          continue;
        }
      }
    }
    catch (DocumentumException e)
    {
      // Base our treatment on the kind of error it is.
      long currentTime = System.currentTimeMillis();
      if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
      {
        Logging.connectors.warn("DCTM: Remote service interruption reading child folders: "+e.getMessage(),e);
        throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
      }
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }


  // Private and protected methods

  /** Grab forced acl out of document specification.
  *@param spec is the document specification.
  *@return the acls.
  */
  protected static String[] getAcls(DocumentSpecification spec)
  {
    HashMap map = new HashMap();
    int i = 0;
    boolean securityOn = true;
    while (i < spec.getChildCount())
    {
      SpecificationNode sn = spec.getChild(i++);
      if (sn.getType().equals("access"))
      {
        String token = sn.getAttributeValue("token");
        map.put(token,token);
      }
      else if (sn.getType().equals("security"))
      {
        String value = sn.getAttributeValue("value");
        if (value.equals("on"))
          securityOn = true;
        else if (value.equals("off"))
          securityOn = false;
      }
    }
    if (!securityOn)
      return null;

    String[] rval = new String[map.size()];
    Iterator iter = map.keySet().iterator();
    i = 0;
    while (iter.hasNext())
    {
      rval[i++] = (String)iter.next();
    }
    return rval;
  }

  /** Class that tracks paths associated with folder IDs, and also keeps track of the name
  * of the metadata attribute to use for the path.
  */
  protected class SystemMetadataDescription
  {
    // The path attribute name
    protected String pathAttributeName;

    // The folder ID to path name mapping (which acts like a cache).
    // The key is the folder ID, and the value is an array of Strings.
    protected Map pathMap = new HashMap();

    // The path name map
    protected MatchMap matchMap = new MatchMap();

    /** Constructor */
    public SystemMetadataDescription(DocumentSpecification spec)
      throws ManifoldCFException
    {
      pathAttributeName = null;
      int i = 0;
      while (i < spec.getChildCount())
      {
        SpecificationNode n = spec.getChild(i++);
        if (n.getType().equals(CONFIG_PARAM_PATHNAMEATTRIBUTE))
          pathAttributeName = n.getAttributeValue("value");
        else if (n.getType().equals(CONFIG_PARAM_PATHMAP))
        {
          String pathMatch = n.getAttributeValue("match");
          String pathReplace = n.getAttributeValue("replace");
          matchMap.appendMatchPair(pathMatch,pathReplace);
        }
      }
    }

    /** Get the path attribute name.
    *@return the path attribute name, or null if none specified.
    */
    public String getPathAttributeName()
    {
      return pathAttributeName;
    }

    /** Given an identifier, get the array of translated strings that goes into the metadata.
    */
    public String[] getPathAttributeValue(IDocumentumObject object)
      throws DocumentumException, RemoteException, ManifoldCFException
    {
      String[] paths = object.getFolderPaths(pathMap);
      String[] rval = new String[paths.length];
      int i = 0;
      while (i < paths.length)
      {
        rval[i] = matchMap.translate(paths[i]);
        i++;
      }
      return rval;
    }

  }
}
