package com.lowdragmc.kilagraph.blueprint.nodes.mc.container;

import com.lowdragmc.kilagraph.blueprint.BlueprintGraph;
import com.lowdragmc.kilagraph.blueprint.nodes.mc.action.McActions;
import com.lowdragmc.kilagraph.graph.core.AnnotatedNode;
import com.lowdragmc.kilagraph.graph.core.ExecInputPort;
import com.lowdragmc.kilagraph.graph.core.ExecOutputPort;
import com.lowdragmc.kilagraph.graph.core.InputPort;
import com.lowdragmc.kilagraph.graph.core.OutputPort;
import com.lowdragmc.kilagraph.graph.exec.EvalContext;
import com.lowdragmc.kilagraph.graph.exec.ExecContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles.ExecutionFlow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * Fluid tanks: finding one, reading it, and moving fluid in or out.
 *
 * <p>The same shape as {@code ContainerNodes} for the other thing blocks store, and for the same reason:
 * the capability interface is what every tank worth talking to speaks. Vanilla has no fluid-storage
 * abstraction at all — a cauldron is a block state, not a tank — so this is Forge's idea end to end
 * and works on modded machines exactly as it does on anything else that implements it.
 *
 * <h2>Fill and drain are the whole API</h2>
 * There is no "set tank 3 to this" here, deliberately. Fluid handlers are not addressable the way item
 * slots are: a tank decides for itself which of its internal tanks a fill goes to, and many are a single
 * logical tank with several compartments. Fill and drain are what the interface offers and what pipes
 * use, so they are what a graph gets.
 */
public final class FluidContainerNodes {

    private static final String GROUP = "mc/container";

    private FluidContainerNodes() {
    }

    // ---- resolving ---------------------------------------------------------------------------

    /** The fluid tank of a block, from a side. */
    @NodeAttribute(name = "mc_block_fluid_container", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class BlockFluidContainer extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_block_fluid_container.tooltip");
        }

        @InputPort public Level level;
        @InputPort public BlockPos pos = BlockPos.ZERO;
        @InputPort public Direction side = Direction.NORTH;
        @OutputPort public IFluidHandler out;
        @OutputPort public boolean found;

