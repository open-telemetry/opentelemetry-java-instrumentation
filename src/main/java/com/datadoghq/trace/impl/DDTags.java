package com.datadoghq.trace.impl;

import io.opentracing.tag.StringTag;

public class DDTags {
    public static final StringTag RESOURCE = new StringTag("resource");
    public static final StringTag SERVICE = new StringTag("service");
}
