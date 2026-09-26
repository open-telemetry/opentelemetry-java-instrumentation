/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.sampler.internal.LinksBasedSamplerDeprecationCustomizerProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SamplerDeprecationAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withPropertyValues(
              "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none")
          .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class));

  @Test
  void warnsWhenStarterSelectsLinksBasedSampler() {
    TestHandler handler = new TestHandler();
    Logger logger =
        Logger.getLogger(LinksBasedSamplerDeprecationCustomizerProvider.class.getName());
    logger.addHandler(handler);
    try {
      contextRunner
          .withPropertyValues("otel.traces.sampler=linksbased_parentbased_always_on")
          .run(
              context -> {
                assertThat(context).hasBean("openTelemetry");
                assertThat(handler.records).hasSize(1);
                assertThat(handler.records.get(0).getLevel()).isEqualTo(WARNING);
                assertThat(handler.records.get(0).getMessage())
                    .contains("linksbased_parentbased_always_on", "there is no replacement");
              });
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void doesNotWarnWhenStarterSelectsAnotherSampler() {
    TestHandler handler = new TestHandler();
    Logger logger =
        Logger.getLogger(LinksBasedSamplerDeprecationCustomizerProvider.class.getName());
    logger.addHandler(handler);
    try {
      contextRunner
          .withPropertyValues("otel.traces.sampler=always_on")
          .run(
              context -> {
                assertThat(context).hasBean("openTelemetry");
                assertThat(handler.records).isEmpty();
              });
    } finally {
      logger.removeHandler(handler);
    }
  }

  private static final class TestHandler extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
