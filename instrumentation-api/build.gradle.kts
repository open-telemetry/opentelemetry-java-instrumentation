import io.opentelemetry.instrumentation.gradle.executions
import net.ltgt.gradle.errorprone.errorprone

plugins {
  id("otel.java-conventions")
  id("otel.animalsniffer-conventions")
  id("otel.jacoco-conventions")
  id("otel.osgi-conventions")
  id("otel.publish-conventions")
  id("otel.jmh-conventions")
  id("otel.nullaway-conventions")
}

group = "io.opentelemetry.instrumentation"

otelJava {
  // InternalInstrumenterCustomizerUtil looks up an instrumentation-api-incubator class via
  // Class.forName and tolerates its absence ("incubator api not available, ignore"). Mark that
  // package optional so the stable instrumentation-api bundle still resolves in an OSGi runtime that
  // does not ship instrumentation-api-incubator. (The upstream io.opentelemetry.api.incubator.*
  // packages remain mandatory - instrumentation-api genuinely requires opentelemetry-api-incubator.)
  osgiImportPackages.add(
    "io.opentelemetry.instrumentation.api.incubator.instrumenter.internal;resolution:=optional",
  )
}

dependencies {
  api("io.opentelemetry:opentelemetry-api")
  implementation("io.opentelemetry:opentelemetry-api-incubator")
  implementation("io.opentelemetry.semconv:opentelemetry-semconv")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-declarative-config")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.opentelemetry:opentelemetry-exporter-common")
  testImplementation(project(":declarative-config-bridge"))

  jmhImplementation(project(":instrumentation-api-incubator"))
}

tasks {
  named<Checkstyle>("checkstyleMain") {
    exclude("**/concurrentlinkedhashmap/**")
  }

  // TODO this should live in jmh-conventions
  named<JavaCompile>("jmhCompileGeneratedClasses") {
    options.errorprone {
      enabled.set(false)
    }
  }
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          // required on jdk17
          jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
          jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
          jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
        }
        register("ExceptionSignalLogs") {
          jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
        }
        register("ExceptionSignalLogsDup") {
          jvmArgs("-Dotel.semconv.exception.signal.preview=logs/dup")
        }
      }
    }
  }
}
