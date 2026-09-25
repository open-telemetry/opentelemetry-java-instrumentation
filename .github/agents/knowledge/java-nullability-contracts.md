# Java nullability and attribute overloads

The upstream OpenTelemetry SDK declares nullable carrier parameters on some
propagation interfaces. Those declarations are not visible in this repository,
so check the upstream contract when changing an implementation's annotations
or carrier handling:

| Interface                 | Method                          | Nullable parameter |
| ------------------------- | ------------------------------- | ------------------ |
| `TextMapGetter<CarrierT>` | `get(CarrierT, String)`         | `carrier`          |
| `TextMapGetter<CarrierT>` | `getAll(CarrierT, String)`      | `carrier`          |
| `TextMapGetter<CarrierT>` | `keys(CarrierT)`                | none               |
| `TextMapSetter<CarrierT>` | `set(CarrierT, String, String)` | `carrier`          |

A getter that directly delegates to another `TextMapGetter` can pass the nullable
carrier through. Its delegate has the same contract, so a second guard adds no
protection:

```java
@Override
@Nullable
public String get(@Nullable C carrier, String key) {
  return delegate.get(carrier, key);
}
```

For attribute setters, the overload selected by Java can change null behavior.
Passing an `Integer` to a setter with `AttributeKey<Long>` chooses the primitive
`int` overload and unboxes the argument *before* the setter runs:

```java
Integer statusCode = response.getStatusCode();
if (statusCode != null) {
  attributes.put(HTTP_RESPONSE_STATUS_CODE, statusCode);
}
```

In contrast, a boxed value whose type matches the key uses the generic nullable
overload, which accepts null without an explicit guard:

```java
span.setAttribute(META_ENABLED, metadata.getEnabled()); // AttributeKey<Boolean>
```

A guard that protects `view.getClass().getName()` protects a dereference, not
just the attribute setter. Keep that distinction when simplifying extraction.
