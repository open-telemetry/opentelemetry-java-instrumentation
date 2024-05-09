/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      SpringSmokeOtelConfiguration.class,
      AbstractKafkaSpringStarterSmokeTest.KafkaConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class KafkaSpringStarterSmokeTest extends AbstractJvmKafkaSpringStarterSmokeTest {}
