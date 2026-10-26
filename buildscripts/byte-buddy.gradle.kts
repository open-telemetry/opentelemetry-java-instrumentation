import org.gradle.api.artifacts.component.ModuleComponentIdentifier

// Shared by the main build and both build-logic builds, without a dependency between them.
abstract class ByteBuddyCapabilityRule : ComponentMetadataRule {
  override fun execute(context: ComponentMetadataContext) {
    context.details.allVariants {
      withCapabilities {
        addCapability("net.bytebuddy", "byte-buddy-asm-variant", context.details.id.version)
      }
    }
  }
}

for (handler in listOf(dependencies, project.buildscript.dependencies)) {
  handler.components {
    withModule<ByteBuddyCapabilityRule>("net.bytebuddy:byte-buddy")
    withModule<ByteBuddyCapabilityRule>("net.bytebuddy:byte-buddy-dep")
  }
}

val byteBuddyVariant = Attribute.of("io.opentelemetry.byte-buddy-variant", String::class.java)
for (container in listOf(configurations, project.buildscript.configurations)) {
  container.configureEach {
    val configuration = this
    resolutionStrategy.componentSelection.all {
      if (candidate.group == "org.mockito" && candidate.module == "mockito-core" &&
        configuration.attributes.getAttribute(byteBuddyVariant) == "byte-buddy-dep"
      ) {
        throw GradleException("Mockito requires standard byte-buddy; separate it from external-ASM transformations")
      }
    }
    resolutionStrategy.capabilitiesResolution.withCapability("net.bytebuddy:byte-buddy-asm-variant") {
      val variant = configuration.attributes.getAttribute(byteBuddyVariant)
      if (variant !in listOf("byte-buddy", "byte-buddy-dep")) {
        throw GradleException(
          "${configuration.name} contains both byte-buddy (relocated ASM) and byte-buddy-dep (external ASM). " +
            "They are not binary interchangeable. Choose a Byte Buddy variant for this source set; " +
            "separate Mockito consumers from external-ASM transformations."
        )
      }
      val candidate = candidates.singleOrNull {
        (it.id as? ModuleComponentIdentifier)?.module == variant
      } ?: throw GradleException("No unique $variant provider in ${configuration.name}")
      select(candidate)
      because("Explicit Byte Buddy ASM variant for ${configuration.name}")
    }
  }
}
