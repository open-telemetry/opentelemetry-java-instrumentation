plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "OpenTelemetry Javaagent testing commons"
group = "io.opentelemetry.javaagent"

dependencies {
  api("org.codehaus.groovy:groovy-all")
  api("org.spockframework:spock-core")
  implementation("org.junit.jupiter:junit-jupiter-api")

  api("io.opentelemetry:opentelemetry-api")
  api("io.opentelemetry:opentelemetry-semconv")
  api("io.opentelemetry:opentelemetry-sdk")
  api("io.opentelemetry:opentelemetry-sdk-metrics")
  api("io.opentelemetry:opentelemetry-sdk-testing")

  api(project(path = ":testing:armeria-shaded-for-testing", configuration = "shadow"))

  implementation("io.opentelemetry:opentelemetry-proto") {
    // Only need the proto, not gRPC.
    exclude("io.grpc")
  }

  implementation("com.google.guava:guava")
  implementation("net.bytebuddy:byte-buddy")
  implementation("net.bytebuddy:byte-buddy-agent")
  implementation("org.slf4j:slf4j-api")
  implementation("ch.qos.logback:logback-classic")
  implementation("org.slf4j:log4j-over-slf4j")
  implementation("org.slf4j:jcl-over-slf4j")
  implementation("org.slf4j:jul-to-slf4j")
  implementation("io.opentelemetry:opentelemetry-extension-annotations")
  implementation("io.opentelemetry:opentelemetry-exporter-logging")
  implementation(project(":instrumentation-api"))

  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service")

  testImplementation("org.assertj:assertj-core")

  testImplementation(project(":javaagent-api"))
  testImplementation(project(":javaagent-tooling"))
  testImplementation(project(":javaagent-bootstrap"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation(project(":instrumentation:external-annotations:javaagent"))

  // We have autoservices defined in test subtree, looks like we need this to be able to properly rebuild this
  testAnnotationProcessor("com.google.auto.service:auto-service")
  testCompileOnly("com.google.auto.service:auto-service")
}

tasks {
  javadoc {
    enabled = false
  }
}
