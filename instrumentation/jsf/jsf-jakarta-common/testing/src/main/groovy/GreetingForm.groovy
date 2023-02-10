/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

class GreetingForm {

  String name = ""
  String message = ""

  String getName() {
    name
  }

  void setName(String name) {
    this.name = name
  }

  String getMessage() {
    return message
  }

  void submit() {
    message = "Hello " + name
    if (name == "exception") {
      throw new Exception("submit exception")
    }
  }
}
