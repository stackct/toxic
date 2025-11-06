# --- Build
FROM ubuntu:latest as build
WORKDIR /src
ARG GRADLE_VERSION=7.6

COPY . .

ENV PATH="/opt/gradle-${GRADLE_VERSION}/bin:${PATH}"
RUN apt-get update \
    && apt-get install -y curl unzip openjdk-17-jdk
# ES: This url appears to be dead at the emoment https://downloads.gradle-dn.com/distributions/gradle-${GRADLE_VERSION}-bin.zip
RUN curl -o gradle.zip https://downloads.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip \
    && unzip -d /opt gradle.zip \
    && rm gradle.zip
RUN gradle build && bin/toxic -doDir=toxic/tests -excludeTags=ignored

# --- Runtime
FROM alpine:latest as final
RUN apk update && apk upgrade && apk add bash openjdk17

RUN mkdir -p /opt/toxic
COPY --from=build /src/bin         /opt/toxic/bin
COPY --from=build /src/conf        /opt/toxic/conf
COPY --from=build /src/docs        /opt/toxic/docs
COPY --from=build /src/resources   /opt/toxic/resources
COPY --from=build /src/toxic       /opt/toxic/toxic
COPY --from=build /src/build/libs  /opt/toxic/build/libs

ENTRYPOINT ["/opt/toxic/bin/toxic"]
