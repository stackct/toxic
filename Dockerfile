FROM centos:7
LABEL Description="Task Execution Engine"

# Set the locale
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8
ENV DOCKER_BUCKET download.docker.com
ENV DOCKER_VERSION 17.12.0-ce
ENV DOCKER_SHA256 05ceec7fd937e1416e5dce12b0b6e1c655907d349d52574319a1e875077ccb79

RUN yum update -y && yum install -y epel-release openssl git mercurial net-tools nc openssh-clients java \
    && yum clean all \
    && curl -fSL "https://${DOCKER_BUCKET}/linux/static/stable/x86_64/docker-${DOCKER_VERSION}.tgz" -o docker.tgz \
    # && echo "${DOCKER_SHA256} *docker.tgz" | sha256sum -c - \
    && tar -xzvf docker.tgz \
    && mv docker/* /usr/local/bin/ \
    && rmdir docker \
    && rm docker.tgz \
    && docker -v

ARG CACHE_ITERATION=0

COPY bin /opt/toxic/bin/
COPY conf /opt/toxic/conf/
COPY docs /opt/toxic/docs/
COPY lib /opt/toxic/lib/
COPY resources /opt/toxic/resources/
COPY gen/toxic.jar /opt/toxic/lib/

VOLUME ["/data"]
VOLUME ["/conf"]
EXPOSE 8001

ENTRYPOINT ["/opt/toxic/bin/toxic-ui", "-j", "/data/jobs"]
CMD ["-s", "/conf/toxic-secure.properties", "-p", "toxic.properties"]
