#!/bin/sh

# Wait for MCF to start
until curl -s http://mcf:8345/mcf-api-service/json/repositoryconnections > /dev/null; do
  echo "Waiting for ManifoldCF..."
  sleep 5
done

echo "ManifoldCF is up. Configuring connections..."

# 1. Create CMIS Output Connection
curl -X PUT http://mcf:8345/mcf-api-service/json/outputconnections/CMIS%20Output \
  -H "Content-Type: application/json" \
  -d '{
    "outputconnection": {
      "name": "CMIS Output",
      "class_name": "org.apache.manifoldcf.agents.output.cmisoutput.CmisOutputConnector",
      "description": "CMIS Output Connection",
      "max_connections": "10",
      "configuration": {
        "_PARAMETER_": [
          { "_attribute_name": "binding", "_value_": "atom" },
          { "_attribute_name": "username", "_value_": "admin" },
          { "_attribute_name": "password", "_value_": "admin" },
          { "_attribute_name": "protocol", "_value_": "http" },
          { "_attribute_name": "server", "_value_": "cmis" },
          { "_attribute_name": "port", "_value_": "8080" },
          { "_attribute_name": "path", "_value_": "/atom11" },
          { "_attribute_name": "repositoryId", "_value_": "A1" },
          { "_attribute_name": "cmisQuery", "_value_": "SELECT * FROM cmis:folder WHERE cmis:name='\''Apache ManifoldCF'\''" }
        ]
      }
    }
  }'

# 2. Create File System Repository Connection
curl -X PUT http://mcf:8345/mcf-api-service/json/repositoryconnections/File%20System \
  -H "Content-Type: application/json" \
  -d '{
    "repositoryconnection": {
      "name": "File System",
      "class_name": "org.apache.manifoldcf.crawler.connectors.filesystem.FileConnector",
      "description": "File System Connection",
      "max_connections": "10"
    }
  }'

# 3. Create Job
curl -X POST http://mcf:8345/mcf-api-service/json/jobs \
  -H "Content-Type: application/json" \
  -d '{
    "job": {
      "description": "File to CMIS Job",
      "repository_connection": "File System",
      "pipelinestage": [
        {
          "stage_id": "0",
          "stage_isoutput": "true",
          "stage_connectionname": "CMIS Output",
          "stage_description": "Output to CMIS"
        }
      ],
      "run_mode": "scan once",
      "start_mode": "manual",
      "priority": "5",
      "hopcount_mode": "never delete",
      "document_specification": {
        "startpoint": [
          {
            "_attribute_path": "/usr/share/manifoldcf/test-data",
            "include": [
              {
                "_attribute_type": "file",
                "_attribute_match": "*"
              }
            ]
          }
        ]
      }
    }
  }'

echo "MCF Setup complete!"
