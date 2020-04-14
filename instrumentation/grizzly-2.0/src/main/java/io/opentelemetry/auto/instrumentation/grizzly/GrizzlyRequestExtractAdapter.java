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
package io.opentelemetry.auto.instrumentation.grizzly;

import io.opentelemetry.context.propagation.HttpTextFormat;
import org.glassfish.grizzly.http.server.Request;

public class GrizzlyRequestExtractAdapter implements HttpTextFormat.Getter<Request> {

  public static final GrizzlyRequestExtractAdapter GETTER = new GrizzlyRequestExtractAdapter();

  @Override
  public String get(final Request carrier, final String key) {
    return carrier.getHeader(key);
  }
}
