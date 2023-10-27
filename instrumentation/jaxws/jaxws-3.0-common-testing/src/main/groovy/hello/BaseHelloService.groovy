/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package hello

class BaseHelloService {

  String hello2(String name) {
    if ("exception" == name) {
      throw new Exception("hello exception")
    }
    return "Hello " + name
  }
}
