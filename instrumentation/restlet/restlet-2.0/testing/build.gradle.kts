plugins {
  id("otel.java-conventions")
}

repositories {
  mavenCentral()
  maven { setUrl("https://oss.sonatype.org/content/repositories/snapshots") }
  maven("https://maven.restlet.talend.com/")
  mavenLocal()
}

dependencies {
  api(project(":testing-common"))

  implementation("org.restlet.jse:org.restlet:2.0.2")
  implementation("org.restlet.jse:org.restlet.ext.spring:2.0.2")

  implementation("org.apache.groovy:groovy")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
