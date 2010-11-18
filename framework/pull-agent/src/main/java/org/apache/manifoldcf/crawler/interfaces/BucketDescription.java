/* $Id: BucketDescription.java 988245 2010-08-23 18:39:35Z kwright $ */

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

/** Class which describes a specification of how to map a string to another string.
* This facility is by necessity based on PostgreSQL's implementation of Posix regular expressions,
* and the substring() operator they provide to extract data from a matched expression of these kinds.
* See http://www.postgresql.org/docs/7.4/static/functions-matching.html for details.
*/
public class BucketDescription
{
  public static final String _rcsid = "@(#)$Id: BucketDescription.java 988245 2010-08-23 18:39:35Z kwright $";

  /** This is the regexp to match.  This will be Posix. */
  protected String regexp;
  /** Set to true if the match should be case sensitive, or false if insensitive. */
  protected boolean isSensitive;

  /** Constructor.
  */
  public BucketDescription(String regexp, boolean isSensitive)
  {
    this.regexp = regexp;
    this.isSensitive = isSensitive;
  }

  /** Get the regexp value.
  */
  public String getRegexp()
  {
    return regexp;
  }

  /** Is this case sensitive?
  */
  public boolean isSensitive()
  {
    return isSensitive;
  }


}
