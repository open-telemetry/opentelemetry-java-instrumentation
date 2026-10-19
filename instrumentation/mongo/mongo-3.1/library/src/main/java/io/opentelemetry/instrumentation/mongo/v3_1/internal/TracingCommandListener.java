/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.mongo.v3_1.internal;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class TracingCommandListener implements CommandListener {

  private final Instrumenter<CommandStartedEvent, Integer> instrumenter;
  private final Map<Integer, ContextAndRequest> requestMap;
  @Nullable private final MongoServerTarget configuredTarget;

  public TracingCommandListener(Instrumenter<CommandStartedEvent, Integer> instrumenter) {
    this(instrumenter, null);
  }

  public TracingCommandListener(
      Instrumenter<CommandStartedEvent, Integer> instrumenter,
      @Nullable MongoServerTarget configuredTarget) {
    this.instrumenter = instrumenter;
    this.requestMap = new ConcurrentHashMap<>();
    this.configuredTarget = configuredTarget;
  }

  @Override
  public void commandStarted(CommandStartedEvent event) {
    MongoClusterTargets.register(event, configuredTarget);
    Context parentContext = Context.current();
    if (instrumenter.shouldStart(parentContext, event)) {
      Context context = instrumenter.start(parentContext, event);
      requestMap.put(event.getRequestId(), ContextAndRequest.create(context, event));
    }
  }

  @Override
  public void commandSucceeded(CommandSucceededEvent event) {
    ContextAndRequest contextAndRequest = requestMap.remove(event.getRequestId());
    if (contextAndRequest != null) {
      instrumenter.end(
          contextAndRequest.getContext(),
          contextAndRequest.getRequest(),
          writeErrorCode(event.getResponse()),
          null);
    }
  }

  @Nullable
  private static Integer writeErrorCode(BsonDocument response) {
    BsonValue writeErrors = response.get("writeErrors");
    if (writeErrors != null && writeErrors.isArray()) {
      for (BsonValue writeError : writeErrors.asArray()) {
        Integer code = errorCode(writeError);
        if (code != null) {
          return code;
        }
      }
    }
    return errorCode(response.get("writeConcernError"));
  }

  @Nullable
  private static Integer errorCode(@Nullable BsonValue error) {
    if (error == null || !error.isDocument()) {
      return null;
    }
    BsonValue code = error.asDocument().get("code");
    if (code == null || !(code.isInt32() || code.isInt64())) {
      return null;
    }
    long value = code.asNumber().longValue();
    return value > 0 && value <= Integer.MAX_VALUE ? (int) value : null;
  }

  @Override
  public void commandFailed(CommandFailedEvent event) {
    ContextAndRequest contextAndRequest = requestMap.remove(event.getRequestId());
    if (contextAndRequest != null) {
      instrumenter.end(
          contextAndRequest.getContext(),
          contextAndRequest.getRequest(),
          null,
          event.getThrowable());
    }
  }
}
