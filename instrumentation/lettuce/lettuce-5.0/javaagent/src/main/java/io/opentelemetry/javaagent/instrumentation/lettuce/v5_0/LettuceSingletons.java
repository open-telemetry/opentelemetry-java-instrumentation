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
import io.lettuce.core.masterslave.StatefulRedisMasterSlaveConnection;
import io.lettuce.core.protocol.AsyncCommand;
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

  public static final VirtualField<RedisChannelHandler<?, ?>, InetSocketAddress>
      CONNECTION_ADDRESS = VirtualField.find(RedisChannelHandler.class, InetSocketAddress.class);

  public static final VirtualField<RedisCommand<?, ?, ?>, InetSocketAddress> COMMAND_ADDRESS =
      VirtualField.find(RedisCommand.class, InetSocketAddress.class);

  public static final VirtualField<DefaultEndpoint, Integer> ENDPOINT_DATABASE_INDEX =
      VirtualField.find(DefaultEndpoint.class, Integer.class);

  public static final VirtualField<RedisChannelHandler<?, ?>, Integer> CONNECTION_DATABASE_INDEX =
      VirtualField.find(RedisChannelHandler.class, Integer.class);

  public static final VirtualField<RedisCommand<?, ?, ?>, Integer> COMMAND_DATABASE_INDEX =
      VirtualField.find(RedisCommand.class, Integer.class);

  public static final VirtualField<DefaultEndpoint, RedisServerTarget> ENDPOINT_TARGET =
      VirtualField.find(DefaultEndpoint.class, RedisServerTarget.class);

  public static final VirtualField<RedisChannelHandler<?, ?>, RedisServerTarget> CONNECTION_TARGET =
      VirtualField.find(RedisChannelHandler.class, RedisServerTarget.class);

  public static final VirtualField<RedisCommand<?, ?, ?>, RedisServerTarget> COMMAND_TARGET =
      VirtualField.find(RedisCommand.class, RedisServerTarget.class);

  public static final VirtualField<RedisClusterClient, RedisServerTarget> CLUSTER_CLIENT_TARGET =
      VirtualField.find(RedisClusterClient.class, RedisServerTarget.class);

  public static final VirtualField<
          StatefulRedisMasterSlaveConnection<?, ?>, RedisChannelHandler<?, ?>>
      MASTER_SLAVE_CONNECTION_DELEGATE =
          VirtualField.find(StatefulRedisMasterSlaveConnection.class, RedisChannelHandler.class);

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
    if (!(connection instanceof RedisChannelHandler)) {
      return;
    }

    RedisChannelHandler<?, ?> connectionHandler = (RedisChannelHandler<?, ?>) connection;
    RedisServerTarget commandTarget = COMMAND_TARGET.get(command);
    if (commandTarget == null) {
      commandTarget = CONNECTION_TARGET.get(connectionHandler);
    }

    // Reactive commands previously copied endpoint metadata only when
    // RedisChannelHandler.getChannelWriter() returned a DefaultEndpoint directly. That works with
    // default ClientOptions through Lettuce 6.4. CommandExpiryWriter can also appear in Lettuce
    // 5.1-6.4 when command timeouts are explicitly enabled.
    //
    // Starting with Lettuce 6.5, command timeouts are enabled by default, so getChannelWriter()
    // returns CommandExpiryWriter instead of its DefaultEndpoint delegate. Lettuce 7 can return
    // MaintenanceAwareExpiryWriter, and CommandListenerWriter can add another outer wrapper.
    // Although these writers eventually delegate write() to DefaultEndpoint, the old
    // "channelWriter instanceof DefaultEndpoint" check is performed against the outer writer
    // object and therefore evaluates to false.
    //
    // As a result, the old branch was skipped and COMMAND_ADDRESS and COMMAND_DATABASE_INDEX
    // remained null. Reactive spans are started later by Reactor doOnSubscribe, where the
    // attributes getter reads these command fields, so the spans lacked server.address,
    // server.port, db.namespace, and the endpoint suffix in the span name.
    //
    // LettuceClientInstrumentation now stores the RedisURI metadata directly on the
    // RedisChannelHandler while the connection and original DefaultEndpoint are both available.
    // Reading CONNECTION_* here avoids depending on the concrete channel-writer wrapper chain.
    COMMAND_ADDRESS.set(command, CONNECTION_ADDRESS.get(connectionHandler));
    COMMAND_DATABASE_INDEX.set(command, CONNECTION_DATABASE_INDEX.get(connectionHandler));
    COMMAND_TARGET.set(command, commandTarget);
  }

  private LettuceSingletons() {}
}
