FROM alpine:latest
LABEL Description="Task Execution Engine"

# Set the locale
ENV LANGUAGE en_US:en
ENV LANG     en_US.UTF-8
ENV LC_ALL   en_US.UTF-8

ARG DIST_DIR_NAME
ARG K8S_VERSION=v1.15.2
ARG YQ_VERSION=2.4.0

COPY ${DIST_DIR_NAME} /opt/toxic

# TODO: Move this to a multi-stage build
RUN apk update && apk add bash curl docker git jq make npm nss openjdk8 openssh openssl openssl-dev tar python3 zip \
    && apk add --virtual=build gcc libffi-dev musl-dev python3-dev \
    && pip3 install --upgrade pip \
    && pip3 install cffi \
    && pip3 install azure-cli \
    && pip3 install awscli \
    && ln -s /usr/bin/python3 /usr/bin/python \
    && apk del --purge build \
    && docker -v \
    && addgroup -g 2000 toxic \
    && adduser -u 2000 -G toxic -D toxic \
    && adduser toxic docker

RUN curl https://storage.googleapis.com/kubernetes-helm/helm-v2.9.0-linux-amd64.tar.gz -o /tmp/helm.tar.gz \
	&& tar -zxvf /tmp/helm.tar.gz -C /tmp \
	&& mv /tmp/linux-amd64/helm /usr/local/bin/helm \
	&& rm -rf /tmp/linux-amd64 \
	&& rm /tmp/helm.tar.gz

RUN curl -LO https://storage.googleapis.com/kubernetes-release/release/${K8S_VERSION}/bin/linux/amd64/kubectl \
	&& chmod +x ./kubectl \
	&& mv ./kubectl /usr/local/bin/kubectl

RUN curl -LO https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/yq_linux_amd64 \
    && chmod +x ./yq_linux_amd64 \
    && mv ./yq_linux_amd64 /usr/local/bin/yq

RUN npm i -g redoc-cli

RUN curl -L https://aka.ms/downloadazcopy-v10-linux -o /tmp/azcopy.tgz \
    && tar -x --strip=1 -f /tmp/azcopy.tgz -C /usr/local/bin \
    && chown root:root /usr/local/bin/azcopy \
    && chmod a+x /usr/local/bin/azcopy \
    && apk add libc6-compat

RUN sed -i 's/ref="console"/ref="rolling"/' /opt/toxic/conf/log4j.xml

VOLUME ["/data"]
EXPOSE 8001
USER toxic


ENV PATH="/opt/toxic/bin:${PATH}"

ENTRYPOINT ["/opt/toxic/bin/toxic-ui", "-j", "/data"]
CMD ["-s", "/conf/toxic-secure.properties", "-p", "toxic.properties"]
