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
    assertThat(
            ElasticsearchTransportServerTarget.of(
                asList(
                    new Endpoint("a.example", 9300),
                    new Endpoint("b.example", 9300),
                    new Endpoint("c.example", 9300),
                    new Endpoint("d.example", 9300),
                    new Endpoint("e.example", 9300),
                    new Endpoint(null, 9300))))
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
  void duplicateEndpointsArePreserved() {
    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(
            asList(
                new Endpoint("duplicate.example", 9300), new Endpoint("duplicate.example", 9300)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("duplicate.example,duplicate.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void exactlyFiveEndpointsAreReportedAfterSorting() {
    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(
            asList(
                new Endpoint("e.example", 9300),
                new Endpoint("c.example", 9300),
                new Endpoint("a.example", 9300),
                new Endpoint("d.example", 9300),
                new Endpoint("b.example", 9300)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("a.example,b.example,c.example,d.example,e.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void onlyFirstFiveOfSixEndpointsUseConfigurationPortModeAfterSorting() {
    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(
            asList(
                new Endpoint("f.example", 9400),
                new Endpoint("d.example", 9300),
                new Endpoint("b.example", 9300),
                new Endpoint("e.example", 9300),
                new Endpoint("a.example", 9300),
                new Endpoint("c.example", 9300)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress())
        .isEqualTo("a.example:9300,b.example:9300,c.example:9300,d.example:9300,e.example:9300")
        .doesNotContain("f.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void invalidEndpointLengthDropsTarget() {
    String longHost = repeat("a", 256);

    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(singletonList(new Endpoint(longHost, 9300)));

    assertThat(target).isNull();
  }

  @Test
  void sharedNonDefaultPortIsIncludedWithEachIpv4AndIpv6Address() {
    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(
            asList(new Endpoint("::1", 9301), new Endpoint("10.0.0.1", 9301)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.1:9301,[::1]:9301");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void literalIpv6AddressesOmitTheDefaultPort() {
    ElasticsearchTransportServerTarget target =
        ElasticsearchTransportServerTarget.of(
            asList(new Endpoint("::1", 9300), new Endpoint("[fe80::1]", 9300)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::1,fe80::1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void credentialsPathQueryAndFragmentHaveNoTarget() {
    List<Endpoint> endpoints =
        asList(
            new Endpoint("user:secret@h1", 9300),
            new Endpoint("h2/prefix", 9300),
            new Endpoint("h3?token=secret", 9300),
            new Endpoint("h4#secret", 9300));

    assertThat(ElasticsearchTransportServerTarget.of(endpoints)).isNull();
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
  void credentialsHaveNoTarget() {
    assertThat(
            ElasticsearchTransportServerTarget.of(
                singletonList(new Endpoint("user:secret@es.example", 9301))))
        .isNull();
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
