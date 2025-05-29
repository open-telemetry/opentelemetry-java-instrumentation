/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.data.internal.ExtendedLogRecordData;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

class JarAnalyzerInstallerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  @SuppressWarnings("ReturnValueIgnored")
  void jarAnalyzerEnabled() throws InterruptedException {
    // We clear exported data before running tests. Here we load a class from testcontainers with
    // the assumption that no testcontainers classes have been loaded yet, and we'll have at least
    // the testcontainers jar show up in jar analyzer events.
    GenericContainer.class.getName();

    List<LogRecordData> events =
        Awaitility.await()
            .until(
                () ->
                    testing.logRecords().stream()
                        .filter(
                            record ->
                                "package.info"
                                    .equals(((ExtendedLogRecordData) record).getEventName()))
                        .collect(toList()),
                (eventList) -> !eventList.isEmpty());

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
