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
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
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
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import java.net.InetSocketAddress;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CassandraServerTargetTest {

  @Mock private Session session;
  @Mock private InternalDriverContext context;
  @Mock private DriverConfig config;
  @Mock private DriverExecutionProfile defaultProfile;
  @Mock private EndPoint customEndPoint;

  @ParameterizedTest
  @MethodSource("validContactPointTargets")
  void contactPointsProduceTheExpectedTarget(
      List<String> contactPoints, String expectedAddress, Integer expectedPort) {
    DbServerTarget target = CassandraServerTarget.of(contactPoints);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo(expectedAddress);
    assertThat(target.getPort()).isEqualTo(expectedPort);
  }

  private static Stream<Arguments> validContactPointTargets() {
    return Stream.of(
        argumentSet(
            "single contact point omits the default port",
            singletonList("cassandra.example.com:9042"),
            "cassandra.example.com",
            null),
        argumentSet(
            "single contact point extracts a non-default port",
            singletonList("cassandra.example.com:9142"),
            "cassandra.example.com",
            9142),
        argumentSet(
            "single IPv6 contact point loses its brackets",
            singletonList("[::1]:9042"),
            "::1",
            null),
        argumentSet(
            "configured contact points preserve order and omit the shared default port",
            asList("node1.example.com:9042", "10.0.0.5:9042"),
            "node1.example.com,10.0.0.5",
            null),
        argumentSet(
            "several contact points inline every shared non-default port",
            asList("node1.example.com:9142", "10.0.0.5:9142"),
            "node1.example.com:9142,10.0.0.5:9142",
            null),
        argumentSet(
            "duplicate configured contact points are preserved",
            asList("cassandra.example.com:9042", "cassandra.example.com:9042"),
            "cassandra.example.com,cassandra.example.com",
            null),
        argumentSet(
            "IPv6 contact points stay bracketed when ports are mixed",
            asList("[::1]:9042", "2001:db8::1:9142", "10.0.0.5:9042"),
            "[::1]:9042,[2001:db8::1]:9142,10.0.0.5:9042",
            null),
        argumentSet(
            "endpoint list includes five endpoints",
            asList(
                "node5.example.com:9042",
                "node2.example.com:9042",
                "node4.example.com:9042",
                "node1.example.com:9042",
                "node3.example.com:9042"),
            "node5.example.com,node2.example.com,node4.example.com,node1.example.com,"
                + "node3.example.com",
            null),
        argumentSet(
            "configured endpoint list keeps the first five endpoints",
            asList(
                "node6.example.com:9042",
                "node3.example.com:9042",
                "node1.example.com:9042",
                "node5.example.com:9042",
                "node2.example.com:9042",
                "node4.example.com:9042"),
            "node6.example.com,node3.example.com,node1.example.com,node5.example.com,"
                + "node2.example.com",
            null));
  }

  @Test
  void configuredMixedPortContactPointOrderIsPreserved() {
    DbServerTarget first =
        CassandraServerTarget.of(asList("node2.example.com:9142", "node1.example.com:9042"));
    DbServerTarget second =
        CassandraServerTarget.of(asList("node1.example.com:9042", "node2.example.com:9142"));

    assertThat(first).isNotNull();
    assertThat(second).isNotNull();
    assertThat(first.getAddress()).isEqualTo("node2.example.com:9142,node1.example.com:9042");
    assertThat(second.getAddress()).isEqualTo("node1.example.com:9042,node2.example.com:9142");
    assertThat(first.getPort()).isNull();
    assertThat(second.getPort()).isNull();
  }

  @ParameterizedTest
  @MethodSource("invalidContactPoints")
  void invalidContactPointsDropTheTarget(List<String> contactPoints) {
    assertThat(CassandraServerTarget.of(contactPoints)).isNull();
  }

  private static Stream<Arguments> invalidContactPoints() {
    return Stream.of(
        argumentSet("empty list", emptyList()),
        argumentSet("null list", (Object) null),
        argumentSet("blank contact point", singletonList("  ")),
        argumentSet("contact point without a port", singletonList("cassandra.example.com")),
        argumentSet("non-numeric port", singletonList("node.example.com:not-a-port")),
        argumentSet("zero port", singletonList("node.example.com:0")),
        argumentSet("unclosed IPv6 bracket", singletonList("[::1:9042")),
        argumentSet("unbracketed loopback IPv6", singletonList("::1")),
        argumentSet("unbracketed IPv6", singletonList("2001:db8::1")),
        argumentSet(
            "credentials in contact point", singletonList("user:password@node.example.com:9042")),
        argumentSet(
            "path and query in contact point",
            singletonList("node.example.com/path?token=secret:9042")),
        argumentSet(
            "unsafe contact point among valid contact points",
            asList("node.example.com:9042", "user:password@other.example.com:9042")),
        argumentSet(
            "several malformed contact points",
            asList("missing-port", "node.example.com:9042", "invalid:not-a-port", "[::1]:9042")));
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

    DbServerTarget target = CassandraServerTarget.of(session, programmaticContactPoints);

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

    DbServerTarget target = CassandraServerTarget.of(session, programmaticContactPoints);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("duplicate.example.com,duplicate.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sessionRejectsMalformedProgrammaticContactPointHost() {
    configureContactPoints(emptyList());
    when(session.getContext()).thenReturn(context);
    Set<EndPoint> programmaticContactPoints =
        singleton(new DefaultEndPoint(InetSocketAddress.createUnresolved("user:password", 9042)));

    assertThat(CassandraServerTarget.of(session, programmaticContactPoints)).isNull();
  }

  @Test
  void sessionRejectsInvalidLongEndpointNames() {
    configureContactPoints(singletonList(repeat('a', 251) + ":9042"));
    when(session.getContext()).thenReturn(context);
    Set<EndPoint> programmaticContactPoints =
        singleton(new DefaultEndPoint(InetSocketAddress.createUnresolved("b.example.com", 9142)));

    DbServerTarget target = CassandraServerTarget.of(session, programmaticContactPoints);

    assertThat(target).isNull();
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

    DbServerTarget forwardTarget = CassandraServerTarget.of(session, forward);
    DbServerTarget reverseTarget = CassandraServerTarget.of(session, reverse);

    assertThat(forwardTarget).isNotNull();
    assertThat(reverseTarget).isNotNull();
    assertThat(forwardTarget.getAddress())
        .isEqualTo("node1.example.com:9042,node2.example.com:9142");
    assertThat(reverseTarget.getAddress()).isEqualTo(forwardTarget.getAddress());
  }

  @Test
  void sessionPreservesConfiguredOrderBeforeSortedProgrammaticContactPoints() {
    configureContactPoints(asList("configured2.example.com:9042", "configured1.example.com:9042"));
    when(session.getContext()).thenReturn(context);
    EndPoint first =
        new DefaultEndPoint(InetSocketAddress.createUnresolved("programmatic1.example.com", 9042));
    EndPoint second =
        new DefaultEndPoint(InetSocketAddress.createUnresolved("programmatic2.example.com", 9042));
    Set<EndPoint> programmaticContactPoints = new LinkedHashSet<>(asList(second, first));

    DbServerTarget target = CassandraServerTarget.of(session, programmaticContactPoints);

    assertThat(target).isNotNull();
    assertThat(target.getAddress())
        .isEqualTo(
            "configured2.example.com,configured1.example.com,programmatic1.example.com,"
                + "programmatic2.example.com");
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

    DbServerTarget target = CassandraServerTarget.of(session, programmaticContactPoints);

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

    DbServerTarget target = CassandraServerTarget.of(session, programmaticContactPoints);

    programmaticContactPoints.clear();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("programmatic.example.com");
    assertThat(target.getPort()).isEqualTo(9142);
  }

  @Test
  void sessionWithOnlyProgrammaticContactPointsReadsTheRealDriverConfiguration() {
    // The basic.contact-points option has no default, so a lookup without one throws on a session
    // that names its contact points on the builder alone.
    when(session.getContext()).thenReturn(context);
    when(context.getConfig()).thenReturn(new DefaultDriverConfigLoader().getInitialConfig());
    Set<EndPoint> programmaticContactPoints =
        new LinkedHashSet<>(
            singletonList(
                new DefaultEndPoint(
                    InetSocketAddress.createUnresolved("programmatic.example.com", 9142))));

    DbServerTarget target = CassandraServerTarget.of(session, programmaticContactPoints);

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
