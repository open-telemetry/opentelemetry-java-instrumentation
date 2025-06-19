plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  implementation("dev.failsafe:failsafe:3.0.1")
}
