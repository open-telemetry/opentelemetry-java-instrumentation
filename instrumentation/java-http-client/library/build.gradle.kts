plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
  id("org.graalvm.buildtools.native")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  testImplementation(project(":instrumentation:java-http-client:testing"))
}


// To be able to execute the tests with GraalVM native
configurations.configureEach {
  exclude("org.apache.groovy", "groovy")
  exclude("org.apache.groovy", "groovy-json")
  exclude("org.spockframework", "spock-core")
}
