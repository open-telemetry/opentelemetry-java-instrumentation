Use `javaToolchainVersion` when a project needs a Java toolchain newer than the repository default:

```kotlin
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_21)
  javaToolchainVersion.set(JavaVersion.VERSION_27)
}
```

This renames the misleading `maxJavaVersionSupported` convention property without changing toolchain selection or fallback behavior. `minJavaVersionSupported` continues to control bytecode compatibility.

- Fixes #20187
