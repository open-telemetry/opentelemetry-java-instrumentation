/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.cassandra.v4_4;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.config.DefaultDriverOption;
import com.datastax.oss.driver.api.core.config.DriverConfig;
import com.datastax.oss.driver.api.core.config.DriverExecutionProfile;
import com.datastax.oss.driver.api.core.context.DriverContext;
import com.datastax.oss.driver.api.core.session.Session;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CassandraServerTargetTest {

  @Mock private Session session;
  @Mock private DriverContext context;
  @Mock private DriverConfig config;
  @Mock private DriverExecutionProfile profile;

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
  void singleContactPointWithoutAPortHasNoPort() {
    CassandraServerTarget target = CassandraServerTarget.of(singletonList("cassandra.example.com"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("cassandra.example.com");
    assertThat(target.getPort()).isNull();
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
        CassandraServerTarget.of(asList("[::1]:9042", "2001:db8::1", "10.0.0.5:9042"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("[::1]:9042,[2001:db8::1],10.0.0.5:9042");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void noContactPointsMeansNoTarget() {
    assertThat(CassandraServerTarget.of(emptyList())).isNull();
    assertThat(CassandraServerTarget.of((List<String>) null)).isNull();
    assertThat(CassandraServerTarget.of(singletonList("  "))).isNull();
  }

  @Test
  void sessionThatNamesNoContactPointHasNoTarget() {
    when(session.getContext()).thenReturn(context);
    when(context.getConfig()).thenReturn(config);
    when(config.getDefaultProfile()).thenReturn(profile);
    when(profile.isDefined(DefaultDriverOption.CONTACT_POINTS)).thenReturn(false);

    assertThat(CassandraServerTarget.of(session)).isNull();
  }

  @Test
  void theTargetDoesNotFollowLaterChangesToTheConfiguredContactPoints() {
    List<String> contactPoints = new ArrayList<>(asList("node1.example.com:9042", "10.0.0.5:9042"));
    when(session.getContext()).thenReturn(context);
    when(context.getConfig()).thenReturn(config);
    when(config.getDefaultProfile()).thenReturn(profile);
    when(profile.isDefined(DefaultDriverOption.CONTACT_POINTS)).thenReturn(true);
    when(profile.getStringList(DefaultDriverOption.CONTACT_POINTS)).thenReturn(contactPoints);

    CassandraServerTarget target = CassandraServerTarget.of(session);

    contactPoints.clear();
    contactPoints.add("somewhere.else.example.com:9042");

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("node1.example.com:9042,10.0.0.5:9042");
  }
}
