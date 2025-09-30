/* $Id: DocumentumException.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.common.DCTM;

public class DocumentumException extends Exception
{
  public static final String _rcsid = "@(#)$Id: DocumentumException.java 988245 2010-08-23 18:39:35Z kwright $";

  // Classes of exception
  public static final int TYPE_SERVICEINTERRUPTION = 0;
  public static final int TYPE_BADCREDENTIALS = 1;
  public static final int TYPE_BADCONNECTIONPARAMS = 2;
  public static final int TYPE_NOTALLOWED = 3;
  public static final int TYPE_GENERAL = 4;
  public static final int TYPE_CORRUPTEDDOCUMENT = 5;

  protected int errType;

  public DocumentumException(String errString)
  {
    super(errString);
    errType = TYPE_GENERAL;
  }

  public DocumentumException(String errString, Throwable cause)
  {
    super(errString,cause);
    errType = TYPE_GENERAL;
  }

  public DocumentumException(Throwable cause)
  {
    super(cause);
    errType = TYPE_GENERAL;
  }

  public DocumentumException(String errString, int errType)
  {
    super(errString);
    this.errType = errType;
  }

  public DocumentumException(String errString, Throwable cause, int errType)
  {
    super(errString,cause);
    this.errType = errType;
  }

  public DocumentumException(Throwable cause, int errType)
  {
    super(cause);
    this.errType = errType;
  }

  public int getType()
  {
    return errType;
  }

}
