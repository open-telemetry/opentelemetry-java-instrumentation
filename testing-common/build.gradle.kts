plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "OpenTelemetry Javaagent testing commons"
group = "io.opentelemetry.javaagent"

sourceSets {
  main {
    val armeriaShadedDeps = project(":testing:armeria-shaded-for-testing")
    output.dir(
      armeriaShadedDeps.file("build/extracted/shadow"),
      "builtBy" to ":testing:armeria-shaded-for-testing:extractShadowJar"
    )

    val protoShadedDeps = project(":testing:proto-shaded-for-testing")
    output.dir(
      protoShadedDeps.file("build/extracted/shadow"),
      "builtBy" to ":testing:proto-shaded-for-testing:extractShadowJar"
    )

    val wiremockShadedDeps = project(":testing:wiremock-shaded-for-testing")
    output.dir(
      wiremockShadedDeps.file("build/extracted/shadow"),
      "builtBy" to ":testing:wiremock-shaded-for-testing:extractShadowJar"
    )
  }
}

dependencies {
  api("org.apache.groovy:groovy")
  api("org.apache.groovy:groovy-json")
  api("org.spockframework:spock-core") {
    // exclude optional dependencies
    exclude(group = "cglib", module = "cglib-nodep")
    exclude(group = "net.bytebuddy", module = "byte-buddy")
    exclude(group = "org.junit.platform", module = "junit-platform-testkit")
    exclude(group = "org.jetbrains", module = "annotations")
    exclude(group = "org.objenesis", module = "objenesis")
    exclude(group = "org.ow2.asm", module = "asm")
  }
  api("org.spockframework:spock-junit4") {
    // spock-core is already added as dependency
    // exclude it here to avoid pulling in optional dependencies
    exclude(group = "org.spockframework", module = "spock-core")
  }
  api("org.junit.jupiter:junit-jupiter-api")
  api("org.junit.jupiter:junit-jupiter-params")

  api("io.opentelemetry:opentelemetry-api")
  api("io.opentelemetry:opentelemetry-sdk")
  api("io.opentelemetry:opentelemetry-sdk-testing")
  api("io.opentelemetry.semconv:opentelemetry-semconv-incubating")
  api(project(":instrumentation-api"))

  api("org.assertj:assertj-core")
  // Needs to be api dependency due to Spock restriction.
  api("org.awaitility:awaitility")
  api("org.mockito:mockito-core")
  api("org.slf4j:slf4j-api")

  compileOnly(project(":testing:armeria-shaded-for-testing", configuration = "shadow"))
  compileOnly(project(":testing:proto-shaded-for-testing", configuration = "shadow"))
  // used to record LLM responses in gen AI tests
  compileOnly(project(":testing:wiremock-shaded-for-testing", configuration = "shadow"))
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
