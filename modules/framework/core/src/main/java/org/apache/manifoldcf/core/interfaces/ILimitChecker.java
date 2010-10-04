/* $Id: ILimitChecker.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.core.interfaces;

/** This interface describes a canonical limit checker for a query.  It will be used to limit the
* size of returned resultset by filtering entries at the time they are read from the jdbc driver.
*
* Since the logic of the implementing class is probably complex, it is not reasonable to expect
* classes that implement this interface to be uniquely describable to the point that hashCode()
* and equals() will work.  However, support is provided via this interface in the chance that
* such methods can be written.  (The definition of "sameness" is that two instances of this interface
* would always return the same answers given the same inputs.)
*/
public interface ILimitChecker
{
  public static final String _rcsid = "@(#)$Id: ILimitChecker.java 988245 2010-08-23 18:39:35Z kwright $";


  /** See if this class can be legitimately compared against another of
  * the same type.
  *@return true if comparisons will ever return "true".
  */
  public boolean doesCompareWork();

  /** Create a duplicate of this class instance.  All current state should be preserved.
  *@return the duplicate.
  */
  public ILimitChecker duplicate();

  /** Find the hashcode for this class.  This will only ever be used if
  * doesCompareWork() returns true.
  *@return the hashcode.
  */
  public int hashCode();

  /** Compare two objects and see if equal.  This will only ever be used
  * if doesCompareWork() returns true.
  *@param object is the object to compare against.
  *@return true if equal.
  */
  public boolean equals(Object object);

  /** See if a result row should be included in the final result set.
  *@param row is the result row to check.
  *@return true if it should be included, false otherwise.
  */
  public boolean checkInclude(IResultRow row)
    throws ManifoldCFException;

  /** See if we should examine another row.
  *@return true if we need to keep going, or false if we are done.
  */
  public boolean checkContinue()
    throws ManifoldCFException;
}
