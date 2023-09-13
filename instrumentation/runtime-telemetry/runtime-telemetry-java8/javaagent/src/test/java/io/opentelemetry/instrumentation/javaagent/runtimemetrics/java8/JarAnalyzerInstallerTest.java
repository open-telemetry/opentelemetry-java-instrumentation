/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JarAnalyzerInstallerTest {

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
                  return "package".equals(attributes.get(AttributeKey.stringKey("event.domain")))
                      && "info".equals(attributes.get(AttributeKey.stringKey("event.name")));
                })
            .collect(toList());
    assertThat(events)
        .hasSizeGreaterThan(0)
        .allSatisfy(
            logRecord ->
                assertThat(logRecord.getAttributes())
                    .containsEntry("package.type", "jar")
                    .containsEntry("package.checksum_algorithm", "SHA1")
                    .hasEntrySatisfying(
                        AttributeKey.stringKey("package.checksum"),
                        value -> assertThat(value).isNotNull())
                    .hasEntrySatisfying(
                        AttributeKey.stringKey("package.path"),
                        value -> assertThat(value).isNotNull())
                    .satisfies(
                        attributes -> {
                          String packageName =
                              attributes.get(AttributeKey.stringKey("package.name"));
                          if (packageName != null) {
                            assertThat(packageName).matches(".*:.*");
                          }
                          String packageVersion =
                              attributes.get(AttributeKey.stringKey("package.version"));
                          if (packageVersion != null) {
                            assertThat(packageVersion).matches(".*\\..*");
                          }
                        }));
  }
}
