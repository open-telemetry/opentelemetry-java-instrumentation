plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  api("jakarta.ws.rs:jakarta.ws.rs-api:3.0.0")

  implementation("org.apache.groovy:groovy")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
  implementation("org.slf4j:slf4j-api")
  implementation("ch.qos.logback:logback-classic")
  implementation("org.slf4j:log4j-over-slf4j")
  implementation("org.slf4j:jcl-over-slf4j")
  implementation("org.slf4j:jul-to-slf4j")

  implementation(project(":javaagent-extension-api"))
  implementation(project(":instrumentation-api"))
  implementation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-common:javaagent"))

  compileOnly("org.eclipse.jetty:jetty-webapp:11.0.0")
}
