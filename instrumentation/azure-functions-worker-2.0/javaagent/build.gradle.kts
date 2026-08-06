plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  // the azure functions java worker is not published to maven central so we compile against stub
  // classes, the same stubs are used from the tests to stand in for the worker
  compileOnly(project(":instrumentation:azure-functions-worker-2.0:compile-stub"))
  testImplementation(project(":instrumentation:azure-functions-worker-2.0:compile-stub"))
}
