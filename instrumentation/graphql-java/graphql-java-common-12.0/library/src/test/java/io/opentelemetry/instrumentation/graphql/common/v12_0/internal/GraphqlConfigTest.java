/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.common.v12_0.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GraphqlConfigTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void operationNameInSpanNameSetting(boolean enabled) {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(config.get("operation_name_in_span_name").getBoolean("enabled", false))
        .thenReturn(enabled);

    assertThat(GraphqlConfig.getOperationNameInSpanNameEnabled(config)).isEqualTo(enabled);
  }

  @Test
  void operationNameInSpanNameIsDisabledByDefault() {
    assertThat(GraphqlConfig.getOperationNameInSpanNameEnabled(empty())).isFalse();
  }

  @Test
  void operationNameInSpanNameIgnoresLegacySetting() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(config.get("operation_name_in_span_name").getBoolean("enabled", false)).thenReturn(false);
    when(config.get("add_operation_name_to_span_name").getBoolean("enabled")).thenReturn(true);

    assertThat(GraphqlConfig.getOperationNameInSpanNameEnabled(config)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void querySanitizationSetting(boolean enabled) {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(config.get("query_sanitization").getBoolean("enabled", true)).thenReturn(enabled);

    assertThat(GraphqlConfig.getQuerySanitizationEnabled(config)).isEqualTo(enabled);
  }

  @Test
  void querySanitizationIsEnabledByDefault() {
    assertThat(GraphqlConfig.getQuerySanitizationEnabled(empty())).isTrue();
  }

  @Test
  void querySanitizationIgnoresLegacySetting() {
    DeclarativeConfigProperties config =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(config.get("query_sanitization").getBoolean("enabled", true)).thenReturn(true);
    when(config.get("query_sanitizer").getBoolean("enabled")).thenReturn(false);

    assertThat(GraphqlConfig.getQuerySanitizationEnabled(config)).isTrue();
  }
}
