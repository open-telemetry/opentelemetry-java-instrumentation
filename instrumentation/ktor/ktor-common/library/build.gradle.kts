import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("otel.library-instrumentation")
  id("org.jetbrains.kotlin.jvm")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.ktor")

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

tasks {
  withType(KotlinCompile::class).configureEach {
    kotlinOptions {
      jvmTarget = "1.8"
    }
  }
}
