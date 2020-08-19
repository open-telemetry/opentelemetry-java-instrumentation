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

package io.opentelemetry.instrumentation.auto.okhttp.v2_2;

import static io.opentelemetry.instrumentation.auto.okhttp.v2_2.OkHttpClientTracer.TRACER;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.io.IOException;

public class TracingInterceptor implements Interceptor {
  @Override
  public Response intercept(Chain chain) throws IOException {
    Span span = TRACER.startSpan(chain.request());
    Request.Builder requestBuilder = chain.request().newBuilder();

    Response response;
    try (Scope scope = TRACER.startScope(span, requestBuilder)) {
      response = chain.proceed(requestBuilder.build());
    } catch (Exception e) {
      TRACER.endExceptionally(span, e);
      throw e;
    }
    TRACER.end(span, response);
    return response;
  }
}
