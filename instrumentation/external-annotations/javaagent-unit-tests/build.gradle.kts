plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation(project(":javaagent-tooling"))
  testImplementation("net.bytebuddy:byte-buddy")
  testImplementation(project(":instrumentation:external-annotations:javaagent"))
}
