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
package org.apache.lcf.crawler.connectors.filesystem;

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.agents.interfaces.*;
import org.apache.lcf.crawler.interfaces.*;
import org.apache.lcf.crawler.system.Logging;
import java.io.*;

/** This is the "repository connector" for a file system.  It's a relative if the share crawler, and should have
* comparable basic functionality, with the exception of the ability to use ActiveDirectory and look at other shares.
*/
public class FileConnector extends org.apache.lcf.crawler.connectors.BaseRepositoryConnector
{
	public static final String _rcsid = "@(#)$Id$";

	// Activities that we know about
	protected final static String ACTIVITY_READ = "read document";

	// Relationships we know about
	protected static final String RELATIONSHIP_CHILD = "child";

	// Activities list
	protected static final String[] activitiesList = new String[]{ACTIVITY_READ};

	// Parameters that this connector cares about
	// public final static String ROOTDIRECTORY = "rootdirectory";

	// Local data
	// protected File rootDirectory = null;

	/** Constructor.
	*/
	public FileConnector()
	{
	}


	/** Return the path for the UI interface JSP elements.
	* These JSP's must be provided to allow the connector to be configured, and to
	* permit it to present document filtering specification information in the UI.
	* This method should return the name of the folder, under the <webapp>/connectors/
	* area, where the appropriate JSP's can be found.  The name should NOT have a slash in it.
	*@return the folder part
	*/
	public String getJSPFolder()
	{
		return "filesystem";
	}

	/** Return the list of relationship types that this connector recognizes.
	*@return the list.
	*/
	public String[] getRelationshipTypes()
	{
		return new String[]{RELATIONSHIP_CHILD};
	}

	/** List the activities we might report on.
	*/
	public String[] getActivitiesList()
	{
		return activitiesList;
	}

	/** For any given document, list the bins that it is a member of.
	* For the file system, this would be typically just a blank value, but since we use this connector for testing, I have
	* it returning TWO values for each document, so I can set up tests to see how the scheduler behaves under those conditions.
	*/
	public String[] getBinNames(String documentIdentifier)
	{
		// Note: This code is for testing, so we can see how documents behave when they are in various kinds of bin situations.
		// The testing model is that there are documents belonging to "SLOW", to "FAST", or both to "SLOW" and "FAST" bins.
		// The connector chooses which bins to assign a document to based on the identifier (which is the document's path), so
		// this is something that should NOT be duplicated by other connector implementers.
		if (documentIdentifier.indexOf("/BOTH/") != -1 || (documentIdentifier.indexOf("/SLOW/") != -1 && documentIdentifier.indexOf("/FAST/") != -1))
			return new String[]{"SLOW","FAST"};
		if (documentIdentifier.indexOf("/SLOW/") != -1)
			return new String[]{"SLOW"};
		if (documentIdentifier.indexOf("/FAST/") != -1)
			return new String[]{"FAST"};
		return new String[]{""};
	}
	
	/** Convert a document identifier to a URI.  The URI is the URI that will be the unique key from
	* the search index, and will be presented to the user as part of the search results.
	*@param documentIdentifier is the document identifier.
	*@return the document uri.
	*/
	protected String convertToURI(String documentIdentifier)
		throws MetacartaException
	{
		//
		// Note well:  This MUST be a legal URI!!!
		try
		{
			return new File(documentIdentifier).toURI().toURL().toString();
		}
		catch (java.io.IOException e)
		{
			throw new MetacartaException("Bad url",e);
		}
	}


	/** Given a document specification, get either a list of starting document identifiers (seeds),
	* or a list of changes (deltas), depending on whether this is a "crawled" connector or not.
	* These document identifiers will be loaded into the job's queue at the beginning of the
	* job's execution.
	* This method can return changes only (because it is provided a time range).  For full
	* recrawls, the start time is always zero.
	* Note that it is always ok to return MORE documents rather than less with this method.
	*@param spec is a document specification (that comes from the job).
	*@param startTime is the beginning of the time range to consider, inclusive.
	*@param endTime is the end of the time range to consider, exclusive.
	*@return the stream of local document identifiers that should be added to the queue.
	*/
	public IDocumentIdentifierStream getDocumentIdentifiers(DocumentSpecification spec, long startTime, long endTime)
		throws MetacartaException
	{
		return new IdentifierStream(spec);
	}


