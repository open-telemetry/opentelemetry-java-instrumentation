plugins {
  id("otel.library-instrumentation")
  id("java-test-fixtures")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation(project(":instrumentation-api"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
  testImplementation("io.github.netmikey.logunit:logunit-jul:1.1.3")
  testImplementation(testFixtures(project))

  testFixturesImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
  testFixturesImplementation("io.opentelemetry:opentelemetry-api")
  testFixturesImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testFixturesImplementation("org.awaitility:awaitility")
  testFixturesImplementation("org.assertj:assertj-core:3.24.2")
}
