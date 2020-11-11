/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import com.opensymphony.xwork2.ActionSupport;

public class GreetingAction extends ActionSupport {

  public String success() {
    return "greeting";
  }

  public String query() {
    return "query";
  }

  public String exception() throws Exception {
    throw new Exception("controller exception");
  }

  public void setSome(String some) {
    System.out.println("Setting query param some to " + some);
  }
}
