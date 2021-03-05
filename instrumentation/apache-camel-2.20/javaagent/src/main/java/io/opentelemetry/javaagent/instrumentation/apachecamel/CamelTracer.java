/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Apache Camel Opentracing Component
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.javaagent.instrumentation.apachecamel.decorators.DecoratorRegistry;
import org.apache.camel.Endpoint;
import org.apache.camel.util.StringHelper;

class CamelTracer extends BaseTracer {

  public static final CamelTracer TRACER = new CamelTracer();

  private final DecoratorRegistry registry = new DecoratorRegistry();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.apache-camel-2.20";
  }

  public SpanBuilder spanBuilder(String name) {
    return tracer.spanBuilder(name);
  }

  public Scope startClientScope(Span span) {
    Context current = super.withClientSpan(Context.current(), span);
    return current.makeCurrent();
  }

  public SpanDecorator getSpanDecorator(Endpoint endpoint) {

    String component = "";
    String uri = endpoint.getEndpointUri();
    String[] splitUri = StringHelper.splitOnCharacter(uri, ":", 2);
    if (splitUri[1] != null) {
      component = splitUri[0];
    }
    return registry.forComponent(component);
  }
}
