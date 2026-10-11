package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;

/** Player placement requires air; automatic return placement preserves safe side foliage. */
public final class FamilyGatewayItem extends Item {
    public FamilyGatewayItem(Properties properties) { super(properties); }

    public static boolean canPlace(Level level, BlockPos root, Player player) {
        return canPlace(level, root, player, player.getMainHandItem());
    }
    private static boolean canPlace(Level level, BlockPos root, Player player, ItemStack stack) {
        return level.dimension().equals(Level.OVERWORLD) && canPlaceStructure(level, root, player, stack);
    }
    /** Strict air-only preflight for the player-placed frame. */
    static boolean canPlaceStructure(Level level, BlockPos root, Player player, ItemStack stack) {
        return canPlaceStructure(level, root, player, stack, false);
    }

    /** Automatic Vale placement may leave harmless natural flora in the frame's side clearance. */
    static boolean canPlaceReturnStructure(Level level, BlockPos root, Player player) {
        return canPlaceStructure(level, root, player, ItemStack.EMPTY, true);
    }

    private static boolean canPlaceStructure(Level level, BlockPos root, Player player, ItemStack stack,
                                             boolean allowFoliageClearance) {
        if (player.level() != level || !player.isAlive() || player.isSpectator()
                || root.getY() <= level.getMinBuildHeight()
                || root.getY() + 4 >= level.getMaxBuildHeight()) return false;
        for (var p : BlockPos.betweenClosed(root.offset(-1, -1, -1), root.offset(1, 3, 1))) {
            if (!level.hasChunkAt(p) || !level.getWorldBorder().isWithinBounds(p)
                    || !level.mayInteract(player, p) || !player.mayUseItemAt(p, Direction.UP, stack)) return false;
            var state = level.getBlockState(p);
            if (p.getY() == root.getY() - 1) {
                if (!state.isFaceSturdy(level, p, Direction.UP) || !state.getFluidState().isEmpty()) return false;
            } else if (level.getBlockEntity(p) != null) return false;
            else if (isFrameCell(root, p) || !allowFoliageClearance) {
                if (!state.isAir()) return false;
            } else if (!safeFoliageClearance(level, root, p, state)) return false;
        }
        return level.getEntities(null, new AABB(root.offset(-1, 0, -1), root.offset(2, 4, 2))).isEmpty();
    }

    private static boolean isFrameCell(BlockPos root, BlockPos pos) {
        return pos.getZ() == root.getZ() && pos.getX() >= root.getX() - 1 && pos.getX() <= root.getX() + 1
                && pos.getY() >= root.getY() && pos.getY() <= root.getY() + 3;
    }

    private static boolean safeFoliageClearance(Level level, BlockPos root, BlockPos pos,
                                               net.minecraft.world.level.block.state.BlockState state) {
        // Moss carpet has a thin collision shape but is harmless groundcover in unused side cells.
        // It is never a frame cell, support block or landing floor, and is left untouched.
        boolean groundcover = pos.getY() == root.getY() && state.is(net.minecraft.world.level.block.Blocks.MOSS_CARPET);
        return (groundcover || state.canBeReplaced() && state.getCollisionShape(level, pos).isEmpty())
                && state.getFluidState().isEmpty() && !state.is(net.minecraft.tags.BlockTags.FIRE)
                && !state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                && !state.is(net.minecraft.world.level.block.Blocks.CACTUS)
                && !state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                && !state.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                && !state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                && !state.is(net.minecraft.world.level.block.Blocks.POINTED_DRIPSTONE);
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel(); var player = context.getPlayer();
        var root = context.getClickedPos().above();
        if (player == null) return InteractionResult.FAIL;
        if (context.getClickedFace() != Direction.UP || !canPlace(level, root, player, context.getItemInHand())) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.dudunka.gateway.space"), false);
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!placeStructure(level, root, player, false)) return InteractionResult.FAIL;
        if (!player.getAbilities().instabuild) context.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    /** Shared snapshot transaction. Automatic return placement consumes no item. */
    static boolean placeStructure(Level level, BlockPos root, Player player, boolean active) {
        var snapshots = new ArrayList<BlockSnapshot>();
        boolean placed = true;
        for (int part = 0; part < 12; part++) {
            var pos = FamilyGatewayBlock.cell(root, part);
            snapshots.add(BlockSnapshot.create(level.dimension(), level, pos));
            placed &= level.setBlock(pos, DudunkaMod.FAMILY_GATEWAY.get().defaultBlockState()
                    .setValue(FamilyGatewayBlock.PART, part).setValue(FamilyGatewayBlock.ACTIVE, active), 3);
        }
        // Normal ItemStack.useOn already owns Forge's snapshot/event transaction. Direct callers need it here.
        if (!placed || (!level.captureBlockSnapshots && ForgeEventFactory.onMultiBlockPlace(player, snapshots, Direction.UP))) {
            boolean restoring = level.restoringBlockSnapshots;
            boolean capturing = level.captureBlockSnapshots;
            level.restoringBlockSnapshots = true;
            level.captureBlockSnapshots = false;
            try { for (int i=snapshots.size()-1;i>=0;i--) snapshots.get(i).restore(true, false); }
            finally { level.restoringBlockSnapshots = restoring; level.captureBlockSnapshots = capturing; }
            return false;
        }
        return true;
    }

    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.dudunka.family_gateway.hint"));
    }
}
