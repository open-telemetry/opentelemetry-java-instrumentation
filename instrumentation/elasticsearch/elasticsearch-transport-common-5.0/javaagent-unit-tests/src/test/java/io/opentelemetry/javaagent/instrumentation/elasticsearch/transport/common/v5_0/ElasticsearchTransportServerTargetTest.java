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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
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
  void oneAddressKeepsItsNonDefaultPort() {
    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(singletonList(new Endpoint("10.0.0.1", 9301)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.1");
    assertThat(target.getPort()).isEqualTo(9301);
  }

  @Test
  void oneAddressOmitsTheDefaultPort() {
    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(singletonList(new Endpoint("10.0.0.1", 9300)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void mixedPortAddressGroupIsStableAcrossIpv4AndIpv6Permutations() {
    ElasticsearchTransportServerTarget first =
        ElasticsearchTransportServerTarget.of(
            asList(new Endpoint("::1", 9301), new Endpoint("10.0.0.1", 9300)));
    ElasticsearchTransportServerTarget second =
        ElasticsearchTransportServerTarget.of(
            asList(new Endpoint("10.0.0.1", 9300), new Endpoint("::1", 9301)));

    assertThat(first).isNotNull();
    assertThat(first.getAddress()).isEqualTo("10.0.0.1:9300,[::1]:9301");
    assertThat(first.getPort()).isNull();
    assertThat(second).isNotNull();
    assertThat(second.getAddress()).isEqualTo(first.getAddress());
  }

  @Test
  void duplicateAddressesWithTheDefaultPortArePreserved() {
    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(
            asList(new Endpoint("10.0.0.1", 9300), new Endpoint("10.0.0.1", 9300)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.1,10.0.0.1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sharedNonDefaultPortIsSeparatedFromIpv4AndIpv6Addresses() {
    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(
            asList(new Endpoint("::1", 9301), new Endpoint("10.0.0.1", 9301)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.1,[::1]");
    assertThat(target.getPort()).isEqualTo(9301);
  }

  @Test
  void literalIpv6AddressesKeepBracketsAndOmitTheDefaultPort() {
    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(
            asList(new Endpoint("::1", 9300), new Endpoint("[fe80::1]", 9300)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[::1],[fe80::1]");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void credentialsPathQueryAndFragmentAreRemoved() {
    List<Endpoint> endpoints =
        asList(
            new Endpoint("user:secret@h1", 9300),
            new Endpoint("h2/prefix", 9300),
            new Endpoint("h3?token=secret", 9300),
            new Endpoint("h4#secret", 9300));

    ElasticsearchTransportServerTarget target = ElasticsearchTransportServerTarget.of(endpoints);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1,h2,h3,h4");
    assertThat(target.getPort()).isNull();
    assertThat(target.getAddress()).doesNotContain("secret");
  }

  @Test
  void userinfoAfterAuthorityHasNoTarget() {
    assertThat(
            ElasticsearchTransportServerTarget.of(
                singletonList(new Endpoint("es.example?token=user@secret", 9300))))
        .isNull();
    assertThat(
            ElasticsearchTransportServerTarget.of(
                singletonList(new Endpoint("user:pa/ss@es.example", 9300))))
        .isNull();
  }

  @Test
  void credentialsAreRemovedFromOneAddress() {
    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(
            singletonList(new Endpoint("user:secret@es.example", 9301)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("es.example");
    assertThat(target.getPort()).isEqualTo(9301);
  }

  @Test
  void addressThatIsOnlyCredentialsHasNoTarget() {
    assertThat(
            ElasticsearchTransportServerTarget.of(
                singletonList(new Endpoint("user:secret@", 9300))))
        .isNull();
    assertThat(
            ElasticsearchTransportServerTarget.of(
                asList(new Endpoint("h1", 9300), new Endpoint("user:secret@", 9300))))
        .isNull();
  }

  @Test
  void resolvedHostAliasContainingCommaHasNoTarget() throws UnknownHostException {
    String unsafeHost =
        InetAddress.getByAddress("h1.example,h2.example", new byte[] {127, 0, 0, 1}).getHostName();

    assertThat(ElasticsearchTransportServerTarget.of(singletonList(new Endpoint(unsafeHost, 9300))))
        .isNull();
    assertThat(
            ElasticsearchTransportServerTarget.of(
                asList(new Endpoint("safe.example", 9300), new Endpoint(unsafeHost, 9300))))
        .isNull();
  }
}
