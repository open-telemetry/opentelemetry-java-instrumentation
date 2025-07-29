# https://github.com/zulu-openjdk/zulu-openjdk/blob/master/ubuntu/8u412-8.78/Dockerfile
FROM ubuntu:noble-20250714@sha256:a08e551cb33850e4740772b38217fc1796a66da2506d312abe51acda354ff061

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

RUN apt-get -qq update && \
    apt-get -qq -y --no-install-recommends install software-properties-common locales curl tzdata unzip && \
    echo "en_US.UTF-8 UTF-8" >> /etc/locale.gen && \
    locale-gen en_US.UTF-8 && \
    curl -sLO https://cdn.azul.com/zulu/bin/zulu1.8.0_31-8.5.0.1-x86lx64.zip && \
    unzip zulu1.8.0_31-8.5.0.1-x86lx64.zip -d /opt && \
    apt-get -qq -y purge --auto-remove software-properties-common curl unzip && \
    rm zulu1.8.0_31-8.5.0.1-x86lx64.zip

ENV JAVA_HOME=/opt/zulu1.8.0_31-8.5.0.1-x86lx64
ENV PATH="${PATH}:/opt/zulu1.8.0_31-8.5.0.1-x86lx64/bin"
