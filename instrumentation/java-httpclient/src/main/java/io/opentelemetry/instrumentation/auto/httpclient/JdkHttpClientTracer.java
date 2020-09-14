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

package io.opentelemetry.instrumentation.auto.httpclient;

import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap;
import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap.Depth;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

public class JdkHttpClientTracer extends HttpClientTracer<HttpRequest, HttpRequest, HttpResponse> {
  public static final JdkHttpClientTracer TRACER = new JdkHttpClientTracer();

  public Depth getCallDepth() {
    return CallDepthThreadLocalMap.getCallDepth(HttpClient.class);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.java-httpclient";
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.method();
  }

  @Override
  protected URI url(HttpRequest httpRequest) {
    return httpRequest.uri();
  }

  @Override
  protected Integer status(HttpResponse httpResponse) {
    return httpResponse.statusCode();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().firstValue(name).orElse(null);
  }

  @Override
  protected String responseHeader(HttpResponse httpResponse, String name) {
    return httpResponse.headers().firstValue(name).orElse(null);
  }

  @Override
  protected Setter<HttpRequest> getSetter() {
    return HttpHeadersInjectAdapter.SETTER;
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    if (throwable instanceof CompletionException) {
      return throwable.getCause();
    }
    return super.unwrapThrowable(throwable);
  }

  public HttpHeaders inject(HttpHeaders original) {
    Map<String, List<String>> headerMap = new HashMap<>();

    OpenTelemetry.getPropagators()
        .getTextMapPropagator()
        .inject(
            Context.current(),
            headerMap,
            (carrier, key, value) -> carrier.put(key, Collections.singletonList(value)));
    headerMap.putAll(original.map());

    return HttpHeaders.of(headerMap, (s, s2) -> true);
  }
}
