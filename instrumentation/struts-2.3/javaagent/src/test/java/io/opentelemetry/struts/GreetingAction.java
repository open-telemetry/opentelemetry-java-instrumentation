/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.struts;

import com.opensymphony.xwork2.ActionSupport;
import io.opentelemetry.instrumentation.test.base.HttpServerTest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts2.ServletActionContext;

public class GreetingAction extends ActionSupport {

  String responseBody = "default";

  public String success() {
    responseBody =
        HttpServerTest.controller(
            HttpServerTest.ServerEndpoint.SUCCESS, HttpServerTest.ServerEndpoint.SUCCESS::getBody);

    return "greeting";
  }

  public String redirect() {
    responseBody =
        HttpServerTest.controller(
            HttpServerTest.ServerEndpoint.REDIRECT,
            HttpServerTest.ServerEndpoint.REDIRECT::getBody);
    return "redirect";
  }

  public String query_param() {
    responseBody =
        HttpServerTest.controller(
            HttpServerTest.ServerEndpoint.QUERY_PARAM,
            HttpServerTest.ServerEndpoint.QUERY_PARAM::getBody);
    return "greeting";
  }

  public String error() {
    HttpServerTest.controller(
        HttpServerTest.ServerEndpoint.ERROR, HttpServerTest.ServerEndpoint.ERROR::getBody);
    return "error";
  }

  public String exception() {
    HttpServerTest.controller(
        HttpServerTest.ServerEndpoint.EXCEPTION,
        () -> {
          throw new Exception(HttpServerTest.ServerEndpoint.EXCEPTION.getBody());
        });
    throw new AssertionError(); // should not reach here
  }

  public String path_param() {
    HttpServerTest.controller(
        HttpServerTest.ServerEndpoint.PATH_PARAM,
        () ->
            "this does nothing, as responseBody is set in setId, but we need this controller span nevertheless");
    return "greeting";
  }

  public String indexed_child() {
    responseBody =
        HttpServerTest.controller(
            HttpServerTest.ServerEndpoint.INDEXED_CHILD,
            () -> {
              HttpServerTest.ServerEndpoint.INDEXED_CHILD.collectSpanAttributes(
                  (name) -> ServletActionContext.getRequest().getParameter(name));
              return HttpServerTest.ServerEndpoint.INDEXED_CHILD.getBody();
            });
    return "greeting";
  }

  public String capture_headers() {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();
    response.setHeader("X-Test-Response", request.getHeader("X-Test-Request"));
    responseBody =
        HttpServerTest.controller(
            HttpServerTest.ServerEndpoint.CAPTURE_HEADERS,
            HttpServerTest.ServerEndpoint.CAPTURE_HEADERS::getBody);
    return "greeting";
  }

  public String dispatch_servlet() {
    return "greetingServlet";
  }

  public void setId(String id) {
    responseBody = id;
  }

  public String getResponseBody() {
    return responseBody;
  }
}
