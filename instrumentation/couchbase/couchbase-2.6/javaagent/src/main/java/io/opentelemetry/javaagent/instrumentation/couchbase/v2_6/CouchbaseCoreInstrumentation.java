/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.v2_6;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.couchbase.client.core.message.CouchbaseRequest;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class CouchbaseCoreInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("com.couchbase.client.core.CouchbaseCore");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(takesArgument(0, named("com.couchbase.client.core.message.CouchbaseRequest")))
            .and(named("send")),
        CouchbaseCoreInstrumentation.class.getName() + "$CouchbaseCoreAdvice");
  }

  public static class CouchbaseCoreAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addOperationIdToSpan(@Advice.Argument(0) CouchbaseRequest request) {

      Span parentSpan = Java8BytecodeBridge.currentSpan();
      if (parentSpan != null) {
        // The scope from the initial rxJava subscribe is not available to the networking layer
        // To transfer the span, the span is added to the context store

        ContextStore<CouchbaseRequest, Span> contextStore =
            InstrumentationContext.get(CouchbaseRequest.class, Span.class);

        Span span = contextStore.get(request);

        if (span == null) {
          span = parentSpan;
          contextStore.put(request, span);
          if (CouchbaseConfig.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
            span.setAttribute("couchbase.operation_id", request.operationId());
          }
        }
      }
    }
  }
}
