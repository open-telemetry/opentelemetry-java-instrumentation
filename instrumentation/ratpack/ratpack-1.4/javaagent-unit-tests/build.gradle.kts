plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:ratpack:ratpack-1.4:javaagent"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation(project(":muzzle"))
}
