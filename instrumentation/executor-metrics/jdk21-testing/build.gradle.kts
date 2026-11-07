import kotlin.math.max

plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testInstrumentation(project(":instrumentation:executor-metrics:javaagent"))

  testImplementation(project(":instrumentation:executor-metrics:testing"))
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
  jvmArgs("-Dotel.instrumentation.executor-metrics.enabled=true")
  jvmArgs("-Dotel.instrumentation.executors.enabled=false")
}
