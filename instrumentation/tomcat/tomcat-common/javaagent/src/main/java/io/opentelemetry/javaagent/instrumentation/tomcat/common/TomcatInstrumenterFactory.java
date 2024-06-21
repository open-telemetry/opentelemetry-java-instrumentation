/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenterBuilder;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletErrorCauseExtractor;
import java.util.Optional;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

public final class TomcatInstrumenterFactory {

  private TomcatInstrumenterFactory() {}

  public static <REQUEST, RESPONSE> Instrumenter<Request, Response> create(
      String instrumentationName, ServletAccessor<REQUEST, RESPONSE> accessor) {
    return JavaagentHttpServerInstrumenterBuilder.createWithCustomizer(
        instrumentationName,
        new TomcatHttpAttributesGetter(),
        Optional.of(TomcatRequestGetter.INSTANCE),
        builder -> builder.setErrorCauseExtractor(new ServletErrorCauseExtractor<>(accessor))
            .addContextCustomizer(
                (context, request, attributes) ->
                    new AppServerBridge.Builder()
                        .captureServletAttributes()
                        .recordException()
                        .init(context))
    );
  }
}
