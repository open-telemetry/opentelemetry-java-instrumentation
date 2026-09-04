/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongoasync.v3_3;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mongodb.ServerAddress;
import com.mongodb.async.client.MongoClient;
import com.mongodb.async.client.MongoClientSettings;
import com.mongodb.async.client.MongoClients;
import com.mongodb.connection.ClusterSettings;
import io.opentelemetry.instrumentation.mongo.testing.AbstractMongoConfiguredTargetTest;
import io.opentelemetry.instrumentation.mongo.testing.ClusterIdCapture;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MongoAsyncConfiguredTargetTest extends AbstractMongoConfiguredTargetTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected ConfiguredClient createClient(List<ServerAddress> seeds) {
    ClusterIdCapture clusterIdCapture = new ClusterIdCapture();
    MongoClientSettings settings =
        MongoClientSettings.builder()
            .clusterSettings(
                ClusterSettings.builder().hosts(seeds).addClusterListener(clusterIdCapture).build())
            .build();
    return createClient(settings, clusterIdCapture);
  }

  private static ConfiguredClient createClient(
      MongoClientSettings settings, ClusterIdCapture clusterIdCapture) {
    MongoClient client = MongoClients.create(settings);
    return new ConfiguredClient(
        clusterIdCapture.getClusterId(), settings.getCommandListeners().get(0), client::close);
  }

  @Test
  void srvConnectionStringDoesNotExposeResolvedHosts() {
    ClusterIdCapture clusterIdCapture = new ClusterIdCapture();
    MongoClientSettings settings =
        MongoClientSettings.builder()
            .clusterSettings(
                ClusterSettings.builder()
                    .applyConnectionString(resolvedSrvConnectionString())
                    .addClusterListener(clusterIdCapture)
                    .build())
            .build();

    try (ConfiguredClient client = createClient(settings, clusterIdCapture)) {
      runCommand(client);
    }

    assertFindSpan("mongodb+srv://cluster0.example.invalid", null);
  }

  @Test
  void unsafeSrvConnectionStringDoesNotFallBackToResolvedHosts() {
    ClusterIdCapture clusterIdCapture = new ClusterIdCapture();
    MongoClientSettings settings =
        MongoClientSettings.builder()
            .clusterSettings(
                ClusterSettings.builder()
                    .applyConnectionString(
                        resolvedSrvConnectionString(
                            "mongodb+srv://user%3Apassword%40cluster0.example.invalid"))
                    .addClusterListener(clusterIdCapture)
                    .build())
            .build();

    try (ConfiguredClient client = createClient(settings, clusterIdCapture)) {
      runCommand(client);
    }

    assertFindSpan(null, null);
  }

  @Test
  void srvHostIsPreferredOverTheSeedsItStandsIn() {
    // srvHost was added in 3.10
    Method srvHost = srvHostSetter();
    assumeTrue(srvHost != null);

    ClusterIdCapture clusterIdCapture = new ClusterIdCapture();
    ClusterSettings.Builder clusterSettings = ClusterSettings.builder();
    invoke(srvHost, clusterSettings, "cluster0.example.invalid");
    MongoClientSettings settings =
        MongoClientSettings.builder()
            .clusterSettings(clusterSettings.addClusterListener(clusterIdCapture).build())
            .build();

    // closing an SRV client races the resolver thread and can report an uncaught exception
    runCommand(createClient(settings, clusterIdCapture));

    assertFindSpan("mongodb+srv://cluster0.example.invalid", null);
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
