/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.nCopies;
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
  void duplicateEndpointsAtAddressLimitArePreserved() {
    String host = repeat("a", 127);

    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(
            asList(new Endpoint(host, 9301), new Endpoint(host, 9301)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo(host + "," + host).hasSize(255);
    assertThat(target.getPort()).isEqualTo(9301);
  }

  @Test
  void endpointsBeyondAddressLimitAreOmittedWholeAfterSorting() {
    Endpoint ipv6 = new Endpoint("::1", 9300);
    Endpoint longHost = new Endpoint(repeat("b", 239), 9300);
    Endpoint omittedHost = new Endpoint("z.example", 9301);

    ElasticsearchTransportServerTarget first =
        ElasticsearchTransportServerTarget.of(asList(omittedHost, longHost, ipv6));
    ElasticsearchTransportServerTarget second =
        ElasticsearchTransportServerTarget.of(asList(ipv6, omittedHost, longHost));

    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(first.getAddress())
        .isEqualTo("[::1]:9300," + repeat("b", 239) + ":9300")
        .hasSize(255)
        .doesNotContain("z.example");
    assertThat(second.getAddress()).isEqualTo(first.getAddress());
    assertThat(first.getPort()).isNull();
    assertThat(second.getPort()).isNull();
  }

  @Test
  void firstEndpointThatExceedsAddressLimitHasNoTarget() {
    String hostAtLimit = repeat("a", 255);
    String hostOverLimit = repeat("a", 256);

    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(singletonList(new Endpoint(hostAtLimit, 9300)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo(hostAtLimit).hasSize(255);
    assertThat(
            ElasticsearchTransportServerTarget.of(singletonList(new Endpoint(hostOverLimit, 9300))))
        .isNull();
    assertThat(
            ElasticsearchTransportServerTarget.of(
                asList(new Endpoint("z.example", 9300), new Endpoint(hostOverLimit, 9300))))
        .isNull();
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

  private static String repeat(String value, int count) {
    return String.join("", nCopies(count, value));
  }
}
