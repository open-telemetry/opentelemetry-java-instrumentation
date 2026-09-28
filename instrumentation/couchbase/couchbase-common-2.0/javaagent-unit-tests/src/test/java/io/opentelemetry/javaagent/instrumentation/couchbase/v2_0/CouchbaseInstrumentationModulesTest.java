/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.v2_0.CouchbaseNetworkInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.v2_6.CouchbaseNetwork26InstrumentationModule;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class CouchbaseInstrumentationModulesTest {

  @Test
  void registersBothNetworkAdvices() {
    CommonConfig commonConfig = mock(CommonConfig.class);
    when(commonConfig.isV3Preview()).thenReturn(false);
    try (MockedStatic<AgentCommonConfig> common = mockStatic(AgentCommonConfig.class)) {
      common.when(AgentCommonConfig::get).thenReturn(commonConfig);
      assertThat(new CouchbaseNetworkInstrumentationModule().typeInstrumentations())
          .extracting(instrumentation -> instrumentation.getClass().getName())
          .containsExactly(
              "io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.v2_0.CouchbaseCoreNetworkInstrumentation",
              "io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.v2_0.CouchbaseNetworkInstrumentation");
      assertThat(new CouchbaseNetwork26InstrumentationModule().typeInstrumentations())
          .extracting(instrumentation -> instrumentation.getClass().getName())
          .containsExactly(
              "io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.v2_6.CouchbaseCoreInstrumentation",
              "io.opentelemetry.javaagent.instrumentation.couchbase.v2_0.network.v2_6.CouchbaseNetworkInstrumentation");
    }
  }
}
