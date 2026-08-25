/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpclient.common.v3_0;

public final class RequestInstrumentationState {

  public static final RequestInstrumentationState ATTEMPTED = new RequestInstrumentationState();

  private RequestInstrumentationState() {}
}