        @Override
        public void evaluate(EvalContext ctx) {
            Level world = ctx.getInput("level", Level.class, null);
            BlockPos at = ctx.getInput("pos", BlockPos.class, null);
            if (world == null || at == null) {
                ctx.setOutput("out", null);
                ctx.setOutput("found", false);
                return;
            }
            Direction from = ctx.getInput("side", Direction.class, Direction.NORTH);
            var blockEntity = world.getBlockEntity(at);
            IFluidHandler handler = blockEntity == null ? null
                    : blockEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, from).orElse(null);
            if (handler == null && VanillaCauldronFluidHandler.isCauldron(world.getBlockState(at))) {
                handler = new VanillaCauldronFluidHandler(world, at);
            }
            ctx.setOutput("out", handler);
            ctx.setOutput("found", handler != null);
        }
    }

    // ---- reading -----------------------------------------------------------------------------

    /** How many separate tanks the handler exposes. Zero when there is no handler. */
    @NodeAttribute(name = "mc_fluid_container_tanks", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Tanks extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_container_tanks.tooltip");
        }

        @InputPort public IFluidHandler container;
        @OutputPort public int tanks;

        @Override
        public void evaluate(EvalContext ctx) {
            IFluidHandler h = handler(ctx);
            ctx.setOutput("tanks", h == null ? 0 : h.getTanks());
        }
    }

    /**
     * What is in one tank, and how much it could hold.
     *
     * <p>Capacity comes out alongside the contents because the useful quantity is almost always the
     * ratio — a tank readout, a comparator signal, a decision about whether to keep filling — and asking
     * for the two halves separately would mean resolving the same tank twice.</p>
     */
    @NodeAttribute(name = "mc_fluid_container_get", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Get extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_container_get.tooltip");
        }

        @InputPort public IFluidHandler container;
        @InputPort public int tank = 0;
        @OutputPort public FluidStack out = FluidStack.EMPTY;
        @OutputPort public int capacity;
        @OutputPort public boolean empty;

        @Override
        public void evaluate(EvalContext ctx) {
            IFluidHandler h = handler(ctx);
            int tank = ctx.getInt("tank", 0);
            if (h == null || tank < 0 || tank >= h.getTanks()) {
                ctx.setOutput("out", FluidStack.EMPTY);
                ctx.setOutput("capacity", 0);
                ctx.setOutput("empty", true);
                return;
            }
            // Copy: the handler's stack must not be modified by whoever receives it downstream.
            FluidStack in = h.getFluidInTank(tank).copy();
            ctx.setOutput("out", in);
            ctx.setOutput("capacity", h.getTankCapacity(tank));
            ctx.setOutput("empty", in.isEmpty());
        }
    }

    // ---- moving fluid ------------------------------------------------------------------------

    /**
     * Puts fluid into a tank.
     *
     * <p>Reports how much was actually accepted, which may be less than offered — tanks fill partially
     * all the time. Simulate answers "would this fit" without changing anything, and is the way to get
     * all-or-nothing behaviour: a fill that only half fits has already moved half.</p>
     */
    @NodeAttribute(name = "mc_fluid_container_fill", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Fill extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_container_fill.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public IFluidHandler container;
        @InputPort public FluidStack fluid = FluidStack.EMPTY;
        @InputPort public boolean simulate = false;
        @OutputPort public int filled;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            IFluidHandler h = ctx.getInput("container", IFluidHandler.class, null);
            FluidStack give = ctx.getInput("fluid", FluidStack.class, FluidStack.EMPTY);
            if (h == null || give == null || give.isEmpty()) {
                ctx.setOutput("filled", 0);
                McActions.done(ctx, false);
                return;
            }
            int moved = h.fill(give, action(ctx));
            ctx.setOutput("filled", moved);
            McActions.done(ctx, moved > 0);
        }
    }

    /**
     * Takes fluid out of a tank.
     *
     * <p>Drains whatever the tank offers up to {@code amount}, which is how pipes work — a graph asking
     * for a bucket's worth from a tank holding half of one gets half, and is told so by the returned
     * stack rather than by a failure.</p>
     */
    @NodeAttribute(name = "mc_fluid_container_drain", group = GROUP, graphTypes = BlueprintGraph.class)
    public static class Drain extends AnnotatedNode {
        @Override
        protected Component getNodeTooltip() {
            return Component.translatable("kg.node.mc_fluid_container_drain.tooltip");
        }

        @ExecInputPort public ExecutionFlow trigger;
        @ExecOutputPort public ExecutionFlow next;

        @InputPort public IFluidHandler container;
        @InputPort public int amount = 1000;
        @InputPort public boolean simulate = false;
        @OutputPort public FluidStack out = FluidStack.EMPTY;
        @OutputPort public boolean ok;

        @Override
        public void execute(ExecContext ctx) {
            IFluidHandler h = ctx.getInput("container", IFluidHandler.class, null);
            int amount = ctx.getInt("amount", 1000);
            if (h == null || amount <= 0) {
                ctx.setOutput("out", FluidStack.EMPTY);
                McActions.done(ctx, false);
                return;
            }
            FluidStack taken = h.drain(amount, action(ctx));
            ctx.setOutput("out", taken);
            McActions.done(ctx, !taken.isEmpty());
        }
    }

    private static IFluidHandler.FluidAction action(ExecContext ctx) {
        return ctx.getBool("simulate", false)
                ? IFluidHandler.FluidAction.SIMULATE
                : IFluidHandler.FluidAction.EXECUTE;
    }

    private static IFluidHandler handler(EvalContext ctx) {
        return ctx.getInput("container", IFluidHandler.class, null);
    }

    /** Forge 1.20.1 has no block capability for vanilla cauldrons; emulate NeoForge's wrapper. */
    private static final class VanillaCauldronFluidHandler implements IFluidHandler {
        private static final Content EMPTY = new Content(Blocks.CAULDRON, Fluids.EMPTY,
                FluidType.BUCKET_VOLUME, 1, null);
        private static final Content WATER = new Content(Blocks.WATER_CAULDRON, Fluids.WATER,
                FluidType.BUCKET_VOLUME, 3, LayeredCauldronBlock.LEVEL);
        private static final Content LAVA = new Content(Blocks.LAVA_CAULDRON, Fluids.LAVA,
                FluidType.BUCKET_VOLUME, 1, null);

        private final Level level;
        private final BlockPos pos;

        private VanillaCauldronFluidHandler(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        private static boolean isCauldron(BlockState state) {
            return contentForBlock(state.getBlock()) != null;
        }

        private static Content contentForBlock(Block block) {
            if (block == Blocks.CAULDRON) return EMPTY;
            if (block == Blocks.WATER_CAULDRON) return WATER;
            if (block == Blocks.LAVA_CAULDRON) return LAVA;
            return null;
        }

        private static Content contentForFluid(Fluid fluid) {
            if (fluid == Fluids.WATER) return WATER;
            if (fluid == Fluids.LAVA) return LAVA;
            return null;
        }

        private Content currentContent(BlockState state) {
            Content content = contentForBlock(state.getBlock());
            return content == null ? EMPTY : content;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (tank != 0) return FluidStack.EMPTY;
            BlockState state = level.getBlockState(pos);
            Content content = currentContent(state);
            int amount = content.totalAmount * content.currentLevel(state) / content.maxLevel;
            return amount == 0 ? FluidStack.EMPTY : new FluidStack(content.fluid, amount);
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank == 0 ? currentContent(level.getBlockState(pos)).totalAmount : 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return tank == 0 && stack != null && contentForFluid(stack.getFluid()) != null;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource == null || resource.isEmpty()) return 0;
            Content inserted = contentForFluid(resource.getFluid());
            if (inserted == null) return 0;

            BlockState state = level.getBlockState(pos);
            Content current = currentContent(state);
            if (current.fluid != Fluids.EMPTY && current.fluid != resource.getFluid()) return 0;

            int divisor = gcd(inserted.maxLevel, inserted.totalAmount);
            int amountIncrement = inserted.totalAmount / divisor;
            int levelIncrement = inserted.maxLevel / divisor;
            int currentLevel = current.currentLevel(state);
            int increments = Math.min(resource.getAmount() / amountIncrement,
                    (inserted.maxLevel - currentLevel) / levelIncrement);
            if (increments > 0) {
                updateLevel(inserted, currentLevel + increments * levelIncrement, action);
            }
            return increments * amountIncrement;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource == null || resource.isEmpty() || resource.hasTag()) return FluidStack.EMPTY;
            BlockState state = level.getBlockState(pos);
            return resource.getFluid() == currentContent(state).fluid
                    ? drain(state, resource.getAmount(), action)
                    : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return maxDrain <= 0 ? FluidStack.EMPTY : drain(level.getBlockState(pos), maxDrain, action);
        }

        private FluidStack drain(BlockState state, int maxDrain, FluidAction action) {
            Content content = currentContent(state);
            int divisor = gcd(content.maxLevel, content.totalAmount);
            int amountIncrement = content.totalAmount / divisor;
            int levelIncrement = content.maxLevel / divisor;
            int currentLevel = content.currentLevel(state);
            int increments = Math.min(maxDrain / amountIncrement, currentLevel / levelIncrement);
            if (increments <= 0) return FluidStack.EMPTY;

            int newLevel = currentLevel - increments * levelIncrement;
            if (newLevel == 0) {
                if (action.execute()) level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
            } else {
                updateLevel(content, newLevel, action);
            }
            return new FluidStack(content.fluid, increments * amountIncrement);
        }

        private void updateLevel(Content content, int newLevel, FluidAction action) {
            if (!action.execute()) return;
            BlockState state = content.block.defaultBlockState();
            if (content.levelProperty != null) {
                state = state.setValue(content.levelProperty, newLevel);
            }
            level.setBlockAndUpdate(pos, state);
        }

        private static int gcd(int a, int b) {
            while (b != 0) {
                int next = a % b;
                a = b;
                b = next;
            }
            return a;
        }

        private record Content(Block block, Fluid fluid, int totalAmount, int maxLevel,
                               IntegerProperty levelProperty) {
            private int currentLevel(BlockState state) {
                if (fluid == Fluids.EMPTY) return 0;
                return levelProperty == null ? 1 : state.getValue(levelProperty);
            }
        }
    }
}
