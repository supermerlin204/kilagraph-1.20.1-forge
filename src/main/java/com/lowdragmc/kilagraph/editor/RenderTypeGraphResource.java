package com.lowdragmc.kilagraph.editor;

import com.lowdragmc.kilagraph.rendertype.RenderTypeGraph;
import com.lowdragmc.kilagraph.rendertype.RenderTypeGraphModel;
import com.lowdragmc.kilagraph.rendertype.gui.RenderTypeGraphView;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.IResourceProvider;
import com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceProviderContainer;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.GraphResource;
import com.lowdragmc.lowdraglib2.nodegraphtookit.editor.IGraphReferenceResolver;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphView;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

public class RenderTypeGraphResource extends GraphResource<RenderTypeGraph> {
    private static final String GRAPH_TAG = "graph";
    private static final String SETTINGS_TAG = "settings";

    public static final RenderTypeGraphResource INSTANCE = new RenderTypeGraphResource();

    protected RenderTypeGraphResource() {}

    @Override
    public RenderTypeGraph createGraph() {
        return new RenderTypeGraph();
    }

    /** The stored form is the wrapped {@code {graph, settings}} tag — every consumer (editor
     *  container, cross-library resolvers, runtimes) goes through this pair. */
    public CompoundTag serializeGraphResource(RenderTypeGraph graph) {
        return serializeGraph(graph);
    }

    public RenderTypeGraph deserializeGraphResource(CompoundTag tag,
            @Nullable IGraphReferenceResolver resolver) {
        return deserializeGraph(tag, resolver);
    }

    @Override
    public Supplier<? extends GraphView> getGraphViewFactory() {
        return RenderTypeGraphView::new;
    }

    public CompoundTag serializeGraph(RenderTypeGraph graph) {
        // Settings are stored in RenderTypeGraphModel's native _additional payload on LDLib2 1.20.1.
        // 1.20.1: MC has no TagValueInput/Output (added in 1.21.5); LDLib2-1.21's graph model serializes
        // directly to NBT via serializeNBT(HolderLookup.Provider) — the canonical GraphView/undo pattern.
        return graph.graphModel.serializeNBT(Platform.getFrozenRegistry());
    }

    public RenderTypeGraph deserializeGraph(CompoundTag tag) {
        return deserializeGraph(tag, null);
    }

    /**
     * The empty (uninitialized) graph instance {@link #deserializeGraph} loads into. Subclass resources
     * override this so a load produces their graph type.
     */
    protected RenderTypeGraph newGraphForLoad() {
        return new RenderTypeGraph(false);
    }

    public RenderTypeGraph deserializeGraph(CompoundTag tag, @Nullable IGraphReferenceResolver resolver) {
        // Start from an empty graph (no default node network): deserialize below clears and reloads
        // nodeModels, so building the default graph here would be wasted work and would leave the model's
        // getNodes() cache primed with stale default nodes. restoreFixedStagesAfterDeserialize() re-ensures
        // the fixed stages after the load.
        var graph = newGraphForLoad();
        graph.graphModel.setReferenceResolver(resolver);
        var graphTag = tag.get(GRAPH_TAG) instanceof CompoundTag compound ? compound : tag;
        graph.graphModel.deserializeNBT(Platform.getFrozenRegistry(), graphTag);
        graph.graphModel.setReferenceResolver(resolver);
        if (tag.get(SETTINGS_TAG) instanceof CompoundTag settingsTag) {
            graph.setSettings(RenderTypeGraphModel.deserializeSettings(settingsTag));
        }
        graph.restoreFixedStagesAfterDeserialize();
        return graph;
    }

    /** Convert resources saved by the 1.21 editor's {graph, settings} wrapper to 1.20.1's native tag. */
    @Override
    public CompoundTag deserializeResource(Tag tag, HolderLookup.Provider provider) {
        CompoundTag root = super.deserializeResource(tag, provider);
        if (!(root.get(GRAPH_TAG) instanceof CompoundTag wrappedGraph)) return root;

        CompoundTag graphTag = wrappedGraph.copy();
        if (root.get(SETTINGS_TAG) instanceof CompoundTag settingsTag) {
            CompoundTag additional = graphTag.get("_additional") instanceof CompoundTag existing
                    ? existing.copy()
                    : new CompoundTag();
            additional.put(RenderTypeGraphModel.SETTINGS_NBT_KEY, settingsTag.copy());
            graphTag.put("_additional", additional);
        }
        return graphTag;
    }

    @Override
    public ResourceProviderContainer<CompoundTag> createResourceProviderContainer(IResourceProvider<CompoundTag> provider) {
        return new RenderTypeGraphResourceProviderContainer(this, provider);
    }

    @Override
    public IGuiTexture getIcon() {
        return Icons.WIDGET_CUSTOM;
    }

    @Override
    public String getName() {
        return "rendertype";
    }
}
