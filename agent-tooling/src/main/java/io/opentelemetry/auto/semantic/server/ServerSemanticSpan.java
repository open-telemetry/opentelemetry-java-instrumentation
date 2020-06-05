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

import io.opentelemetry.auto.semantic.base.BaseSemanticSpan;
import io.opentelemetry.trace.Span;

public abstract class ServerSemanticSpan<T extends ServerSemanticSpan, REQUEST, RESPONSE>
    extends BaseSemanticSpan<T> {

  public ServerSemanticSpan(Span delegate) {
    super(delegate);
  }

  protected abstract T onRequest(REQUEST request);

  protected abstract T onResponse(RESPONSE response);

  public void end(RESPONSE response) {
    onResponse(response).end();
  }
}
