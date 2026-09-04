/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenSearchConfiguredHostTest {

  @Test
  void nothingIsNoTarget() {
    assertThat(OpenSearchConfiguredHost.parse(null)).isNull();
    assertThat(OpenSearchConfiguredHost.parse("")).isNull();
  }

  @Test
  void bareHostKeepsItsNameAndHasNoPort() {
    DbServerTarget target =
        OpenSearchConfiguredHost.parse("search-domain.us-east-1.es.amazonaws.com", "https");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("search-domain.us-east-1.es.amazonaws.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void schemeAndPortAreRead() {
    DbServerTarget target = OpenSearchConfiguredHost.parse("https://os.example:9200");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("os.example");
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @Test
  void defaultPortIsOmitted() {
    DbServerTarget target = OpenSearchConfiguredHost.parse("https://os.example:443");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("os.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void credentialsPathQueryAndFragmentAreRemoved() {
    DbServerTarget target =
        OpenSearchConfiguredHost.parse("https://user:secret@os.example:9200/prefix?token=secret#f");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("os.example");
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @Test
  void credentialsWithoutAPortAreRemoved() {
    DbServerTarget target = OpenSearchConfiguredHost.parse("user:secret@os.example", "https");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("os.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void pathPrefixWithoutAPortIsRemoved() {
    DbServerTarget target = OpenSearchConfiguredHost.parse("os.example/prefix", "https");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("os.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void urlInQueryDoesNotReplaceTarget() {
    DbServerTarget target =
        OpenSearchConfiguredHost.parse("os.example?token=https://secret.example", "https");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("os.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void userinfoAfterAuthorityHasNoTarget() {
    assertThat(OpenSearchConfiguredHost.parse("os.example?token=user@secret")).isNull();
    assertThat(OpenSearchConfiguredHost.parse("user:123/secret@os.example")).isNull();
    assertThat(OpenSearchConfiguredHost.parse("os.example#user@secret")).isNull();
  }

  @Test
  void literalIpv6AddressDropsItsBrackets() {
    DbServerTarget target = OpenSearchConfiguredHost.parse("https://[::1]:9200");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::1");
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @Test
  void literalIpv6AddressWithoutAPortHasNoPort() {
    DbServerTarget target = OpenSearchConfiguredHost.parse("https://[::1]");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::1");
    assertThat(target.getPort()).isNull();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "os.example:not-a-port",
        "os.example:+9200",
        "os.example:-0",
        "os.example:-1",
        "os.example:65536",
        "os.example:９２００",
        "https://[::1]:not-a-port",
        "https://[::1",
        "https://[::1]suffix",
        "https://::1"
      })
  void malformedAuthorityIsNoTarget(String host) {
    assertThat(OpenSearchConfiguredHost.parse(host)).isNull();
  }

  @Test
  void hostWithoutPortOrDefaultSchemeIsNoTarget() {
    assertThat(OpenSearchConfiguredHost.parse("os.example")).isNull();
  }

  @Test
  void hostThatIsOnlyCredentialsIsNoTarget() {
    assertThat(OpenSearchConfiguredHost.parse("https://user:secret@")).isNull();
  }
}
