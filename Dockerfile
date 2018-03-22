FROM alpine:latest
LABEL Description="Task Execution Engine"

ARG DIST_DIR_NAME

COPY ${DIST_DIR_NAME} /opt/toxic

RUN apk update && apk add bash curl docker git jq openjdk8 openssh openssl make \
    && docker -v \
    && addgroup -g 2000 toxic \
    && adduser -u 2000 -G toxic -D toxic \ 
    && adduser toxic docker

RUN curl https://kubernetes-helm.storage.googleapis.com/helm-v2.8.2-linux-amd64.tar.gz -o /tmp/helm.tar.gz \
	&& tar -zxvf /tmp/helm.tar.gz -C /tmp \
	&& mv /tmp/linux-amd64/helm /usr/local/bin/helm \
	&& rm -rf /tmp/linux-amd64 \
	&& rm /tmp/helm.tar.gz

RUN curl -LO https://storage.googleapis.com/kubernetes-release/release/$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)/bin/linux/amd64/kubectl \
	&& chmod +x ./kubectl \
	&& mv ./kubectl /usr/local/bin/kubectl

RUN sed -i 's/ref="console"/ref="rolling"/' /opt/toxic/conf/log4j.xml

VOLUME ["/data"]
EXPOSE 8001
USER toxic

ENV PATH="/opt/toxic/bin:${PATH}"

ENTRYPOINT ["/opt/toxic/bin/toxic-ui", "-j", "/data"]
CMD ["-s", "/conf/toxic-secure.properties", "-p", "toxic.properties"]
