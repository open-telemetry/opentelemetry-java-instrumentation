/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.cameltest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CamelTestApplication {
  private CamelTestApplication() {}

  public static void main(String[] args) {
    SpringApplication.run(CamelTestApplication.class, args);
  }
}
