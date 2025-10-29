plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "OpenTelemetry Javaagent testing commons"
group = "io.opentelemetry.javaagent"

sourceSets {
  main {
    val shadedDeps = project(":testing:dependencies-shaded-for-testing")
    output.dir(
      shadedDeps.file("build/extracted/shadow"),
      "builtBy" to ":testing:dependencies-shaded-for-testing:extractShadowJar"
    )
  }
}

dependencies {
  api("org.junit.jupiter:junit-jupiter-api")
  api("org.junit.jupiter:junit-jupiter-params")

  api("io.opentelemetry:opentelemetry-api")
  api("io.opentelemetry:opentelemetry-sdk")
  api("io.opentelemetry:opentelemetry-sdk-testing")
  api("io.opentelemetry.semconv:opentelemetry-semconv-incubating")
  api(project(":instrumentation-api"))

  api("org.assertj:assertj-core")
  api("org.awaitility:awaitility")
  api("org.mockito:mockito-core")
  api("org.slf4j:slf4j-api")
  api("com.google.code.findbugs:annotations")

  compileOnly(project(":testing:dependencies-shaded-for-testing", configuration = "shadow"))
  compileOnly(project(":javaagent-bootstrap"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation("net.bytebuddy:byte-buddy")
  implementation("ch.qos.logback:logback-classic")
  implementation("org.slf4j:log4j-over-slf4j")
  implementation("org.slf4j:jcl-over-slf4j")
  implementation("org.slf4j:jul-to-slf4j")
  implementation("io.opentelemetry:opentelemetry-exporter-logging")
  implementation("io.opentelemetry.contrib:opentelemetry-baggage-processor")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-incubator")
  api(project(":instrumentation-api-incubator"))

  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service")

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

  jar {
    // When there are duplicates between multiple shaded dependencies, just ignore them.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  }
}
