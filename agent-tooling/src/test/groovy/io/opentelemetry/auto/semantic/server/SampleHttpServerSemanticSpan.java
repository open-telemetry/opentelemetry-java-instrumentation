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
package io.opentelemetry.auto.semantic.server;

import io.opentelemetry.auto.semantic.server.http.HttpServerSemanticSpan;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;

public class SampleHttpServerSemanticSpan
    extends HttpServerSemanticSpan<SampleHttpServerSemanticSpan, String, String> {
  public SampleHttpServerSemanticSpan(Span delegate) {
    super(delegate);
  }

  @Override
  protected SampleHttpServerSemanticSpan onRequest(String o) {
    delegate.setAttribute("requested", true);
    return this;
  }

  @Override
  protected SampleHttpServerSemanticSpan onResponse(String o) {
    delegate.setStatus(Status.OK);
    return this;
  }

  @Override
  protected SampleHttpServerSemanticSpan self() {
    return this;
  }
}
