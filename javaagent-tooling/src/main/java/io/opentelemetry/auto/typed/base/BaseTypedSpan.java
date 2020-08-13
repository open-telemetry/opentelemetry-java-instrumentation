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

package io.opentelemetry.auto.typed.base;

import io.opentelemetry.trace.EndSpanOptions;
import io.opentelemetry.trace.Span;

public abstract class BaseTypedSpan<T extends BaseTypedSpan> extends DelegatingSpan {

  public BaseTypedSpan(Span delegate) {
    super(delegate);
  }

  public void end(Throwable throwable) {
    // add error details to the span.
    super.end();
  }

  /** The end(Throwable), or end(RESPONSE) methods should be used instead. */
  @Deprecated
  @Override
  public void end() {
    super.end();
  }

  /** The end(Throwable), or end(RESPONSE) methods should be used instead. */
  @Deprecated
  @Override
  public void end(EndSpanOptions endOptions) {
    super.end(endOptions);
  }

  protected abstract T self();
}
