import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.jetbrains.kotlinx")
    module.set("kotlinx-coroutines-core")
    versions.set("[1.0.0,1.3.8)")
  }
  // 1.3.9 (and beyond?) have changed how artifact names are resolved due to multiplatform variants
  pass {
    group.set("org.jetbrains.kotlinx")
    module.set("kotlinx-coroutines-core-jvm")
    versions.set("[1.3.9,)")
  }
}
dependencies {
  compileOnly("io.opentelemetry:opentelemetry-extension-kotlin")
  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  // Use first version with flow support since we have tests for it.
  library("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.0")
  library("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.3.0")
  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  testImplementation("io.opentelemetry:opentelemetry-extension-kotlin")
  testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

tasks {
  withType(KotlinCompile::class).configureEach {
    kotlinOptions {
      jvmTarget = "1.8"
    }
  }

  val compileTestKotlin by existing(AbstractCompile::class)

  named<GroovyCompile>("compileTestGroovy") {
    // Note: look like it should be `classpath += files(sourceSets.test.kotlin.classesDirectory)`
    // instead, but kotlin plugin doesn't support it (yet?)
    classpath = classpath.plus(files(compileTestKotlin.get().destinationDir))
  }
}
