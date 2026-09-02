# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Release: https://github.com/apache/geode/releases/tag/rel/v2.0.2
# Dockerfile source: https://github.com/apache/geode/blob/1685bb49297e2c8f1c0d436f11f93bfb6209686b/docker/Dockerfile

FROM bellsoft/liberica-openjdk-alpine:25@sha256:b728c08690506dac4ed7150ab40f406236d43f487c51a72f375e817f02d7cbde

RUN apk add --no-cache \
		bash \
		ncurses

ENV GEODE_GPG 5C3DA8FBB1052F4DF1DEB1EF62F7DA41B7D8F26C

ENV GEODE_HOME /geode
ENV PATH $PATH:$GEODE_HOME/bin

ENV GEODE_VERSION 2.0.2
ENV GEODE_SHA256 a1a875d5df88d80ae2953def1b0811ee8a466010a5b897682356740bdf19bb7c

RUN set -eux; \
	apk add --no-cache --virtual .fetch-deps \
		libressl \
		gnupg \
	; \
	for file in \
		"geode/$GEODE_VERSION/apache-geode-$GEODE_VERSION.tgz" \
		"geode/$GEODE_VERSION/apache-geode-$GEODE_VERSION.tgz.asc" \
	; do \
		target="$(basename "$file")"; \
		for url in \
			"https://downloads.apache.org/$file" \
			"https://archive.apache.org/dist/$file" \
		; do \
			if wget -O "$target" "$url"; then \
				break; \
			fi; \
		done; \
	done; \
	[ -s "apache-geode-$GEODE_VERSION.tgz" ]; \
	[ -s "apache-geode-$GEODE_VERSION.tgz.asc" ]; \
	echo "$GEODE_SHA256 *apache-geode-$GEODE_VERSION.tgz" | sha256sum -c -; \
	export GNUPGHOME="$(mktemp -d)"; \
	gpg --keyserver keyserver.ubuntu.com --recv-keys "$GEODE_GPG"; \
	gpg --batch --verify "apache-geode-$GEODE_VERSION.tgz.asc" "apache-geode-$GEODE_VERSION.tgz"; \
	rm -rf "$GNUPGHOME"; \
	mkdir /geode; \
	tar --extract \
		--file "apache-geode-$GEODE_VERSION.tgz" \
		--directory /geode \
		--strip-components 1 \
	; \
	rm -rf /geode/javadoc "apache-geode-$GEODE_VERSION.tgz" "apache-geode-$GEODE_VERSION.tgz.asc"; \
	apk del .fetch-deps; \
	gfsh version

EXPOSE 8080 10334 40404 1099 7070
VOLUME ["/data"]
CMD ["gfsh"]
