plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("com.linecorp.armeria:armeria:1.3.0")
  api("com.linecorp.armeria:armeria-junit4:1.3.0")
}
