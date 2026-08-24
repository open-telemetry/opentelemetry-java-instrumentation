/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0.OpenSearchServerTarget;
import org.junit.jupiter.api.Test;

class OpenSearchConfiguredHostTest {

  @Test
  void nothingIsNoTarget() {
    assertThat(OpenSearchConfiguredHost.parse(null)).isNull();
    assertThat(OpenSearchConfiguredHost.parse("")).isNull();
  }

  @Test
  void aBareHostKeepsItsNameAndHasNoPort() {
    OpenSearchServerTarget target =
        OpenSearchConfiguredHost.parse("search-domain.us-east-1.es.amazonaws.com");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("search-domain.us-east-1.es.amazonaws.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void aSchemeAndPortAreRead() {
    OpenSearchServerTarget target = OpenSearchConfiguredHost.parse("https://os.example:9200");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("os.example");
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @Test
  void credentialsPathQueryAndFragmentAreRemoved() {
    OpenSearchServerTarget target =
        OpenSearchConfiguredHost.parse("https://user:secret@os.example:9200/prefix?token=secret#f");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("os.example");
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @Test
  void aPathPrefixWithoutAPortIsRemoved() {
    OpenSearchServerTarget target = OpenSearchConfiguredHost.parse("os.example/prefix");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("os.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void aLiteralIpv6AddressKeepsItsBrackets() {
    OpenSearchServerTarget target = OpenSearchConfiguredHost.parse("https://[::1]:9200");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[::1]");
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @Test
  void aLiteralIpv6AddressWithoutAPortHasNoPort() {
    OpenSearchServerTarget target = OpenSearchConfiguredHost.parse("https://[::1]");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[::1]");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void aHostThatIsOnlyCredentialsIsNoTarget() {
    assertThat(OpenSearchConfiguredHost.parse("https://user:secret@")).isNull();
  }
}
