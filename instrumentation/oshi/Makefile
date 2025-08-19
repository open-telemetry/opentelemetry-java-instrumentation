# From where to resolve the containers (e.g. "otel/weaver").
WEAVER_CONTAINER_REPOSITORY=docker.io
# Versioned, non-qualified references to containers used in this Makefile.
# These are parsed from dependencies.Dockerfile so dependabot will autoupdate
# the versions of docker files we use.
VERSIONED_WEAVER_CONTAINER_NO_REPO=$(shell cat weaver.Dockerfile | awk '$$4=="weaver" {print $$2}')
# Versioned, non-qualified references to containers used in this Makefile.
WEAVER_CONTAINER=$(WEAVER_CONTAINER_REPOSITORY)/$(VERSIONED_WEAVER_CONTAINER_NO_REPO)

# Next - we want to run docker as our local file user, so generated code is not
# owned by root, and we don't give unnecessary access.
#
# Determine if "docker" is actually podman
DOCKER_VERSION_OUTPUT := $(shell docker --version 2>&1)
DOCKER_IS_PODMAN := $(shell echo $(DOCKER_VERSION_OUTPUT) | grep -c podman)
ifeq ($(DOCKER_IS_PODMAN),0)
    DOCKER_COMMAND := docker
else
    DOCKER_COMMAND := podman
endif
DOCKER_RUN=$(DOCKER_COMMAND) run
DOCKER_USER=$(shell id -u):$(shell id -g)
DOCKER_USER_IS_HOST_USER_ARG=-u $(DOCKER_USER)
ifeq ($(DOCKER_COMMAND),podman)
 # On podman, additional arguments are needed to make "-u" work
 # correctly with the host user ID and host group ID.
 #
 #      Error: OCI runtime error: crun: setgroups: Invalid argument
 DOCKER_USER_IS_HOST_USER_ARG=--userns=keep-id -u $(DOCKER_USER)
endif

.PHONY: generate-docs
generate-docs:
	mkdir -p docs
	$(DOCKER_RUN) --rm \
		$(DOCKER_USER_IS_HOST_USER_ARG) \
		--mount 'type=bind,source=$(PWD)/model,target=/home/weaver/model,readonly' \
		--mount 'type=bind,source=$(PWD)/templates,target=/home/weaver/templates,readonly' \
		--mount 'type=bind,source=$(PWD)/docs,target=/home/weaver/target' \
		${WEAVER_CONTAINER} registry generate \
		--registry=/home/weaver/model \
		markdown \
		--future \
		/home/weaver/target

.PHONY: check
check:
	$(DOCKER_RUN) --rm \
		$(DOCKER_USER_IS_HOST_USER_ARG) \
		--mount 'type=bind,source=$(PWD)/model,target=/home/weaver/model,readonly' \
		--mount 'type=bind,source=$(PWD)/templates,target=/home/weaver/templates,readonly' \
		--mount 'type=bind,source=$(PWD)/docs,target=/home/weaver/target' \
		${WEAVER_CONTAINER} registry check \
		--registry=/home/weaver/model

.PHONY: generate-java
generate-java:
	mkdir -p library/src/main/java/io/opentelemetry/oshi
	$(DOCKER_RUN) --rm \
		$(DOCKER_USER_IS_HOST_USER_ARG) \
		--mount 'type=bind,source=$(PWD)/model,target=/home/weaver/model,readonly' \
		--mount 'type=bind,source=$(PWD)/templates,target=/home/weaver/templates,readonly' \
		--mount 'type=bind,source=$(PWD)/library/src/main/java/io/opentelemetry/instrumentation/oshi,target=/home/weaver/target' \
		${WEAVER_CONTAINER} registry generate \
		--registry=/home/weaver/model \
		java \
		--future \
		/home/weaver/target

.PHONY: generate
generate: generate-docs generate-java
