/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.opentelemetry.instrumentation.docs.internal.EmittedEvents;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("NullAway")
class EventParserTest {

  private static InstrumentationModule module(String instrumentationName, String scopeName) {
    InstrumentationModule module =
        new InstrumentationModule.Builder(instrumentationName).srcPath("").build();
    module.setScopeInfo(InstrumentationScopeInfo.create(scopeName));
    return module;
  }

  @Test
  void getEventsFiltersOutOtherScopes(@TempDir Path tempDir) throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));

    Files.writeString(
        telemetryDir.resolve("events-1.yaml"),
        """
        when: default
        events_by_scope:
          - scope: io.opentelemetry.openai-java-1.1
            events:
              - name: gen_ai.choice
                attributes:
                  - name: gen_ai.provider.name
                    type: STRING
          - scope: io.opentelemetry.sdk.metrics
            events:
              - name: some.sdk.event
                attributes:
          - scope: test
            events:
              - name: test.event
                attributes:
      """);

    Map<String, List<EmittedEvents.Event>> result =
        EventParser.getEvents(
            module("openai-java-1.1", "io.opentelemetry.openai-java-1.1"),
            new FileManager(tempDir));

    assertThat(result.get("default"))
        .extracting(EmittedEvents.Event::getName)
        .containsExactly("gen_ai.choice");
  }

  @Test
  void getEventsExcludesTestAttributesAndKeepsSeverity(@TempDir Path tempDir) throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));

    Files.writeString(
        telemetryDir.resolve("events-1.yaml"),
        """
        when: otel.semconv.exception.signal.preview=logs
        events_by_scope:
          - scope: io.opentelemetry.grpc-1.6
            events:
              - name: rpc.client.operation.exception
                severity: WARN
                attributes:
                  - name: exception.type
                    type: STRING
                  - name: test-baggage-key-1
                    type: STRING
                  - name: x-test-request
                    type: STRING_ARRAY
      """);

    Map<String, List<EmittedEvents.Event>> result =
        EventParser.getEvents(
            module("grpc-1.6", "io.opentelemetry.grpc-1.6"), new FileManager(tempDir));

    List<EmittedEvents.Event> events = result.get("otel.semconv.exception.signal.preview=logs");
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getSeverity()).isEqualTo("WARN");
    assertThat(events.get(0).getAttributes())
        .extracting(TelemetryAttribute::getName)
        .containsExactly("exception.type");
  }

  @Test
  void getEventsIncludesAllowListedScopes(@TempDir Path tempDir) throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));

    // armeria-grpc emits through the grpc-1.6 instrumenter, which is allow-listed for it
    Files.writeString(
        telemetryDir.resolve("events-1.yaml"),
        """
        when: default
        events_by_scope:
          - scope: io.opentelemetry.grpc-1.6
            events:
              - name: rpc.client.operation.exception
                severity: WARN
                attributes:
                  - name: exception.type
                    type: STRING
      """);

    Map<String, List<EmittedEvents.Event>> result =
        EventParser.getEvents(
            module("armeria-grpc-1.14", "io.opentelemetry.armeria-grpc-1.14"),
            new FileManager(tempDir));

    assertThat(result.get("default"))
        .extracting(EmittedEvents.Event::getName)
        .containsExactly("rpc.client.operation.exception");
  }

  @Test
  void getEventsKeepsBothSeveritiesOfTheSameEvent(@TempDir Path tempDir) throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));

    // sofa-rpc builds a client and a server instrumenter under one scope and keeps the default
    // exception event, so it emits that event at WARN and at ERROR under a single scope.
    Files.writeString(
        telemetryDir.resolve("events-1.yaml"),
        """
        when: otel.semconv.exception.signal.preview=logs
        events_by_scope:
          - scope: io.opentelemetry.sofa-rpc-5.4
            events:
              - name: exception
                severity: WARN
                attributes:
                  - name: exception.type
                    type: STRING
              - name: exception
                severity: ERROR
                attributes:
                  - name: rpc.method
                    type: STRING
      """);

    Map<String, List<EmittedEvents.Event>> result =
        EventParser.getEvents(
            module("sofa-rpc-5.4", "io.opentelemetry.sofa-rpc-5.4"), new FileManager(tempDir));

    List<EmittedEvents.Event> events = result.get("otel.semconv.exception.signal.preview=logs");
    assertThat(events)
        .extracting(EmittedEvents.Event::getName, EmittedEvents.Event::getSeverity)
        .containsExactly(tuple("exception", "ERROR"), tuple("exception", "WARN"));
    assertThat(events.get(0).getAttributes())
        .extracting(TelemetryAttribute::getName)
        .containsExactly("rpc.method");
    assertThat(events.get(1).getAttributes())
        .extracting(TelemetryAttribute::getName)
        .containsExactly("exception.type");
  }

  @Test
  void getEventsReturnsEmptyWhenNoEventFiles(@TempDir Path tempDir) throws IOException {
    Map<String, List<EmittedEvents.Event>> result =
        EventParser.getEvents(
            module("openai-java-1.1", "io.opentelemetry.openai-java-1.1"),
            new FileManager(tempDir));
    assertThat(result).isEmpty();
  }
}
