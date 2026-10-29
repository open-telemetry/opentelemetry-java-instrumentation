plugins {
  id("otel.java-conventions")
}

repositories {
  mavenCentral()
  maven("https://maven.restlet.talend.com/")
  mavenLocal()
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-javaagent-instrumentation-testing")

  implementation("org.restlet.jse:org.restlet:2.0.2")
  implementation("org.restlet.jse:org.restlet.ext.spring:2.0.2")
}
