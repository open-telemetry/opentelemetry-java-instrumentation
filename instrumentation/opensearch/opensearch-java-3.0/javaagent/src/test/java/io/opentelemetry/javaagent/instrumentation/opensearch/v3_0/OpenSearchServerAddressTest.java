/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class OpenSearchServerAddressTest {

  @Test
  void shouldRemoveIpv6Brackets() {
    // Verify the URI parsing approach used in OpenSearchServerAddress.fromHost():
    // URI.getHost() returns IPv6 addresses wrapped in brackets, and they must be stripped.
    URI uri = URI.create("https://[2001:db8::1]:9200");
    String address = uri.getHost();
    if (address != null && address.startsWith("[") && address.endsWith("]")) {
      address = address.substring(1, address.length() - 1);
    }
    assertThat(address).isEqualTo("2001:db8::1");
    assertThat(uri.getPort()).isEqualTo(9200);
  }
}
