/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.testing;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_COLLECTION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CONNECTION_STRING;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_MONGODB_COLLECTION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.MONGODB;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import com.mongodb.ConnectionString;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(PER_CLASS)
public abstract class AbstractMongoConfiguredTargetTest {

  private static final String DATABASE_NAME = "test_db";
  private static final String COLLECTION_NAME = "testCollection";

  // deliberately different from every configured seed
  private static final ServerAddress SELECTED_SERVER = new ServerAddress("selected.example", 27099);

  private static final AtomicInteger requestIds = new AtomicInteger();

  protected abstract InstrumentationExtension testing();

  // the client is never connected, so seeds do not need to resolve
  protected abstract ConfiguredClient createClient(List<ServerAddress> seeds);

  protected boolean supportsIpv6Seeds() {
    return true;
  }

  protected static ConnectionString resolvedSrvConnectionString() {
    return resolvedSrvConnectionString(
        "mongodb+srv://user:password@cluster0.example.invalid/database?tls=true#fragment");
  }

  protected static ConnectionString resolvedSrvConnectionString(String connectionString) {
    return new ConnectionString("mongodb://placeholder.example") {
      @Override
      public List<String> getHosts() {
        return asList("resolved2.example:27018", "resolved1.example:27017");
      }

      @Override
      public String getConnectionString() {
        return connectionString;
      }
    };
  }

