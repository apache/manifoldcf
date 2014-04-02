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
package org.apache.manifoldcf.agents.output.solr;

import org.junit.Ignore;
import org.junit.Test;

import junit.framework.TestCase;
import org.apache.log4j.Logger;
import org.apache.manifoldcf.agents.interfaces.IOutputAddActivity;
import org.apache.manifoldcf.agents.interfaces.RepositoryDocument;
import org.apache.manifoldcf.agents.system.Logging;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.*;

/**
 * @Author: Alessandro Benedetti Date: 19/12/2013
 */

@RunWith( PowerMockRunner.class )
@PrepareForTest( { HttpPoster.class } )
public class HttpPosterTest
  extends TestCase
{


  HttpPoster httpPosterToTest;

  RepositoryDocument document;

  IOutputAddActivity act;

  @Override
  public void setUp()
    throws Exception
  {

    httpPosterToTest = spy(new HttpPoster( "localhost", "collection1", 5000, 500, "update", "removePath", "statusPath",
                 "allowAttributeName", "denyAttributeName", "idAttributeName",
                 "modifiedDateAttributeName", "createdDateAttributeName", "indexedDateAttributeName",
                 "fileNameAttributeName", "mimeTypeAttributeName", new Long( 5000 ), "true"));
  }

  /**
   * Verify  the IndexPost Method create the correct IngestThread class
   * @throws Exception
   */
  public void testIndexPost()
    throws Exception
  {
    String[] shareAcls = new String[]{ "shareAcl1", "shareAcl2" };
    String[] shareDenyAcls = new String[]{ "denyShareAcl1", "denyShareAcl2" };
    String[] acls = new String[]{ "acl1", "acl2" };
    String[] denyAcls = new String[]{ "denyAcl1", "denyAcl2" };

    Map<String,String[]> aclsMap = new HashMap<String,String[]>();
    Map<String,String[]> denyAclsMap = new HashMap<String,String[]>();
    aclsMap.put("share",shareAcls);
    aclsMap.put("document",acls);
    denyAclsMap.put("share",shareDenyAcls);
    denyAclsMap.put("document",denyAcls);

    Logging.ingest = mock( Logger.class );
    when( Logging.ingest.isDebugEnabled() ).thenReturn( false );
    initRepositoryDocumentMock( shareAcls, shareDenyAcls, acls, denyAcls );

    act = mock( IOutputAddActivity.class );
    when(act.qualifyAccessToken( eq( "AuthorityString"), anyString() )).thenAnswer(new Answer<String>() {
      @Override
      public String answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        return (String) args[1];
      }
    });

    Map<String, List<String>> sourceTargets = this.getMappingsMap();
    Map<String, List<String>> streamParam = this.getArgumentsMap();

    String commitWithin = "true";
    HttpPoster.IngestThread mockIngestionThread=mock(HttpPoster.IngestThread.class);
    Mockito.doThrow(new RuntimeException()).when( mockIngestionThread ).run();

    whenNew( HttpPoster.IngestThread.class).withArguments("Document Id", document, streamParam, true, sourceTargets,
      aclsMap, denyAclsMap, commitWithin).thenReturn(mockIngestionThread);
    httpPosterToTest.indexPost("Document Id", document, streamParam,
      sourceTargets,true, "AuthorityString",act);

    verifyNew(HttpPoster.IngestThread.class).withArguments("Document Id", document, streamParam, true, sourceTargets,
      aclsMap, denyAclsMap, commitWithin );
  }

  private void initRepositoryDocumentMock( String[] shareAcls, String[] shareDenyAcls, String[] acls,
    String[] denyAcls )
    throws IOException
  {
    document = mock( RepositoryDocument.class );
    List<String> fields = getFields();
    List<String> securityTypes = getSecurityTypes();
    Iterator<String> fieldsIterator = fields.iterator();
    Iterator<String> securityTypesIterator = securityTypes.iterator();
    when( document.getFields() ).thenReturn( fieldsIterator );
    when( document.getFieldAsStrings( "cm:description" ) ).thenReturn( new String[]{ "description" } );
    when( document.getFieldAsStrings( "cm:name" ) ).thenReturn( new String[]{ "name" } );
    when( document.getFieldAsStrings( "cm:title" ) ).thenReturn( new String[]{ "title" } );
    when( document.getFieldAsStrings( "extraMetadata1" ) ).thenReturn( new String[]{ "value1" } );
    when( document.getFieldAsStrings( "extraMetadata2" ) ).thenReturn( new String[]{ "value2" } );
    when( document.getFieldAsStrings( "extraMetadata3" ) ).thenReturn( new String[]{ "value3" } );
    when( document.securityTypesIterator()).thenReturn(securityTypesIterator);
    when( document.getSecurityACL( "document" ) ).thenReturn(acls);
    when( document.getSecurityACL( "share" ) ).thenReturn(shareAcls);
    when( document.getSecurityDenyACL( "document" ) ).thenReturn(shareAcls);
    when( document.getSecurityDenyACL( "share" ) ).thenReturn(shareDenyAcls);
  }

  /**
   * inits the expected solr params for both the tests, in the first one we expect the extra params not to be present
   * because they are not in the mappings
   *
   * @param test2
   * @return
   * @throws java.io.UnsupportedEncodingException
   */
  private ModifiableSolrParams initExpectedSolrParams( Boolean test2 )
    throws UnsupportedEncodingException
  {
    ModifiableSolrParams expectedParams = new ModifiableSolrParams();
    expectedParams.add( "literal.idAttributeName", "document id" );
    expectedParams.add( "literal.allowAttributeNameshare", "shareAcl1" );
    expectedParams.add( "literal.allowAttributeNameshare", "shareAcl2" );
    expectedParams.add( "literal.denyAttributeNameshare", "denyShareAcl1" );
    expectedParams.add( "literal.denyAttributeNameshare", "denyShareAcl2" );
    expectedParams.add( "literal.allowAttributeNamedocument", "acl1" );
    expectedParams.add( "literal.allowAttributeNamedocument", "acl2" );
    expectedParams.add( "literal.denyAttributeNamedocument", "denyAcl1" );
    expectedParams.add( "literal.denyAttributeNamedocument", "denyAcl2" );
    expectedParams.add( "stream.type", "text/plain" );
    expectedParams.add( "literal.cm_description_s", "description" );
    expectedParams.add( "literal.cm_title_s", "title" );
    expectedParams.add( "literal.cm_name_s", "name" );
    if ( test2 )
    {
      expectedParams.add( "literal.extraMetadata1", "value1" );
      expectedParams.add( "literal.extraMetadata2", "value2" );
      expectedParams.add( "literal.extraMetadata3", "value3" );
    }
    expectedParams.add( "commitWithin", "true" );
    return expectedParams;
  }

  /**
   * return a list of example metadata fields present in a mock document
   *
   * @return
   */
  private List<String> getFields()
  {
    List<String> fields = new ArrayList<String>();
    fields.add( "cm:description" );
    fields.add( "cm:title" );
    fields.add( "cm:name" );
    fields.add( "extraMetadata1" );
    fields.add( "extraMetadata2" );
    fields.add( "extraMetadata3" );
    return fields;
  }

  private List<String> getSecurityTypes()
  {
    List<String> securityTypes = new ArrayList<String>();
    securityTypes.add( "document" );
    securityTypes.add( "share" );
    return securityTypes;
  }
  
  /**
   * returns a testing mapping map
   *
   * @return
   */
  private Map<String, List<String>> getMappingsMap()
  {
    Map<String, List<String>> sourceTargets = new HashMap<String, List<String>>();
    List<String> firstList = new ArrayList<String>();
    firstList.add( "cm_description_s" );
    List<String> secondList = new ArrayList<String>();
    secondList.add( "cm_name_s" );
    List<String> thirdList = new ArrayList<String>();
    thirdList.add( "cm_title_s" );

    sourceTargets.put( "cm:description", firstList );
    sourceTargets.put( "cm:name", secondList );
    sourceTargets.put( "cm:title", thirdList );
    return sourceTargets;
  }

  /**
   * returns a testing argument map
   *
   * @return
   */
  private Map<String, List<String>> getArgumentsMap()
  {
    Map<String, List<String>> sourceTargets = new HashMap<String, List<String>>();
    List<String> firstList = new ArrayList<String>();
    firstList.add( "text/plain" );
    sourceTargets.put( "stream.type", firstList );
    return sourceTargets;
  }
}
