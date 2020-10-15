/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachecamel;
/*
 * Includes work from:
 * Copyright Apache Camel Authors
 * SPDX-License-Identifier: Apache-2.0
 */
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.javaagent.instrumentation.apachecamel.decorators.DecoratorRegistry;
import io.opentelemetry.trace.Span;
import org.apache.camel.Endpoint;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CamelTracer extends BaseTracer {

  public static final CamelTracer TRACER = new CamelTracer();
  public static final Logger LOG = LoggerFactory.getLogger(CamelTracer.class);

  private final DecoratorRegistry registry = new DecoratorRegistry();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.apache-camel-2.20";
  }

  public Span.Builder spanBuilder(String name) {
    return tracer.spanBuilder(name);
  }

  public SpanDecorator getSpanDecorator(Endpoint endpoint) {

    String component = "";
    String uri = endpoint.getEndpointUri();
    String splitURI[] = StringHelper.splitOnCharacter(uri, ":", 2);
    if (splitURI[1] != null) {
      component = splitURI[0];
    }
    return registry.forComponent(component);
  }
}
