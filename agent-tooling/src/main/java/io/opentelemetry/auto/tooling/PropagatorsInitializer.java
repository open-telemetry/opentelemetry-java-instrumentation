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

package io.opentelemetry.auto.tooling;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.propagation.DefaultContextPropagators;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.extensions.trace.propagation.B3Propagator;
import io.opentelemetry.extensions.trace.propagation.JaegerPropagator;
import io.opentelemetry.extensions.trace.propagation.OtTracerPropagator;
import io.opentelemetry.trace.propagation.HttpTraceContext;
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

  private static final Map<String, HttpTextFormat> TEXTMAP_PROPAGATORS =
      ImmutableMap.of(
          TRACE_CONTEXT,
          new HttpTraceContext(),
          B3,
          B3Propagator.getMultipleHeaderPropagator(),
          B3_SINGLE,
          B3Propagator.getSingleHeaderPropagator(),
          JAEGER,
          new JaegerPropagator(),
          OT_TRACER,
          OtTracerPropagator.getInstance());

  /** Initialize OpenTelemetry global Propagators with propagator list, if any. */
  public static void initializePropagators(List<String> propagators) {
    /* Only override the default propagators *if* the user specified any. */
    if (propagators.size() == 0) {
      return;
    }

    DefaultContextPropagators.Builder propagatorsBuilder = DefaultContextPropagators.builder();

    boolean addedPropagator = false;
    for (String propagatorId : propagators) {
      HttpTextFormat textPropagator = TEXTMAP_PROPAGATORS.get(propagatorId.trim().toLowerCase());
      if (textPropagator != null) {
        if (addedPropagator) {
          log.warn(
              "Only one propagator per concern can be added, " + textPropagator + " is ignored");
          continue;
        }
        propagatorsBuilder.addHttpTextFormat(textPropagator);
        log.info("Added " + textPropagator + " propagator");
        addedPropagator = true;
      } else {
        log.warn("No matching propagator for " + propagatorId);
      }
    }

    OpenTelemetry.setPropagators(propagatorsBuilder.build());
  }
}
