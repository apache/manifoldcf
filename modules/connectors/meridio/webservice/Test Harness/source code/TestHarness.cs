using System;
using System.Collections;
using System.Threading;
using MetaCartaTestHarness.MetaCartaWebService;
using MetaCartaTestHarness.DMWebRef;

namespace MetaCartaTestHarness
{

	public class TestHarness
	{
		public static int iterationsPerThread=0;
		public static int numOfThreads=0;
		public static int maxUserID=0;
		public static string token="";

		[STAThread]
		public static void Main(string[] args)
		{
			try
			{
				string username= "";
				string password = "";

				MeridioDM DMWS = new MeridioDM();
				DMWS.Credentials = System.Net.CredentialCache.DefaultCredentials;
				Console.Write("Meridio username : ");
				username = Convert.ToString(Console.ReadLine());
				Console.Write("Meridio password : ");
				password = Convert.ToString(Console.ReadLine());

				//Login to meridio and retrieve a session token
				token = DMWS.Login(username, password, "test");

				Console.Write("Do you want to perform a single userid search? (y/n) ");
				string yesOrNo = Convert.ToString(Console.ReadLine());


				if(yesOrNo == "y")
				{
					//The user has selected that a single search should be performed
					Console.Write("Enter a meridio user id : ");
					int userid = Convert.ToInt32(Console.ReadLine());
					MetaCarta metaCarta = new MetaCarta();
					metaCarta.Credentials = System.Net.CredentialCache.DefaultCredentials;
					GroupResult[] returnedResults;
					
					//call the web service method to retrieve a list of groups
					bool success = metaCarta.GetUsersGroups(token, userid, out returnedResults);			

					if(success)
					{
						//cycle through the results and output to screen
						foreach (GroupResult result in returnedResults)
						{
							Console.WriteLine("User ID: {0} - Group ID: {1} - Group name: {2}", userid, result.groupID, result.groupName);
						}
					}
					else
					{
						Console.WriteLine("Invalid token supplied");
					}
				}
				else
				{ 
					//A single search is not to be performed, multiple calls are going to be made
					Console.Write("Enter max userid in Meridio : ");
					//This value is used to select a random userid to pass into the web service method
					maxUserID = Convert.ToInt32(Console.ReadLine()); 
					Console.Write("Number of threads? ");
					numOfThreads = Convert.ToInt32(Console.ReadLine());
					Console.Write("Number of iterations per thread? ");
					iterationsPerThread = Convert.ToInt32(Console.ReadLine());
					for(int i=0; i<numOfThreads; i++)
					{
						Thread newThread = new Thread(new ThreadStart(ThreadJob));
						newThread.Start();
					}
				}			
				
			}
			catch(Exception ex)
			{
				Console.WriteLine("Error: {0}", ex.ToString());
			}
		}


		static void ThreadJob()
		{
			Random random = new Random();
			for (int i=0; i < iterationsPerThread; i++)
			{
				int userid= random.Next(maxUserID);
				try
				{
					MetaCarta metaCarta = new MetaCarta();
					metaCarta.Credentials = System.Net.CredentialCache.DefaultCredentials;
					GroupResult[] returnedResults;
					
					//call the web service method to retrieve a list of groups
					bool success = metaCarta.GetUsersGroups(token, userid, out returnedResults);			

					if(success)
					{
						//cycle through the results and output to screen
						foreach (GroupResult result in returnedResults)
						{
							Console.WriteLine("User ID: {0} - Group ID: {1} - Group name: {2}", userid, result.groupID, result.groupName);
						}
					}
					else
					{
						Console.WriteLine("Invalid token supplied");
					}

				}
				catch(Exception ex)
				{
					throw(ex);
				}
			}
		}

	}
}
