/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.struts;

import com.opensymphony.xwork2.ActionSupport;
import io.opentelemetry.instrumentation.test.base.HttpServerTest;

public class GreetingAction extends ActionSupport {

  String responseBody = "default";

  public String success() {
    responseBody =
        HttpServerTest.controller(
            HttpServerTest.ServerEndpoint.SUCCESS, HttpServerTest.ServerEndpoint.SUCCESS::getBody);

    return "greeting";
  }

  public String query() {
    responseBody =
        HttpServerTest.controller(
            HttpServerTest.ServerEndpoint.QUERY_PARAM,
            HttpServerTest.ServerEndpoint.QUERY_PARAM::getBody);
    return "greeting";
  }

  public String exception() {
    responseBody =
        HttpServerTest.controller(
            HttpServerTest.ServerEndpoint.EXCEPTION,
            () -> {
              throw new Exception(HttpServerTest.ServerEndpoint.EXCEPTION.getBody());
            });
    return "exception";
  }

  public String pathParam() {
    HttpServerTest.controller(
        HttpServerTest.ServerEndpoint.PATH_PARAM,
        () -> "this does nothing, as responseBody is set in setId, but we need this controller span nevertheless");
    return "greeting";
  }

  public void setId(String id) {
    responseBody = id;
  }

  public void setSome(String some) {
    responseBody = "some=" + some;
    System.out.println("Setting query param some to " + some);
  }

  public String getResponseBody() {
    return responseBody;
  }
}
