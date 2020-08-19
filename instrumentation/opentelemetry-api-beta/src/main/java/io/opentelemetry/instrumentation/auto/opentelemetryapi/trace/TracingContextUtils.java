/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.opentelemetryapi.trace;

import static io.opentelemetry.instrumentation.auto.opentelemetryapi.trace.Bridging.toApplication;

import application.io.grpc.Context;
import application.io.opentelemetry.context.Scope;
import application.io.opentelemetry.trace.DefaultSpan;
import application.io.opentelemetry.trace.Span;
import io.opentelemetry.instrumentation.auto.api.ContextStore;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.context.ApplicationScope;
import io.opentelemetry.instrumentation.auto.opentelemetryapi.context.NoopScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracingContextUtils {

  private static final Logger log = LoggerFactory.getLogger(TracingContextUtils.class);

  public static Context withSpan(
      Span applicationSpan,
      Context applicationContext,
      ContextStore<Context, io.grpc.Context> contextStore) {
    io.opentelemetry.trace.Span agentSpan = Bridging.toAgentOrNull(applicationSpan);
    if (agentSpan == null) {
      if (log.isDebugEnabled()) {
        log.debug("unexpected span: {}", applicationSpan, new Exception("unexpected span"));
      }
      return applicationContext;
    }
    io.grpc.Context agentContext = contextStore.get(applicationContext);
    if (agentContext == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "unexpected context: {}", applicationContext, new Exception("unexpected context"));
      }
      return applicationContext;
    }
    io.grpc.Context agentUpdatedContext =
        io.opentelemetry.trace.TracingContextUtils.withSpan(agentSpan, agentContext);
    Context applicationUpdatedContext = applicationContext.fork();
    contextStore.put(applicationUpdatedContext, agentUpdatedContext);
    return applicationUpdatedContext;
  }

  public static Span getCurrentSpan() {
    return toApplication(io.opentelemetry.trace.TracingContextUtils.getCurrentSpan());
  }

  public static Span getSpan(
      Context applicationContext, ContextStore<Context, io.grpc.Context> contextStore) {
    io.grpc.Context agentContext = contextStore.get(applicationContext);
    if (agentContext == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "unexpected context: {}", applicationContext, new Exception("unexpected context"));
      }
      return DefaultSpan.getInvalid();
    }
    return toApplication(io.opentelemetry.trace.TracingContextUtils.getSpan(agentContext));
  }

  public static Span getSpanWithoutDefault(
      Context applicationContext, ContextStore<Context, io.grpc.Context> contextStore) {
    io.grpc.Context agentContext = contextStore.get(applicationContext);
    if (agentContext == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "unexpected context: {}", applicationContext, new Exception("unexpected context"));
      }
      return null;
    }
    io.opentelemetry.trace.Span agentSpan =
        io.opentelemetry.trace.TracingContextUtils.getSpanWithoutDefault(agentContext);
    return agentSpan == null ? null : toApplication(agentSpan);
  }

  public static Scope currentContextWith(Span applicationSpan) {
    if (!applicationSpan.getContext().isValid()) {
      // this supports direct usage of DefaultSpan.getInvalid()
      return new ApplicationScope(
          io.opentelemetry.trace.TracingContextUtils.currentContextWith(
              io.opentelemetry.trace.DefaultSpan.getInvalid()));
    }
    if (applicationSpan instanceof ApplicationSpan) {
      return new ApplicationScope(
          io.opentelemetry.trace.TracingContextUtils.currentContextWith(
              ((ApplicationSpan) applicationSpan).getAgentSpan()));
    }
    if (log.isDebugEnabled()) {
      log.debug("unexpected span: {}", applicationSpan, new Exception("unexpected span"));
    }
    return NoopScope.getInstance();
  }
}
