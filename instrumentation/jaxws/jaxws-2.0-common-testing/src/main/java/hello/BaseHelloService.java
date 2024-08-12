/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package hello;

public class BaseHelloService {

  public String hello2(String name) throws Exception {
    if ("exception".equals(name)) {
      throw new Exception("hello exception");
    }
    return "Hello " + name;
  }
}
