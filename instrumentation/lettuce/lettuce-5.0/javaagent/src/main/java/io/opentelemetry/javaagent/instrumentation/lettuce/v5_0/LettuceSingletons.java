/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.DecoratedCommand;
import io.lettuce.core.protocol.DefaultEndpoint;
import io.lettuce.core.protocol.RedisCommand;
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

  public static final VirtualField<DefaultEndpoint, InetSocketAddress> ENDPOINT_ADDRESS =
      VirtualField.find(DefaultEndpoint.class, InetSocketAddress.class);

  public static final VirtualField<RedisCommand<?, ?, ?>, InetSocketAddress> COMMAND_ADDRESS =
      VirtualField.find(RedisCommand.class, InetSocketAddress.class);

  private static final VirtualField<RedisCommand<?, ?, ?>, LettuceCommandPeer> COMMAND_PEER =
      VirtualField.find(RedisCommand.class, LettuceCommandPeer.class);
  private static final Object commandPeerLock = new Object();

  private static final VirtualField<RedisCommand<?, ?, ?>, Boolean> REACTIVE_COMMAND =
      VirtualField.find(RedisCommand.class, Boolean.class);

  public static final VirtualField<DefaultEndpoint, Integer> ENDPOINT_DATABASE_INDEX =
      VirtualField.find(DefaultEndpoint.class, Integer.class);

  public static final VirtualField<RedisCommand<?, ?, ?>, Integer> COMMAND_DATABASE_INDEX =
      VirtualField.find(RedisCommand.class, Integer.class);

  public static final VirtualField<DefaultEndpoint, RedisServerTarget> ENDPOINT_TARGET =
      VirtualField.find(DefaultEndpoint.class, RedisServerTarget.class);

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
        COMMAND_ADDRESS.set(command, ENDPOINT_ADDRESS.get(endpoint));
        COMMAND_DATABASE_INDEX.set(command, ENDPOINT_DATABASE_INDEX.get(endpoint));
        COMMAND_TARGET.set(command, ENDPOINT_TARGET.get(endpoint));
      }
    }
  }

  static void recordCommandPeer(RedisCommand<?, ?, ?> command, InetSocketAddress peerAddress) {
    commandPeer(command).record(peerAddress);
  }

  public static void linkCommandPeer(RedisCommand<?, ?, ?> command) {
    synchronized (commandPeerLock) {
      attachCommandPeer(command, findCommandPeer(command));
    }
  }

  private static LettuceCommandPeer commandPeer(RedisCommand<?, ?, ?> command) {
    LettuceCommandPeer peer = findCommandPeer(command);
    if (peer != null) {
      return peer;
    }
    synchronized (commandPeerLock) {
      return attachCommandPeer(command, findCommandPeer(command));
    }
  }

  @Nullable
  private static LettuceCommandPeer findCommandPeer(RedisCommand<?, ?, ?> command) {
    RedisCommand<?, ?, ?> current = command;
    while (current != null) {
      LettuceCommandPeer peer = COMMAND_PEER.get(current);
      if (peer != null) {
        return peer;
      }
      current =
          current instanceof DecoratedCommand
              ? ((DecoratedCommand<?, ?, ?>) current).getDelegate()
              : null;
    }
    return null;
  }

  private static LettuceCommandPeer attachCommandPeer(
      RedisCommand<?, ?, ?> command, @Nullable LettuceCommandPeer peer) {
    if (peer == null) {
      peer = new LettuceCommandPeer();
    }
    RedisCommand<?, ?, ?> current = command;
    while (current != null) {
      COMMAND_PEER.set(current, peer);
      current =
          current instanceof DecoratedCommand
              ? ((DecoratedCommand<?, ?, ?>) current).getDelegate()
              : null;
    }
    return peer;
  }

  @Nullable
  static InetSocketAddress commandPeerAddress(RedisCommand<?, ?, ?> command) {
    if (Boolean.TRUE.equals(REACTIVE_COMMAND.get(command)) || closesConnection(command)) {
      return null;
    }
    LettuceCommandPeer peer = findCommandPeer(command);
    return peer == null ? null : peer.getAddress();
  }

  private static boolean closesConnection(RedisCommand<?, ?, ?> command) {
    String commandName = LettuceInstrumentationUtil.getCommandName(command);
    if ("SHUTDOWN".equals(commandName)) {
      return true;
    }
    return "DEBUG".equals(commandName)
        && command.getArgs() != null
        && "SEGFAULT".equals(command.getArgs().toCommandString().trim());
  }

  public static void markReactiveCommand(RedisCommand<?, ?, ?> command) {
    REACTIVE_COMMAND.set(command, true);
  }

  private LettuceSingletons() {}
}
