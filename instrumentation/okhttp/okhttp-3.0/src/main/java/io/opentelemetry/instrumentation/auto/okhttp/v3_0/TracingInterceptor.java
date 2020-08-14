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

package io.opentelemetry.instrumentation.auto.okhttp.v3_0;

import static io.opentelemetry.instrumentation.auto.okhttp.v3_0.OkHttpClientTracer.TRACER;
import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class TracingInterceptor implements Interceptor {

  @Override
  public Response intercept(final Chain chain) throws IOException {
    Span span = TRACER.startSpan(chain.request());
    Context context = withSpan(span, Context.current());

    Request.Builder requestBuilder = chain.request().newBuilder();
    OpenTelemetry.getPropagators()
        .getHttpTextFormat()
        .inject(context, requestBuilder, RequestBuilderInjectAdapter.SETTER);

    Response response;
    try (Scope scope = withScopedContext(context)) {
      response = chain.proceed(requestBuilder.build());
    } catch (final Exception e) {
      TRACER.endExceptionally(span, e);
      throw e;
    }
    TRACER.end(span, response);
    return response;
  }
}
