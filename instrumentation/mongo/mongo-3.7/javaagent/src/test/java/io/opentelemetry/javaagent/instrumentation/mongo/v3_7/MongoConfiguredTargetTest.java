/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_7;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.UnixServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import io.opentelemetry.instrumentation.mongo.testing.AbstractMongoConfiguredTargetTest;
import io.opentelemetry.instrumentation.mongo.testing.ClusterIdCapture;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MongoConfiguredTargetTest extends AbstractMongoConfiguredTargetTest {

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
            .applyToClusterSettings(
                builder -> builder.hosts(seeds).addClusterListener(clusterIdCapture))
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
            .applyConnectionString(resolvedSrvConnectionString())
            .applyToClusterSettings(builder -> builder.addClusterListener(clusterIdCapture))
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
            .applyConnectionString(
                resolvedSrvConnectionString(
                    "mongodb+srv://user%3Apassword%40cluster0.example.invalid"))
            .applyToClusterSettings(builder -> builder.addClusterListener(clusterIdCapture))
            .build();

    try (ConfiguredClient client = createClient(settings, clusterIdCapture)) {
      runCommand(client);
    }

    assertFindSpan(null, null);
  }

  @Test
  void srvHostIsPreferredOverTheSeedsItStandsIn() {
    ClusterIdCapture clusterIdCapture = new ClusterIdCapture();
    MongoClientSettings settings =
        MongoClientSettings.builder()
            .applyToClusterSettings(
                builder -> {
                  applySrvHost(builder, "cluster0.example.invalid");
                  builder.addClusterListener(clusterIdCapture);
                })
            .build();

    // closing an SRV client races the resolver thread and can report an uncaught exception
    runCommand(createClient(settings, clusterIdCapture));

    assertFindSpan("mongodb+srv://cluster0.example.invalid", null);
  }

  @Test
  void relativeUnixSocketOmitsTheStableTarget() {
    try (ConfiguredClient client =
        createClient(singletonList(new UnixServerAddress("mongodb.sock")))) {
      runCommand(client);
    }

    assertFindSpan(null, null);
  }

  @Test
  void relativeUnixSocketInSeedListOmitsTheStableTarget() {
    try (ConfiguredClient client =
        createClient(
            asList(
                new UnixServerAddress("mongodb.sock"),
                new ServerAddress("configured.example", 27017)))) {
      runCommand(client);
    }

    assertFindSpan(null, null);
  }
}
