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

  implementation("org.codehaus.groovy:groovy-all")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
