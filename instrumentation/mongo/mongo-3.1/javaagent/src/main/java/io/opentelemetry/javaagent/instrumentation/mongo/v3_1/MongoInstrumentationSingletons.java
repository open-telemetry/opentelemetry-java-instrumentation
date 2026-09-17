/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_1;

import com.mongodb.connection.ConnectionDescription;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoInstrumenterFactory;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoNetworkPeer;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.TracingCommandListener;

public class MongoInstrumentationSingletons {

  private static final VirtualField<ConnectionDescription, MongoNetworkPeer> CONNECTION_PEER =
      VirtualField.find(ConnectionDescription.class, MongoNetworkPeer.class);

  private static final Instrumenter<CommandStartedEvent, Void> instrumenter =
      MongoInstrumenterFactory.createInstrumenter(
          GlobalOpenTelemetry.get(),
          "io.opentelemetry.mongo-3.1",
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "mongo"),
          CONNECTION_PEER::get);

  private static final CommandListener tracingListener = new TracingCommandListener(instrumenter);

  public static CommandListener tracingListener() {
    return tracingListener;
  }

  public static boolean isTracingListener(CommandListener commandListener) {
    return commandListener.getClass().getName().equals(tracingListener.getClass().getName());
  }

  private MongoInstrumentationSingletons() {}
}
