/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfig;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.session.Session;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader;
import com.datastax.oss.driver.internal.core.context.InternalDriverContext;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
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
  @Mock private DriverConfig config;
  @Mock private DriverExecutionProfile defaultProfile;
  @Mock private EndPoint customEndPoint;

  @Test
  void singleContactPointOmitsTheDefaultPort() {
    CassandraServerTarget target =
        CassandraServerTarget.of(singletonList("cassandra.example.com:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("cassandra.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void singleContactPointExtractsANonDefaultPort() {
    CassandraServerTarget target =
        CassandraServerTarget.of(singletonList("cassandra.example.com:9142"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("cassandra.example.com");
    assertThat(target.getPort()).isEqualTo(9142);
  }

  @Test
  void singleIpv6ContactPointLosesItsBrackets() {
    CassandraServerTarget target = CassandraServerTarget.of(singletonList("[::1]:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void contactPointWithoutAPortIsIgnored() {
    CassandraServerTarget target = CassandraServerTarget.of(singletonList("cassandra.example.com"));

    assertThat(target).isNull();
  }

  @Test
  void severalContactPointsOmitTheSharedDefaultPort() {
    CassandraServerTarget target =
        CassandraServerTarget.of(asList("node1.example.com:9042", "10.0.0.5:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.5,node1.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void severalContactPointsExtractASharedNonDefaultPort() {
    CassandraServerTarget target =
        CassandraServerTarget.of(asList("node1.example.com:9142", "10.0.0.5:9142"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.5,node1.example.com");
    assertThat(target.getPort()).isEqualTo(9142);
  }

  @Test
  void mixedPortContactPointPermutationsHaveTheSameOrder() {
    CassandraServerTarget first =
        CassandraServerTarget.of(asList("node2.example.com:9142", "node1.example.com:9042"));
    CassandraServerTarget second =
        CassandraServerTarget.of(asList("node1.example.com:9042", "node2.example.com:9142"));

    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(first.getAddress()).isEqualTo("node1.example.com:9042,node2.example.com:9142");
    assertThat(second.getAddress()).isEqualTo(first.getAddress());
    assertThat(first.getPort()).isNull();
    assertThat(second.getPort()).isNull();
  }

  @Test
  void duplicateConfiguredContactPointsArePreserved() {
    CassandraServerTarget target =
        CassandraServerTarget.of(
            asList("cassandra.example.com:9042", "cassandra.example.com:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("cassandra.example.com,cassandra.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void ipv6ContactPointsStayBracketedWhenPortsAreMixed() {
    CassandraServerTarget target =
        CassandraServerTarget.of(asList("[::1]:9042", "2001:db8::1:9142", "10.0.0.5:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.5:9042,[2001:db8::1]:9142,[::1]:9042");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void endpointListMayReachTheExactAddressLimit() {
    String first = repeat('a', 127);
    String second = repeat('b', 127);

    CassandraServerTarget target =
        CassandraServerTarget.of(asList(first + ":9042", second + ":9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo(first + "," + second).hasSize(255);
    assertThat(target.getPort()).isNull();
  }

  @Test
  void endpointListStopsBeforeAnOverflowingCompleteEndpoint() {
    String first = repeat('a', 250);

    CassandraServerTarget target = CassandraServerTarget.of(asList("b:9142", first + ":9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo(first + ":9042").hasSize(255);
    assertThat(target.getPort()).isNull();
  }

  @Test
  void invalidContactPointsAreIgnored() {
    assertThat(CassandraServerTarget.of(emptyList())).isNull();
    assertThat(CassandraServerTarget.of((List<String>) null)).isNull();
    assertThat(CassandraServerTarget.of(singletonList("  "))).isNull();
    assertThat(CassandraServerTarget.of(singletonList("node.example.com:not-a-port"))).isNull();
    assertThat(CassandraServerTarget.of(singletonList("node.example.com:0"))).isNull();
    assertThat(CassandraServerTarget.of(singletonList("[::1:9042"))).isNull();
    assertThat(CassandraServerTarget.of(singletonList("user:password@node.example.com:9042")))
        .isNull();
    assertThat(CassandraServerTarget.of(singletonList("node.example.com/path?token=secret:9042")))
        .isNull();
    assertThat(
            CassandraServerTarget.of(
                asList("node.example.com:9042", "user:password@other.example.com:9042")))
        .isNull();

    CassandraServerTarget target =
        CassandraServerTarget.of(
            asList("missing-port", "node.example.com:9042", "invalid:not-a-port", "[::1]:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::1,node.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sessionWithSniContactPointHasNoStableTarget() {
    configureContactPoints(emptyList());
    when(session.getContext()).thenReturn(context);
    Set<EndPoint> programmaticContactPoints =
        singleton(
            new SniEndPoint(
                InetSocketAddress.createUnresolved("proxy.example.com", 29042), "host-id"));

    assertThat(CassandraServerTarget.of(session, programmaticContactPoints)).isNull();
  }

  @Test
  void sessionWithCustomDiscoveryEndPointHasNoStableTarget() {
    configureContactPoints(emptyList());
    when(session.getContext()).thenReturn(context);

    assertThat(CassandraServerTarget.of(session, singleton(customEndPoint))).isNull();
    verify(customEndPoint, never()).resolve();
  }

  @Test
  void sessionThatNamesNoContactPointHasNoTarget() {
    configureContactPoints(emptyList());
    when(session.getContext()).thenReturn(context);

    assertThat(CassandraServerTarget.of(session, emptySet())).isNull();
  }

  @Test
  void sessionUsesMergedProgrammaticAndConfiguredContactPoints() {
    configureContactPoints(singletonList("configured.example.com:9042"));
    when(session.getContext()).thenReturn(context);
    Set<EndPoint> programmaticContactPoints =
        new LinkedHashSet<>(
            singletonList(
                new DefaultEndPoint(
                    InetSocketAddress.createUnresolved("programmatic.example.com", 9142))));

    CassandraServerTarget target = CassandraServerTarget.of(session, programmaticContactPoints);

    assertThat(target).isNotNull();
    assertThat(target.getAddress())
        .isEqualTo("configured.example.com:9042,programmatic.example.com:9142");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sessionPreservesDuplicatesFromConfiguredAndProgrammaticContactPoints() {
    configureContactPoints(singletonList("duplicate.example.com:9042"));
    when(session.getContext()).thenReturn(context);
    Set<EndPoint> programmaticContactPoints =
        new LinkedHashSet<>(
            singletonList(
                new DefaultEndPoint(
                    InetSocketAddress.createUnresolved("duplicate.example.com", 9042))));

    CassandraServerTarget target = CassandraServerTarget.of(session, programmaticContactPoints);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("duplicate.example.com,duplicate.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sessionHasNoTargetWhenTheFirstRenderedEndpointCannotFit() {
    configureContactPoints(singletonList(repeat('a', 251) + ":9042"));
    when(session.getContext()).thenReturn(context);
    Set<EndPoint> programmaticContactPoints =
        singleton(new DefaultEndPoint(InetSocketAddress.createUnresolved("b.example.com", 9142)));

    assertThat(CassandraServerTarget.of(session, programmaticContactPoints)).isNull();
  }

  @Test
  void programmaticContactPointSetIterationOrderDoesNotChangeTheTarget() {
    configureContactPoints(emptyList());
    when(session.getContext()).thenReturn(context);
    EndPoint first =
        new DefaultEndPoint(InetSocketAddress.createUnresolved("node1.example.com", 9042));
    EndPoint second =
        new DefaultEndPoint(InetSocketAddress.createUnresolved("node2.example.com", 9142));
    Set<EndPoint> forward = new LinkedHashSet<>(asList(first, second));
    Set<EndPoint> reverse = new LinkedHashSet<>(asList(second, first));

    CassandraServerTarget forwardTarget = CassandraServerTarget.of(session, forward);
    CassandraServerTarget reverseTarget = CassandraServerTarget.of(session, reverse);

    assertThat(forwardTarget).isNotNull();
    assertThat(reverseTarget).isNotNull();
    assertThat(forwardTarget.getAddress())
        .isEqualTo("node1.example.com:9042,node2.example.com:9142");
    assertThat(reverseTarget.getAddress()).isEqualTo(forwardTarget.getAddress());
  }

  @Test
  void sessionDoesNotResolveConfiguredContactPointsAgain() {
    configureContactPoints(singletonList("configured.invalid:9042"));
    when(session.getContext()).thenReturn(context);
    Set<EndPoint> programmaticContactPoints =
        new LinkedHashSet<>(
            singletonList(
                new DefaultEndPoint(
                    InetSocketAddress.createUnresolved("programmatic.example.com", 9142))));

    CassandraServerTarget target = CassandraServerTarget.of(session, programmaticContactPoints);

    assertThat(target).isNotNull();
    assertThat(target.getAddress())
        .isEqualTo("configured.invalid:9042,programmatic.example.com:9142");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void theTargetDoesNotFollowLaterChangesToTheMergedContactPoints() {
    Set<EndPoint> programmaticContactPoints =
        new LinkedHashSet<>(
            singletonList(
                new DefaultEndPoint(
                    InetSocketAddress.createUnresolved("programmatic.example.com", 9142))));
    configureContactPoints(emptyList());
    when(session.getContext()).thenReturn(context);

    CassandraServerTarget target = CassandraServerTarget.of(session, programmaticContactPoints);

    programmaticContactPoints.clear();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("programmatic.example.com");
    assertThat(target.getPort()).isEqualTo(9142);
  }

  @Test
  void sessionWithOnlyProgrammaticContactPointsReadsTheRealDriverConfiguration() {
    // basic.contact-points has no default, so a lookup without one throws on a session that names
    // its contact points on the builder alone
    when(session.getContext()).thenReturn(context);
    when(context.getConfig()).thenReturn(new DefaultDriverConfigLoader().getInitialConfig());
    Set<EndPoint> programmaticContactPoints =
        new LinkedHashSet<>(
            singletonList(
                new DefaultEndPoint(
                    InetSocketAddress.createUnresolved("programmatic.example.com", 9142))));

    CassandraServerTarget target = CassandraServerTarget.of(session, programmaticContactPoints);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("programmatic.example.com");
    assertThat(target.getPort()).isEqualTo(9142);
  }

  private void configureContactPoints(List<String> contactPoints) {
    when(context.getConfig()).thenReturn(config);
    when(config.getDefaultProfile()).thenReturn(defaultProfile);
    when(defaultProfile.getStringList(DefaultDriverOption.CONTACT_POINTS, emptyList()))
        .thenReturn(contactPoints);
  }

  private static String repeat(char value, int count) {
    StringBuilder result = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      result.append(value);
    }
    return result.toString();
  }
}
