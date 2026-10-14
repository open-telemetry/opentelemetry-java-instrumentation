plugins {
  id("otel.javaagent-instrumentation")
}

val scalaVersion = "2.12"

dependencies {
  compileOnly("com.typesafe.play:play-ahc-ws-standalone_$scalaVersion:1.0.2")
}
