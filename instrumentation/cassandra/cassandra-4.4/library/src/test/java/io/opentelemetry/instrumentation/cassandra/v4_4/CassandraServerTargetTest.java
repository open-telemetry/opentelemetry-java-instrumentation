/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfig;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.session.Session;
import com.datastax.oss.driver.internal.core.ContactPoints;
import com.datastax.oss.driver.internal.core.config.typesafe.DefaultDriverConfigLoader;
import com.datastax.oss.driver.internal.core.context.InternalDriverContext;
import com.datastax.oss.driver.internal.core.metadata.DefaultEndPoint;
import com.datastax.oss.driver.internal.core.metadata.DefaultNode;
import com.datastax.oss.driver.internal.core.metadata.MetadataManager;
import com.datastax.oss.driver.internal.core.metadata.SniEndPoint;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
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

  private static final InetSocketAddress PROXY_ADDRESS =
      InetSocketAddress.createUnresolved("proxy.example.com", 29042);

  @Mock private Session session;
  @Mock private InternalDriverContext context;
  @Mock private DriverConfig config;
  @Mock private DriverExecutionProfile defaultProfile;
  @Mock private MetadataManager metadataManager;
  @Mock private DefaultNode configuredNode;
  @Mock private DefaultNode programmaticNode;
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
    String first = repeat('a', 60);
    String second = repeat('b', 60);
    String third = repeat('c', 60);
    String fourth = repeat('d', 60);
    String fifth = repeat('e', 60);
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
            "unbracketed IPv6 contact point with a port is accepted",
            singletonList("2001:db8::1:9042"),
            "2001:db8::1",
            null),
        argumentSet(
            "unbracketed IPv6 contact point ending with a double colon is accepted",
            singletonList("2001:db8:::9142"),
            "2001:db8::",
            9142),
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
            "IPv6 contact points omit brackets when they share a port",
            asList("[::1]:9042", "2001:db8::1:9042", "10.0.0.5:9042"),
            "::1,2001:db8::1,10.0.0.5",
            null),
        argumentSet(
            "IPv6 contact points stay bracketed when ports are mixed",
            asList("[::1]:9042", "2001:db8::1:9142", "10.0.0.5:9042"),
            "[::1]:9042,[2001:db8::1]:9142,10.0.0.5:9042",
            null),
        argumentSet(
            "five long endpoints are included without a length limit",
            asList(
                fifth + ":9042",
                third + ":9042",
                first + ":9042",
                fourth + ":9042",
                second + ":9042"),
            String.join(",", fifth, third, first, fourth, second),
            null),
        argumentSet(
            "configured endpoint list keeps the first five endpoints",
            asList(
                "node6.example.com:9042",
                "node3.example.com:9042",
                "node1.example.com:9042",
                "node5.example.com:9142",
                "node2.example.com:9042",
                "node4.example.com:9042"),
            "node6.example.com:9042,node3.example.com:9042,node1.example.com:9042,"
                + "node5.example.com:9142,node2.example.com:9042",
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
  @MethodSource("validExplicitAddressTargets")
  void explicitAddressesProduceTheExpectedTarget(
      List<InetSocketAddress> contactPoints, String expectedAddress, Integer expectedPort) {
    DbServerTarget target = CassandraServerTarget.ofAddresses(contactPoints);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo(expectedAddress);
    assertThat(target.getPort()).isEqualTo(expectedPort);
  }

  private static Stream<Arguments> validExplicitAddressTargets() {
    return Stream.of(
        argumentSet(
            "single address omits the default port",
            singletonList(InetSocketAddress.createUnresolved("node.example.com", 9042)),
            "node.example.com",
            null),
        argumentSet(
            "single address extracts a non-default port",
            singletonList(InetSocketAddress.createUnresolved("node.example.com", 9142)),
            "node.example.com",
            9142),
        argumentSet(
            "several addresses are naturally ordered",
            asList(
                InetSocketAddress.createUnresolved("node2.example.com", 9142),
                InetSocketAddress.createUnresolved("node1.example.com", 9042)),
            "node1.example.com:9042,node2.example.com:9142",
            null),
        argumentSet(
            "several addresses omit the shared default port",
            asList(
                InetSocketAddress.createUnresolved("node2.example.com", 9042),
                InetSocketAddress.createUnresolved("node1.example.com", 9042)),
            "node1.example.com,node2.example.com",
            null),
        argumentSet(
            "several addresses inline every shared non-default port",
            asList(
                InetSocketAddress.createUnresolved("node2.example.com", 9142),
                InetSocketAddress.createUnresolved("node1.example.com", 9142)),
            "node1.example.com:9142,node2.example.com:9142",
            null),
        argumentSet(
            "address list includes at most five naturally ordered endpoints",
            asList(
                InetSocketAddress.createUnresolved("node6.example.com", 9042),
                InetSocketAddress.createUnresolved("node3.example.com", 9042),
                InetSocketAddress.createUnresolved("node1.example.com", 9042),
                InetSocketAddress.createUnresolved("node5.example.com", 9042),
                InetSocketAddress.createUnresolved("node2.example.com", 9042),
                InetSocketAddress.createUnresolved("node4.example.com", 9042)),
            "node1.example.com,node2.example.com,node3.example.com,node4.example.com,"
                + "node5.example.com",
            null),
        argumentSet(
            "IPv6 brackets are normalized",
            asList(
                InetSocketAddress.createUnresolved("[::1]", 9042),
                InetSocketAddress.createUnresolved("node.example.com", 9142)),
            "[::1]:9042,node.example.com:9142",
            null));
  }

  @ParameterizedTest
  @MethodSource("unsafeExplicitContactPointAddresses")
  void unsafeExplicitContactPointAddressesHaveNoTarget(InetSocketAddress contactPoint) {
    assertThat(CassandraServerTarget.ofAddresses(singletonList(contactPoint))).isNull();
  }

  private static Stream<Arguments> unsafeExplicitContactPointAddresses() {
    return Stream.of(
        argumentSet("null address", (Object) null),
        argumentSet(
            "zero port", InetSocketAddress.createUnresolved("node.example.com", 0)),
        argumentSet(
            "credentials in host",
            InetSocketAddress.createUnresolved("user:password@node.example.com", 9042)),
        argumentSet(
            "unclosed IPv6 bracket", InetSocketAddress.createUnresolved("[::1", 9042)),
        argumentSet(
            "invalid IPv6 literal", InetSocketAddress.createUnresolved("not:ipv6", 9042)));
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
  void sessionThatNamesNoContactPointHasNoTarget() {
    when(session.getContext()).thenReturn(context);
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.wasImplicitContactPoint()).thenReturn(true);

    assertThat(CassandraServerTarget.of(session)).isNull();
  }

  @Test
  void sessionWithSniContactPointHasNoStableTarget() {
    configureMetadataContactPoint(new SniEndPoint(PROXY_ADDRESS, "host-id"));

    assertThat(CassandraServerTarget.of(session)).isNull();
  }

  @Test
  void sessionWithCustomDiscoveryEndPointHasNoStableTarget() {
    configureMetadataContactPoint(customEndPoint);

    assertThat(CassandraServerTarget.of(session)).isNull();
    verify(customEndPoint, never()).resolve();
  }

  @Test
  void capturedSniContactPointHasNoStableTarget() {
    configureContactPoints(emptyList());
    when(session.getContext()).thenReturn(context);

    assertThat(
            CassandraServerTarget.of(session, singleton(new SniEndPoint(PROXY_ADDRESS, "host-id"))))
        .isNull();
  }

  @Test
  void capturedCustomDiscoveryEndPointHasNoStableTarget() {
    configureContactPoints(emptyList());
    when(session.getContext()).thenReturn(context);

    assertThat(CassandraServerTarget.of(session, singleton(customEndPoint))).isNull();
    verify(customEndPoint, never()).resolve();
  }

  @Test
  void sessionWithMixedContactPointSourcesHasNoTarget() {
    Set<DefaultNode> contactPoints = new LinkedHashSet<>(asList(configuredNode, programmaticNode));
    configureContactPoints(singletonList("configured.example.com:9042"));
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

    DbServerTarget target = CassandraServerTarget.of(session);

    assertThat(target).isNull();
  }

  @Test
  void sessionUsesSeveralProgrammaticContactPoints() {
    Set<DefaultNode> contactPoints = new LinkedHashSet<>(asList(configuredNode, programmaticNode));
    configureContactPoints(emptyList());
    when(session.getContext()).thenReturn(context);
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.getContactPoints()).thenReturn(contactPoints);
    when(configuredNode.getEndPoint())
        .thenReturn(
            new DefaultEndPoint(
                InetSocketAddress.createUnresolved("programmatic1.example.com", 9042)));
    when(programmaticNode.getEndPoint())
        .thenReturn(
            new DefaultEndPoint(
                InetSocketAddress.createUnresolved("programmatic2.example.com", 9142)));

    DbServerTarget target = CassandraServerTarget.of(session);

    assertThat(target).isNotNull();
    assertThat(target.getAddress())
        .isEqualTo("programmatic1.example.com:9042,programmatic2.example.com:9142");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sessionUsesCapturedProgrammaticAndConfiguredContactPoints() {
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
  void sessionFallsBackWhenResolvedConfiguredPointCannotBeMatched() {
    configureContactPoints(singletonList("configured.invalid:9042"));
    when(session.getContext()).thenReturn(context);
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.getContactPoints()).thenReturn(singleton(configuredNode));
    when(configuredNode.getEndPoint())
        .thenReturn(
            new DefaultEndPoint(InetSocketAddress.createUnresolved("old-address.invalid", 9042)));

    assertThat(CassandraServerTarget.of(session)).isNull();
  }

  @Test
  void sessionPreservesAConfiguredHostnameWithoutResolvingItAgain() {
    List<String> configuredContactPoints = singletonList("localhost.:9042");
    Set<DefaultNode> contactPoints = new LinkedHashSet<>();
    for (EndPoint endPoint : ContactPoints.merge(emptySet(), configuredContactPoints, true)) {
      DefaultNode node = mock(DefaultNode.class);
      when(node.getEndPoint()).thenReturn(endPoint);
      contactPoints.add(node);
    }
    configureContactPoints(configuredContactPoints);
    when(session.getContext()).thenReturn(context);
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.getContactPoints()).thenReturn(contactPoints);

    DbServerTarget target = CassandraServerTarget.of(session);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("localhost.");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sessionPreservesAConfiguredIpv6LiteralAfterResolution() throws UnknownHostException {
    configureContactPoints(singletonList("[::1]:9042"));
    when(session.getContext()).thenReturn(context);
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.getContactPoints()).thenReturn(singleton(configuredNode));
    when(configuredNode.getEndPoint())
        .thenReturn(
            new DefaultEndPoint(
                new InetSocketAddress(
                    InetAddress.getByAddress(
                        new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}),
                    9042)));

    DbServerTarget target = CassandraServerTarget.of(session);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sessionPreservesAnIpv4MappedIpv6LiteralAfterNormalization() {
    configureContactPoints(singletonList("[::ffff:127.0.0.1]:9042"));
    when(session.getContext()).thenReturn(context);
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.getContactPoints()).thenReturn(singleton(configuredNode));
    when(configuredNode.getEndPoint())
        .thenReturn(new DefaultEndPoint(InetSocketAddress.createUnresolved("127.0.0.1", 9042)));

    DbServerTarget target = CassandraServerTarget.of(session);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::ffff:127.0.0.1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sessionRejectsANoncanonicalIpv4LiteralAfterNormalization() {
    configureContactPoints(singletonList("127.000.000.001:9042"));
    when(session.getContext()).thenReturn(context);
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.getContactPoints()).thenReturn(singleton(configuredNode));
    when(configuredNode.getEndPoint())
        .thenReturn(new DefaultEndPoint(InetSocketAddress.createUnresolved("127.0.0.1", 9042)));

    DbServerTarget target = CassandraServerTarget.of(session);

    assertThat(target).isNull();
  }

  @Test
  void theTargetDoesNotFollowLaterChangesToTheMergedContactPoints() {
    Set<DefaultNode> contactPoints = new LinkedHashSet<>(singletonList(configuredNode));
    configureContactPoints(singletonList("configured.example.com:9042"));
    when(session.getContext()).thenReturn(context);
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.getContactPoints()).thenReturn(contactPoints);
    when(configuredNode.getEndPoint())
        .thenReturn(
            new DefaultEndPoint(
                InetSocketAddress.createUnresolved("configured.example.com", 9042)));

    DbServerTarget target = CassandraServerTarget.of(session);

    contactPoints.clear();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("configured.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sessionWithOnlyProgrammaticContactPointsReadsTheRealDriverConfiguration() {
    // The basic.contact-points option has no default, so a lookup without one throws on a session
    // that names its contact points on the builder alone.
    when(session.getContext()).thenReturn(context);
    when(context.getConfig()).thenReturn(new DefaultDriverConfigLoader().getInitialConfig());
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.getContactPoints()).thenReturn(singleton(programmaticNode));
    when(programmaticNode.getEndPoint())
        .thenReturn(
            new DefaultEndPoint(
                InetSocketAddress.createUnresolved("programmatic.example.com", 9142)));

    DbServerTarget target = CassandraServerTarget.of(session);

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

  private void configureMetadataContactPoint(EndPoint endPoint) {
    configureContactPoints(emptyList());
    when(session.getContext()).thenReturn(context);
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.getContactPoints()).thenReturn(singleton(configuredNode));
    when(configuredNode.getEndPoint()).thenReturn(endPoint);
  }

  private static String repeat(char value, int count) {
    StringBuilder result = new StringBuilder(count);
    for (int i = 0; i < count; i++) {
      result.append(value);
    }
    return result.toString();
  }
}
