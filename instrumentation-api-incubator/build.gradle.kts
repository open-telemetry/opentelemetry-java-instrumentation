plugins {
  id("otel.java-conventions")
  id("otel.animalsniffer-conventions")
  id("otel.jacoco-conventions")
  id("otel.publish-conventions")
  id("otel.nullaway-conventions")
}

group = "io.opentelemetry.instrumentation"

val jflex = configurations.create("jflex")

dependencies {
  jflex("de.jflex:jflex:1.9.1")

  api("io.opentelemetry.semconv:opentelemetry-semconv")
  api(project(":instrumentation-api"))
  api("io.opentelemetry:opentelemetry-api-incubator")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  testImplementation("io.opentelemetry:opentelemetry-sdk")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.opentelemetry.semconv:opentelemetry-semconv-incubating")
}

val jflexSourceDir = layout.projectDirectory.dir("src/main/jflex")
val jflexOutputDir = layout.buildDirectory.dir("generated/sources/jflex")

val generateJflex by tasks.registering(JavaExec::class) {
  classpath(jflex)
  mainClass.set("jflex.Main")

  inputs.dir(jflexSourceDir)
  outputs.dir(jflexOutputDir)

  val sourceDir = jflexSourceDir
  val outputDirProvider = jflexOutputDir

  doFirst {
    val outputDir = outputDirProvider.get().asFile
    outputDir.mkdirs()
    val specFile = sourceDir.asFile.resolve("SqlSanitizer.jflex")
    args(
      "-d", outputDir.absolutePath,
      "--nobak",
      specFile.absolutePath,
    )
  }
}

sourceSets {
  main {
    java.srcDir(jflexOutputDir)
  }
}

tasks.compileJava {
  dependsOn(generateJflex)
}

tasks {
  // exclude auto-generated code
  named<Checkstyle>("checkstyleMain") {
    exclude("**/AutoSqlSanitizer.java")
  }

  // Work around https://github.com/jflex-de/jflex/issues/762
  compileJava {
    with(options) {
      compilerArgs.add("-Xlint:-fallthrough")
    }
  }

  sourcesJar {
    dependsOn(generateJflex)
    // Avoid configuration cache issue by not capturing task reference
    from("src/main/jflex") {
      include("**/*.java")
    }
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database,code")
  }

  val testBothSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database/dup,code/dup")
  }

  check {
    dependsOn(testStableSemconv, testBothSemconv)
  }
}
