/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.auditors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.docs.utils.FileManager;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked") // to allow mocking of generic HttpResponse
class SuppressionListAuditorTest {

  @Test
  void testPerformAuditWithNoMissingItems() throws IOException, InterruptedException {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
        .thenReturn(mockResponse);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(createDisableListContent());

    try (MockedStatic<FileManager> fileManagerMock = Mockito.mockStatic(FileManager.class)) {
      fileManagerMock
          .when(() -> FileManager.readFileToString(any()))
          .thenReturn(createInstrumentationListContent());

      SuppressionListAuditor auditor = new SuppressionListAuditor();
      Optional<String> result = auditor.performAudit(mockClient);

      assertThat(result).isEmpty();
    }
  }

  @Test
  void testPerformAuditWithMissingItems() throws IOException, InterruptedException {
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
        .thenReturn(mockResponse);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(createDisableListContentMissing());

    try (MockedStatic<FileManager> fileManagerMock = Mockito.mockStatic(FileManager.class)) {
      fileManagerMock
          .when(() -> FileManager.readFileToString(any()))
          .thenReturn(createInstrumentationListContent());

      SuppressionListAuditor auditor = new SuppressionListAuditor();
      Optional<String> result = auditor.performAudit(mockClient);

      assertThat(result).isPresent();
      assertThat(result.get()).contains("Missing Disable List (1 item(s) missing):");
      assertThat(result.get()).contains("- activej-http");
    }
  }

  @Test
  void testGetAuditorName() {
    SuppressionListAuditor auditor = new SuppressionListAuditor();
    assertThat(auditor.getAuditorName()).isEqualTo("Suppression List Auditor");
  }

  @Test
  void testParseDocumentationDisabledList() {
    String testFile =
"""
## Enable manual instrumentation only

You can suppress all auto instrumentations but have support for manual
instrumentation with `@WithSpan` and normal API interactions by using
`-Dotel.instrumentation.common.default-enabled=false -Dotel.instrumentation.opentelemetry-api.enabled=true -Dotel.instrumentation.opentelemetry-instrumentation-annotations.enabled=true`

## Suppressing specific agent instrumentation

You can suppress agent instrumentation of specific libraries.

{{% config_option name="otel.instrumentation.[name].enabled" %}} Set to `false`
to suppress agent instrumentation of specific libraries, where [name] is the
corresponding instrumentation name: {{% /config_option %}}

| Library/Framework                                | Instrumentation name                        |
| ------------------------------------------------ | ------------------------------------------- |
| Additional methods tracing                       | `methods`                                   |
| Additional tracing annotations                   | `external-annotations`                      |
| Akka Actor                                       | `akka-actor`                                |
| Akka HTTP                                        | `akka-http`                                 |
| Apache Axis2                                     | `axis2`                                     |
| Apache Camel                                     | `camel`                                     |
""";

    var result = SuppressionListAuditor.parseDocumentationDisabledList(testFile);
    assertThat(result).hasSize(6);
    assertThat(result)
        .containsExactlyInAnyOrder(
            "methods", "external-annotations", "akka-actor", "akka-http", "axis2", "camel");
  }

  @Test
  void testParseInstrumentationList() {
    String testList =
"""
libraries:
  activej:
  - name: activej-http-6.0
    description: This instrumentation enables SERVER spans and metrics for the ActiveJ
      HTTP server.
    source_path: instrumentation/activej-http-6.0
    minimum_java_version: 17
    scope:
      name: io.opentelemetry.activej-http-6.0
    target_versions:
      javaagent:
      - io.activej:activej-http:[6.0,)
  akka:
  - name: akka-actor-2.3
    source_path: instrumentation/akka/akka-actor-2.3
    scope:
      name: io.opentelemetry.akka-actor-2.3
    target_versions:
      javaagent:
      - com.typesafe.akka:akka-actor_2.11:[2.3,)
      - com.typesafe.akka:akka-actor_2.12:[2.3,)
      - com.typesafe.akka:akka-actor_2.13:[2.3,)
  - name: akka-actor-fork-join-2.5
    source_path: instrumentation/akka/akka-actor-fork-join-2.5
    scope:
      name: io.opentelemetry.akka-actor-fork-join-2.5
    target_versions:
      javaagent:
      - com.typesafe.akka:akka-actor_2.12:[2.5,2.6)
      - com.typesafe.akka:akka-actor_2.13:[2.5.23,2.6)
      - com.typesafe.akka:akka-actor_2.11:[2.5,)
  - name: akka-http-10.0
    description: This instrumentation enables CLIENT and SERVER spans and metrics
      for the Akka HTTP client and server.
    source_path: instrumentation/akka/akka-http-10.0
    scope:
      name: io.opentelemetry.akka-http-10.0
""";
    var result = SuppressionListAuditor.parseInstrumentationList(testList);

    assertThat(result).hasSize(4);
    assertThat(result)
        .containsExactlyInAnyOrder(
            "activej-http-6.0", "akka-actor-2.3", "akka-actor-fork-join-2.5", "akka-http-10.0");
  }

