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
package org.apache.manifoldcf.crawler.connectors.webcrawler.tests;

import org.apache.manifoldcf.agents.interfaces.IOutputConnection;
import org.apache.manifoldcf.agents.interfaces.IOutputConnectionManager;
import org.apache.manifoldcf.agents.interfaces.OutputConnectionManagerFactory;
import org.apache.manifoldcf.core.interfaces.ConfigParams;
import org.apache.manifoldcf.core.interfaces.IResultRow;
import org.apache.manifoldcf.core.interfaces.IResultSet;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.Specification;
import org.apache.manifoldcf.core.interfaces.SpecificationNode;
import org.apache.manifoldcf.core.interfaces.ThreadContextFactory;
import org.apache.manifoldcf.crawler.interfaces.*;
import org.apache.manifoldcf.crawler.connectors.webcrawler.WebcrawlerConfig;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;

public class DocumentContentExclusionHSQLDBIT extends BaseITHSQLDB {

    private static final int MAX_DOC_COUNT = 3;

    public static final String CONTENTFILTER_SERVLET_PATH = "/contentexclusiontest";
    private static final int PORT = 8191;
    public static final long MAX_WAIT_TIME = 60 * 1000L;
    public static final String WEB_CONNECTION = "Web Connection";
    static String baseUrl = "http://127.0.0.1:" + PORT + CONTENTFILTER_SERVLET_PATH + "?page=";

    private Server server = null;
    private IJobManager jobManager;
    private IOutputConnectionManager outputConnectionManager;


    private IRepositoryConnectionManager repoConnectionManager;


    @Before
    public void beforeDocumentContentFilterTest() throws Exception {
        server = new Server(new QueuedThreadPool(20));
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(PORT);
        connector.setIdleTimeout(60000);// important for Http KeepAlive
        server.addConnector(connector);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.addServlet(ContentFilterTestServlet.class, CONTENTFILTER_SERVLET_PATH);
        server.setHandler(context);
        server.start();

        IThreadContext tc = ThreadContextFactory.make();
        repoConnectionManager = RepositoryConnectionManagerFactory.make(tc);
        outputConnectionManager = OutputConnectionManagerFactory.make(tc);
        jobManager = JobManagerFactory.make(tc);
        createRepoConnector();
        createOutputConnector();
    }


    @Test
    public void testDocumentContentExclusion() throws Exception {
        //No content exclusion rule
        IJobDescription job = setupContentFilterJob();
        runContentFilterJob(job);
        checkContentFilterHistory(false);
        cleanupContentFilterJobs(job);

        //With exclusion rule
        job = setupContentFilterJob();
        //add content exclusion rule
        addContentExclusionRule(job);
        runContentFilterJob(job);
        checkContentFilterHistory(true);
        cleanupContentFilterJobs(job);
    }

    private void checkContentFilterHistory(boolean hasContentExcluded) throws Exception {
        FilterCriteria filter = new FilterCriteria(new String[]{"process"}, 0l, Long.MAX_VALUE, new RegExpCriteria(".*\\" + CONTENTFILTER_SERVLET_PATH + ".*", true), null);
        SortOrder sortOrderValue = new SortOrder();
        sortOrderValue.addCriteria("entityid", SortOrder.SORT_ASCENDING);
        IResultSet result = repoConnectionManager.genHistorySimple(WEB_CONNECTION, filter, sortOrderValue, 0, 20);
        assertThat(result.getRowCount(), is(MAX_DOC_COUNT));

        for (int i = 0; i < MAX_DOC_COUNT; i++) {
            IResultRow row = result.getRow(i);
            assertThat((String) row.getValue("identifier"), is(baseUrl + i));
            if (hasContentExcluded && i == 1) {
                //if excluding, only page 1 will be excluded
                assertThat((String) row.getValue("resultcode"), is("EXCLUDEDCONTENT"));
                assertThat((String) row.getValue("resultdesc"), is("Rejected due to content exclusion rule"));
            } else {
                assertThat((String) row.getValue("resultcode"), is("OK"));
                assertThat(row.getValue("resultdesc"), is(nullValue()));
            }
        }
    }

    @After
    public void tearDownDocumentContentFilterTest() throws Exception {
        if (server != null) {
            server.stop();
        }
    }


