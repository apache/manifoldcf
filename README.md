# Apache ManifoldCF™

[![Apache ManifoldCF Release](https://img.shields.io/badge/Release-2.31-blue.svg?logo=apache&logoColor=D22128)](https://manifoldcf.apache.org/)
[![Java Baseline](https://img.shields.io/badge/Java-25%2B-ED8B00?logo=openjdk&logoColor=white)](https://jdk.java.net/25/)
[![Concurrency](https://img.shields.io/badge/Concurrency-Virtual%20Threads%20%28Loom%29-43A047?logo=java&logoColor=white)](https://openjdk.org/projects/loom/)
[![Web Stack](https://img.shields.io/badge/Embedded-Jetty%2012.1-005C8A)](https://eclipse.dev/jetty/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Docker Pulls](https://img.shields.io/docker/pulls/apache/manifoldcf?logo=docker&logoColor=white&color=099CEC)](https://hub.docker.com/r/apache/manifoldcf)
[![Issue Tracker](https://img.shields.io/badge/JIRA-CONNECTORS-0052CC?logo=jira&logoColor=white)](https://issues.apache.org/jira/projects/CONNECTORS)
[![DeepWiki](https://img.shields.io/badge/DeepWiki-manifoldcf-6B46C1?logo=wikipedia&logoColor=white)](https://deepwiki.com/apache/manifoldcf)

An open-source enterprise content integration framework designed to crawl, extract, and index documents from disparate content repositories into search engines, vector databases, and AI pipelines while preserving native document Access Control Lists (ACLs).

---

## 🚀 Key Features

* **Security-Aware Ingestion:** Preserves native document Access Control Lists (ACLs) and security trimming to ensure users only search content they are authorized to view.
* **Extensive Repository Connectors:** Pre-built connectors for SharePoint, Google Drive, Amazon S3, CMIS, Web, File Systems, JDBC, Nuxeo, REST APIs, and more.
* **Flexible Output Targets:** Native output support for Apache Solr, OpenSearch, Elasticsearch, Qdrant, and custom RAG / vector storage pipelines.
* **High-Throughput Concurrency:** Powered by **Java 25 & Virtual Threads (Project Loom)** for ultra-scalable, non-blocking asynchronous document fetching.
* **Modern Web Stack:** Embedded **Jetty 12.1** application container.
* **Cloud Native:** Container-ready with official Docker images and Helm/Kubernetes integration support.

---

## 🛠️ Prerequisites & Building from Source

### Requirements
* **Java Development Kit (JDK):** Java 25 or higher (`java -version`).
* **Apache Ant:** Version 1.10.0 or higher (`ant -version`).
* **Apache Maven:** Version 3.8.0 or higher (for Maven builds).

### Building with Apache Ant
```bash
# Build complete release package
ant clean build

# Run single-process example
cd dist/example
java --enable-preview -jar start.jar
```

### Building with Apache Maven
```bash
mvn clean install
```

---

## 🐳 Docker Quickstart

Pull and run the official Apache ManifoldCF container:

```bash
docker pull apache/manifoldcf:2.31
docker run -d -p 8345:8345 --name manifoldcf apache/manifoldcf:2.31
```

Access the UI at `http://localhost:8345/mcf-crawler-ui`.

---

## 📂 Source Code Structure

* **`framework/`**: Core Apache ManifoldCF crawler framework and execution agents.
* **`connectors/`**: Repository, authority, transform, and output connector modules.
* **`distribution/`**: Packaging build targets and example setups.
* **`site/`**: Documentation and website source files.

---

## 🤝 Community & Support

* **Website:** [https://manifoldcf.apache.org/](https://manifoldcf.apache.org/)
* **Issue Tracker:** [Apache JIRA (CONNECTORS)](https://issues.apache.org/jira/projects/CONNECTORS)
* **Mailing Lists:**
  * Developer List: `dev@manifoldcf.apache.org`
  * User List: `user@manifoldcf.apache.org`

---

## 📄 Licensing

Apache ManifoldCF is licensed under the [Apache License 2.0](LICENSE.txt).
