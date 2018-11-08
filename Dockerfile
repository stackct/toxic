FROM alpine:latest
LABEL Description="Task Execution Engine"

ARG DIST_DIR_NAME
ARG K8S_VERSION=v1.10.5

COPY ${DIST_DIR_NAME} /opt/toxic

# TODO: Move this to a multi-stage build
RUN apk update && apk add bash curl docker gcc git jq make openjdk8 openssh openssl openssl-dev tar python3 \
    && apk add --virtual=build libffi-dev musl-dev python3-dev \
    && pip3 install --upgrade pip \
    && pip3 install cffi \
    && pip3 install azure-cli \
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

# Install dotnet: This is required to perform the init steps for converting stack -> webapi.
# This may not be needed once webapi is fully converted
RUN apk add libcurl libgcc krb5-libs icu-libs libssl1.0 libstdc++ libunwind libuuid libintl zlib
RUN curl https://raw.githubusercontent.com/dotnet/cli/master/scripts/obtain/dotnet-install.sh | bash \
     && mv /root/.dotnet /home/toxic \
     && chown -R toxic:toxic /home/toxic/.dotnet \
     && ln -s /home/toxic/.dotnet/dotnet /usr/bin/dotnet \
     && chmod a+x /usr/bin/dotnet \
     && dotnet --info

RUN sed -i 's/ref="console"/ref="rolling"/' /opt/toxic/conf/log4j.xml

VOLUME ["/data"]
EXPOSE 8001
USER toxic

ENV PATH="/opt/toxic/bin:${PATH}"

ENTRYPOINT ["/opt/toxic/bin/toxic-ui", "-j", "/data"]
CMD ["-s", "/conf/toxic-secure.properties", "-p", "toxic.properties"]
