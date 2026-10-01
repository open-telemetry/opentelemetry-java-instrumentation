/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticsearchTransportServerTarget.Endpoint;
import org.junit.jupiter.api.Test;

class ElasticsearchTransportServerTargetTest {

  @Test
  void noAddressHasNoTarget() {
    assertThat(ElasticsearchTransportServerTarget.of(null)).isNull();
    assertThat(ElasticsearchTransportServerTarget.of(emptyList())).isNull();
  }

  @Test
  void oneAddressKeepsItsNonDefaultPort() {
    DbServerTarget target =
        ElasticsearchTransportServerTarget.of(singletonList(new Endpoint("10.0.0.1", 9301)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.1");
    assertThat(target.getPort()).isEqualTo(9301);
  }

  @Test
  void oneAddressOmitsTheDefaultPort() {
    DbServerTarget target =
        ElasticsearchTransportServerTarget.of(singletonList(new Endpoint("10.0.0.1", 9300)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void configuredTransportAddressOrderDoesNotChangeTarget() {
    DbServerTarget first =
        ElasticsearchTransportServerTarget.of(
            asList(new Endpoint("::1", 9301), new Endpoint("10.0.0.1", 9300)));
    DbServerTarget second =
        ElasticsearchTransportServerTarget.of(
            asList(new Endpoint("10.0.0.1", 9300), new Endpoint("::1", 9301)));

    assertThat(first).isNotNull();
    assertThat(first.getAddress()).isEqualTo("10.0.0.1:9300,[::1]:9301");
    assertThat(first.getPort()).isNull();
    assertThat(second).isNotNull();
    assertThat(second.getAddress()).isEqualTo(first.getAddress());
  }
}
