<%@ include file="adminHeaders.jsp" %>

<%

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
%>

<%
	// The purpose of this jsp is to execute commands, and then dispatch to the right form or display page using
	// jsp:forward.
	// Note that, for errors, the logic must involve another step.  That step involves dispatching to the error
	// page, and passing in the error and the name of the page to go to when the error is accepted by the user.
	// This page must therefore NEVER cause text to be streamed, and must ALWAYS pass information to the page
	// it forwards to using arguments and thread context only.
	//
	// The parameters that the page expects depend on the operation.  The operation is passed as the parameter "op".
	// For many operations there is a secondary parameter determining the type of entity being operated on.
	// This is passed in the parameter "type".
	//
	// Dispatches are handled within the logic for each operation type.  This includes dispatches to the error page.
	// The error page receives the following argument values: "text" (the error text), and "target" (the page to go to
	// on confirmation).
	//
	// If no operation at all is present, or if no dispatch occurs, then this code simply dispatches to the home admin page.

	try
	{
		// Make a few things we will need
		// Get the job manager handle
		IJobManager manager = JobManagerFactory.make(threadContext);
		IRepositoryConnectionManager connManager = RepositoryConnectionManagerFactory.make(threadContext);
		IAuthorityConnectionManager authConnManager = AuthorityConnectionManagerFactory.make(threadContext);
		IOutputConnectionManager outputManager = OutputConnectionManagerFactory.make(threadContext);
		
		String op = variableContext.getParameter("op");

		if (op != null)
		{
		    if (op.equals("Report") || op.equals("Status"))
		    {
			String type = variableContext.getParameter("type");
			if (type != null)
			{
%>
				<jsp:forward page='<%=type+".jsp"%>'/>
<%
			}
		    }
		    else if (op.equals("Save"))
		    {
			// Save operation.
			// There are two different kinds of save: for jobs, and for repository connections.
			String type = variableContext.getParameter("type");
			if (type != null && type.equals("job"))
			{
				// Saving a job.
				try
				{
					String jobID = variableContext.getParameter("jobid");
					IJobDescription job;
					if (jobID == null)
						job = manager.createJob();
					else
					{
						job = manager.load(new Long(jobID));
						if (job == null)
							throw new MetacartaException("No such job: "+jobID);
					}

					// Gather all the data from the form.
					String x = variableContext.getParameter("description");
					if (x != null)
						job.setDescription(x);
					x = variableContext.getParameter("outputname");
					if (x != null)
						job.setOutputConnectionName(x);
					x = variableContext.getParameter("connectionname");
					if (x != null)
						job.setConnectionName(x);
					x = variableContext.getParameter("scheduletype");
					if (x != null)
						job.setType(Integer.parseInt(x));
					x = variableContext.getParameter("startmethod");
					if (x != null)
						job.setStartMethod(Integer.parseInt(x));
					x = variableContext.getParameter("hopcountmode");
					if (x != null)
						job.setHopcountMode(Integer.parseInt(x));

					x = variableContext.getParameter("schedulerecords");
					String[] y;

					if (x != null)
					{
						// Read records and put them into the job description
						job.clearScheduleRecords();
						int recordCount = Integer.parseInt(x);
						int j = 0;
						while (j < recordCount)
						{
							String indexValue = Integer.toString(j);
							EnumeratedValues srDayOfWeek = null;
							EnumeratedValues srDayOfMonth = null;
							EnumeratedValues srMonthOfYear = null;
							EnumeratedValues srYear = null;
							EnumeratedValues srHourOfDay = null;
							EnumeratedValues srMinutesOfHour = null;
							Long srDuration = null;

							y = variableContext.getParameterValues("dayofweek"+indexValue);
							if (y != null)
							{
								if (y.length >= 1 && y[0].equals("none"))
									srDayOfWeek = null;
								else
									srDayOfWeek = new EnumeratedValues(y);
							}
							y = variableContext.getParameterValues("dayofmonth"+indexValue);
							if (y != null)
							{
								if (y.length >= 1 && y[0].equals("none"))
									srDayOfMonth = null;
								else
									srDayOfMonth = new EnumeratedValues(y);
							}
							y = variableContext.getParameterValues("monthofyear"+indexValue);
							if (y != null)
							{
								if (y.length >= 1 && y[0].equals("none"))
									srMonthOfYear = null;
								else
									srMonthOfYear = new EnumeratedValues(y);
							}
							y = variableContext.getParameterValues("year"+indexValue);
							if (y != null)
							{
								if (y.length >= 1 && y[0].equals("none"))
									srYear = null;
								else
									srYear = new EnumeratedValues(y);
							}
							y = variableContext.getParameterValues("hourofday"+indexValue);
							if (y != null)
							{
								if (y.length >= 1 && y[0].equals("none"))
									srHourOfDay = null;
								else
									srHourOfDay = new EnumeratedValues(y);
							}
							y = variableContext.getParameterValues("minutesofhour"+indexValue);
							if (y != null)
							{
								if (y.length >= 1 && y[0].equals("none"))
									srMinutesOfHour = null;
								else
									srMinutesOfHour = new EnumeratedValues(y);
							}
							x = variableContext.getParameter("duration"+indexValue);
							if (x != null)
							{
								if (x.length() == 0)
									srDuration = null;
								else
									srDuration = new Long(new Long(x).longValue()*60000L);
							}
							ScheduleRecord sr = new ScheduleRecord(srDayOfWeek,srMonthOfYear,srDayOfMonth,srYear,srHourOfDay,srMinutesOfHour,
								null,srDuration);
							job.addScheduleRecord(sr);
							j++;
						}
					}

					x = variableContext.getParameter("priority");
					if (x != null)
						job.setPriority(Integer.parseInt(x));
					x = variableContext.getParameter("recrawlinterval");
					if (x != null)
					{
						if (x.length() == 0)
							job.setInterval(null);
						else
							job.setInterval(new Long(new Long(x).longValue() * 60000L));
					}
					x = variableContext.getParameter("reseedinterval");
					if (x != null)
					{
						if (x.length() == 0)
							job.setReseedInterval(null);
						else
							job.setReseedInterval(new Long(new Long(x).longValue() * 60000L));
					}
					x = variableContext.getParameter("expirationinterval");
					if (x != null)
					{
						if (x.length() == 0)
							job.setExpiration(null);
						else
							job.setExpiration(new Long(new Long(x).longValue() * 60000L));
					}

					IRepositoryConnection connection = connManager.load(job.getConnectionName());
					IOutputConnection outputConnection = outputManager.load(job.getOutputConnectionName());
					String JSPFolder = RepositoryConnectorFactory.getJSPFolder(threadContext,connection.getClassName());
					String outputJSPFolder = OutputConnectorFactory.getJSPFolder(threadContext,outputConnection.getClassName());
					String[] relationshipTypes = RepositoryConnectorFactory.getRelationshipTypes(threadContext,connection.getClassName());

					// Gather hopcount filters
					x = variableContext.getParameter("hopfilters");
					if (x != null && relationshipTypes != null)
					{
						job.clearHopCountFilters();
						int j = 0;
						job.clearHopCountFilters();
						while (j < relationshipTypes.length)
						{
							String relationshipType = relationshipTypes[j++];
							x = variableContext.getParameter("hopmax_"+relationshipType);
							if (x != null && x.length() > 0)
							{
								job.addHopCountFilter(relationshipType,new Long(x));
							}
						}
					}
						
					if (outputJSPFolder != null)
					{
						threadContext.save("OutputSpecification",job.getOutputSpecification());
						threadContext.save("OutputConnection",outputConnection);
%>
					<jsp:include page='<%="/output/"+outputJSPFolder+"/postspec.jsp"%>' flush="false"/>
<%
					}

					if (JSPFolder != null)
					{
						threadContext.save("DocumentSpecification",job.getSpecification());
						threadContext.save("RepositoryConnection",connection);
%>
					<jsp:include page='<%="/connectors/"+JSPFolder+"/postspec.jsp"%>' flush="false"/>
<%
					}
					manager.save(job);
					// Reset the job schedule. We may want to make this explicit at some point; having
					// this happen all the time seems wrong.
					manager.resetJobSchedule(job.getID());
					variableContext.setParameter("jobid",job.getID().toString());
%>
					<jsp:forward page="viewjob.jsp"/>
<%

				}
				catch (MetacartaException e)
				{
					e.printStackTrace();
					variableContext.setParameter("text",e.getMessage());
					variableContext.setParameter("target","listjobs.jsp");
%>
					<jsp:forward page="error.jsp"/>
<%
				}
			}
			else if (type != null && type.equals("output"))
			{
				// Saving a connection.
				try
				{
					String connectionName = variableContext.getParameter("connname");
					IOutputConnection connection = outputManager.load(connectionName);
					if (connection == null)
					{
						connection = outputManager.create();
						connection.setName(connectionName);
					}

					// Gather all the data from the form.
					String x = variableContext.getParameter("description");
					if (x != null)
						connection.setDescription(x);
					x = variableContext.getParameter("classname");
					if (x != null)
						connection.setClassName(x);
					x = variableContext.getParameter("maxconnections");
					if (x != null && x.length() > 0)
						connection.setMaxConnections(Integer.parseInt(x));

					String JSPFolder = OutputConnectorFactory.getJSPFolder(threadContext,connection.getClassName());

					threadContext.save("Parameters",connection.getConfigParams());
					if (JSPFolder != null)
					{
%>
					<jsp:include page='<%="/output/"+JSPFolder+"/postconfig.jsp"%>' flush="false"/>
<%
					}
					outputManager.save(connection);
					variableContext.setParameter("connname",connectionName);
%>
					<jsp:forward page="viewoutput.jsp"/>
<%
				}
				catch (MetacartaException e)
				{
					e.printStackTrace();
					variableContext.setParameter("text",e.getMessage());
					variableContext.setParameter("target","listoutputs.jsp");
%>
					<jsp:forward page="error.jsp"/>
<%
				}
			}
			else if (type != null && type.equals("connection"))
			{
				// Saving a connection.
				try
				{
					String connectionName = variableContext.getParameter("connname");
					IRepositoryConnection connection = connManager.load(connectionName);
					if (connection == null)
					{
						connection = connManager.create();
						connection.setName(connectionName);
					}

					// Gather all the data from the form.
					String x = variableContext.getParameter("description");
					if (x != null)
						connection.setDescription(x);
					x = variableContext.getParameter("classname");
					if (x != null)
						connection.setClassName(x);
					x = variableContext.getParameter("authorityname");
					if (x != null && x.length() > 0)
					{
						if (x.equals("_none_"))
							connection.setACLAuthority(null);
						else
							connection.setACLAuthority(x);
					}
					x = variableContext.getParameter("maxconnections");
					if (x != null && x.length() > 0)
						connection.setMaxConnections(Integer.parseInt(x));

					// Gather and save throttles
					x = variableContext.getParameter("throttlecount");
					if (x != null)
					{
						int throttleCount = Integer.parseInt(x);
						connection.clearThrottleValues();
						int j = 0;
						while (j < throttleCount)
						{
							String regexp = variableContext.getParameter("throttle_"+Integer.toString(j));
							String desc = variableContext.getParameter("throttledesc_"+Integer.toString(j));
							if (desc == null)
								desc = "";
							String value = variableContext.getParameter("throttlevalue_"+Integer.toString(j));
							connection.addThrottleValue(regexp,desc,(float)(((double)new Long(value).longValue())/(double)(60000.0)));
							j++;
						}
						x = variableContext.getParameter("throttleop");
						if (x != null && x.equals("Delete"))
						{
							// Delete an item from the throttles list
							x = variableContext.getParameter("throttlenumber");
							String regexp = variableContext.getParameter("throttle_"+x);
							connection.deleteThrottleValue(regexp);
						}
						else if (x != null && x.equals("Add"))
						{
							// Add an item to the throttles list
							String regexp = variableContext.getParameter("throttle");
							String desc = variableContext.getParameter("throttledesc");
							if (desc == null)
								desc = "";
							Long value = new Long(variableContext.getParameter("throttlevalue"));
							connection.addThrottleValue(regexp,desc,(float)(((double)value.longValue())/(double)(60000.0)));
						}
					}

					String JSPFolder = RepositoryConnectorFactory.getJSPFolder(threadContext,connection.getClassName());

					threadContext.save("Parameters",connection.getConfigParams());
					if (JSPFolder != null)
					{
%>
					<jsp:include page='<%="/connectors/"+JSPFolder+"/postconfig.jsp"%>' flush="false"/>
<%
					}
					connManager.save(connection);
					variableContext.setParameter("connname",connectionName);
%>
					<jsp:forward page="viewconnection.jsp"/>
<%
				}
				catch (MetacartaException e)
				{
					e.printStackTrace();
					variableContext.setParameter("text",e.getMessage());
					variableContext.setParameter("target","listconnections.jsp");
%>
					<jsp:forward page="error.jsp"/>
<%
				}
			}
			else if (type != null && type.equals("authority"))
			{
				// Saving a connection.
				try
				{
					String connectionName = variableContext.getParameter("connname");
					IAuthorityConnection connection = authConnManager.load(connectionName);
					if (connection == null)
					{
						connection = authConnManager.create();
						connection.setName(connectionName);
					}

					// Gather all the data from the form.
					String x = variableContext.getParameter("description");
					if (x != null)
						connection.setDescription(x);
					x = variableContext.getParameter("classname");
					if (x != null)
						connection.setClassName(x);
					x = variableContext.getParameter("maxconnections");
					if (x != null && x.length() > 0)
						connection.setMaxConnections(Integer.parseInt(x));

					String JSPFolder = AuthorityConnectorFactory.getJSPFolder(threadContext,connection.getClassName());

					threadContext.save("Parameters",connection.getConfigParams());
					if (JSPFolder != null)
					{
%>
					<jsp:include page='<%="/authorities/"+JSPFolder+"/postconfig.jsp"%>' flush="false"/>
<%
					}
					authConnManager.save(connection);
					variableContext.setParameter("connname",connectionName);
%>
					<jsp:forward page="viewauthority.jsp"/>
<%
				}
				catch (MetacartaException e)
				{
					e.printStackTrace();
					variableContext.setParameter("text",e.getMessage());
					variableContext.setParameter("target","listauthorities.jsp");
%>
					<jsp:forward page="error.jsp"/>
<%
				}
			}
			else
			{
				// Error
				variableContext.setParameter("text","Illegal parameter to page");
				variableContext.setParameter("target","index.jsp");
%>
				<jsp:forward page="error.jsp"/>
<%
			}
		    }


		    else if (op.equals("Continue"))
		    {
			// Continue (while editing a job)
			String type = variableContext.getParameter("type");
			if (type != null && (type.equals("simplereport") || type.equals("maxactivityreport") ||
				type.equals("maxbandwidthreport") || type.equals("resultreport") ||
				type.equals("documentstatus") || type.equals("queuestatus")))
			{
%>
				<jsp:forward page='<%=type+".jsp"%>'/>
<%
			}
			else if (type != null && type.equals("job"))
			{
%>
				<jsp:forward page="editjob.jsp"/>
<%
			}
			else if (type != null && type.equals("output"))
			{
%>
				<jsp:forward page="editoutput.jsp"/>
<%
			}
			else if (type != null && type.equals("connection"))
			{
%>
				<jsp:forward page="editconnection.jsp"/>
<%
			}
			else if (type != null && type.equals("authority"))
			{
%>
				<jsp:forward page="editauthority.jsp"/>
<%
			}
			else
			{
				// Error
				variableContext.setParameter("text","Illegal parameter to page");
				variableContext.setParameter("target","index.jsp");
%>
				<jsp:forward page="error.jsp"/>
<%
			}
		    }


		    else if (op.equals("Delete"))
		    {
			// Delete operation
			String type = variableContext.getParameter("type");
			if (type != null && type.equals("job"))
			{
				try
				{
					String jobID = variableContext.getParameter("jobid");
					if (jobID == null)
						throw new MetacartaException("Missing job parameter");
					manager.deleteJob(new Long(jobID));
%>
					<jsp:forward page="listjobs.jsp"/>
<%
				}
				catch (MetacartaException e)
				{
					e.printStackTrace();
					variableContext.setParameter("text",e.getMessage());
					variableContext.setParameter("target","listjobs.jsp");
%>
					<jsp:forward page="error.jsp"/>
<%
				}
			}
			else if (type != null && type.equals("output"))
			{
				try
				{
					String connectionName = variableContext.getParameter("connname");
					if (connectionName == null)
						throw new MetacartaException("Missing connection parameter");
					outputManager.delete(connectionName);
%>
					<jsp:forward page="listoutputs.jsp"/>
<%
				}
				catch (MetacartaException e)
				{
					e.printStackTrace();
					variableContext.setParameter("text",e.getMessage());
					variableContext.setParameter("target","listoutputs.jsp");
%>
					<jsp:forward page="error.jsp"/>
<%
				}
			}
			else if (type != null && type.equals("connection"))
			{
				try
				{
					String connectionName = variableContext.getParameter("connname");
					if (connectionName == null)
						throw new MetacartaException("Missing connection parameter");
					connManager.delete(connectionName);
%>
					<jsp:forward page="listconnections.jsp"/>
<%
				}
				catch (MetacartaException e)
				{
					e.printStackTrace();
					variableContext.setParameter("text",e.getMessage());
					variableContext.setParameter("target","listconnections.jsp");
%>
					<jsp:forward page="error.jsp"/>
<%
				}
			}
			else if (type != null && type.equals("authority"))
			{
				try
				{
					String connectionName = variableContext.getParameter("connname");
					if (connectionName == null)
						throw new MetacartaException("Missing connection parameter");
					authConnManager.delete(connectionName);
%>
					<jsp:forward page="listauthorities.jsp"/>
<%
				}
				catch (MetacartaException e)
				{
					e.printStackTrace();
					variableContext.setParameter("text",e.getMessage());
					variableContext.setParameter("target","listauthorities.jsp");
%>
					<jsp:forward page="error.jsp"/>
<%
				}
			}
			else
			{
				// Error
				variableContext.setParameter("text","Illegal parameter to page");
				variableContext.setParameter("target","index.jsp");
%>
				<jsp:forward page="error.jsp"/>
<%
			}
		    }
		    else if (op.equals("Cancel"))
		    {
			// Cancel operation
			// Once again, lots of different cancels
			String type = variableContext.getParameter("type");
			if (type != null && type.equals("job"))
			{
				String jobID = variableContext.getParameter("jobid");
				if (jobID != null)
				{
					variableContext.setParameter("jobid",jobID);
%>
					<jsp:forward page="viewjob.jsp"/>
<%
				}
				else
				{
%>
					<jsp:forward page="listjobs.jsp"/>
<%
				}
			}
			else if (type != null && type.equals("output"))
			{
%>
				<jsp:forward page="listoutputs.jsp"/>
<%
			}
			else if (type != null && type.equals("connection"))
			{
%>
				<jsp:forward page="listconnections.jsp"/>
<%
			}
			else if (type != null && type.equals("authority"))
			{
%>
				<jsp:forward page="listauthorities.jsp"/>
<%
			}
			else
			{
				// Error
				variableContext.setParameter("text","Illegal parameter to page");
				variableContext.setParameter("target","index.jsp");
%>
				<jsp:forward page="error.jsp"/>
<%
			}
		    }



		    else if (op.equals("Start"))
		    {
			// Start a job.
			String jobID = variableContext.getParameter("jobid");
			manager.manualStart(new Long(jobID));
			// Forward to showjobstatus
%>
			<jsp:forward page="showjobstatus.jsp"/>
<%
		    }



		    else if (op.equals("Pause"))
		    {
			// Pause a job
			String jobID = variableContext.getParameter("jobid");
			manager.pauseJob(new Long(jobID));
			// Forward to showjobstatus
%>
			<jsp:forward page="showjobstatus.jsp"/>
<%
		    }



		    else if (op.equals("Abort"))
		    {
			// Abort a job
			String jobID = variableContext.getParameter("jobid");
			manager.manualAbort(new Long(jobID));
			// Forward to showjobstatus
%>
			<jsp:forward page="showjobstatus.jsp"/>
<%
		    }


		    else if (op.equals("Restart"))
		    {
			// Abort a job
			String jobID = variableContext.getParameter("jobid");
			manager.manualAbortRestart(new Long(jobID));
			// Forward to showjobstatus
%>
			<jsp:forward page="showjobstatus.jsp"/>
<%
		    }


		    else if (op.equals("Resume"))
		    {
			// Pause a job
			String jobID = variableContext.getParameter("jobid");
			manager.restartJob(new Long(jobID));
			// Forward to showjobstatus
%>
			<jsp:forward page="showjobstatus.jsp"/>
<%
		    }


		    else
		    {
			variableContext.setParameter("text","Illegal operation to page");
			variableContext.setParameter("target","index.jsp");

%>
			<jsp:forward page="error.jsp"/>
<%
		    }
		}

		// If we didn't have an op, then we transfer control back to where the page said to.
		String target = variableContext.getParameter("target");
		if (target != null)
		{
%>
			<jsp:forward page='<%=target%>'/>
<%

		}
%>
		<jsp:forward page="index.jsp"/>
<%
	}
	catch (MetacartaException e)
	{
		e.printStackTrace();
		variableContext.setParameter("text",e.getMessage());
		variableContext.setParameter("target","index.jsp");
%>
		<jsp:forward page="error.jsp"/>
<%
	}
%>
