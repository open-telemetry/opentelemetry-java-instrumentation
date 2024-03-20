plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  api(project(":instrumentation:servlet:servlet-common:bootstrap"))

  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")
}
