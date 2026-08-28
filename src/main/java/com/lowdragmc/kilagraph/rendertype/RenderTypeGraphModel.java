package com.lowdragmc.kilagraph.rendertype;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.ChangeHint;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.GraphElementModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link RenderTypeGraph}'s graph model. Inherits the shader vec-assign rule from
 * {@link ShaderGraphModelBase}, and redirects inline subgraph creation to {@link ShaderFunctionGraph}
 * so "create subgraph from selection" yields a pure shader-function graph (no fixed stages / entity
 * shader init) instead of cloning the whole RenderTypeGraph.
 */
public class RenderTypeGraphModel extends ShaderGraphModelBase {
    public static final String SETTINGS_NBT_KEY = "kilagraphSettings";

    public RenderTypeGraphModel(Graph graph) {
        super(graph);
    }

    /**
     * No-arg subgraph creation (used by {@code extractSelectionToLocalSubgraph} and the editor's
     * "create subgraph from selection") produces a {@link ShaderFunctionGraph}. Accepted because
     * {@link RenderTypeGraph#acceptsSubgraphGraph} opts into it.
     */
    @Override
    public CustomGraphModelImpl createLocalSubgraphInstance() {
        return createLocalSubgraphInstance(ShaderFunctionGraph.class);
    }

    /**
     * Every {@code deserialize} rebuilds the node models into fresh instances — including undo/redo,
     * which round-trip the whole model through NBT ({@code UndoableGraphCommand}) directly on this model
     * (not via the resource load that calls {@link RenderTypeGraph#restoreFixedStagesAfterDeserialize()}).
     * Re-resolve the graph's cached fixed vertex/fragment stage references here, the single point all
     * deserialize paths funnel through, so the whole-graph compiler walks the live stages. Otherwise it
     * keeps the old, now-orphaned stage refs and emits an empty (fully transparent) shader — e.g. the
     * graph-tool preview going blank after deleting a node and undoing.
     */
    @Override
    public void afterDeserialize() {
        super.afterDeserialize();
        if (getGraph() instanceof RenderTypeGraph renderTypeGraph) {
            renderTypeGraph.restoreFixedStagesAfterDeserialize();
        }
    }

    // --- Preview geometry persistence -------------------------------------------------------------
    // The chosen preview shape (KGPreviewContents key) for each node thumbnail and for the graph-tool
    // preview panel lives in this model's NBT so it survives both reopen and undo/redo (which round-trip
    // exactly this model — see the afterDeserialize note above). The runtime UI elements (NodeShaderPreview,
    // ShaderPreviewTool) are the ephemeral mirror; this map/string is the persisted source of truth,
    // mirroring how AbstractNodeModel.previewExpanded backs the runtime NodePreviewModel.

    /** Node UID → chosen {@link com.lowdragmc.kilagraph.rendertype.preview.KGPreviewContent} key. */
    private final Map<UUID, String> nodePreviewContentKeys = new HashMap<>();
    /** Chosen preview-content key for the graph-tool preview panel (null = its default). */
    @Nullable
    private String previewToolContentKey;

    @Nullable
    public String getNodePreviewContentKey(UUID nodeUid) {
        return nodePreviewContentKeys.get(nodeUid);
    }

    /**
     * Records a node thumbnail's chosen preview geometry. Registers a {@link ChangeHint#DATA} change for
     * the owning node so the choice is part of undo history and marks the graph dirty (mirrors
     * {@code AbstractNodeModel.setPreviewExpanded}). Entries are never pruned on node delete — undo
     * restores the node with the same UID and a surviving entry brings its shape back.
     */
    public void setNodePreviewContentKey(UUID nodeUid, @Nullable String key) {
        if (key == null) {
            if (nodePreviewContentKeys.remove(nodeUid) == null) return;
        } else if (key.equals(nodePreviewContentKeys.put(nodeUid, key))) {
            return; // unchanged
        }
        GraphElementModel node = getModel(nodeUid);
        if (node != null) {
            getCurrentGraphChangeDescription().addChangedModel(node, ChangeHint.DATA);
        }
    }

    @Nullable
    public String getPreviewToolContentKey() {
        return previewToolContentKey;
    }

    public void setPreviewToolContentKey(@Nullable String key) {
        if (Objects.equals(previewToolContentKey, key)) return;
        previewToolContentKey = key;
        setGraphObjectDirty();
    }

    @Override
    public Tag serializeAdditionalNBT(HolderLookup.Provider provider) {
        var tag = (CompoundTag) super.serializeAdditionalNBT(provider);
        if (getGraph() instanceof RenderTypeGraph renderTypeGraph) {
            tag.put(SETTINGS_NBT_KEY, serializeSettings(renderTypeGraph.getSettings()));
        }
        if (!nodePreviewContentKeys.isEmpty()) {
            var previews = new CompoundTag();
            for (var entry : nodePreviewContentKeys.entrySet()) {
                if (entry.getValue() != null) {
                    previews.putString(entry.getKey().toString(), entry.getValue());
                }
            }
            if (!previews.isEmpty()) tag.put("nodePreviewContents", previews);
        }
        if (previewToolContentKey != null) {
            tag.putString("previewToolContent", previewToolContentKey);
        }
        return tag;
    }

