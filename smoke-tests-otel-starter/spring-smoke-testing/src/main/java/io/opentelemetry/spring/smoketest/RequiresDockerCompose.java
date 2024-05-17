/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@EnabledIfEnvironmentVariable(
    named = "DOCKER_COMPOSE_TEST",
    matches = "true",
    disabledReason =
        "Testcontainers does not work in some cases with GraalVM native images. "
            + "A container has to be started manually. "
            + "So, an environment variable is used to disable the test by default.")
public @interface RequiresDockerCompose {}
