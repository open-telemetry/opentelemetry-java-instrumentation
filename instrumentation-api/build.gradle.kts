import net.ltgt.gradle.errorprone.errorprone

plugins {
  id("otel.java-conventions")
  id("otel.animalsniffer-conventions")
  id("otel.jacoco-conventions")
  id("otel.publish-conventions")
  id("otel.jmh-conventions")
  id("otel.nullaway-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  api("io.opentelemetry:opentelemetry-api")
  implementation("io.opentelemetry:opentelemetry-api-incubator")
  implementation("io.opentelemetry.semconv:opentelemetry-semconv")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.opentelemetry:opentelemetry-exporter-common")

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

  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }

  val testExceptionSignalLogs by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv.exception.signal.opt-in=logs")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }

  val testExceptionSignalLogsDup by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv.exception.signal.opt-in=logs/dup")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }

  check {
    dependsOn(testExceptionSignalLogs, testExceptionSignalLogsDup)
  }
}