    private IJobDescription setupContentFilterJob() throws Exception {

        // Create a job.
        IJobDescription job = jobManager.createJob();
        job.setDescription("Test Job");
        job.setConnectionName(WEB_CONNECTION);
        job.addPipelineStage(-1, true, "Null Connection", "");
        job.setType(job.TYPE_SPECIFIED);
        job.setStartMethod(job.START_DISABLE);
        job.setHopcountMode(job.HOPCOUNT_NEVERDELETE);

        Specification jobSpec = job.getSpecification();

        // 3 seeds only
        SpecificationNode sn = new SpecificationNode(WebcrawlerConfig.NODE_SEEDS);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_DOC_COUNT; i++) {
            sb.append(baseUrl + i + "\n");
        }
        sn.setValue(sb.toString());
        jobSpec.addChild(jobSpec.getChildCount(), sn);

        sn = new SpecificationNode(WebcrawlerConfig.NODE_INCLUDES);
        sn.setValue(".*\n");
        jobSpec.addChild(jobSpec.getChildCount(), sn);

        sn = new SpecificationNode(WebcrawlerConfig.NODE_INCLUDESINDEX);
        sn.setValue(".*\n");
        jobSpec.addChild(jobSpec.getChildCount(), sn);
        // Save the job.
        jobManager.save(job);

        return job;

    }

    private void addContentExclusionRule(IJobDescription job) throws ManifoldCFException {
        Specification jobSpec = job.getSpecification();
        SpecificationNode sn;
        sn = new SpecificationNode(WebcrawlerConfig.NODE_EXCLUDESCONTENTINDEX);
        sn.setValue(".*expired.*\n");
        jobSpec.addChild(jobSpec.getChildCount(), sn);
        jobManager.save(job);
    }

    private IOutputConnection createOutputConnector() throws ManifoldCFException {
        // Create a basic null output connection, and save it.
        IOutputConnection outputConn = outputConnectionManager.create();
        outputConn.setName("Null Connection");
        outputConn.setDescription("Null Connection");
        outputConn.setClassName("org.apache.manifoldcf.agents.tests.TestingOutputConnector");
        outputConn.setMaxConnections(10);
        // Now, save
        outputConnectionManager.save(outputConn);

        return outputConn;
    }

    private IRepositoryConnection createRepoConnector() throws ManifoldCFException {
        //TODO: This is a copy/paste: Could we have common method for creating test jobs???
        IRepositoryConnection repoConnection = repoConnectionManager.create();
        repoConnection.setName("Web Connection");
        repoConnection.setDescription("Web Connection");
        repoConnection.setClassName("org.apache.manifoldcf.crawler.connectors.webcrawler.WebcrawlerConnector");
        repoConnection.setMaxConnections(50);
        ConfigParams cp = repoConnection.getConfigParams();

        cp.setParameter(WebcrawlerConfig.PARAMETER_EMAIL, "someone@somewhere.com");
        cp.setParameter(WebcrawlerConfig.PARAMETER_ROBOTSUSAGE, "none");

        repoConnectionManager.save(repoConnection);

        return repoConnection;
    }

    private void cleanupContentFilterJobs(IJobDescription job) throws ManifoldCFException, InterruptedException {
        repoConnectionManager.cleanUpHistoryData(WEB_CONNECTION);
        jobManager.deleteJob(job.getID());
        mcfInstance.waitJobDeletedNative(jobManager, job.getID(), MAX_WAIT_TIME);
    }

    private void runContentFilterJob(IJobDescription job) throws ManifoldCFException, InterruptedException {
        jobManager.manualStart(job.getID());

        try {
            mcfInstance.waitJobInactiveNative(jobManager, job.getID(), MAX_WAIT_TIME);
        } catch (ManifoldCFException e) {
            System.err.println("Halting for inspection");
            Thread.sleep(1000L);
            throw e;
        }
        // Check to be sure we actually processed the right number of documents.
        JobStatus status = jobManager.getStatus(job.getID());
        System.err.println("doc processed: " + status.getDocumentsProcessed() + " Job status: " + status.getStatus());
    }


    public static class ContentFilterTestServlet extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,
                IOException {
            response.setContentType("text/html; charset=utf-8");
            //response.setHeader("Keep-Alive", "timeout=5, max=100");
            response.setStatus(HttpServletResponse.SC_OK);
            String page = request.getParameter("page");
            page = (page == null) ? "unkown" : page;
            response.getWriter().println("<html><head><title></title></head><body><h1>You are now on page " + page + " </h1>");
            if ("1".equals(page)) {
                //Only page 1 will contain the keyword "expired"
                response.getWriter().println("<h1>Page 1 has expired. bye bye</h1>");
            }
            response.getWriter().println("</body>");
            response.getWriter().flush();
        }
    }

}