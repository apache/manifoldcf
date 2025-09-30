/* $Id: FilenetImpl.java 988245 2010-08-23 18:39:35Z kwright $ */

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
package org.apache.manifoldcf.crawler.common.filenet;

import java.rmi.*;
import java.rmi.server.UnicastRemoteObject;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.*;
import java.io.*;


import com.filenet.api.core.*;
import com.filenet.api.util.UserContext;
import com.filenet.api.collection.*;
import com.filenet.api.meta.*;
import com.filenet.api.admin.*;
import com.filenet.api.query.*;
import com.filenet.api.util.*;
import com.filenet.api.exception.*;
import com.filenet.api.security.*;
import com.filenet.api.constants.*;
import com.filenet.api.property.*;


/** This class abstracts away from the filenet methods necessary to "do things" that
* the crawler or authority needs to be done with the Filenet repository.
*/
public class FilenetImpl extends UnicastRemoteObject implements IFilenet
{
  public static final String _rcsid = "@(#)$Id: FilenetImpl.java 988245 2010-08-23 18:39:35Z kwright $";

  //Web service login module name
  private static final String PARAM_LOGIN_MODULE = "FileNetP8WSI";
  //Top level document class name
  private static final String PARAM_ROOT_DOC_CLASSNAME = "Document";

  private String userID = null;
  private String password = null;
  private String serverWsiURI=null;
  private String fnDomainString=null;
  private String objectStoreName=null;

  private Connection conn = null;
  private com.filenet.api.core.ObjectStore os = null;
  private Domain fnDomain = null;
  private UserContext uc =null;

  /** Instantiate */
  public FilenetImpl()
    throws RemoteException
  {
    super(0,new RMILocalClientSocketFactory(),new RMILocalSocketFactory());
  }

