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

package io.opentelemetry.auto.instrumentation.jetty;

import io.opentelemetry.auto.instrumentation.servlet.v3_0.CountingHttpServletResponse;
import io.opentelemetry.auto.instrumentation.servlet.v3_0.Servlet3HttpServerTracer;
import io.opentelemetry.trace.Span;
import javax.servlet.ServletResponse;

public class JettyHttpServerTracer extends Servlet3HttpServerTracer {
  public static final JettyHttpServerTracer TRACER = new JettyHttpServerTracer();

  public static void contentLengthHelper(Span span, ServletResponse response) {
    if (response instanceof CountingHttpServletResponse) {
      TRACER.setContentLength(span, ((CountingHttpServletResponse) response).getContentLength());
    }
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.jetty-8.0";
  }
}
