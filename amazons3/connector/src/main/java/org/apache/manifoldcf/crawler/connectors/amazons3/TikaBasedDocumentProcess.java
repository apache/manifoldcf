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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.connectors.common.amazons3.S3Artifact;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.crawler.interfaces.IExistingVersions;
import org.apache.manifoldcf.crawler.interfaces.IProcessActivity;
import org.apache.manifoldcf.crawler.system.Logging;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.Grant;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;

/**
 * Tika based document process
 * @author Kuhajeyan
 *
 */
public class TikaBasedDocumentProcess extends AmazonS3DocumentProcessUtility implements DocumentProcess {

	
	
	AutoDetectParser parser;

	BodyContentHandler handler;

	Metadata metadata;

	Tika tika;

	ParseContext context;
	public TikaBasedDocumentProcess() {
		parser = new AutoDetectParser();
		handler = new BodyContentHandler(AmazonS3Config.CHARACTER_LIMIT);
		metadata = new Metadata();
		tika = new Tika();
		context = new ParseContext();
	}
	
	
	
	/**
	 * Process documents based on Tika extractor
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
			boolean usesDefaultAuthority, AmazonS3 amazons3Client ) throws ManifoldCFException {
		
		if (amazons3Client == null)
			throw new ManifoldCFException(
					"Amazon client can not connect at the moment");
		String[] acls = null;

		// loop documents and process
		for (String documentIdentifier : documentIdentifiers) {
			try {
				if (documentIdentifier != null
						&& StringUtils.isNotEmpty(documentIdentifier)) {
					String versionString;
					String[] aclsToUse;

					if (documentIdentifier.split(AmazonS3Config.STD_SEPARATOR_BUCKET_AND_KEY) == null
							&& documentIdentifier.length() < 1) {
						continue;
					}

					S3Artifact s3Artifact = getS3Artifact(documentIdentifier);
					S3Object s3Obj = amazons3Client
							.getObject(new GetObjectRequest(s3Artifact
									.getBucketName(), s3Artifact.getKey()));

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

					Logging.connectors.debug("version string : "
							+ versionString);

					if (versionString.length() > 0
							&& !activities.checkDocumentNeedsReindexing(
									documentIdentifier, versionString)) {
						Logging.connectors
								.info("Document need not to be reindexed : "
										+ documentIdentifier);
						continue;
					}
					
					//====

					Logging.connectors
							.debug("JIRA: Processing document identifier '"
									+ documentIdentifier + "'");

					long startTime = System.currentTimeMillis();
					String errorCode = null;
					String errorDesc = null;
					Long fileSize = null;

					try {
						String mimeType = "text/plain";// default

						// tika works starts
						InputStream in = null;
						

						String document = null;
						try {
							in = s3Obj.getObjectContent();

							parser.parse(in, handler, metadata, context);
							mimeType = tika.detect(in);
							document = handler.toString();
							if (document == null)
								continue;
							metadata.set(Metadata.CONTENT_TYPE, mimeType);
						}
						catch (Exception e) {
							Logging.connectors.error(
									"Error while parsing tika contents", e);
						}
						finally {
							if (in != null)
								IOUtils.closeQuietly(in);
						}

						String documentURI = getDocumentURI(s3Artifact);

						Logging.connectors.debug("document : " + documentURI);

						// need some investigation
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

						// set all meta-data fields
						addAllMetaData(rd, metadata);

						// get document

						try {
							byte[] documentBytes = document
									.getBytes(StandardCharsets.UTF_8);
							long fileLength = documentBytes.length;

							if (!activities.checkLengthIndexable(fileLength)) {
								errorCode = activities.EXCLUDED_LENGTH;
								errorDesc = "Excluded because of document length ("
										+ fileLength + ")";
								activities.noDocument(documentIdentifier,
										versionString);
								continue;
							}

							InputStream is = new ByteArrayInputStream(
									documentBytes);
							try {
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
						catch (Exception e) {
							Logging.connectors.error(e);
						}
					}
					catch (Exception e) {
						Logging.connectors.error(e);
					}

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
