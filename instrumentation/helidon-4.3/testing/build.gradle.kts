plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_21)
}

dependencies {
  api(project(":testing-common"))
  implementation("io.helidon.webserver:helidon-webserver:4.3.0")
}
