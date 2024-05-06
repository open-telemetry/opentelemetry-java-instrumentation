/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * This test class enforces the order of the tests to make sure that {@link #shouldSendTelemetry()},
 * which asserts the telemetry data from the application startup, is executed first.
 *
 * <p>The exporters are not reset using {@link BeforeEach}, because it would prevent the telemetry
 * data from the application startup to be asserted.
 */
@SpringBootTest(
    classes = {
      OtelSpringStarterSmokeTestApplication.class,
      AbstractOtelSpringStarterSmokeTest.TestConfiguration.class,
      SpringSmokeOtelConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      // The headers are simply set here to make sure that headers can be parsed
      "otel.exporter.otlp.headers.c=3"
    })
class OtelSpringStarterSmokeTest extends AbstractOtelSpringStarterSmokeTest {}
