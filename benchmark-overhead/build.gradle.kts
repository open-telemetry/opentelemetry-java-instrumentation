plugins {
  id("java")
  id("com.diffplug.spotless") version "8.11.0"
}

val startupHelper = sourceSets.create("startupHelper") {
  java.srcDir("src/startupHelper/java")
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
  testImplementation("com.squareup.okhttp3:okhttp:5.5.1")
  testImplementation("org.jooq:joox:2.0.2")
  testImplementation("com.jayway.jsonpath:json-path:3.0.0")
  testImplementation("org.slf4j:slf4j-simple:2.0.20")
  testImplementation("org.assertj:assertj-core:3.27.7")
  testImplementation("org.springframework:spring-webmvc:7.1.0")
  add(startupHelper.compileOnlyConfigurationName, "org.springframework:spring-webmvc:7.1.0")
  testImplementation(startupHelper.output)

  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<JavaCompile>(startupHelper.compileJavaTaskName) {
  options.release.set(17)
}

tasks {
  test {
    useJUnitPlatform()
    exclude("**/AotStartupBenchmark.class")
    exclude("**/AgentStartupBenchmark.class")
  }
}

val compileStartupRecorder = tasks.register<JavaCompile>("compileStartupRecorder") {
  source("src/test/java/io/opentelemetry/startup/StartupRecorder.java")
  classpath = files()
  destinationDirectory.set(layout.buildDirectory.dir("classes/startupRecorder"))
  options.release.set(17)
}

val startupRecorderJar = tasks.register<Jar>("startupRecorderJar") {
  dependsOn(compileStartupRecorder)
  from(compileStartupRecorder)
  include("io/opentelemetry/startup/StartupRecorder.class")
  archiveFileName.set("startup-recorder.jar")
  manifest.attributes("Premain-Class" to "io.opentelemetry.startup.StartupRecorder")
}

val petclinicStartupHelperJar = tasks.register<Jar>("petclinicStartupHelperJar") {
  dependsOn(startupHelper.classesTaskName)
  from(startupHelper.output) {
    include("io/opentelemetry/startup/PetclinicStartupUptimeResponseAdvice.class")
    include("META-INF/spring.factories")
    include("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
  }
  archiveFileName.set("petclinic-startup-helper.jar")
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
  systemProperty("startup.benchmark.workload", providers.gradleProperty("startupBenchmarkWorkload").orElse("smoke").get())
  systemProperty("startup.benchmark.annotations", providers.gradleProperty("startupBenchmarkAnnotationsJar").orElse("").get())
  val startupWorkload = providers.gradleProperty("startupBenchmarkWorkload").orElse("smoke")
  val suppliedPetclinicHelper = providers.gradleProperty("startupBenchmarkPetclinicHelperJar")
  if (startupWorkload.get() == "petclinic") {
    if (suppliedPetclinicHelper.isPresent) {
      systemProperty("startup.benchmark.petclinicHelper", suppliedPetclinicHelper.get())
    } else {
      dependsOn(petclinicStartupHelperJar)
      systemProperty(
        "startup.benchmark.petclinicHelper",
        petclinicStartupHelperJar.get().archiveFile.get().asFile.absolutePath
      )
    }
  }
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
