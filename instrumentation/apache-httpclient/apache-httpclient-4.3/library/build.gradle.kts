plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.animalsniffer-conventions")
}

dependencies {
  library("org.apache.httpcomponents:httpclient:4.3")

  implementation("org.slf4j:slf4j-api")

  testImplementation(project(":instrumentation:apache-httpclient:apache-httpclient-4.3:testing"))

  latestDepTestLibrary("org.apache.httpcomponents:httpclient:4.+")
}
