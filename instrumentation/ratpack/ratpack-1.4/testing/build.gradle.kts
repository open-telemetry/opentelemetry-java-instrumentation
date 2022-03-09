plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  // it's important for these to not be api dependencies, because api dependencies pull in their
  // transitive dependencies as well, which causes issues for testLatestDep
  implementation("io.ratpack:ratpack-core:1.4.0")
  implementation("io.ratpack:ratpack-test:1.4.0")

  implementation("org.apache.groovy:groovy")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
