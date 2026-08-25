/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.testing;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
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

import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterId;
import com.mongodb.connection.ConnectionDescription;
import com.mongodb.connection.ServerId;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Verifies that a client configured with one address reports it instead of the server that answered
 * a command, while a client configured with several seeds keeps reporting the selected server.
 *
 * <p>The client is built against seeds it never connects to, which is enough for the driver to
 * construct its cluster. The command events a connected client would deliver are handed to the same
 * listener the instrumentation installed, describing a different selected server. What is exercised
 * is the whole path a real command takes: the driver builds the client, the instrumentation reads
 * the cluster configuration, and every command event finds it again through the selected server.
 */
@TestInstance(PER_CLASS)
public abstract class AbstractMongoConfiguredTargetTest {

  private static final String DATABASE_NAME = "test_db";
  private static final String COLLECTION_NAME = "testCollection";

  // the server the driver picked, which is deliberately none of the configured seeds
  private static final ServerAddress SELECTED_SERVER = new ServerAddress("selected.example", 27099);

  private static final AtomicInteger requestIds = new AtomicInteger();

  protected abstract InstrumentationExtension testing();

  /**
   * Builds a client configured with {@code seeds} and returns the cluster identity the driver gave
   * it together with the command listener the instrumentation added to it. The client is never
   * connected to, so the seeds do not have to exist.
   */
  protected abstract ConfiguredClient createClient(List<ServerAddress> seeds);

  /**
   * Whether the driver can hold a literal ipv6 address in its cluster settings, which it re-parses
   * without brackets before 3.3 and therefore rejects.
   */
  protected boolean supportsIpv6Seeds() {
    return true;
  }

  @Test
  void severalSeedsKeepReportingTheSelectedServer() {
    try (ConfiguredClient client =
        createClient(
            asList(
                new ServerAddress("db1.example", 27017),
                new ServerAddress("db2.example", 27018)))) {
      runCommand(client);
    }

    assertFindSpan("selected.example", 27099L);
  }

  @Test
  void singleSeedKeepsItsPort() {
    try (ConfiguredClient client =
        createClient(singletonList(new ServerAddress("db1.example", 27017)))) {
      runCommand(client);
    }

    assertFindSpan("db1.example", 27017L);
  }

  @Test
  void severalIpv6SeedsKeepReportingTheSelectedServer() {
    assumeTrue(supportsIpv6Seeds());

    try (ConfiguredClient client =
        createClient(
            asList(new ServerAddress("[::1]", 27017), new ServerAddress("[fe80::1]", 27018)))) {
      runCommand(client);
    }

    assertFindSpan("selected.example", 27099L);
  }

  @Test
  void commandWithNoDatabaseIsNamedByTheSelectedServerForASeedGroup() {
    try (ConfiguredClient client =
        createClient(
            asList(
                new ServerAddress("db1.example", 27017),
                new ServerAddress("db2.example", 27018)))) {
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
                                    ? "listDatabases selected.example:27099"
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
                            .hasAttributesSatisfyingExactly(
                                equalTo(
                                    SERVER_ADDRESS,
                                    emitStableDatabaseSemconv()
                                        ? configuredAddress
                                        : SELECTED_SERVER.getHost()),
                                equalTo(
                                    SERVER_PORT,
                                    emitStableDatabaseSemconv()
                                        ? configuredPort
                                        : Long.valueOf(SELECTED_SERVER.getPort())),
                                satisfies(
                                    maybeStable(DB_STATEMENT),
                                    val ->
                                        val.satisfies(
                                            v ->
                                                assertThat(v.replaceAll(" ", ""))
                                                    .isEqualTo(
                                                        "{\"find\":\"" + COLLECTION_NAME + "\"}"))),
                                equalTo(maybeStable(DB_SYSTEM), MONGODB),
                                equalTo(
                                    DB_CONNECTION_STRING,
                                    emitStableDatabaseSemconv()
                                        ? null
                                        : "mongodb://"
                                            + SELECTED_SERVER.getHost()
                                            + ":"
                                            + SELECTED_SERVER.getPort()),
                                equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                                equalTo(maybeStable(DB_OPERATION), "find"),
                                equalTo(maybeStable(DB_MONGODB_COLLECTION), COLLECTION_NAME))));
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
        // driver 5.0 replaced the constructor above with one that also carries a request context
        // and an operation id
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
      // a driver without this class still has the constructors that do not take one
      return Void.class;
    }
  }

  /** A client, the cluster the driver built for it and the listener the agent added to it. */
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
