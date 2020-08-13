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

package io.opentelemetry.auto.instrumentation.elasticsearch.rest.v5_0;

import static io.opentelemetry.auto.instrumentation.elasticsearch.rest.ElasticsearchRestClientDecorator.DECORATE;

import io.opentelemetry.trace.Span;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;

public class RestResponseListener implements ResponseListener {

  private final ResponseListener listener;
  private final Span span;

  public RestResponseListener(final ResponseListener listener, final Span span) {
    this.listener = listener;
    this.span = span;
  }

  @Override
  public void onSuccess(final Response response) {
    if (response.getHost() != null) {
      DECORATE.onResponse(span, response);
    }

    try {
      listener.onSuccess(response);
    } finally {
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void onFailure(final Exception e) {
    DECORATE.onError(span, e);

    try {
      listener.onFailure(e);
    } finally {
      DECORATE.beforeFinish(span);
      span.end();
    }
  }
}
