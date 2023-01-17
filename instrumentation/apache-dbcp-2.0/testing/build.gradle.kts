plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  api("org.mockito:mockito-core")
  api("org.mockito:mockito-junit-jupiter")

  compileOnly("org.apache.commons:commons-dbcp2:2.0")
}