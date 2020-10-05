/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

class FooModel {
  public long id
  public String name

  FooModel(long id, String name) {
    this.id = id
    this.name = name
  }

  @Override
  String toString() {
    return "{\"id\":" + id + ",\"name\":\"" + name + "\"}"
  }
}
