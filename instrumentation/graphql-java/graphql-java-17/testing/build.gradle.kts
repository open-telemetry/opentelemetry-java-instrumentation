plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
//  api(project(":instrumentation:graphql-java:graphql-java-17:library"))

  api("com.graphql-java:graphql-java:17.0")

//  implementation("com.google.guava:guava")

  implementation("org.codehaus.groovy:groovy-all")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")
}
