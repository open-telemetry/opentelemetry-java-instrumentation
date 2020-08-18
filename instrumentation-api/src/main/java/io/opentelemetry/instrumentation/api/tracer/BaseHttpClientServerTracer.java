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

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.attributes.SemanticAttributes;
import java.net.URI;

public abstract class BaseHttpClientServerTracer extends BaseTracer {

  protected static final String USER_AGENT = "User-Agent";

  public BaseHttpClientServerTracer() {
    super();
  }

  public BaseHttpClientServerTracer(Tracer tracer) {
    super(tracer);
  }

  protected void tagUrl(URI url, Span span) {
    if (url != null) {
      StringBuilder urlBuilder = new StringBuilder();
      if (url.getScheme() != null) {
        urlBuilder.append(url.getScheme());
        urlBuilder.append("://");
      }
      if (url.getHost() != null) {
        urlBuilder.append(url.getHost());
        if (url.getPort() > 0 && url.getPort() != 80 && url.getPort() != 443) {
          urlBuilder.append(":");
          urlBuilder.append(url.getPort());
        }
      }
      String path = url.getPath();
      if (path.isEmpty()) {
        urlBuilder.append("/");
      } else {
        urlBuilder.append(path);
      }
      String query = url.getQuery();
      if (query != null) {
        urlBuilder.append("?").append(query);
      }
      String fragment = url.getFragment();
      if (fragment != null) {
        urlBuilder.append("#").append(fragment);
      }

      span.setAttribute(SemanticAttributes.HTTP_URL.key(), urlBuilder.toString());
    }
  }

  @Override
  protected abstract String getInstrumentationName();
}
