package com.lowdragmc.kilagraph.rendertype.gui;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.HDRColorConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;
import org.joml.Vector4f;

/** Adapts LDLib2 1.20.1's RGB-plus-intensity editor to a graph option. */
public final class HDRColorConfiguratorAdapter {
    private static final Vector4f DEFAULT = new Vector4f(1f, 1f, 1f, 1f);

    private HDRColorConfiguratorAdapter() {}

    public static IConfigurable build(IFieldValueConfigurable configurable) {
        return new HDRColorConfigurator(
                "",
                () -> copy(configurable.getValue()),
                value -> configurable.setValue(new Vector4f(value)),
                new Vector4f(DEFAULT),
                configurable.forceUpdate());
    }

    private static Vector4f copy(Object value) {
        return value instanceof Vector4f vector ? new Vector4f(vector) : new Vector4f(DEFAULT);
    }
}
