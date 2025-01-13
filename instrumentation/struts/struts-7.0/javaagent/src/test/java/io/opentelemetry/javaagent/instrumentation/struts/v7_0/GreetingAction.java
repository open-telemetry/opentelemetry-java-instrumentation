/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.struts.v7_0;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest.controller;

import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.apache.struts2.interceptor.parameter.StrutsParameter;

public class GreetingAction extends ActionSupport {

  String responseBody = "default";

  public String success() {
    responseBody = controller(ServerEndpoint.SUCCESS, ServerEndpoint.SUCCESS::getBody);

    return "greeting";
  }

  public String redirect() {
    responseBody = controller(ServerEndpoint.REDIRECT, ServerEndpoint.REDIRECT::getBody);
    return "redirect";
  }

  public String query_param() {
    responseBody = controller(ServerEndpoint.QUERY_PARAM, ServerEndpoint.QUERY_PARAM::getBody);
    return "greeting";
  }

  public String error() {
    controller(ServerEndpoint.ERROR, ServerEndpoint.ERROR::getBody);
    return "error";
  }

  public String exception() {
    controller(
        ServerEndpoint.EXCEPTION,
        () -> {
          throw new IllegalStateException(ServerEndpoint.EXCEPTION.getBody());
        });
    throw new AssertionError(); // should not reach here
  }

  public String path_param() {
    controller(
        ServerEndpoint.PATH_PARAM,
        () ->
            "this does nothing, as responseBody is set in setId, but we need this controller span nevertheless");
    return "greeting";
  }

  public String indexed_child() {
    responseBody =
        controller(
            ServerEndpoint.INDEXED_CHILD,
            () -> {
              ServerEndpoint.INDEXED_CHILD.collectSpanAttributes(
                  (name) -> ServletActionContext.getRequest().getParameter(name));
              return ServerEndpoint.INDEXED_CHILD.getBody();
            });
    return "greeting";
  }

  public String capture_headers() {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();
    response.setHeader("X-Test-Response", request.getHeader("X-Test-Request"));
    responseBody =
        controller(ServerEndpoint.CAPTURE_HEADERS, ServerEndpoint.CAPTURE_HEADERS::getBody);
    return "greeting";
  }

  public String dispatch_servlet() {
    return "greetingServlet";
  }

  @StrutsParameter
  public void setId(String id) {
    responseBody = id;
  }

  public String getResponseBody() {
    return responseBody;
  }
}
