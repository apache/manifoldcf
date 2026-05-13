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
package org.apache.manifoldcf.core.util;

import org.apache.manifoldcf.core.interfaces.*;

/** Various useful converter methods for working with JDBC output
*/
public class Converter
{
  public static final String _rcsid = "@(#)$Id$";

  /** Convert a JDBC output value to a double.
  */
  public static double asDouble(Object o)
    throws ManifoldCFException
  {
    if (o instanceof Double d)
      return d;
    if (o instanceof String s)
      return Double.parseDouble(s);
    if (o instanceof Float f)
      return f.doubleValue();
    if (o instanceof Long l)
      return l.doubleValue();
    throw new ManifoldCFException("Can't convert to double");
  }

  /** Convert a JDBC output value to a long.
  */
  public static long asLong(Object o)
    throws ManifoldCFException
  {
    if (o instanceof Long l)
      return l;
    if (o instanceof Double d)
      return d.longValue();
    if (o instanceof String s)
      return Long.parseLong(s);
    if (o instanceof Float f)
      return f.longValue();
    throw new ManifoldCFException("Can't convert to long");
  }

}
