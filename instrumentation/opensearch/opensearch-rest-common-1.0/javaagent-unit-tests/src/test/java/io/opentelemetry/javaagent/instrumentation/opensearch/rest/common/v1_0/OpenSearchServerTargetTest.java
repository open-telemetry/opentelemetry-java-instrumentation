/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0.OpenSearchServerTarget.Endpoint;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Stream;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OpenSearchServerTargetTest {

  @Test
  void noEndpointsHasNoTarget() {
    assertThat(OpenSearchServerTarget.of(null)).isNull();
    assertThat(OpenSearchServerTarget.of(emptyList())).isNull();
  }

  @Test
  void singleEndpointKeepsItsHostAndPort() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(singletonList(new Endpoint("search.example", 9200, "https")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("search.example");
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @ParameterizedTest
  @MethodSource("defaultPortCases")
  void singleEndpointOmitsItsDefaultPort(String scheme, int port) {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(singletonList(new Endpoint("search.example", port, scheme)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("search.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleEndpointWithoutPortHasNoPort() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(singletonList(new Endpoint("search.example", -1, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("search.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleIpv6EndpointDropsItsBrackets() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(singletonList(new Endpoint("[::1]", 9200, "https")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::1");
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @Test
  void mixedPortsStayInTheSortedAddressList() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(
            asList(new Endpoint("h2", 9201, "http"), new Endpoint("h1", 9200, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1:9200,h2:9201");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void mixedDefaultAndNonDefaultPortsStayInTheAddressList() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(
            asList(
                new Endpoint("non-default.example", 9200, "http"),
                new Endpoint("default.example", -1, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("default.example:80,non-default.example:9200");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sharedNonDefaultPortStaysInIpv4AndIpv6Addresses() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(
            asList(new Endpoint("::1", 9200, "https"), new Endpoint("192.0.2.1", 9200, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("192.0.2.1:9200,[::1]:9200");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void mixedHttpAndHttpsDefaultPortsAreOmitted() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(
            asList(
                new Endpoint("secure.example", 443, "https"),
                new Endpoint("plain.example", 80, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("plain.example,secure.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void endpointPermutationsHaveTheSameTarget() {
    OpenSearchServerTarget first =
        OpenSearchServerTarget.of(
            asList(
                new Endpoint("h3", 9202, "http"),
                new Endpoint("h1", 9200, "http"),
                new Endpoint("h2", 9201, "http")));
    OpenSearchServerTarget second =
        OpenSearchServerTarget.of(
            asList(
                new Endpoint("h2", 9201, "http"),
                new Endpoint("h3", 9202, "http"),
                new Endpoint("h1", 9200, "http")));

    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(first.getAddress()).isEqualTo("h1:9200,h2:9201,h3:9202");
    assertThat(second.getAddress()).isEqualTo(first.getAddress());
  }

  @Test
  void duplicateEndpointsArePreserved() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(
            asList(
                new Endpoint("h2", 9201, "http"),
                new Endpoint("h1", 9200, "http"),
                new Endpoint("h1", 9200, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1:9200,h1:9200,h2:9201");
  }

  @Test
  void fiveEndpointsAreIncludedAfterSorting() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(
            asList(
                new Endpoint("h5", 80, "http"),
                new Endpoint("h3", 80, "http"),
                new Endpoint("h1", 80, "http"),
                new Endpoint("h4", 80, "http"),
                new Endpoint("h2", 80, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1,h2,h3,h4,h5");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sixthEndpointIsOmittedAfterSorting() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(
            asList(
                new Endpoint("h6", 80, "http"),
                new Endpoint("h3", 80, "http"),
                new Endpoint("h1", 80, "http"),
                new Endpoint("h5", 80, "http"),
                new Endpoint("h2", 80, "http"),
                new Endpoint("h4", 80, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1,h2,h3,h4,h5");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void literalIpv6AddressesAreBracketedInGroups() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(
            asList(new Endpoint("::1", 9200, "http"), new Endpoint("[fe80::1]", 9201, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[::1]:9200,[fe80::1]:9201");
  }

  @Test
  void credentialsPathQueryAndFragmentAreRemoved() {
    List<Endpoint> endpoints =
        asList(
            new Endpoint("user:secret@h1", 9200, "https"),
            new Endpoint("h2/prefix", 9200, "https"),
            new Endpoint("h3?token=secret", 9200, "https"),
            new Endpoint("h4#secret", 9200, "https"));

    OpenSearchServerTarget target = OpenSearchServerTarget.of(endpoints);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1:9200,h2:9200,h3:9200,h4:9200");
    assertThat(target.getPort()).isNull();
    assertThat(target.getAddress()).doesNotContain("secret");
  }

  @Test
  void userInfoAfterAuthorityHasNoTarget() {
    assertThat(
            OpenSearchServerTarget.of(
                singletonList(new Endpoint("search.example?token=user@secret", 9200, "https"))))
        .isNull();
    assertThat(
            OpenSearchServerTarget.of(
                singletonList(new Endpoint("user:pa/ss@search.example", 9200, "https"))))
        .isNull();
  }

  @Test
  void credentialsAreRemovedFromASingleEndpoint() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(
            singletonList(new Endpoint("user:secret@search.example", 9200, "https")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("search.example");
  }

  @Test
  void endpointThatIsOnlyCredentialsHasNoTarget() {
    assertThat(
            OpenSearchServerTarget.of(singletonList(new Endpoint("user:secret@", 9200, "https"))))
        .isNull();
    assertThat(
            OpenSearchServerTarget.of(
                asList(
                    new Endpoint("h1", 9200, "https"),
                    new Endpoint("user:secret@", 9200, "https"))))
        .isNull();
  }

  @Test
  void resolvedHttpHostAliasContainingCommaHasNoTarget() throws UnknownHostException {
    HttpHost unsafeHost =
        new HttpHost(
            InetAddress.getByAddress("h1.example,h2.example", new byte[] {127, 0, 0, 1}),
            9200,
            "http");
    Endpoint unsafeEndpoint =
        new Endpoint(unsafeHost.getHostName(), unsafeHost.getPort(), unsafeHost.getSchemeName());

    assertThat(OpenSearchServerTarget.of(singletonList(unsafeEndpoint))).isNull();
    assertThat(
            OpenSearchServerTarget.of(
                asList(new Endpoint("safe.example", 9200, "http"), unsafeEndpoint)))
        .isNull();
  }

  private static Stream<Arguments> defaultPortCases() {
    return Stream.of(
        argumentSet("HTTP", "http", 80),
        argumentSet("case-insensitive HTTP", "HTTP", 80),
        argumentSet("HTTPS", "https", 443),
        argumentSet("case-insensitive HTTPS", "HTTPS", 443));
  }
}
