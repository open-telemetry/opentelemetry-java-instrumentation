package io.opentelemetry.test.instrumentation.springwebflux.server

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
