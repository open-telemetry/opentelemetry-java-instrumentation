plugins {
  id("java")
  id("com.diffplug.spotless") version "8.10.4"
}

spotless {
  java {
    googleJavaFormat()
    licenseHeaderFile(rootProject.file("../buildscripts/spotless.license.java"), "(package|import|public)")
    target("src/**/*.java")
  }
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(enforcedPlatform("org.junit:junit-bom:5.14.4"))

  testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
  testImplementation("org.testcontainers:testcontainers:2.0.5")
  testImplementation("org.testcontainers:testcontainers-postgresql")
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testImplementation("com.squareup.okhttp3:okhttp:5.5.0")
  testImplementation("org.jooq:joox:2.0.2")
  testImplementation("com.jayway.jsonpath:json-path:3.0.0")
  testImplementation("org.slf4j:slf4j-simple:2.0.20")
  testImplementation("org.assertj:assertj-core:3.27.7")

  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
  test {
    useJUnitPlatform()
    exclude("**/AotStartupBenchmark.class")
    exclude("**/AgentStartupBenchmark.class")
  }
}

val startupRecorderJar = tasks.register<Jar>("startupRecorderJar") {
  dependsOn(tasks.testClasses)
  from(sourceSets.test.get().output.classesDirs)
  include("io/opentelemetry/startup/StartupRecorder.class")
  archiveFileName.set("startup-recorder.jar")
  manifest.attributes("Premain-Class" to "io.opentelemetry.startup.StartupRecorder")
}

tasks.register<Test>("agentStartupBenchmark") {
  description = "Measures ordinary Java agent startup overhead against a no-agent control."
  group = "verification"
  dependsOn(tasks.testClasses)
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  useJUnitPlatform()
  include("**/AgentStartupBenchmark.class")
  maxParallelForks = 1
  outputs.upToDateWhen { false }
  outputs.cacheIf { false }
  systemProperty("startup.benchmark.enabled", "true")
  systemProperty("startup.benchmark.output", layout.buildDirectory.dir("reports/agent-startup").get().asFile.absolutePath)
  systemProperty("startup.benchmark.agent", providers.gradleProperty("startupBenchmarkAgentJar").orElse("").get())
  systemProperty("startup.benchmark.revision", providers.gradleProperty("startupBenchmarkRevision").orElse("unknown").get())
  systemProperty("startup.benchmark.baselineAgent", providers.gradleProperty("startupBenchmarkBaselineAgentJar").orElse("").get())
  systemProperty("startup.benchmark.baselineRevision", providers.gradleProperty("startupBenchmarkBaselineRevision").orElse("unknown").get())
  val suppliedRecorder = providers.gradleProperty("startupBenchmarkRecorderJar")
  if (suppliedRecorder.isPresent) {
    systemProperty("startup.benchmark.recorder", suppliedRecorder.get())
  } else {
    dependsOn(startupRecorderJar)
    systemProperty(
      "startup.benchmark.recorder",
      startupRecorderJar.get().archiveFile.get().asFile.absolutePath
    )
  }
  systemProperty(
    "startup.benchmark.fixtureClass",
    providers.gradleProperty("startupBenchmarkFixtureClass")
      .orElse("../smoke-tests/images/spring-boot/build/classes/java/main/io/opentelemetry/smoketest/springboot/controller/WebController.class")
      .get()
  )
  systemProperty("startup.benchmark.samples", providers.gradleProperty("startupBenchmarkSamples").orElse("20").get())
  systemProperty("startup.benchmark.warmups", providers.gradleProperty("startupBenchmarkWarmups").orElse("2").get())
}

tasks.register<Test>("aotStartupBenchmark") {
  description = "Measures JDK 25 Spring startup with and without AOT and the Java agent."
  group = "verification"
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  useJUnitPlatform()
  include("**/AotStartupBenchmark.class")
  maxParallelForks = 1
  outputs.upToDateWhen { false }
  outputs.cacheIf { false }
  systemProperty("aot.benchmark.enabled", "true")
  systemProperty("aot.benchmark.output", layout.buildDirectory.dir("reports/aot-startup").get().asFile.absolutePath)
  systemProperty("aot.benchmark.agent", providers.gradleProperty("aotBenchmarkAgentJar").orElse("").get())
  systemProperty("aot.benchmark.samples", providers.gradleProperty("aotBenchmarkSamples").orElse("20").get())
  systemProperty("aot.benchmark.warmups", providers.gradleProperty("aotBenchmarkWarmups").orElse("2").get())
  testLogging.showStandardStreams = true
}
