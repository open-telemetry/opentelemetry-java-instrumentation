plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  api("javax.ws.rs:javax.ws.rs-api:2.0")

  implementation("org.codehaus.groovy:groovy-all")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
  implementation("org.slf4j:slf4j-api")
  implementation("ch.qos.logback:logback-classic")
  implementation("org.slf4j:log4j-over-slf4j")
  implementation("org.slf4j:jcl-over-slf4j")
  implementation("org.slf4j:jul-to-slf4j")

  implementation(project(":javaagent-instrumentation-api"))
  implementation(project(":instrumentation-api"))
  implementation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-common:javaagent"))

  compileOnly("org.eclipse.jetty:jetty-webapp:8.0.0.v20110901")
}
