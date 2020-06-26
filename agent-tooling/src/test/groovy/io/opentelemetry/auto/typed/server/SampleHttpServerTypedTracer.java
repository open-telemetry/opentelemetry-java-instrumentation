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

package io.opentelemetry.auto.typed.server;

import io.opentelemetry.auto.typed.server.http.HttpServerTypedTracer;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;

public class SampleHttpServerTypedTracer
    extends HttpServerTypedTracer<SampleHttpServerTypedSpan, String, String> {
  @Override
  protected String getInstrumentationName() {
    return "test";
  }

  @Override
  protected String getVersion() {
    return "test";
  }

  @Override
  protected String getSpanName(String o) {
    return "test-span";
  }

  @Override
  protected SampleHttpServerTypedSpan wrapSpan(Span span) {
    return new SampleHttpServerTypedSpan(span);
  }

  @Override
  protected HttpTextFormat.Getter<String> getGetter() {
    return new HttpTextFormat.Getter<String>() {
      @Override
      public String get(String carrier, String key) {
        return null;
      }
    };
  }
}
