plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("dev.failsafe:failsafe:3.0.1")
}
