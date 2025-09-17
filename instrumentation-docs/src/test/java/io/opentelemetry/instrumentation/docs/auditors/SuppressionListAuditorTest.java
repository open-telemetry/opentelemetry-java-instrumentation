/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.auditors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.docs.utils.FileManager;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SuppressionListAuditorTest {

  @Test
  void testPerformAudit_noMissingItems() throws IOException, InterruptedException {
    // Mock HTTP client to return disable list content
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockClient.send(any(), any())).thenReturn(mockResponse);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(createDisableListContent());

    // Mock file reading for instrumentation list
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
  void testPerformAudit_withMissingItems() throws IOException, InterruptedException {
    // Mock HTTP client to return disable list content
    HttpClient mockClient = mock(HttpClient.class);
    HttpResponse<String> mockResponse = mock(HttpResponse.class);
    when(mockClient.send(any(), any())).thenReturn(mockResponse);
    when(mockResponse.statusCode()).thenReturn(200);
    when(mockResponse.body()).thenReturn(createDisableListContentMissing());

    // Mock file reading for instrumentation list
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

  private String createDisableListContent() {
    return """
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

  private String createDisableListContentMissing() {
    return """
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

  private String createInstrumentationListContent() {
    return """
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
