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
package org.apache.manifoldcf.crawler.connectors.amazons3;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.amazons3.S3Artifact;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.tika.metadata.Metadata;

import com.amazonaws.services.s3.model.CanonicalGrantee;
import com.amazonaws.services.s3.model.Grant;
import com.amazonaws.services.s3.model.Grantee;
import com.amazonaws.services.s3.model.ObjectMetadata;

public class AmazonS3DocumentProcessUtility {

  public AmazonS3DocumentProcessUtility() {
    super();
  }

  /**
   * Get users has the the access the to artifact
   * @param grants available for artifact
   * @return
   */
  protected String[] getUsers(Set<Grant> grants) {
    Set<String> users = new HashSet<String>();// no duplicates
    for (Grant grant : grants) {
      if (grant != null && grant.getGrantee() != null) {
        Grantee grantee = grant.getGrantee();
  
        if (grantee instanceof CanonicalGrantee) {
          users.add(((CanonicalGrantee) grantee).getDisplayName());
        }
        else {
          users.add(grantee.getIdentifier());
        }
      }
    }
  
    return users.toArray(new String[users.size()]);
  }

  /**
   * Constructs document URI for s3artifact
   * @param s3Artifact
   * @return
   */
  protected String getDocumentURI(S3Artifact s3Artifact) {
    return String.format(Locale.ROOT, AmazonS3Config.DOCUMENT_URI_FORMAT,
        s3Artifact.getBucketName(), s3Artifact.getKey());
  }

  /**
   * Adds available meta data to repository documetn
   * @param rd repository document
   * @param metadata2
   * @throws ManifoldCFException
   */
  protected void addAllMetaData(RepositoryDocument rd, Metadata metadata2)
      throws ManifoldCFException {
        for (String field : metadata2.names()) {
          rd.addField(field, metadata2.get(field));
        }
      }

  /**
   * Get the s3artifact (document) using the document identifier ( bucket,key)
   * @param documentIdentifier
   * @return
   * @throws ManifoldCFException
   */
  protected S3Artifact getS3Artifact(String documentIdentifier) throws ManifoldCFException {
    String key;
    String bucketName = documentIdentifier
        .split(AmazonS3Config.STD_SEPARATOR_BUCKET_AND_KEY)[0];
    key = documentIdentifier.split(AmazonS3Config.STD_SEPARATOR_BUCKET_AND_KEY)[1];
    if (StringUtils.isEmpty(bucketName) || StringUtils.isEmpty(key))
      throw new ManifoldCFException("bucket or key name is empty");
  
    return new S3Artifact(bucketName, key);
  }

  protected void addRawMetadata(RepositoryDocument rd, ObjectMetadata objectMetadata) {
    Map<String, Object> rawMetadata = objectMetadata.getRawMetadata();
  
    for (Entry<String, Object> entry : rawMetadata.entrySet()) {
      try {
        String value = entry.getValue().toString();
        String key = entry.getKey();
        rd.addField(key, value);
      }
      catch (ManifoldCFException e) {
        Logging.connectors.error("Error while adding metadata",e);
      }
    }
  
  }

}