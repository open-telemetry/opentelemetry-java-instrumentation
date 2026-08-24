/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v3_0;

import static java.util.Locale.ROOT;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.EndPoint;
import com.datastax.driver.core.Session;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

// Sibling instrumentations record the target a client was configured with, rather than the endpoint
// one response came back from, once the stable database semantic conventions are enabled.
// cassandra-3.0 keeps recording the coordinator instead, and these assertions hold the reasons in
// place. Driver 3.11.5 is the last 3.x release, so nothing later can change them.
class CassandraConfiguredContactPointsTest {

  @Test
  void builtClientTellsNobodyWhatItWasConfiguredWith() {
    // Contact points reach the client through Cluster.Initializer and then survive only on the
    // package private Cluster.Manager, in a field whose type changed from InetSocketAddress to
    // EndPoint in driver 3.8.0. Neither the cluster nor a session offers them, so the coordinator
    // of each response is the only target the instrumentation can see.
    assertThat(contactPointAccessors(Cluster.class)).isEmpty();
    assertThat(contactPointAccessors(Session.class)).isEmpty();
  }

  @Test
  void configuredHostNamesAreResolvedBeforeTheClientExists() {
    // The driver resolves a contact point host name the moment it is given, and one name becomes
    // one contact point per address it resolves to, so what the driver holds is a set of addresses
    // rather than the target an operator configured.
    List<EndPoint> contactPoints =
        Cluster.builder().addContactPoint("localhost").getContactPoints();

    assertThat(contactPoints).isNotEmpty();
    assertThat(contactPoints)
        .allSatisfy(endPoint -> assertThat(endPoint.resolve().isUnresolved()).isFalse());
  }

  private static List<Method> contactPointAccessors(Class<?> type) {
    return Arrays.stream(type.getMethods())
        .filter(method -> method.getParameterCount() == 0)
        .filter(
            method ->
                Collection.class.isAssignableFrom(method.getReturnType())
                    || EndPoint.class.isAssignableFrom(method.getReturnType())
                    || InetSocketAddress.class.isAssignableFrom(method.getReturnType()))
        .filter(method -> method.getName().toLowerCase(ROOT).contains("contact"))
        .collect(toList());
  }
}
