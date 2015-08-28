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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.interfaces.ServiceInterruption;
import org.apache.manifoldcf.connectors.common.amazons3.S3Artifact;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.crawler.interfaces.IExistingVersions;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.system.Logging;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.Grant;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;

/**
 * Generic amazons3 extractor
 * @author Kuhajeyan
 *
 */
public class GenericDocumentProcess extends AmazonS3DocumentProcessUtility
    implements DocumentProcess {

  /**
   * Process documents with out any tika extractor
   * @param documentIdentifiers
   * @param statuses
   * @param spec
   * @param activities
   * @param jobMode
   * @param usesDefaultAuthority
   * @param amazons3Client
   * @throws ManifoldCFException
   */
  @Override
  public void doPocessDocument(String[] documentIdentifiers,
      IExistingVersions statuses, Specification spec,
      IProcessActivity activities, int jobMode,
      boolean usesDefaultAuthority, AmazonS3 amazons3Client)
      throws ManifoldCFException {
    if (amazons3Client == null)
      throw new ManifoldCFException(
          "Amazon client can not connect at the moment");

    for (String documentIdentifier : documentIdentifiers) {
      try {
        if (documentIdentifier == null
            || StringUtils.isEmpty(documentIdentifier)) {
          Logging.connectors
              .warn("Document identifier is empty, document will not be processed");
          continue;
        }

        String versionString;
        String[] aclsToUse;

        if (documentIdentifier
            .split(AmazonS3Config.STD_SEPARATOR_BUCKET_AND_KEY) == null
            && documentIdentifier.length() < 1) {
          continue;
        }

        S3Artifact s3Artifact = getS3Artifact(documentIdentifier);
        S3Object s3Obj = amazons3Client.getObject(new GetObjectRequest(
            s3Artifact.getBucketName(), s3Artifact.getKey()));

        if (s3Obj == null) {
          // no such document in the bucket now
          // delete document
          activities.deleteDocument(documentIdentifier);
          continue;
        }

        Logging.connectors.info("Content-Type: "
            + s3Obj.getObjectMetadata().getContentType());
        ObjectMetadata objectMetadata = s3Obj.getObjectMetadata();

        Date lastModified = objectMetadata.getLastModified();
        StringBuilder sb = new StringBuilder();
        if (lastModified == null) {
          // remove the content
          activities.deleteDocument(documentIdentifier);
          continue;
        }

        aclsToUse = new String[0];

        AccessControlList objectAcl = amazons3Client.getObjectAcl(
            s3Artifact.getBucketName(), s3Artifact.getKey());

        Set<Grant> grants = objectAcl.getGrants();
        String[] users = getUsers(grants);

        aclsToUse = users;

        //
        sb.append(lastModified.toString());
        versionString = sb.toString();

        Logging.connectors.debug("version string : " + versionString);

        if (versionString.length() > 0
            && !activities.checkDocumentNeedsReindexing(
                documentIdentifier, versionString)) {
          Logging.connectors
              .info("Document need not to be reindexed : "
                  + documentIdentifier);
          continue;
        }

        Logging.connectors
            .debug("JIRA: Processing document identifier '"
                + documentIdentifier + "'");

        long startTime = System.currentTimeMillis();
        String errorCode = null;
        String errorDesc = null;
        Long fileSize = null;

        String mimeType = "text/plain";// default

        // tika works starts
        InputStream in = null;
        ByteArrayOutputStream bao = new ByteArrayOutputStream();

        String document = null;

        try {
          in = s3Obj.getObjectContent();
          IOUtils.copy(in, bao);
          long fileLength = bao.size();
          if(fileLength < 1)
          {
            Logging.connectors.warn("File length 0");
            continue;
          }

          String documentURI = getDocumentURI(s3Artifact);
          Logging.connectors.debug("document : " + documentURI);

          try {
            if (!activities.checkURLIndexable(documentURI)) {
              errorCode = activities.EXCLUDED_URL;
              errorDesc = "Excluded because of URL ('"
                  + documentURI + "')";
              activities.noDocument(documentIdentifier,
                  versionString);
              continue;
            }

            if (!activities.checkMimeTypeIndexable(mimeType)) {
              errorCode = activities.EXCLUDED_MIMETYPE;
              errorDesc = "Excluded because of mime type ('"
                  + mimeType + "')";
              activities.noDocument(documentIdentifier,
                  versionString);
              continue;
            }
            if (!activities.checkDateIndexable(lastModified)) {
              errorCode = activities.EXCLUDED_DATE;
              errorDesc = "Excluded because of date ("
                  + lastModified + ")";
              activities.noDocument(documentIdentifier,
                  versionString);
              continue;
            }

            // otherwise process
            RepositoryDocument rd = new RepositoryDocument();
            addRawMetadata(rd, objectMetadata);
            // Turn into acls and add into
            // description
            String[] denyAclsToUse;
            if (aclsToUse.length > 0)
              denyAclsToUse = new String[] { AmazonS3Config.defaultAuthorityDenyToken };
            else
              denyAclsToUse = new String[0];
            rd.setSecurity(
                RepositoryDocument.SECURITY_TYPE_DOCUMENT,
                aclsToUse, denyAclsToUse);

            rd.setMimeType(mimeType);

            if (lastModified != null)
              rd.setModifiedDate(lastModified);

            
              

            if (!activities.checkLengthIndexable(fileLength)) {
              errorCode = activities.EXCLUDED_LENGTH;
              errorDesc = "Excluded because of document length ("
                  + fileLength + ")";
              activities.noDocument(documentIdentifier,
                  versionString);
              continue;
            }

            InputStream is = null;
            try {
              is = new ByteArrayInputStream(bao.toByteArray());
              rd.setBinary(is, fileLength);
              activities.ingestDocumentWithException(
                  documentIdentifier, versionString,
                  documentURI, rd);

              errorCode = "OK";
              fileSize = new Long(fileLength);
            }
            finally {
              if (is != null)
                IOUtils.closeQuietly(is);
            }

          }
          catch (ServiceInterruption e) {
            Logging.connectors
                .error("Error while checking if document is indexable",
                    e);
          }
        }
        catch (IOException e1) {
          Logging.connectors.error("Error while copying stream", e1);
        }
        finally {
          //close output stream
          IOUtils.closeQuietly(bao);

          //close input stream
          if (in != null)
            IOUtils.closeQuietly(in);
        }

      }
      catch (AmazonServiceException e) {
        Logging.connectors.error(e);
      }
      catch (AmazonClientException e) {
        Logging.connectors.error(e);
      }
    }

  }

}
