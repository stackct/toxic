FROM alpine:latest
LABEL Description="Task Execution Engine"

# Set the locale
ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8
ENV DOCKER_BUCKET download.docker.com
ENV DOCKER_VERSION 17.12.0-ce
ENV DOCKER_SHA256 05ceec7fd937e1416e5dce12b0b6e1c655907d349d52574319a1e875077ccb79

RUN apk update && apk add bash curl docker git openjdk8 openssh openssl make \
    && docker -v \
    && addgroup -g 2000 toxic \
    && adduser -u 2000 -G toxic -D toxic \ 
    && adduser toxic docker

ARG CACHE_ITERATION=0

COPY bin /opt/toxic/bin/
COPY conf /opt/toxic/conf/
COPY docs /opt/toxic/docs/
COPY lib /opt/toxic/lib/
COPY resources /opt/toxic/resources/
COPY gen/toxic.jar /opt/toxic/lib/

RUN sed -i 's/ref="console"/ref="rolling"/' /opt/toxic/conf/log4j.xml

VOLUME ["/data"]
EXPOSE 8001
USER toxic

ENTRYPOINT ["/opt/toxic/bin/toxic-ui", "-j", "/data"]
CMD ["-s", "/conf/toxic-secure.properties", "-p", "toxic.properties"]
