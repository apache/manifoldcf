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
package org.apache.lcf.ui.multipart;

import org.apache.lcf.core.interfaces.*;
import org.apache.commons.fileupload.*;
import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import java.io.*;

/** This class provides abstract parameter service, including support for uploaded files and
* multipart forms.  It is styled much like HttpServletRequest, but wraps this interface so
* that code can access either standard post data or multipart data transparently.
*/
public class MultipartWrapper
{
        public static final String _rcsid = "@(#)$Id$";

        /** This is the HttpServletRequest object, which will be used for parameters only if
        * the form is not multipart. */
        protected HttpServletRequest request = null;
        protected Map variableMap = new HashMap();
        protected String characterEncoding = null;

        /** Constructor.
        */
        public MultipartWrapper(HttpServletRequest request)
                throws LCFException
        {
                // Check that we have a file upload request
                boolean isMultipart = FileUpload.isMultipartContent(request);
                if (!isMultipart)
                {
                        this.request = request;
                        return;
                }

                // It is multipart!

                // Create a factory for disk-based file items
                DefaultFileItemFactory factory = new DefaultFileItemFactory();

                // Create a new file upload handler
                DiskFileUpload upload = new DiskFileUpload(factory);

                // Parse the request
                try
                {
                        characterEncoding = request.getCharacterEncoding();
                        // Handle broken tomcat; characterEncoding always comes back null for tomcat4
                        if (characterEncoding == null)
                                characterEncoding = "utf8";
                        List items = upload.parseRequest(request);
                        Iterator iter = items.iterator();
                        while (iter.hasNext())
                        {
                                FileItem item = (FileItem) iter.next();
                                String name = item.getFieldName();
                                ArrayList list = (ArrayList)variableMap.get(name);
                                if (list == null)
                                {
                                        list = new ArrayList();
                                        variableMap.put(name,list);
                                }
                                else
                                {
                                        if (((FileItem)list.get(0)).isFormField() != item.isFormField())
                                                throw new LCFException("Illegal form data; posted form has the same name for different data types ('"+name+"')!");
                                }
                                list.add(item);
                        }
                }
                catch (FileUploadException e)
                {
                        throw new LCFException("Problem uploading file: "+e.getMessage(),e);
                }
        }

        /** Get multiple parameter values.
        */
        public String[] getParameterValues(String name)
        {
                // Expect multiple items, all strings
                ArrayList list = (ArrayList)variableMap.get(name);
                if (list == null)
                {
                        if (request != null)
                                return request.getParameterValues(name);
                        return null;
                }

                Object x = list.get(0);

                if ((x instanceof FileItem) && !((FileItem)x).isFormField())
                        return null;

                String[] rval = new String[list.size()];
                int i = 0;
                while (i < rval.length)
                {
                        x = list.get(i);
                        if (x instanceof String)
                                rval[i] = (String)x;
                        else
                        {
                                try
                                {
                                        rval[i] = ((FileItem)x).getString(characterEncoding);
                                }
                                catch (UnsupportedEncodingException e)
                                {
                                        rval[i] = ((FileItem)x).getString();
                                }
                        }
                        i++;
                }
                return rval;
        }

        /** We need to be able to set a parameter
        /** Get single parameter value.
        */
        public String getParameter(String name)
        {
                // Get it as a parameter.
                ArrayList list = (ArrayList)variableMap.get(name);
                if (list == null)
                {
                        if (request != null)
                                return request.getParameter(name);
                        return null;
                }

                Object x = list.get(0);
                if (x instanceof String)
                        return (String)x;

                FileItem item = (FileItem)x;
                if (!item.isFormField())
                        return null;

                try
                {
                        return item.getString(characterEncoding);
                }
                catch (UnsupportedEncodingException e)
                {
                        return item.getString();
                }
        }

        /** Get a file parameter, as a binary input.
        */
        public BinaryInput getBinaryStream(String name)
                throws LCFException
        {
                if (request != null)
                        return null;

                ArrayList list = (ArrayList)variableMap.get(name);
                if (list == null)
                        return null;

                Object x = list.get(0);
                if (x instanceof String)
                        return null;

                FileItem item = (FileItem)x;
                if (item.isFormField())
                        return null;

                try
                {
                        InputStream uploadedStream = item.getInputStream();
                        try
                        {
                                return new TempFileInput(uploadedStream);
                        }
                        finally
                        {
                                uploadedStream.close();
                        }
                }
                catch (IOException e)
                {
                        throw new LCFException("Error creating file binary stream",e);
                }
        }

        /** Get file parameter, as a byte array.
        */
        public byte[] getBinaryBytes(String name)
        {
                if (request != null)
                        return null;

                ArrayList list = (ArrayList)variableMap.get(name);
                if (list == null)
                        return null;

                Object x = list.get(0);
                if (x instanceof String)
                        return null;

                FileItem item = (FileItem)x;
                if (item.isFormField())
                        return null;

                return item.get();
        }

        /** Set a parameter value
        */
        public void setParameter(String name, String value)
        {
                ArrayList values = new ArrayList();
                if (value != null)
                        values.add(value);
                variableMap.put(name,values);
        }

        /** Set an array of parameter values
        */
        public void setParameterValues(String name, String[] values)
        {
                ArrayList valueArray = new ArrayList();
                int i = 0;
                while (i < values.length)
                {
                        valueArray.add(values[i++]);
                }
                variableMap.put(name,valueArray);
        }

}
