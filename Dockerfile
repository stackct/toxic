FROM alpine:latest
LABEL Description="Task Execution Engine"

ARG DIST_DIR_NAME

COPY ${DIST_DIR_NAME} /opt/toxic

RUN apk update && apk add bash curl docker git openjdk8 openssh openssl make \
    && docker -v \
    && addgroup -g 2000 toxic \
    && adduser -u 2000 -G toxic -D toxic \ 
    && adduser toxic docker

RUN sed -i 's/ref="console"/ref="rolling"/' /opt/toxic/conf/log4j.xml

VOLUME ["/data"]
EXPOSE 8001
USER toxic

ENTRYPOINT ["/opt/toxic/bin/toxic-ui", "-j", "/data"]
CMD ["-s", "/conf/toxic-secure.properties", "-p", "toxic.properties"]
