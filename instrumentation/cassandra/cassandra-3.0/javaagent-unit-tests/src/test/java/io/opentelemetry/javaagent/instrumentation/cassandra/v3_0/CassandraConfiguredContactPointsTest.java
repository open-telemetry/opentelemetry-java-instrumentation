/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class CassandraConfiguredContactPointsTest {

  @Test
  void createsSingleHostTargetWithoutResolvingIt() {
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(new String[] {"db.example"}, 9042);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("db.example");
    assertThat(target.getPort()).isEqualTo(9042);
  }

  @Test
  void createsOrderedMultiHostTarget() throws UnknownHostException {
    InetAddress address = InetAddress.getByAddress(new byte[] {10, 0, 0, 1});
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(
            asList(
                "db.example", address, InetSocketAddress.createUnresolved("other.example", 9142)),
            9042);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("db.example:9042,10.0.0.1:9042,other.example:9142");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void preservesUnresolvedSocketAddress() {
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(
            InetSocketAddress.createUnresolved("unresolved.example", 9042), 9142);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("unresolved.example");
    assertThat(target.getPort()).isEqualTo(9042);
  }

  @Test
  void formatsIpv6InMultiHostTarget() throws UnknownHostException {
    InetAddress address =
        InetAddress.getByAddress(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(asList(address, "db.example"), 9042);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[0:0:0:0:0:0:0:1]:9042,db.example:9042");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void ignoresInvalidContactPointData() {
    CassandraConfiguredTarget target =
        CassandraConfiguredTarget.create(new Object[] {null, "", new Object()}, 9042);

    assertThat(target).isNull();
    assertThat(CassandraConfiguredTarget.create("db.example", 0)).isNull();
  }
}
