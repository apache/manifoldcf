/* $Id: ManifoldCFException.java 988245 2010-08-23 18:39:35Z kwright $ */

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

public class ManifoldCFException extends Exception
{
  public static final String _rcsid = "@(#)$Id: ManifoldCFException.java 988245 2010-08-23 18:39:35Z kwright $";

  public final static int GENERAL_ERROR = 0;
  public final static int DATABASE_ERROR = 1;
  public final static int INTERRUPTED = 2;
  public final static int SETUP_ERROR = 3;
  public final static int DATABASE_CONNECTION_ERROR = 4;
  public final static int REPOSITORY_CONNECTION_ERROR = 5;
  public final static int DATABASE_TRANSACTION_ABORT = 6;
  // MHL

  protected int errcode;

  public ManifoldCFException(String errString)
  {
    super(errString);
    this.errcode = GENERAL_ERROR;
  }

  public ManifoldCFException(String errString, int errcode)
  {
    super(errString);
    this.errcode = errcode;
  }

  public ManifoldCFException(String errString, Throwable cause, int errcode)
  {
    super(errString,cause);
    this.errcode = errcode;
  }

  public ManifoldCFException(String errString, Throwable cause)
  {
    super(errString,cause);
    this.errcode = GENERAL_ERROR;
  }

  public ManifoldCFException(Throwable cause, int errcode)
  {
    super(cause);
    this.errcode = errcode;
  }

  public ManifoldCFException(Throwable cause)
  {
    super(cause);
    this.errcode = GENERAL_ERROR;
  }

  public int getErrorCode()
  {
    return errcode;
  }


}
