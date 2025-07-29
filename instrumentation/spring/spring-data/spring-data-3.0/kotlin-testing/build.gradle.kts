import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("otel.javaagent-testing")
  id("org.jetbrains.kotlin.jvm")
}

dependencies {
  testInstrumentation(project(":instrumentation:r2dbc-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-core-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-data:spring-data-1.8:javaagent"))

  testLibrary("org.springframework.data:spring-data-r2dbc:3.0.0")

  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.8.1")
  testImplementation("org.jetbrains.kotlin:kotlin-reflect")

  testImplementation("org.testcontainers:testcontainers")
  testImplementation("io.r2dbc:r2dbc-h2:1.0.0.RELEASE")
  testImplementation("com.h2database:h2:1.4.197")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
  }
}
