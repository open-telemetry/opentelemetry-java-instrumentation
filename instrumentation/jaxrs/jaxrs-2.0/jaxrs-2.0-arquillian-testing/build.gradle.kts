plugins {
  id("otel.javaagent-testing")
}

dependencies {
  compileOnly("javax:javaee-api:7.0")

  api(project(":testing-common"))
  implementation("io.opentelemetry:opentelemetry-api")

  val arquillianVersion = "1.4.0.Final"
  implementation("org.jboss.arquillian.junit:arquillian-junit-container:$arquillianVersion")
  implementation("org.jboss.arquillian.protocol:arquillian-protocol-servlet:$arquillianVersion")
  implementation("org.jboss.arquillian.spock:arquillian-spock-container:1.0.0.CR1")
  api("org.jboss.shrinkwrap:shrinkwrap-impl-base:1.2.6")
}
