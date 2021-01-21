package io.opentelemetry.instrumentation.api.tracer.binding;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.opentelemetry.api.common.AttributeKey;

public class AttributeBinder {

    public static AttributeBinding bind(String name, Type type) {

        if (type == String.class) {
            return (builder, arg) -> builder.setAttribute(name, (String) arg);
        }
        if (type == int.class || type == Integer.class) {
            return (builder, arg) -> builder.setAttribute(name, (Integer) arg);
        }
        if (type == long.class || type == Long.class) {
            return (builder, arg) -> builder.setAttribute(name, (Long) arg);
        }
        if (type == boolean.class || type == Boolean.class) {
            return (builder, arg) -> builder.setAttribute(name, (Boolean) arg);
        }
        if (type == double.class || type == Double.class) {
            return (builder, arg) -> builder.setAttribute(name, (Double) arg);
        }
        if (type == float.class || type == Float.class) {
            return (builder, arg) -> builder.setAttribute(name, (Float) arg);
        }
        if (type == String[].class) {
            AttributeKey<List<String>> key = AttributeKey.stringArrayKey(name);
            return (builder, arg) -> builder.setAttribute(key, Arrays.asList((String[]) arg));
        }
        if (type == Long[].class) {
            AttributeKey<List<Long>> key = AttributeKey.longArrayKey(name);
            return (builder, arg) -> builder.setAttribute(key, Arrays.asList((Long[]) arg));
        }
        if (type == Boolean[].class) {
            AttributeKey<List<Boolean>> key = AttributeKey.booleanArrayKey(name);
            return (builder, arg) -> builder.setAttribute(key, Arrays.asList((Boolean[]) arg));
        }
        if (type == Double[].class) {
            AttributeKey<List<Double>> key = AttributeKey.doubleArrayKey(name);
            return (builder, arg) -> builder.setAttribute(key, Arrays.asList((Double[]) arg));
        }
        if (type == Integer[].class) {
            return boxedIntegerArrayConverter(name);
        }
        if (type == Float[].class) {
            return boxedFloatArrayConverter(name);
        }
        if (type == int[].class) {
            return intArrayConverter(name);
        }
        if (type == float[].class) {
            return floatArrayConverter(name);
        }
        if (type == double[].class) {
            return doubleArrayConverter(name);
        }
        if (type == long[].class) {
            return longArrayConverter(name);
        }
        if (type == boolean[].class) {
            return booleanArrayConverter(name);
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type rawClass = parameterizedType.getRawType();

            if (rawClass == List.class || rawClass == Set.class || rawClass == Collection.class || rawClass == EnumSet.class) {
                Type componentType = parameterizedType.getActualTypeArguments()[0];
                return collectionConverter(name, componentType);
            }
        }
        if (type == Object.class) {
            return objectConverter(name);
        }

        return defaultConverter(name);
    }

    @SuppressWarnings("unchecked")
    private static AttributeBinding collectionConverter(String name, Type componentType) {
        if (componentType == String.class) {
            return collectionConverter(AttributeKey.stringArrayKey(name), String.class);
        }
        if (componentType == Long.class) {
            return collectionConverter(AttributeKey.longArrayKey(name), Long.class);
        }
        if (componentType == Integer.class) {
            return collectionConverter(AttributeKey.longArrayKey(name), Integer.class, Integer::longValue);
        }
        if (componentType == Double.class) {
            return collectionConverter(AttributeKey.doubleArrayKey(name), Double.class);
        }
        if (componentType == Float.class) {
            return collectionConverter(AttributeKey.doubleArrayKey(name), Float.class, Float::doubleValue);
        }
        if (componentType instanceof Class) {
            Class<?> componentClass = (Class<?>) componentType;
            if (componentClass.isEnum()) {
                return collectionConverter(AttributeKey.stringArrayKey(name), Enum.class, Enum::toString);
            }
        }
        return AttributeBinder.objectCollectionConverter(name);
    }

    private static <T> AttributeBinding collectionConverter(AttributeKey<List<T>> attributeKey, Class<T> componentType) {

        return collectionConverter(attributeKey, componentType, Function.identity());
    }

    private static <T, A> AttributeBinding collectionConverter(
            AttributeKey<List<A>> attributeKey,
            Class<T> componentType,
            Function<T, A> mapping) {

        return (builder, arg) -> {
            if (arg == null) {
                return builder;
            }
            @SuppressWarnings("unchecked")
            Collection<T> collection = (Collection<T>) arg;
            List<A> list = collection.stream()
                    .map(mapping)
                    .collect(Collectors.toList());
            return builder.setAttribute(attributeKey, list);
        };
    }

