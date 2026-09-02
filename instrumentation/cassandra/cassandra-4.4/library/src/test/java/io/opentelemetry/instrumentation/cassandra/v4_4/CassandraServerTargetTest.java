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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  void severalContactPointsInlineASharedNonDefaultPort() {
    CassandraServerTarget target =
        CassandraServerTarget.of(asList("node1.example.com:9142", "10.0.0.5:9142"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.5:9142,node1.example.com:9142");
    assertThat(target.getPort()).isNull();
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
  void explicitContactPointAddressesApplySinglePortRules() {
    CassandraServerTarget defaultPortTarget =
        CassandraServerTarget.ofAddresses(
            singletonList(InetSocketAddress.createUnresolved("node.example.com", 9042)));
    CassandraServerTarget nonDefaultPortTarget =
        CassandraServerTarget.ofAddresses(
            singletonList(InetSocketAddress.createUnresolved("node.example.com", 9142)));

    assertThat(defaultPortTarget).isNotNull();
    assertThat(defaultPortTarget.getAddress()).isEqualTo("node.example.com");
    assertThat(defaultPortTarget.getPort()).isNull();
    assertThat(nonDefaultPortTarget).isNotNull();
    assertThat(nonDefaultPortTarget.getAddress()).isEqualTo("node.example.com");
    assertThat(nonDefaultPortTarget.getPort()).isEqualTo(9142);
  }

  @Test
  void explicitContactPointAddressesBecomeTheTarget() {
    CassandraServerTarget target =
        CassandraServerTarget.ofAddresses(
            asList(
                InetSocketAddress.createUnresolved("node2.example.com", 9142),
                InetSocketAddress.createUnresolved("node1.example.com", 9042)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("node1.example.com:9042,node2.example.com:9142");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void explicitContactPointAddressesApplySharedPortRules() {
    CassandraServerTarget defaultPortTarget =
        CassandraServerTarget.ofAddresses(
            asList(
                InetSocketAddress.createUnresolved("node2.example.com", 9042),
                InetSocketAddress.createUnresolved("node1.example.com", 9042)));
    CassandraServerTarget nonDefaultPortTarget =
        CassandraServerTarget.ofAddresses(
            asList(
                InetSocketAddress.createUnresolved("node2.example.com", 9142),
                InetSocketAddress.createUnresolved("node1.example.com", 9142)));

    assertThat(defaultPortTarget).isNotNull();
    assertThat(defaultPortTarget.getAddress()).isEqualTo("node1.example.com,node2.example.com");
    assertThat(defaultPortTarget.getPort()).isNull();
    assertThat(nonDefaultPortTarget).isNotNull();
    assertThat(nonDefaultPortTarget.getAddress())
        .isEqualTo("node1.example.com:9142,node2.example.com:9142");
    assertThat(nonDefaultPortTarget.getPort()).isNull();
  }

  @Test
  void explicitContactPointAddressesIncludeAtMostFiveOrderedEndpoints() {
    List<InetSocketAddress> fiveContactPoints =
        asList(
            InetSocketAddress.createUnresolved("node5.example.com", 9042),
            InetSocketAddress.createUnresolved("node3.example.com", 9042),
            InetSocketAddress.createUnresolved("node1.example.com", 9042),
            InetSocketAddress.createUnresolved("node4.example.com", 9042),
            InetSocketAddress.createUnresolved("node2.example.com", 9042));
    List<InetSocketAddress> sixContactPoints = new ArrayList<>(fiveContactPoints);
    sixContactPoints.add(InetSocketAddress.createUnresolved("node6.example.com", 9042));

    CassandraServerTarget fiveEndpointTarget = CassandraServerTarget.ofAddresses(fiveContactPoints);
    CassandraServerTarget sixEndpointTarget = CassandraServerTarget.ofAddresses(sixContactPoints);

    assertThat(fiveEndpointTarget).isNotNull();
    assertThat(fiveEndpointTarget.getAddress())
        .isEqualTo(
            "node1.example.com,node2.example.com,node3.example.com,node4.example.com,"
                + "node5.example.com");
    assertThat(sixEndpointTarget).isNotNull();
    assertThat(sixEndpointTarget.getAddress()).isEqualTo(fiveEndpointTarget.getAddress());
  }

  @Test
  void explicitContactPointAddressesNormalizeIpv6Brackets() {
    CassandraServerTarget target =
        CassandraServerTarget.ofAddresses(
            asList(
                InetSocketAddress.createUnresolved("[::1]", 9042),
                InetSocketAddress.createUnresolved("node.example.com", 9142)));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[::1]:9042,node.example.com:9142");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void unsafeExplicitContactPointAddressesHaveNoTarget() {
    assertThat(CassandraServerTarget.ofAddresses(singletonList((InetSocketAddress) null))).isNull();
    assertThat(
            CassandraServerTarget.ofAddresses(
                singletonList(InetSocketAddress.createUnresolved("node.example.com", 0))))
        .isNull();
    assertThat(
            CassandraServerTarget.ofAddresses(
                singletonList(
                    InetSocketAddress.createUnresolved("user:password@node.example.com", 9042))))
        .isNull();
    assertThat(
            CassandraServerTarget.ofAddresses(
                singletonList(InetSocketAddress.createUnresolved("[::1", 9042))))
        .isNull();
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
  void ipv6ContactPointsOmitBracketsWhenTheyShareAPort() {
    CassandraServerTarget target =
        CassandraServerTarget.of(asList("[::1]:9042", "2001:db8::1:9042", "10.0.0.5:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("10.0.0.5,2001:db8::1,::1");
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
  void fiveEndpointsAreAllIncludedWithoutALengthLimit() {
    String first = repeat('a', 60);
    String second = repeat('b', 60);
    String third = repeat('c', 60);
    String fourth = repeat('d', 60);
    String fifth = repeat('e', 60);

    CassandraServerTarget target =
        CassandraServerTarget.of(
            asList(
                fifth + ":9042",
                third + ":9042",
                first + ":9042",
                fourth + ":9042",
                second + ":9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress())
        .isEqualTo(String.join(",", first, second, third, fourth, fifth))
        .hasSize(304);
    assertThat(target.getPort()).isNull();
  }

  @Test
  void onlyTheFirstFiveOrderedEndpointsAreIncluded() {
    CassandraServerTarget target =
        CassandraServerTarget.of(
            asList(
                "node6.example.com:9042",
                "node3.example.com:9042",
                "node1.example.com:9042",
                "node5.example.com:9142",
                "node2.example.com:9042",
                "node4.example.com:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress())
        .isEqualTo(
            "node1.example.com:9042,node2.example.com:9042,node3.example.com:9042,"
                + "node4.example.com:9042,node5.example.com:9142");
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

    CassandraServerTarget target = CassandraServerTarget.of(session);

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

    CassandraServerTarget target = CassandraServerTarget.of(session);

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

    CassandraServerTarget target = CassandraServerTarget.of(session);

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

    CassandraServerTarget target = CassandraServerTarget.of(session);

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

    CassandraServerTarget target = CassandraServerTarget.of(session);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("::ffff:127.0.0.1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sessionPreservesANoncanonicalIpv4LiteralAfterNormalization() {
    configureContactPoints(singletonList("127.000.000.001:9042"));
    when(session.getContext()).thenReturn(context);
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.getContactPoints()).thenReturn(singleton(configuredNode));
    when(configuredNode.getEndPoint())
        .thenReturn(new DefaultEndPoint(InetSocketAddress.createUnresolved("127.0.0.1", 9042)));

    CassandraServerTarget target = CassandraServerTarget.of(session);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("127.000.000.001");
    assertThat(target.getPort()).isNull();
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

    CassandraServerTarget target = CassandraServerTarget.of(session);

    contactPoints.clear();

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("configured.example.com");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sessionWithOnlyProgrammaticContactPointsReadsTheRealDriverConfiguration() {
    // basic.contact-points has no default, so a lookup without one throws on a session that names
    // its contact points on the builder alone
    when(session.getContext()).thenReturn(context);
    when(context.getConfig()).thenReturn(new DefaultDriverConfigLoader().getInitialConfig());
    when(context.getMetadataManager()).thenReturn(metadataManager);
    when(metadataManager.getContactPoints()).thenReturn(singleton(programmaticNode));
    when(programmaticNode.getEndPoint())
        .thenReturn(
            new DefaultEndPoint(
                InetSocketAddress.createUnresolved("programmatic.example.com", 9142)));

    CassandraServerTarget target = CassandraServerTarget.of(session);

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
