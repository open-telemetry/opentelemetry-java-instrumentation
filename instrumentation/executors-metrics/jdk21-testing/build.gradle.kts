import kotlin.math.max

plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:executors-metrics:javaagent"))

  testImplementation(project(":instrumentation:executors-metrics:testing"))
}

otelJava {
  val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()
  minJavaVersionSupported.set(
    JavaVersion.toVersion(
      max(
        testJavaVersion.majorVersion.toInt(),
        JavaVersion.VERSION_21.majorVersion.toInt(),
      )
    )
  )
}

tasks.test {
  jvmArgs("-Dotel.instrumentation.executors-metrics.enabled=true")
  jvmArgs("-Dotel.instrumentation.executors.enabled=false")
}
