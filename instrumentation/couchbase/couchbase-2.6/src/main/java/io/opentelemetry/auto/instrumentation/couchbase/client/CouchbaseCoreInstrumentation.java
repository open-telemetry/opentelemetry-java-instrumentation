/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.instrumentation.couchbase.client;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.couchbase.client.core.message.CouchbaseRequest;
import com.google.auto.service.AutoService;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class CouchbaseCoreInstrumentation extends Instrumenter.Default {

  public CouchbaseCoreInstrumentation() {
    super("couchbase");
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    return named("com.couchbase.client.core.CouchbaseCore");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("com.couchbase.client.core.message.CouchbaseRequest", Span.class.getName());
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {getClass().getName() + "$CouchbaseCoreAdvice"};
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
    public static final Tracer TRACER =
        OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.couchbase-2.6");

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void addOperationIdToSpan(@Advice.Argument(0) final CouchbaseRequest request) {

      final Span parentSpan = TRACER.getCurrentSpan();
      if (parentSpan != null) {
        // The scope from the initial rxJava subscribe is not available to the networking layer
        // To transfer the span, the span is added to the context store

        final ContextStore<CouchbaseRequest, Span> contextStore =
            InstrumentationContext.get(CouchbaseRequest.class, Span.class);

        Span span = contextStore.get(request);

        if (span == null) {
          span = parentSpan;
          contextStore.put(request, span);

          span.setAttribute("couchbase.operation_id", request.operationId());
        }
      }
    }
  }
}
