package com.lowdragmc.kilagraph.rendertype.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-only registry mapping a {@link VertexFormatElement}'s id to the call that fills it for a preview
 * vertex. {@link PreviewRenderer} writes Position (via {@code addVertex}) then, for every other element the
 * target format declares, invokes the matching writer — so a preview vertex always carries exactly the
 * attributes the buffer expects (no "Missing elements in vertex"). Seeded with the built-ins; a mod that
 * registers a custom {@link com.lowdragmc.kilagraph.rendertype.format.KGVertexElement} should also
 * {@link #register} a writer for it, or preview of a format containing it can't be completed.
 */
public final class PreviewVertexWriters {

    /** Fills one element of a preview vertex into the consumer. */
    @FunctionalInterface
    public interface Writer {
        void write(VertexConsumer vc, PoseStack.Pose pose, PreviewVertex v);
    }

    private static final Map<VertexFormatElement, Writer> BY_ELEMENT = new HashMap<>();

    static {
        register(DefaultVertexFormat.ELEMENT_COLOR, (vc, pose, v) -> vc.color(v.color));
        register(DefaultVertexFormat.ELEMENT_UV0, (vc, pose, v) -> vc.uv(v.u, v.v));
        register(DefaultVertexFormat.ELEMENT_UV1, (vc, pose, v) -> vc.overlayCoords(v.overlay));
        register(DefaultVertexFormat.ELEMENT_UV2, (vc, pose, v) -> vc.uv2(v.light));
        register(DefaultVertexFormat.ELEMENT_NORMAL,
                (vc, pose, v) -> vc.normal(pose.normal(), v.nx, v.ny, v.nz));
        // (No LINE_WIDTH writer: 1.20.1 has no LineWidth vertex element — line width is a render-state, not a
        // per-vertex attribute — so there is nothing to write.)
    }

    private PreviewVertexWriters() {}

    public static void register(VertexFormatElement element, Writer writer) {
        BY_ELEMENT.put(element, writer);
    }

    public static Writer get(VertexFormatElement element) {
        return BY_ELEMENT.get(element);
    }
}
