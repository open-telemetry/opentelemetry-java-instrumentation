plugins {
  id("org.xbib.gradle.plugin.jflex")

  id("otel.java-conventions")
  id("otel.animalsniffer-conventions")
  id("otel.jacoco-conventions")
  id("otel.japicmp-conventions")
  id("otel.publish-conventions")
}

sourceSets {
  main {
    java {
      // gradle-jflex-plugin has a bug in that it always looks for the last srcDir in this source
      // set to generate into. By default it would be the src/main directory itself.
      srcDir("$buildDir/generated/sources/jflex")
    }
  }
}

group = "io.opentelemetry.instrumentation"

dependencies {
  api("io.opentelemetry:opentelemetry-api")
  api("io.opentelemetry:opentelemetry-semconv")

  implementation("io.opentelemetry:opentelemetry-api-metrics")
  implementation("org.slf4j:slf4j-api")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":testing-common"))
  testImplementation("org.mockito:mockito-core")
  testImplementation("org.mockito:mockito-junit-jupiter")
  testImplementation("org.assertj:assertj-core")
  testImplementation("org.awaitility:awaitility")
  testImplementation("io.opentelemetry:opentelemetry-sdk-metrics")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}

tasks {
  named<Checkstyle>("checkstyleMain") {
    exclude("**/concurrentlinkedhashmap/**")
  }

  sourcesJar {
    dependsOn("generateJflex")
  }

  val testStatementSanitizerConfig by registering(Test::class) {
    filter {
      includeTestsMatching("StatementSanitizationConfigTest")
      isFailOnNoMatchingTests = false
    }
    include("**/StatementSanitizationConfigTest.*")
    jvmArgs("-Dotel.instrumentation.common.db-statement-sanitizer.enabled=false")
  }

  test {
    dependsOn(testStatementSanitizerConfig)

    filter {
      excludeTestsMatching("StatementSanitizationConfigTest")
      isFailOnNoMatchingTests = false
    }
  }
}
