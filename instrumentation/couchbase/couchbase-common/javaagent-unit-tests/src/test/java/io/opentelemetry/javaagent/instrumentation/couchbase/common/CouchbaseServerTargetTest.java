/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CouchbaseServerTargetTest {

  @Test
  void singleSeedOmitsTheDefaultPort() {
    CouchbaseServerTarget.Builder builder = CouchbaseServerTarget.builder("couchbase");
    builder.addSeed("node.example", 11210);

    CouchbaseServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("node.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void secureSingleSeedOmitsTheDefaultPort() {
    CouchbaseServerTarget.Builder builder = CouchbaseServerTarget.builder("COUCHBASES");
    builder.addSeed("node.example", 11207);

    CouchbaseServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("node.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void unqualifiedSeedUsesTheSchemeDefaultPort() {
    CouchbaseServerTarget.Builder builder = CouchbaseServerTarget.builder("couchbases");
    builder.addSeed("cluster.example", 0);

    CouchbaseServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("cluster.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void callerCanSupplyTheDefaultPortForAnotherScheme() {
    CouchbaseServerTarget.Builder builder = CouchbaseServerTarget.builderWithDefaultPort(18098);
    builder.addSeed("cluster.example", 18098);

    CouchbaseServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("cluster.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void severalDefaultPortSeedsAreRenderedWithoutPortsInNormalizedOrder() {
    CouchbaseServerTarget.Builder first = CouchbaseServerTarget.builder("couchbase");
    first.addSeed("two.example", 11210);
    first.addSeed("one.example", 0);

    CouchbaseServerTarget.Builder second = CouchbaseServerTarget.builder("couchbase");
    second.addSeed("one.example", 0);
    second.addSeed("two.example", 11210);

    assertThat(first.build().getAddress()).isEqualTo("one.example,two.example");
    assertThat(first.build().getPort()).isNull();
    assertThat(second.build().getAddress()).isEqualTo("one.example,two.example");
    assertThat(second.build().getPort()).isNull();
  }

  @Test
  void severalSeedsWithTheSameNonDefaultPortUseServerPort() {
    CouchbaseServerTarget.Builder builder = CouchbaseServerTarget.builder("couchbase");
    builder.addSeed("two.example", 11211);
    builder.addSeed("one.example", 11211);

    CouchbaseServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("one.example,two.example");
    assertThat(target.getPort()).isEqualTo(11211);
  }

  @Test
  void severalSeedsWithDifferentPortsKeepInlinePorts() {
    CouchbaseServerTarget.Builder builder = CouchbaseServerTarget.builder("couchbase");
    builder.addSeed("two.example", 11211);
    builder.addSeed("one.example", 0);

    CouchbaseServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("one.example:11210,two.example:11211");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void duplicateSeedsArePreserved() {
    CouchbaseServerTarget.Builder builder = CouchbaseServerTarget.builder("couchbase");
    builder.addSeed("two.example", 11210);
    builder.addSeed("one.example", 0);
    builder.addSeed("two.example", 11210);

    CouchbaseServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("one.example,two.example,two.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void ipv4SeedsAreRenderedAsTheyAreConfigured() {
    CouchbaseServerTarget.Builder single = CouchbaseServerTarget.builder("couchbase");
    single.addSeed("192.0.2.1", 11210);
    assertThat(single.build().getAddress()).isEqualTo("192.0.2.1");
    assertThat(single.build().getPort()).isNull();

    CouchbaseServerTarget.Builder group = CouchbaseServerTarget.builder("couchbase");
    group.addSeed("192.0.2.2", 11211);
    group.addSeed("192.0.2.1", 0);
    assertThat(group.build().getAddress()).isEqualTo("192.0.2.1:11210,192.0.2.2:11211");
  }

  @Test
  void loneIpv6SeedLosesItsBrackets() {
    CouchbaseServerTarget.Builder builder = CouchbaseServerTarget.builder("couchbase");
    builder.addSeed("[2001:db8::1]", 11210);

    CouchbaseServerTarget target = builder.build();
    assertThat(target.getAddress()).isEqualTo("2001:db8::1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void groupedIpv6SeedsAreBracketed() {
    CouchbaseServerTarget.Builder builder = CouchbaseServerTarget.builder("couchbase");
    builder.addSeed("[2001:db8::2]", 11211);
    builder.addSeed("2001:db8::1", 0);

    assertThat(builder.build().getAddress()).isEqualTo("[2001:db8::1]:11210,[2001:db8::2]:11211");
  }

  @Test
  void credentialsPathParametersAndFragmentsAreStripped() {
    CouchbaseServerTarget.Builder builder = CouchbaseServerTarget.builder();
    builder.addSeed("user:secret@node.example/bucket?timeout=5s#anchor", 11210);

    assertThat(builder.build().getAddress()).isEqualTo("node.example");
  }

  @Test
  void seedThatNamesNoHostDropsTheTarget() {
    CouchbaseServerTarget.Builder empty = CouchbaseServerTarget.builder();
    assertThat(empty.build()).isNull();

    CouchbaseServerTarget.Builder blank = CouchbaseServerTarget.builder();
    blank.addSeed("  ", 0);
    assertThat(blank.build()).isNull();

    CouchbaseServerTarget.Builder partial = CouchbaseServerTarget.builder();
    partial.addSeed("one.example", 0);
    partial.addSeed(null, 0);
    assertThat(partial.build()).isNull();
  }

  @Test
  void builtTargetDoesNotFollowLaterSeeds() {
    CouchbaseServerTarget.Builder builder = CouchbaseServerTarget.builder();
    builder.addSeed("one.example", 11210);

    CouchbaseServerTarget target = builder.build();
    builder.addSeed("two.example", 11210);

    assertThat(target.getAddress()).isEqualTo("one.example");
    assertThat(target.getPort()).isEqualTo(11210);
    assertThat(builder.build().getAddress()).isEqualTo("one.example,two.example");
    assertThat(builder.build().getPort()).isEqualTo(11210);
  }
}
