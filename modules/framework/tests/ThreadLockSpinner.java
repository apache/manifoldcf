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

import org.apache.lcf.core.interfaces.*;
import org.apache.lcf.core.system.LCF;

public class ThreadLockSpinner
{

	protected final static String lockName = "TESTLOCK";
	
	public static void main(String[] argv)
		throws Exception
	{
		LCF.initializeEnvironment();
		
		// Start up multiple threads of each kind
		int t1instanceCount = 25;
		int t2instanceCount = 5;
		int i;
		Thread[] t1s = new Thread[t1instanceCount];
		Thread[] t2s = new Thread[t2instanceCount];
		i = 0;
		while (i < t1instanceCount)
		{
			t1s[i] = new Thread1(i);
			i++;
		}
		i = 0;
		while (i < t2instanceCount)
		{
			t2s[i] = new Thread2(i);
			i++;
		}

		System.out.println("Starting test");
		
		i = 0;
		while (i < t1instanceCount)
		{
			t1s[i].start();
			i++;
		}

		i = 0;
		while (i < t2instanceCount)
		{
			t2s[i].start();
			i++;
		}

		while (true)
		{
			i = 0;
			boolean isAlive = false;
			while (i < t1instanceCount)
			{
				if (t1s[i].isAlive())
				{
					isAlive = true;
					break;
				}
				i++;
			}
			if (isAlive == false)
			{
				i = 0;
				while (i < t2instanceCount)
				{
					if (t2s[i].isAlive())
					{
						isAlive = true;
						break;
					}
					i++;
				}
			}
			
			if (isAlive)
			{
				Thread.sleep(1000);
			}
			else
				break;
		}
				
		System.out.println("Done test - no hang");
		
	}

	protected static class Thread1 extends Thread
	{
		public Thread1(int i)
		{
			super();
			setName("Reader - "+Integer.toString(i));
		}

		public void run()
		{
			try
			{
				// Create a thread context object.
				IThreadContext threadContext = ThreadContextFactory.make();
				ILockManager lockManager = LockManagerFactory.make(threadContext);
				
				int i = 0;
				while (i < 1000000)
				{
					if ((i % 1000) == 0)
						System.out.println(getName()+" iteration "+Integer.toString(i));

					// This thread is a reader.
					lockManager.enterReadLock(lockName);
					try
					{
						Thread.sleep(10);
					}
					finally
					{
						lockManager.leaveReadLock(lockName);
					}
					// Pick a random number to sleep, 50-150
					int random = (int)(Math.random() * 100.0);
					Thread.sleep(50+random);
					i++;
				}
				System.out.println(getName()+" done");
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

	}
	
	protected static class Thread2 extends Thread
	{
		public Thread2(int i)
		{
			super();
			setName("Writer - "+Integer.toString(i));
		}
		
		public void run()
		{
			try
			{
				// Create a thread context object.
				IThreadContext threadContext = ThreadContextFactory.make();
				ILockManager lockManager = LockManagerFactory.make(threadContext);
				
				int i = 0;
				while (i < 100000)
				{
					if ((i % 100) == 0)
						System.out.println(getName()+" iteration "+Integer.toString(i));
					// This thread is a writer.
					lockManager.enterWriteLock(lockName);
					try
					{
						Thread.sleep(10);
					}
					finally
					{
						lockManager.leaveWriteLock(lockName);
					}
					int random = (int)(Math.random() * 200.0);
					Thread.sleep(100+random);
					i++;
				}
				System.out.println(getName()+" complete");
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}

	}
	
}
