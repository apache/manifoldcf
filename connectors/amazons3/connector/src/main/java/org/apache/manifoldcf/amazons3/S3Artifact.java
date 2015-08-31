/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
* http://www.apache.org/licenses/LICENSE-2.0
 * 
* Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.manifoldcf.amazons3;

/**
 * 
 * @author Kuhajeyan
 *
 */
public class S3Artifact {

  private String bucketName;

  private String key;

  public S3Artifact() {

  }

  public S3Artifact(String bucketName, String key) {

    this.bucketName = bucketName;
    this.key = key;
  }

  public String getBucketName() {
    return bucketName;
  }

  public void setBucketName(String bucketName) {
    this.bucketName = bucketName;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }
  
  @Override
  public boolean equals(Object obj) {
    if(obj == null)
      return false;
    
    if(!(obj instanceof S3Artifact))
      return false;
    
    if(obj == this){
      return true;
    }
    
    S3Artifact newObj = (S3Artifact)obj;
    if(newObj.getBucketName() == this.getBucketName() && newObj.getKey() == this.getKey())
      return true;
    
    return false;
  }
  
  @Override
  public int hashCode() {    
    return this.getBucketName().hashCode() + this.getKey().hashCode() + 345;
  }

}
