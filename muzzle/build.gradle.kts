import io.opentelemetry.instrumentation.gradle.ByteBuddyVariant
import io.opentelemetry.instrumentation.gradle.useByteBuddy

plugins {
  id("otel.java-conventions")
  id("otel.nullaway-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

useByteBuddy(sourceSets.main.get(), ByteBuddyVariant.EXTERNAL_ASM)
useByteBuddy(sourceSets.test.get(), ByteBuddyVariant.STANDARD)

dependencies {
  // Only used during compilation by bytebuddy plugin
  compileOnly("com.google.guava:guava")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  api("net.bytebuddy:byte-buddy-dep")
  implementation("org.ow2.asm:asm-tree")

  implementation(project(":javaagent-bootstrap"))
  implementation(project(":instrumentation-api"))
  implementation(project(":instrumentation-api-incubator"))
  implementation(project(":javaagent-extension-api"))

  // Used by byte-buddy but not brought in as a transitive dependency.
  compileOnly("com.google.code.findbugs:annotations")

  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  testImplementation("com.google.guava:guava")
}
