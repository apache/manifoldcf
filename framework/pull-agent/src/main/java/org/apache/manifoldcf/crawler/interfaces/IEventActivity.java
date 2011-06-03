/* $Id: IEventActivity.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** This interface abstracts from the activities that use and govern events.
*
* The purpose of this model is to allow a connector to:
* (a) insure that documents whose prerequisites have not been met do not get processed until those prerequisites are completed
* (b) guarantee that only one thread at a time deal with sequencing of documents
*
* The way it works is as follows.  We define the notion of an "event", which is described by a simple string (and thus can be global,
* local to a connection, or local to a job, whichever is appropriate).  An event is managed solely by the connector that knows about it.
* Effectively it can be in either of two states: "completed", or "pending".  The only time the framework ever changes an event state is when
* the crawler is restarted, at which point all pending events are marked "completed".
*
* Documents, when they are added to the processing queue, specify the set of events on which they will block.  If an event is in the "pending" state,
* no documents that block on that event will be processed at that time.  Of course, it is possible that a document could be handed to processing just before
* an event entered the "pending" state - in which case it is the responsibility of the connector itself to avoid any problems or conflicts.  This can
* usually be handled by proper handling of event signalling.  More on that later.
*
* The presumed underlying model of flow inside the connector's processing method is as follows:
* (1) The connector examines the document in question, and decides whether it can be processed successfully or not, based on what it knows about sequencing
* (2) If the connector determines that the document can properly be processed, it does so, and that's it.
* (3) If the connector finds a sequencing-related problem, it:
*     (a) Begins an appropriate event sequence.
*     (b) If the framework indicates that this event is already in the "pending" state, then some other thread is already handling the event, and the connector
*          should abort processing of the current document.
*     (c) If the framework successfully begins the event sequence, then the connector code knows unequivocably that it is the only thread processing the event.
*         It should take whatever action it needs to - which might be requesting special documents, for instance.  [Note well: At this time, there is no way
*         to guarantee that special documents added to the queue are in fact properly synchronized by this mechanism, so I recommend avoiding this practice,
*         and instead handling any special document sequences without involving the queue.]
*     (d) If the connector CANNOT successfully take the action it needs to to push the sequence along, it MUST set the event back to the "completed" state.
*         Otherwise, the event will remain in the "pending" state until the next time the crawler is restarted.
*     (e) If the current document cannot yet be processed, its processing should be aborted.
* (4) When the connector determines that the event's conditions have been met, or when it determines that an event sequence is no longer viable and has been
*     aborted, it must set the event status to "completed".
*
* In summary, a connector may perform the following event-related actions:
* (a) Set an event into the "pending" state
* (b) Set an event into the "completed" state
* (c) Add a document to the queue with a specified set of prerequisite events attached
* (d) Request that the current document be requeued for later processing (i.e. abort processing of a document due to sequencing reasons)
*
*/
public interface IEventActivity extends INamingActivity
{
  public static final String _rcsid = "@(#)$Id: IEventActivity.java 988245 2010-08-23 18:39:35Z kwright $";

  /** Begin an event sequence.
  * This method should be called by a connector when a sequencing event should enter the "pending" state.  If the event is already in that state,
  * this method will return false, otherwise true.  The connector has the responsibility of appropriately managing sequencing given the response
  * status.
  *@param eventName is the event name.
  *@return false if the event is already in the "pending" state.
  */
  public boolean beginEventSequence(String eventName)
    throws ManifoldCFException;

  /** Complete an event sequence.
  * This method should be called to signal that an event is no longer in the "pending" state.  This can mean that the prerequisite processing is
  * completed, but it can also mean that prerequisite processing was aborted or cannot be completed.
  * Note well: This method should not be called unless the connector is CERTAIN that an event is in progress, and that the current thread has
  * the sole right to complete it.  Otherwise, race conditions can develop which would be difficult to diagnose.
  *@param eventName is the event name.
  */
  public void completeEventSequence(String eventName)
    throws ManifoldCFException;

  /** Abort processing a document (for sequencing reasons).
  * This method should be called in order to cause the specified document to be requeued for later processing.  While this is similar in some respects
  * to the semantics of a ServiceInterruption, it is applicable to only one document at a time, and also does not specify any delay period, since it is
  * presumed that the reason for the requeue is because of sequencing issues synchronized around an underlying event.
  *@param localIdentifier is the document identifier to requeue
  */
  public void retryDocumentProcessing(String localIdentifier)
    throws ManifoldCFException;


}
