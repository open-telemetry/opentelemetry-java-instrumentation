# Declarative Config Bridge

This module provides utilities for bridging between declarative configuration (YAML-based) and
the traditional `ConfigProperties` interface.

The main classes are:

- `ConfigPropertiesBackedConfigProvider`: Creates a `ConfigProvider` backed by `ConfigProperties`
- `ConfigPropertiesBackedDeclarativeConfigProperties`: Adapts `ConfigProperties` to `DeclarativeConfigProperties`
