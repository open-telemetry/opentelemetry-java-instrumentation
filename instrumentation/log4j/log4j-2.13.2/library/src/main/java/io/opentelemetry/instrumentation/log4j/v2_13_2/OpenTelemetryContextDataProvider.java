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

package io.opentelemetry.instrumentation.log4j.v2_13_2;

import com.google.auto.service.AutoService;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.TracingContextUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.core.util.ContextDataProvider;

@AutoService(ContextDataProvider.class)
public class OpenTelemetryContextDataProvider implements ContextDataProvider {
  @Override
  public Map<String, String> supplyContextData() {
    Span currentSpan = TracingContextUtils.getCurrentSpan();
    if (currentSpan == null || !currentSpan.getContext().isValid()) {
      return Collections.emptyMap();
    }

    Map<String, String> contextData = new HashMap<>();
    SpanContext spanContext = currentSpan.getContext();
    contextData.put("traceId", spanContext.getTraceId().toLowerBase16());
    contextData.put("spanId", spanContext.getSpanId().toLowerBase16());
    return contextData;
  }
}
