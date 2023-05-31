plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":muzzle"))
  testImplementation(project(":javaagent-extension-api"))
  testRuntimeOnly(project(":instrumentation:aws-sdk:aws-sdk-2.2:javaagent"))
}
