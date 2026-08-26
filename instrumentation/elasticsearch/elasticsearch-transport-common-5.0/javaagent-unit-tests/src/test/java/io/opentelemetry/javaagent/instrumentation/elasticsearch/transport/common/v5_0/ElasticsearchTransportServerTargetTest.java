/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0.ElasticsearchTransportServerTarget.Endpoint;
import org.junit.jupiter.api.Test;

class ElasticsearchTransportServerTargetTest {

  @Test
  void noAddressHasNoTarget() {
    assertThat(ElasticsearchTransportServerTarget.of(null)).isNull();
    assertThat(ElasticsearchTransportServerTarget.of(emptyList())).isNull();
    assertThat(ElasticsearchTransportServerTarget.of(singletonList(new Endpoint(null, 9300))))
        .isNull();
    assertThat(ElasticsearchTransportServerTarget.of(singletonList(new Endpoint("", 9300))))
        .isNull();
  }

  @Test
  void oneAddressKeepsItsHostAndPort() {
    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(singletonList(new Endpoint("10.0.0.1", 9300)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.1");
    assertThat(target.getPort()).isEqualTo(9300);
  }

  @Test
  void severalAddressesAreListedWithoutAPortOfTheirOwn() {
    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(
            asList(new Endpoint("10.0.0.1", 9300), new Endpoint("10.0.0.2", 9301)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.1:9300,10.0.0.2:9301");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void literalIpv6AddressesAreBracketed() {
    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(
            asList(new Endpoint("::1", 9300), new Endpoint("[fe80::1]", 9300)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[::1]:9300,[fe80::1]:9300");
  }
}
