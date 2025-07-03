import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("otel.java-conventions")
  id("otel.shadow-conventions")

  id("io.opentelemetry.instrumentation.muzzle-generation")
  id("io.opentelemetry.instrumentation.muzzle-check")
}

val testInstrumentation by configurations.creating
val testAgent by configurations.creating

dependencies {
  add("testInstrumentation", platform(project(":dependencyManagement")))
  compileOnly("io.opentelemetry:opentelemetry-sdk")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  compileOnly("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
  compileOnly("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api-incubator")
  compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")
  compileOnly("io.opentelemetry.semconv:opentelemetry-semconv")

  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service")
  compileOnly(project(":bootstrap"))

  // the javaagent that is going to be used when running instrumentation unit tests
  testAgent(project(path = ":testing:agent-for-testing", configuration = "shadow"))
  // test dependencies
  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.assertj:assertj-core")

  add("codegen", platform(project(":dependencyManagement")))
  add("muzzleBootstrap", platform(project(":dependencyManagement")))
  add("muzzleTooling", platform(project(":dependencyManagement")))
  
  add("codegen", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")
  add("muzzleBootstrap", "io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations-support")
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")
  add("muzzleTooling", "io.opentelemetry.javaagent:opentelemetry-javaagent-tooling")
}

tasks.named<ShadowJar>("shadowJar").configure {
  configurations = listOf(project.configurations.runtimeClasspath.get(), testInstrumentation)
  mergeServiceFiles()
  archiveFileName.set("agent-testing.jar")
}

tasks.withType<Test>().configureEach {
  val shadowJar = tasks.shadowJar.get()
  val agentShadowJar = project(":testing:agent-for-testing").tasks.shadowJar
  inputs.file(shadowJar.archiveFile)

  dependsOn(shadowJar)
  dependsOn(agentShadowJar.get())

  jvmArgs("-Dotel.javaagent.debug=true")
  jvmArgs("-Dotel.javaagent.experimental.initializer.jar=${shadowJar.archiveFile.get().asFile.absolutePath}")
  jvmArgs("-Dotel.javaagent.testing.additional-library-ignores.enabled=false")
  jvmArgs("-Dotel.javaagent.testing.fail-on-context-leak=true")
  jvmArgs("-Dotel.javaagent.testing.transform-safe-logging.enabled=true")

  // The sources are packaged into the testing jar so we need to make sure to exclude from the test
  // classpath, which automatically inherits them, to ensure our shaded versions are used.
  classpath = classpath.filter {
    if (it == file(layout.buildDirectory.dir("resources/main")) || it == file(layout.buildDirectory.dir("classes/java/main"))) {
      return@filter false
    }
    return@filter true
  }
} 
