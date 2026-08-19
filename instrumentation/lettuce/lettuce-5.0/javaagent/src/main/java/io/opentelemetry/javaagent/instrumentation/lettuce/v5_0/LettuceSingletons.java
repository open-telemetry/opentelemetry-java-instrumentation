/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.CommandArgsAccessor;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.DefaultEndpoint;
import io.lettuce.core.protocol.RedisCommand;
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
import javax.annotation.Nullable;

public class LettuceSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.lettuce-5.0";

  private static final Instrumenter<RedisCommand<?, ?, ?>, Void> instrumenter;
  private static final Instrumenter<LettuceBatchRequest, Void> batchInstrumenter;
  private static final Instrumenter<RedisURI, Void> connectInstrumenter;

  public static final ContextKey<Context> COMMAND_CONTEXT_KEY =
      ContextKey.named("opentelemetry-lettuce-v5_0-context-key");

  public static final VirtualField<AsyncCommand<?, ?, ?>, Context> CONTEXT =
      VirtualField.find(AsyncCommand.class, Context.class);

  public static final VirtualField<DefaultEndpoint, RedisURI> ENDPOINT_URI =
      VirtualField.find(DefaultEndpoint.class, RedisURI.class);

  public static final VirtualField<RedisCommand<?, ?, ?>, RedisURI> COMMAND_URI =
      VirtualField.find(RedisCommand.class, RedisURI.class);

  public static final VirtualField<RedisCommand<?, ?, ?>, Integer> COMMAND_DATABASE =
      VirtualField.find(RedisCommand.class, Integer.class);

  private static final VirtualField<DefaultEndpoint, DatabaseState> ENDPOINT_DATABASE =
      VirtualField.find(DefaultEndpoint.class, DatabaseState.class);

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

    LettuceConnectNetworkAttributesGetter connectNetworkAttributesGetter =
        new LettuceConnectNetworkAttributesGetter();

    connectInstrumenter =
        Instrumenter.<RedisURI, Void>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, redisUri -> "CONNECT")
            .addAttributesExtractor(
                ServerAttributesExtractor.create(connectNetworkAttributesGetter))
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(
                    connectNetworkAttributesGetter, GlobalOpenTelemetry.get()))
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
    if (connection instanceof RedisChannelHandler) {
      Object channelWriter = ((RedisChannelHandler<?, ?>) connection).getChannelWriter();
      if (channelWriter instanceof DefaultEndpoint) {
        DefaultEndpoint endpoint = (DefaultEndpoint) channelWriter;
        COMMAND_URI.set(command, ENDPOINT_URI.get(endpoint));
        attachDatabaseState(command, ENDPOINT_DATABASE.get(endpoint));
      }
    }
  }

  public static void attachEndpoint(DefaultEndpoint endpoint, RedisURI redisUri) {
    ENDPOINT_URI.set(endpoint, redisUri);
    ENDPOINT_DATABASE.set(endpoint, new DatabaseState(redisUri.getDatabase()));
  }

  public static void attachDatabase(RedisCommand<?, ?, ?> command, DefaultEndpoint endpoint) {
    attachDatabaseState(command, ENDPOINT_DATABASE.get(endpoint));
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
  static Integer databaseIndex(DefaultEndpoint endpoint) {
    DatabaseState state = ENDPOINT_DATABASE.get(endpoint);
    return state == null ? null : state.get();
  }

  private static void attachDatabaseState(
      RedisCommand<?, ?, ?> command, @Nullable DatabaseState state) {
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
    Long database = CommandArgsAccessor.getFirstInteger(command.getArgs());
    return database == null ? null : database.intValue();
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
