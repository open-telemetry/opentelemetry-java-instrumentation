/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server;

public class FooModel {
  private final long id;
  private final String name;

  public FooModel(long id, String name) {
    this.id = id;
    this.name = name;
  }

  public long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return "{\"id\":" + id + ",\"name\":\"" + name + "\"}";
  }
}