  protected static void applySrvHost(ClusterSettings.Builder builder, String host) {
    Method srvHost = srvHostSetter();
    assumeTrue(srvHost != null);
    try {
      srvHost.invoke(builder, host);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }

  // srvHost was added in 3.10
  private static Method srvHostSetter() {
    try {
      return ClusterSettings.Builder.class.getMethod("srvHost", String.class);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  @Test
  void mixedPortSeedsRetainTheirPortsInTheStableLogicalServer() {
    try (ConfiguredClient client =
        createClient(
            asList(
                new ServerAddress("db2.example", 27018),
                new ServerAddress("db1.example", 27017)))) {
      runCommand(client);
    }

    assertFindSpan("db1.example:27017,db2.example:27018", null);
  }

  @Test
  void singleOmittedDefaultPortIsNotReported() {
    try (ConfiguredClient client = createClient(singletonList(new ServerAddress("db1.example")))) {
      runCommand(client);
    }

    assertFindSpan("db1.example", null);
  }

  @Test
  void singleCustomPortIsReportedSeparately() {
    try (ConfiguredClient client =
        createClient(singletonList(new ServerAddress("db1.example", 27018)))) {
      runCommand(client);
    }

    assertFindSpan("db1.example", 27018L);
  }

  @Test
  void severalMaterializedDefaultPortsAreNotReported() {
    try (ConfiguredClient client =
        createClient(
            asList(
                new ServerAddress("db2.example", 27017),
                new ServerAddress("db1.example", 27017)))) {
      runCommand(client);
    }

    assertFindSpan("db1.example,db2.example", null);
  }

  @Test
  void sharedCustomPortIsIncludedInEveryAddress() {
    try (ConfiguredClient client =
        createClient(
            asList(
                new ServerAddress("db2.example", 27018),
                new ServerAddress("db1.example", 27018)))) {
      runCommand(client);
    }

    assertFindSpan("db1.example:27018,db2.example:27018", null);
  }

  @Test
  void duplicateConfiguredSeedsAreRemoved() {
    try (ConfiguredClient client =
        createClient(
            asList(
                new ServerAddress("db2.example", 27018),
                new ServerAddress("db1.example", 27017),
                new ServerAddress("db1.example", 27017)))) {
      runCommand(client);
    }

    assertFindSpan("db1.example:27017,db2.example:27018", null);
  }

  @Test
  void onlyTheFirstFiveConfiguredSeedsAreReported() {
    try (ConfiguredClient client =
        createClient(
            asList(
                new ServerAddress("db6.example", 27017),
                new ServerAddress("db5.example", 27017),
                new ServerAddress("db4.example", 27017),
                new ServerAddress("db3.example", 27017),
                new ServerAddress("db2.example", 27017),
                new ServerAddress("db1.example", 27017)))) {
      runCommand(client);
    }

    assertFindSpan("db1.example,db2.example,db3.example,db4.example,db5.example", null);
  }

  @Test
  void unsafeConfiguredSeedTargetIsOmitted() {
    try (ConfiguredClient client =
        createClient(singletonList(new ServerAddress("user%3Apassword%40db1.example", 27017)))) {
      runCommand(client);
    }

    assertFindSpan(null, null);
  }

  @Test
  void severalIpv6SeedsUseUnambiguousFormatting() {
    assumeTrue(supportsIpv6Seeds());

    try (ConfiguredClient client =
        createClient(
            asList(new ServerAddress("[fe80::1]", 27018), new ServerAddress("[::1]", 27017)))) {
      runCommand(client);
    }

    assertFindSpan("[::1]:27017,[fe80::1]:27018", null);
  }

  @Test
  void commandWithNoDatabaseUsesConfiguredSeedsInStableSpanName() {
    try (ConfiguredClient client =
        createClient(
            asList(
                new ServerAddress("db2.example", 27018),
                new ServerAddress("db1.example", 27018)))) {
      runCommand(
          client, null, "listDatabases", new BsonDocument("listDatabases", new BsonInt32(1)));
    }

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(
                                emitStableDatabaseSemconv()
                                    ? "listDatabases db1.example:27018,db2.example:27018"
                                    : "listDatabases")
                            .hasKind(CLIENT)));
  }

  protected static void runCommand(ConfiguredClient client) {
    runCommand(
        client, DATABASE_NAME, "find", new BsonDocument("find", new BsonString(COLLECTION_NAME)));
  }

  private static void runCommand(
      ConfiguredClient client, String databaseName, String commandName, BsonDocument command) {
    ConnectionDescription connectionDescription =
        new ConnectionDescription(new ServerId(client.getClusterId(), SELECTED_SERVER));
    int requestId = requestIds.incrementAndGet();
    CommandListener listener = client.getCommandListener();
    listener.commandStarted(
        commandStartedEvent(requestId, connectionDescription, databaseName, commandName, command));
    listener.commandSucceeded(
        commandSucceededEvent(requestId, connectionDescription, commandName, new BsonDocument()));
  }

  @SuppressWarnings("deprecation")
  // TODO DB_CONNECTION_STRING deprecation
  protected void assertFindSpan(String configuredAddress, Long configuredPort) {
    List<AttributeAssertion> attributes = new ArrayList<>();
    attributes.add(
        equalTo(
            SERVER_ADDRESS,
            emitStableDatabaseSemconv() ? configuredAddress : SELECTED_SERVER.getHost()));
    attributes.add(
        equalTo(
            SERVER_PORT,
            emitStableDatabaseSemconv()
                ? configuredPort
                : Long.valueOf(SELECTED_SERVER.getPort())));
    attributes.add(equalTo(NETWORK_PEER_ADDRESS, null));
    attributes.add(equalTo(NETWORK_PEER_PORT, null));
    if (emitOldDatabaseSemconv()) {
      attributes.add(
          satisfies(
              DB_STATEMENT,
              val ->
                  val.satisfies(
                      v ->
                          assertThat(v.replaceAll(" ", ""))
                              .isEqualTo("{\"find\":\"" + COLLECTION_NAME + "\"}"))));
      attributes.add(equalTo(DB_SYSTEM, MONGODB));
      attributes.add(
          equalTo(
              DB_CONNECTION_STRING,
              "mongodb://" + SELECTED_SERVER.getHost() + ":" + SELECTED_SERVER.getPort()));
      attributes.add(equalTo(DB_NAME, DATABASE_NAME));
      attributes.add(equalTo(DB_OPERATION, "find"));
      attributes.add(equalTo(DB_MONGODB_COLLECTION, COLLECTION_NAME));
    }
    if (emitStableDatabaseSemconv()) {
      attributes.add(
          satisfies(
              DB_QUERY_TEXT,
              val ->
                  val.satisfies(
                      v ->
                          assertThat(v.replaceAll(" ", ""))
                              .isEqualTo("{\"find\":\"" + COLLECTION_NAME + "\"}"))));
      attributes.add(equalTo(DB_SYSTEM_NAME, MONGODB));
      attributes.add(equalTo(DB_NAMESPACE, DATABASE_NAME));
      attributes.add(equalTo(DB_OPERATION_NAME, "find"));
      attributes.add(equalTo(DB_COLLECTION_NAME, COLLECTION_NAME));
    }
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(
                                emitStableDatabaseSemconv()
                                    ? "find " + COLLECTION_NAME
                                    : "find " + DATABASE_NAME + "." + COLLECTION_NAME)
                            .hasKind(CLIENT)
                            .hasAttributesSatisfyingExactly(attributes)));
  }

  private static CommandStartedEvent commandStartedEvent(
      int requestId,
      ConnectionDescription connectionDescription,
      String databaseName,
      String commandName,
      BsonDocument command) {
    return construct(
        CommandStartedEvent.class,
        new Class<?>[] {
          int.class, ConnectionDescription.class, String.class, String.class, BsonDocument.class
        },
        new Object[] {requestId, connectionDescription, databaseName, commandName, command},
        new Class<?>[] {
          requestContextType(),
          long.class,
          int.class,
          ConnectionDescription.class,
          String.class,
          String.class,
          BsonDocument.class
        },
        new Object[] {
          null, 0L, requestId, connectionDescription, databaseName, commandName, command
        });
  }

  private static CommandSucceededEvent commandSucceededEvent(
      int requestId,
      ConnectionDescription connectionDescription,
      String commandName,
      BsonDocument response) {
    return construct(
        CommandSucceededEvent.class,
        new Class<?>[] {
          int.class, ConnectionDescription.class, String.class, BsonDocument.class, long.class
        },
        new Object[] {
          requestId, connectionDescription, commandName, response, MILLISECONDS.toNanos(1)
        },
        new Class<?>[] {
          requestContextType(),
          long.class,
          int.class,
          ConnectionDescription.class,
          String.class,
          String.class,
          BsonDocument.class,
          long.class
        },
        new Object[] {
          null,
          0L,
          requestId,
          connectionDescription,
          DATABASE_NAME,
          commandName,
          response,
          MILLISECONDS.toNanos(1)
        });
  }

  private static <T> T construct(
      Class<T> type,
      Class<?>[] legacyParameterTypes,
      Object[] legacyArguments,
      Class<?>[] currentParameterTypes,
      Object[] currentArguments) {
    try {
      try {
        return type.getConstructor(legacyParameterTypes).newInstance(legacyArguments);
      } catch (NoSuchMethodException ignored) {
        // driver 5.0 removed the legacy constructors, leaving only the request context and
        // operation id form
        return type.getConstructor(currentParameterTypes).newInstance(currentArguments);
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Cannot build a " + type.getSimpleName(), e);
    }
  }

  private static Class<?> requestContextType() {
    try {
      return Class.forName("com.mongodb.RequestContext");
    } catch (ClassNotFoundException ignored) {
      return Void.class;
    }
  }

  protected static class ConfiguredClient implements AutoCloseable {

    private final ClusterId clusterId;
    private final CommandListener commandListener;
    private final Runnable closeAction;

    public ConfiguredClient(
        ClusterId clusterId, CommandListener commandListener, Runnable closeAction) {
      this.clusterId = clusterId;
      this.commandListener = commandListener;
      this.closeAction = closeAction;
    }

    ClusterId getClusterId() {
      return clusterId;
    }

    CommandListener getCommandListener() {
      return commandListener;
    }

    @Override
    public void close() {
      closeAction.run();
    }
  }
}
