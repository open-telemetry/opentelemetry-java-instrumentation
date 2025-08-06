# Smoke Test Fake Backend

This module provides a fake backend service used in smoke tests.

## Docker Images

The fake backend is available as Docker images for both Linux (multi-arch) and Windows:

- Linux (AMD64 + ARM64): `ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-fake-backend:latest`
- Windows: `ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-fake-backend-windows:latest`

### Building Images

To build and push the images:

Ensure Docker BuildX is installed and configured for multi-arch builds:

```bash
docker buildx create --name multiarch --driver docker-container --use
```

Run the build:

```bash
./gradlew :smoke-tests:images:fake-backend:dockerPush
```

This will build and push:
- A multi-architecture Linux image supporting both AMD64 and ARM64
- A Windows-specific image
