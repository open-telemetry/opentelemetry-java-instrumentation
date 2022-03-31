plugins {
  id("otel.java-conventions")
}

repositories {
  mavenCentral()
  maven("https://maven.restlet.talend.com/")
  mavenLocal()
}

dependencies {
  api(project(":testing-common"))

  implementation("org.restlet:org.restlet:1.1.5")
  implementation("com.noelios.restlet:com.noelios.restlet:1.1.5")
  implementation("com.noelios.restlet:com.noelios.restlet.ext.servlet:1.1.5")
  implementation("org.restlet:org.restlet.ext.spring:1.1.5")
  implementation("org.springframework:spring:2.5.6")

  implementation("org.apache.groovy:groovy")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")

  val jettyVersion = "8.1.8.v20121106"
  api("org.eclipse.jetty:jetty-annotations:$jettyVersion")
  implementation("org.eclipse.jetty:jetty-server:$jettyVersion")
  implementation("org.eclipse.jetty:jetty-servlet:$jettyVersion")
}