	/** Get document versions given an array of document identifiers.
	* This method is called for EVERY document that is considered. It is
	* therefore important to perform as little work as possible here.
	*@param documentIdentifiers is the array of local document identifiers, as understood by this connector.
	*@return the corresponding version strings, with null in the places where the document no longer exists.
	* Empty version strings indicate that there is no versioning ability for the corresponding document, and the document
	* will always be processed.
	*/
	public String[] getDocumentVersions(String[] documentIdentifiers, DocumentSpecification spec)
		throws MetacartaException, ServiceInterruption
	{
		String[] rval = new String[documentIdentifiers.length];
		int i = 0;
		while (i < rval.length)
		{
			File file = new File(documentIdentifiers[i]);
			if (file.exists())
			{
				if (file.isDirectory())
				{
					// It's a directory.  The version ID will be the
					// last modified date.
					long lastModified = file.lastModified();
					rval[i] = new Long(lastModified).toString();

					// Signal that we don't have any versioning.
					// rval[i] = "";
				}
				else
				{
					// It's a file
					// Get the file's modified date.
					long lastModified = file.lastModified();
					long fileLength = file.length();
					StringBuffer sb = new StringBuffer();
					sb.append(new Long(lastModified).toString()).append(":").append(new Long(fileLength).toString());
					rval[i] = sb.toString();
				}
			}
			else
				rval[i] = null;
			i++;
		}
		return rval;
	}


	/** Process a set of documents.
	* This is the method that should cause each document to be fetched, processed, and the results either added
	* to the queue of documents for the current job, and/or entered into the incremental ingestion manager.
	* The document specification allows this class to filter what is done based on the job.
	*@param documentIdentifiers is the set of document identifiers to process.
	*@param activities is the interface this method should use to queue up new document references
	* and ingest documents.
	*@param spec is the document specification.
	*@param scanOnly is an array corresponding to the document identifiers.  It is set to true to indicate when the processing
	* should only find other references, and should not actually call the ingestion methods.
	*/
	public void processDocuments(String[] documentIdentifiers, String[] versions, IProcessActivity activities, DocumentSpecification spec, boolean[] scanOnly)
		throws MetacartaException, ServiceInterruption
	{
		int i = 0;
		while (i < documentIdentifiers.length)
		{
			File file = new File(documentIdentifiers[i]);
			if (file.exists())
			{
				if (file.isDirectory())
				{
					// Queue up stuff for directory
					long startTime = System.currentTimeMillis();
					String errorCode = "OK";
					String errorDesc = null;
					String documentIdentifier = documentIdentifiers[i];
					String entityReference = documentIdentifier;
					try
					{
						try
						{
							File[] files = file.listFiles();
							if (files != null)
							{
								int j = 0;
								while (j < files.length)
								{
									File f = files[j++];
									String canonicalPath = f.getCanonicalPath();
									if (checkInclude(f,canonicalPath,spec))
										activities.addDocumentReference(canonicalPath,documentIdentifier,RELATIONSHIP_CHILD);
								}
							}
						}
						catch (IOException e)
						{
							errorCode = "IO ERROR";
							errorDesc = e.getMessage();
							throw new MetacartaException("IO Error: "+e.getMessage(),e);
						}
					}
					finally
					{
						activities.recordActivity(new Long(startTime),ACTIVITY_READ,null,entityReference,errorCode,errorDesc,null);
					}
				}
				else
				{
					if (!scanOnly[i])
					{
						// We've already avoided queuing documents that we don't want, based on file specifications.
						// We still need to check based on file data.
						if (checkIngest(file,spec))
						{
							long startTime = System.currentTimeMillis();
							String errorCode = "OK";
							String errorDesc = null;
							Long fileLength = null;
							String documentIdentifier = documentIdentifiers[i];
							String version = versions[i];
							String entityDescription = documentIdentifier;
							try
							{
								// Ingest the document.
								try
								{
									InputStream is = new FileInputStream(file);
									try
									{
										long fileBytes = file.length();
										RepositoryDocument data = new RepositoryDocument();
										data.setBinary(is,fileBytes);
										data.addField("uri",file.toString());
										// MHL for other metadata
										activities.ingestDocument(documentIdentifier,version,convertToURI(documentIdentifier),data);
										fileLength = new Long(fileBytes);
									}
									finally
									{
										is.close();
									}
								}
								catch (IOException e)
								{
									errorCode = "IO ERROR";
									errorDesc = e.getMessage();
									throw new MetacartaException("IO Error: "+e.getMessage(),e);
								}
							}
							finally
							{
								activities.recordActivity(new Long(startTime),ACTIVITY_READ,fileLength,entityDescription,errorCode,errorDesc,null);
							}
						}
					}
				}
			}
			i++;
		}
	}



