/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JarAnalyzerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void jarAnalyzerEnabled() throws InterruptedException {
    Thread.sleep(5000);

    List<LogRecordData> logRecordData = testing.logRecords();
    List<LogRecordData> events =
        logRecordData.stream()
            .filter(
                record -> {
                  Attributes attributes = record.getAttributes();
                  return "jvm".equals(attributes.get(AttributeKey.stringKey("event.domain")))
                      && "info".equals(attributes.get(AttributeKey.stringKey("event.name")));
                })
            .peek(logRecord -> System.out.println(logRecord.getAttributes()))
            .collect(Collectors.toList());
    assertThat(events)
        .hasSizeGreaterThan(0)
        .allSatisfy(
            logRecord -> {
              Attributes attributes = logRecord.getAttributes();
              assertThat(attributes.get(AttributeKey.stringKey("package.type"))).isEqualTo("jar");
              assertThat(attributes.get(AttributeKey.stringKey("package.checksum"))).isNotNull();
              assertThat(attributes.get(AttributeKey.stringKey("package.path"))).isNotNull();
              String packageName = attributes.get(AttributeKey.stringKey("package.name"));
              if (packageName != null) {
                assertThat(packageName).matches(".*:.*");
              }
              String packageVersion = attributes.get(AttributeKey.stringKey("package.version"));
              if (packageVersion != null) {
                assertThat(packageVersion).matches(".*\\..*");
              }
              String packageDescription =
                  attributes.get(AttributeKey.stringKey("package.description"));
              if (packageDescription != null) {
                assertThat(packageDescription).isNotEmpty();
              }
            });
  }
}
