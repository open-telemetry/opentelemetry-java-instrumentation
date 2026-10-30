plugins {
  id("otel.java-conventions")
}

val scalaVersion = "2.12"

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-instrumentation-testing")
  api("com.typesafe.play:play-ahc-ws-standalone_$scalaVersion:1.0.2")

  implementation("io.opentelemetry:opentelemetry-api")
}
