/*
 * Copyright 2020, OpenTelemetry Authors
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
package io.opentelemetry.auto.bootstrap.instrumentation.decorator;

import io.opentelemetry.auto.instrumentation.api.MoreTags;
import io.opentelemetry.trace.Span;

public abstract class ClientDecorator extends BaseDecorator {

  protected abstract String service();

  @Override
  public Span afterStart(final Span span) {
    assert span != null;
    span.setAttribute(MoreTags.SERVICE_NAME, service());
    return super.afterStart(span);
  }
}
