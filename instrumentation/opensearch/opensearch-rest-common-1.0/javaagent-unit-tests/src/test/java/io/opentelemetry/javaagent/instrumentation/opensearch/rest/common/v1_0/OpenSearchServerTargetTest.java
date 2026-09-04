/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0.OpenSearchServerTarget.Endpoint;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OpenSearchServerTargetTest {

  @Test
  void noEndpointsHasNoTarget() {
    assertThat(OpenSearchServerTarget.of(null)).isNull();
    assertThat(OpenSearchServerTarget.of(emptyList())).isNull();
  }

  @Test
  void singleEndpointKeepsItsHostAndPort() {
    DbServerTarget target =
        OpenSearchServerTarget.of(singletonList(new Endpoint("search.example", 9200, "https")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("search.example");
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @ParameterizedTest
  @MethodSource("defaultPortCases")
  void singleEndpointOmitsItsDefaultPort(String scheme, int port) {
    DbServerTarget target =
        OpenSearchServerTarget.of(singletonList(new Endpoint("search.example", port, scheme)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("search.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleEndpointWithoutPortHasNoPort() {
    DbServerTarget target =
        OpenSearchServerTarget.of(singletonList(new Endpoint("search.example", -1, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("search.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void mixedHttpAndHttpsDefaultPortsAreOmitted() {
    DbServerTarget target =
        OpenSearchServerTarget.of(
            asList(
                new Endpoint("secure.example", 443, "https"),
                new Endpoint("plain.example", 80, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("secure.example,plain.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void configuredNodeOrderIsPreserved() {
    DbServerTarget first =
        OpenSearchServerTarget.of(
            asList(
                new Endpoint("h3", 9202, "http"),
                new Endpoint("h1", 9200, "http"),
                new Endpoint("h2", 9201, "http")));
    DbServerTarget second =
        OpenSearchServerTarget.of(
            asList(
                new Endpoint("h2", 9201, "http"),
                new Endpoint("h3", 9202, "http"),
                new Endpoint("h1", 9200, "http")));

    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(first.getAddress()).isEqualTo("h3:9202,h1:9200,h2:9201");
    assertThat(second.getAddress()).isEqualTo("h2:9201,h3:9202,h1:9200");
  }

  private static Stream<Arguments> defaultPortCases() {
    return Stream.of(
        argumentSet("HTTP", "http", 80),
        argumentSet("case-insensitive HTTP", "HTTP", 80),
        argumentSet("HTTPS", "https", 443),
        argumentSet("case-insensitive HTTPS", "HTTPS", 443));
  }
}