  @Test
  void testIdentifyMissingItems() {
    var documentationDisabledList = List.of("methods", "akka-actor", "akka-http");
    var instrumentationList =
        List.of(
            "methods",
            "akka-actor-2.3",
            "activej-http-6.0",
            "akka-actor-fork-join-2.5",
            "camel-2.20");

    var missingItems =
        SuppressionListAuditor.identifyMissingItems(documentationDisabledList, instrumentationList);
    assertThat(missingItems).hasSize(2);
    assertThat(missingItems).containsExactlyInAnyOrder("camel", "activej-http");
  }

  @Test
  void testIdentifyMissingItemsWithHyphenatedMatch() {
    var documentationDisabledList = List.of("clickhouse");
    var instrumentationList = List.of("clickhouse-client-0.5");

    var missingItems =
        SuppressionListAuditor.identifyMissingItems(documentationDisabledList, instrumentationList);
    assertThat(missingItems).isEmpty();
  }

  private static String createDisableListContent() {
    return
"""
## Enable manual instrumentation only

You can suppress all auto instrumentations but have support for manual
instrumentation with `@WithSpan` and normal API interactions by using
`-Dotel.instrumentation.common.default-enabled=false -Dotel.instrumentation.opentelemetry-api.enabled=true -Dotel.instrumentation.opentelemetry-instrumentation-annotations.enabled=true`

## Suppressing specific agent instrumentation

You can suppress agent instrumentation of specific libraries.

{{% config_option name="otel.instrumentation.[name].enabled" %}} Set to `false`
to suppress agent instrumentation of specific libraries, where [name] is the
corresponding instrumentation name: {{% /config_option %}}

| Library/Framework                                | Instrumentation name                        |
| ------------------------------------------------ | ------------------------------------------- |
| Additional methods tracing                       | `methods`                                   |
| ActiveJ                                          | `activej-http`                              |
| Akka Actor                                       | `akka-actor`                                |
| Akka HTTP                                        | `akka-http`                                 |
""";
  }

  private static String createDisableListContentMissing() {
    return
"""
## Enable manual instrumentation only

You can suppress all auto instrumentations but have support for manual
instrumentation with `@WithSpan` and normal API interactions by using
`-Dotel.instrumentation.common.default-enabled=false -Dotel.instrumentation.opentelemetry-api.enabled=true -Dotel.instrumentation.opentelemetry-instrumentation-annotations.enabled=true`

## Suppressing specific agent instrumentation

You can suppress agent instrumentation of specific libraries.

{{% config_option name="otel.instrumentation.[name].enabled" %}} Set to `false`
to suppress agent instrumentation of specific libraries, where [name] is the
corresponding instrumentation name: {{% /config_option %}}

| Library/Framework                                | Instrumentation name                        |
| ------------------------------------------------ | ------------------------------------------- |
| Additional methods tracing                       | `methods`                                   |
| Akka Actor                                       | `akka-actor`                                |
| Akka HTTP                                        | `akka-http`                                 |
""";
  }

  private static String createInstrumentationListContent() {
    return
"""
libraries:
  activej:
  - name: activej-http-6.0
    description: This instrumentation enables SERVER spans and metrics for the ActiveJ
      HTTP server.
    source_path: instrumentation/activej-http-6.0
    minimum_java_version: 17
    scope:
      name: io.opentelemetry.activej-http-6.0
    target_versions:
      javaagent:
      - io.activej:activej-http:[6.0,)
  akka:
  - name: akka-actor-2.3
    source_path: instrumentation/akka/akka-actor-2.3
    scope:
      name: io.opentelemetry.akka-actor-2.3
    target_versions:
      javaagent:
      - com.typesafe.akka:akka-actor_2.11:[2.3,)
      - com.typesafe.akka:akka-actor_2.12:[2.3,)
      - com.typesafe.akka:akka-actor_2.13:[2.3,)
  - name: akka-http-10.0
    description: This instrumentation enables CLIENT and SERVER spans and metrics
      for the Akka HTTP client and server.
    source_path: instrumentation/akka/akka-http-10.0
    scope:
      name: io.opentelemetry.akka-http-10.0
""";
  }
}
