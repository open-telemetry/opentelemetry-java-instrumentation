plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  api("org.mockito:mockito-core")
  api("org.mockito:mockito-junit-jupiter")

  compileOnly("com.mchange:c3p0:0.9.2")
}