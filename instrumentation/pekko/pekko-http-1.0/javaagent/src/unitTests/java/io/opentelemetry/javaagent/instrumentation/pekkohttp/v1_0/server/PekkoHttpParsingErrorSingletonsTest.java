/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.apache.pekko.http.scaladsl.model.ErrorInfo;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PekkoHttpParsingErrorSingletonsTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void markedRejectionIgnoresAmbientServerSpanAndStartsOnlyOnce() {
    HttpRequest request = (HttpRequest) org.apache.pekko.http.javadsl.model.HttpRequest.create();
    Context parentContext = PekkoHttpServerSingletons.instrumenter().start(Context.root(), request);
    ErrorInfo info = new ErrorInfo("parsing error", "invalid request");
    PekkoHttpParsingErrorSingletons.markParsingError(info);

    try (Scope ignored = parentContext.makeCurrent()) {
      Object[] enter = PekkoHttpParsingErrorSingletons.startSpan(info);
      assertThat(enter).isNotNull();
      assertThat(PekkoHttpParsingErrorSingletons.startSpan(info)).isNull();

      PekkoHttpParsingErrorSingletons.endSpan(
          enter, org.apache.pekko.http.javadsl.model.HttpResponse.create(), null);
      assertThat(Context.current()).isSameAs(parentContext);
    }

    PekkoHttpServerSingletons.instrumenter()
        .end(
            parentContext,
            request,
            (HttpResponse) org.apache.pekko.http.javadsl.model.HttpResponse.create(),
            null);

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasNoParent()),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasNoParent()));
  }
}
