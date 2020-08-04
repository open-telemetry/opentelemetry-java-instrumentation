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

package io.opentelemetry.auto.instrumentation.okhttp.v2_2;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpClientTracer;
import java.net.URI;
import java.net.URISyntaxException;

public class OkHttpClientTracer extends HttpClientTracer<Request, Response> {
  public static final OkHttpClientTracer TRACER = new OkHttpClientTracer();

  @Override
  protected String method(final Request request) {
    return request.method();
  }

  @Override
  protected URI url(final Request request) throws URISyntaxException {
    return request.url().toURI();
  }

  @Override
  protected Integer status(final Response response) {
    return response.code();
  }

  @Override
  protected String requestHeader(Request request, String name) {
    return request.header(name);
  }

  @Override
  protected String responseHeader(Response response, String name) {
    return response.header(name);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.okhttp-2.2";
  }
}
