plugins {
  id("otel.java-conventions")
}

dependencies {
  compileOnly("javax:javaee-api:7.0")

  api(project(":testing-common"))
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.jsoup:jsoup:1.13.1")

  val arquillianVersion = "1.7.0.Alpha10"
  implementation("org.jboss.arquillian.junit5:arquillian-junit5-container:$arquillianVersion")
  implementation("org.jboss.arquillian.protocol:arquillian-protocol-servlet:$arquillianVersion")
  api("org.jboss.shrinkwrap:shrinkwrap-impl-base:1.2.6")
}
