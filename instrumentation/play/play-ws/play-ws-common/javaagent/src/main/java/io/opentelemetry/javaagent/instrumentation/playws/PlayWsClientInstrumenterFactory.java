/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenters;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;

public final class PlayWsClientInstrumenterFactory {

  public static Instrumenter<Request, Response> createInstrumenter(String instrumentationName) {
    return JavaagentHttpClientInstrumenters.create(
        instrumentationName, new PlayWsClientHttpAttributesGetter(), HttpHeaderSetter.INSTANCE);
  }

  private PlayWsClientInstrumenterFactory() {}
}
