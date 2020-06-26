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

package io.opentelemetry.auto.bootstrap.spi;

import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.sdk.trace.TracerSdkProvider;
import io.opentelemetry.sdk.trace.config.TraceConfig;

/**
 * A service provider to allow programmatic customization of the tracing configuration. It will be
 * executed after the SDK has initialized a {@link TracerSdkProvider}. This means that not only can
 * the provided {@link TracerSdkProvider} be configured e.g. by calling {@link
 * TracerSdkProvider#updateActiveTraceConfig(TraceConfig)}, but static methods on {@link
 * io.opentelemetry.OpenTelemetry}, e.g., {@link
 * io.opentelemetry.OpenTelemetry#setPropagators(ContextPropagators)} can be used as well.
 *
 * <p>An implementation of {@link TracerCustomizer} can either be provided as part of an initializer
 * JAR, using the {@code ota.initializer.jar} property or can be included in the same JAR as the
 * agent in a redistribution.
 */
public interface TracerCustomizer {

  /** Callback executed after the initial {@link TracerSdkProvider} has been initialized. */
  void configure(TracerSdkProvider tracerSdkProvider);
}
