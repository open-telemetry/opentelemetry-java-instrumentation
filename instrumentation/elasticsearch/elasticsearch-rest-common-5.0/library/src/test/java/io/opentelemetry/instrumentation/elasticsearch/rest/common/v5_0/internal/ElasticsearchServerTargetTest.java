/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;

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
  void severalHostsOmitTheirSharedScheme() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(
            asList(new HttpHost("h1", 9200, "http"), new HttpHost("h2", 9201, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1:9200,h2:9201");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void severalHostsWithDifferentSchemesOmitThem() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(
            asList(new HttpHost("h1", 9200, "http"), new HttpHost("h2", 9200, "https")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1:9200,h2:9200");
  }

  @Test
  void literalIpv6AddressesAreBracketed() {
    ElasticsearchServerTarget target =
        ElasticsearchServerTarget.of(
            asList(new HttpHost("::1", 9200, "http"), new HttpHost("[fe80::1]", 9200, "http")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[::1]:9200,[fe80::1]:9200");
  }

  @Test
  void credentialsPathQueryAndFragmentAreRemoved() {
    List<HttpHost> hosts =
        asList(
            new HttpHost("user:secret@h1", 9200, "https"),
            new HttpHost("h2/prefix", 9200, "https"),
            new HttpHost("h3?token=secret", 9200, "https"),
            new HttpHost("h4#secret", 9200, "https"));

    ElasticsearchServerTarget target = ElasticsearchServerTarget.of(hosts);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("h1:9200,h2:9200,h3:9200,h4:9200");
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
}