	// Protected static methods

	/** Check if a file or directory should be included, given a document specification.
	*@param fileName is the canonical file name.
	*@param documentSpecification is the specification.
	*@return true if it should be included.
	*/
	protected static boolean checkInclude(File file, String fileName, DocumentSpecification documentSpecification)
		throws MetacartaException
	{
	    if (Logging.connectors.isDebugEnabled())
	    {
		Logging.connectors.debug("Checking whether to include file '"+fileName+"'");
	    }

	    try
	    {
		String pathPart;
		String filePart;
		if (file.isDirectory())
		{
			pathPart = fileName;
			filePart = null;
		}
		else
		{
			pathPart = file.getParentFile().getCanonicalPath();
			filePart = file.getName();
		}

		// Scan until we match a startpoint
		int i = 0;
		while (i < documentSpecification.getChildCount())
		{
			SpecificationNode sn = documentSpecification.getChild(i++);
			if (sn.getType().equals("startpoint"))
			{
				String path = new File(sn.getAttributeValue("path")).getCanonicalPath();
				if (Logging.connectors.isDebugEnabled())
				{
					Logging.connectors.debug("Checking path '"+path+"' against canonical '"+pathPart+"'");
				}
				// Compare with filename
				int matchEnd = matchSubPath(path,pathPart);
				if (matchEnd == -1)
				{
					if (Logging.connectors.isDebugEnabled())
					{
						Logging.connectors.debug("Match check '"+path+"' against canonical '"+pathPart+"' failed");
					}

					continue;
				}
				// matchEnd is the start of the rest of the path (after the match) in fileName.
				// We need to walk through the rules and see whether it's in or out.
				int j = 0;
				while (j < sn.getChildCount())
				{
					SpecificationNode node = sn.getChild(j++);
					String flavor = node.getType();
					String match = node.getAttributeValue("match");
					String type = node.getAttributeValue("type");
					// If type is "file", then our match string is against the filePart.
					// If filePart is null, then this rule is simply skipped.
					String sourceMatch;
					int sourceIndex;
					if (type.equals("file"))
					{
						if (filePart == null)
							continue;
						sourceMatch = filePart;
						sourceIndex = 0;
					}
					else
					{
						if (filePart != null)
							continue;
						sourceMatch = pathPart;
						sourceIndex = matchEnd;
					}

					if (flavor.equals("include"))
					{
						if (checkMatch(sourceMatch,sourceIndex,match))
							return true;
					}
					else if (flavor.equals("exclude"))
					{
						if (checkMatch(sourceMatch,sourceIndex,match))
							return false;
					}
				}
			}
		}
		if (Logging.connectors.isDebugEnabled())
		{
			Logging.connectors.debug("Not including '"+fileName+"' because no matching rules");
		}

		return false;
	    }
	    catch (IOException e)
	    {
		throw new MetacartaException("IO Error",e);
	    }
	}

	/** Check if a file should be ingested, given a document specification.  It is presumed that
	* documents that do not pass checkInclude() will be checked with this method.
	*@param file is the file.
	*@param documentSpecification is the specification.
	*/
	protected static boolean checkIngest(File file, DocumentSpecification documentSpecification)
		throws MetacartaException
	{
		// Since the only exclusions at this point are not based on file contents, this is a no-op.
		// MHL
		return true;
	}

	/** Match a sub-path.  The sub-path must match the complete starting part of the full path, in a path
	* sense.  The returned value should point into the file name beyond the end of the matched path, or
	* be -1 if there is no match.
	*@param subPath is the sub path.
	*@param fullPath is the full path.
	*@return the index of the start of the remaining part of the full path, or -1.
	*/
	protected static int matchSubPath(String subPath, String fullPath)
	{
		if (subPath.length() > fullPath.length())
			return -1;
		if (fullPath.startsWith(subPath) == false)
			return -1;
		int rval = subPath.length();
		if (fullPath.length() == rval)
			return rval;
		char x = fullPath.charAt(rval);
		if (x == File.separatorChar)
			rval++;
		return rval;
	}

