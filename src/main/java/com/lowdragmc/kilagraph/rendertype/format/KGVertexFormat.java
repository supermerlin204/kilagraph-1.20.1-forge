package com.lowdragmc.kilagraph.rendertype.format;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.logging.LogUtils;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

/**
 * Builds a real {@link VertexFormat} from an ordered list of {@link KGVertexElement} keys (a graph's
 * {@code Settings.vertexFormatElements()}). The attribute <b>name</b> the builder records is the
 * element's {@link KGVertexElement#attribName()}, which is exactly the name the generated GLSL declares
 * its {@code in} by — so the pipeline layout and the shader always agree.
 *
 * <p>Results are cached by key list, padded to Minecraft's required 4-byte vertex alignment, and — when
 * the composition matches a stock {@link DefaultVertexFormat} — that shared constant is returned instead
 * of a fresh-but-equal instance (so Minecraft's own immediate buffers/optimizations apply).</p>
 *
 * <p><b>Client only</b> — references {@code com.mojang.blaze3d}. Reached only from the render-thread
 * factory and editor preview, never from the dedicated server.</p>
 */
public final class KGVertexFormat {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<List<String>, VertexFormat> CACHE = new ConcurrentHashMap<>();

    /** Stock formats we prefer to reuse when a composition matches them exactly. */
    private static final List<VertexFormat> KNOWN = List.of(
            DefaultVertexFormat.BLOCK,
            DefaultVertexFormat.NEW_ENTITY,
            DefaultVertexFormat.PARTICLE,
            DefaultVertexFormat.POSITION,
            DefaultVertexFormat.POSITION_COLOR,
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            DefaultVertexFormat.POSITION_TEX,
            DefaultVertexFormat.POSITION_TEX_COLOR,
            DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP,
            DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL);
            // (No line-width formats: 1.20.1 has no LineWidth vertex element — line width is a render-state,
            // not a per-vertex attribute — so POSITION_COLOR_LINE_WIDTH / …_NORMAL_LINE_WIDTH don't exist here.)

    private KGVertexFormat() {}

    /** The (cached) {@link VertexFormat} for an ordered list of {@link KGVertexElement} keys. */
    public static VertexFormat of(List<String> elementKeys) {
        return CACHE.computeIfAbsent(List.copyOf(elementKeys), KGVertexFormat::build);
    }

    private static VertexFormat build(List<String> elementKeys) {
        ImmutableMap.Builder<String, VertexFormatElement> builder = ImmutableMap.builder();
        int size = 0;
        int added = 0;
        var seen = new HashSet<String>();
        for (String key : elementKeys) {
            KGVertexElement desc = KGVertexElements.get(key);
            if (desc == null) continue;
            // The builder keys elements by attribName (an ImmutableMap) — a duplicate would throw, so skip
            // any element already added (the editor can transiently offer the same element twice).
            if (!seen.add(desc.attribName())) continue;
            VertexFormatElement element = elementById(desc.mcElementId());
            if (element == null) continue;
            builder.put(desc.attribName(), element);
            size += element.getByteSize();
            added++;
        }
        if (added == 0) {
            LOGGER.warn("[KilaGraph] empty/unknown vertex format {} — falling back to ENTITY", elementKeys);
            return DefaultVertexFormat.NEW_ENTITY;
        }
        int pad = (4 - (size % 4)) % 4; // Minecraft requires the vertex size to be a multiple of 4
        if (pad > 0) builder.put("Padding", DefaultVertexFormat.ELEMENT_PADDING);

        VertexFormat built;
        try {
            built = new VertexFormat(builder.build());
        } catch (RuntimeException e) {
            LOGGER.warn("[KilaGraph] invalid vertex format {} ({}) — falling back to ENTITY", elementKeys, e.getMessage());
            return DefaultVertexFormat.NEW_ENTITY;
        }
        for (VertexFormat known : KNOWN) {
            if (known.equals(built)) return known;
        }
        return built;
    }

    /** Resolve the stable built-in ids used by the graph format metadata on Minecraft 1.20.1. */
    public static VertexFormatElement elementById(int id) {
        return switch (id) {
            case 0 -> DefaultVertexFormat.ELEMENT_POSITION;
            case 1 -> DefaultVertexFormat.ELEMENT_COLOR;
            case 2 -> DefaultVertexFormat.ELEMENT_UV0;
            case 3 -> DefaultVertexFormat.ELEMENT_UV1;
            case 4 -> DefaultVertexFormat.ELEMENT_UV2;
            case 5 -> DefaultVertexFormat.ELEMENT_NORMAL;
            default -> null;
        };
    }

    /** Reverse of {@link #elementById(int)}; padding and unknown custom elements return {@code -1}. */
    public static int idOf(VertexFormatElement element) {
        for (int id = 0; id <= 5; id++) {
            if (element.equals(elementById(id))) return id;
        }
        return -1;
    }
}
