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

package io.opentelemetry.instrumentation.auto.kubernetesclient;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.instrumentation.auto.kubernetesclient.KubernetesClientTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import io.grpc.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;

public class TracingInterceptor implements Interceptor {

  @Override
  public Response intercept(Chain chain) throws IOException {

    KubernetesRequestDigest digest = KubernetesRequestDigest.parse(chain.request());

    Span span = TRACER.startSpan(digest);
    TRACER.onRequest(span, chain.request());

    Context context = withSpan(span, Context.current());

    Response response;
    try (Scope scope = withScopedContext(context)) {
      response = chain.proceed(chain.request());
    } catch (Exception e) {
      TRACER.endExceptionally(span, e);
      throw e;
    }

    TRACER.end(span, response);
    return response;
  }
}
