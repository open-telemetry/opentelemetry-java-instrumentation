plugins {
  id("otel.java-conventions")
}

tasks.withType<JavaCompile>().configureEach {
  with(options) {
    release.set(11)
  }
}
