plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")

  compileOnly("org.restlet:org.restlet:2.5.0")
  compileOnly("org.restlet:org.restlet.ext.spring:2.5.0")
}
