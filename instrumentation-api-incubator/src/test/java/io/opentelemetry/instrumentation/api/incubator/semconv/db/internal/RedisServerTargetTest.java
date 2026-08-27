/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class RedisServerTargetTest {

  @Test
  void singleEndpointKeepsPort() {
    RedisServerTarget target = RedisServerTarget.ofEndpoint("redis://localhost:6379");

    assertThat(target.getAddress()).isEqualTo("localhost");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void hostAndPort() {
    RedisServerTarget target = RedisServerTarget.ofHostAndPort("localhost", 6379);

    assertThat(target.getAddress()).isEqualTo("localhost");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void hostWithoutPort() {
    RedisServerTarget target = RedisServerTarget.ofHostAndPort("localhost", -1);

    assertThat(target.getAddress()).isEqualTo("localhost");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void logicalName() {
    RedisServerTarget target = RedisServerTarget.ofLogicalName("  mymaster  ");

    assertThat(target.getAddress()).isEqualTo("mymaster");
    assertThat(target.getPort()).isNull();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void emptyLogicalName(String name) {
    assertThat(RedisServerTarget.ofLogicalName(name)).isNull();
  }

  @Test
  void discoveryEndpointsAreSortedDeduplicatedAndIndependentlyScoped() {
    RedisServerTarget target =
        RedisServerTarget.ofEndpointsAndLogicalName(
            asList("redis://sentinel2:26380", "redis://sentinel1:26379", "redis://sentinel2:26380"),
            "  mymaster  ");

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379/mymaster,sentinel2:26380/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleDiscoveryEndpointKeepsItsPortInTheAddress() {
    RedisServerTarget target =
        RedisServerTarget.ofEndpointsAndLogicalName(
            singletonList("redis://sentinel1:26379"), "mymaster");

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void discoveryTargetSanitizesAndBracketsEndpoints() {
    RedisServerTarget target =
        RedisServerTarget.ofEndpointsAndLogicalName(
            asList("redis://user:password@[::2]:26380/2", "redis://[::1]:26379?timeout=5s"),
            "mymaster");

    assertThat(target.getAddress()).isEqualTo("[::1]:26379/mymaster,[::2]:26380/mymaster");
  }

  @Test
  void discoveryTargetFallsBackToSortedEndpoints() {
    RedisServerTarget target =
        RedisServerTarget.ofEndpointsAndLogicalName(
            asList("sentinel2:26380", "sentinel1:26379"), " ");

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void discoveryTargetFallsBackToLogicalName() {
    RedisServerTarget target =
        RedisServerTarget.ofEndpointsAndLogicalName(
            asList("", "redis://", "://sentinel"), "mymaster");

    assertThat(target.getAddress()).isEqualTo("mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void endpointListCarriesEveryEndpointAndNoPort() {
    RedisServerTarget target =
        RedisServerTarget.ofEndpoints(asList("node1:6379", "node2:6380", "node3:6381"));

    assertThat(target.getAddress()).isEqualTo("node1:6379,node2:6380,node3:6381");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void endpointListOmitsSchemes() {
    RedisServerTarget target =
        RedisServerTarget.ofEndpoints(asList("redis://node1:6379", "rediss://node2:6380"));

    assertThat(target.getAddress()).isEqualTo("node1:6379,node2:6380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singletonEndpointListIsSingular() {
    RedisServerTarget target = RedisServerTarget.ofEndpoints(singletonList("node1:6379"));

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void repeatedEndpointIsSingular() {
    RedisServerTarget target =
        RedisServerTarget.ofEndpoints(asList("node1:6379", "node1:6379", "node1:6379"));

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void duplicatesAreCollapsedInOrder() {
    RedisServerTarget target =
        RedisServerTarget.ofEndpoints(asList("node2:6380", "node1:6379", "node2:6380"));

    assertThat(target.getAddress()).isEqualTo("node2:6380,node1:6379");
  }

  @Test
  void unusableEndpointsAreSkipped() {
    RedisServerTarget target =
        RedisServerTarget.ofEndpoints(asList("", "node1:6379", "   ", "node2:6380"));

    assertThat(target.getAddress()).isEqualTo("node1:6379,node2:6380");
  }

  @ParameterizedTest
  @NullAndEmptySource
  void emptyEndpointList(List<String> endpoints) {
    assertThat(RedisServerTarget.ofEndpoints(endpoints)).isNull();
  }

  @Test
  void onlyUnusableEndpoints() {
    assertThat(RedisServerTarget.ofEndpoints(asList("", "  ", "://nowhere"))).isNull();
    assertThat(RedisServerTarget.ofEndpoints(emptyList())).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "redis://user:password@localhost:6379, localhost, 6379",
    "redis://:password@localhost:6379, localhost, 6379",
    "redis://user@localhost:6379, localhost, 6379",
    "user:password@localhost:6379, localhost, 6379",
    "redis://user:p%40ss@localhost:6379, localhost, 6379",
    "redis://localhost:6379/2, localhost, 6379",
    "redis://localhost:6379/, localhost, 6379",
    "redis://localhost:6379?timeout=5s, localhost, 6379",
    "redis://localhost:6379/2?timeout=5s, localhost, 6379",
    "redis://localhost:6379#fragment, localhost, 6379",
    "redis://localhost:6379/2?timeout=5s#fragment, localhost, 6379",
    "redis://user:pass@localhost:6379/2?timeout=5s#fragment, localhost, 6379",
    "redis://localhost, localhost, ",
    "localhost, localhost, ",
    "redis://[::1]:6379, ::1, 6379",
    "[2001:db8::1]:6380, 2001:db8::1, 6380",
    "redis://[::1], ::1, ",
    "::1, ::1, ",
    "localhost:banana, localhost:banana, ",
    "localhost:70000, localhost:70000, ",
    "localhost:, localhost:, ",
    "localhost:-1, localhost:-1, ",
    "localhost:6379x, localhost:6379x, ",
  })
  void sanitizesEndpoint(String endpoint, String address, Integer port) {
    RedisServerTarget target = RedisServerTarget.ofEndpoint(endpoint);

    assertThat(target.getAddress()).isEqualTo(address);
    assertThat(target.getPort()).isEqualTo(port);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {
        "  ",
        "://localhost:6379",
        "redis://",
        "redis-socket://",
        "unix://",
        "redis://?timeout=5s",
        "redis-socket://?db=1",
        "unix://#fragment",
        "redis://default:sec/ret@localhost:6379",
        "redis://default:sec?ret@localhost:6379",
        "redis://default:sec#ret@localhost:6379"
      })
  void unusableEndpoint(String endpoint) {
    assertThat(RedisServerTarget.ofEndpoint(endpoint)).isNull();
  }

  @Test
  void endpointListDropsAmbiguousCredentials() {
    assertThat(
            RedisServerTarget.ofEndpoints(
                asList(
                    "redis://user:sec/ret@node1:6379",
                    "redis://user:sec?ret@node2:6379",
                    "redis://user:sec#ret@node3:6379")))
        .isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "redis-socket:///var/run/redis.sock, /var/run/redis.sock",
    "unix:///var/run/redis.sock, /var/run/redis.sock",
    "redis-socket://user:password@/var/run/redis.sock, /var/run/redis.sock",
    "/var/run/redis.sock, /var/run/redis.sock",
    "redis-socket:///var/run/redis.sock?db=1, /var/run/redis.sock",
  })
  void socketEndpointKeepsPathAndDropsPort(String endpoint, String address) {
    RedisServerTarget target = RedisServerTarget.ofEndpoint(endpoint);

    assertThat(target.getAddress()).isEqualTo(address);
    assertThat(target.getPort()).isNull();
  }

  @Test
  void socketEndpointsInAList() {
    RedisServerTarget target =
        RedisServerTarget.ofEndpoints(
            asList("redis-socket:///var/run/redis1.sock", "redis-socket:///var/run/redis2.sock"));

    assertThat(target.getAddress()).isEqualTo("/var/run/redis1.sock,/var/run/redis2.sock");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void socketEndpointCredentialsFailClosedWhenAmbiguous() {
    assertThat(RedisServerTarget.ofEndpoint("redis-socket://user:pa/ss@/var/run/redis.sock"))
        .isNull();
    assertThat(RedisServerTarget.ofEndpoint("redis-socket://user:pa?ss@/var/run/redis.sock"))
        .isNull();
    assertThat(RedisServerTarget.ofEndpoint("redis-socket://user:pa#ss@/var/run/redis.sock"))
        .isNull();
    String passwordContainingAt =
        "redis-socket://" + "user:pa" + "@" + "ss" + "@" + "/var/run/redis.sock";
    assertThat(RedisServerTarget.ofEndpoint(passwordContainingAt))
        .extracting(RedisServerTarget::getAddress)
        .isEqualTo("/var/run/redis.sock");
    assertThat(RedisServerTarget.ofEndpoint("redis-socket:///var/run/user@redis.sock"))
        .extracting(RedisServerTarget::getAddress)
        .isEqualTo("/var/run/user@redis.sock");
  }

  @Test
  void endpointListStripsCredentials() {
    RedisServerTarget target =
        RedisServerTarget.ofEndpoints(
            asList("redis://user:password@node1:6379/1", "redis://user:password@node2:6380/1"));

    assertThat(target.getAddress()).isEqualTo("node1:6379,node2:6380");
  }

  @Test
  void endpointListBracketsIpv6() {
    RedisServerTarget target = RedisServerTarget.ofEndpoints(asList("[::1]:6379", "[::2]:6380"));

    assertThat(target.getAddress()).isEqualTo("[::1]:6379,[::2]:6380");
  }

  @Test
  void endpointRendering() {
    assertThat(RedisServerTarget.endpoint("localhost", 6379)).isEqualTo("localhost:6379");
    assertThat(RedisServerTarget.endpoint("localhost", -1)).isEqualTo("localhost");
    assertThat(RedisServerTarget.endpoint("::1", 6379)).isEqualTo("[::1]:6379");
    assertThat(RedisServerTarget.endpoint("[::1]", 6379)).isEqualTo("[::1]:6379");
    assertThat(RedisServerTarget.endpoint(null, 6379)).isEmpty();
  }

  @Test
  void nullHost() {
    assertThat(RedisServerTarget.ofHostAndPort(null, 6379)).isNull();
  }
}
