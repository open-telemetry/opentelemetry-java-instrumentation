import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.jetbrains.kotlinx")
    module.set("kotlinx-coroutines-core")
    versions.set("[1.3.0,1.3.8)")
  }
  // 1.3.9 (and beyond?) have changed how artifact names are resolved due to multiplatform variants
  pass {
    group.set("org.jetbrains.kotlinx")
    module.set("kotlinx-coroutines-core-jvm")
    versions.set("[1.3.9,)")
  }
}

dependencies {
  library("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.0")
  compileOnly(project(":instrumentation-annotations-support"))
  implementation(project(":instrumentation:kotlinx-coroutines:kotlinx-coroutines-flow-1.3:javaagent-kotlin"))

  testInstrumentation(project(":instrumentation:kotlinx-coroutines:kotlinx-coroutines-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:opentelemetry-extension-kotlin-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))

  testImplementation("io.opentelemetry:opentelemetry-extension-kotlin")
  testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  testImplementation(project(":instrumentation:reactor:reactor-3.1:library"))
  testImplementation(project(":instrumentation-annotations"))

  testLibrary("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.3.0")
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
  }
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false")
}
