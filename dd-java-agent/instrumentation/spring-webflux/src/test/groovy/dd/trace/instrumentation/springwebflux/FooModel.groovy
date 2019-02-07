package dd.trace.instrumentation.springwebflux

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
