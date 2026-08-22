/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpenSearchServerAddressTest {

  @Test
  void shouldRemoveIpv6Brackets() {
    OpenSearchServerAddress server = OpenSearchServerAddress.fromHost("[2001:db8::1]:9200");
    assertThat(server).isNotNull();
    assertThat(server.address()).isEqualTo("2001:db8::1");
    assertThat(server.port()).isEqualTo(9200);
  }
}
