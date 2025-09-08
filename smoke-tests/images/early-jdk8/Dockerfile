# https://github.com/zulu-openjdk/zulu-openjdk/blob/master/ubuntu/8u412-8.78/Dockerfile
FROM ubuntu:noble-20250805@sha256:9cbed754112939e914291337b5e554b07ad7c392491dba6daf25eef1332a22e8

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
