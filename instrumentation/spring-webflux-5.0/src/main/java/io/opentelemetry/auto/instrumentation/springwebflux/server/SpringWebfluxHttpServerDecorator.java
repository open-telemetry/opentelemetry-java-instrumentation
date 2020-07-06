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

package io.opentelemetry.auto.instrumentation.springwebflux.server;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.ServerDecorator;
import io.opentelemetry.trace.Tracer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SpringWebfluxHttpServerDecorator extends ServerDecorator {
  public static final SpringWebfluxHttpServerDecorator DECORATE =
      new SpringWebfluxHttpServerDecorator();
  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.spring-webflux-5.0");
}
