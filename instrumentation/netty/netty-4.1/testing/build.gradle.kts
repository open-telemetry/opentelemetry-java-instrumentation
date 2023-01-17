plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("io.netty:netty-codec-http:4.1.0.Final")
}