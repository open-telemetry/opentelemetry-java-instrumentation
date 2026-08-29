/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static java.util.Arrays.asList;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
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
    // SRV settings include a placeholder seed that the client never contacts
    ClusterIdCapture clusterId = new ClusterIdCapture();
    MongoClientSettings settings =
        MongoClientSettings.builder()
            .applyToClusterSettings(
                builder ->
                    builder.srvHost("cluster0.example.invalid").addClusterListener(clusterId))
            .build();

    // closing an SRV client races the resolver thread and can report an uncaught exception
    runCommand(createClient(settings, clusterId));

    assertFindSpan("mongodb+srv://cluster0.example.invalid", null);
  }

  @Test
  void theSelectedServerIsTheOperationDurationNetworkPeerForSeveralSeeds() {
    try (ConfiguredClient client =
        createClient(
            asList(
                new ServerAddress("db1.example", 27017),
                new ServerAddress("db2.example", 27018)))) {
      runCommand(client);
    }

    assertFindSpan(null, null);
    assertDurationMetric(
        testing,
        "io.opentelemetry.mongo-4.0",
        DB_SYSTEM_NAME,
        DB_NAMESPACE,
        DB_OPERATION_NAME,
        DB_COLLECTION_NAME,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT);
    if (emitStableDatabaseSemconv()) {
      testing.waitAndAssertMetrics(
          "io.opentelemetry.mongo-4.0",
          metric ->
              metric
                  .hasName("db.client.operation.duration")
                  .hasHistogramSatisfying(
                      histogram ->
                          histogram.hasPointsSatisfying(
                              point ->
                                  point
                                      .hasAttribute(NETWORK_PEER_ADDRESS, "selected.example")
                                      .hasAttribute(NETWORK_PEER_PORT, 27099L))));
    }
  }
}
