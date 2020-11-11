/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.opensymphony.xwork2.ActionSupport;
import io.opentelemetry.instrumentation.test.base.HttpServerTest;

public class GreetingAction extends ActionSupport {

  String message = "default";

  public String success() {
    message =
        HttpServerTest.controller(
            HttpServerTest.ServerEndpoint.SUCCESS, HttpServerTest.ServerEndpoint.SUCCESS::getBody);

    return "greeting";
  }

  public String query() {
    message =
        HttpServerTest.controller(
            HttpServerTest.ServerEndpoint.QUERY_PARAM,
            HttpServerTest.ServerEndpoint.QUERY_PARAM::getBody);
    return "greeting";
  }

  public String exception() throws Exception {
    message =
        HttpServerTest.controller(
            HttpServerTest.ServerEndpoint.EXCEPTION,
            () -> {
              throw new Exception(HttpServerTest.ServerEndpoint.EXCEPTION.getBody());
            });
    return "exception";
  }

  public void setSome(String some) {
    message = "some=" + some;
    System.out.println("Setting query param some to " + some);
  }

  public String getMessage() {
    return message;
  }
}
