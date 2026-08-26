/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_1;

import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import io.opentelemetry.instrumentation.mongo.testing.AbstractMongoConfiguredTargetTest;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.extension.RegisterExtension;

class MongoConfiguredTargetTest extends AbstractMongoConfiguredTargetTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected ConfiguredClient createClient(List<ServerAddress> seeds) {
    MongoClientOptions options = MongoClientOptions.builder().build();
    MongoClient client = new MongoClient(seeds, options);
    return new ConfiguredClient(
        clusterId(client), options.getCommandListeners().get(0), client::close);
  }

  @Override
  protected boolean supportsIpv6Seeds() {
    // driver 3.1 strips IPv6 brackets while reparsing cluster settings, then rejects the address
    return false;
  }

  // driver 3.1 predates cluster listeners, so read the id directly from the client
  private static ClusterId clusterId(MongoClient client) {
    try {
      Method getCluster = Mongo.class.getDeclaredMethod("getCluster");
      getCluster.setAccessible(true);
      Object cluster = getCluster.invoke(client);
      for (Class<?> type = cluster.getClass(); type != null; type = type.getSuperclass()) {
        try {
          Field clusterIdField = type.getDeclaredField("clusterId");
          clusterIdField.setAccessible(true);
          return (ClusterId) clusterIdField.get(cluster);
        } catch (NoSuchFieldException ignored) {
        }
      }
      throw new IllegalStateException("No cluster id in " + cluster.getClass());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
