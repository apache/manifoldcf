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
import org.apache.manifoldcf.connectorcommon.interfaces.*;
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
  public static String CONFIG_PARAM_FORMAT_ALL = "mimetypeall";
  public static String CONFIG_PARAM_PATHNAMEATTRIBUTE = "pathnameattribute";
  public static String CONFIG_PARAM_PATHMAP = "pathmap";
  public static String CONFIG_PARAM_FILTER = "filter";
  
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

    public void finishUp()
      throws InterruptedException, java.net.MalformedURLException, NotBoundException, RemoteException, DocumentumException
    {
      join();
      Throwable thr = exception;
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
        else if (thr instanceof Error)
          throw (Error)thr;
        else
          throw new RuntimeException("Unexpected exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
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
      t.start();
      try
      {
        t.finishUp();
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
    protected final String query;
    protected final String fieldName;
    
    protected Throwable exception = null;
    protected final List<String> list = new ArrayList<String>();

    public GetListOfValuesThread(String query, String fieldName)
    {
      super();
      setDaemon(true);
      this.query = query;
      this.fieldName = fieldName;
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

    public List<String> finishUp()
      throws InterruptedException, RemoteException, DocumentumException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
        if (thr instanceof RemoteException)
          throw (RemoteException)thr;
        else if (thr instanceof DocumentumException)
          throw (DocumentumException)thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        else if (thr instanceof Error)
          throw (Error)thr;
        else
          throw new RuntimeException("Unexpected exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
      return list;
    }

  }

  protected List<String> getAttributesForType(String typeName)
    throws DocumentumException, ManifoldCFException, ServiceInterruption
  {
    String strDQL = "select distinct attr_name FROM dmi_dd_attr_info where type_name = '" + typeName + "'";

    while (true)
    {
      boolean noSession = (session==null);
      getSession();
      GetListOfValuesThread t = new GetListOfValuesThread(strDQL,"attr_name");
      t.start();
      try
      {
        return t.finishUp();
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

    public void finishUp()
      throws InterruptedException, RemoteException, DocumentumException
    {    
      join();
      Throwable thr = exception;
      if (thr != null)
      {
        if (thr instanceof RemoteException)
          throw (RemoteException)thr;
        else if (thr instanceof DocumentumException)
          throw (DocumentumException)thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        else if (thr instanceof Error)
          throw (Error)thr;
        else
          throw new RuntimeException("Unexpected exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
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
      t.start();
      try
      {
        t.finishUp();
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

    public String finishUp()
      throws InterruptedException, RemoteException, DocumentumException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
        if (thr instanceof RemoteException)
          throw (RemoteException)thr;
        else if (thr instanceof DocumentumException)
          throw (DocumentumException)thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        else if (thr instanceof Error)
          throw (Error)thr;
        else
          throw new RuntimeException("Unexpected exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
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
      t.start();
      try
      {
        return t.finishUp();
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

    public void finishUp()
      throws InterruptedException, RemoteException, DocumentumException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
        if (thr instanceof RemoteException)
          throw (RemoteException)thr;
        else if (thr instanceof DocumentumException)
          throw (DocumentumException)thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        else if (thr instanceof Error)
          throw (Error)thr;
        else
          throw new RuntimeException("Unexpected exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
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
      t.start();
      try
      {
        t.finishUp();
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
      t.start();
      try
      {
        t.finishUp();
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

    public void finishUp()
      throws RemoteException, DocumentumException, InterruptedException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
        if (thr instanceof RemoteException)
          throw (RemoteException)thr;
        else if (thr instanceof DocumentumException)
          throw (DocumentumException)thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        else if (thr instanceof Error)
          throw (Error)thr;
        else
          throw new RuntimeException("Unexpected exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
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
    // Extract startTime
    long startTime;
    if (lastSeedVersion == null)
      startTime = 0L;
    else
    {
      // Unpack seed time from seed version string
      startTime = new Long(lastSeedVersion).longValue();
    }

    // First, build the query

    StringBuilder strLocationsClause = new StringBuilder();
    Map<String,Map<String,Map<String,Set<String>>>> tokenList = new HashMap<String,Map<String,Map<String,Set<String>>>>();
    List<String> contentList = null;
    boolean seenAllMimeTypes = false;
    boolean allMimeTypes = false;
    String maxSize = null;

    for (int i = 0; i < spec.getChildCount(); i++)
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
        Map<String,Map<String,Set<String>>> filters = tokenList.get(objType);
        if (filters == null)
        {
          filters = new HashMap<String,Map<String,Set<String>>>();
          tokenList.put(objType,filters);
        }
        // Go through children and pick out filters
        for (int j = 0; j < n.getChildCount(); j++)
        {
          SpecificationNode sn = n.getChild(j);
          if (sn.getType().equals(CONFIG_PARAM_FILTER))
          {
            String attributeName = sn.getAttributeValue("name");
            String operation = sn.getAttributeValue("op");
            String value = sn.getAttributeValue("value");
            Map<String,Set<String>> operations = filters.get(attributeName);
            if (operations == null)
            {
              operations = new HashMap<String,Set<String>>();
              filters.put(attributeName,operations);
            }
            Set<String> values = operations.get(operation);
            if (values == null)
            {
              values = new HashSet<String>();
              operations.put(operation,values);
            }
            values.add(value);
          }
        }
      }
      else if (n.getType().equals(CONFIG_PARAM_FORMAT_ALL))
      {
	seenAllMimeTypes = true;
	String all = n.getAttributeValue("value");
	if (all.equals("true"))
	{
	  allMimeTypes = true;
	}
      }
      else if (n.getType().equals(CONFIG_PARAM_FORMAT))
      {
	seenAllMimeTypes = true;
        String docType = n.getAttributeValue("value");
        if (contentList == null)
          contentList = new ArrayList<String>();
        contentList.add(docType);
      }
      else if (n.getType().equals(CONFIG_PARAM_MAXLENGTH))
      {
        maxSize = n.getAttributeValue("value");
      }

    }

    if (tokenList.size() == 0)
    {
      Logging.connectors.debug("DCTM: No ObjectType found in Document Spec. Setting it to dm_document");
      tokenList.put("dm_document",new HashMap<String,Map<String,Set<String>>>());
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
        " and r_modify_date<=" + buildDateString(seedTime) +
        " AND (i_is_deleted=TRUE Or (i_is_deleted=FALSE AND a_full_text=TRUE AND r_content_size>0");

      // append maxsize if set
      if (maxSize != null && maxSize.length() > 0)
      {
        strDQLend.append(" AND r_content_size<=").append(maxSize);
      }

      // If we don't even see the allmimetypes record, we emit no restriction
      if (seenAllMimeTypes == true && allMimeTypes == false)
      {
	String[] dctmTypes = convertToDCTMTypes(contentList);
	if (dctmTypes == null || dctmTypes.length == 0)
	  strDQLend.append(" AND 1<0");
	else
	{
	  strDQLend.append(" AND a_content_type IN (");
	  boolean commaNeeded = false;
	  for (String cType : dctmTypes)
	  {
	    if (commaNeeded)
	      strDQLend.append(",");
	    else
	      commaNeeded = true;
	    strDQLend.append(quoteDQLString(cType));
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
      for (String tokenValue : tokenList.keySet())
      {
        activities.checkJobStillActive();
        
        // Construct the filter part of the DQL query
        Map<String,Map<String,Set<String>>> filters = tokenList.get(tokenValue);
        
        StringBuilder filterPart = new StringBuilder();
        // For each attribute, go through the operations and emit an AND clause
        for (String attributeName : filters.keySet())
        {
          Map<String,Set<String>> operations = filters.get(attributeName);
          for (String operation : operations.keySet())
          {
            Set<String> values = operations.get(operation);
            if (operation.equals("="))
            {
              filterPart.append(" AND \"").append(attributeName).append("\"").append(" IN (");
              boolean commaNeeded = false;
              for (String value : values)
              {
                if (commaNeeded)
                  filterPart.append(",");
                else
                  commaNeeded = true;
                filterPart.append(quoteDQLString(value));
              }
              filterPart.append(")");
            }
            else if (operation.equals("<>"))
            {
              filterPart.append(" AND (");
              boolean andNeeded = false;
              for (String value : values)
              {
                if (andNeeded)
                  filterPart.append(" AND ");
                else
                  andNeeded = true;
                filterPart.append("\"").append(attributeName).append("\"").append("<>").append(quoteDQLString(value));
              }
              filterPart.append(")");
            }
            else
              throw new ManifoldCFException("Unrecognized operation: "+operation);
          }
        }
        
        String strDQL = strDQLstart + tokenValue + strDQLend + filterPart;
        if (Logging.connectors.isDebugEnabled())
          Logging.connectors.debug("DCTM: About to execute query= (" + strDQL + ")");
        while (true)
        {
          boolean noSession = (session==null);
          getSession();
          try
          {
            StringQueue stringQueue = new StringQueue();
            GetDocumentsFromQueryThread t = new GetDocumentsFromQueryThread(strDQL,stringQueue);
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
              t.finishUp();
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
          }
          catch (InterruptedException e)
          {
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
            // Go back around again
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
    return new Long(seedTime).toString();
  }

  /** Do a query and read back the name column */
  protected static String[] convertToDCTMTypes(List<String> contentList)
    throws ManifoldCFException, ServiceInterruption
  {
    if (contentList != null && contentList.size() > 0)
    {
      // The contentList has type names.
      return contentList.toArray(new String[0]);
    }
    return null;

  }

  protected static String quoteDQLString(String value)
  {
    StringBuilder sb = new StringBuilder();
    sb.append("'");
    for (int i = 0; i < value.length(); i++)
    {
      char x = value.charAt(i);
      if (x == '\'')
        sb.append("'");
      sb.append(x);
    }
    sb.append("'");
    return sb.toString();
  }
  
  protected class ProcessDocumentThread extends Thread
  {
    // Initial data
    protected final String documentIdentifier;
    protected final SpecInfo sDesc;
    
    // State
    protected volatile boolean versionPartDone = false;
    protected volatile boolean threadExit = false;
    protected volatile boolean startFetch = false;
    protected volatile boolean abort = false;
    
    // Return info
    protected File objFileTemp = null;
    protected Throwable exception = null;
    protected String versionString = null;
    protected RepositoryDocument rval = null;
    protected Long activityStartTime = null;
    protected Long activityFileLength = null;
    protected String activityStatus = null;
    protected String activityMessage = null;
    protected String uri = null;
    protected String contentType = null;
    protected Long contentSize = null;

    public ProcessDocumentThread(String documentIdentifier, SpecInfo sDesc)
    {
      super();
      setDaemon(true);
      this.documentIdentifier = documentIdentifier;
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
          long contentSizeValue = (object==null)?0L:object.getContentSize();
          contentSize = new Long(contentSizeValue);
          // Get the type name; this is what we use to figure out the desired attributes
          String typeName = (object==null)?null:object.getTypeName();
          
          if (object != null && object.exists() && !object.isDeleted() && !object.isHidden() && object.getPermit() > 1 &&
            contentSizeValue > 0 && object.getPageCount() > 0)
          {
            // According to Ryck, the version label is not helping us much, so if it's null it's ok
            String versionLabel = object.getVersionLabel();

            // The version string format was reorganized on 11/6/2006.

            StringBuilder strVersionLabel = new StringBuilder();

            strVersionLabel.append(sDesc.getMetadataVersionAddendum(typeName));

            // Now do the forced acls.  Since this is a reorganization of the version string,
            // I decided to make these parseable, and pass them through to processDocument() in that
            // way, because most connectors seem to be heading in that direction.
            strVersionLabel.append(sDesc.getForcedAclString());

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
            strVersionLabel.append(sDesc.getPathNameAttributeAddendum());

            // Append the Webtop base url.  This was added on 9/7/2007.
            strVersionLabel.append("_").append(webtopBaseURL);

            versionString = strVersionLabel.toString();
          }
          else
            versionString = null;
          
          // Signal that we are done with the version string
          synchronized (this)
          {
            versionPartDone = true;
            notifyAll();
            while (true)
            {
              if (startFetch || abort)
                break;
              wait();
            }
            if (abort)
              return;
          }

          // Do fetch phase
          if (object == null) {
            activityStatus = "MISSING";
            return;
          }
          
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
            activityMessage = dfe.getMessage();
            if (dfe.getType() == DocumentumException.TYPE_NOTALLOWED)
            {
              activityStatus = "NOTALLOWED";
              return;
            }
            else if (dfe.getType() == DocumentumException.TYPE_CORRUPTEDDOCUMENT)
            {
              activityStatus = "CORRUPTEDDOCUMENT";
              return;
            }
            throw dfe;
          }
          long fileLength = objFileTemp.length();
          activityFileLength = new Long(fileLength);

          if (strFilePath == null)
          {
            activityStatus = "CONTENTDIDNOTFETCH";
            activityMessage = "Content could not be fetched";
            // We don't know why it won't fetch, but skip it and keep going.
            return;
          }

          activityStatus = "OK";

          rval = new RepositoryDocument();

          if (contentType != null)
            rval.setMimeType(contentType);
            
          List<String> attributeDescriptions = sDesc.getMetadataFields(typeName);
          if (attributeDescriptions != null)
          {
            for (String attrName : attributeDescriptions)
            {
              // Fetch the attributes from the object
              String[] values = object.getAttributeValues(attrName);
              // Add the attribute to the rd
              rval.addField(attrName,values);
            }
          }

          if (objName != null)
            rval.setFileName(objName);
          
          // Add the path metadata item into the mix, if enabled
          String pathAttributeName = sDesc.getPathAttributeName();
          if (pathAttributeName != null && pathAttributeName.length() > 0)
          {
            String[] pathString = sDesc.getPathAttributeValue(object);
            rval.addField(pathAttributeName,pathString);
          }

          // Handle the forced acls
          String[] denyAcls = new String[]{denyToken};
          String[] acls = sDesc.getAcls();
          if (acls != null && acls.length == 0)
          {
            String[] strarrACL = new String[1];
            // This used to go back-and-forth to documentum to get the docbase name, but that seemed stupid, so i just
            // use the one I have already now.
            strarrACL[0] = docbaseName + ":" + object.getACLDomain() + "." + object.getACLName();
            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("DCTM: Processing document (" + objName + ") with ACL=" + strarrACL[0] + " and size=" + object.getContentSize() + " bytes.");
            rval.setSecurityACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT,strarrACL);
            rval.setSecurityDenyACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT,denyAcls);
          }
          else if (acls != null)
          {
            rval.setSecurityACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT,acls);
            rval.setSecurityDenyACL(RepositoryDocument.SECURITY_TYPE_DOCUMENT,denyAcls);

            if (Logging.connectors.isDebugEnabled())
              Logging.connectors.debug("DCTM: Processing document (" + objName + ") with size=" + object.getContentSize() + " bytes.");
          }

          uri = convertToURI(object.getObjectId(),contentType);
        }
        finally
        {
          object.release();
        }

      }
      catch (DocumentumException dfe)
      {
        // Fetch by qualification failed
        activityMessage = dfe.getMessage();
        if (dfe.getType() == DocumentumException.TYPE_NOTALLOWED)
        {
          activityStatus = "NOTALLOWED";
          return;
        }
        else if (dfe.getType() == DocumentumException.TYPE_CORRUPTEDDOCUMENT)
        {
          activityStatus = "CORRUPTEDDOCUMENT";
          return;
        }
        this.exception = dfe;
      }
      catch (Throwable e)
      {
        this.exception = e;
      }
      finally
      {
        synchronized(this)
        {
          threadExit = true;
          notifyAll();
        }
      }
    }

    public String getVersionString()
      throws RemoteException, DocumentumException, InterruptedException
    {
      // First, wait for version to be ready
      synchronized (this)
      {
        while (true)
        {
          wait();
          if (threadExit || versionPartDone)
            break;
        }
      }
      if (exception != null)
      {
        if (exception instanceof RemoteException)
          throw (RemoteException)exception;
        else if (exception instanceof DocumentumException)
          throw (DocumentumException)exception;
        else if (exception instanceof RuntimeException)
          throw (RuntimeException)exception;
        else if (exception instanceof Error)
          throw (Error)exception;
        else
          throw new RuntimeException("Unexpected exception type: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
      }
      // Return the version
      return versionString;
    }
    
    public void startFetch(File objFileTemp)
    {
      // Begin the fetch part
      synchronized (this)
      {
        this.objFileTemp = objFileTemp;
        startFetch = true;
        notifyAll();
      }
    }
    
    public void finishWithoutFetch()
      throws InterruptedException
    {
      // Abort the fetch phase, and shut the thread down
      synchronized (this)
      {
        abort = true;
        notifyAll();
      }
      join();
    }

    public RepositoryDocument finishUp()
      throws RemoteException, DocumentumException, InterruptedException, ManifoldCFException
    {
      join();
      if (exception != null)
      {
        if (exception instanceof RemoteException)
          throw (RemoteException)exception;
        else if (exception instanceof DocumentumException)
          throw (DocumentumException)exception;
        else if (exception instanceof ManifoldCFException)
          throw (ManifoldCFException)exception;
        else if (exception instanceof RuntimeException)
          throw (RuntimeException)exception;
        else if (exception instanceof Error)
          throw (Error)exception;
        else
          throw new RuntimeException("Unexpected exception type: "+exception.getClass().getName()+": "+exception.getMessage(),exception);
      }
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
    long currentTime;
    // Do any preliminary work
    // Build the node/path cache
    SpecInfo sDesc = new SpecInfo(spec);
    
    try
    {
      // Now we are ready to go through the document identifiers
      for (String documentIdentifier : documentIdentifiers)
      {
        // It is better, performance-wise, to fetch a document object just once.  Under RMI,
        // though, we will need to do this in a background thread, since it's socket-based and can therefore
        // be broken by network disruption.  On the other hand, decisions about how to proceed can
        // only be undertaken in the local ManifoldCF worker thread.
        // In order to deal with these constraints, the background thread needs to have multiple "stages".
        // Each stage executes to completion and then blocks, while the MCF worker thread looks at the
        // results, and then informs the background thread to proceed (or to abort, if no further work
        // is desired).
        
        // Since each documentum access is time-consuming, be sure that we abort if the job has gone inactive
        activities.checkJobStillActive();

        while (true)
        {
          boolean noSession = (session==null);
          getSession();
          
          String errorCode = null;
          String errorDesc = null;
          Long fileLengthLong = null;
          Long startTime = null;
          
          try
          {

            ProcessDocumentThread t = new ProcessDocumentThread(documentIdentifier, sDesc);
            // Start the thread
            t.start();
            try
            {
              // Wait for version string
              String versionString = t.getVersionString();

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
              
              if (versionString == null)
              {
                t.finishWithoutFetch();
                activities.deleteDocument(documentIdentifier);
                break;
              }
              
              // Start the fetch part
              try
              {
                // Create a temporary file for every attempt, because we don't know yet whether we'll need it or not -
                // but probably we will.
                File objFileTemp = File.createTempFile("_mc_dctm_", null);
                try
                {
                  t.startFetch(objFileTemp);
                  RepositoryDocument rd = t.finishUp();
                  
                  if (rd == null)
                  {
                    errorCode = t.getActivityStatus();
                    errorDesc = t.getActivityMessage();
                    activities.noDocument(documentIdentifier,versionString);
                    break;
                  }
                    
                  long fileLength = t.getContentSize().longValue();
                  if (!activities.checkLengthIndexable(fileLength))
                  {
                    errorCode = activities.EXCLUDED_LENGTH;
                    errorDesc = "Excluded due to content length ("+fileLength+")";
                    activities.noDocument(documentIdentifier,versionString);
                    break;
                  }
                  
                  String contentType = t.getContentType();
                  if (!activities.checkMimeTypeIndexable(contentType))
                  {
                    errorCode = activities.EXCLUDED_MIMETYPE;
                    errorDesc = "Excluded due to mime type ("+contentType+")";
                    activities.noDocument(documentIdentifier,versionString);
                    break;
                  }

                  // Stream the data to the ingestion system
                  InputStream is = new FileInputStream(objFileTemp);
                  try
                  {
                    rd.setBinary(is, fileLength);
                    // Do the ingestion
                    activities.ingestDocumentWithException(documentIdentifier,versionString,
                      t.getURI(), rd);
                    errorCode = t.getActivityStatus();
                    errorDesc = t.getActivityMessage();
                    fileLengthLong = t.getActivityFileLength();
                    startTime = t.getActivityStartTime();
                    break;
                  }
                  finally
                  {
                    is.close();
                  }
                }
                finally
                {
                  objFileTemp.delete();
                }
              }
              catch (java.io.IOException e)
              {
                errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
                errorDesc = e.getMessage();
                handleIOException(e);
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
          catch (DocumentumException e)
          {
            errorCode = e.getClass().getSimpleName().toUpperCase(Locale.ROOT);
            errorDesc = e.getMessage();
            throw e;
          }
          catch (ManifoldCFException e)
          {
            if (e.getErrorCode() == ManifoldCFException.INTERRUPTED)
              errorCode = null;
            throw e;
          }
          finally
          {
            if (errorCode != null)
              activities.recordActivity(startTime,ACTIVITY_FETCH,
                fileLengthLong,documentIdentifier,errorCode,errorDesc,null);
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
        Logging.connectors.warn("DCTM: Remote service interruption processing documents: "+e.getMessage(),e);
        throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
      }
      throw new ManifoldCFException(e.getMessage(),e);
    }
  }

  protected static void handleIOException(IOException e)
    throws ManifoldCFException, ServiceInterruption
  {
    if (e instanceof java.net.SocketTimeoutException)
      throw new ManifoldCFException(e.getMessage(),e);
    else if (e instanceof InterruptedIOException)
      throw new ManifoldCFException(e.getMessage(),e,ManifoldCFException.INTERRUPTED);
    throw new ManifoldCFException(e.getMessage(),e);
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
"    alert(\""+Messages.getBodyJavascriptString(locale,"DCTM.PleaseSupplyTheNameofaDocbase")+"\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"DCTM.Docbase") + "\");\n"+
"    editconnection.docbasename.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.docbaseusername.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.ConnectionRequiresValidDocumentumUsername")+"\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"DCTM.Docbase") + "\");\n"+
"    editconnection.docbaseusername.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.docbasepassword.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.ConnectionRequiresPassword")+"\");\n"+
"    SelectTab(\"" + Messages.getBodyJavascriptString(locale,"DCTM.Docbase") + "\");\n"+
"    editconnection.docbasepassword.focus();\n"+
"    return false;\n"+
"  }\n"+
"  if (editconnection.webtopbaseurl.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.SpecifyBaseWebtopURL")+"\");\n"+
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
    String seqPrefix = "s"+connectionSequenceNumber+"_";

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
"function "+seqPrefix+"checkSpecification()\n"+
"{\n"+
"  if (editjob."+seqPrefix+"specmaxdoclength.value != \"\" && !isInteger(editjob."+seqPrefix+"specmaxdoclength.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.MaximumDocumentLengthMustBeNullOrAnInteger") + "\");\n"+
"    editjob."+seqPrefix+"specmaxdoclength.focus();\n"+
"    return false;\n"+
"  }\n"+
"  return true;\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"SpecOp(n, opValue, anchorvalue)\n"+
"{\n"+
"  eval(\"editjob.\"+n+\".value = \\\"\"+opValue+\"\\\"\");\n"+
"  postFormSetAnchor(anchorvalue);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"DeleteFilter(k,l)\n"+
"{\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"filter_\"+k+\"_\"+l+\"_op\",\"Delete\",\""+seqPrefix+"filter_\"+k+\"_\"+l);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"AddFilter(k,l)\n"+
"{\n"+
"  if (eval(\"editjob."+seqPrefix+"filter_\"+k+\"_name.value == \\\"\\\"\"))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.SelectAnAttributeFirst") + "\");\n"+
"    eval(\"editjob."+seqPrefix+"filter_\"+k+\"_name.focus()\");\n"+
"    return;\n"+
"  }\n"+
"\n"+
"  if (eval(\"editjob."+seqPrefix+"filter_\"+k+\"_operation.value == \\\"\\\"\"))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.SelectAnOperation") + "\");\n"+
"    eval(\"editjob."+seqPrefix+"filter_\"+k+\"_operation.focus()\");\n"+
"    return;\n"+
"  }\n"+
"\n"+
"  if (eval(\"editjob."+seqPrefix+"filter_\"+k+\"_value.value == \\\"\\\"\"))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.FilterMustHaveValue") + "\");\n"+
"    eval(\"editjob."+seqPrefix+"filter_\"+k+\"_value.focus()\");\n"+
"    return;\n"+
"  }\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"filter_\"+k+\"_op\",\"Add\",\""+seqPrefix+"filter_\"+k+\"_\"+l);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"SpecAddToPath(anchorvalue)\n"+
"{\n"+
"  if (editjob."+seqPrefix+"pathaddon.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.SelectAFolderFirst") + "\");\n"+
"    editjob."+seqPrefix+"pathaddon.focus();\n"+
"    return;\n"+
"  }\n"+
"\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"pathop\",\"AddToPath\",anchorvalue);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"SpecAddToken(anchorvalue)\n"+
"{\n"+
"  if (editjob."+seqPrefix+"spectoken.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.NullTokensNotAllowed") + "\");\n"+
"    editjob."+seqPrefix+"spectoken.focus();\n"+
"    return;\n"+
"  }\n"+
"\n"+
"  "+seqPrefix+"SpecOp(\"accessop\",\"Add\",anchorvalue);\n"+
"}\n"+
"\n"+
"function "+seqPrefix+"SpecAddMapping(anchorvalue)\n"+
"{\n"+
"  if (editjob."+seqPrefix+"specmatch.value == \"\")\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.EnterASpecificationFirst") + "\");\n"+
"    editjob."+seqPrefix+"specmatch.focus();\n"+
"    return;\n"+
"  }\n"+
"  if (!isRegularExpression(editjob."+seqPrefix+"specmatch.value))\n"+
"  {\n"+
"    alert(\"" + Messages.getBodyJavascriptString(locale,"DCTM.SpecificationMustBeValidRegularExpression") + "\");\n"+
"    editjob."+seqPrefix+"specmatch.focus();\n"+
"    return false;\n"+
"  }\n"+
"  "+seqPrefix+"SpecOp(\""+seqPrefix+"specmappingop\",\"Add\",anchorvalue);\n"+
"  }\n"+
"\n"+
"//-->\n"+
"</script>\n"
    );
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
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    int k;

    // Paths tab
    if (tabName.equals(Messages.getString(locale,"DCTM.Paths")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Now, loop through paths
      k = 0;
      for (int i = 0; i < ds.getChildCount(); i++)
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(CONFIG_PARAM_LOCATION))
        {
          String pathDescription = "_" + Integer.toString(k);
          String pathName = seqPrefix + "specpath" + pathDescription;
          String pathOpName = seqPrefix + "pathop" + pathDescription;
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+pathName+"\" value=\""+sn.getAttributeValue("path")+"\"/>\n"+
"      <input type=\"hidden\" name=\""+pathOpName+"\" value=\"\"/>\n"+
"      <a name=\""+seqPrefix+"path_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Delete\" onClick='Javascript:"+seqPrefix+"SpecOp(\""+pathOpName+"\",\"Delete\",\""+seqPrefix+"path_"+Integer.toString(k)+"\")' alt=\"" + Messages.getAttributeString(locale,"DCTM.DeletePath")+Integer.toString(k)+"\"/>\n"+
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
"      <input type=\"hidden\" name=\""+seqPrefix+"pathcount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
	
      String pathSoFar = (String)currentContext.get(seqPrefix+"specpath");
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
"      <a name=\""+seqPrefix+"path_"+Integer.toString(k)+"\">\n"+
"        <input type=\"hidden\" name=\""+seqPrefix+"specpath\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathSoFar)+"\"/>\n"+
"        <input type=\"hidden\" name=\""+seqPrefix+"pathop\" value=\"\"/>\n"+
"        <input type=\"button\" value=\"Add\" alt=\"" + Messages.getAttributeString(locale,"DCTM.AddPath") + "\" onClick='Javascript:"+seqPrefix+"SpecOp(\""+seqPrefix+"pathop\",\"Add\",\""+seqPrefix+"path_"+Integer.toString(k+1)+"\")'/>&nbsp;\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(pathSoFar)+"\n"
        );
        if (pathSoFar.length() > 1)
        {
          out.print(
"      <input type=\"button\" value=\"-\" alt=\"" + Messages.getAttributeString(locale,"DCTM.RemoveFromPath") + "\" onClick='Javascript:"+seqPrefix+"SpecOp(\""+seqPrefix+"pathop\",\"Up\",\""+seqPrefix+"path_"+Integer.toString(k)+"\")'/>\n"
          );
        }
        if (childList.length > 0)
        {
          out.print(
"      <input type=\"button\" value=\"+\" alt=\"" + Messages.getAttributeString(locale,"DCTM.AddToPath") + "\" onClick='Javascript:"+seqPrefix+"SpecAddToPath(\""+seqPrefix+"path_"+Integer.toString(k)+"\")'/>&nbsp;\n"+
"      <select multiple=\"false\" name=\""+seqPrefix+"pathaddon\" size=\"2\">\n"+
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
      k = 0;
      for (int i =0; i < ds.getChildCount(); i++)
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals(CONFIG_PARAM_LOCATION))
        {
          String pathDescription = "_" + Integer.toString(k);
          String pathName = seqPrefix + "specpath" + pathDescription;
          out.print(
"<input type=\"hidden\" name=\""+pathName+"\" value=\""+sn.getAttributeValue("path")+"\"/>\n"
          );
          k++;
        }
      }
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"pathcount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }

    // Security tab
    // Find whether security is on or off
    boolean securityOn = true;
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals("security"))
      {
        String securityValue = sn.getAttributeValue("value");
        if (securityValue.equals("off"))
          securityOn = false;
        else if (securityValue.equals("on"))
          securityOn = true;
      }
    }

    if (tabName.equals(Messages.getString(locale,"DCTM.Security")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.Security2") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"1\">\n"+
"      <input type=\"radio\" name=\""+seqPrefix+"specsecurity\" value=\"on\" "+((securityOn)?"checked=\"true\"":"")+" />Enabled&nbsp;\n"+
"      <input type=\"radio\" name=\""+seqPrefix+"specsecurity\" value=\"off\" "+((securityOn==false)?"checked=\"true\"":"")+" />Disabled\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      // Finally, go through forced ACL
      k = 0;
      for (int i = 0; i < ds.getChildCount(); i++)
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("access"))
        {
          String accessDescription = "_"+Integer.toString(k);
          String accessOpName = seqPrefix+"accessop"+accessDescription;
          String accessTokenName = seqPrefix+"spectoken"+accessDescription;
          String token = sn.getAttributeValue("token");
          out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"+
"      <input type=\"hidden\" name=\""+accessOpName+"\" value=\"\"/>\n"+
"      <input type=\"hidden\" name=\""+accessTokenName+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(token)+"\"/>\n"+
"      <a name=\""+seqPrefix+"token_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Delete\" alt=\""+Messages.getAttributeString(locale,"DCTM.DeleteAccessToken")+Integer.toString(k)+"\" onClick='Javascript:"+seqPrefix+"SpecOp(\""+accessOpName+"\",\"Delete\",\""+seqPrefix+"token_"+Integer.toString(k)+"\")'/>\n"+
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
"      <input type=\"hidden\" name=\""+seqPrefix+"tokencount\" value=\""+Integer.toString(k)+"\"/>\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"accessop\" value=\"\"/>\n"+
"      <a name=\""+seqPrefix+"token_"+Integer.toString(k)+"\">\n"+
"        <input type=\"button\" value=\"Add\" alt=\"" + Messages.getAttributeString(locale,"DCTM.AddAccessToken") + "\" onClick='Javascript:"+seqPrefix+"SpecAddToken(\""+seqPrefix+"token_"+Integer.toString(k+1)+"\")'/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">\n"+
"      <input type=\"text\" size=\"30\" name=\""+seqPrefix+"spectoken\" value=\"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specsecurity\" value=\""+(securityOn?"on":"off")+"\"/>\n"
      );
      // Finally, go through forced ACL
      k = 0;
      for (int i = 0; i < ds.getChildCount(); i++)
      {
        SpecificationNode sn = ds.getChild(i);
        if (sn.getType().equals("access"))
        {
          String accessDescription = "_"+Integer.toString(k);
          String token = sn.getAttributeValue("token");
          out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"spectoken"+accessDescription+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(token)+"\"/>\n"
          );
          k++;
        }
      }

      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"tokencount\" value=\""+Integer.toString(k)+"\"/>\n"
      );
    }

    // Document Types tab

    // First, build a hash map containing all the currently selected document types
    Map<String,Object> dtMetadata = new HashMap<String,Object>();
    Map<String,Map<String,Map<String,Set<String>>>> dtFilters = new HashMap<String,Map<String,Map<String,Set<String>>>>();
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(CONFIG_PARAM_OBJECTTYPE))
      {
        String token = sn.getAttributeValue("token");
        if (token != null && token.length() > 0)
        {
          String isAllString = sn.getAttributeValue("all");
          if (isAllString != null && isAllString.equals("true"))
            dtMetadata.put(token,new Boolean(true));
          else
          {
            Set<String> attrMap = new HashSet<String>();
            // Go through the children and look for attribute records
            for (int kk = 0; kk < sn.getChildCount(); kk++)
            {
              SpecificationNode dsn = sn.getChild(kk);
              if (dsn.getType().equals(CONFIG_PARAM_ATTRIBUTENAME))
              {
                String attr = dsn.getAttributeValue("attrname");
                attrMap.add(attr);
              }
            }
            dtMetadata.put(token,attrMap);
          }
          Map<String,Map<String,Set<String>>> filterInfo = new HashMap<String,Map<String,Set<String>>>();
          for (int kk = 0; kk < sn.getChildCount(); kk++)
          {
            SpecificationNode dsn = sn.getChild(kk);
            if (dsn.getType().equals(CONFIG_PARAM_FILTER))
            {
              String name = dsn.getAttributeValue("name");
              String op = dsn.getAttributeValue("op");
              String value = dsn.getAttributeValue("value");
              Map<String,Set<String>> filters = filterInfo.get(name);
              if (filters == null)
              {
                filters = new HashMap<String,Set<String>>();
                filterInfo.put(name,filters);
              }
              Set<String> filterValues = filters.get(op);
              if (filterValues == null)
              {
                filterValues = new HashSet<String>();
                filters.put(op,filterValues);
              }
              filterValues.add(value);
            }
          }
          dtFilters.put(token,filterInfo);
        }
      }
    }

    if (tabName.equals(Messages.getString(locale,"DCTM.DocumentTypes")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );
      
      // Need to catch potential license exception here
      try
      {
        out.print(
"  <tr>\n"+
"    <td class=\"boxcell\" colspan=\"2\">\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"filter_op\" value=\"Continue\"/>\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"DCTM.DocumentType") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"DCTM.Filters") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"DCTM.AllMetadataQ") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"DCTM.SpecificMetadata") + "</nobr></td>\n"+
"        </tr>\n"
        );

        k = 0;
        String[] strarrObjTypes = getObjectTypes();
        for (String strObjectType : strarrObjTypes)
        {
          if (strObjectType != null && strObjectType.length() > 0)
	  {
            // Get the attributes for this data type
            String[] values = getIngestableAttributes(strObjectType);

            out.print(
"        <tr class=\""+(((k % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"          <td class=\"formcolumncell\">\n"+
"            <input type=\"hidden\" name=\""+seqPrefix+"datatype_"+k+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(strObjectType)+"\"/>\n"
            );

            Object o = dtMetadata.get(strObjectType);
            if (o == null)
            {
              out.print(
"            <input type=\"checkbox\" name=\""+seqPrefix+"specfiletype\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(strObjectType)+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(strObjectType)+"</input>\n"
              );
            }
            else
            {
              out.print(
"            <input type=\"checkbox\" name=\""+seqPrefix+"specfiletype\" checked=\"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(strObjectType)+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(strObjectType)+"</input>\n"
              );

            }
            out.print(
"          </td>\n"+
"          <td class=\"boxcell\">\n"+
"            <input type=\"hidden\" name=\""+seqPrefix+"filter_"+k+"_op\" value=\"Continue\"/>\n"+
"            <table class=\"formtable\">\n"+
"              <tr class=\"formheaderrow\">\n"+
"                <td class=\"formcolumnheader\"></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"DCTM.AttributeName") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"DCTM.Operation") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"DCTM.Value") + "</nobr></td>\n"+
"              </tr>\n"
            );
            
            // Now, do filters.  This will be a table-with-a-table, with an "Add" button at the bottom.
            Map<String,Map<String,Set<String>>> currentFilters = dtFilters.get(strObjectType);
            int l = 0;
            if (currentFilters != null)
            {
              String[] filterAttributes = currentFilters.keySet().toArray(new String[0]);
              java.util.Arrays.sort(filterAttributes);
              for (String filterAttribute : filterAttributes)
              {
                Map<String,Set<String>> filters = currentFilters.get(filterAttribute);
                String[] sortedOperations = filters.keySet().toArray(new String[0]);
                java.util.Arrays.sort(sortedOperations);
                for (String filterOperation : sortedOperations)
                {
                  Set<String> filterValues = filters.get(filterOperation);
                  String[] sortedValues = filterValues.toArray(new String[0]);
                  java.util.Arrays.sort(sortedValues);
                  for (String filterValue : sortedValues)
                  {
                    out.print(
"              <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <input type=\"hidden\" name=\""+seqPrefix+"filter_"+k+"_"+l+"_op\" value=\"Continue\"/>\n"+
"                  <input type=\"hidden\" name=\""+seqPrefix+"filter_"+k+"_"+l+"_name\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(filterAttribute)+"\"/>\n"+
"                  <input type=\"hidden\" name=\""+seqPrefix+"filter_"+k+"_"+l+"_operation\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(filterOperation)+"\"/>\n"+
"                  <input type=\"hidden\" name=\""+seqPrefix+"filter_"+k+"_"+l+"_value\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(filterValue)+"\"/>\n"+
"                  <a name=\""+seqPrefix+"filter_"+k+"_"+l+"\">\n"+
"                    <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"DCTM.Delete") + "\" alt=\""+Messages.getAttributeString(locale,"DCTM.DeleteFilter")+"\" onclick='javascript:"+seqPrefix+"DeleteFilter("+k+","+l+");'/>\n"+
"                  </a>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(filterAttribute)+"\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(filterOperation)+"\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(filterValue)+"\n"+
"                </td>\n"+
"              </tr>\n"
                    );
                    l++;
                  }
                }
              }
            }
            
            if (l == 0)
            {
              out.print(
"              <tr class=\"formrow\"><td colspan=\"4\" class=\"formcolumnmessage\"><nobr>" + Messages.getBodyString(locale,"DCTM.NoAttributeFiltersSpecified") + "</nobr></td></tr>\n"
              );
            }
            out.print(
"              <tr class=\"formrow\"><td colspan=\"4\" class=\"formseparator\"><hr/></td></tr>\n"+
"              <tr class=\"formrow\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <a name=\""+seqPrefix+"filter_"+k+"_"+l+"\">\n"+
"                    <input type=\"button\" value=\"" + Messages.getAttributeString(locale,"DCTM.Add") + "\" alt=\"" + Messages.getAttributeString(locale,"DCTM.AddFilter") + "\" onclick='javascript:"+seqPrefix+"AddFilter("+k+","+l+");'/>\n"+
"                    <input type=\"hidden\" name=\""+seqPrefix+"filter_"+k+"_count\" value=\""+l+"\"/>\n"+
"                  </a>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <select multiple=\"false\" name=\""+seqPrefix+"filter_"+k+"_name\" size=\"3\">\n"+
"                    <option value=\"\" selected=\"selected\">" + Messages.getBodyString(locale,"DCTM.PickAnAttribute") + "</option>\n"
            );

            for (String attributeName : values)
            {
              out.print(
"                    <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(attributeName)+"\">" + org.apache.manifoldcf.ui.util.Encoder.bodyEscape(attributeName) + "</option>\n"
              );
            }

            out.print(
"                  </select>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <select multiple=\"false\" name=\""+seqPrefix+"filter_"+k+"_operation\" size=\"3\">\n"+
"                    <option value=\"\" selected=\"selected\">" + Messages.getBodyString(locale,"DCTM.PickAnOperation") + "</option>\n"+
"                    <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape("=")+"\">" + Messages.getBodyString(locale,"DCTM.Equals") + "</option>\n"+
"                    <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape("<>")+"\">" + Messages.getBodyString(locale,"DCTM.NotEquals") + "</option>\n"+
"                  </select>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\"><input type=\"text\" name=\""+seqPrefix+"filter_"+k+"_value\" size=\"30\" value=\"\"/></td>\n"+
"              </tr>\n"+
"            </table>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"
            );
            
            boolean isAll = false;
            Set<String> attrMap = null;
            if (o instanceof Boolean)
            {
              isAll = ((Boolean)o).booleanValue();
              attrMap = new HashSet<String>();
            }
            else
            {
              isAll = false;
              attrMap = (Set<String>)o;
            }
            out.print(
"            <input type=\"checkbox\" name=\""+seqPrefix+"specfileallattrs_"+k+"\" value=\"true\" "+(isAll?"checked=\"\"":"")+"/>\n"+
"          </td>\n"+
"          <td class=\"formcolumncell\">\n"+
"            <select multiple=\"true\" name=\""+seqPrefix+"specfileattrs_"+k+"\" size=\"3\">\n"
            );
            for (String option : values)
            {
              if (attrMap != null && attrMap.contains(option))
              {
                // Selected
                out.print(
"              <option selected=\"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(option)+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(option)+"</option>\n"
                );
              }
              else
              {
                // Unselected
                out.print(
"              <option value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(option)+"\">"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(option)+"</option>\n"
                );
              }
            }
            out.print(
"            </select>\n"+
"          </td>\n"
            );
            out.print(
"        </tr>\n"
            );
            k++;
      	  }
	}
        out.print(
"      </table>\n"+
"      <input type=\"hidden\" name=\""+seqPrefix+"datatype_count\" value=\""+k+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"
        );
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
      k = 0;
      for (String strObjectType : dtMetadata.keySet())
      {
        out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"datatype_"+k+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(strObjectType)+"\"/>\n"
        );
        Map<String,Map<String,Set<String>>> currentFilters = dtFilters.get(strObjectType);
        int l = 0;
        for (String filterAttribute : currentFilters.keySet())
        {
          Map<String,Set<String>> filters = currentFilters.get(filterAttribute);
          for (String filterOperation : filters.keySet())
          {
            Set<String> filterValues = filters.get(filterOperation);
            for (String filterValue : filterValues)
            {
              out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"filter_"+k+"_"+l+"_name\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(filterAttribute)+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"filter_"+k+"_"+l+"_operation\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(filterOperation)+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"filter_"+k+"_"+l+"_value\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(filterValue)+"\"/>\n"
              );
              l++;
            }
          }
        }
        out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"filter_"+k+"_count\" value=\""+l+"\"/>\n"
        );

        Object o = dtMetadata.get(strObjectType);
        out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specfiletype\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(strObjectType)+"\"/>\n"
        );
        if (o instanceof Boolean)
        {
          Boolean b = (Boolean)o;
          out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specfileallattrs_"+k+"\" value=\""+(b.booleanValue()?"true":"false")+"\"/>\n"
          );
        }
        else
        {
          Set<String> map = (Set<String>)o;
          for (String attrName : map)
          {
            out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specfileattrs_"+k+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(attrName)+"\"/>\n"
            );
          }
        }
        k++;
      }
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"datatype_count\" value=\""+k+"\"/>\n"
      );

    }

    // Content types tab

    // First, build a hash map containing all the currently selected document types
    Set<String> ctMap = null;
    boolean seenAll = false;
    boolean doAll = false;
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(CONFIG_PARAM_FORMAT))
      {
	seenAll = true;
        String token = sn.getAttributeValue("value");
        if (token != null && token.length() > 0)
        {
          if (ctMap == null)
            ctMap = new HashSet<String>();
          ctMap.add(token);
        }
      }
      else if (sn.getType().equals(CONFIG_PARAM_FORMAT_ALL))
      {
	seenAll = true;
	String value = sn.getAttributeValue("value");
	if (value.equals("true")) {
	  doAll = true;
	}
      }
    }

    // Hidden variable so we know that the form was posted
    out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specmimetype_posted\" value=\"true\"/>\n"
    );
    
    if (tabName.equals(Messages.getString(locale,"DCTM.ContentTypes")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\">\n"
      );
      
      // If _ALL record not even seen, do default thing
      if (seenAll == false || doAll)
      {
      out.print(
"      <input type=\"checkbox\" name=\""+seqPrefix+"specmimetypeall\" checked=\"\" value=\"true\"></input>\n"
      );
      }
      else 
      {
      out.print(
"      <input type=\"checkbox\" name=\""+seqPrefix+"specmimetypeall\" value=\"true\"></input>\n"
      );
      }
      out.print(
"    </td>\n"+
"    <td class=\"value\">\n"+
"      "+Messages.getBodyString(locale,"DCTM.AllContentTypes")+"\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"
      );     
      // Need to catch potential license exception here
      try
      {
        String[] strarrMimeTypes = getContentTypes();
        for (String strMimeType : strarrMimeTypes)
        {
          if (strMimeType != null && strMimeType.length() > 0)
	  {
            out.print(
"  <tr>\n"+
"    <td class=\"description\">\n"
            );
            if (ctMap != null && ctMap.contains(strMimeType))
            {
              out.print(
"      <input type=\"checkbox\" name=\""+seqPrefix+"specmimetype\" checked=\"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(strMimeType)+"\"></input>\n"
              );
            }
            else
            {
              out.print(
"      <input type=\"checkbox\" name=\""+seqPrefix+"specmimetype\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(strMimeType)+"\"></input>\n"
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
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specmimetypeall\" value=\""+((seenAll == false || doAll)?"true":"false")+"\"/>\n"
      );

      if (ctMap != null)
      {
        for (String strMimeType : ctMap)
        {
          out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specmimetype\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(strMimeType)+"\"/>\n"
          );
        }
      }
    }


    // The Content Length tab

    // Search for max document size
    String maxDocLength = "";
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(CONFIG_PARAM_MAXLENGTH))
      {
        maxDocLength = sn.getAttributeValue("value");
      }
    }

    if (tabName.equals(Messages.getString(locale,"DCTM.ContentLength")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"+
"    <td class=\"separator\" colspan=\"2\"><hr/></td>\n"+
"  </tr>\n"+
"  <tr>\n"+
"    <td class=\"description\"><nobr>" + Messages.getBodyString(locale,"DCTM.ContentLength") + "</nobr></td>\n"+
"    <td class=\"value\">\n"+
"      <input name=\""+seqPrefix+"specmaxdoclength\" type=\"text\" size=\"10\" value=\""+maxDocLength+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specmaxdoclength\" value=\""+maxDocLength+"\"/>\n"
      );
    }


    // Path metadata tab

    // Find the path-value metadata attribute name
    String pathNameAttribute = "";
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(CONFIG_PARAM_PATHNAMEATTRIBUTE))
      {
        pathNameAttribute = sn.getAttributeValue("value");
      }
    }

    // Find the path-value mapping data
    org.apache.manifoldcf.crawler.connectors.DCTM.MatchMap matchMap = new org.apache.manifoldcf.crawler.connectors.DCTM.MatchMap();
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(CONFIG_PARAM_PATHMAP))
      {
        String pathMatch = sn.getAttributeValue("match");
        String pathReplace = sn.getAttributeValue("replace");
        matchMap.appendMatchPair(pathMatch,pathReplace);
      }
    }

    if (tabName.equals(Messages.getString(locale,"DCTM.PathMetadata")) && connectionSequenceNumber == actualSequenceNumber)
    {
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specmappingcount\" value=\""+Integer.toString(matchMap.getMatchCount())+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"specmappingop\" value=\"\"/>\n"+
"\n"+
"<table class=\"displaytable\">\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"+
"  <tr>\n"+
"    <td class=\"description\" colspan=\"1\"><nobr>" + Messages.getBodyString(locale,"DCTM.PathAttributeName") + "</nobr></td>\n"+
"    <td class=\"value\" colspan=\"3\">\n"+
"      <input type=\"text\" name=\""+seqPrefix+"specpathnameattribute\" size=\"20\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathNameAttribute)+"\"/>\n"+
"    </td>\n"+
"  </tr>\n"+
"  <tr><td class=\"separator\" colspan=\"4\"><hr/></td></tr>\n"
      );
      for (int i = 0; i < matchMap.getMatchCount(); i++)
      {
        String matchString = matchMap.getMatchString(i);
        String replaceString = matchMap.getReplaceString(i);
        out.print(
"  <tr>\n"+
"    <td class=\"description\"><input type=\"hidden\" name=\""+seqPrefix+"specmappingop_"+Integer.toString(i)+"\" value=\"\"/>\n"+
"      <a name=\""+seqPrefix+"mapping_"+Integer.toString(i)+"\">\n"+
"        <input type=\"button\" onClick='Javascript:"+seqPrefix+"SpecOp(\""+seqPrefix+"specmappingop_"+Integer.toString(i)+"\",\"" + Messages.getAttributeJavascriptString(locale,"DCTM.Delete") + "\",\""+seqPrefix+"mapping_"+Integer.toString(i)+"\")' alt=\"" + Messages.getAttributeString(locale,"DCTM.DeleteMapping") + Integer.toString(i)+"\" value=\"Delete\"/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\"><input type=\"hidden\" name=\""+seqPrefix+"specmatch_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(matchString)+"\"/>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(matchString)+"</td>\n"+
"    <td class=\"value\">==></td>\n"+
"    <td class=\"value\"><input type=\"hidden\" name=\""+seqPrefix+"specreplace_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(replaceString)+"\"/>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(replaceString)+"</td>\n"+
"  </tr>\n"
        );
      }
      if (matchMap.getMatchCount() == 0)
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
"      <a name=\""+seqPrefix+"mapping_"+Integer.toString(matchMap.getMatchCount())+"\">\n"+
"        <input type=\"button\" onClick='Javascript:"+seqPrefix+"SpecAddMapping(\""+seqPrefix+"mapping_"+Integer.toString(matchMap.getMatchCount()+1)+"\")' alt=\"" + Messages.getAttributeString(locale,"DCTM.AddToMappings") + "\" value=\"" + Messages.getAttributeString(locale,"DCTM.Add") + "\"/>\n"+
"      </a>\n"+
"    </td>\n"+
"    <td class=\"value\">" + Messages.getBodyString(locale,"DCTM.MatchRegexp") + "&nbsp;<input type=\"text\" name=\""+seqPrefix+"specmatch\" size=\"32\" value=\"\"/></td>\n"+
"    <td class=\"value\">==></td>\n"+
"    <td class=\"value\">" + Messages.getBodyString(locale,"DCTM.ReplaceString") + "&nbsp;<input type=\"text\" name=\""+seqPrefix+"specreplace\" size=\"32\" value=\"\"/></td>\n"+
"  </tr>\n"+
"</table>\n"
      );
    }
    else
    {
      out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specmappingcount\" value=\""+Integer.toString(matchMap.getMatchCount())+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"specpathnameattribute\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(pathNameAttribute)+"\"/>\n"
      );
      for (int i = 0; i < matchMap.getMatchCount(); i++)
      {
        String matchString = matchMap.getMatchString(i);
        String replaceString = matchMap.getReplaceString(i);
        out.print(
"<input type=\"hidden\" name=\""+seqPrefix+"specmatch_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(matchString)+"\"/>\n"+
"<input type=\"hidden\" name=\""+seqPrefix+"specreplace_"+Integer.toString(i)+"\" value=\""+org.apache.manifoldcf.ui.util.Encoder.attributeEscape(replaceString)+"\"/>\n"
        );
      }
    }
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
    String x;
    String[] y;
    
    String seqPrefix = "s"+connectionSequenceNumber+"_";

    x = variableContext.getParameter(seqPrefix+"pathcount");
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
      	String pathOpName = seqPrefix+"pathop"+pathDescription;
        String pathName = seqPrefix+"specpath"+pathDescription;
      	x = variableContext.getParameter(pathOpName);
      	if (x != null && x.equals("Delete"))
      	{
          // Skip to the next
          i++;
          continue;
      	}
      	// Path inserts won't happen until the very end
      	String path = variableContext.getParameter(pathName);
      	SpecificationNode node = new SpecificationNode(CONFIG_PARAM_LOCATION);
      	node.setAttribute("path",path);
      	ds.addChild(ds.getChildCount(),node);
      	i++;
      }

      // See if there's a global add operation
      String op = variableContext.getParameter(seqPrefix+"pathop");
      if (op != null && op.equals("Add"))
      {
      	String path = variableContext.getParameter(seqPrefix+"specpath");
      	SpecificationNode node = new SpecificationNode(CONFIG_PARAM_LOCATION);
      	node.setAttribute("path",path);
      	ds.addChild(ds.getChildCount(),node);
      }
      else if (op != null && op.equals("Up"))
      {
      	// Strip off end
      	String path = variableContext.getParameter(seqPrefix+"specpath");
      	int k = path.lastIndexOf("/");
      	if (k != -1)
          path = path.substring(0,k);
      	if (path.length() == 0)
          path = "/";
      	currentContext.save(seqPrefix+"specpath",path);
      }
      else if (op != null && op.equals("AddToPath"))
      {
      	String path = variableContext.getParameter(seqPrefix+"specpath");
      	String addon = variableContext.getParameter(seqPrefix+"pathaddon");
      	if (addon != null && addon.length() > 0)
      	{
          if (path.length() == 1)
            path = "/" + addon;
          else
            path += "/" + addon;
      	}
      	currentContext.save(seqPrefix+"specpath",path);
      }
    }

    x = variableContext.getParameter(seqPrefix+"specsecurity");
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

    x = variableContext.getParameter(seqPrefix+"tokencount");
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
      	String accessOpName = seqPrefix+"accessop"+accessDescription;
      	x = variableContext.getParameter(accessOpName);
      	if (x != null && x.equals("Delete"))
      	{
          // Next row
          i++;
          continue;
      	}
      	// Get the stuff we need
      	String accessSpec = variableContext.getParameter(seqPrefix+"spectoken"+accessDescription);
      	SpecificationNode node = new SpecificationNode("access");
      	node.setAttribute("token",accessSpec);
      	ds.addChild(ds.getChildCount(),node);
      	i++;
      }

      String op = variableContext.getParameter(seqPrefix+"accessop");
      if (op != null && op.equals("Add"))
      {
      	String accessspec = variableContext.getParameter(seqPrefix+"spectoken");
      	SpecificationNode node = new SpecificationNode("access");
      	node.setAttribute("token",accessspec);
      	ds.addChild(ds.getChildCount(),node);
      }
    }

    x = variableContext.getParameter(seqPrefix+"datatype_count");
    if (x != null)
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

      int dataCount = Integer.parseInt(x);
      
      y = variableContext.getParameterValues(seqPrefix+"specfiletype");
      Set<String> checkedTypes = new HashSet<String>();
      if (y != null)
      {
        for (String s : y)
        {
          checkedTypes.add(s);
        }
      }
      
      // Loop through specs
      for (int k = 0; k < dataCount; k++)
      {
      	String fileType = variableContext.getParameter(seqPrefix+"datatype_"+k);
        if (checkedTypes.contains(fileType))
        {
          SpecificationNode node = new SpecificationNode(CONFIG_PARAM_OBJECTTYPE);
          node.setAttribute("token",fileType);
          String isAll = variableContext.getParameter(seqPrefix+"specfileallattrs_"+k);
          if (isAll != null)
            node.setAttribute("all",isAll);
          String[] z = variableContext.getParameterValues(seqPrefix+"specfileattrs_"+k);
          if (z != null)
          {
            for (int kk = 0; kk < z.length; kk++)
            {
              SpecificationNode attrNode = new SpecificationNode(CONFIG_PARAM_ATTRIBUTENAME);
              attrNode.setAttribute("attrname",z[kk]);
              node.addChild(node.getChildCount(),attrNode);
            }
          }
          x = variableContext.getParameter(seqPrefix+"filter_"+k+"_count");
          int filterCount = Integer.parseInt(x);
          for (int kk = 0; kk < filterCount; kk++)
          {
            String op = variableContext.getParameter(seqPrefix+"filter_"+k+"_"+kk+"_op");
            if (op == null || !op.equals("Delete"))
            {
              String attributeName = variableContext.getParameter(seqPrefix+"filter_"+k+"_"+kk+"_name");
              String operation = variableContext.getParameter(seqPrefix+"filter_"+k+"_"+kk+"_operation");
              String value = variableContext.getParameter(seqPrefix+"filter_"+k+"_"+kk+"_value");
              SpecificationNode filterNode = new SpecificationNode(CONFIG_PARAM_FILTER);
              filterNode.setAttribute("name",attributeName);
              filterNode.setAttribute("op",operation);
              filterNode.setAttribute("value",value);
              node.addChild(node.getChildCount(),filterNode);
            }
          }
          // Add at the end
          x = variableContext.getParameter(seqPrefix+"filter_"+k+"_op");
          if (x != null && x.equals("Add"))
          {
            String attributeName = variableContext.getParameter(seqPrefix+"filter_"+k+"_name");
            String operation = variableContext.getParameter(seqPrefix+"filter_"+k+"_operation");
            String value = variableContext.getParameter(seqPrefix+"filter_"+k+"_value");
            SpecificationNode filterNode = new SpecificationNode(CONFIG_PARAM_FILTER);
            filterNode.setAttribute("name",attributeName);
            filterNode.setAttribute("op",operation);
            filterNode.setAttribute("value",value);
            node.addChild(node.getChildCount(),filterNode);
          }
          ds.addChild(ds.getChildCount(),node);
        }
      }
    }

    if (variableContext.getParameter(seqPrefix+"specmimetype_posted") != null)
    {
      String all = variableContext.getParameter(seqPrefix+"specmimetypeall");
      y = variableContext.getParameterValues(seqPrefix+"specmimetype");
      // Delete all file specs first
      int i = 0;
      while (i < ds.getChildCount())
      {
      	SpecificationNode sn = ds.getChild(i);
      	if (sn.getType().equals(CONFIG_PARAM_FORMAT) || sn.getType().equals(CONFIG_PARAM_FORMAT_ALL))
          ds.removeChild(i);
      	else
          i++;
      }
      
      SpecificationNode n2 = new SpecificationNode(CONFIG_PARAM_FORMAT_ALL);
      n2.setAttribute("value",(all!=null&&all.equals("true"))?"true":"false");
      ds.addChild(ds.getChildCount(),n2);

      // Loop through specs
      if (y != null)
      {
	i = 0;
	while (i < y.length)
	{
	  String fileType = y[i++];
	  SpecificationNode node = new SpecificationNode(CONFIG_PARAM_FORMAT);
	  node.setAttribute("value",fileType);
	  ds.addChild(ds.getChildCount(),node);
	}
      }
    }

    x = variableContext.getParameter(seqPrefix+"specmaxdoclength");
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

    String xc = variableContext.getParameter(seqPrefix+"specpathnameattribute");
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

    xc = variableContext.getParameter(seqPrefix+"specmappingcount");
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
      	String pathOpName = seqPrefix+"specmappingop"+pathDescription;
      	xc = variableContext.getParameter(pathOpName);
      	if (xc != null && xc.equals("Delete"))
      	{
          // Skip to the next
          i++;
          continue;
      	}
      	// Inserts won't happen until the very end
      	String match = variableContext.getParameter(seqPrefix+"specmatch"+pathDescription);
      	String replace = variableContext.getParameter(seqPrefix+"specreplace"+pathDescription);
      	SpecificationNode node = new SpecificationNode(CONFIG_PARAM_PATHMAP);
      	node.setAttribute("match",match);
      	node.setAttribute("replace",replace);
      	ds.addChild(ds.getChildCount(),node);
      	i++;
      }

      // Check for add
      xc = variableContext.getParameter(seqPrefix+"specmappingop");
      if (xc != null && xc.equals("Add"))
      {
      	String match = variableContext.getParameter(seqPrefix+"specmatch");
      	String replace = variableContext.getParameter(seqPrefix+"specreplace");
      	SpecificationNode node = new SpecificationNode(CONFIG_PARAM_PATHMAP);
      	node.setAttribute("match",match);
      	node.setAttribute("replace",replace);
      	ds.addChild(ds.getChildCount(),node);
      }
    }
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
    out.print(
"<table class=\"displaytable\">\n"+
"  <tr>\n"
    );
    boolean seenAny = false;
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
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
"  <tr>\n"+
"    <td class=\"boxcell\" colspan=\"2\">\n"+
"      <table class=\"formtable\">\n"+
"        <tr class=\"formheaderrow\">\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"DCTM.DocumentType") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"DCTM.Filters") + "</nobr></td>\n"+
"          <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"DCTM.Metadata") + "</nobr></td>\n"+
"        </tr>\n"
    );
    int l = 0;
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
      if (sn.getType().equals(CONFIG_PARAM_OBJECTTYPE))
      {
        out.print(
"        <tr class=\""+(((l % 2)==0)?"evenformrow":"oddformrow")+"\">\n"
        );

        String strObjectType = sn.getAttributeValue("token");
        out.print(
"          <td class=\"formcolumncell\">\n"+
"            "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(strObjectType)+"\n"+
"          </td>\n"
        );
        out.print(
"          <td class=\"boxcell\">\n"+
"            <table class=\"formtable\">\n"+
"              <tr class=\"formheaderrow\">\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"DCTM.AttributeName") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"DCTM.Operation") + "</nobr></td>\n"+
"                <td class=\"formcolumnheader\"><nobr>" + Messages.getBodyString(locale,"DCTM.Value") + "</nobr></td>\n"+
"              </tr>\n"
        );
        Map<String,Map<String,Set<String>>> currentFilters = new HashMap<String,Map<String,Set<String>>>();
        for (int k = 0; k < sn.getChildCount(); k++)
        {
          SpecificationNode dsn = sn.getChild(k);
          if (dsn.getType().equals(CONFIG_PARAM_FILTER))
          {
            String attributeName = dsn.getAttributeValue("name");
            String operation = dsn.getAttributeValue("op");
            String value = dsn.getAttributeValue("value");
            Map<String,Set<String>> filters = currentFilters.get(attributeName);
            if (filters == null)
            {
              filters = new HashMap<String,Set<String>>();
              currentFilters.put(attributeName,filters);
            }
            Set<String> values = filters.get(operation);
            if (values == null)
            {
              values = new HashSet<String>();
              filters.put(operation,values);
            }
            values.add(value);
          }
        }
        
        int kk = 0;
        String[] sortedAttributes = currentFilters.keySet().toArray(new String[0]);
        java.util.Arrays.sort(sortedAttributes);
        for (String filterAttribute : sortedAttributes)
        {
          Map<String,Set<String>> currentOperations = currentFilters.get(filterAttribute);
          String[] sortedOperations = currentOperations.keySet().toArray(new String[0]);
          java.util.Arrays.sort(sortedOperations);
          for (String filterOperation : sortedOperations)
          {
            Set<String> currentValues = currentOperations.get(filterOperation);
            String[] sortedValues = currentValues.toArray(new String[0]);
            java.util.Arrays.sort(sortedValues);
            StringBuilder sb = new StringBuilder();
            boolean commaNeeded = false;
            for (String value : sortedValues)
            {
              if (commaNeeded)
                sb.append(", ");
              else
                commaNeeded = true;
              sb.append("\"").append(value).append("\"");
            }
            out.print(
"              <tr class=\""+(((kk % 2)==0)?"evenformrow":"oddformrow")+"\">\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(filterAttribute)+"</nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(filterOperation)+"</nobr>\n"+
"                </td>\n"+
"                <td class=\"formcolumncell\">\n"+
"                  "+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(sb.toString())+"\n"+
"                </td>\n"+
"              </tr>\n"
            );
            kk++;
          }
        }
        
        out.print(
"            </table>\n"+
"          </td>\n"
        );
        out.print(
"          <td class=\"formcolumncell\">\n"
        );

        String isAll = sn.getAttributeValue("all");
        if (isAll != null && isAll.equals("true"))
          out.print(
"            <nobr>" + Messages.getBodyString(locale,"DCTM.allMetadataAttributes") + "</nobr>\n"
          );
        else
        {
          for (int k = 0; k < sn.getChildCount(); k++)
          {
            SpecificationNode dsn = sn.getChild(k);
            if (dsn.getType().equals(CONFIG_PARAM_ATTRIBUTENAME))
            {
              String attrName = dsn.getAttributeValue("attrname");
              out.print(
"            <nobr>"+org.apache.manifoldcf.ui.util.Encoder.bodyEscape(attrName)+"</nobr>\n"
              );
            }
          }
        }
        out.print(
"          </td>\n"
        );

        out.print(
"        </tr>\n"
        );
        
        l++;
      }
      
    }
    if (l == 0)
    {
      out.print(
"        <tr class=\"formrow\">\n"+
"          <td colspan=\"3\" class=\"message\">" + Messages.getBodyString(locale,"DCTM.NoDocumentTypesSpecified") + "</td>\n"+
"        </tr>\n"
      );
    }
    out.print(
"      </table>\n"+
"    </td>\n"+
"  </tr>\n"+
"\n"+
"  <tr><td class=\"separator\" colspan=\"2\"><hr/></td></tr>\n"+
"  <tr>\n"
    );
    seenAny = false;
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
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
    String maxDocumentLength = "unlimited";
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
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
    boolean securityOn = true;
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
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
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
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
    String pathNameAttribute = "";
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
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
    org.apache.manifoldcf.crawler.connectors.DCTM.MatchMap matchMap = new org.apache.manifoldcf.crawler.connectors.DCTM.MatchMap();
    for (int i = 0; i < ds.getChildCount(); i++)
    {
      SpecificationNode sn = ds.getChild(i);
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
      for (int i = 0; i < matchMap.getMatchCount(); i++)
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
        GetListOfValuesThread t = new GetListOfValuesThread(dql,"name");
        try
        {
          t.start();
          List<String> contentTypes = t.finishUp();
          
          String[] rval = new String[contentTypes.size()];
          int i = 0;
          while (i < rval.length)
          {
            rval[i] = contentTypes.get(i);
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
    //if (true)
    //  return new String[]{"type1","type2","type3"};
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
        GetListOfValuesThread t = new GetListOfValuesThread(strDQL,"r_type_name");
        try
        {
          t.start();
          List<String> objectTypes = t.finishUp();
          String[] rval = new String[objectTypes.size()];
          int i = 0;
          while (i < rval.length)
          {
            rval[i] = objectTypes.get(i);
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
    protected final String strTheParentFolderPath;
    
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

    public String[] finishUp()
      throws InterruptedException, RemoteException, DocumentumException
    {
      join();
      Throwable thr = exception;
      if (thr != null)
      {
        if (thr instanceof RemoteException)
          throw (RemoteException)thr;
        else if (thr instanceof DocumentumException)
          throw (DocumentumException)thr;
        else if (thr instanceof RuntimeException)
          throw (RuntimeException)thr;
        else if (thr instanceof Error)
          throw (Error)thr;
        else
          throw new RuntimeException("Unexpected exception type: "+thr.getClass().getName()+": "+thr.getMessage(),thr);
      }
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
          return t.finishUp();
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
    //if (true)
    //  return new String[]{"attribute1","attribute2","attribute3"};
    try
    {
      String strDQL = "select distinct attr_name FROM dmi_dd_attr_info where type_name = '" + docType + "' order by attr_name asc";
      while (true)
      {
        boolean noSession = (session==null);
        getSession();
        GetListOfValuesThread t = new GetListOfValuesThread(strDQL,"attr_name");
        try
        {
          t.start();
          List<String> attributes = t.finishUp();
          String[] rval = new String[attributes.size()];
          int i = 0;
          while (i < rval.length)
          {
            rval[i] = attributes.get(i);
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

  /** This class digests specifications and allows easy access to the data within.
  */
  protected class SpecInfo
  {
    /** The path attribute name */
    protected final String pathAttributeName;
    /** The folder ID to path name mapping (which acts like a cache).
      The key is the folder ID, and the value is an array of Strings. */
    protected final Map<String,String[]> pathMap = new HashMap<String,String[]>();
    /** The path name map */
    protected final MatchMap matchMap = new MatchMap();
    /** A set of forced acls */
    protected final Set<String> aclSet = new HashSet<String>();
    /** Security on/off */
    protected final boolean securityOn;
    /** Map of type to selected attributes */
    protected final Map<String,List<String>> typeMap = new HashMap<String,List<String>>();

    /** Constructor */
    public SpecInfo(Specification spec)
      throws ManifoldCFException, ServiceInterruption
    {
      String pathAttributeName = null;
      boolean securityOn = true;
      for (int i = 0; i < spec.getChildCount(); i++)
      {
        SpecificationNode n = spec.getChild(i);
        if (n.getType().equals(CONFIG_PARAM_PATHNAMEATTRIBUTE))
          pathAttributeName = n.getAttributeValue("value");
        else if (n.getType().equals(CONFIG_PARAM_PATHMAP))
        {
          String pathMatch = n.getAttributeValue("match");
          String pathReplace = n.getAttributeValue("replace");
          matchMap.appendMatchPair(pathMatch,pathReplace);
        }
        else if (n.getType().equals("access"))
        {
          String token = n.getAttributeValue("token");
          aclSet.add(token);
        }
        else if (n.getType().equals("security"))
        {
          String value = n.getAttributeValue("value");
          if (value.equals("on"))
            securityOn = true;
          else if (value.equals("off"))
            securityOn = false;
        }
        else if (n.getType().equals(CONFIG_PARAM_OBJECTTYPE))
        {
          String typeName = n.getAttributeValue("token");
          String isAll = n.getAttributeValue("all");
          List<String> list;
          if (isAll != null && isAll.equals("true"))
          {
            // "All" attributes are specified
            // The current complete list of attributes must be fetched for this document type
            try
            {
              list = getAttributesForType(typeName);
            }
            catch (DocumentumException e)
            {
              // Base our treatment on the kind of error it is.
              if (e.getType() == DocumentumException.TYPE_SERVICEINTERRUPTION)
              {
                long currentTime = System.currentTimeMillis();
                Logging.connectors.warn("DCTM: Remote service interruption listing attributes: "+e.getMessage(),e);
                throw new ServiceInterruption(e.getMessage(),e,currentTime + 300000L,currentTime + 12 * 60 * 60000L,-1,true);
              }
              throw new ManifoldCFException(e.getMessage(),e);
            }
          }
          else
          {
            list = new ArrayList<String>();
            for (int l = 0; i < n.getChildCount(); l++)
            {
              SpecificationNode sn = n.getChild(l);
              if (sn.getType().equals(CONFIG_PARAM_ATTRIBUTENAME))
              {
                String attrName = sn.getAttributeValue("attrname");
                list.add(attrName);
              }
            }
          }
          typeMap.put(typeName,list);
        }
      }
      this.pathAttributeName = pathAttributeName;
      this.securityOn = securityOn;
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
      for (int i = 0; i < paths.length; i++)
      {
        rval[i] = matchMap.translate(paths[i]);
      }
      return rval;
    }

    /** Grab forced acl out of document specification.
    *@param spec is the document specification.
    *@return the acls.
    */
    public String[] getAcls()
    {
      if (!securityOn)
        return null;

      String[] rval = new String[aclSet.size()];
      Iterator<String> iter = aclSet.iterator();
      int i = 0;
      for (String value : aclSet)
      {
        rval[i++] = value;
      }
      return rval;
    }

    public String getForcedAclString()
    {
      // Get the forced acls (and whether security is on as well)
      String[] acls = getAcls();
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
      return forcedAclString.toString();
    }
    
    public List<String> getMetadataFields(String typeName)
    {
      return typeMap.get(typeName);
    }
    
    public String getMetadataVersionAddendum(String typeName)
    {
      // Sort the attribute names, because we need them to be comparable.
      StringBuilder sb = new StringBuilder();
      List<String> list = typeMap.get(typeName);
      if (list == null)
        packList(sb,new String[0],'+');
      else
      {
        String[] sortArray = new String[list.size()];
        int j = 0;
        for (String thing : list)
        {
          sortArray[j++] = thing;
        }
        java.util.Arrays.sort(sortArray);
        packList(sb,sortArray,'+');
      }
      return sb.toString();
    }
    
    public String getPathNameAttributeAddendum()
    {
      // This starts with = since ; is used by another optional component (the forced acls)
      StringBuilder pathNameAttributeVersion = new StringBuilder();
      if (pathAttributeName != null)
        pathNameAttributeVersion.append("=").append(pathAttributeName).append(":").append(matchMap);
      return pathNameAttributeVersion.toString();
    }
    
  }
  
}
