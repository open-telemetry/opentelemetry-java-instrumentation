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

// Driver 3.x does not expose the original contact points after construction, so stable telemetry
// must continue reporting the coordinator.
class CassandraConfiguredContactPointsTest {

  @Test
  void builtClientTellsNobodyWhatItWasConfiguredWith() {
    assertThat(contactPointAccessors(Cluster.class)).isEmpty();
    assertThat(contactPointAccessors(Session.class)).isEmpty();
  }

  @Test
  void configuredHostNamesAreResolvedBeforeTheClientExists() {
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