    @Override
    public void deserializeAdditionalNBT(Tag tag, HolderLookup.Provider provider) {
        super.deserializeAdditionalNBT(tag, provider);
        // Clear first so a deserialize replaces (never merges) the persisted shapes.
        nodePreviewContentKeys.clear();
        previewToolContentKey = null;
        if (!(tag instanceof CompoundTag compound)) return;
        if (compound.get(SETTINGS_NBT_KEY) instanceof CompoundTag settingsTag
                && getGraph() instanceof RenderTypeGraph renderTypeGraph) {
            renderTypeGraph.setSettings(deserializeSettings(settingsTag));
        }
        if (compound.contains("nodePreviewContents")) {
            var previews = compound.getCompound("nodePreviewContents");
            for (var key : previews.getAllKeys()) {
                var value = previews.getString(key);
                if (value.isEmpty()) continue;
                try {
                    nodePreviewContentKeys.put(UUID.fromString(key), value);
                } catch (IllegalArgumentException ignored) {
                    // skip a malformed/legacy key rather than failing the whole deserialize
                }
            }
        }
        if (compound.contains("previewToolContent")) {
            var value = compound.getString("previewToolContent");
            previewToolContentKey = value.isEmpty() ? null : value;
        }
    }

    public static CompoundTag serializeSettings(RenderTypeGraph.Settings settings) {
        var tag = new CompoundTag();
        tag.putString("vertexFormatElements", String.join(",", settings.vertexFormatElements()));
        tag.putString("vertexFormatMode", settings.vertexFormatMode().name());
        tag.putString("blend", settings.blend().name());
        tag.putString("depthTest", settings.depthTest().name());
        tag.putBoolean("depthWrite", settings.depthWrite());
        tag.putBoolean("cull", settings.cull());
        tag.putString("outputTarget", settings.outputTarget().name());
        tag.putBoolean("affectsOutline", settings.affectsOutline());
        tag.putBoolean("sortOnUpload", settings.sortOnUpload());
        return tag;
    }

    public static RenderTypeGraph.Settings deserializeSettings(CompoundTag tag) {
        var defaults = RenderTypeGraph.Settings.defaults();
        return new RenderTypeGraph.Settings(
                readVertexFormatElements(tag, defaults.vertexFormatElements()),
                readEnum(tag, "vertexFormatMode", RenderTypeGraph.Settings.VertexFormatMode.class,
                        defaults.vertexFormatMode()),
                readEnum(tag, "blend", RenderTypeGraph.Settings.BlendMode.class, defaults.blend()),
                readEnum(tag, "depthTest", RenderTypeGraph.Settings.DepthTest.class, defaults.depthTest()),
                readBool(tag, "depthWrite", defaults.depthWrite()),
                readBool(tag, "cull", defaults.cull()),
                readEnum(tag, "outputTarget", RenderTypeGraph.Settings.OutputTarget.class, defaults.outputTarget()),
                readBool(tag, "affectsOutline", defaults.affectsOutline()),
                readBool(tag, "sortOnUpload", defaults.sortOnUpload())
        );
    }

    private static List<String> readVertexFormatElements(CompoundTag tag, List<String> fallback) {
        var joined = tag.getString("vertexFormatElements");
        if (!joined.isBlank()) {
            var keys = new ArrayList<String>();
            for (var part : joined.split(",")) {
                if (!part.isBlank()) keys.add(part.trim());
            }
            if (!keys.isEmpty()) return keys;
        }
        var legacy = tag.contains("vertexFormatPreset") ? tag.getString("vertexFormatPreset") : null;
        if (legacy != null) {
            return switch (legacy) {
                case "BLOCK" -> com.lowdragmc.kilagraph.rendertype.format.VertexFormatPresets.BLOCK;
                case "POSITION_COLOR_TEX" ->
                        com.lowdragmc.kilagraph.rendertype.format.VertexFormatPresets.POSITION_COLOR_TEX;
                default -> com.lowdragmc.kilagraph.rendertype.format.VertexFormatPresets.ENTITY;
            };
        }
        return fallback;
    }

    private static boolean readBool(CompoundTag tag, String key, boolean fallback) {
        return tag.contains(key) ? tag.getBoolean(key) : fallback;
    }

    private static <E extends Enum<E>> E readEnum(CompoundTag tag, String key, Class<E> enumClass, E fallback) {
        var name = tag.contains(key) ? tag.getString(key) : fallback.name();
        try {
            return Enum.valueOf(enumClass, name);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
