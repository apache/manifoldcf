/* $Id: DefaultAuthenticator.java 1688076 2015-06-28 23:04:30Z kwright $ */

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
package org.apache.manifoldcf.crawler.connectors.nuxeo.model;

/**
 * @author David Arroyo Escobar <arroyoescobardavid@gmail.com>
 *
 */
public class Ace {

  protected String name;
  protected boolean granted;
  protected String status;
  /**
   * @return the name
   */
  public String getName() {
    return name;
  }
  /**
   * @param name the name to set
   */
  public void setName(String name) {
    this.name = name;
  }
  /**
   * @return the granted
   */
  public boolean isGranted() {
    return granted;
  }
  /**
   * @param granted the granted to set
   */
  public void setGranted(boolean granted) {
    this.granted = granted;
  }
  /**
   * @return the status
   */
  public String getStatus() {
    return status;
  }
  /**
   * @param status the status to set
   */
  public void setStatus(String status) {
    this.status = status;
  }
  
  
}
