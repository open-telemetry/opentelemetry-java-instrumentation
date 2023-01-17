import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("otel.library-instrumentation")
  id("org.jetbrains.kotlin.jvm")
}

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
