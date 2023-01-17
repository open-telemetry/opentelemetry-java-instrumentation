plugins {
  id("otel.java-conventions")
}

// Jetty client 9.2 is the best starting point, HttpClient.send() is stable there
val jettyVers_base9 = "9.2.0.v20140526"

dependencies {
  api(project(":testing-common"))

  api("org.eclipse.jetty:jetty-client:$jettyVers_base9")

  implementation("org.junit.jupiter:junit-jupiter-api")

  implementation("org.apache.groovy:groovy")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
