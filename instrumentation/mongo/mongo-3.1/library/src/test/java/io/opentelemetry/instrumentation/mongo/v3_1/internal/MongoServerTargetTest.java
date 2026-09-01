/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.ConnectionString;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterSettings;
import org.junit.jupiter.api.Test;

class MongoServerTargetTest {

  @Test
  void omittedAndMaterializedDefaultPortsAreNotReported() {
    MongoServerTarget omitted =
        MongoServerTarget.seeds(singletonList(new ServerAddress("db1.example")));
    MongoServerTarget materialized =
        MongoServerTarget.seeds(singletonList(new ServerAddress("db1.example", 27017)));

    assertThat(omitted.getAddress()).isEqualTo("db1.example");
    assertThat(omitted.getPort()).isNull();
    assertThat(materialized.getAddress()).isEqualTo("db1.example");
    assertThat(materialized.getPort()).isNull();
  }

  @Test
  void singleCustomPortIsReportedSeparately() {
    MongoServerTarget target =
        MongoServerTarget.seeds(singletonList(new ServerAddress("db1.example", 27018)));

    assertThat(target.getAddress()).isEqualTo("db1.example");
    assertThat(target.getPort()).isEqualTo(27018);
  }

  @Test
  void defaultPortSeedGroupsHaveAStableOrderWithoutPorts() {
    MongoServerTarget target =
        MongoServerTarget.seeds(
            asList(
                new ServerAddress("db2.example", 27017), new ServerAddress("db1.example", 27017)));

    assertThat(target.getAddress()).isEqualTo("db1.example,db2.example");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sharedCustomPortIsReportedSeparately() {
    MongoServerTarget target =
        MongoServerTarget.seeds(
            asList(
                new ServerAddress("db2.example", 27018), new ServerAddress("db1.example", 27018)));

    assertThat(target.getAddress()).isEqualTo("db1.example,db2.example");
    assertThat(target.getPort()).isEqualTo(27018);
  }

  @Test
  void mixedPortSeedGroupsRetainPortsInTheAddress() {
    MongoServerTarget target =
        MongoServerTarget.seeds(
            asList(
                new ServerAddress("db2.example", 27018), new ServerAddress("db1.example", 27017)));

    assertThat(target.getAddress()).isEqualTo("db1.example:27017,db2.example:27018");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void duplicateSeedsArePreserved() {
    MongoServerTarget target =
        MongoServerTarget.seeds(
            asList(
                new ServerAddress("db2.example", 27018),
                new ServerAddress("db1.example", 27017),
                new ServerAddress("db1.example", 27017)));

    assertThat(target.getAddress())
        .isEqualTo("db1.example:27017,db1.example:27017,db2.example:27018");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleIpv6SeedIsNotBracketed() {
    MongoServerTarget target =
        MongoServerTarget.seeds(singletonList(new ServerAddress("[::1]", 27017)));

    assertThat(target.getAddress()).isEqualTo("::1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void anAlreadyBracketedIpv6SeedIsUnwrapped() {
    MongoServerTarget target = MongoServerTarget.seeds(singletonList(bracketedSeed("::1", 27018)));

    assertThat(target.getAddress()).isEqualTo("::1");
    assertThat(target.getPort()).isEqualTo(27018);
  }

  @Test
  void severalIpv6SeedsAreBracketed() {
    MongoServerTarget target =
        MongoServerTarget.seeds(
            asList(bracketedSeed("fe80::1", 27018), bracketedSeed("::1", 27017)));

    assertThat(target.getAddress()).isEqualTo("[::1]:27017,[fe80::1]:27018");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void severalIpv6SeedsWithASharedPortAreNotBracketed() {
    MongoServerTarget target =
        MongoServerTarget.seeds(
            asList(bracketedSeed("fe80::1", 27018), bracketedSeed("::1", 27018)));

    assertThat(target.getAddress()).isEqualTo("::1,fe80::1");
    assertThat(target.getPort()).isEqualTo(27018);
  }

  @Test
  void scopedIpv6SeedIsPreserved() {
    MongoServerTarget target =
        MongoServerTarget.seeds(singletonList(seedWithHost("[fe80::1%eth0]", 27018)));

    assertThat(target.getAddress()).isEqualTo("fe80::1%eth0");
    assertThat(target.getPort()).isEqualTo(27018);
  }

  @Test
  void unixSocketSeedCarriesNoPort() {
    MongoServerTarget target =
        MongoServerTarget.seeds(singletonList(new ServerAddress("/tmp/mongodb-27017.sock")));

    assertThat(target.getAddress()).isEqualTo("/tmp/mongodb-27017.sock");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void multipleUnixSocketSeedsAreNotReported() {
    assertThat(
            MongoServerTarget.seeds(
                asList(
                    new ServerAddress("/tmp/mongodb-27018.sock"),
                    new ServerAddress("/tmp/mongodb-27017.sock"))))
        .isNull();
  }

  @Test
  void mixedUnixSocketAndTcpSeedsAreNotReported() {
    assertThat(
            MongoServerTarget.seeds(
                asList(
                    new ServerAddress("/tmp/mongodb-27017.sock"),
                    new ServerAddress("db.example", 27018))))
        .isNull();
  }

  @Test
  void hostnameEndingInSockIsNotTreatedAsAUnixSocket() {
    MongoServerTarget target =
        MongoServerTarget.seeds(singletonList(new ServerAddress("db.sock", 27018)));

    assertThat(target.getAddress()).isEqualTo("db.sock");
    assertThat(target.getPort()).isEqualTo(27018);
  }

  @Test
  void srvHostUsesTheNativeDiscoveryIdentity() {
    MongoServerTarget target = MongoServerTarget.srvHost("cluster0.example.com");

    assertThat(target.getAddress()).isEqualTo("mongodb+srv://cluster0.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void srvHostOmitsCredentialsPathQueryAndFragment() {
    MongoServerTarget target =
        MongoServerTarget.srvHost(
            "mongodb+srv://user:password@cluster0.example.com/database?tls=true#fragment");

    assertThat(target.getAddress()).isEqualTo("mongodb+srv://cluster0.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void unsafeEncodedSrvIdentityIsNotReported() {
    assertThat(
            MongoServerTarget.srvConnectionString(
                "mongodb+srv://user%3Apassword%40cluster0.example.com"))
        .isNull();
    assertThat(MongoServerTarget.srvConnectionString("mongodb://cluster0.example.com")).isNull();
  }

  @Test
  void unknownTargetsAreNotReported() {
    assertThat(MongoServerTarget.srvHost(null)).isNull();
    assertThat(MongoServerTarget.srvHost("")).isNull();
    assertThat(MongoServerTarget.seeds(null)).isNull();
    assertThat(MongoServerTarget.seeds(emptyList())).isNull();
  }

  @Test
  void unsafeDirectSeedHostsAreNotReported() {
    assertThat(
            MongoServerTarget.seeds(
                singletonList(seedWithHost("user:password@example.com", 27017))))
        .isNull();
    assertThat(
            MongoServerTarget.seeds(
                singletonList(seedWithHost("user%3Apassword%40example.com", 27017))))
        .isNull();
    assertThat(
            MongoServerTarget.seeds(
                singletonList(seedWithHost("mongodb://user:password@example.com", 27017))))
        .isNull();
    assertThat(
            MongoServerTarget.seeds(
                asList(
                    new ServerAddress("safe.example", 27017),
                    seedWithHost("unsafe.example?authSource=admin", 27017))))
        .isNull();
    assertThat(MongoServerTarget.seeds(singletonList(seedWithHost("apiKey=secret", 27017))))
        .isNull();
    assertThat(
            MongoServerTarget.seeds(singletonList(seedWithHost("/tmp/apiKey=secret.sock", 27017))))
        .isNull();
    assertThat(MongoServerTarget.seeds(singletonList(seedWithHost("abc:def:123", 27017)))).isNull();
    assertThat(MongoServerTarget.seeds(singletonList(seedWithHost("[::1%3Apassword]", 27017))))
        .isNull();
  }

  @Test
  void multiSeedConnectionStringIsReportedAsOneLogicalTarget() {
    ClusterSettings settings =
        ClusterSettings.builder()
            .applyConnectionString(
                new ConnectionString(
                    "mongodb://user:pass@db2.example:27018,db1.example:27017/mydb"
                        + "?replicaSet=rs0&ssl=true"))
            .build();

    MongoServerTarget target = MongoServerTarget.seeds(settings.getHosts());

    assertThat(settings.getRequiredReplicaSetName()).isEqualTo("rs0");
    assertThat(target.getAddress()).isEqualTo("db1.example:27017,db2.example:27018");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void connectionStringDefaultsAreNormalizedAfterTheDriverMaterializesThem() {
    ClusterSettings settings =
        ClusterSettings.builder()
            .applyConnectionString(new ConnectionString("mongodb://db2.example,db1.example/mydb"))
            .build();

    MongoServerTarget target = MongoServerTarget.seeds(settings.getHosts());

    assertThat(target.getAddress()).isEqualTo("db1.example,db2.example");
    assertThat(target.getPort()).isNull();
  }

  // drivers 3.3 through 3.7 preserve IPv6 brackets; the compile-time driver strips them
  private static ServerAddress bracketedSeed(String address, int port) {
    return seedWithHost("[" + address + "]", port);
  }

  private static ServerAddress seedWithHost(String host, int port) {
    return new ServerAddress("safe.example", port) {
      private static final long serialVersionUID = 1L;

      @Override
      public String getHost() {
        return host;
      }
    };
  }
}
