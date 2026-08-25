/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoInstrumenterFactory.DEFAULT_MAX_NORMALIZED_QUERY_LENGTH;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandStartedEvent;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.junit.jupiter.api.Test;

class MongoConfiguredTargetTest {

  private static final ServerAddress SELECTED_SERVER = new ServerAddress("db2.example", 27018);

  private final MongoDbAttributesGetter getter =
      new MongoDbAttributesGetter(true, DEFAULT_MAX_NORMALIZED_QUERY_LENGTH);

  @Test
  void configuredSeedGroupKeepsReportingTheSelectedServer() {
    ClusterId clusterId = new ClusterId();
    MongoClusterTargets.register(
        clusterId,
        MongoServerTarget.seeds(asList(new ServerAddress("db1.example", 27017), SELECTED_SERVER)));
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");

    assertThat(getter.getServerAddress(event)).isEqualTo("db2.example");
    assertThat(getter.getServerPort(event)).isEqualTo(27018);
  }

  @Test
  void configuredSingleSeedKeepsItsPort() {
    ClusterId clusterId =
        configuredCluster(
            MongoServerTarget.seeds(singletonList(new ServerAddress("db1.example", 27017))));
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");

    assertThat(getter.getServerAddress(event))
        .isEqualTo(emitStableDatabaseSemconv() ? "db1.example" : "db2.example");
    assertThat(getter.getServerPort(event)).isEqualTo(emitStableDatabaseSemconv() ? 27017 : 27018);
  }

  @Test
  void configuredSrvHostDescribesEveryCommand() {
    ClusterId clusterId = configuredCluster(MongoServerTarget.srvHost("cluster0.example.com"));
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");

    assertThat(getter.getServerAddress(event))
        .isEqualTo(emitStableDatabaseSemconv() ? "cluster0.example.com" : "db2.example");
    assertThat(getter.getServerPort(event)).isEqualTo(emitStableDatabaseSemconv() ? null : 27018);
  }

  @Test
  void clientWithNoConfiguredTargetKeepsReportingTheServerThatAnswered() {
    CommandStartedEvent event = commandStartedEvent(new ClusterId(), "test_db", "find");

    assertThat(getter.getServerAddress(event)).isEqualTo("db2.example");
    assertThat(getter.getServerPort(event)).isEqualTo(27018);
  }

  @Test
  @SuppressWarnings("deprecation") // db.connection_string is part of the old semantic conventions
  void theOldConnectionStringKeepsDescribingTheServerThatAnswered() {
    ClusterId clusterId =
        configuredCluster(
            MongoServerTarget.seeds(
                asList(new ServerAddress("db1.example", 27017), SELECTED_SERVER)));
    CommandStartedEvent event = commandStartedEvent(clusterId, "test_db", "find");

    assertThat(getter.getConnectionString(event)).isEqualTo("mongodb://db2.example:27018");
  }

  @Test
  void commandWithNoDatabaseIsNamedByTheSelectedServerForASeedGroup() {
    ClusterId clusterId = new ClusterId();
    MongoClusterTargets.register(
        clusterId,
        MongoServerTarget.seeds(asList(new ServerAddress("db1.example", 27017), SELECTED_SERVER)));
    CommandStartedEvent event = commandStartedEvent(clusterId, null, "listDatabases");

    String spanName = new MongoSpanNameExtractor(getter).extract(event);

    assertThat(spanName)
        .isEqualTo(
            emitStableDatabaseSemconv() ? "listDatabases db2.example:27018" : "listDatabases");
  }

  private static ClusterId configuredCluster(MongoServerTarget target) {
    ClusterId clusterId = new ClusterId();
    MongoClusterTargets.register(clusterId, target);
    return clusterId;
  }

  private static CommandStartedEvent commandStartedEvent(
      ClusterId clusterId, String databaseName, String commandName) {
    ConnectionDescription connectionDescription =
        new ConnectionDescription(new ServerId(clusterId, SELECTED_SERVER));
    return new CommandStartedEvent(
        0,
        connectionDescription,
        databaseName,
        commandName,
        new BsonDocument(commandName, new BsonInt32(1)));
  }
}
