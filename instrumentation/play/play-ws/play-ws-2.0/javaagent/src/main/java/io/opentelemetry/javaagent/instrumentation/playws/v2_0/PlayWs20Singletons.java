/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws.v2_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.playws.common.v1_0.PlayWsClientInstrumenterFactory;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;

public class PlayWs20Singletons {

  private static final Instrumenter<Request, Response> instrumenter =
      PlayWsClientInstrumenterFactory.createInstrumenter("io.opentelemetry.play-ws-2.0");

  public static Instrumenter<Request, Response> instrumenter() {
    return instrumenter;
  }

  private PlayWs20Singletons() {}
}
