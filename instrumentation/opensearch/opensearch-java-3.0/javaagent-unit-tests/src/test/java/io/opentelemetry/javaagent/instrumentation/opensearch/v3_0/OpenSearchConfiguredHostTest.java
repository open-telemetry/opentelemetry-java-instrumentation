/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import static java.util.Collections.singletonList;
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
  void bareHostKeepsItsNameAndHasNoPort() {
    OpenSearchServerTarget target =
        OpenSearchConfiguredHost.parse("search-domain.us-east-1.es.amazonaws.com");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("search-domain.us-east-1.es.amazonaws.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void schemeAndPortAreRead() {
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
  void pathPrefixWithoutAPortIsRemoved() {
    OpenSearchServerTarget target = OpenSearchConfiguredHost.parse("os.example/prefix");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("os.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void userinfoAfterAuthorityHasNoTarget() {
    assertThat(
            OpenSearchServerTarget.of(
                singletonList(
                    new OpenSearchServerTarget.Endpoint("os.example?token=user@secret", 9200))))
        .isNull();
    assertThat(
            OpenSearchServerTarget.of(
                singletonList(new OpenSearchServerTarget.Endpoint("user:pa/ss@os.example", 9200))))
        .isNull();
  }

  @Test
  void literalIpv6AddressDropsItsBrackets() {
    OpenSearchServerTarget target = OpenSearchConfiguredHost.parse("https://[::1]:9200");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::1");
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @Test
  void literalIpv6AddressWithoutAPortHasNoPort() {
    OpenSearchServerTarget target = OpenSearchConfiguredHost.parse("https://[::1]");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void hostThatIsOnlyCredentialsIsNoTarget() {
    assertThat(OpenSearchConfiguredHost.parse("https://user:secret@")).isNull();
  }
}
