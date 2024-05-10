/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.junit.jupiter.api.condition.DisabledInNativeImage;

@DisabledInNativeImage // See GraalVmNativeKafkaSpringStarterSmokeTest for the GraalVM native test
public class KafkaSpringStarterSmokeTest extends AbstractJvmKafkaSpringStarterSmokeTest {}
