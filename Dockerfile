FROM openjdk:11-jre
LABEL maintainer="The Apache ManifoldCF Project"

ARG MCF_USER=manifoldcf
ARG MCF_USER_ID=100001

ARG MCF_GROUP=manifoldcf
ARG MCF_GROUP_ID=100002

ARG APP_DIR=/usr/share/manifoldcf

RUN set -ex; \
  groupadd -r --gid "$MCF_GROUP_ID" "$MCF_GROUP"; \
  useradd -r --uid "$MCF_USER_ID" --gid "$MCF_GROUP_ID" "$MCF_USER"

COPY dist ${APP_DIR}

RUN chown ${MCF_USER}:${MCF_USER} -R ${APP_DIR}
RUN chmod +x ${APP_DIR}/example/start.sh

USER ${MCF_USER}
EXPOSE 8345
WORKDIR ${APP_DIR}/example
CMD ["./start.sh"]