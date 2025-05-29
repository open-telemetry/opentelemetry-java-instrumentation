/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.junit.jupiter.api.condition.DisabledInNativeImage;

@DisabledInNativeImage // See GraalVmNativeMongodbSpringStarterSmokeTest for the GraalVM native test
public class MongoSpringStarterSmokeTest extends AbstractJvmMongodbSpringStarterSmokeTest {}
