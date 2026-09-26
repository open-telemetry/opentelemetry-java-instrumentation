/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import static io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames.expandDeprecatedNames;
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DeprecatedInstrumentationNamesTest {

  @AfterEach
  void tearDown() {
    AgentDistributionConfig.resetForTest();
  }

  @Test
  void warnsForBothConfiguredLegacyHibernateNames() {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean("otel.instrumentation.hibernate-procedure-call.enabled"))
        .thenReturn(false);
    when(config.getBoolean("otel.instrumentation.hibernate-procedure-call-4.3.enabled"))
        .thenReturn(true);
    AgentDistributionConfig.set(AgentDistributionConfig.fromConfigProperties(config));

    List<String> warnings = new ArrayList<>();
    List<LogRecord> records = new ArrayList<>();
    Logger logger = Logger.getLogger(DeprecatedInstrumentationNames.class.getName());
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            records.add(record);
            warnings.add(MessageFormat.format(record.getMessage(), record.getParameters()));
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    logger.addHandler(handler);
    try {
      assertThat(
              expandDeprecatedNames(
                  "hibernate-4.0",
                  "hibernate-4.0-procedure-call",
                  "hibernate|deprecated:hibernate-procedure-call",
                  "hibernate-4.0|deprecated:hibernate-procedure-call-4.3"))
          .containsExactly(
              "hibernate-4.0",
              "hibernate-4.0-procedure-call",
              "hibernate",
              "hibernate-procedure-call",
              "hibernate-4.0",
              "hibernate-procedure-call-4.3");
    } finally {
      logger.removeHandler(handler);
    }

    assertThat(warnings)
        .containsExactly(
            "otel.instrumentation.hibernate-procedure-call.enabled is deprecated; "
                + "use otel.instrumentation.hibernate.enabled instead.",
            "otel.instrumentation.hibernate-procedure-call-4.3.enabled is deprecated; "
                + "use otel.instrumentation.hibernate-4.0.enabled instead.");
    assertThat(records).extracting(LogRecord::getLevel).containsExactly(WARNING, WARNING);
  }
}
