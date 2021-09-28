/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.tracer.HttpServerTracer;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LibertyDispatcherTracer
    extends HttpServerTracer<
        LibertyRequestWrapper, LibertyResponseWrapper, LibertyConnectionWrapper, Void> {
  private static final Logger logger = LoggerFactory.getLogger(LibertyDispatcherTracer.class);
  private static final LibertyDispatcherTracer TRACER = new LibertyDispatcherTracer();

  public static LibertyDispatcherTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.liberty-dispatcher";
  }

  @Override
  @Nullable
  protected Integer peerPort(LibertyConnectionWrapper libertyConnectionWrapper) {
    return libertyConnectionWrapper.peerPort();
  }

  @Override
  @Nullable
  protected String peerHostIp(LibertyConnectionWrapper libertyConnectionWrapper) {
    return libertyConnectionWrapper.peerHostIp();
  }

  @Override
  protected String flavor(
      LibertyConnectionWrapper libertyConnectionWrapper,
      LibertyRequestWrapper libertyRequestWrapper) {
    return libertyConnectionWrapper.getProtocol();
  }

  private static final TextMapGetter<LibertyRequestWrapper> GETTER =
      new TextMapGetter<LibertyRequestWrapper>() {

        @Override
        public Iterable<String> keys(LibertyRequestWrapper carrier) {
          return carrier.getAllHeaderNames();
        }

        @Override
        public String get(LibertyRequestWrapper carrier, String key) {
          return carrier.getHeaderValue(key);
        }
      };

  @Override
  protected TextMapGetter<LibertyRequestWrapper> getGetter() {
    return GETTER;
  }

  @Override
  protected String scheme(LibertyRequestWrapper libertyRequestWrapper) {
    return libertyRequestWrapper.getScheme();
  }

  @Override
  protected String host(LibertyRequestWrapper libertyRequestWrapper) {
    return libertyRequestWrapper.getServerName() + ":" + libertyRequestWrapper.getServerPort();
  }

  @Override
  protected String target(LibertyRequestWrapper libertyRequestWrapper) {
    String target = libertyRequestWrapper.getRequestUri();
    String queryString = libertyRequestWrapper.getQueryString();
    if (queryString != null) {
      target += "?" + queryString;
    }
    return target;
  }

  @Override
  protected String method(LibertyRequestWrapper libertyRequestWrapper) {
    return libertyRequestWrapper.getMethod();
  }

  @Override
  protected @Nullable String requestHeader(
      LibertyRequestWrapper libertyRequestWrapper, String name) {
    return libertyRequestWrapper.getHeaderValue(name);
  }

  @Override
  protected int responseStatus(LibertyResponseWrapper libertyResponseWrapper) {
    return libertyResponseWrapper.getStatus();
  }

  @Override
  @Nullable
  public Context getServerContext(Void none) {
    return null;
  }

  @Override
  protected void attachServerContext(Context context, Void none) {
    // This advice is only used when server didn't find matching application or got an internal
    // error. Nothing that is called within this advice should require access to the span.
  }
}
