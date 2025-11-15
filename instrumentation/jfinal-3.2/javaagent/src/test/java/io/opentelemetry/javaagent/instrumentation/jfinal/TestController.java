/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jfinal;

import static io.opentelemetry.instrumentation.testing.GlobalTraceUtil.runWithSpan;
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;

import com.jfinal.core.ActionKey;
import com.jfinal.core.Controller;
import com.jfinal.render.TextRender;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;

public class TestController extends Controller {

  public void success() {
    runWithSpan("controller", () -> renderText(ServerEndpoint.SUCCESS.getBody()));
  }

  public void redirect() {
    runWithSpan("controller", () -> redirect(ServerEndpoint.REDIRECT.getBody()));
  }

  @ActionKey("error-status")
  public void error() throws Exception {
    runWithSpan(
        "controller", () -> renderError(500, new TextRender(ServerEndpoint.ERROR.getBody())));
  }

  public void exception() throws Throwable {
    try {
      runWithSpan(
          "controller",
          () -> {
            throw new IllegalStateException(EXCEPTION.getBody());
          });
    } catch (Throwable t) {
      Span.current().recordException(t);
      throw t;
    }
  }

  public void captureHeaders() {
    runWithSpan(
        "controller",
        () -> {
          String header = getHeader("X-Test-Request");
          getResponse().setHeader("X-Test-Response", header);
          renderText(ServerEndpoint.CAPTURE_HEADERS.getBody());
        });
  }

  public void captureParameters() {
    runWithSpan(
        "controller",
        () -> {
          renderText(ServerEndpoint.CAPTURE_PARAMETERS.getBody());
        });
  }

  public void query() {

    runWithSpan(
        "controller",
        () -> {
          renderText(ServerEndpoint.QUERY_PARAM.getBody());
        });
  }

  @ActionKey("path/123/param")
  public void pathVar() {
    runWithSpan(
        "controller",
        () -> {
          renderText(ServerEndpoint.PATH_PARAM.getBody());
        });
  }

  public void authRequired() {
    runWithSpan(
        "controller",
        () -> {
          renderText(ServerEndpoint.AUTH_REQUIRED.getBody());
        });
  }

  public void login() {
    runWithSpan(
        "controller",
        () -> {
          redirect(ServerEndpoint.LOGIN.getBody());
        });
  }

  @ActionKey("basicsecured/endpoint(")
  public void basicsecured_endpoint() {
    renderText(ServerEndpoint.AUTH_ERROR.getBody());
  }

  public void child() {
    runWithSpan(
        "controller",
        () -> {
          ServerEndpoint.INDEXED_CHILD.collectSpanAttributes(
              name -> {
                return getPara(name);
              });
          renderText(ServerEndpoint.INDEXED_CHILD.getBody());
        });
  }
}
