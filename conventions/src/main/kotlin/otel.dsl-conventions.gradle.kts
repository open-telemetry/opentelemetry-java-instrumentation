import io.opentelemetry.instrumentation.gradle.BaseVersionExtension
import io.opentelemetry.instrumentation.gradle.OtelPropsExtension

extensions.create<OtelPropsExtension>("otelProps", project)
extensions.create<BaseVersionExtension>("baseVersion", project)
