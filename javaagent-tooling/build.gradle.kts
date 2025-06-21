import net.ltgt.gradle.errorprone.errorprone

plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
  id("otel.jmh-conventions")
}

group = "io.opentelemetry.javaagent"

dependencies {
  implementation(project(":javaagent-bootstrap"))
  implementation(project(":javaagent-extension-api"))
  implementation(project(":javaagent-tooling:javaagent-tooling-java9"))
  implementation(project(":instrumentation-api"))
  implementation(project(":instrumentation-api-incubator"))
  implementation(project(":instrumentation-annotations-support"))
  implementation(project(":muzzle"))
  implementation(project(":sdk-autoconfigure-support"))

  implementation("io.opentelemetry:opentelemetry-api")
  implementation("io.opentelemetry:opentelemetry-sdk")
  implementation("io.opentelemetry:opentelemetry-extension-kotlin")
  implementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
  // the incubator's ViewConfigCustomizer is used to support loading yaml-based metric views
  implementation("io.opentelemetry:opentelemetry-sdk-extension-incubator") {
    // we use byte-buddy-dep
    exclude("net.bytebuddy", "byte-buddy")
  }

  // Exporters with dependencies
  implementation("io.opentelemetry:opentelemetry-exporter-logging")
  implementation("io.opentelemetry:opentelemetry-exporter-otlp")
  implementation("io.opentelemetry:opentelemetry-exporter-logging-otlp")

  implementation("io.opentelemetry:opentelemetry-exporter-prometheus")
  implementation("io.opentelemetry:opentelemetry-exporter-zipkin")

  implementation("io.opentelemetry:opentelemetry-sdk-extension-jaeger-remote-sampler")

  implementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")

  implementation("io.opentelemetry.contrib:opentelemetry-azure-resources")
  implementation("io.opentelemetry.contrib:opentelemetry-aws-resources")
  implementation("io.opentelemetry.contrib:opentelemetry-gcp-resources")
  implementation("io.opentelemetry.contrib:opentelemetry-cloudfoundry-resources")
  implementation("io.opentelemetry.contrib:opentelemetry-baggage-processor")

  api("net.bytebuddy:byte-buddy-dep")
  implementation("org.ow2.asm:asm-tree")
  implementation("org.ow2.asm:asm-util")

  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service-annotations")
  testCompileOnly("com.google.auto.service:auto-service-annotations")

  // Used by byte-buddy but not brought in as a transitive dependency.
  compileOnly("com.google.code.findbugs:annotations")
  testCompileOnly("com.google.code.findbugs:annotations")

  testImplementation(project(":testing-common"))
  testImplementation("com.google.guava:guava")
  testImplementation("org.junit-pioneer:junit-pioneer")
}

testing {
  suites {
    val testExceptionHandler by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":javaagent-bootstrap"))
        implementation(project(":javaagent-tooling"))
        implementation("net.bytebuddy:byte-buddy-dep")

        // Used by byte-buddy but not brought in as a transitive dependency.
        compileOnly("com.google.code.findbugs:annotations")
      }
    }

    val testMissingType by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":javaagent-bootstrap"))
        implementation(project(":javaagent-tooling"))
        implementation("net.bytebuddy:byte-buddy-dep")
        compileOnly("com.google.guava:guava")

        // Used by byte-buddy but not brought in as a transitive dependency.
        compileOnly("com.google.code.findbugs:annotations")
      }
      targets {
        all {
          testTask.configure {
            filter {
              // Helper class used in test that refers to a class that is missing from the test
              // classpath. We need to exclude it to avoid junit failing while it is searching for
              // test classes.
              excludeTestsMatching("MissingTypeTest\$SomeClass")
            }
          }
        }
      }
    }

    val testPatchBytecodeVersion by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":javaagent-bootstrap"))
        implementation(project(":javaagent-tooling"))
        implementation("net.bytebuddy:byte-buddy-dep")

        // Used by byte-buddy but not brought in as a transitive dependency.
        compileOnly("com.google.code.findbugs:annotations")
      }
    }
  }
}

// Here we only include autoconfigure but don"t include OTLP exporters to ensure they are only in
// the full distribution. We need to override the default exporter setting of OTLP as a result.
tasks {
  withType<Test>().configureEach {
    environment("OTEL_TRACES_EXPORTER", "none")
    environment("OTEL_METRICS_EXPORTER", "none")

    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }

  // TODO this should live in jmh-conventions
  named<JavaCompile>("jmhCompileGeneratedClasses") {
    options.errorprone {
      isEnabled.set(false)
    }
  }

  check {
    dependsOn(testing.suites)
  }
}

// Mockito inline mocking uses byte-buddy but agent tooling currently uses byte-buddy-dep, which cannot be on the same
// classpath. Disable inline mocking to prevent conflicts.
// TODO: Find a better solution
configurations {
  testRuntimeClasspath {
    dependencies {
      exclude("org.mockito", "mockito-inline")
    }
  }
}
