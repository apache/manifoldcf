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
package com.filenet.api.core;

import com.filenet.api.exception.*;
import com.filenet.api.property.*;

/** Stub interface to allow the connector to build fully.
*/
public class Factory
{
  public static class Connection
  {
    public static com.filenet.api.core.Connection getConnection(String uri)
      throws EngineRuntimeException
    {
      return null;
    }
  }
  
  public static class Domain
  {
    public static com.filenet.api.core.Domain fetchInstance(com.filenet.api.core.Connection conn, String domain, PropertyFilter filter)
      throws EngineRuntimeException
    {
      return null;
    }
  }
  
  public static class ObjectStore
  {
    public static com.filenet.api.core.ObjectStore fetchInstance(com.filenet.api.core.Domain domain, String name, PropertyFilter filter)
    {
      return null;
    }
  }
  
  public static class ClassDefinition
  {
    public static com.filenet.api.admin.ClassDefinition fetchInstance(com.filenet.api.core.ObjectStore os, String rootClass, PropertyFilter filter)
    {
      return null;
    }
  }
  
  public static class Document
  {
    public static com.filenet.api.core.Document fetchInstance(com.filenet.api.core.ObjectStore os, String docId, PropertyFilter filter)
    {
      return null;
    }
  }
  
  public static class User
  {
    public static com.filenet.api.security.User fetchInstance(com.filenet.api.core.Connection conn, String gname, PropertyFilter filter)
    {
      return null;
    }
  }

  public static class Group
  {
    public static com.filenet.api.security.Group fetchInstance(com.filenet.api.core.Connection conn, String gname, PropertyFilter filter)
    {
      return null;
    }
  }
  
  public static class ClassDescription
  {
    public static com.filenet.api.meta.ClassDescription fetchInstance(com.filenet.api.core.Scope scope, String className, PropertyFilter filter)
    {
      return null;
    }
  }
  
}
