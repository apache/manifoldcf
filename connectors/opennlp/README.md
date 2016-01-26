# OpenNLP Transformation Connector for Apache ManifoldCF

OpenNLP connector extracts named entities(People, Locations and Organizations) from document content attaches metadata (ner_people, ner_locations and ner_organizations) to repository document.


## Building the Connector
---

```
git clone https://github.com/apache/manifoldcf.git
cd manifoldcf/
git checkout release-2.2-branch
mvn clean install 

git clone https://github.com/ChalithaUdara/OpenNLP-Manifold-Connector.git
cd OpenNLP-Manifold-Connector
mvn clean install -DskipTests=true
```

## Configure Connector with ManifoldCF
---

Copy mcf-opennlp-connector-2.2-jar-with-dependencies.jar to **$MANIFOLD_DIR/connectors-lib**
To configure connector with manifoldcf add following to **$MANIFOLD_DIR/connectors.xml** file.

```
<transformationconnector name="OpenNLP Extractor" class="org.apache.manifoldcf.agents.transformation.opennlp.OpenNlpExtractor" />
```
---

In order to extract named entities with OpenNLP, you first need to download the required OpenNLP models. Run **download-models** script to download models.

```
sh download-models.sh
```

This will download models to nlpmodels directory.

In manifoldcf job configuration, you need to configure paths to corresponding models.  