    private static AttributeBinding objectConverter(String name) {
        return (builder, arg) -> {
            if (arg instanceof String) {
                return builder.setAttribute(name, (String) arg);
            } else if (arg instanceof Long) {
                return builder.setAttribute(name, (Long) arg);
            } else if (arg instanceof Integer) {
                return builder.setAttribute(name, (Integer) arg);
            } else if (arg instanceof Boolean) {
                return builder.setAttribute(name, (Boolean) arg);
            } else if (arg instanceof Double) {
                return builder.setAttribute(name, (Double) arg);
            } else if (arg instanceof Float) {
                return builder.setAttribute(name, (Float) arg);
            } else if (arg instanceof String[]) {
                return builder.setAttribute(AttributeKey.stringArrayKey(name), Arrays.asList((String[]) arg));
            } else if (arg instanceof Long[]) {
                return builder.setAttribute(AttributeKey.longArrayKey(name), Arrays.asList((Long[]) arg));
            } else if (arg instanceof Boolean[]) {
                return builder.setAttribute(AttributeKey.booleanArrayKey(name), Arrays.asList((Boolean[]) arg));
            } else if (arg instanceof Double[]) {
                return builder.setAttribute(AttributeKey.doubleArrayKey(name), Arrays.asList((Double[]) arg));
            } else if (arg instanceof long[]) {
                return longArrayConverter(name).apply(builder, arg);
            } else if (arg instanceof int[]) {
                return intArrayConverter(name).apply(builder, arg);
            } else if (arg instanceof Integer[]) {
                return boxedIntegerArrayConverter(name).apply(builder, arg);
            } else if (arg instanceof boolean[]) {
                return booleanArrayConverter(name).apply(builder, arg);
            } else if (arg instanceof double[]) {
                return doubleArrayConverter(name).apply(builder, arg);
            } else if (arg instanceof float[]) {
                return floatArrayConverter(name).apply(builder, arg);
            } else if (arg instanceof Float[]) {
                return boxedFloatArrayConverter(name).apply(builder, arg);
            } else if (arg instanceof Collection<?>) {
                return objectCollectionConverter(name).apply(builder, arg);
            }
            return defaultConverter(name).apply(builder, arg);
        };
    }

    private static AttributeBinding defaultConverter(String name) {
        return (builder, arg) -> builder.setAttribute(name, arg.toString());
    }

    private static AttributeBinding intArrayConverter(String name) {
        AttributeKey<List<Long>> key = AttributeKey.longArrayKey(name);
        return (builder, arg) -> {
            int[] array = (int[]) arg;
            List<Long> list = new ArrayList<>(array.length);
            for (int value : array) {
                list.add((long) value);
            }

            return builder.setAttribute(key, list);
        };
    }

    private static AttributeBinding boxedIntegerArrayConverter(String name) {
        AttributeKey<List<Long>> key = AttributeKey.longArrayKey(name);
        return (builder, arg) -> {
            Integer[] array = (Integer[]) arg;
            List<Long> list = new ArrayList<>(array.length);
            for (Integer value : array) {
                if (value != null) {
                    list.add(value.longValue());
                } else {
                    list.add(null);
                }
            }

            return builder.setAttribute(key, list);
        };
    }

    private static AttributeBinding longArrayConverter(String name) {
        AttributeKey<List<Long>> key = AttributeKey.longArrayKey(name);
        return (builder, arg) -> {
            long[] array = (long[]) arg;
            List<Long> list = new ArrayList<>(array.length);
            for (long value : array) {
                list.add(value);
            }

            return builder.setAttribute(key, list);
        };
    }

    private static AttributeBinding doubleArrayConverter(String name) {
        AttributeKey<List<Double>> key = AttributeKey.doubleArrayKey(name);
        return (builder, arg) -> {
            double[] array = (double[]) arg;
            List<Double> list = new ArrayList<>(array.length);
            for (double value : array) {
                list.add(value);
            }

            return builder.setAttribute(key, list);
        };
    }

    private static AttributeBinding booleanArrayConverter(String name) {
        AttributeKey<List<Boolean>> key = AttributeKey.booleanArrayKey(name);
        return (builder, arg) -> {
            boolean[] array = (boolean[]) arg;
            List<Boolean> list = new ArrayList<>(array.length);
            for (boolean value : array) {
                list.add(value);
            }

            return builder.setAttribute(key, list);
        };
    }

    private static AttributeBinding floatArrayConverter(String name) {
        AttributeKey<List<Double>> key = AttributeKey.doubleArrayKey(name);
        return (builder, arg) -> {
            float[] array = (float[]) arg;
            List<Double> list = new ArrayList<>(array.length);
            for (float value : array) {
                list.add((double) value);
            }

            return builder.setAttribute(key, list);
        };
    }

    private static AttributeBinding boxedFloatArrayConverter(String name) {
        AttributeKey<List<Double>> key = AttributeKey.doubleArrayKey(name);
        return (builder, arg) -> {
            Float[] array = (Float[]) arg;
            List<Double> list = new ArrayList<>(array.length);
            for (Float value : array) {
                if (value != null) {
                    list.add(value.doubleValue());
                } else {
                    list.add(null);
                }
            }

            return builder.setAttribute(key, list);
        };
    }

    private static AttributeBinding objectCollectionConverter(String name) {
        AttributeKey<List<String>> key = AttributeKey.stringArrayKey(name);
        return (builder, arg) -> {
            Collection<?> collection = (Collection<?>) arg;
            List<String> list = new ArrayList<>(collection.size());
            for (Object value : collection) {
                if (value != null) {
                    list.add(value.toString());
                } else {
                    list.add(null);
                }
            }

            return builder.setAttribute(key, list);
        };
    }
}
