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

package io.opentelemetry.instrumentation.auto.elasticsearch.rest.v5_0;

import static io.opentelemetry.instrumentation.auto.elasticsearch.rest.ElasticsearchRestClientTracer.TRACER;

import io.opentelemetry.trace.Span;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

public class RestResponseListener implements ResponseListener {

  private final ResponseListener listener;
  private final Span span;

  public RestResponseListener(ResponseListener listener, Span span) {
    this.listener = listener;
    this.span = span;
  }

  @Override
  public void onSuccess(Response response) {
    if (response.getHost() != null) {
      TRACER.onResponse(span, response);
    }

    try {
      listener.onSuccess(response);
    } finally {
      TRACER.end(span);
    }
  }

  @Override
  public void onFailure(Exception e) {
    try {
      listener.onFailure(e);
    } finally {
      TRACER.endExceptionally(span, e);
    }
  }
}
