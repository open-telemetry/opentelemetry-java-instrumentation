plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("io.micrometer:micrometer-core:1.5.0")
}
