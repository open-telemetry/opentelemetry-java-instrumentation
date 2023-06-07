plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation("jakarta.servlet:jakarta.servlet-api:5.0.0")
  testImplementation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
}
