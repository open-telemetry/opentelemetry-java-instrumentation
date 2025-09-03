/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocSynchronizationTest {

  @Test
  void testGetDocumentationInstrumentationList() {
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

    List<String> result = DocSynchronization.parseDocumentationDisabledList(testFile);
    assertThat(result.size()).isEqualTo(6);
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
    telemetry:
    - when: default
      metrics:
      - name: http.server.request.duration
        description: Duration of HTTP server requests.
        type: HISTOGRAM
        unit: s
        attributes:
        - name: http.request.method
          type: STRING
        - name: http.response.status_code
          type: LONG
        - name: network.protocol.version
          type: STRING
        - name: url.scheme
          type: STRING
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
    List<String> result = DocSynchronization.parseInstrumentationList(testList);

    assertThat(result.size()).isEqualTo(4);
    assertThat(result)
        .containsExactlyInAnyOrder(
            "activej-http-6.0", "akka-actor-2.3", "akka-actor-fork-join-2.5", "akka-http-10.0");
  }

  @Test
  void identifyMissingItems() {
    List<String> documentationDisabledList = List.of("methods", "akka-actor", "akka-http");

    List<String> instrumentationList =
        List.of(
            "methods",
            "akka-actor-2.3",
            "activej-http-6.0",
            "akka-actor-fork-join-2.5",
            "camel-2.20");

    List<String> missingItems =
        DocSynchronization.identifyMissingItems(documentationDisabledList, instrumentationList);
    assertThat(missingItems.size()).isEqualTo(2);
    assertThat(missingItems).containsExactlyInAnyOrder("camel", "activej-http");
  }

  @Test
  void testIdentifyMissingItemsWithHyphenatedMatch() {
    List<String> documentationDisabledList = List.of("clickhouse");
    List<String> instrumentationList = List.of("clickhouse-client-0.5");

    List<String> missingItems =
        DocSynchronization.identifyMissingItems(documentationDisabledList, instrumentationList);
    assertThat(missingItems).isEmpty();
  }
}
