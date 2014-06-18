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
package org.apache.manifoldcf.authorities.system;

import org.apache.manifoldcf.core.interfaces.*;
import org.apache.manifoldcf.authorities.interfaces.*;
import java.util.*;

/** This class describes a user mapping request.  The request has state: It can be in an incomplete state, or it can be in a complete state.
* The thread that cares whether the request is complete needs to be able to wait for that situation to occur, so the request has
* a method that does just that.
*/
public class MappingRequest
{
  public static final String _rcsid = "@(#)$Id$";

  // This is where the request data actually lives
  protected String userID;
  protected final IMappingConnection mappingConnection;
  protected final String identifyingString;

  // These are the possible results of the request
  protected boolean answerComplete = false;
  protected String outputUserID = null;
  protected Throwable answerException = null;

  /** Construct the request, and record the question.
  */
  public MappingRequest(IMappingConnection mappingConnection, String identifyingString)
  {
    this.mappingConnection = mappingConnection;
    this.identifyingString = identifyingString;
  }

  /** Set the user ID we'll be using */
  public void setUserID(String userID)
  {
    this.userID = userID;
  }
  
  /** Get the user ID */
  public String getUserID()
  {
    return userID;
  }
  
  /** Get the mapping connection.
  */
  public IMappingConnection getMappingConnection()
  {
    return mappingConnection;
  }

  /** Get the identifying string, to pass back to the user if there was a problem */
  public String getIdentifyingString()
  {
    return identifyingString;
  }

  /** Wait for an auth request to be complete.
  */
  public void waitForComplete()
    throws InterruptedException
  {
    synchronized (this)
    {
      if (answerComplete)
        return;
      this.wait();
    }
  }

  /** Note that the request is complete, and record the answers.
  */
  public void completeRequest(String outputUserID, Throwable answerException)
  {
    synchronized (this)
    {
      if (answerComplete)
        return;

      // Record the answer.
      answerComplete = true;
      this.outputUserID = outputUserID;
      this.answerException = answerException;

      // Notify threads waiting on the answer.
      this.notifyAll();
    }
  }

  /** Get the answer user */
  public String getAnswerResponse()
  {
    return outputUserID;
  }

  /** Get the answer exception */
  public Throwable getAnswerException()
  {
    return answerException;
  }

}
