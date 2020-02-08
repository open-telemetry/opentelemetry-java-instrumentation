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

package io.opentelemetry.helpers.core;

import java.util.concurrent.Callable;

/**
 * Defines the core API of tracing span decorators.
 *
 * @param <C> the context propagation carrier type
 * @param <Q> the request or input object type
 * @param <P> the response or output object type
 */
public interface SpanDecorator<C, Q, P> {

  /**
   * Initiates a new tracing span and populates with all known span data at the time of initiation.
   *
   * @param spanName the span name
   * @param carrier the propagation info carrier
   * @param inbound the request or input object
   * @return the closable wrapper
   */
  SpanScope<Q, P> startSpan(String spanName, C carrier, Q inbound);

  /**
   * Executes the provided callable with full telemetry collected about the call.
   *
   * @param spanName the span name
   * @param carrier the propagation info carrier
   * @param inbound the request or input object
   * @param callable
   * @return the response or output object
   * @throws Exception if any issues with the call
   */
  P callWithTelemetry(String spanName, C carrier, Q inbound, Callable<P> callable) throws Exception;
}
