/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LegacyProtostellarConnectionStringsTest {

  @ParameterizedTest
  @MethodSource("connectionStrings")
  void reportsLegacyProtostellarTarget(String connectionString) {
    CouchbaseServerTarget target = CouchbaseConnectionStrings.target(connectionString);

    assertThat(target.getAddress()).isEqualTo("node");
    assertThat(target.getPort()).isNull();
  }

  private static Stream<Arguments> connectionStrings() {
    return Stream.of(
        argumentSet("implicit default port", "protostellar://node"),
        argumentSet("explicit default port", "protostellar://node:18098"));
  }
}
