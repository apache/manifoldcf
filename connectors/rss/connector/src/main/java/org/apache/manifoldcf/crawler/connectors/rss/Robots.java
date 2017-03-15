/* $Id: Robots.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.rss;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.agents.interfaces.*;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.connectorcommon.interfaces.*;
import org.apache.manifoldcf.crawler.system.Logging;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import java.net.*;


/** This class is a cache of a specific robots data.  It is loaded and fetched according to standard
* robots rules; namely, caching for up to 24 hrs, format and parsing rules consistent with
* http://www.robotstxt.org/wc/robots.html.  The apache Httpclient is used to fetch the robots files, when necessary.
* An instance of this class should be constructed statically in order for the caching properties to work to
* maximum advantage.
*/
public class Robots
{
  public static final String _rcsid = "@(#)$Id: Robots.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Robots fetch timeout value */
  protected static final int ROBOT_TIMEOUT_MILLISECONDS = 60000;
  /** Robots connection type value */
  protected static final String ROBOT_CONNECTION_TYPE = "Robot";
  /** Robot file name value */
  protected static final String ROBOT_FILE_NAME = "/robots.txt";

  /** Fetcher to use to get the data from wherever */
  protected ThrottledFetcher fetcher;
  /** Reference count */
  protected int refCount = 0;

  /** This is the cache hash - which is keyed by the protocol/host/port, and has a Host object as the
  * value.
  */
  protected Map cache = new HashMap();

  /** Constructor.
  */
  public Robots(ThrottledFetcher fetcher)
  {
    this.fetcher = fetcher;
  }

  /** Note that a connection has been established. */
  public synchronized void noteConnectionEstablished()
  {
    refCount++;
  }

  /** Note that a connection has been released, and free resources if no reason
  * to retain them. */
  public synchronized void noteConnectionReleased()
  {
    refCount--;
    if (refCount == 0)
    {
      // Clear the cache!
      cache.clear();
    }
  }

  /** Clean idle stuff out of cache */
  public synchronized void poll()
  {
    // What we want to do is get the current time and
    // expire all entries that are marked for death before that time.

    Map newCache = new HashMap();
    long currentTime = System.currentTimeMillis();
    Iterator iter = cache.keySet().iterator();
    while (iter.hasNext())
    {
      String identifyingString = (String)iter.next();
      Host host = (Host)cache.get(identifyingString);
      if (!host.canBeFlushed(currentTime))
        newCache.put(identifyingString,host);
    }
    cache = newCache;
  }

  /** Decide whether a specific robot can crawl a specific URL.
  * A ServiceInterruption exception is thrown if the fetch itself fails in a transient way.
  * A permanent failure (such as an invalid URL) with throw a ManifoldCFException.
  *@param userAgent is the user-agent string used by the robot.
  *@param from is the email address.
  *@param protocol is the name of the protocol (e.g. "http")
  *@param port is the port number (-1 being the default for the protocol)
  *@param hostName is the fqdn of the host
  *@param pathString is the path (non-query) part of the URL
  *@return true if fetch is allowed, false otherwise.
  */
  public boolean isFetchAllowed(IThreadContext threadContext, String throttleGroupName,
    String protocol, int port, String hostName, String pathString,
    String userAgent, String from,
    String proxyHost, int proxyPort, String proxyAuthDomain, String proxyAuthUsername, String proxyAuthPassword,
    IProcessActivity activities, int connectionLimit)
    throws ManifoldCFException, ServiceInterruption
  {
    String identifyingString = protocol + "://" + hostName;
    if (port != -1)
      identifyingString += ":" + Integer.toString(port);

    // Now, look for a valid Host object, and create one if none exists.
    Host host;

    synchronized (this)
    {
      host = (Host)cache.get(identifyingString);
      if (host == null)
      {
        host = new Host(protocol,port,hostName);
        cache.put(identifyingString,host);
      }
    }

    return host.isFetchAllowed(threadContext,throttleGroupName,
      System.currentTimeMillis(),pathString,
      userAgent,from,
      proxyHost, proxyPort,proxyAuthDomain,proxyAuthUsername,proxyAuthPassword,activities,connectionLimit);
  }

  // Protected methods and classes

