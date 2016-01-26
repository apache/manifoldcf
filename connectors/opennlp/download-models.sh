#!/bin/bash

MODELS_DIR=nlpmodels

if [ ! -d "$MODELS_DIR" ]; then
  echo “$MODELS_DIR does not exist…”
  echo “creating $MODELS_DIR …”
  mkdir -p ${MODELS_DIR}
fi

echo “downloading models…”
wget -O ${MODELS_DIR}/en-sent.bin http://opennlp.sourceforge.net/models-1.5/en-sent.bin
wget -O ${MODELS_DIR}/en-token.bin http://opennlp.sourceforge.net/models-1.5/en-token.bin
wget -O ${MODELS_DIR}/en-ner-person.bin http://opennlp.sourceforge.net/models-1.5/en-ner-person.bin
wget -O ${MODELS_DIR}/en-ner-location.bin http://opennlp.sourceforge.net/models-1.5/en-ner-location.bin
wget -O ${MODELS_DIR}/en-ner-organization.bin http://opennlp.sourceforge.net/models-1.5/en-ner-organization.bin
echo “downloading finished…”