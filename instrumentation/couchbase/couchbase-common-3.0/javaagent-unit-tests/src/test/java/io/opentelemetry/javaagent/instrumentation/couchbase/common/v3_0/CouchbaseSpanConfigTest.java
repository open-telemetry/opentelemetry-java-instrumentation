/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CouchbaseSpanConfigTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void experimentalTelemetryConfigControlsAllExperimentalTelemetry(boolean enabled) {
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    when(config.getBoolean("emit_experimental_telemetry/development", false)).thenReturn(enabled);
    assertThat(CouchbaseSpan.emitExperimentalTelemetry(config)).isEqualTo(enabled);
    verify(config).getBoolean("emit_experimental_telemetry/development", false);
  }
}
