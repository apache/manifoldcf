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
package org.apache.lcf.core.interfaces;

import org.apache.lcf.core.system.LCF;
import org.apache.lcf.core.system.Logging;
import java.io.*;
import java.util.*;

public class IDFactory
{
	public static final String _rcsid = "@(#)$Id$";

	private static long _id = 0L;
	private static final Integer _proplock = new Integer(0);
	private static boolean propertyChecked = false;
	private static File idFile = null;
	private static File idLock = null;
	private static ArrayList idPool = new ArrayList();

	// The id algorithm depends on the clock.  We don't want to fetch too many; we'll
	// run the risk of a restart in the non-synchronized case beating the clock.
	private final static int poolSize = 100;

	private IDFactory()
	{
	}

	public static String make()
		throws LCFException
	{
		synchronized (_proplock)
		{
			if (propertyChecked == false)
			{
				String synchDirectory = LCF.getProperty(LCF.synchDirectoryProperty);
				if (synchDirectory != null)
				{
					idFile = new File(synchDirectory,"idfile.file");
					idLock = new File(synchDirectory,"idfile.lock");
				}
				propertyChecked = true;
			}
		}

		// See if there's anything in the pool
		synchronized (idPool)
		{
			if (idPool.size() > 0)
				return (String)idPool.remove(idPool.size()-1);

			// Need to fill the pool.
			// If synchronized, we must lock first
			if (idLock != null)
			{
			    try
			    {
				while (true)
				{
					try
					{
						if (idLock.createNewFile())
							break;
					}
					catch (InterruptedIOException e)
					{
						throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
					}
					catch (IOException e)
					{
						// same as returning false above.
					}
					LCF.sleep(10);
				}
			    }
			    catch (InterruptedException e)
			    {
				throw new LCFException("Interrupted: "+e.getMessage(),e,LCFException.INTERRUPTED);
			    }

			}
			try
			{
				// Get the last id used systemwide
				if (idFile != null)
				{
				    try
				    {
					FileReader fr = new FileReader(idFile);
					try
					{
						BufferedReader x = new BufferedReader(fr);
						try
						{
							StringBuffer sb = new StringBuffer();
							while (true)
							{
								int rval = x.read();
								if (rval == -1)
									break;
								sb.append((char)rval);
							}
							_id = new Long(sb.toString()).longValue();
						}
					        catch (NumberFormatException e)
				    		{
							// This should never happen, but it does for reasons as yet unknown on Linux.
							Logging.misc.error("Lost ability to read id file; resetting");
							_id = 0L;
				    		}
						catch (IOException e)
						{
							throw new LCFException("Could not read from id file: '"+idFile.toString()+"'",e);
						}
						finally
						{
							x.close();
						}
					}
					catch (IOException e)
					{
						throw new LCFException("Could not read from id file: '"+idFile.toString()+"'",e);
					}
					finally
					{
						fr.close();
					}
				    }
				    catch (IOException e)
				    {
					_id = 0L;
				    }
				}

				int i = 0;
				while (i < poolSize)
				{
					long newid = System.currentTimeMillis();
					if (newid <= _id)
					{
						newid = _id + 1;
					}
					_id = newid;
					idPool.add(Long.toString(newid));
					i++;
				}

				// Write the updated _id value into the file
				if (idFile != null)
				{
				    try
				    {
					FileWriter fw = new FileWriter(idFile);
					try
					{
						BufferedWriter x = new BufferedWriter(fw);
						try
						{
							x.write(Long.toString(_id));
						}
						finally
						{
							x.close();
						}
					}
					finally
					{
						fw.close();
					}
				    }
				    catch (IOException e)
				    {
					// Hard failure
					throw new LCFException("Can't write id file",e);
				    }

				}
			}
			finally
			{
				if (idLock != null)
					idLock.delete();
			}

			return (String)idPool.remove(idPool.size()-1);
		}
	}

}
