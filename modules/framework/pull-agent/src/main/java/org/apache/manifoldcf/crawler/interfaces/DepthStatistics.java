/* $Id: DepthStatistics.java 988245 2010-08-23 18:39:35Z kwright $ */

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
import org.apache.manifoldcf.crawler.system.Logging;
import java.util.*;

/** An instance of this class keeps a running average of how long it takes for every connection to process a document.
* This information is used to limit queuing per connection to something reasonable given the characteristics of the connection.
*/
public class DepthStatistics
{
  public static final String _rcsid = "@(#)$Id: DepthStatistics.java 988245 2010-08-23 18:39:35Z kwright $";

  // These are the bins used by all the documents scanned in a set.  Each element is a String[].
  protected ArrayList scanSetBins = new ArrayList();

  /** Constructor */
  public DepthStatistics()
  {
  }

  /** Add a document's bins to the set */
  public synchronized void addBins(Double priority)
  {
    //System.out.println("Adding "+Integer.toString(binNames.length)+" bins to scan record");
    scanSetBins.add(priority);
  }

  /** Grab all the bins in a big array */
  public synchronized Double[] getBins()
  {
    Double[] rval = new Double[scanSetBins.size()];
    int i = 0;
    while (i < scanSetBins.size())
    {
      rval[i] = (Double)scanSetBins.get(i);
      //System.out.println(" Bin record "+Integer.toString(i)+" has "+Integer.toString(rval[i].length)+" bins");
      i++;
    }
    //System.out.println("Returning "+Integer.toString(rval.length)+" individual bin records");
    return rval;
  }

}