	/** Check a match between two strings with wildcards.
	*@param sourceMatch is the expanded string (no wildcards)
	*@param sourceIndex is the starting point in the expanded string.
	*@param match is the wildcard-based string.
	*@return true if there is a match.
	*/
	protected static boolean checkMatch(String sourceMatch, int sourceIndex, String match)
	{
		// Note: The java regex stuff looks pretty heavyweight for this purpose.
		// I've opted to try and do a simple recursive version myself, which is not compiled.
		// Basically, the match proceeds by recursive descent through the string, so that all *'s cause
		// recursion.
		boolean caseSensitive = true;

		return processCheck(caseSensitive, sourceMatch, sourceIndex, match, 0);
	}

	/** Recursive worker method for checkMatch.  Returns 'true' if there is a path that consumes both
	* strings in their entirety in a matched way.
	*@param caseSensitive is true if file names are case sensitive.
	*@param sourceMatch is the source string (w/o wildcards)
	*@param sourceIndex is the current point in the source string.
	*@param match is the match string (w/wildcards)
	*@param matchIndex is the current point in the match string.
	*@return true if there is a match.
	*/
	protected static boolean processCheck(boolean caseSensitive, String sourceMatch, int sourceIndex,
		String match, int matchIndex)
	{
		// Logging.connectors.debug("Matching '"+sourceMatch+"' position "+Integer.toString(sourceIndex)+
		// 	" against '"+match+"' position "+Integer.toString(matchIndex));

		// Match up through the next * we encounter
		while (true)
		{
			// If we've reached the end, it's a match.
			if (sourceMatch.length() == sourceIndex && match.length() == matchIndex)
				return true;
			// If one has reached the end but the other hasn't, no match
			if (match.length() == matchIndex)
				return false;
			if (sourceMatch.length() == sourceIndex)
			{
				if (match.charAt(matchIndex) != '*')
					return false;
				matchIndex++;
				continue;
			}
			char x = sourceMatch.charAt(sourceIndex);
			char y = match.charAt(matchIndex);
			if (!caseSensitive)
			{
				if (x >= 'A' && x <= 'Z')
					x -= 'A'-'a';
				if (y >= 'A' && y <= 'Z')
					y -= 'A'-'a';
			}
			if (y == '*')
			{
				// Wildcard!
				// We will recurse at this point.
				// Basically, we want to combine the results for leaving the "*" in the match string
				// at this point and advancing the source index, with skipping the "*" and leaving the source
				// string alone.
				return processCheck(caseSensitive,sourceMatch,sourceIndex+1,match,matchIndex) ||
					processCheck(caseSensitive,sourceMatch,sourceIndex,match,matchIndex+1);
			}
			if (y == '?' || x == y)
			{
				sourceIndex++;
				matchIndex++;
			}
			else
				return false;
		}
	}

	/** Document identifier stream.
	*/
	protected static class IdentifierStream implements IDocumentIdentifierStream
	{
		protected String[] ids = null;
		protected int currentIndex = 0;

		public IdentifierStream(DocumentSpecification spec)
			throws MetacartaException
		{
		    try
		    {
			// Walk the specification for the "startpoint" types.  Amalgamate these into a list of strings.
			// Presume that all roots are startpoint nodes
			int i = 0;
			int j = 0;
			while (i < spec.getChildCount())
			{
				SpecificationNode n = spec.getChild(i);
				if (n.getType().equals("startpoint"))
					j++;
				i++;
			}
			ids = new String[j];
			i = 0;
			j = 0;
			while (i < ids.length)
			{
				SpecificationNode n = spec.getChild(i);
				if (n.getType().equals("startpoint"))
				{
					// The id returned MUST be in canonical form!!!
					ids[j] = new File(n.getAttributeValue("path")).getCanonicalPath();
					if (Logging.connectors.isDebugEnabled())
					{
						Logging.connectors.debug("Seed = '"+ids[j]+"'");
					}
					j++;
				}
				i++;
			}
		    }
		    catch (IOException e)
		    {
			throw new MetacartaException("Could not get a canonical path",e);
		    }
		}

		/** Get the next identifier.
		*@return the next document identifier, or null if there are no more.
		*/
		public String getNextIdentifier()
			throws MetacartaException, ServiceInterruption
		{
			if (currentIndex == ids.length)
				return null;
			return ids[currentIndex++];
		}

		/** Close the stream.
		*/
		public void close()
			throws MetacartaException
		{
			ids = null;
		}

	}

}
