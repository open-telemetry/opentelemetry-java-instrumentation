plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":muzzle"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation(project(":instrumentation:aws-sdk:aws-sdk-2.2:javaagent"))
  testCompileOnly("com.google.auto.service:auto-service-annotations") // Avoid compiler warnings
}
