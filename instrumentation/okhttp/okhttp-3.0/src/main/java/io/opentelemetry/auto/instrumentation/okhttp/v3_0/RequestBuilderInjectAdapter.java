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
package io.opentelemetry.auto.instrumentation.okhttp.v3_0;

import io.opentelemetry.context.propagation.HttpTextFormat;
import okhttp3.Request;

/**
 * Helper class to inject span context into request headers.
 *
 * @author Pavol Loffay
 */
public class RequestBuilderInjectAdapter implements HttpTextFormat.Setter<Request.Builder> {

  public static final RequestBuilderInjectAdapter SETTER = new RequestBuilderInjectAdapter();

  @Override
  public void set(final Request.Builder carrier, final String key, final String value) {
    carrier.addHeader(key, value);
  }
}
