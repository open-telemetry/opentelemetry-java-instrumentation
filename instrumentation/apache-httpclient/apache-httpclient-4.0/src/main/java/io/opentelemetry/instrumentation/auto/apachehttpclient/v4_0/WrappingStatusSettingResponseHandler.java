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

package io.opentelemetry.auto.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.auto.instrumentation.apachehttpclient.v4_0.ApacheHttpClientTracer.TRACER;

import io.opentelemetry.trace.Span;
import java.io.IOException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;

public class WrappingStatusSettingResponseHandler implements ResponseHandler {
  final Span span;
  final ResponseHandler handler;

  public WrappingStatusSettingResponseHandler(final Span span, final ResponseHandler handler) {
    this.span = span;
    this.handler = handler;
  }

  @Override
  public Object handleResponse(final HttpResponse response)
      throws ClientProtocolException, IOException {
    if (null != span) {
      TRACER.onResponse(span, response);
    }
    return handler.handleResponse(response);
  }
}