  /** Create a session.
  *@param userID is the userID to use to establish the session.
  *@param password is the password to use to establish the session.
  *@param objectStore is the object store to use to establish the session.
  *@param serverWSIURI is the URI to use to get to the server's web services.
  */
  public void createSession(String userID, String password, String domain, String objectStore, String serverWSIURI)
    throws FilenetException, RemoteException
  {
    this.userID = userID;
    this.password = password;
    this.fnDomainString = domain;
    this.serverWsiURI = serverWSIURI;
    this.objectStoreName = objectStore;

    // Now, set up the connection


    try
    {
      conn = Factory.Connection.getConnection(serverWsiURI);
      setConnectionCredentials();
      //uc = UserContext.get();
      //uc.setLocale(null);
      //uc.pushSubject(UserContext.createSubject(conn, userName, password, PARAM_LOGIN_MODULE));
      fnDomain = Factory.Domain.fetchInstance(conn,fnDomainString, null);
      if (fnDomain == null)
        throw new FilenetException("Could not locate FileNet domain '"+fnDomain+"'");
      os = Factory.ObjectStore.fetchInstance(fnDomain, objectStoreName, null);
      if (os == null)
        throw new FilenetException("Could not locate FileNet objectstore '"+objectStoreName+"'");
    }
    catch(EngineRuntimeException e)
    {
      Throwable e2 = e.getCause();
      ExceptionCode code = e.getExceptionCode();
      if (code.equals(ExceptionCode.TRANSPORT_WSI_NETWORK_ERROR))
        throw new FilenetException("Transport error: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),FilenetException.TYPE_SERVICEINTERRUPTION);
      if (code.equals(ExceptionCode.SECURITY_WSI_NO_LOGIN_MODULES_SUCCEEDED))
        throw new FilenetException("Login failure: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),FilenetException.TYPE_BADCREDENTIALS);
      if (code.equals(ExceptionCode.API_INVALID_URI))
        throw new FilenetException("Invalid URI error connecting to FileNet: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():"")+" This probably means your connection parameters are incorrect.",FilenetException.TYPE_BADCONNECTIONPARAMS);
      throw new FilenetException("Runtime exception connecting to FileNet: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""));
    }
    catch(Exception e)
    {
      throw new FilenetException("Exception connecting to FileNet: "+e.getMessage());
    }

  }


  /** Delete a session.
  */
  public void destroySession()
    throws FilenetException, RemoteException
  {
    userID = null;
    password = null;
    serverWsiURI = null;
    fnDomainString = null;
    objectStoreName = null;
    fnDomain = null;
    os = null;
    conn = null;
  }

  /** Check if there is a working connection.
  */
  public void checkConnection()
    throws FilenetException, RemoteException
  {
    // Establishing a working session is enough to test connectivity
  }

  /** Get the set of folder names that are children of the specified folder path. */
  public String[] getChildFolders(String[] parentFolderPath)
    throws FilenetException, RemoteException
  {
    setConnectionCredentials();
    // Start at root.
    Folder currentFolder = os.get_RootFolder();
    // Work our way down through the path.  If the path turns out to be invalid,
    // we return null.
    int i = 0;
    while (i < parentFolderPath.length)
    {
      // For each path segment, find the matching child folder
      FolderSet folderSet = currentFolder.get_SubFolders();
      currentFolder = null;
      Iterator fldrIter = folderSet.iterator();
      while (fldrIter.hasNext())
      {
        Folder folder = (Folder)fldrIter.next();
        if (folder.get_FolderName().equals(parentFolderPath[i]))
        {
          currentFolder = folder;
          break;
        }
      }
      
      // Found no folder object with the correct name; the setup must have changed.
      if (currentFolder == null)
        return null;
      
      i++;
    }
    
    // We've located the correct parent folder object.  Construct a list of children to return.
    ArrayList rval = new ArrayList();
    FolderSet children = currentFolder.get_SubFolders();
    Iterator childFolderIterator = children.iterator();
    while (childFolderIterator.hasNext())
    {
      Folder child = (Folder)childFolderIterator.next();
      rval.add(child.get_FolderName());
    }
    
    String[] rvalArray = new String[rval.size()];
    rval.toArray(rvalArray);
    return rvalArray;
  }
  
  /** Get the set of available document classes. */
  public DocumentClassDefinition[] getDocumentClassesDetails()
    throws FilenetException, RemoteException
  {
    DocumentClassDefinition[] oDocClasses=null;
    setConnectionCredentials();

    ArrayList docClasses = getFNDocClasses(PARAM_ROOT_DOC_CLASSNAME);
    docClasses.add(new DocumentClassDefinition(PARAM_ROOT_DOC_CLASSNAME,PARAM_ROOT_DOC_CLASSNAME));
    oDocClasses = new DocumentClassDefinition[docClasses.size()];
    docClasses.toArray((DocumentClassDefinition[]) oDocClasses);

    return oDocClasses;
  }

  /** Get the set of available metadata fields per document class */
  public MetadataFieldDefinition[] getDocumentClassMetadataFieldsDetails(String documentClassName)
    throws FilenetException, RemoteException
  {
    MetadataFieldDefinition[] oProps =null;
    ArrayList propDescs = new ArrayList();
    PropertyDescriptionList pdescs=null;
    setConnectionCredentials();
    int i=0;

    try
    {
      PropertyFilter pf = new PropertyFilter();
      pf.addIncludeType(0, null, Boolean.TRUE, FilteredPropertyType.ANY, null);

      ClassDescription classDesc = (ClassDescription)Factory.ClassDescription.fetchInstance(os, documentClassName, pf);
      if (classDesc != null)
      {
        pdescs = classDesc.get_PropertyDescriptions();
        PropertyDescription propDesc = null;
        Iterator iter = pdescs.iterator();
        while (iter.hasNext())
        {
          propDesc = (PropertyDescription) iter.next();
          MetadataFieldDefinition mdf = new MetadataFieldDefinition(propDesc.get_DisplayName(),propDesc.get_SymbolicName());
          propDescs.add(mdf);
        }
      }
    }
    catch (EngineRuntimeException e)
    {
      Throwable e2 = e.getCause();
      throw new FilenetException("Runtime exception getting document class properties: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""));
    }
    catch(Exception e)
    {
      throw new FilenetException("FileNet exception getting document class properties: "+e.getMessage());
    }
    oProps = new MetadataFieldDefinition[propDescs.size()];
    propDescs.toArray(oProps);

    return oProps;
  }

  /** Execute a sql statement against FileNet and return the matching object id's */
  public String[] getMatchingObjectIds(String sql)
    throws RemoteException, FilenetException
  {
    setConnectionCredentials();

    HashMap docIds = new HashMap();

    SearchSQL sqlObject = new SearchSQL();
    sqlObject.setQueryString(sql);
    // System.out.println("Sql string is: "+sql);
    SearchScope searchScope = new SearchScope(os);

    // Uses fetchRows to test the SQL statement.
    try
    {
      // System.out.println("Fetching rows");
      RepositoryRowSet rowSet = searchScope.fetchRows(sqlObject, null, null, new Boolean(true));
      Iterator iter = rowSet.iterator();
      while (iter.hasNext())
      {
        // System.out.println("Found a row");
        RepositoryRow row = (RepositoryRow) iter.next();
        String docId = row.getProperties().get("Id").getIdValue().toString();
        docIds.put(docId,docId);
      }
    }
    catch (EngineRuntimeException e)
    {
      Throwable e2 = e.getCause();
      ExceptionCode code = e.getExceptionCode();
      if (code.equals(ExceptionCode.TRANSPORT_WSI_NETWORK_ERROR))
        throw new FilenetException("Transport error: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),FilenetException.TYPE_SERVICEINTERRUPTION);
      if (code.equals(ExceptionCode.SECURITY_WSI_NO_LOGIN_MODULES_SUCCEEDED))
        throw new FilenetException("Login failure: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),FilenetException.TYPE_BADCREDENTIALS);
      if (code.equals(ExceptionCode.E_ACCESS_DENIED))
        throw new FilenetException("Access denied getting document information: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),FilenetException.TYPE_NOTALLOWED);
      throw new FilenetException("Runtime exception getting matching object ids: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""));
    }
    catch(Exception e)
    {
      throw new FilenetException("Query failed: '" + sql + "':" + e.getMessage(),e);
    }

    String[] rval = new String[docIds.size()];
    Iterator iter = docIds.keySet().iterator();
    int i = 0;
    while (iter.hasNext())
    {
      String docId = (String)iter.next();
      rval[i] = docId;
      i++;
    }

    return rval;
  }

  /** Get the document content information given an object id.  Will return null if the version id is not a current document version id. */
  public Integer getDocumentContentCount(String docId)
    throws RemoteException, FilenetException
  {
    setConnectionCredentials();
    try
    {
      com.filenet.api.core.Document doc = Factory.Document.fetchInstance(os, docId, null);
      if (doc == null)
        return null;
      ContentElementList elements = doc.get_ContentElements();
      int count = elements.size();
      return new Integer(count);
    }
    catch (EngineRuntimeException e)
    {
      Throwable e2 = e.getCause();
      ExceptionCode code = e.getExceptionCode();
      if (code.equals(ExceptionCode.TRANSPORT_WSI_NETWORK_ERROR))
        throw new FilenetException("Transport error: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),FilenetException.TYPE_SERVICEINTERRUPTION);
      if (code.equals(ExceptionCode.SECURITY_WSI_NO_LOGIN_MODULES_SUCCEEDED))
        throw new FilenetException("Login failure: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),FilenetException.TYPE_BADCREDENTIALS);
      if (code.equals(ExceptionCode.E_ACCESS_DENIED))
        throw new FilenetException("Access denied getting content information: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),FilenetException.TYPE_NOTALLOWED);
      if (code.equals(ExceptionCode.API_NO_CONTENT_ELEMENTS))
        return new Integer(0);
      if (code.equals(ExceptionCode.E_OBJECT_NOT_FOUND))
        return null;
      throw new FilenetException("Runtime exception getting content information: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""));
    }
    catch(Exception e)
    {
      throw new FilenetException("Content count request failed: '" + docId + "':" + e.getMessage(),e);
    }
  }

  /** Get document information for a given filenet document.  Will return null if the version id is not a current document version id.
  * The metadataFields hashmap is keyed by document class, and contains as a value either Boolean(true) (meaning "all"), or a String[] that has the
  * list of fields desired. */
  public FileInfo getDocumentInformation(String docId, Map<String,Object> metadataFields)
    throws FilenetException, RemoteException
  {
    //System.out.println("Looking for document information on "+docId);
    setConnectionCredentials();
    try
    {
      com.filenet.api.core.Document doc = Factory.Document.fetchInstance(os, docId, null);
      if (doc == null)
      {
        //System.out.println(" For "+docId+", null object return");
        return null;
      }
      if(!(doc.get_IsCurrentVersion().booleanValue()))
      {
        //System.out.println(" For "+docId+",isCurrent() is false");
        return null;
      }
      //else
      //      System.out.println("For "+docId+" isCurrent() is true");

      String docClass = doc.getClassName();
      FileInfo rval = new FileInfo(docClass);

      //System.out.println("Got class name for "+docId);
      Object metadataObject = metadataFields.get(docClass);
      if (metadataObject != null && metadataObject instanceof Boolean)
      {
        // "All metadata"
        com.filenet.api.property.Properties props = doc.getProperties();
        Iterator iter = props.iterator();
        while (iter.hasNext())
        {
          com.filenet.api.property.Property prop = (com.filenet.api.property.Property)iter.next();
          String sPropName = prop.getPropertyName();
          Object objPropVal = prop.getObjectValue();
          if (objPropVal != null)
            rval.addMetadataValue(sPropName, objPropVal.toString());
        }
      }
      else if (metadataObject != null)
      {
        String[] fields = (String[])metadataObject;
        for (int j=0; j < fields.length; j++)
        {
          String sPropName = fields[j];
          try
          {
            //System.out.println("Getting properties for "+docId);
            Object objPropVal = doc.getProperties().getObjectValue(sPropName);
            if (objPropVal != null)
              rval.addMetadataValue(sPropName,objPropVal.toString());
          }
          catch (Exception e)
          {
            // Is this what happens when you ask for a property that doesn't exist?  Ask - MHL
          }
        }
      }
      else
      {
        // It's a kind of document we didn't want
        return null;
      }

      //System.out.println("Getting permissions for "+docId);
      AccessPermissionList apl = doc.get_Permissions();
      //System.out.println("Got permissions for "+docId);
      Iterator iter = apl.iterator();
      while (iter.hasNext())
      {
        AccessPermission ap = (AccessPermission)iter.next();
        AccessType at = ap.get_AccessType();
        int atval = at.getValue();
        int am = ap.get_AccessMask().intValue();
        int tmp = AccessLevel.VIEW_AS_INT;

        if ((am & tmp) ==  tmp && (atval==AccessType.ALLOW_AS_INT || atval==AccessType.DENY_AS_INT))
        {

          String gname = ap.get_GranteeName();
          // System.out.println("Docid "+docId+" has view access for "+gname);
          if (!gname.equals("#AUTHENTICATED-USERS"))
          {
            //System.out.println("Getting user "+gname);
            SecurityPrincipalType gtype = ap.get_GranteeType();
            if (gtype.getValue() == SecurityPrincipalType.USER_AS_INT) {
              User usr = Factory.User.fetchInstance(conn, gname, null);
              if (usr != null) {
                String sid = usr.get_Id();
                if (atval == AccessType.ALLOW_AS_INT)
                  rval.addAclValue(sid);
                else if (atval == AccessType.DENY_AS_INT)
                  rval.addDenyAclValue(sid);
              }
            } else {
              Group grp = Factory.Group.fetchInstance(conn, gname, null);
              if (grp != null) {
                String sid = grp.get_Id();
                if (atval == AccessType.ALLOW_AS_INT)
                  rval.addAclValue(sid);
                else if (atval == AccessType.DENY_AS_INT)
                  rval.addDenyAclValue(sid);
              }
            }
          }
          else
          {
            if (atval == AccessType.ALLOW_AS_INT)
              // Still trying to verify that this SID means the right thing in this context
              rval.addAclValue("S-1-1-0");
            else if (atval == AccessType.DENY_AS_INT)
              rval.addDenyAclValue("S-1-1-0");
          }
        }
        // else
        //      System.out.println("Docid "+docId+" has access level "+Integer.toString(am)+" with type "+Integer.toString(atval));
      }
      // System.out.println("DocId "+docId+" has "+Integer.toString(rval.getAclCount())+" acls");
      return rval;
    }
    catch (EngineRuntimeException e)
    {
      Throwable e2 = e.getCause();
      ExceptionCode code = e.getExceptionCode();
      if (code.equals(ExceptionCode.E_OBJECT_NOT_FOUND))
      {
        //System.out.println(" For "+docId+", OBJECT_NOT_FOUND exception");
        return null;
      }
      if (code.equals(ExceptionCode.TRANSPORT_WSI_NETWORK_ERROR))
        throw new FilenetException("Transport error: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),FilenetException.TYPE_SERVICEINTERRUPTION);
      if (code.equals(ExceptionCode.SECURITY_WSI_NO_LOGIN_MODULES_SUCCEEDED))
        throw new FilenetException("Login failure: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),FilenetException.TYPE_BADCREDENTIALS);
      if (code.equals(ExceptionCode.E_ACCESS_DENIED))
        throw new FilenetException("Access denied getting document information: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),FilenetException.TYPE_NOTALLOWED);
      throw new FilenetException("Runtime exception getting document information: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""));
    }
    catch(Exception e)
    {
      throw new FilenetException("Get document information failed:" + e.getMessage(),e);
    }

  }

  /** Get document contents */
  public void getDocumentContents(String docId, int elementNumber, String tempFileName)
    throws FilenetException, RemoteException
  {
    setConnectionCredentials();
    try
    {
      com.filenet.api.core.Document doc =  Factory.Document.fetchInstance(os, docId, null);
      if (doc != null)
      {

        ContentElementList elements = doc.get_ContentElements();
        Iterator iter = elements.iterator();

        // There are multiple documents per "document version" possible in filenet.
        ContentTransfer element = null;
        int i = 0;
        while ( iter.hasNext())
        {
          element = (ContentTransfer)iter.next();
          if (i == elementNumber)
            break;
          i++;
        }

        if (element == null)
          throw new FilenetException("Could not locate element "+Integer.toString(elementNumber)+" in document '"+docId+"'");

        File f = new File(tempFileName);

        InputStream is = element.accessContentStream();
        try
        {
          // Copy the document to the temporary file so described, and return
          OutputStream os = new FileOutputStream(f);
          try
          {
            byte[] byteBuffer = new byte[65536];
            while (true)
            {
              int amt = is.read(byteBuffer);
              if (amt == -1)
                break;
              os.write(byteBuffer,0,amt);
            }
          }
          finally
          {
            os.close();
          }
        }
        catch (IOException e)
        {
          f.delete();
          throw new FilenetException("Could not read file from FileNet for document '"+docId+"': "+e.getMessage(),e);
        }
        finally
        {
          try
          {
            is.close();
          }
          catch (IOException e)
          {
            // Do nothing
          }
        }
      }
      else
        throw new FilenetException("Unknown file: '"+docId+"'");
    }
    catch (EngineRuntimeException e)
    {
      Throwable e2 = e.getCause();
      ExceptionCode code = e.getExceptionCode();
      if (code.equals(ExceptionCode.E_OBJECT_NOT_FOUND))
        throw new FilenetException("File not found: '"+docId+"'");
      if (code.equals(ExceptionCode.TRANSPORT_WSI_NETWORK_ERROR))
        throw new FilenetException("Transport error: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),FilenetException.TYPE_SERVICEINTERRUPTION);
      if (code.equals(ExceptionCode.SECURITY_WSI_NO_LOGIN_MODULES_SUCCEEDED))
        throw new FilenetException("Login failure: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),FilenetException.TYPE_BADCREDENTIALS);
      if (code.equals(ExceptionCode.E_ACCESS_DENIED))
        throw new FilenetException("Access denied getting document contents: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""),FilenetException.TYPE_NOTALLOWED);
      throw new FilenetException("Runtime exception getting document contents: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""));
    }
    catch(Exception e)
    {
      throw new FilenetException("Get document contents failed:" + e.getMessage(),e);
    }

  }


  // Protected methods

  protected void setConnectionCredentials()
  {
    uc = UserContext.get();
    //uc.setLocale(null);
    uc.pushSubject(UserContext.createSubject(conn, userID, password, PARAM_LOGIN_MODULE));
  }

  protected ArrayList getFNDocClasses(String rootClass)
    throws FilenetException
  {
    ArrayList al = new ArrayList();

    try
    {
      ClassDefinition classDef = Factory.ClassDefinition.fetchInstance(os, rootClass, null);
      ClassDefinitionSet cds = classDef.get_ImmediateSubclassDefinitions();
      ClassDefinition clsDesc = null;
      Iterator iter = cds.iterator();
      if (iter != null){
        while (iter.hasNext()){
          clsDesc = (ClassDefinition) iter.next();
          al.addAll(getFNDocClasses(clsDesc.get_SymbolicName()));
          DocumentClassDefinition dc = new DocumentClassDefinition(clsDesc.get_DisplayName(),clsDesc.get_SymbolicName());
          al.add(dc);
        }
      }
    }
    catch (EngineRuntimeException e)
    {
      Throwable e2 = e.getCause();
      throw new FilenetException("Runtime exception getting FileNet class definition details: "+e.getMessage()+((e2!=null)?": "+e2.getMessage():""));
    }
    catch(Exception e)
    {
      throw new FilenetException("Exception getting FileNet class definition details: "+e.getMessage());
    }
    return al;
  }


}
