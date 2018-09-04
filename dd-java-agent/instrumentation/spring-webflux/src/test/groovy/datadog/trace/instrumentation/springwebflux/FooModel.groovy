package datadog.trace.instrumentation.springwebflux

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

  static FooModel[] createXFooModels(long count) {
    FooModel[] foos = new FooModel[count]
    for (int i = 0; i < count; ++i) {
      foos[i] = new FooModel(i, String.valueOf(i))
    }
    return foos
  }

  static String createXFooModelsStringFromArray(FooModel[] foos) {
    StringBuilder sb = new StringBuilder()
    sb.append("[")
    for (int i = 0; i < foos.length; ++i) {
      sb.append(foos[i].toString())
      if (i < foos.length - 1) {
        sb.append(",")
      }
    }
    sb.append("]")
    return sb.toString()
  }
}
