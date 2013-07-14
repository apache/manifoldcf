/* $Id: IHTTPOutput.java 988245 2010-08-23 18:39:35Z kwright $ */

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

import java.io.*;

/** This interface abstracts from the output character stream used to construct
* HTML output for a web interface.  More broadly, it provides the services that all
* connectors will need in order to provide UI components.
*/
public interface IHTTPOutput extends IHTTPOutputActivity, IPasswordMapperActivity
{
  public static final String _rcsid = "@(#)$Id: IHTTPOutput.java 988245 2010-08-23 18:39:35Z kwright $";

}
