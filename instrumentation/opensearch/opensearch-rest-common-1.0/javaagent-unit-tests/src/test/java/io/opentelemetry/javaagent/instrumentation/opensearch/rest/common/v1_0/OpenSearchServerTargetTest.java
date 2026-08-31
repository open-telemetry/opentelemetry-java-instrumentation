/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0.OpenSearchServerTarget.Endpoint;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenSearchServerTargetTest {

  @Test
  void noEndpointsHasNoTarget() {
    assertThat(OpenSearchServerTarget.of(null)).isNull();
    assertThat(OpenSearchServerTarget.of(emptyList())).isNull();
  }

  @Test
  void singleEndpointKeepsItsHostAndPort() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(singletonList(new Endpoint("search.example", 9200)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("search.example");
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @Test
  void singleEndpointWithoutPortHasNoPort() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(singletonList(new Endpoint("search.example", -1)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("search.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleIpv6EndpointDropsItsBrackets() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(singletonList(new Endpoint("[::1]", 9200)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::1");
    assertThat(target.getPort()).isEqualTo(9200);
  }

  @Test
  void severalEndpointsAreSortedAndIncludeTheirPorts() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(asList(new Endpoint("h2", 9201), new Endpoint("h1", 9200)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1:9200,h2:9201");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void endpointPermutationsHaveTheSameTarget() {
    OpenSearchServerTarget first =
        OpenSearchServerTarget.of(
            asList(new Endpoint("h3", 9202), new Endpoint("h1", 9200), new Endpoint("h2", 9201)));
    OpenSearchServerTarget second =
        OpenSearchServerTarget.of(
            asList(new Endpoint("h2", 9201), new Endpoint("h3", 9202), new Endpoint("h1", 9200)));

    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(first.getAddress()).isEqualTo("h1:9200,h2:9201,h3:9202");
    assertThat(second.getAddress()).isEqualTo(first.getAddress());
  }

  @Test
  void duplicateEndpointsArePreserved() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(
            asList(new Endpoint("h2", 9201), new Endpoint("h1", 9200), new Endpoint("h1", 9200)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1:9200,h1:9200,h2:9201");
  }

  @Test
  void literalIpv6AddressesAreBracketedInGroups() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(
            asList(new Endpoint("::1", 9200), new Endpoint("[fe80::1]", 9201)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[::1]:9200,[fe80::1]:9201");
  }

  @Test
  void credentialsPathQueryAndFragmentAreRemoved() {
    List<Endpoint> endpoints =
        asList(
            new Endpoint("user:secret@h1", 9200),
            new Endpoint("h2/prefix", 9200),
            new Endpoint("h3?token=secret", 9200),
            new Endpoint("h4#secret", 9200));

    OpenSearchServerTarget target = OpenSearchServerTarget.of(endpoints);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1:9200,h2:9200,h3:9200,h4:9200");
    assertThat(target.getAddress()).doesNotContain("secret");
  }

  @Test
  void userInfoAfterAuthorityHasNoTarget() {
    assertThat(
            OpenSearchServerTarget.of(
                singletonList(new Endpoint("search.example?token=user@secret", 9200))))
        .isNull();
    assertThat(
            OpenSearchServerTarget.of(
                singletonList(new Endpoint("user:pa/ss@search.example", 9200))))
        .isNull();
  }

  @Test
  void credentialsAreRemovedFromASingleEndpoint() {
    OpenSearchServerTarget target =
        OpenSearchServerTarget.of(singletonList(new Endpoint("user:secret@search.example", 9200)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("search.example");
  }

  @Test
  void endpointThatIsOnlyCredentialsHasNoTarget() {
    assertThat(OpenSearchServerTarget.of(singletonList(new Endpoint("user:secret@", 9200))))
        .isNull();
    assertThat(
            OpenSearchServerTarget.of(
                asList(new Endpoint("h1", 9200), new Endpoint("user:secret@", 9200))))
        .isNull();
  }
}
