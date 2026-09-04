/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.core.utils.ConnectionString;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class CouchbaseConnectionStringsTest {

  @Test
  void omitsTheCouchbaseDefaultPort() {
    DbServerTarget target =
        CouchbaseConnectionStrings.target(ConnectionString.create("couchbase://node:11210"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("node");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void omitsTheCouchbasesDefaultPort() {
    DbServerTarget target =
        CouchbaseConnectionStrings.target(ConnectionString.create("couchbases://node:11207"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("node");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void keepsSharedCouchbasesNonDefaultPortsInline() {
    DbServerTarget target =
        CouchbaseConnectionStrings.target(
            ConnectionString.create("couchbases://two.example:11208,one.example:11208"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("two.example:11208,one.example:11208");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void keepsDifferentCouchbasesPortsInline() {
    DbServerTarget target =
        CouchbaseConnectionStrings.target(
            ConnectionString.create("couchbases://two.example:11208,one.example"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("two.example:11208,one.example:11207");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void readsSeveralSeedsInConfiguredOrder() {
    DbServerTarget target =
        CouchbaseConnectionStrings.target(
            ConnectionString.create("couchbases://two.example,one.example"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("two.example,one.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void stripsCredentialsFromAConnectionStringTheDriverParsed() {
    DbServerTarget target =
        CouchbaseConnectionStrings.target(ConnectionString.create("couchbase://user@node.example"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("node.example");
  }

  @Test
  void readsTheSeedsOfTheDriver20Shape() {
    DbServerTarget target =
        CouchbaseConnectionStrings.target(
            new SeedListConnectionString(
                asList(
                    InetSocketAddress.createUnresolved("one.example", 0),
                    InetSocketAddress.createUnresolved("two.example", 11210))));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("one.example,two.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void keepsCommonNonDefaultPortsInline() {
    DbServerTarget target =
        CouchbaseConnectionStrings.target(
            new SeedListConnectionString(
                asList(
                    InetSocketAddress.createUnresolved("two.example", 11211),
                    InetSocketAddress.createUnresolved("one.example", 11211))));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("two.example:11211,one.example:11211");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void keepsDifferentPortsInline() {
    DbServerTarget target =
        CouchbaseConnectionStrings.target(
            new SeedListConnectionString(
                asList(
                    InetSocketAddress.createUnresolved("two.example", 11211),
                    InetSocketAddress.createUnresolved("one.example", 0))));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("two.example:11211,one.example:11210");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void prefersTheConfiguredSeedsOverTheResolvedOnes() {
    DbServerTarget target =
        CouchbaseConnectionStrings.target(
            new ResolvedSeedListConnectionString(
                asList(
                    InetSocketAddress.createUnresolved("resolvable.example", 0),
                    InetSocketAddress.createUnresolved("unresolvable.example", 0)),
                singletonList(InetSocketAddress.createUnresolved("resolvable.example", 0))));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("resolvable.example,unresolvable.example");
  }

  @Test
  void readsTheSeedsOfTheDriver27Shape() {
    DbServerTarget target =
        CouchbaseConnectionStrings.target(
            new SeedListConnectionString(
                asList(new Seed("node.example", 0), new Seed("2001:db8::1", 11207)), "COUCHBASES"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("node.example,2001:db8::1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void loneSeedNamesItselfWhateverTheShape() {
    DbServerTarget seedTarget =
        CouchbaseConnectionStrings.target(
            new SeedListConnectionString(singletonList(new Seed("cluster.example", 0))));
    DbServerTarget socketTarget =
        CouchbaseConnectionStrings.target(
            new SeedListConnectionString(
                singletonList(InetSocketAddress.createUnresolved("[2001:db8::1]", 11210))));

    assertThat(seedTarget).isNotNull();
    assertThat(seedTarget.getAddress()).isEqualTo("cluster.example");
    assertThat(socketTarget).isNotNull();
    assertThat(socketTarget.getAddress()).isEqualTo("2001:db8::1");
  }

  @Test
  void anUnknownShapeHasNoTarget() {
    assertThat(CouchbaseConnectionStrings.target(null)).isNull();
    assertThat(CouchbaseConnectionStrings.target("couchbase://node.example")).isNull();
    assertThat(CouchbaseConnectionStrings.target(new SeedListConnectionString(emptyList())))
        .isNull();
  }

  // Shape exposed by drivers up to 2.5.6, which expose only hosts.
  public static class SeedListConnectionString {

    private final List<?> hosts;
    private final String scheme;

    SeedListConnectionString(List<?> hosts) {
      this(hosts, "COUCHBASE");
    }

    SeedListConnectionString(List<?> hosts, String scheme) {
      this.hosts = hosts;
      this.scheme = scheme;
    }

    public List<?> hosts() {
      return hosts;
    }

    public String scheme() {
      return scheme;
    }
  }

  // Shape exposed by drivers 2.5.7 and later, which drop unresolved seeds from hosts and keep the
  // configured seeds in allHosts.
  public static class ResolvedSeedListConnectionString extends SeedListConnectionString {

    private final List<?> allHosts;

    ResolvedSeedListConnectionString(List<?> allHosts, List<?> resolvedHosts) {
      super(resolvedHosts);
      this.allHosts = allHosts;
    }

    public List<?> allHosts() {
      return allHosts;
    }
  }

  // Seed shape exposed by driver 2.7.
  public static class Seed {

    private final String hostname;
    private final int port;

    Seed(String hostname, int port) {
      this.hostname = hostname;
      this.port = port;
    }

    public String hostname() {
      return hostname;
    }

    public int port() {
      return port;
    }
  }
}
