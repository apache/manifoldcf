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

/*
* To change this template, choose Tools | Templates
* and open the template in the editor.
*/

package org.apache.acf.crawler.connectors.memex;

import com.memex.mie.*;


/**
*
* @author mxadmin
*/
class ACFMemexConnection{

  /**Class to extend the regular MemexConnection to deal with
  * the fact the ACF connect method doesn't report authentication
  * failure immediately.
  */

  protected String ConnectionMessage = "No connection attempted";
  protected RegistryEntry[] localRegistry = null;
  protected MemexConnection mie = null;
  protected String name = "";
  protected long checkExpireTime = -1L;


  ACFMemexConnection(){

  }

  protected boolean isConnected(){

    try{
      if(mie.isConnected()){
        long currentTime = System.currentTimeMillis();
        if (checkExpireTime == -1L || currentTime >= checkExpireTime)
        {
          String mytest = mie.mxie_environment_get("MXIE_CURRENT_USER");
          // Don't try this again for at least a minute; it's unnecessary and way too slow
          checkExpireTime = currentTime + 60000L;
        }
        return true;
      }else{
        checkExpireTime = -1L;
        return false;
      }
    }
    catch(MemexException mex){
      checkExpireTime = -1L;
      return false;
    }
    catch(java.lang.NullPointerException e){
      checkExpireTime = -1L;
      return false;
    }
  }

}
