plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.scala-conventions")
}

val zioVersion = "2.0.0"
val scalaVersion = "2.12"

muzzle {
  pass {
    group.set("dev.zio")
    module.set("zio_2.12")
    versions.set("[$zioVersion,)")
    assertInverse.set(true)
  }
  pass {
    group.set("dev.zio")
    module.set("zio_2.13")
    versions.set("[$zioVersion,)")
    assertInverse.set(true)
  }
  pass {
    group.set("dev.zio")
    module.set("zio_3")
    versions.set("[$zioVersion,)")
    assertInverse.set(true)
  }
}
dependencies {
  library("dev.zio:zio_$scalaVersion:$zioVersion")
  testCompileOnly("org.scala-lang:scala-library:2.12.20")
}

tasks {
  withType<Test>().configureEach {
    if (name != "testDisabled") {
      jvmArgs("-Dotel.instrumentation.zio.enabled=true")
    }
    if (name != "testScopeLifecycle") {
      exclude("**/ZioScopeLifecycleTest*")
    }
  }

  test {
    jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false")
  }

  val testDisabled = register<Test>("testDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*DefaultEnablementTest") }
  }

  val testScopeLifecycle = register<Test>("testScopeLifecycle") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter { includeTestsMatching("*ZioScopeLifecycleTest") }
    jvmArgs("-Dio.opentelemetry.context.enableStrictContext=true")
    jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=true")
  }

  check {
    dependsOn(testDisabled, testScopeLifecycle)
  }
}
