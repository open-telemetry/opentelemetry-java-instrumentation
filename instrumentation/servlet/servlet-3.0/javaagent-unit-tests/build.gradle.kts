plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation("javax.servlet:javax.servlet-api:3.0.1")
  testImplementation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testImplementation(project(":instrumentation:servlet:servlet-common:bootstrap"))
}
