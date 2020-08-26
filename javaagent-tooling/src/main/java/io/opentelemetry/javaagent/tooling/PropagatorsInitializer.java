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

package io.opentelemetry.javaagent.tooling;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.propagation.DefaultContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.extensions.trace.propagation.AwsXRayPropagator;
import io.opentelemetry.extensions.trace.propagation.B3Propagator;
import io.opentelemetry.extensions.trace.propagation.JaegerPropagator;
import io.opentelemetry.extensions.trace.propagation.OtTracerPropagator;
import io.opentelemetry.extensions.trace.propagation.TraceMultiPropagator;
import io.opentelemetry.extensions.trace.propagation.TraceMultiPropagator.Builder;
import io.opentelemetry.trace.propagation.HttpTraceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropagatorsInitializer {

  private static final Logger log = LoggerFactory.getLogger(PropagatorsInitializer.class);

  private static final String TRACE_CONTEXT = "tracecontext";
  private static final String B3 = "b3";
  private static final String B3_SINGLE = "b3single";
  private static final String JAEGER = "jaeger";
  private static final String OT_TRACER = "ottracer";
  private static final String XRAY = "xray";

  private static final Map<String, TextMapPropagator> TEXTMAP_PROPAGATORS =
      ImmutableMap.<String, TextMapPropagator>builder()
          .put(TRACE_CONTEXT, new HttpTraceContext())
          .put(B3, B3Propagator.getMultipleHeaderPropagator())
          .put(B3_SINGLE, B3Propagator.getSingleHeaderPropagator())
          .put(JAEGER, new JaegerPropagator())
          .put(OT_TRACER, OtTracerPropagator.getInstance())
          .put(XRAY, new AwsXRayPropagator())
          .build();

  /** Initialize OpenTelemetry global Propagators with propagator list, if any. */
  public static void initializePropagators(List<String> propagators) {
    /* Only override the default propagators *if* the user specified any. */
    if (propagators.size() == 0) {
      return;
    }

    DefaultContextPropagators.Builder propagatorsBuilder = DefaultContextPropagators.builder();

    List<TextMapPropagator> textPropagators = new ArrayList<>(propagators.size());
    for (String propagatorId : propagators) {
      TextMapPropagator textPropagator = TEXTMAP_PROPAGATORS.get(propagatorId.trim().toLowerCase());
      if (textPropagator != null) {
        textPropagators.add(textPropagator);
        log.info("Added " + textPropagator + " propagator");
      } else {
        log.warn("No matching propagator for " + propagatorId);
      }
    }
    if (textPropagators.size() > 1) {
      Builder traceMultiPropagatorBuilder = TraceMultiPropagator.builder();
      for (TextMapPropagator textPropagator : textPropagators) {
        traceMultiPropagatorBuilder.addPropagator(textPropagator);
      }
      propagatorsBuilder.addTextMapPropagator(traceMultiPropagatorBuilder.build());
    } else if (textPropagators.size() == 1) {
      propagatorsBuilder.addTextMapPropagator(textPropagators.get(0));
    }
    // Register it in the global propagators:
    OpenTelemetry.setPropagators(propagatorsBuilder.build());
  }
}
