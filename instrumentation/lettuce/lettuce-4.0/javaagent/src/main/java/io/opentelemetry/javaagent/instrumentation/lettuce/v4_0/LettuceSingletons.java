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
import com.lambdaworks.redis.cluster.RedisClusterClient;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.net.InetSocketAddress;
import java.util.Collection;
import javax.annotation.Nullable;

public class LettuceSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.lettuce-4.0";

  private static final Instrumenter<RedisCommand<?, ?, ?>, Void> instrumenter;
  private static final Instrumenter<LettuceBatchRequest, Void> batchInstrumenter;
  private static final Instrumenter<RedisURI, Void> connectInstrumenter;

  public static final ContextKey<Context> COMMAND_CONTEXT_KEY =
      ContextKey.named("opentelemetry-lettuce-v4_0-context-key");

  public static final ContextKey<LettucePeerAddress> COMMAND_PEER_KEY =
      ContextKey.named("opentelemetry-lettuce-v4_0-peer-key");

  public static final VirtualField<RedisCommand<?, ?, ?>, Context> CONTEXT =
      VirtualField.find(RedisCommand.class, Context.class);

  public static final VirtualField<ReactiveCommandDispatcher<?, ?, ?>, Context>
      REACTIVE_DISPATCHER_CONTEXT =
          VirtualField.find(ReactiveCommandDispatcher.class, Context.class);

  public static final VirtualField<RedisChannelHandler<?, ?>, InetSocketAddress>
      CONNECTION_ADDRESS = VirtualField.find(RedisChannelHandler.class, InetSocketAddress.class);

  public static final VirtualField<RedisCommand<?, ?, ?>, LettucePeerAddress> COMMAND_PEER =
      VirtualField.find(RedisCommand.class, LettucePeerAddress.class);

  public static final VirtualField<RedisCommand<?, ?, ?>, InetSocketAddress> COMMAND_ADDRESS =
      VirtualField.find(RedisCommand.class, InetSocketAddress.class);

  public static final VirtualField<RedisChannelHandler<?, ?>, Integer> CONNECTION_DATABASE_INDEX =
      VirtualField.find(RedisChannelHandler.class, Integer.class);

  public static final VirtualField<RedisCommand<?, ?, ?>, Integer> COMMAND_DATABASE_INDEX =
      VirtualField.find(RedisCommand.class, Integer.class);

  public static final VirtualField<RedisChannelHandler<?, ?>, RedisServerTarget> CONNECTION_TARGET =
      VirtualField.find(RedisChannelHandler.class, RedisServerTarget.class);

  public static final VirtualField<RedisCommand<?, ?, ?>, RedisServerTarget> COMMAND_TARGET =
      VirtualField.find(RedisCommand.class, RedisServerTarget.class);

  public static final VirtualField<RedisClusterClient, RedisServerTarget> CLUSTER_CLIENT_TARGET =
      VirtualField.find(RedisClusterClient.class, RedisServerTarget.class);

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
    COMMAND_PEER.set(command, new LettucePeerAddress());
    COMMAND_DATABASE_INDEX.set(command, databaseIndex(connection));
    COMMAND_TARGET.set(command, serverTarget(connection));
  }

  @Nullable
  static InetSocketAddress serverAddress(StatefulConnection<?, ?> connection) {
    return connection instanceof RedisChannelHandler
        ? CONNECTION_ADDRESS.get((RedisChannelHandler<?, ?>) connection)
        : null;
  }

  public static void recordCommandPeers(Object message, InetSocketAddress address) {
    if (message instanceof RedisCommand) {
      recordCommandPeer((RedisCommand<?, ?, ?>) message, address);
    } else if (message instanceof Collection) {
      for (Object item : (Collection<?>) message) {
        if (item instanceof RedisCommand) {
          recordCommandPeer((RedisCommand<?, ?, ?>) item, address);
        }
      }
    }
  }

  private static void recordCommandPeer(RedisCommand<?, ?, ?> command, InetSocketAddress address) {
    LettucePeerAddress peer = COMMAND_PEER.get(command);
    if (peer != null) {
      peer.record(address);
    }
  }

  @Nullable
  static InetSocketAddress commandPeerAddress(RedisCommand<?, ?, ?> command) {
    if (!InstrumentationPoints.expectsResponse(command)) {
      return null;
    }
    LettucePeerAddress peer = COMMAND_PEER.get(command);
    return peer != null ? peer.getAddress() : null;
  }

  @Nullable
  static RedisServerTarget serverTarget(StatefulConnection<?, ?> connection) {
    return connection instanceof RedisChannelHandler
        ? CONNECTION_TARGET.get((RedisChannelHandler<?, ?>) connection)
        : null;
  }

  @Nullable
  static Integer databaseIndex(StatefulConnection<?, ?> connection) {
    return connection instanceof RedisChannelHandler
        ? CONNECTION_DATABASE_INDEX.get((RedisChannelHandler<?, ?>) connection)
        : null;
  }

  private LettuceSingletons() {}
}
