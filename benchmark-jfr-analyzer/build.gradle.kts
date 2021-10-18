plugins {
  id("otel.java-conventions")
}

tasks.withType<JavaCompile>().configureEach {
  with(options) {
    release.set(11)
  }
}

dependencies {
  implementation("com.google.code.findbugs:jsr305:3.0.2")
}
