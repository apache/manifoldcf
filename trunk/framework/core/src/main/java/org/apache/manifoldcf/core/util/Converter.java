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
    if (o instanceof Double)
      return ((Double)o).doubleValue();
    if (o instanceof String)
      return new Double((String)o).doubleValue();
    if (o instanceof Float)
      return (double)((Float)o).floatValue();
    if (o instanceof Long)
      return (double)((Long)o).longValue();
    throw new ManifoldCFException("Can't convert to double");
  }

  /** Convert a JDBC output value to a long.
  */
  public static long asLong(Object o)
    throws ManifoldCFException
  {
    if (o instanceof Long)
      return ((Long)o).longValue();
    if (o instanceof Double)
      return (long)((Double)o).doubleValue();
    if (o instanceof String)
      return new Long((String)o).longValue();
    if (o instanceof Float)
      return (long)((Float)o).floatValue();
    throw new ManifoldCFException("Can't convert to long");
  }

}
