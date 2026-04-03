import io.opentelemetry.instrumentation.gradle.OtelPropsExtension

extensions.create<OtelPropsExtension>("otelProps", project)
