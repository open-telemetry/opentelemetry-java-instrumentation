/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.opentelemetry.instrumentation.docs.internal.EmittedEvents;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("NullAway")
class EmittedEventParserTest {

  @Test
  void getEventsFromFilesCombinesFilesCorrectly(@TempDir Path tempDir) throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));

    String file1Content =
        """
        when: default
        events_by_scope:
          - scope: io.opentelemetry.openai-java-1.1
            events:
              - name: gen_ai.user.message
                attributes:
                  - name: gen_ai.provider.name
                    type: STRING
      """;

    String file2Content =
        """
        when: default
        events_by_scope:
          - scope: io.opentelemetry.openai-java-1.1
            events:
              - name: gen_ai.choice
                attributes:
                  - name: gen_ai.provider.name
                    type: STRING
              - name: gen_ai.user.message
                attributes:
                  - name: gen_ai.system
                    type: STRING
      """;

    Files.writeString(telemetryDir.resolve("events-1.yaml"), file1Content);
    Files.writeString(telemetryDir.resolve("events-2.yaml"), file2Content);
    // duplicate contents to test deduplication
    Files.writeString(telemetryDir.resolve("events-3.yaml"), file2Content);

    Map<String, EmittedEvents> result = EmittedEventParser.getEventsByScopeFromFiles(tempDir, "");

    EmittedEvents events = result.get("default");
    assertThat(events.getEventsByScope()).hasSize(1);

    List<EmittedEvents.Event> openAiEvents = events.getEventsByScope().get(0).getEvents();
    // deduplicated by name across the three files
    assertThat(openAiEvents)
        .extracting(EmittedEvents.Event::getName)
        .containsExactlyInAnyOrder("gen_ai.user.message", "gen_ai.choice");

    EmittedEvents.Event userMessage =
        openAiEvents.stream()
            .filter(event -> event.getName().equals("gen_ai.user.message"))
            .findFirst()
            .orElse(null);
    // attributes are unioned across files
    assertThat(userMessage.getAttributes())
        .extracting(TelemetryAttribute::getName)
        .containsExactlyInAnyOrder("gen_ai.provider.name", "gen_ai.system");
  }

  @Test
  void getEventsFromFilesSeparatesWhenConditionsAndKeepsSeverity(@TempDir Path tempDir)
      throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));

    String defaultContent =
        """
        when: default
        events_by_scope:
          - scope: io.opentelemetry.jdbc
            events:
              - name: package.info
                attributes:
      """;

    String configuredContent =
        """
        when: otel.semconv.exception.signal.preview=logs
        events_by_scope:
          - scope: io.opentelemetry.jdbc
            events:
              - name: db.client.operation.exception
                severity: WARN
                attributes:
                  - name: exception.type
                    type: STRING
      """;

    Files.writeString(telemetryDir.resolve("events-1.yaml"), defaultContent);
    Files.writeString(telemetryDir.resolve("events-2.yaml"), configuredContent);

    Map<String, EmittedEvents> result = EmittedEventParser.getEventsByScopeFromFiles(tempDir, "");

    assertThat(result.keySet())
        .containsExactlyInAnyOrder("default", "otel.semconv.exception.signal.preview=logs");

    EmittedEvents.Event exceptionEvent =
        result
            .get("otel.semconv.exception.signal.preview=logs")
            .getEventsByScope()
            .get(0)
            .getEvents()
            .get(0);
    assertThat(exceptionEvent.getName()).isEqualTo("db.client.operation.exception");
    assertThat(exceptionEvent.getSeverity()).isEqualTo("WARN");

    EmittedEvents.Event packageInfo =
        result.get("default").getEventsByScope().get(0).getEvents().get(0);
    assertThat(packageInfo.getName()).isEqualTo("package.info");
    assertThat(packageInfo.getSeverity()).isNull();
  }

  @Test
  void getEventsFromFilesKeepsBothSeveritiesOfTheSameEvent(@TempDir Path tempDir)
      throws IOException {
    Path telemetryDir = Files.createDirectories(tempDir.resolve(".telemetry"));

    // A single scope can emit the same event at two severities: ERROR for server and consumer
    // operations, WARN for client and producer ones. sofa-rpc builds a client and a server
    // instrumenter under one scope and keeps the default exception event, so it does exactly that.
    String serverContent =
        """
        when: otel.semconv.exception.signal.preview=logs
        events_by_scope:
          - scope: io.opentelemetry.sofa-rpc-5.4
            events:
              - name: exception
                severity: ERROR
                attributes:
                  - name: rpc.method
                    type: STRING
      """;

    String clientContent =
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
      """;

    Files.writeString(telemetryDir.resolve("events-1.yaml"), serverContent);
    Files.writeString(telemetryDir.resolve("events-2.yaml"), clientContent);

    Map<String, EmittedEvents> result = EmittedEventParser.getEventsByScopeFromFiles(tempDir, "");

    List<EmittedEvents.Event> events =
        result
            .get("otel.semconv.exception.signal.preview=logs")
            .getEventsByScope()
            .get(0)
            .getEvents();

    // both shapes are kept, sorted by name then severity, and their attributes are not merged
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
  void getEventsFromFilesHandlesNonexistentDirectory(@TempDir Path tempDir) throws IOException {
    Map<String, EmittedEvents> result =
        EmittedEventParser.getEventsByScopeFromFiles(tempDir, "nonexistent");
    assertThat(result).isEmpty();
  }
}
