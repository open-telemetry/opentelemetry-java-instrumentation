/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import org.junit.jupiter.api.Test;

class GrpcConfigTest {

  @Test
  void readsRequestCapturedHeaderLists() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("client").getScalarList("request_captured_headers", String.class, emptyList()))
        .thenReturn(asList("client-key", "OTHER"));
    when(config.get("server").getScalarList("request_captured_headers", String.class, emptyList()))
        .thenReturn(singletonList("server-key"));

    GrpcConfig grpcConfig = new GrpcConfig(config);

    assertThat(grpcConfig.getClientRequestCapturedHeaders()).containsExactly("client-key", "OTHER");
    assertThat(grpcConfig.getServerRequestCapturedHeaders()).containsExactly("server-key");
  }

  @Test
  void defaultsToNoCapturedHeaders() {
    DeclarativeConfigProperties config = mockConfig();

    GrpcConfig grpcConfig = new GrpcConfig(config);

    assertThat(grpcConfig.getClientRequestCapturedHeaders()).isEmpty();
    assertThat(grpcConfig.getServerRequestCapturedHeaders()).isEmpty();
  }

  @Test
  void unavailableDeclarativeConfigurationCapturesNothing() {
    GrpcConfig grpcConfig = GrpcConfig.create(OpenTelemetry.noop());

    assertThat(grpcConfig.getClientRequestCapturedHeaders()).isEmpty();
    assertThat(grpcConfig.getServerRequestCapturedHeaders()).isEmpty();
  }

  @Test
  void preservesLiteralWildcardNames() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("client").getScalarList("request_captured_headers", String.class, emptyList()))
        .thenReturn(asList("*", "some-?ey"));

    GrpcConfig grpcConfig = new GrpcConfig(config);

    assertThat(grpcConfig.getClientRequestCapturedHeaders()).containsExactly("*", "some-?ey");
  }

  private static DeclarativeConfigProperties mockConfig() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    for (String side : asList("client", "server")) {
      when(config.get(side).getScalarList("request_captured_headers", String.class, emptyList()))
          .thenReturn(emptyList());
    }
    return config;
  }
}
