# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM eclipse-temurin:11-jre-jammy
LABEL maintainer="The Apache ManifoldCF Project"

ARG MCF_VERSION="2.27-SNAPSHOT"

ARG MCF_USER=manifoldcf
ARG MCF_USER_ID=100001

ARG MCF_GROUP=manifoldcf
ARG MCF_GROUP_ID=100002

ARG MCF_HOME=/usr/share/manifoldcf
ARG MCF_PORT=8345

RUN apt-get update && apt-get install -y iputils-ping && \
	apt-get install -y dnsutils

COPY dist/. ${MCF_HOME}/

#Below the specific COPY instructions to configure a distribution without propprietary artifacts
#COPY dist/connector-build.xml ${MCF_HOME}/connector-build.xml
#COPY dist/connectors.xml ${MCF_HOME}/connectors.xml
#COPY dist/DEPENDENCIES.txt ${MCF_HOME}/DEPENDENCIES.txt
#COPY dist/LICENSE.txt ${MCF_HOME}/LICENSE.txt
#COPY dist/README.txt ${MCF_HOME}/README.txt

#COPY dist/connector-common-lib ${MCF_HOME}/connector-common-lib
#COPY dist/connector-lib ${MCF_HOME}/connector-lib
#COPY dist/doc ${MCF_HOME}/doc
#COPY dist/example ${MCF_HOME}/example
#COPY dist/file-resources ${MCF_HOME}/file-resources
#COPY dist/lib ${MCF_HOME}/lib
#COPY dist/multiprocess-file-example ${MCF_HOME}/multiprocess-file-example
#COPY dist/multiprocess-zk-example ${MCF_HOME}/multiprocess-zk-example
#COPY dist/obfuscation-utility ${MCF_HOME}/obfuscation-utility
#COPY dist/plugins/solr ${MCF_HOME}/plugins/solr
#COPY dist/script-engine ${MCF_HOME}/script-engine
#COPY dist/test-lib ${MCF_HOME}/test-lib
#COPY dist/web/war ${MCF_HOME}/web/war


LABEL org.opencontainers.image.title="Apache ManifoldCF"
LABEL org.opencontainers.image.description="Apache ManifoldCF is a multi-repository crawler framework, with multiple connectors."
LABEL org.opencontainers.image.authors="The Apache ManifoldCF Project"
LABEL org.opencontainers.image.url="https://manifoldcf.apache.org"
LABEL org.opencontainers.image.source="https://github.com/apache/manifoldcf"
LABEL org.opencontainers.image.documentation="https://manifoldcf.apache.org/release/release-2.25/en_US/index.html"
LABEL org.opencontainers.image.version="${MCF_VERSION}"
LABEL org.opencontainers.image.licenses="Apache-2.0"

ENV MCF_USER="manifoldcf"
ENV MCF_USER_ID="100001"
ENV MCF_GROUP="manifoldcf"
ENV MCF_GROUP_ID="100002"
ENV MCF_PORT="8345"

RUN set -ex; \
    groupadd -r --gid "$MCF_GROUP_ID" "$MCF_GROUP"; \
    useradd -r --uid "$MCF_USER_ID" --gid "$MCF_GROUP_ID" "$MCF_USER"

RUN chown ${MCF_USER}:${MCF_USER} -R ${MCF_HOME}
RUN chmod +x ${MCF_HOME}/example/start.sh

USER ${MCF_USER}
EXPOSE ${MCF_PORT}
WORKDIR ${MCF_HOME}/example
CMD ["./start.sh"]