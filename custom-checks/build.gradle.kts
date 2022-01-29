plugins {
  `java-library`
}

dependencies {
  implementation("com.google.errorprone:error_prone_core:2.11.0")

  annotationProcessor("com.google.auto.service:auto-service:1.0.1")
  compileOnly("com.google.auto.service:auto-service-annotations")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.2")

  testImplementation("com.google.errorprone:error_prone_test_helpers:2.11.0")
}

tasks {
  test {
    useJUnitPlatform()
  }

  withType<JavaCompile> {
    options.compilerArgs.addAll(listOf(
      "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
      "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
      "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
    ))
  }
}
