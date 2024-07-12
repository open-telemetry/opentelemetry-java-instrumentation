/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(RuntimeHints.class)
public class OtelSpringStarterSmokeTestApplication {

  public OtelSpringStarterSmokeTestApplication() {}

  public static void main(String[] args) {
    SpringApplication.run(OtelSpringStarterSmokeTestApplication.class);
  }
}
