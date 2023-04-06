/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.internal.logging;

import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@SuppressWarnings("OtelPrivateConstructorForUtilityClass")
public class TestSpringApp {

  public static void main(String[] args) throws Exception {
    SpringApplication app = new SpringApplication(TestSpringApp.class);
    try (ConfigurableApplicationContext ignored = app.run()) {
      // pretend to do some work for a second
      TimeUnit.SECONDS.sleep(1);
      LoggerFactory.getLogger(TestSpringApp.class).info("Done!");
    }
  }
}
