package com.lowdragmc.kilagraph.blueprint.nodes.mc.gameplay;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Reading and building the {@code potion_contents} of an item stack.
 *
 * <h2>No world input, unlike the enchantment nodes next door</h2>
 * {@link EnchantmentNodes} takes a {@code Level} on almost every port because enchantments became a
 * datapack registry in 1.21. Potions and mob effects did not — they are still {@link BuiltInRegistries}
 * entries, so an id resolves from the static table and these nodes stay world-free. The two files sit
 * side by side precisely so the difference is visible rather than looking like an inconsistency.
 *
 * <h2>A potion is a component, not an item</h2>
 * There is no "swiftness potion" item; there is {@code minecraft:potion} carrying a
 * {@link PotionContents} component. That is why {@code mc_make_potion} takes the item separately: the same
 * potion id put on {@code splash_potion}, {@code lingering_potion} or {@code tipped_arrow} produces the
 * three other forms, and a node per form would be three nodes saying one thing.
 *
 * <p>No validity checking on which item receives the component, matching {@code mc_add_enchantment} and
 * matching {@code /give} — the game itself will happily hand you a potion-flavoured stick.
 */
public final class PotionNodes {

    private static final String GROUP = "mc/gameplay";

    private PotionNodes() {
    }

    /**
     * Every effect a potion stack would apply, as three parallel lists.
     *
     * <p>Reads {@code getAllEffects()}, which is the base potion's effects followed by any custom ones, so
     * a graph sees what drinking it actually does rather than having to reassemble that from the two
     * halves.
     *
     * <p>Same shape as {@code mc_enchantments}: parallel lists rather than a map, because that is what
     * survives a For Each with an index. A duration of -1 means infinite, which is the game's own
     * encoding and is why this is not clamped to a positive number.</p>
     */
    @NodeAttribute(name = "mc_potion_effects", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Effects extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_potion_effects.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public List<?> ids;
        @OutputPort public List<?> durations;
        @OutputPort public List<?> amplifiers;
        @OutputPort public int count;

        @Override
        public void evaluate(EvalContext ctx) {
            List<ResourceLocation> ids = new ArrayList<>();
            List<Integer> durations = new ArrayList<>();
            List<Integer> amplifiers = new ArrayList<>();
            for (MobEffectInstance effect : PotionUtils.getMobEffects(stack(ctx))) {
                ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect());
                if (id == null) continue;
                ids.add(id);
                durations.add(effect.getDuration());
                amplifiers.add(effect.getAmplifier());
            }
            ctx.setOutput("ids", ids);
            ctx.setOutput("durations", durations);
            ctx.setOutput("amplifiers", amplifiers);
            ctx.setOutput("count", ids.size());
        }
    }

    /**
     * The base potion a stack is brewed from, e.g. {@code minecraft:long_swiftness}.
     *
     * <p>{@code found = false} for a stack with no potion component and for one that carries only custom
     * effects — both are "there is no named potion here", and the effects are on
     * {@code mc_potion_effects} either way.</p>
     */
    @NodeAttribute(name = "mc_potion_type", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Type extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_potion_type.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @OutputPort public ResourceLocation out;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack stack = stack(ctx);
            ResourceLocation id = stack.hasTag() && stack.getTag().contains(PotionUtils.TAG_POTION)
                    ? BuiltInRegistries.POTION.getKey(PotionUtils.getPotion(stack))
                    : null;
            ctx.setOutput("out", id);
            ctx.setOutput("found", id != null);
        }
    }

    /**
     * A single stack of a named potion.
     *
     * <p>{@code item} chooses the form — bottle, splash, lingering, tipped arrow — and defaults to the
     * drinkable bottle. An unknown potion id is {@code ok = false} with an empty stack rather than a plain
     * bottle, because a bottle of water is a plausible-looking wrong answer.</p>
     */
    @NodeAttribute(name = "mc_make_potion", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Make extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_make_potion.tooltip");
        }

        @InputPort public Item item = Items.POTION;
        @InputPort public ResourceLocation potion;
        @OutputPort public ItemStack out = ItemStack.EMPTY;
        @OutputPort public boolean ok;

        @Override
        public void evaluate(EvalContext ctx) {
            Item item = ctx.getInput("item", Item.class, Items.POTION);
            ResourceLocation id = ctx.getInput("potion", ResourceLocation.class, null);
            Potion potion = id != null && BuiltInRegistries.POTION.containsKey(id)
                    ? BuiltInRegistries.POTION.get(id) : null;
            if (item == null || potion == null) {
                ctx.setOutput("out", ItemStack.EMPTY);
                ctx.setOutput("ok", false);
                return;
            }
            ctx.setOutput("out", PotionUtils.setPotion(new ItemStack(item), potion));
            ctx.setOutput("ok", true);
        }
    }

    /**
     * A copy of the stack with one more custom effect on its potion component.
     *
     * <p>Custom effects stack on top of the base potion instead of replacing it, so this is how a graph
     * builds something no brewing stand can make. Applying it to a stack with no potion component gives it
     * one holding only this effect — which is exactly what {@code /give} with a bare {@code custom_effects}
     * produces.
     *
     * <p>A data node, like every other stack operation here: it returns a new stack and writes nothing.
     * {@code duration} of -1 means infinite, the game's own encoding; 0 is rejected, since an effect that
     * lasts no time is a mistake rather than a request.</p>
     */
    @NodeAttribute(name = "mc_add_custom_effect", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class AddCustomEffect extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_add_custom_effect.tooltip");
        }

        @InputPort public ItemStack stack = ItemStack.EMPTY;
        @InputPort public ResourceLocation effect;
        @InputPort public int duration = 200;
        @InputPort public int amplifier = 0;
        @OutputPort public ItemStack out = ItemStack.EMPTY;
        @OutputPort public boolean ok;

        @Override
        public void evaluate(EvalContext ctx) {
            ItemStack s = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
            ResourceLocation id = ctx.getInput("effect", ResourceLocation.class, null);
            int duration = ctx.getInt("duration", 200);
            int amplifier = ctx.getInt("amplifier", 0);
            MobEffect effect = id != null && BuiltInRegistries.MOB_EFFECT.containsKey(id)
                    ? BuiltInRegistries.MOB_EFFECT.get(id) : null;
            if (s == null || s.isEmpty() || effect == null
                    || (duration <= 0 && duration != MobEffectInstance.INFINITE_DURATION)) {
                ctx.setOutput("out", s == null ? ItemStack.EMPTY : s);
                ctx.setOutput("ok", false);
                return;
            }
            // Copy first: the input stack may already have been read by another branch of this run.
            ItemStack copy = s.copy();
            List<MobEffectInstance> effects = new ArrayList<>(PotionUtils.getCustomEffects(copy));
            effects.add(new MobEffectInstance(effect, duration, Math.max(0, amplifier)));
            PotionUtils.setCustomEffects(copy, effects);
            ctx.setOutput("out", copy);
            ctx.setOutput("ok", true);
        }
    }

    private static ItemStack stack(EvalContext ctx) {
        ItemStack s = ctx.getInput("stack", ItemStack.class, ItemStack.EMPTY);
        return s == null ? ItemStack.EMPTY : s;
    }
}
