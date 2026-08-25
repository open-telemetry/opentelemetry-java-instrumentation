/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;

class LettuceServerTargetsTest {

  @Test
  void standalone() {
    RedisServerTarget target = LettuceServerTargets.of(RedisURI.create("redis://host:6379"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void standaloneDropsCredentialsAndDatabase() {
    RedisServerTarget target =
        LettuceServerTargets.of(RedisURI.create("redis://user:password@host:6379/2"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void sentinelIsNamedByItsMaster() {
    RedisServerTarget target =
        LettuceServerTargets.of(
            RedisURI.Builder.sentinel("sentinel1", 26379, "mymaster")
                .withSentinel("sentinel2", 26380)
                .build());

    assertThat(target.getAddress()).isEqualTo("mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelWithoutMasterKeepsTheSentinelEndpoints() {
    RedisURI redisUri = RedisURI.Builder.sentinel("sentinel1", 26379, "mymaster").build();
    redisUri.getSentinels().add(RedisURI.create("redis://sentinel2:26380"));
    redisUri.setSentinelMasterId(null);

    RedisServerTarget target = LettuceServerTargets.of(redisUri);

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void socket() {
    RedisServerTarget target =
        LettuceServerTargets.of(RedisURI.Builder.socket("/var/run/redis.sock").build());

    assertThat(target.getAddress()).isEqualTo("/var/run/redis.sock");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void laterUriChangesDoNotChangeTheTarget() {
    RedisURI redisUri = RedisURI.create("redis://host:6379");
    RedisServerTarget target = LettuceServerTargets.of(redisUri);

    redisUri.setHost("other");
    redisUri.setPort(6380);

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void noUri() {
    assertThat(LettuceServerTargets.of(null)).isNull();
  }

  @Test
  void clusterKeepsEveryConfiguredEndpoint() {
    RedisServerTarget target =
        LettuceServerTargets.ofUris(
            asList(RedisURI.create("redis://node1:7000"), RedisURI.create("redis://node2:7001")));

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterWithOneEndpointKeepsItsPort() {
    RedisServerTarget target =
        LettuceServerTargets.ofUris(singletonList(RedisURI.create("redis://node1:7000")));

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void clusterDropsCredentialsAndDatabase() {
    RedisServerTarget target =
        LettuceServerTargets.ofUris(
            asList(
                RedisURI.create("redis://user:pass@node1:7000/2"),
                RedisURI.create("redis://node2:7001")));

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void laterClusterUriChangesDoNotChangeTheTarget() {
    RedisURI first = RedisURI.create("redis://node1:7000");
    RedisURI second = RedisURI.create("redis://node2:7001");
    RedisServerTarget target = LettuceServerTargets.ofUris(asList(first, second));

    first.setHost("other");
    second.setPort(7002);

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void noClusterUris() {
    assertThat(LettuceServerTargets.ofUris(null)).isNull();
    assertThat(LettuceServerTargets.ofUris(emptyList())).isNull();
  }
}
