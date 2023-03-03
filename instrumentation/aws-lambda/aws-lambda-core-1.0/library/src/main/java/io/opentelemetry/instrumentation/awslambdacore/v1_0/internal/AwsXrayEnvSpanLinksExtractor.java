/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdacore.v1_0.internal;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.contrib.awsxray.propagator.AwsXrayPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.awslambdacore.v1_0.AwsLambdaRequest;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AwsXrayEnvSpanLinksExtractor implements SpanLinksExtractor<AwsLambdaRequest> {

  private static final String AWS_TRACE_HEADER_ENV_KEY = "_X_AMZN_TRACE_ID";

  // lower-case map getter used for extraction
  private static final String AWS_TRACE_HEADER_PROPAGATOR_KEY = "x-amzn-trace-id";

  private static final Attributes LINK_ATTRIBUTES =
      Attributes.of(AttributeKey.stringKey("source"), "x-ray-env");

  @Override
  public void extract(
      SpanLinksBuilder spanLinks,
      io.opentelemetry.context.Context parentContext,
      AwsLambdaRequest awsLambdaRequest) {
    extract(spanLinks);
  }

  public static void extract(SpanLinksBuilder spanLinks) {
    String parentTraceHeader = System.getenv(AWS_TRACE_HEADER_ENV_KEY);
    if (parentTraceHeader == null || parentTraceHeader.isEmpty()) {
      return;
    }
    Context xrayContext =
        AwsXrayPropagator.getInstance()
            .extract(
                // see BaseTracer#extract() on why we're using root() here
                Context.root(),
                Collections.singletonMap(AWS_TRACE_HEADER_PROPAGATOR_KEY, parentTraceHeader),
                MapGetter.INSTANCE);
    SpanContext envVarSpanCtx = Span.fromContext(xrayContext).getSpanContext();
    if (envVarSpanCtx.isValid()) {
      spanLinks.addLink(envVarSpanCtx, LINK_ATTRIBUTES);
    }
  }

  private enum MapGetter implements TextMapGetter<Map<String, String>> {
    INSTANCE;

    @Override
    public Iterable<String> keys(Map<String, String> map) {
      return map.keySet();
    }

    @Override
    public String get(Map<String, String> map, String s) {
      return map.get(s.toLowerCase(Locale.ROOT));
    }
  }
}
