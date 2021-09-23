plugins {
  id("otel.java-conventions")
}

val scalaVersion = "2.12"

dependencies {
  api(project(":testing-common"))
  api("com.typesafe.play:play-ahc-ws-standalone_$scalaVersion:1.0.2")

  implementation("org.codehaus.groovy:groovy-all")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
