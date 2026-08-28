package com.lowdragmc.kilagraph.blueprint.nodes.mc.component;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * Compatibility form of the generic component nodes for Minecraft 1.20.1.
 *
 * <p>1.20.1 stores item and fluid metadata in NBT rather than the typed data-component map added in
 * 1.20.5. The public graph surface remains component-shaped, while this implementation maps common
 * vanilla component ids to their legacy NBT paths and stores unknown namespaced ids verbatim.</p>
 */
public final class DataComponentNodes {
    private static final String GROUP = "mc/component";
    public static final String VALUE_KEY = "value";

    private DataComponentNodes() {}

    @NodeAttribute(name = "mc_item_components", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class ItemComponents extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_components.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public List<?> out;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack stack = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            ctx.setOutput("out", stack == null ? List.of() : componentIds(stack.getTag()));
        }
    }

    @NodeAttribute(name = "mc_fluid_components", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class FluidComponents extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_components.tooltip");
        }

        @InputPort public FluidStack stack = FluidStack.EMPTY;
        @OutputPort public List<?> out;

        @Override
        public void evaluate(EvalContext ctx) {
            FluidStack stack = ctx.getInput("stack", FluidStack.class, FluidStack.EMPTY);
            ctx.setOutput("out", stack == null || stack.isEmpty() ? List.of() : componentIds(stack.getTag()));
        }
    }

    @NodeAttribute(name = "mc_item_has_component", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class HasComponent extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_has_component.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @InputPort public ResourceLocation component;
        @OutputPort public boolean out;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack stack = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            ResourceLocation id = component(ctx);
            String path = path(id);
            ctx.setOutput("out", stack != null && path != null && read(stack.getTag(), path) != null);
        }
    }

    @NodeAttribute(name = "mc_item_get_component", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class GetComponent extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_get_component.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @InputPort public ResourceLocation component;
        @OutputPort public CompoundTag nbt;
        @OutputPort public String text;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack stack = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            ResourceLocation id = component(ctx);
            String path = path(id);
            Tag value = stack == null || path == null ? null : read(stack.getTag(), path);
            ctx.setOutput("nbt", value == null ? new CompoundTag() : wrap(value.copy()));
            ctx.setOutput("text", value == null ? "" : value.toString());
            ctx.setOutput("found", value != null);
        }
    }

    @NodeAttribute(name = "mc_item_set_component", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class SetComponent extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_set_component.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @InputPort public ResourceLocation component;
        @InputPort public CompoundTag nbt;
        @OutputPort public ItemStack out;
        @OutputPort public boolean ok;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack stack = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            if (stack == null) stack = ItemStack.EMPTY;
            ResourceLocation id = component(ctx);
            String path = path(id);
            CompoundTag value = ctx.getInput("nbt", CompoundTag.class, null);
            if (path == null || value == null || stack.isEmpty()) {
                ctx.setOutput("out", stack);
                ctx.setOutput("ok", false);
                return;
            }
            ItemStack copy = stack.copy();
            write(copy.getOrCreateTag(), path, unwrap(value).copy());
            ctx.setOutput("out", copy);
            ctx.setOutput("ok", true);
        }
    }

    @NodeAttribute(name = "mc_item_remove_component", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class RemoveComponent extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_item_remove_component.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @InputPort public ResourceLocation component;
        @OutputPort public ItemStack out;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack stack = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            if (stack == null) stack = ItemStack.EMPTY;
            ResourceLocation id = component(ctx);
            String path = path(id);
            if (path == null || stack.isEmpty()) {
                ctx.setOutput("out", stack);
                return;
            }
            ItemStack copy = stack.copy();
            remove(copy.getTag(), path);
            // Forge 1.20.1 recreates Damage:0 when setTag(null) is called on a damageable item.
            // Keep an empty tag here so an explicitly removed damage component remains absent.
            if (!"Damage".equals(path) && copy.getTag() != null && copy.getTag().isEmpty()) {
                copy.setTag(null);
            }
            ctx.setOutput("out", copy);
        }
    }

    private static ResourceLocation component(EvalContext ctx) {
        return ctx.getInput("component", ResourceLocation.class, null);
    }

    private static String path(ResourceLocation id) {
        if (id == null || !"minecraft".equals(id.getNamespace())) return null;
        return switch (id.getPath()) {
            case "damage" -> "Damage";
            case "custom_model_data" -> "CustomModelData";
            case "repair_cost" -> "RepairCost";
            case "unbreakable" -> "Unbreakable";
            case "enchantments" -> "Enchantments";
            case "stored_enchantments" -> "StoredEnchantments";
            case "attribute_modifiers" -> "AttributeModifiers";
            case "can_break" -> "CanDestroy";
            case "can_place_on" -> "CanPlaceOn";
            case "custom_name" -> "display/Name";
            case "lore" -> "display/Lore";
            default -> null;
        };
    }

    private static List<ResourceLocation> componentIds(CompoundTag root) {
        if (root == null) return List.of();
        List<ResourceLocation> ids = new ArrayList<>();
        for (String key : root.getAllKeys()) {
            if ("display".equals(key) && root.get("display") instanceof CompoundTag display) {
                if (display.contains("Name")) ids.add(new ResourceLocation("minecraft", "custom_name"));
                if (display.contains("Lore")) ids.add(new ResourceLocation("minecraft", "lore"));
                continue;
            }
            ids.add(idForKey(key));
        }
        return ids;
    }

    private static ResourceLocation idForKey(String key) {
        return switch (key) {
            case "Damage" -> new ResourceLocation("minecraft", "damage");
            case "CustomModelData" -> new ResourceLocation("minecraft", "custom_model_data");
            case "RepairCost" -> new ResourceLocation("minecraft", "repair_cost");
            case "Unbreakable" -> new ResourceLocation("minecraft", "unbreakable");
            case "Enchantments" -> new ResourceLocation("minecraft", "enchantments");
            case "StoredEnchantments" -> new ResourceLocation("minecraft", "stored_enchantments");
            case "AttributeModifiers" -> new ResourceLocation("minecraft", "attribute_modifiers");
            case "CanDestroy" -> new ResourceLocation("minecraft", "can_break");
            case "CanPlaceOn" -> new ResourceLocation("minecraft", "can_place_on");
            default -> ResourceLocation.tryParse(key) != null
                    ? ResourceLocation.tryParse(key)
                    : new ResourceLocation("minecraft", key.toLowerCase());
        };
    }

    private static Tag read(CompoundTag root, String path) {
        if (root == null) return null;
        int slash = path.indexOf('/');
        if (slash < 0) return root.get(path);
        Tag parent = root.get(path.substring(0, slash));
        return parent instanceof CompoundTag compound ? compound.get(path.substring(slash + 1)) : null;
    }

    private static void write(CompoundTag root, String path, Tag value) {
        int slash = path.indexOf('/');
        if (slash < 0) {
            root.put(path, value);
            return;
        }
        String parentKey = path.substring(0, slash);
        CompoundTag parent = root.get(parentKey) instanceof CompoundTag compound ? compound : new CompoundTag();
        parent.put(path.substring(slash + 1), value);
        root.put(parentKey, parent);
    }

    private static void remove(CompoundTag root, String path) {
        if (root == null) return;
        int slash = path.indexOf('/');
        if (slash < 0) {
            root.remove(path);
            return;
        }
        String parentKey = path.substring(0, slash);
        if (root.get(parentKey) instanceof CompoundTag parent) {
            parent.remove(path.substring(slash + 1));
            if (parent.isEmpty()) root.remove(parentKey);
        }
    }

    private static CompoundTag wrap(Tag tag) {
        if (tag instanceof CompoundTag compound) return compound;
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(VALUE_KEY, tag);
        return wrapper;
    }

    private static Tag unwrap(CompoundTag tag) {
        if (tag.size() == 1 && tag.contains(VALUE_KEY)) {
            Tag value = tag.get(VALUE_KEY);
            if (value != null) return value;
        }
        return tag;
    }
}
