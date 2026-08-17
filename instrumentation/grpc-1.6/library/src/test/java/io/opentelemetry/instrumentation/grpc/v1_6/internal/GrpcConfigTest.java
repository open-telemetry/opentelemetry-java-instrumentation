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

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GrpcConfigTest {

  @AfterEach
  void clearWarnings() throws Exception {
    Field field = GrpcConfig.class.getDeclaredField("warnedDeprecatedProperties");
    field.setAccessible(true);
    ((Set<?>) field.get(null)).clear();
  }

  @Test
  void readsNewSelectors() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("client").get("request_metadata").getScalarList("included", String.class))
        .thenReturn(asList("client-*", "other"));
    when(config.get("client").get("request_metadata").getScalarList("excluded", String.class))
        .thenReturn(singletonList("*-secret"));
    when(config.get("server").get("request_metadata").getScalarList("excluded", String.class))
        .thenReturn(singletonList("server-secret"));

    GrpcConfig grpcConfig = new GrpcConfig(config, false);

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
  void newSelectorTakesPrecedenceOverDeprecatedConfig() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("client").get("request_metadata").getScalarList("included", String.class))
        .thenReturn(singletonList("new"));
    when(config.get("capture_metadata").get("client").getScalarList("request", String.class))
        .thenReturn(singletonList("deprecated"));

    GrpcConfig grpcConfig = new GrpcConfig(config, false);

    assertThat(grpcConfig.getClientRequestMetadata().getIncluded()).containsExactly("new");
  }

  @Test
  void deprecatedConfigIsIncludeOnlyFallbackAndWarnsOnce() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("capture_metadata").get("client").getScalarList("request", String.class))
        .thenReturn(singletonList("deprecated"));
    Logger logger = Logger.getLogger(GrpcConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      GrpcConfig first = new GrpcConfig(config, false);
      GrpcConfig second = new GrpcConfig(config, false);

      assertThat(first.getClientRequestMetadata().getIncluded()).containsExactly("deprecated");
      assertThat(first.getClientRequestMetadata().getExcluded()).isEmpty();
      assertThat(second.getClientRequestMetadata()).isEqualTo(first.getClientRequestMetadata());
      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "The otel.instrumentation.grpc.capture-metadata.client.request setting and the"
                  + " equivalent declarative configuration property are deprecated and will be"
                  + " removed in 3.0. Use"
                  + " otel.instrumentation.grpc.client.request-metadata.included or equivalent"
                  + " declarative configuration instead.");
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void deprecatedConfigMatchesMetadataKeysExactlyAndIgnoresWildcards() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("capture_metadata").get("client").getScalarList("request", String.class))
        .thenReturn(asList("exact-key", "prefix-*"));

    GrpcConfig grpcConfig = new GrpcConfig(config, false);

    IncludeExclude client = grpcConfig.getClientRequestMetadata();
    assertThat(client).isNotNull();
    assertThat(client.matches("exact-key")).isTrue();
    assertThat(client.matches("prefix-value")).isFalse();
  }

  @Test
  void deprecatedConfigWithOnlyWildcardsCapturesNothing() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("capture_metadata").get("client").getScalarList("request", String.class))
        .thenReturn(singletonList("*"));

    assertThat(new GrpcConfig(config, false).getClientRequestMetadata()).isNull();
  }

  @Test
  void emptyDeprecatedConfigCapturesNothing() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("capture_metadata").get("client").getScalarList("request", String.class))
        .thenReturn(emptyList());

    GrpcConfig grpcConfig = new GrpcConfig(config, false);

    assertThat(grpcConfig.getClientRequestMetadata()).isNull();
  }

  @Test
  void emptySelectorCapturesNothing() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("client").get("request_metadata").getScalarList("included", String.class))
        .thenReturn(emptyList());
    when(config.get("client").get("request_metadata").getScalarList("excluded", String.class))
        .thenReturn(emptyList());

    GrpcConfig grpcConfig = new GrpcConfig(config, false);

    assertThat(grpcConfig.getClientRequestMetadata()).isNull();
  }

  @Test
  void deprecatedConfigIsIgnoredInV3Preview() {
    DeclarativeConfigProperties config = mockConfig();
    when(config.get("capture_metadata").get("client").getScalarList("request", String.class))
        .thenReturn(singletonList("deprecated"));

    GrpcConfig grpcConfig = new GrpcConfig(config, true);

    assertThat(grpcConfig.getClientRequestMetadata()).isNull();
  }

  @Test
  void createUsesV3PreviewFromOpenTelemetryInstance() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties grpcConfig = mockConfig();
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("grpc")).thenReturn(grpcConfig);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(commonConfig.getBoolean("v3_preview")).thenReturn(true);
    when(grpcConfig.get("capture_metadata").get("client").getScalarList("request", String.class))
        .thenReturn(singletonList("deprecated"));

    GrpcConfig config = GrpcConfig.create(openTelemetry);

    assertThat(config.getClientRequestMetadata()).isNull();
  }

  private static DeclarativeConfigProperties mockConfig() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    for (String side : asList("client", "server")) {
      when(config.get(side).get("request_metadata").getScalarList("included", String.class))
          .thenReturn(null);
      when(config.get(side).get("request_metadata").getScalarList("excluded", String.class))
          .thenReturn(null);
      when(config.get("capture_metadata").get(side).getScalarList("request", String.class))
          .thenReturn(null);
    }
    return config;
  }

  private static final class TestHandler extends Handler {

    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
