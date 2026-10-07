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

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import org.junit.jupiter.api.Test;

class GrpcConfigTest {

  @Test
  void readsNewSelectors() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("client").get("request_metadata").getScalarList("included", String.class))
        .thenReturn(asList("client-*", "other"));
    when(config.get("client").get("request_metadata").getScalarList("excluded", String.class))
        .thenReturn(singletonList("*-secret"));
    when(config.get("server").get("request_metadata").getScalarList("excluded", String.class))
        .thenReturn(singletonList("server-secret"));

    GrpcConfig grpcConfig = new GrpcConfig(config);

    IncludeExclude client = grpcConfig.getClientRequestMetadata();
    assertThat(client).isNotNull();
    assertThat(client.getIncluded()).containsExactly("client-*", "other");
    assertThat(client.getExcluded()).containsExactly("*-secret");
    IncludeExclude server = grpcConfig.getServerRequestMetadata();
    assertThat(server).isNotNull();
    assertThat(server.getIncluded()).isEmpty();
    assertThat(server.getExcluded()).containsExactly("server-secret");
  }

  @Test
  void deprecatedConfigIsIgnored() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("capture_metadata").get("client").getScalarList("request", String.class))
        .thenReturn(singletonList("deprecated"));
    when(config.get("capture_metadata").get("server").getScalarList("request", String.class))
        .thenReturn(singletonList("deprecated"));

    GrpcConfig grpcConfig = new GrpcConfig(config);

    assertThat(grpcConfig.getClientRequestMetadata()).isNull();
    assertThat(grpcConfig.getServerRequestMetadata()).isNull();
  }

  @Test
  void emptySelectorCapturesNothing() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("client").get("request_metadata").getScalarList("included", String.class))
        .thenReturn(emptyList());
    when(config.get("client").get("request_metadata").getScalarList("excluded", String.class))
        .thenReturn(emptyList());

    GrpcConfig grpcConfig = new GrpcConfig(config);

    assertThat(grpcConfig.getClientRequestMetadata()).isNull();
  }

  private static DeclarativeConfigProperties mockConfig() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    for (String side : asList("client", "server")) {
      when(config.get(side).get("request_metadata").getScalarList("included", String.class))
          .thenReturn(null);
      when(config.get(side).get("request_metadata").getScalarList("excluded", String.class))
          .thenReturn(null);
    }
    return config;
  }
}