  /** Convert a string from the robots file into a readable form that does NOT contain NUL characters (since postgresql does not accept those).
  */
  protected static String makeReadable(String inputString)
  {
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < inputString.length())
    {
      char y = inputString.charAt(i++);
      if (y >= ' ')
        sb.append(y);
      else
      {
        sb.append('^');
        sb.append((char)(y + '@'));
      }
    }
    return sb.toString();
  }

  /** Check if path matches specification */
  protected static boolean doesPathMatch(String path, String spec)
  {
    // For robots 1.0, this function would do just this:
    // return path.startsWith(spec);
    // However, we implement the "google bot" spec, which allows wildcard matches that are, in fact, regular-expression-like in some ways.
    // The "specification" can be found here: http://www.google.com/support/webmasters/bin/answer.py?hl=en&answer=40367
    return doesPathMatch(path,0,spec,0);
  }

  /** Recursive method for matching specification to path. */
  protected static boolean doesPathMatch(String path, int pathIndex, String spec, int specIndex)
  {
    while (true)
    {
      if (specIndex == spec.length())
        // Hit the end of the specification!  We're done.
        return true;
      char specChar = spec.charAt(specIndex++);
      if (specChar == '*')
      {
        // Found a specification wildcard.
        // Eat up all the '*' characters at this position - otherwise each additional one increments the exponent of how long this can take,
        // making denial-of-service via robots parsing a possibility.
        while (specIndex < spec.length())
        {
          if (spec.charAt(specIndex) != '*')
            break;
          specIndex++;
        }
        // It represents zero or more characters, so we must recursively try for a match against all remaining characters in the path string.
        while (true)
        {
          boolean match = doesPathMatch(path,pathIndex,spec,specIndex);
          if (match)
            return true;
          if (path.length() == pathIndex)
            // Nothing further to try, and no match
            return false;
          pathIndex++;
          // Try again
        }
      }
      else if (specChar == '$' && specIndex == spec.length())
      {
        // Found a specification end-of-path character.
        // (It can only be legitimately the last character of the specification.)
        return pathIndex == path.length();
      }
      if (pathIndex == path.length())
        // Hit the end of the path! (but not the end of the specification!)
        return false;
      if (path.charAt(pathIndex) != specChar)
        return false;
      // On to the next match
      pathIndex++;
    }
  }

  /** This class maintains status for a given host.  There's an instance of this class for
  * each host in the robots cache.
  */
  protected class Host
  {
    /** Protocol */
    protected String protocol;
    /** Port */
    protected int port;
    /** Host name */
    protected String hostName;
    /** Timestamp.  This is the time that the cache record becomes invalid. */
    protected long invalidTime = -1L;
    /** This flag describes whether or not the host record is valid yet. */
    protected boolean isValid = false;
    /** This is the list of robots records for the host, or null if no robots.txt found. */
    protected ArrayList records = null;
    /** This will be set to "true" if the robots.txt for this host is in the process of being read. */
    protected boolean readingRobots = false;
    /** This will be set to nonzero if the robots structure is currently in use */
    protected int checkingRobots = 0;

    /** Constructor.
    */
    public Host(String protocol, int port, String hostName)
    {
      this.protocol = protocol;
      this.port = port;
      this.hostName = hostName;
    }

    /** Check a given path string against this host's robots file.
    *@param currentTime is the current time in milliseconds since epoch.
    *@param pathString is the path string to check.
    *@return true if crawling is allowed, false otherwise.
    */
    public boolean isFetchAllowed(IThreadContext threadContext, String throttleGroupName,
      long currentTime, String pathString,
      String userAgent, String from,
      String proxyHost, int proxyPort, String proxyAuthDomain, String proxyAuthUsername, String proxyAuthPassword,
      IProcessActivity activities, int connectionLimit)
      throws ServiceInterruption, ManifoldCFException
    {
      synchronized (this)
      {
        while (true)
        {
          if (readingRobots)
          {
            // Some other thread is already reading it, so wait until awakened
            try
            {
              wait();
            }
            catch (InterruptedException e)
            {
              throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
            }
            // Back around...
          }
          else if (isValid == false || currentTime >= invalidTime)
          {
            // Need to read!  If checking is going on, need to wait also
            if (checkingRobots > 0)
            {
              // Some other thread is in the midst of checking the previous version of the robots data.  Wait until awakened.
              try
              {
                wait();
              }
              catch (InterruptedException e)
              {
                throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
              }
              // Back around...
            }
            else
            {
              // Nobody is doing anything, so note that we are now reading robots, and invalidate the record if it isn't already
              isValid = false;
              records = null;
              readingRobots = true;
              break;
            }
          }
          else
          {
            // The current cached robots is valid; note that we are accessing it, and resume.
            checkingRobots++;
            break;
          }
        }
      }
      // Go into a try so we have some guarantee of cleaning up if there's an exception
      try
      {
        // Note well: This section of code, while not in a synchronizer, is in fact protected so that EITHER one thread is in here to read the
        // robots file, or multiple threads are but only to access the cached robots data.

        if (readingRobots)
          // This doesn't need to be synchronized because readingRobots blocks all other threads from getting at this object
          makeValid(threadContext,throttleGroupName,currentTime,userAgent,from,
          proxyHost, proxyPort, proxyAuthDomain, proxyAuthUsername, proxyAuthPassword,
          hostName, activities, connectionLimit);

        // If we get this far, the records should all be in good shape, so interpret them
        if (records == null)
          return true;

        boolean wasDisallowed = false;
        boolean wasAllowed = false;

        // First matching user-agent takes precedence, according to the following chunk of spec:
        // "These name tokens are used in User-agent lines in /robots.txt to
        // identify to which specific robots the record applies. The robot
        // must obey the first record in /robots.txt that contains a User-
        // Agent line whose value contains the name token of the robot as a
        // substring. The name comparisons are case-insensitive. If no such
        // record exists, it should obey the first record with a User-agent
        // line with a "*" value, if present. If no record satisfied either
        // condition, or no records are present at all, access is unlimited."

        boolean sawAgent = false;
        String userAgentUpper = userAgent.toUpperCase(Locale.ROOT);

        int i = 0;
        while (i < records.size())
        {
          Record r = (Record)records.get(i++);
          if (r.isAgentMatch(userAgentUpper,false))
          {
            if (r.isDisallowed(pathString))
              wasDisallowed = true;
            if (r.isAllowed(pathString))
              wasAllowed = true;
            sawAgent = true;
            break;
          }
        }

        if (sawAgent == false)
        {
          i = 0;
          while (i < records.size())
          {
            Record r = (Record)records.get(i++);
            if (r.isAgentMatch("*",true))
            {
              if (r.isDisallowed(pathString))
                wasDisallowed = true;
              if (r.isAllowed(pathString))
                wasAllowed = true;
              sawAgent = true;
              break;
            }
          }
        }

        if (sawAgent == false)
          return true;

        // Allowed always overrides disallowed
        if (wasAllowed)
          return true;
        if (wasDisallowed)
          return false;

        // No match -> crawl allowed
        return true;

      }
      finally
      {
        synchronized (this)
        {
          if (readingRobots)
            readingRobots = false;
          else
            checkingRobots--;
          // Wake up any sleeping threads
          notifyAll();
        }
      }

    }

    /** Check if the current record can be flushed.
    * This is not quite the same as whether the record is valid, since a not-yet-valid record still should not be flushed when there
    * is activity going on with that record!
    */
    public synchronized boolean canBeFlushed(long currentTime)
    {
      // Check if active FIRST.  readingRobots or checkingRobots will be properly set if there is activity going on with this Host.
      // In that case, the Host record must persist.
      if (readingRobots || checkingRobots > 0)
        return false;
      // Now, since the record is known not active, see if we can flush it.
      if (!isValid)
        return true;
      if (currentTime >= invalidTime)
      {
        isValid = false;
        records = null;
        return true;
      }
      return false;
    }

    /** Initialize the record.  This method reads the robots file on the specified protocol/host/port,
    * and parses it according to the rules.
    */
    protected void makeValid(IThreadContext threadContext, String throttleGroupName,
      long currentTime, String userAgent, String from,
      String proxyHost, int proxyPort, String proxyAuthDomain, String proxyAuthUsername, String proxyAuthPassword,
      String hostName, IProcessActivity activities, int connectionLimit)
      throws ServiceInterruption, ManifoldCFException
    {
      invalidTime = currentTime + 24L * 60L * 60L * 1000L;

      // Do the fetch
      IThrottledConnection connection = fetcher.createConnection(threadContext,throttleGroupName,
        hostName,connectionLimit,ROBOT_TIMEOUT_MILLISECONDS,
        proxyHost,proxyPort,proxyAuthDomain,proxyAuthUsername,proxyAuthPassword,
        activities);
      try
      {
        connection.beginFetch(ROBOT_CONNECTION_TYPE);
        try
        {
          int responseCode = connection.executeFetch(protocol,port,ROBOT_FILE_NAME,userAgent,from,
            null,null);
          switch (responseCode)
          {
          case IThrottledConnection.STATUS_OK:
            InputStream is = connection.getResponseBodyStream();
            try
            {
              Reader r = new InputStreamReader(is, StandardCharsets.UTF_8);
              try
              {
                BufferedReader br = new BufferedReader(r);
                try
                {
                  parseRobotsTxt(br,hostName,activities);
                }
                finally
                {
                  br.close();
                }
              }
              finally
              {
                r.close();
              }
            }
            finally
            {
              is.close();
            }
            break;

          case IThrottledConnection.STATUS_SITEERROR:
            // Permanent errors that mean, "fetch not allowed"
            Record r = new Record();
            r.addAgent("*");
            r.addDisallow("/");
            records = new ArrayList();
            records.add(r);
            break;

          case IThrottledConnection.STATUS_PAGEERROR:
          default:
            // Permanent errors that mean essentially "do what we want"
            break;
          }
        }
        finally
        {
          connection.doneFetch(activities);
        }
      }
      catch (InterruptedIOException e)
      {
        throw new ManifoldCFException("Interrupted: "+e.getMessage(),e,ManifoldCFException.INTERRUPTED);
      }
      catch (IOException e)
      {
        throw new ServiceInterruption("Couldn't fetch robots.txt from "+protocol+"://"+hostName+((port==-1)?"":":"+Integer.toString(port)),currentTime + 300000L);
      }
      finally
      {
        connection.close();
      }
      isValid = true;
    }

    /** Parse the robots.txt file using a reader.
    * Is NOT expected to close the stream.
    */
    protected void parseRobotsTxt(BufferedReader r, String hostName, IProcessActivity activities)
      throws IOException, ManifoldCFException
    {
      boolean parseCompleted = false;
      boolean robotsWasHtml = false;
      boolean foundErrors = false;
      String description = null;

      long startParseTime = System.currentTimeMillis();
      try
      {
        records = new ArrayList();
        Record record = null;
        boolean seenAction = false;
        while (true)
        {
          String x = r.readLine();
          if (x == null)
            break;
          int numSignPos = x.indexOf("#");
          if (numSignPos != -1)
            x = x.substring(0,numSignPos);
          String lowercaseLine = x.toLowerCase(Locale.ROOT).trim();
          if (lowercaseLine.startsWith("user-agent:"))
          {
            if (seenAction)
            {
              records.add(record);
              record = null;
              seenAction = false;
            }
            if (record == null)
              record = new Record();

            String agentName = x.substring("User-agent:".length()).trim();
            record.addAgent(agentName);
          }
          else if (lowercaseLine.startsWith("user-agent"))
          {
            if (seenAction)
            {
              records.add(record);
              record = null;
              seenAction = false;
            }
            if (record == null)
              record = new Record();

            String agentName = x.substring("User-agent".length()).trim();
            record.addAgent(agentName);
          }
          else if (lowercaseLine.startsWith("disallow:"))
          {
            if (record == null)
            {
              description = "Disallow without User-agent";
              Logging.connectors.warn("Web: Bad robots.txt file format from '"+hostName+"': "+description);
              foundErrors = true;
            }
            else
            {
              String disallowPath = x.substring("Disallow:".length()).trim();
              // The spec says that a blank disallow means let everything through.
              if (disallowPath.length() > 0)
                record.addDisallow(disallowPath);
              seenAction = true;
            }
          }
          else if (lowercaseLine.startsWith("disallow"))
          {
            if (record == null)
            {
              description = "Disallow without User-agent";
              Logging.connectors.warn("Web: Bad robots.txt file format from '"+hostName+"': "+description);
              foundErrors = true;
            }
            else
            {
              String disallowPath = x.substring("Disallow".length()).trim();
              // The spec says that a blank disallow means let everything through.
              if (disallowPath.length() > 0)
                record.addDisallow(disallowPath);
              seenAction = true;
            }
          }
          else if (lowercaseLine.startsWith("allow:"))
          {
            if (record == null)
            {
              description = "Allow without User-agent";
              Logging.connectors.warn("Web: Bad robots.txt file format from '"+hostName+"': "+description);
              foundErrors = true;
            }
            else
            {
              String allowPath = x.substring("Allow:".length()).trim();
              // The spec says that a blank disallow means let everything through.
              if (allowPath.length() > 0)
                record.addAllow(allowPath);
              seenAction = true;
            }
          }
          else if (lowercaseLine.startsWith("allow"))
          {
            if (record == null)
            {
              description = "Allow without User-agent";
              Logging.connectors.warn("Web: Bad robots.txt file format from '"+hostName+"': "+description);
              foundErrors = true;
            }
            else
            {
              String allowPath = x.substring("Allow".length()).trim();
              // The spec says that a blank disallow means let everything through.
              if (allowPath.length() > 0)
                record.addAllow(allowPath);
              seenAction = true;
            }
          }
          else if (lowercaseLine.startsWith("crawl-delay:"))
          {
            // We don't complain about this, but right now we don't listen to it either.
          }
          else if (lowercaseLine.startsWith("crawl-delay"))
          {
            // We don't complain about this, but right now we don't listen to it either.
          }
          else if (lowercaseLine.startsWith("sitemap:"))
          {
            // We don't complain about this, but right now we don't listen to it either.
          }
          else if (lowercaseLine.startsWith("sitemap"))
          {
            // We don't complain about this, but right now we don't listen to it either.
          }
          else
          {
            // If it's not just a blank line, complain
            if (x.trim().length() > 0)
            {
              String problemLine = makeReadable(x);
              description = "Unknown robots.txt line: '"+problemLine+"'";
              Logging.connectors.warn("Web: Unknown robots.txt line from '"+hostName+"': '"+problemLine+"'");
              if (x.indexOf("<html") != -1 || x.indexOf("<HTML") != -1)
              {
                // Looks like some kind of an html file, probably as a result of a redirection, so just abort as if we have a page error
                robotsWasHtml = true;
                parseCompleted = true;
                break;
              }
              foundErrors = true;
            }
          }
        }
        if (record != null)
          records.add(record);
        parseCompleted = true;
      }
      finally
      {
        // Log the fact that we attempted to parse robots.txt, as well as what happened
        // These are the following situations we will report:
        // (1) INCOMPLETE - Parsing did not complete - if the stream was interrupted
        // (2) HTML - Robots was html - if the robots data seemed to be html
        // (3) ERRORS - Robots had errors - if the robots data was accepted but had errors in it
        // (4) SUCCESS - Robots parsed successfully - if the robots data was parsed without problem
        String status;
        if (parseCompleted)
        {
          if (robotsWasHtml)
          {
            status = "HTML";
            description = "Robots file contained HTML, skipped";
          }
          else
          {
            if (foundErrors)
            {
              status = "ERRORS";
              // description should already be set
            }
            else
            {
              status = "SUCCESS";
              description = null;
            }
          }
        }
        else
        {
          status = "INCOMPLETE";
          description = "Parsing was interrupted";
        }

        activities.recordActivity(new Long(startParseTime),RSSConnector.ACTIVITY_ROBOTSPARSE,
          null,hostName,status,description,null);
      }
    }
  }


  /** This class represents a record in a robots.txt file.  It contains one or
  * more user-agents, and one or more disallows.
  */
  protected static class Record
  {
    protected ArrayList userAgents = new ArrayList();
    protected ArrayList disallows = new ArrayList();
    protected ArrayList allows = new ArrayList();

    /** Constructor.
    */
    public Record()
    {
    }

    /** Add a user-agent.
    */
    public void addAgent(String agentName)
    {
      userAgents.add(agentName);
    }

    /** Add a disallow.
    */
    public void addDisallow(String disallowPath)
    {
      disallows.add(disallowPath);
    }

    /** Add an allow.
    */
    public void addAllow(String allowPath)
    {
      allows.add(allowPath);
    }

    /** See if user-agent matches.
    */
    public boolean isAgentMatch(String agentNameUpper, boolean exactMatch)
    {
      int i = 0;
      while (i < userAgents.size())
      {
        String agent = ((String)userAgents.get(i++)).toUpperCase(Locale.ROOT);
        if (exactMatch && agent.trim().equals(agentNameUpper))
          return true;
        if (!exactMatch && agentNameUpper.indexOf(agent) != -1)
          return true;
      }
      return false;
    }


    /** See if path is disallowed.  Only called if user-agent has already
    * matched.  (This checks if there's an explicit match with one of the
    * Disallows clauses.)
    */
    public boolean isDisallowed(String path)
    {
      int i = 0;
      while (i < disallows.size())
      {
        String disallow = (String)disallows.get(i++);
        if (doesPathMatch(path,disallow))
          return true;
      }
      return false;
    }

    /** See if path is allowed.  Only called if user-agent has already
    * matched.  (This checks if there's an explicit match with one of the
    * Allows clauses).
    */
    public boolean isAllowed(String path)
    {
      int i = 0;
      while (i < allows.size())
      {
        String allow = (String)allows.get(i++);
        if (doesPathMatch(path,allow))
          return true;
      }
      return false;
    }

  }

}
