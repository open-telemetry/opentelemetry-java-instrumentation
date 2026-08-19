/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import com.lambdaworks.redis.ReactiveCommandDispatcher;
import com.lambdaworks.redis.RedisChannelHandler;
import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.api.StatefulConnection;
import com.lambdaworks.redis.protocol.AsyncCommand;
import com.lambdaworks.redis.protocol.CommandType;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

public class LettuceSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.lettuce-4.0";

  private static final Instrumenter<RedisCommand<?, ?, ?>, Void> instrumenter;
  private static final Instrumenter<LettuceBatchRequest, Void> batchInstrumenter;
  private static final Instrumenter<RedisURI, Void> connectInstrumenter;

  public static final ContextKey<Context> COMMAND_CONTEXT_KEY =
      ContextKey.named("opentelemetry-lettuce-v4_0-context-key");

  public static final VirtualField<RedisCommand<?, ?, ?>, Context> CONTEXT =
      VirtualField.find(RedisCommand.class, Context.class);

  public static final VirtualField<ReactiveCommandDispatcher<?, ?, ?>, Context>
      REACTIVE_DISPATCHER_CONTEXT =
          VirtualField.find(ReactiveCommandDispatcher.class, Context.class);

  public static final VirtualField<RedisChannelHandler<?, ?>, InetSocketAddress>
      CONNECTION_ADDRESS = VirtualField.find(RedisChannelHandler.class, InetSocketAddress.class);

  public static final VirtualField<RedisCommand<?, ?, ?>, InetSocketAddress> COMMAND_ADDRESS =
      VirtualField.find(RedisCommand.class, InetSocketAddress.class);

  public static final VirtualField<RedisChannelHandler<?, ?>, RedisURI> CONNECTION_URI =
      VirtualField.find(RedisChannelHandler.class, RedisURI.class);

  public static final VirtualField<RedisCommand<?, ?, ?>, RedisURI> COMMAND_URI =
      VirtualField.find(RedisCommand.class, RedisURI.class);

  public static final VirtualField<RedisCommand<?, ?, ?>, Integer> COMMAND_DATABASE =
      VirtualField.find(RedisCommand.class, Integer.class);

  private static final VirtualField<RedisChannelHandler<?, ?>, DatabaseState> CONNECTION_DATABASE =
      VirtualField.find(RedisChannelHandler.class, DatabaseState.class);

  private static final VirtualField<RedisCommand<?, ?, ?>, DatabaseState> COMMAND_DATABASE_STATE =
      VirtualField.find(RedisCommand.class, DatabaseState.class);

  static {
    LettuceDbAttributesGetter dbAttributesGetter = new LettuceDbAttributesGetter();
    // Redis semantic conventions don't follow the regular pattern of adding db.namespace to the
    // span name.
    LettuceDbAttributesGetter spanNameAttributesGetter =
        new LettuceDbAttributesGetter() {
          @Override
          @Nullable
          public String getDbNamespace(RedisCommand<?, ?, ?> request) {
            return null;
          }
        };

    InstrumenterBuilder<RedisCommand<?, ?, ?>, Void> builder =
        Instrumenter.<RedisCommand<?, ?, ?>, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(spanNameAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());

    LettuceBatchAttributesGetter batchAttributesGetter = new LettuceBatchAttributesGetter();
    LettuceBatchAttributesGetter batchSpanNameAttributesGetter =
        new LettuceBatchAttributesGetter() {
          @Override
          @Nullable
          public String getDbNamespace(LettuceBatchRequest request) {
            return null;
          }
        };
    InstrumenterBuilder<LettuceBatchRequest, Void> batchBuilder =
        Instrumenter.<LettuceBatchRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(batchSpanNameAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(batchAttributesGetter))
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(batchBuilder);
    batchInstrumenter = batchBuilder.buildInstrumenter(SpanKindExtractor.alwaysClient());

    LettuceConnectNetworkAttributesGetter netAttributesGetter =
        new LettuceConnectNetworkAttributesGetter();

    connectInstrumenter =
        Instrumenter.<RedisURI, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, redisUri -> "CONNECT")
            .addAttributesExtractor(ServerAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(
                    netAttributesGetter, GlobalOpenTelemetry.get()))
            .addAttributesExtractor(new LettuceConnectAttributesExtractor())
            .setEnabled(
                DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "lettuce")
                    .get("connection_telemetry")
                    .getBoolean("enabled", false))
            .buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<RedisCommand<?, ?, ?>, Void> instrumenter() {
    return instrumenter;
  }

  public static Instrumenter<LettuceBatchRequest, Void> batchInstrumenter() {
    return batchInstrumenter;
  }

  public static Instrumenter<RedisURI, Void> connectInstrumenter() {
    return connectInstrumenter;
  }

  public static void attachAddress(
      RedisCommand<?, ?, ?> command, StatefulConnection<?, ?> connection) {
    COMMAND_ADDRESS.set(command, serverAddress(connection));
    COMMAND_URI.set(command, redisUri(connection));
    if (connection instanceof RedisChannelHandler) {
      attachDatabase(command, CONNECTION_DATABASE.get((RedisChannelHandler<?, ?>) connection));
    }
  }

  public static void attachConnection(RedisChannelHandler<?, ?> connection, RedisURI redisUri) {
    CONNECTION_URI.set(connection, redisUri);
    CONNECTION_DATABASE.set(connection, new DatabaseState(redisUri.getDatabase()));
  }

  public static void copyDatabaseState(RedisCommand<?, ?, ?> source, RedisCommand<?, ?, ?> target) {
    COMMAND_DATABASE.set(target, COMMAND_DATABASE.get(source));
    COMMAND_DATABASE_STATE.set(target, COMMAND_DATABASE_STATE.get(source));
  }

  public static void trackDatabaseSelection(
      RedisCommand<?, ?, ?> command, @Nullable AsyncCommand<?, ?, ?> asyncCommand) {
    DatabaseState state = COMMAND_DATABASE_STATE.get(command);
    Integer database = selectedDatabase(command);
    if (state == null || database == null || asyncCommand == null) {
      return;
    }
    asyncCommand.handle(
        (value, throwable) -> {
          if (throwable == null && "OK".equals(value)) {
            state.set(database);
          }
          return null;
        });
  }

  public static void updateDatabase(RedisCommand<?, ?, ?> command, boolean successful) {
    DatabaseState state = COMMAND_DATABASE_STATE.get(command);
    Integer database = selectedDatabase(command);
    if (successful && state != null && database != null) {
      state.set(database);
    }
  }

  @Nullable
  static Integer databaseIndex(StatefulConnection<?, ?> connection) {
    if (!(connection instanceof RedisChannelHandler)) {
      return null;
    }
    DatabaseState state = CONNECTION_DATABASE.get((RedisChannelHandler<?, ?>) connection);
    return state == null ? null : state.get();
  }

  private static void attachDatabase(RedisCommand<?, ?, ?> command, @Nullable DatabaseState state) {
    if (state != null) {
      COMMAND_DATABASE.set(command, state.get());
      COMMAND_DATABASE_STATE.set(command, state);
    }
  }

  @Nullable
  private static Integer selectedDatabase(RedisCommand<?, ?, ?> command) {
    if (!command.getType().equals(CommandType.SELECT) || command.getArgs() == null) {
      return null;
    }
    Long database = command.getArgs().getFirstInteger();
    return database == null ? null : database.intValue();
  }

  @Nullable
  static InetSocketAddress serverAddress(StatefulConnection<?, ?> connection) {
    return connection instanceof RedisChannelHandler
        ? CONNECTION_ADDRESS.get((RedisChannelHandler<?, ?>) connection)
        : null;
  }

  @Nullable
  static RedisURI redisUri(StatefulConnection<?, ?> connection) {
    return connection instanceof RedisChannelHandler
        ? CONNECTION_URI.get((RedisChannelHandler<?, ?>) connection)
        : null;
  }

  private static final class DatabaseState {
    private volatile int database;

    private DatabaseState(int database) {
      this.database = database;
    }

    private int get() {
      return database;
    }

    private void set(int database) {
      this.database = database;
    }
  }

  private LettuceSingletons() {}
}
