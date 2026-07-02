package org.apache.manifoldcf.springboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ThreadContextFactory;
import org.apache.manifoldcf.crawler.system.ManifoldCF;
import org.apache.manifoldcf.agents.system.AgentsDaemon;
import org.apache.manifoldcf.apiservlet.APIServlet;
import org.apache.manifoldcf.authorityservlet.UserACLServlet;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class ManifoldCFSpringBootApplication {

    private AgentsDaemon agentsDaemon = null;
    private AgentsDaemonThread agentsDaemonThread = null;

    public static void main(String[] args) {
        SpringApplication.run(ManifoldCFSpringBootApplication.class, args);
    }

    @Bean
    public ServletRegistrationBean<APIServlet> apiServlet() {
        ServletRegistrationBean<APIServlet> registration = new ServletRegistrationBean<>(new APIServlet(), "/api/*");
        registration.setName("APIServlet");
        return registration;
    }

    @Bean
    public ServletRegistrationBean<UserACLServlet> authorityServlet() {
        ServletRegistrationBean<UserACLServlet> registration = new ServletRegistrationBean<>(new UserACLServlet(), "/UserACLs");
        registration.setName("UserACLServlet");
        return registration;
    }

    @PostConstruct
    public void init() throws Exception {
        String configFile = System.getProperty(ManifoldCF.lcfConfigFileProperty);
        if (configFile == null) {
            configFile = "./properties.xml";
            System.setProperty(ManifoldCF.lcfConfigFileProperty, configFile);
        }

        File f = new File(configFile);
        if (!f.exists()) {
            copyResource("/properties.xml", f);
        }
        
        File c = new File("./connectors.xml");
        if (!c.exists()) {
            copyResource("/connectors.xml", c);
        }

        if (System.getProperty("org.apache.manifoldcf.logconfigfile") == null) {
            System.setProperty("org.apache.manifoldcf.logconfigfile", "./logging.xml");
        }
        File l = new File("./logging.xml");
        if (!l.exists()) {
            copyResource("/logging.xml", l);
        }

        IThreadContext tc = ThreadContextFactory.make();
        ManifoldCF.initializeEnvironment(tc);
        ManifoldCF.createSystemDatabase(tc);
        ManifoldCF.installTables(tc);
        ManifoldCF.installSystemTables(tc);
        ManifoldCF.registerThisAgent(tc);
        ManifoldCF.reregisterAllConnectors(tc);

        // Start agents daemon
        AgentsDaemon.clearAgentsShutdownSignal(tc);
        agentsDaemon = new AgentsDaemon(ManifoldCF.getProcessID());
        agentsDaemonThread = new AgentsDaemonThread();
        agentsDaemonThread.start();
    }

    @PreDestroy
    public void destroy() {
        IThreadContext tc = ThreadContextFactory.make();
        try {
            if (agentsDaemonThread != null) {
                AgentsDaemon.assertAgentsShutdownSignal(tc);
                agentsDaemonThread.join();
                agentsDaemonThread = null;
            }
        } catch (InterruptedException e) {
            // Ignore
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            ManifoldCF.cleanUpEnvironment(tc);
        }
    }

    private class AgentsDaemonThread extends Thread {
        public AgentsDaemonThread() {
            setName("AgentsDaemonThread");
        }

        @Override
        public void run() {
            try {
                IThreadContext tc = ThreadContextFactory.make();
                agentsDaemon.runAgents(tc);
                agentsDaemon.stopAgents(tc);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void copyResource(String resourceName, File targetFile) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(resourceName);
             FileOutputStream os = new FileOutputStream(targetFile)) {
            if (is != null) {
                is.transferTo(os);
            }
        }
    }

    @RestController
    class StatusController {

        @GetMapping("/status")
        public StatusResponse getStatus() {
            // Using Java 21 Record and checking if current thread is virtual
            return new StatusResponse("ManifoldCF Spring Boot is running", Thread.currentThread().isVirtual());
        }
    }

    // Java 21 Record
    public record StatusResponse(String status, boolean virtualThreadsEnabled) {}
}
