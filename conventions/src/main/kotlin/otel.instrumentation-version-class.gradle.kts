import io.opentelemetry.instrumentation.gradle.GenerateInstrumentationVersionClassTask
import io.opentelemetry.instrumentation.gradle.InstrumentationVersionClassExtension

plugins {
  `java-library`
}

val instrumentationVersionClass = extensions.create<InstrumentationVersionClassExtension>("instrumentationVersionClass")
val generatedInstrumentationVersionClassDir = layout.buildDirectory.dir("generated/sources/instrumentationVersionClass/java/main")

val generateInstrumentationVersionClass = tasks.register<GenerateInstrumentationVersionClassTask>("generateInstrumentationVersionClass") {
  className.set(instrumentationVersionClass.className)
  instrumentationVersion.set(project.version.toString())
  outputDirectory.set(generatedInstrumentationVersionClassDir)
}

sourceSets {
  main {
    java {
      srcDir(generatedInstrumentationVersionClassDir)
    }
  }
}

tasks.matching {
  it.name == "compileJava" || it.name == "sourcesJar" || it.name == "compileKotlin" || it.name == "kotlinSourcesJar"
}.configureEach {
  dependsOn(generateInstrumentationVersionClass)
}

tasks.withType<Checkstyle>().matching { it.name == "checkstyleMain" }.configureEach {
  exclude("build/generated/sources/instrumentationVersionClass/java/main/**")
}
