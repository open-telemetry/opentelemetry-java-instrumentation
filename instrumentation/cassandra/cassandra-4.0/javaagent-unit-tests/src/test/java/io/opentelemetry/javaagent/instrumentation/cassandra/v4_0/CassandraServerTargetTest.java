/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.session.Session;
import com.datastax.oss.driver.internal.core.context.InternalDriverContext;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.DefaultNode;
import com.datastax.oss.driver.internal.core.metadata.MetadataManager;
import java.net.InetSocketAddress;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CassandraServerTargetTest {

  @Mock private Session session;
  @Mock private InternalDriverContext context;
  @Mock private MetadataManager metadataManager;
  @Mock private DefaultNode configuredNode;
  @Mock private DefaultNode programmaticNode;

  @Test
  void singleContactPointKeepsItsHostAndPort() {
    CassandraServerTarget target =
        CassandraServerTarget.of(singletonList("cassandra.example.com:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("cassandra.example.com");
    assertThat(target.getPort()).isEqualTo(9042);
  }

  @Test
  void singleIpv6ContactPointLosesItsBrackets() {
    // server.address holds a bare address, unlike a group where brackets keep the port unambiguous
    CassandraServerTarget target = CassandraServerTarget.of(singletonList("[::1]:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::1");
    assertThat(target.getPort()).isEqualTo(9042);
  }

  @Test
  void contactPointWithoutAPortIsIgnored() {
    CassandraServerTarget target = CassandraServerTarget.of(singletonList("cassandra.example.com"));

    assertThat(target).isNull();
  }

  @Test
  void severalContactPointsBecomeOneAddressWithoutAPort() {
    CassandraServerTarget target =
        CassandraServerTarget.of(asList("node1.example.com:9042", "10.0.0.5:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("node1.example.com:9042,10.0.0.5:9042");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void ipv6ContactPointsStayBracketedInAGroup() {
    CassandraServerTarget target =
        CassandraServerTarget.of(asList("[::1]:9042", "2001:db8::1:9042", "10.0.0.5:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[::1]:9042,[2001:db8::1]:9042,10.0.0.5:9042");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void invalidContactPointsAreIgnored() {
    assertThat(CassandraServerTarget.of(emptyList())).isNull();
    assertThat(CassandraServerTarget.of((List<String>) null)).isNull();
    assertThat(CassandraServerTarget.of(singletonList("  "))).isNull();
    assertThat(CassandraServerTarget.of(singletonList("node.example.com:not-a-port"))).isNull();

    CassandraServerTarget target =
        CassandraServerTarget.of(
            asList("missing-port", "node.example.com:9042", "invalid:not-a-port", "[::1]:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("node.example.com:9042,[::1]:9042");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sessionThatNamesNoContactPointHasNoTarget() {
    when(session.getContext()).thenReturn(context);
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.wasImplicitContactPoint()).thenReturn(true);

    assertThat(CassandraServerTarget.of(session)).isNull();
  }

  @Test
  void sessionUsesMergedProgrammaticAndConfiguredContactPoints() {
    Set<DefaultNode> contactPoints = new LinkedHashSet<>(asList(configuredNode, programmaticNode));
    when(session.getContext()).thenReturn(context);
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.getContactPoints()).thenReturn(contactPoints);
    when(configuredNode.getEndPoint())
        .thenReturn(
            new DefaultEndPoint(
                InetSocketAddress.createUnresolved("configured.example.com", 9042)));
    when(programmaticNode.getEndPoint())
        .thenReturn(
            new DefaultEndPoint(
                InetSocketAddress.createUnresolved("programmatic.example.com", 9142)));

    CassandraServerTarget target = CassandraServerTarget.of(session);

    assertThat(target).isNotNull();
    assertThat(target.getAddress())
        .isEqualTo("configured.example.com:9042,programmatic.example.com:9142");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void theTargetDoesNotFollowLaterChangesToTheMergedContactPoints() {
    Set<DefaultNode> contactPoints = new LinkedHashSet<>(singletonList(configuredNode));
    when(session.getContext()).thenReturn(context);
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.getContactPoints()).thenReturn(contactPoints);
    when(configuredNode.getEndPoint())
        .thenReturn(
            new DefaultEndPoint(
                InetSocketAddress.createUnresolved("configured.example.com", 9042)));

    CassandraServerTarget target = CassandraServerTarget.of(session);

    contactPoints.clear();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("configured.example.com");
    assertThat(target.getPort()).isEqualTo(9042);
  }
}
