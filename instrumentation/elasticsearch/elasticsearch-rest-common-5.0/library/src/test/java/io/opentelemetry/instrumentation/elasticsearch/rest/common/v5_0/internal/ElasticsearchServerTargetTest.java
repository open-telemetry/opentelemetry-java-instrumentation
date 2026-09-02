/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.nCopies;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Stream;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ElasticsearchServerTargetTest {

  @Test
  void noHostsHasNoTarget() {
    assertThat(ElasticsearchServerTarget.of(null)).isNull();
    assertThat(ElasticsearchServerTarget.of(emptyList())).isNull();
  }

  @Test
  void singleHostKeepsItsHostAndPort() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(singletonList(new HttpHost("es.example", 9200, "https")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("es.example");
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @ParameterizedTest
  @MethodSource("defaultPortCases")
  void singleHostOmitsItsDefaultPort(String scheme, int port) {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(singletonList(new HttpHost("es.example", port, scheme)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("es.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleHostWithoutPortHasNoPort() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(singletonList(new HttpHost("es.example")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("es.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleIpv6HostDropsItsBrackets() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(singletonList(new HttpHost("[::1]", 9200, "https")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::1");
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @Test
  void mixedPortsStayInTheSortedAddressList() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(
            asList(new HttpHost("h2", 9201, "http"), new HttpHost("h1", 9200, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1:9200,h2:9201");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void mixedDefaultAndNonDefaultPortsStayInTheAddressList() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(
            asList(
                new HttpHost("non-default.example", 9200, "http"),
                new HttpHost("default.example", -1, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("default.example:80,non-default.example:9200");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sharedNonDefaultPortStaysInIpv4AndIpv6Addresses() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(
            asList(new HttpHost("::1", 9200, "https"), new HttpHost("192.0.2.1", 9200, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("192.0.2.1:9200,[::1]:9200");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void mixedHttpAndHttpsDefaultPortsAreOmitted() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(
            asList(
                new HttpHost("secure.example", 443, "https"),
                new HttpHost("plain.example", 80, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("plain.example,secure.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void hostPermutationsHaveTheSameTarget() {
    ElasticsearchServerTarget first =
        ElasticsearchServerTarget.of(
            asList(new HttpHost("h3", 9202), new HttpHost("h1", 9200), new HttpHost("h2", 9201)));
    ElasticsearchServerTarget second =
        ElasticsearchServerTarget.of(
            asList(new HttpHost("h2", 9201), new HttpHost("h3", 9202), new HttpHost("h1", 9200)));

    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(first.getAddress()).isEqualTo("h1:9200,h2:9201,h3:9202");
    assertThat(second.getAddress()).isEqualTo(first.getAddress());
  }

  @Test
  void duplicateHostsArePreserved() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(
            asList(new HttpHost("h2", 9201), new HttpHost("h1", 9200), new HttpHost("h1", 9200)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1:9200,h1:9200,h2:9201");
  }

  @Test
  void fiveEndpointsAreKeptAfterSorting() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(
            asList(
                new HttpHost("h5", 80, "http"),
                new HttpHost("h1", 80, "http"),
                new HttpHost("h3", 80, "http"),
                new HttpHost("h2", 80, "http"),
                new HttpHost("h4", 80, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1,h2,h3,h4,h5");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void endpointsAfterFirstFiveAreOmittedAfterSorting() {
    ElasticsearchServerTarget first =
        ElasticsearchServerTarget.of(
            asList(
                new HttpHost("h6", 80, "http"),
                new HttpHost("h2", 80, "http"),
                new HttpHost("h4", 80, "http"),
                new HttpHost("h1", 80, "http"),
                new HttpHost("h5", 80, "http"),
                new HttpHost("h3", 80, "http")));
    ElasticsearchServerTarget second =
        ElasticsearchServerTarget.of(
            asList(
                new HttpHost("h3", 80, "http"),
                new HttpHost("h5", 80, "http"),
                new HttpHost("h1", 80, "http"),
                new HttpHost("h4", 80, "http"),
                new HttpHost("h2", 80, "http"),
                new HttpHost("h6", 80, "http")));

    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(first.getAddress()).isEqualTo("h1,h2,h3,h4,h5");
    assertThat(second.getAddress()).isEqualTo(first.getAddress());
    assertThat(first.getPort()).isNull();
    assertThat(second.getPort()).isNull();
  }

  @Test
  void endpointLengthDoesNotLimitTarget() {
    String host = repeat("a", 256);

    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(singletonList(new HttpHost(host, 9200, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo(host).hasSize(256);
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @Test
  void severalHostsWithDifferentSchemesOmitThem() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(
            asList(new HttpHost("h1", 9200, "http"), new HttpHost("h2", 9200, "https")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1:9200,h2:9200");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void literalIpv6AddressesAreBracketed() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(
            asList(new HttpHost("::1", 9200, "http"), new HttpHost("[fe80::1]", 9200, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[::1]:9200,[fe80::1]:9200");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void credentialsPathQueryAndFragmentAreRemoved() {
    List<HttpHost> hosts =
        asList(
            new HttpHost("user:secret@h1", 443, "https"),
            new HttpHost("h2/prefix", 443, "https"),
            new HttpHost("h3?token=secret", 443, "https"),
            new HttpHost("h4#secret", 443, "https"));

    ElasticsearchServerTarget target = ElasticsearchServerTarget.of(hosts);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1,h2,h3,h4");
    assertThat(target.getPort()).isNull();
    assertThat(target.getAddress()).doesNotContain("secret");
  }

  @Test
  void userinfoAfterAuthorityHasNoTarget() {
    assertThat(
            ElasticsearchServerTarget.of(
                singletonList(new HttpHost("es.example?token=user@secret", 9200))))
        .isNull();
    assertThat(
            ElasticsearchServerTarget.of(
                singletonList(new HttpHost("user:pa/ss@es.example", 9200))))
        .isNull();
  }

  @Test
  void credentialsAreRemovedFromASingleHost() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(
            singletonList(new HttpHost("user:secret@es.example", 9200, "https")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("es.example");
  }

  @Test
  void hostThatIsOnlyCredentialsHasNoTarget() {
    assertThat(ElasticsearchServerTarget.of(singletonList(new HttpHost("user:secret@", 9200))))
        .isNull();
    assertThat(
            ElasticsearchServerTarget.of(
                asList(new HttpHost("h1", 9200), new HttpHost("user:secret@", 9200))))
        .isNull();
  }

  @Test
  void resolvedHostAliasContainingCommaHasNoTarget() throws UnknownHostException {
    HttpHost unsafeHost =
        new HttpHost(
            InetAddress.getByAddress("h1.example,h2.example", new byte[] {127, 0, 0, 1}),
            9200,
            "http");

    assertThat(ElasticsearchServerTarget.of(singletonList(unsafeHost))).isNull();
    assertThat(
            ElasticsearchServerTarget.of(
                asList(new HttpHost("safe.example", 9200, "http"), unsafeHost)))
        .isNull();
  }

  private static Stream<Arguments> defaultPortCases() {
    return Stream.of(
        argumentSet("HTTP", "http", 80),
        argumentSet("case-insensitive HTTP", "HTTP", 80),
        argumentSet("HTTPS", "https", 443),
        argumentSet("case-insensitive HTTPS", "HTTPS", 443));
  }

  private static String repeat(String value, int count) {
    return String.join("", nCopies(count, value));
  }
}
