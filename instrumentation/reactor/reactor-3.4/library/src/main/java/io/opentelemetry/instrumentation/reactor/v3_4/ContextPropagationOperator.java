/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2013-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.reactor.v3_4;

import io.opentelemetry.context.Context;

public final class ContextPropagationOperator
    extends io.opentelemetry.instrumentation.reactor.v3.common.ContextPropagationOperator {

  public ContextPropagationOperator(boolean captureExperimentalSpanAttributes) {
    super(captureExperimentalSpanAttributes);
  }

  /**
   * Gets Trace {@link Context} from Reactor {@link reactor.util.context.ContextView}.
   *
   * @param contextView Reactor's context to get trace context from.
   * @param defaultTraceContext Default value to be returned if no trace context is found on Reactor
   *     context.
   * @return Trace context or default value.
   */
  public static Context getOpenTelemetryContextByContextView(
      reactor.util.context.ContextView contextView, Context defaultTraceContext) {
    return contextView.getOrDefault(TRACE_CONTEXT_KEY, defaultTraceContext);
  }

  public static ContextPropagationOperator create() {
    return builder().build();
  }

  public static ContextPropagationOperatorBuilder builder() {
    return new ContextPropagationOperatorBuilder();
  }
}
