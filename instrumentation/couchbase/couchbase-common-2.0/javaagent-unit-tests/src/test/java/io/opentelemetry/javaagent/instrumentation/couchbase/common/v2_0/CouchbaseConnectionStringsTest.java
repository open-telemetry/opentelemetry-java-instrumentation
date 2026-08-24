/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v2_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.couchbase.client.core.utils.ConnectionString;
import io.opentelemetry.javaagent.instrumentation.couchbase.common.CouchbaseServerTarget;
import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class CouchbaseConnectionStringsTest {

  @Test
  void readsAConnectionStringTheDriverParsed() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target(ConnectionString.create("couchbase://node:11210"));

    assertThat(target.getAddress()).isEqualTo("node");
    assertThat(target.getPort()).isEqualTo(11210);
  }

  @Test
  void readsSeveralSeedsInTheOrderTheyWereConfigured() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target(
            ConnectionString.create("couchbases://two.example,one.example"));

    assertThat(target.getAddress()).isEqualTo("couchbases://two.example,one.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void stripsCredentialsFromAConnectionStringTheDriverParsed() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target(ConnectionString.create("couchbase://user@node.example"));

    assertThat(target.getAddress()).isEqualTo("node.example");
  }

  @Test
  void readsTheSeedsOfTheDriver20Shape() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target(
            new SeedListConnectionString(
                "COUCHBASE",
                asList(
                    InetSocketAddress.createUnresolved("one.example", 0),
                    InetSocketAddress.createUnresolved("two.example", 11210))));

    assertThat(target.getAddress()).isEqualTo("couchbase://one.example,two.example:11210");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void prefersTheConfiguredSeedsOverTheResolvedOnes() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target(
            new ResolvedSeedListConnectionString(
                "COUCHBASE",
                asList(
                    InetSocketAddress.createUnresolved("resolvable.example", 0),
                    InetSocketAddress.createUnresolved("unresolvable.example", 0)),
                singletonList(InetSocketAddress.createUnresolved("resolvable.example", 0))));

    assertThat(target.getAddress())
        .isEqualTo("couchbase://resolvable.example,unresolvable.example");
  }

  @Test
  void readsTheSeedsOfTheDriver27Shape() {
    CouchbaseServerTarget target =
        CouchbaseConnectionStrings.target(
            new SeedListConnectionString(
                "COUCHBASES", asList(new Seed("2001:db8::1", 11207), new Seed("node.example", 0))));

    assertThat(target.getAddress()).isEqualTo("couchbases://[2001:db8::1]:11207,node.example");
  }

  @Test
  void loneSeedNamesItselfWhateverTheShape() {
    assertThat(
            CouchbaseConnectionStrings.target(
                    new SeedListConnectionString(
                        "COUCHBASE", singletonList(new Seed("cluster.example", 0))))
                .getAddress())
        .isEqualTo("cluster.example");
    assertThat(
            CouchbaseConnectionStrings.target(
                    new SeedListConnectionString(
                        "COUCHBASE",
                        singletonList(InetSocketAddress.createUnresolved("[2001:db8::1]", 11210))))
                .getAddress())
        .isEqualTo("2001:db8::1");
  }

  @Test
  void anUnknownShapeHasNoTarget() {
    assertThat(CouchbaseConnectionStrings.target(null)).isNull();
    assertThat(CouchbaseConnectionStrings.target("couchbase://node.example")).isNull();
    assertThat(
            CouchbaseConnectionStrings.target(
                new SeedListConnectionString("COUCHBASE", emptyList())))
        .isNull();
  }

  /** A connection string of the shape driver 2.0 to 2.3 and driver 2.7 expose. */
  public static class SeedListConnectionString {

    private final String scheme;
    private final List<?> hosts;

    SeedListConnectionString(String scheme, List<?> hosts) {
      this.scheme = scheme;
      this.hosts = hosts;
    }

    public String scheme() {
      return scheme;
    }

    public List<?> hosts() {
      return hosts;
    }
  }

  /** A connection string of the shape driver 2.4 to 2.6 expose, which drops unresolved seeds. */
  public static class ResolvedSeedListConnectionString extends SeedListConnectionString {

    private final List<?> allHosts;

    ResolvedSeedListConnectionString(String scheme, List<?> allHosts, List<?> resolvedHosts) {
      super(scheme, resolvedHosts);
      this.allHosts = allHosts;
    }

    public List<?> allHosts() {
      return allHosts;
    }
  }

  /** A seed of the shape driver 2.7 exposes. */
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
