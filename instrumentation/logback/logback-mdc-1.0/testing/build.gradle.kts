plugins {
  id("otel.java-conventions")
}

dependencies {
  compileOnly(project(":instrumentation:logback:logback-mdc-1.0:library"))

  api(project(":testing-common"))

  api("ch.qos.logback:logback-classic:1.0.0")

  implementation("com.google.guava:guava")

  implementation("org.apache.groovy:groovy")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
