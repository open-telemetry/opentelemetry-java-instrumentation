/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_7;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.connection.ClusterSettings;
import io.opentelemetry.instrumentation.mongo.testing.AbstractMongoConfiguredTargetTest;
import io.opentelemetry.instrumentation.mongo.testing.ClusterIdCapture;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
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
    ClusterIdCapture clusterId = new ClusterIdCapture();
    MongoClientSettings settings =
        MongoClientSettings.builder()
            .applyToClusterSettings(builder -> builder.hosts(seeds).addClusterListener(clusterId))
            .build();
    return createClient(settings, clusterId);
  }

  private static ConfiguredClient createClient(
      MongoClientSettings settings, ClusterIdCapture clusterId) {
    MongoClient client = MongoClients.create(settings);
    return new ConfiguredClient(
        clusterId.getClusterId(), settings.getCommandListeners().get(0), client::close);
  }

  @Test
  void anSrvHostIsPreferredOverTheSeedsItStandsIn() {
    // srvHost was added in 3.10
    Method srvHost = srvHostSetter();
    assumeTrue(srvHost != null);

    ClusterIdCapture clusterId = new ClusterIdCapture();
    MongoClientSettings settings =
        MongoClientSettings.builder()
            .applyToClusterSettings(
                builder -> {
                  invoke(srvHost, builder, "cluster0.example.invalid");
                  builder.addClusterListener(clusterId);
                })
            .build();

    // closing an SRV client races the resolver thread and can report an uncaught exception
    runCommand(createClient(settings, clusterId));

    assertFindSpan("cluster0.example.invalid", null);
  }

  private static Method srvHostSetter() {
    try {
      return ClusterSettings.Builder.class.getMethod("srvHost", String.class);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  private static void invoke(Method method, Object target, Object argument) {
    try {
      method.invoke(target, argument);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
