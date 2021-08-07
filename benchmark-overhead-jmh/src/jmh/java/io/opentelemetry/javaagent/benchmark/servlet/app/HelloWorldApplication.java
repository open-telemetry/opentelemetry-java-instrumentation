/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.benchmark.servlet.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class HelloWorldApplication {

  private static volatile ConfigurableApplicationContext context;

  public static void main(String... args) {

    context = SpringApplication.run(HelloWorldApplication.class, args);
  }

  public static void stop() {
    context.stop();
  }
}
