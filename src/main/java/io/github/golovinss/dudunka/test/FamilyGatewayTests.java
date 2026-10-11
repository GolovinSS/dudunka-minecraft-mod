package io.github.golovinss.dudunka.test;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.golovinss.dudunka.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.*;

@GameTestHolder(DudunkaMod.ID)
@PrefixGameTestTemplate(false)
public final class FamilyGatewayTests {
    // Only persisted identity, ownership, home and care state must remain fixed during
    // a ritual. Vanilla/companion ticking may advance recovery and cooldown counters.
    private static final List<String> RITUAL_STABLE_NBT = List.of(
            "FamilyOwner", "FamilyHome", "HomeDimension", "HomeMarker", "HomeMode",
            "Furniture", "FurnitureDimension", "Staying", "Trust", "GrowthTicks",
            "CharacterVariant", "NextCharacterVariant");

    static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final ServerLevel level;
        final BlockPos root;
        final ServerPlayer owner;
        final List<ServerPlayer> players = new ArrayList<>();
        final Map<UUID, List<Packet<?>>> outbound = new HashMap<>();
        final List<Companion> friends = new ArrayList<>();
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        boolean closed;
        Fixture(GameTestHelper h) { this(h, false); }
        Fixture(GameTestHelper h, boolean elevatedDrySite) { this(h,elevatedDrySite,195); }
        Fixture(GameTestHelper h, boolean elevatedDrySite,long cleanupTick) {
            this.h = h; level = h.getLevel();
            var normalRoot = h.absolutePos(new BlockPos(8, 4, 8));
            // This opt-in site avoids fluids entering a long-running multiplayer
            // fixture through the generated terrain at the usual GameTest Y=-56.
            root = elevatedDrySite ? new BlockPos(normalRoot.getX(), 160, normalRoot.getZ()) : normalRoot;
            for (var p : BlockPos.betweenClosed(root.offset(-5, -1, -5), root.offset(5, 4, 5))) {
                blocks.put(p.immutable(), level.getBlockState(p));
                level.setBlock(p, p.getY() == root.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 18);
            }
            owner = player();
            // Runs even if a delayed assertion fails. Success paths also close immediately.
            h.runAtTickTime(cleanupTick, this::close);
        }
        ServerPlayer player() {
            var p = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "GateTest"));
            var packets = new ArrayList<Packet<?>>(); outbound.put(p.getUUID(), packets);
            var connection = new Connection(PacketFlow.SERVERBOUND) {
                @Override public void send(Packet<?> packet) { packets.add(packet); }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
            };
            p.connection = new ServerGamePacketListenerImpl(p.server, connection, p) {
                @Override public void send(Packet<?> packet) { packets.add(packet); }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
            };
            p.moveTo(root.getX() + .5, root.getY(), root.getZ() + 3.5 + players.size(), 37, 12);
            level.addNewPlayer(p); players.add(p); return p;
        }
        InteractionResult place() {
            return place(root);
        }
        InteractionResult place(BlockPos position) {
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DudunkaMod.FAMILY_GATEWAY_ITEM.get()));
            var context = new UseOnContext(owner, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(position.below()), Direction.UP, position.below(), false));
            return owner.getMainHandItem().useOn(context);
        }
        FamilyGatewayEntity gateway() { return (FamilyGatewayEntity) level.getBlockEntity(root); }
        Companion friend(Kind kind, ServerPlayer player) {
            var mob = DudunkaMod.TYPES.get(kind).get().create(level);
            mob.initialize(player.getUUID(), root.west(4));
            mob.moveTo(root.getX() - 3.5 + kind.ordinal() * 2, root.getY(), root.getZ() - 2.5, 0, 0);
            var tag = new CompoundTag(); mob.addAdditionalSaveData(tag);
            tag.putInt("GrowthTicks", DudunkaMod.GROWTH_SECONDS.get() * 40);
            tag.putInt("Trust", FamilyGatewayRules.TRUST); tag.putBoolean("Staying", false); tag.putBoolean("HomeMode", true);
            mob.readAdditionalSaveData(tag); mob.setNoAi(true);
            level.addFreshEntity(mob); friends.add(mob); return mob;
        }
        List<Companion> family(ServerPlayer player, boolean friendly) {
            var family = new ArrayList<Companion>();
            for (var kind : Kind.values()) family.add(friend(kind, player));
            if (friendly) bond(player, family);
            return family;
        }
        void bond(ServerPlayer player, List<Companion> family) {
            var ledger = FamilyFriendships.get(player.server);
            for (int a = 0; a < family.size(); a++) for (int b = a + 1; b < family.size(); b++) {
                var first = family.get(a); var second = family.get(b);
                // Real existing accrual API, no production setter or friendship mutation for the portal.
                for (int sample = 0; sample <= 600; sample++)
                    ledger.observe(player.getUUID(), first.getUUID(), first.kind, second.getUUID(), second.kind, sample * 20L);
            }
        }
        void homes(List<Companion> family) {
            for(var mob:family) {
                var home=root.offset(-4+mob.kind.ordinal()*2,0,-4);
                level.setBlockAndUpdate(home,DudunkaMod.HOMES.get(mob.kind).get().defaultBlockState());
                ((HomeMarkerEntity)level.getBlockEntity(home)).claim(mob.ownerId()); mob.bindHome(home);
                var furniture=home.north();
                level.setBlockAndUpdate(furniture,DudunkaMod.FURNITURE.get(mob.kind).get().defaultBlockState());
                ((FurnitureEntity)level.getBlockEntity(furniture)).assign(mob); mob.bindFurniture(furniture);
            }
        }
        void missing(String suffix) {
            var result = FamilyGatewayRules.inspect(level, root, owner.getUUID());
            h.assertTrue(!result.ready() && result.missing().getContents() instanceof TranslatableContents text
                    && text.getKey().equals("message.dudunka.gateway." + suffix), "Exact private missing condition: " + suffix);
        }
        void finish(Runnable assertions) {
            h.runAfterDelay(FamilyGatewayRules.RITUAL_TICKS + 2, () -> {
                try { assertions.run(); h.succeed(); } finally { close(); }
            });
        }
        @Override public void close() {
            if (closed) return; closed = true;
            if (gateway() != null) gateway().cancel();
            friends.forEach(Entity::discard);
            level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(root).inflate(6),
                    item->item.getItem().is(DudunkaMod.FAMILY_GATEWAY_ITEM.get())).forEach(Entity::discard);
            for (var player : players) {
                MushroomValeReturns.get(player.server).forget(player.getUUID());
                player.remove(Entity.RemovalReason.DISCARDED);
            }
            // Remove the root before restoration so frame cleanup cannot erase restored fixture blocks.
            if (level.getBlockState(root).is(DudunkaMod.FAMILY_GATEWAY.get())) level.removeBlock(root, false);
            blocks.forEach((p, state) -> level.setBlock(p, state, 18));
        }
    }
    private static void care(Companion mob, int stage, int trust) {
        var tag = new CompoundTag(); mob.addAdditionalSaveData(tag);
        tag.putInt("GrowthTicks", DudunkaMod.GROWTH_SECONDS.get() * 20 * stage); tag.putInt("Trust", trust);
        mob.readAdditionalSaveData(tag);
    }

    @GameTest(template="empty", timeoutTicks=80, batch="isolated_gateway_dev_setup")
    public static void developerPreparationIsOptInCompleteAndLeavesExistingCompanionsUntouched(GameTestHelper h) {
        String original=System.getProperty(MushroomVale.DEV_PROPERTY);
        try(var f=new Fixture(h,true)) {
            try {
                System.clearProperty(MushroomVale.DEV_PROPERTY);
                var hidden=new CommandDispatcher<CommandSourceStack>();MushroomVale.registerCommands(hidden);
                h.assertTrue(hidden.getRoot().getChild("dudunka_dev")==null,"No developer route exists without JVM opt-in");
                System.setProperty(MushroomVale.DEV_PROPERTY,"true");
                var dispatcher=new CommandDispatcher<CommandSourceStack>();MushroomVale.registerCommands(dispatcher);
                String command="dudunka_dev mushroom_vale prepare_family";
                try {dispatcher.execute(command,f.owner.createCommandSourceStack().withPermission(0));throw new AssertionError("Permission level 2 is required");}
                catch(CommandSyntaxException expected) {}

                var old=f.friend(Kind.DUDUNKA,f.owner);care(old,1,7);var oldUuid=old.getUUID();var oldNbt=old.saveWithoutId(new CompoundTag());
                try {dispatcher.execute(command,f.owner.createCommandSourceStack().withPermission(2));throw new AssertionError("Nearby owned companion must refuse setup");}
                catch(CommandSyntaxException expected) {}
                h.assertTrue(f.level.getEntity(oldUuid)==old&&oldNbt.equals(old.saveWithoutId(new CompoundTag())),"Refusal preserves existing companion identity and all saved data");
                old.discard();f.friends.remove(old);

                h.assertTrue(dispatcher.execute(command,f.owner.createCommandSourceStack().withPermission(2))==1,"Opted-in operator player can prepare family");
                var family=f.level.getEntitiesOfClass(Companion.class,new AABB(f.owner.blockPosition()).inflate(12),mob->f.owner.getUUID().equals(mob.ownerId()));
                f.friends.addAll(family);
                h.assertTrue(family.size()==3,"Exactly three new owned companions are created nearby");
                var byKind=new EnumMap<Kind,Companion>(Kind.class);for(var mob:family)byKind.put(mob.kind,mob);
                h.assertTrue(byKind.size()==3,"One Dudunka, Marusya and Syusya are prepared");
                for(var mob:family)h.assertTrue(mob.stage()==2&&mob.trust()>=FamilyGatewayRules.TRUST&&!mob.staying()&&!mob.atHomeMode(),"New companion is adult, trusted and free to follow");
                var ledger=FamilyFriendships.get(f.owner.server);
                for(int a=0;a<family.size();a++)for(int b=a+1;b<family.size();b++)h.assertTrue(ledger.score(f.owner.getUUID(),family.get(a).getUUID(),family.get(b).getUUID())>=FamilyGatewayRules.FRIENDSHIP,"Every pair is seeded as an ordinary saved-data friendship");
                h.assertTrue(FamilyGatewayRules.inspect(f.level,f.owner.blockPosition(),f.owner.getUUID()).ready(),"Prepared family meets the actual gateway rules near its owner");
                try {dispatcher.execute(command,f.owner.createCommandSourceStack().withPermission(2));throw new AssertionError("Repeated setup must be blocked");}
                catch(CommandSyntaxException expected) {}
                h.assertTrue(f.level.getEntitiesOfClass(Companion.class,new AABB(f.owner.blockPosition()).inflate(12),mob->f.owner.getUUID().equals(mob.ownerId())).size()==3,"Repeat refusal creates no duplicates");
            } catch(CommandSyntaxException e) {throw new AssertionError(e);}
            finally {if(original==null)System.clearProperty(MushroomVale.DEV_PROPERTY);else System.setProperty(MushroomVale.DEV_PROPERTY,original);}
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_placement")
    public static void gatewayPlacementNeverOverwritesBuildingsFluidsPlantsOrEntities(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var obstruction = f.root.east().above(2);
            for (var block : List.of(Blocks.STONE, Blocks.CHEST, Blocks.WATER, Blocks.TALL_GRASS, Blocks.MOSS_CARPET)) {
                f.level.setBlockAndUpdate(obstruction, block.defaultBlockState());
                h.assertTrue(f.place() == InteractionResult.FAIL && f.owner.getMainHandItem().getCount() == 1
                        && f.level.getBlockState(obstruction).is(block) && f.level.getBlockState(f.root).isAir(),
                        "Blocked placement preserves existing blocks, block entities and item: " + block);
                f.level.removeBlock(obstruction, false);
            }
            f.level.removeBlock(f.root.below(), false);
            h.assertTrue(f.place() == InteractionResult.FAIL, "Unsupported floor rejects placement");
            f.level.setBlockAndUpdate(f.root.below(), Blocks.STONE.defaultBlockState());
            var mob = f.friend(Kind.DUDUNKA, f.owner); mob.moveTo(Vec3.atBottomCenterOf(f.root));
            h.assertTrue(f.place() == InteractionResult.FAIL, "Placement never encloses an entity");
            mob.discard();
            h.assertTrue(f.place() == InteractionResult.CONSUME && f.owner.getMainHandItem().isEmpty()
                    && FamilyGatewayBlock.complete(f.level, f.root) && f.gateway() != null,
                    "Valid existing Overworld ground installs all twelve cells and consumes exactly one item");
            h.assertTrue(!f.gateway().awakened(f.owner.getUUID()) && !f.gateway().getBlockState().getValue(FamilyGatewayBlock.ACTIVE),
                    "New portal is visibly inactive and locked");
            f.level.destroyBlock(f.root.east().above(), false);
            for (int part=0;part<12;part++) h.assertTrue(f.level.getBlockState(FamilyGatewayBlock.cell(f.root,part)).isAir(),
                    "Breaking a frame piece cleans the complete gateway");
            var drops=f.level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(f.root).inflate(3),
                    item->item.getItem().is(DudunkaMod.FAMILY_GATEWAY_ITEM.get()));
            h.assertTrue(drops.size()==1 && drops.get(0).getItem().getCount()==1, "Breaking a frame returns exactly one ordinary gateway item");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_protection")
    public static void gatewayProtectedPlacementRollsBackAndKeepsItem(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            Consumer<BlockEvent.EntityMultiPlaceEvent> veto = event -> { if (event.getEntity() == f.owner) event.setCanceled(true); };
            MinecraftForge.EVENT_BUS.addListener(veto);
            try {
                h.assertTrue(f.place() == InteractionResult.FAIL && f.owner.getMainHandItem().getCount() == 1,
                        "Protection veto consumes no gateway");
                for (int part=0;part<12;part++) h.assertTrue(f.level.getBlockState(FamilyGatewayBlock.cell(f.root,part)).isAir(),
                        "Protection veto restores every cell");
                h.assertTrue(f.gateway()==null, "Protection veto leaves no unlock data");
            } finally { MinecraftForge.EVENT_BUS.unregister(veto); }
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_care")
    public static void gatewayReportsMissingJuvenileTrustAndEveryFriendshipPair(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.place(); f.missing("missing");
            var family = f.family(f.owner, false);
            care(family.get(0), 1, 20); f.missing("young");
            care(family.get(0), 2, 19); f.missing("trust");
            care(family.get(0), 2, 20); f.missing("friendship");
            var ledger = FamilyFriendships.get(f.owner.server);
            for (var pair : List.of(List.of(family.get(0),family.get(1)), List.of(family.get(0),family.get(2)))) {
                f.bond(f.owner,pair); f.missing("friendship");
            }
            f.bond(f.owner,List.of(family.get(1),family.get(2)));
            var before = ledger.save(new CompoundTag());
            h.assertTrue(FamilyGatewayRules.inspect(f.level,f.root,f.owner.getUUID()).ready(), "Inclusive trust 20 and all pair scores 10 pass");
            h.assertTrue(before.equals(ledger.save(new CompoundTag())), "Eligibility never awards or alters friendship");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_ownership")
    public static void gatewayIgnoresOtherOwnersDeadCarriedUnloadedAndDistantFriends(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.place(); var stranger = f.player(); f.family(stranger,true); f.missing("missing");
            f.friends.forEach(Entity::discard);
            var family=f.family(f.owner,true); var syusya=family.get(2);
            syusya.moveTo(f.root.getX()+20,f.root.getY(),f.root.getZ()); f.missing("missing");
            FamilyRegistry.get(f.owner.server).observe(syusya);
            syusya.moveTo(f.root.getX()+.5,f.root.getY(),f.root.getZ()-2.5);
            var carrier=CarrierLedger.get(f.owner.server); var token=UUID.randomUUID();
            carrier.capture(syusya.getUUID(),token,f.owner.getUUID()); f.missing("missing");
            carrier.consume(syusya.getUUID(),token,f.owner.getUUID());
            syusya.setHealth(0); f.missing("missing"); syusya.setHealth(20);
            syusya.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); f.missing("missing");
            h.assertTrue(FamilyRegistry.get(f.owner.server).member(syusya.getUUID())!=null,
                    "Stale registry position cannot substitute for a live loaded friend");
            h.assertTrue(!f.gateway().awakened(f.owner.getUUID()), "Failures never unlock the owner");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_duplicates")
    public static void gatewayRejectsDuplicateKindsAndUuids(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.place(); var family=f.family(f.owner,true);
            var repeated=new ArrayList<>(family); repeated.add(family.get(0));
            h.assertTrue(!FamilyGatewayRules.evaluate(f.level,f.root,f.owner.getUUID(),repeated).ready(), "Same UUID cannot count twice");
            var extra=f.friend(Kind.DUDUNKA,f.owner); f.missing("duplicate_kind"); extra.discard();
            var detached=DudunkaMod.TYPES.get(Kind.DUDUNKA).get().create(f.level);
            detached.load(family.get(0).saveWithoutId(new CompoundTag()));
            var impostors=new ArrayList<>(family); impostors.set(0,detached);
            h.assertTrue(!FamilyGatewayRules.evaluate(f.level,f.root,f.owner.getUUID(),impostors).ready(),
                    "Unloaded duplicate NBT cannot impersonate the canonical live entity");
            var vale=f.owner.server.getLevel(MushroomVale.LEVEL); vale.getChunk(0,0);
            var duplicate=DudunkaMod.TYPES.get(Kind.SYUSYA).get().create(vale);
            duplicate.setUUID(family.get(2).getUUID()); duplicate.moveTo(8,100,8,0,0); duplicate.setNoAi(true);
            // Entity insertion itself can reject duplicate UUIDs (including via Forge).
            // The portal cannot be expected to inspect a duplicate the engine refused.
            boolean inserted = vale.addFreshEntity(duplicate);
            try {
                if (inserted) {
                    f.missing("duplicate");
                } else {
                    h.assertTrue(vale.getEntity(duplicate.getUUID()) == null,
                            "Rejected cross-level fixture must not leave a duplicate entity");
                }
            } finally { duplicate.discard(); }
            h.assertTrue(FamilyGatewayRules.inspect(f.level,f.root,f.owner.getUUID()).ready(),
                    "No live duplicate restores ordinary eligibility");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_awaken")
    public static void gatewayThreeAdultsAwakenWithoutMovingOrChangingHomeAndFriendship(GameTestHelper h) {
        var f=new Fixture(h); f.place(); var family=f.family(f.owner,true); f.homes(family);
        var before=new HashMap<UUID,CompoundTag>();
        var positions=new HashMap<UUID,Vec3>();
        for(var mob:family) {
            var data=new CompoundTag(); mob.addAdditionalSaveData(data); before.put(mob.getUUID(),data);
            positions.put(mob.getUUID(),mob.position());
        }
        var bonds=FamilyFriendships.get(f.owner.server).save(new CompoundTag());
        f.gateway().interact(f.owner);
        h.assertTrue(f.gateway().awakening() && !f.gateway().awakened(f.owner.getUUID()), "Ritual starts without immediate unlock or travel");
        f.finish(()->{
            h.assertTrue(f.gateway().awakened(f.owner.getUUID()) && !f.gateway().awakening(), "Three adults awaken owner access after three seconds");
            for(int part=0;part<12;part++) h.assertTrue(f.level.getBlockState(FamilyGatewayBlock.cell(f.root,part)).getValue(FamilyGatewayBlock.ACTIVE),
                    "Every visible piece synchronizes the active state");
            for(var mob:family) {
                var after=new CompoundTag(); mob.addAdditionalSaveData(after);
                h.assertTrue(f.level.getEntity(mob.getUUID()) == mob && !mob.isRemoved()
                        && positions.get(mob.getUUID()).equals(mob.position()),
                        "Awakening retains the living UUID, instance and exact position: " + mob.kind);
                var saved = before.get(mob.getUUID());
                for (String key : RITUAL_STABLE_NBT)
                    h.assertTrue(Objects.equals(saved.get(key), after.get(key)),
                            "Awakening preserves " + key + " for " + mob.kind);
            }
            h.assertTrue(bonds.equals(FamilyFriendships.get(f.owner.server).save(new CompoundTag())), "Ritual never changes any friendship score");
            h.assertTrue(f.owner.serverLevel()==f.level, "Awakening never automatically teleports the player");
        });
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_pose")
    public static void gatewayUsesExistingCharacterPosesAndCancelsWhenAFriendLeaves(GameTestHelper h) {
        var f=new Fixture(h); f.place(); var family=f.family(f.owner,true);
        for(var mob:family) mob.setNoAi(false);
        f.gateway().interact(f.owner);
        h.runAfterDelay(12,()->{
            try {
                h.assertTrue(family.get(0).activity()==Activity.WAVE && family.get(1).activity()==Activity.PURR
                        && family.get(2).activity()==Activity.CURIOUS, "Each character performs an existing characteristic pose");
                family.get(2).moveTo(f.root.getX()+20,f.root.getY(),f.root.getZ());
                FamilyGatewayEntity.tick(f.level,f.root,f.gateway().getBlockState(),f.gateway());
                h.assertTrue(!f.gateway().awakening() && !f.gateway().awakened(f.owner.getUUID()), "A departing friend cancels without unlock");
                for(var mob:family) h.assertTrue(!mob.portalPosing(), "Cancellation releases every pose");
                h.succeed();
            } finally { f.close(); }
        });
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_reload")
    public static void gatewayOwnerUnlockSurvivesBlockEntityReloadAndRejectsMalformedOwners(GameTestHelper h) {
        var f=new Fixture(h); f.place(); f.family(f.owner,true); var second=f.player();
        f.gateway().interact(f.owner);
        f.finish(()->{
            var tag=f.gateway().saveWithFullMetadata();
            tag.getList("AwakenedOwners",Tag.TAG_COMPOUND).add(new CompoundTag());
            f.level.removeBlockEntity(f.root);
            var restored=new FamilyGatewayEntity(f.root,f.level.getBlockState(f.root)); restored.load(tag); f.level.setBlockEntity(restored);
            h.assertTrue(restored.awakened(f.owner.getUUID()) && !restored.awakened(second.getUUID()) && !restored.awakening(),
                    "Chunk NBT reload preserves owner access, ignores malformed rows and never resumes a stale ritual");
            var savedAgain=restored.saveWithFullMetadata();
            h.assertTrue(savedAgain.getList("AwakenedOwners",Tag.TAG_COMPOUND).size()==1, "Only valid owners persist");
            restored.interact(second);
            h.assertTrue(!restored.awakened(second.getUUID()) && second.serverLevel()==f.level,
                    "Active visual state cannot grant another player entry");
        });
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_two_players")
    public static void gatewayCompetingPlayersProgressIndependently(GameTestHelper h) {
        // Keep the two rituals dry and independent of generated sea-level terrain.
        var f = new Fixture(h, true);
        f.place();
        f.family(f.owner, true);
        var second = f.player();
        var secondFamily = f.family(second, true);
        for (var mob : secondFamily)
            mob.moveTo(f.root.getX() - 3.5 + mob.kind.ordinal() * 2,
                    f.root.getY(), f.root.getZ() + 2.5, 0, 0);
        h.assertTrue(FamilyGatewayRules.inspect(f.level, f.root, second.getUUID()).ready(),
                "Second family meets its requirements before competing activation");
        f.gateway().interact(f.owner);
        f.gateway().interact(second);

        // IMPORTANT: Minecraft's GameTestInfo iterates its scheduled-actions map.
        // Register all delayed callbacks NOW, never from inside a delayed callback.
        // Adding runAfterDelay() while that map is being iterated can crash the
        // GameTest server inside fastutil's Object2LongOpenHashMap iterator.
        var gate = f.gateway();
        var expectedFriends = secondFamily.stream().map(Companion::getUUID).toList();
        var syusya = secondFamily.stream().filter(mob -> mob.kind == Kind.SYUSYA).findFirst().orElseThrow();
        final String[] firstBreak = {null};

        h.runAtTickTime(62, () -> {
            h.assertTrue(gate.awakened(f.owner.getUUID()) && !gate.awakened(second.getUUID()),
                    "Competing interaction cannot inherit or replace the first owner's ritual");
            var eligible = FamilyGatewayRules.inspect(f.level, f.root, second.getUUID());
            h.assertTrue(eligible.ready(), "Second family eligible before its own ritual: "
                    + (eligible.missing() == null ? "ready" : eligible.missing().getString()));
            gate.interact(second);
            h.assertTrue(gate.awakening(), "Second family begins its own ritual");
        });

        // A few fixed checkpoints retain useful diagnostics without registering
        // sixty-one actions from inside the callback at tick 62.
        for (int at : new int[]{72, 92, 108, 115, 121, 125}) {
            final int sample = at;
            h.runAtTickTime(sample, () -> {
                if (firstBreak[0] != null || gate.awakened(second.getUUID())) return;
                var current = FamilyGatewayRules.inspect(f.level, f.root, second.getUUID());
                boolean same = expectedFriends.equals(current.friends().stream().map(Companion::getUUID).toList());
                if (!gate.awakening() || !current.ready() || !same) {
                    firstBreak[0] = "tick=" + sample + " awakening=" + gate.awakening()
                            + " eligible=" + current.ready()
                            + " reason=" + (current.missing() == null ? "none" : current.missing().getString())
                            + " sameParticipants=" + same
                            + " syusyaPos=" + syusya.blockPosition()
                            + " syusyaWater=" + syusya.isInWaterOrBubble()
                            + " syusyaPassenger=" + syusya.isPassenger()
                            + " syusyaVehicle=" + syusya.isVehicle()
                            + " syusyaLeashed=" + syusya.isLeashed();
                }
            });
        }
        h.runAtTickTime(130, () -> {
            try {
                var current = FamilyGatewayRules.inspect(f.level, f.root, second.getUUID());
                h.assertTrue(gate.awakened(f.owner.getUUID()) && gate.awakened(second.getUUID()),
                        "Second family's ritual did not unlock independently; firstBreak=" + firstBreak[0]
                        + " reasonNow=" + (current.missing() == null ? "none" : current.missing().getString())
                        + " firstUnlocked=" + gate.awakened(f.owner.getUUID())
                        + " secondUnlocked=" + gate.awakened(second.getUUID())
                        + " awakening=" + gate.awakening());
                h.succeed();
            } finally { f.close(); }
        });
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_no_loads")
    public static void gatewayIdleChecksPlacementAndRitualNeverLoadOrForceChunks(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.place(); f.family(f.owner,true);
            var remote=new BlockPos(2000000,80,2000000);
            var forced=new HashSet<>(f.level.getForcedChunks());
            int loaded=f.level.getChunkSource().getLoadedChunksCount();
            h.assertTrue(!FamilyGatewayItem.canPlace(f.level,remote,f.owner), "Unloaded placement fails before reading blocks");
            h.assertTrue(!FamilyGatewayRules.inspect(f.level,remote,f.owner.getUUID()).ready(), "Remote family lookup cannot awaken a portal");
            f.gateway().interact(f.owner);
            FamilyGatewayEntity.tick(f.level,f.root,f.gateway().getBlockState(),f.gateway());
            h.assertTrue(!f.level.hasChunkAt(remote) && loaded==f.level.getChunkSource().getLoadedChunksCount()
                    && forced.equals(new HashSet<>(f.level.getForcedChunks())), "Activation and safety checks add no loaded chunks or forced tickets");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_scene_reload")
    public static void gatewayInterruptedSceneCannotUnlockAfterReloadOrOwnerDeparture(GameTestHelper h) {
        var f=new Fixture(h); f.place(); var family=f.family(f.owner,true); f.gateway().interact(f.owner);
        var tag=f.gateway().saveWithFullMetadata();
        f.level.removeBlockEntity(f.root);
        var restored=new FamilyGatewayEntity(f.root,f.level.getBlockState(f.root));restored.load(tag);f.level.setBlockEntity(restored);
        h.assertTrue(!restored.awakening() && !restored.awakened(f.owner.getUUID()), "Reload cancels an incomplete ritual and grants no access");
        for(var mob:family) h.assertTrue(!mob.portalPosing(), "Unload releases temporary companion poses");
        restored.interact(f.owner);
        f.owner.moveTo(f.root.getX()+20,f.root.getY(),f.root.getZ());
        f.finish(()->h.assertTrue(!f.gateway().awakening() && !f.gateway().awakened(f.owner.getUUID()),
                "Owner departure cannot finish an unattended awakening"));
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_competing_gateways")
    public static void gatewayFriendsCannotAwakenTwoGatewaysAtOnce(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            f.place();var family=f.family(f.owner,true);
            for(var mob:family)mob.moveTo(f.root.getX()-1.5+mob.kind.ordinal()*2,f.root.getY(),f.root.getZ()-2.5,0,0);
            var otherRoot=f.root.east(4);
            h.assertTrue(f.place(otherRoot)==InteractionResult.CONSUME, "Second physical portal fits on its own clear floor");
            f.gateway().interact(f.owner);
            var other=(FamilyGatewayEntity)f.level.getBlockEntity(otherRoot);
            other.interact(f.owner);
            h.assertTrue(f.gateway().awakening() && !other.awakening(), "Shared companions reserve only one awakening");
            var result=FamilyGatewayRules.inspect(f.level,otherRoot,f.owner.getUUID());
            h.assertTrue(!result.ready() && result.missing().getContents() instanceof TranslatableContents text
                    && text.getKey().equals("message.dudunka.gateway.friend_busy"), "Competing portal explains which friend is busy");
            f.level.removeBlock(otherRoot,false);
        }
        h.succeed();
    }

    static final class Arrival implements AutoCloseable {
        final ServerLevel vale;
        final Map<BlockPos,BlockState> saved=new LinkedHashMap<>();
        final Map<BlockPos,CompoundTag> savedEntities=new LinkedHashMap<>();
        Arrival(ServerPlayer player) {
            vale=player.server.getLevel(MushroomVale.LEVEL); vale.getChunk(0,0);
            for(var p:BlockPos.betweenClosed(new BlockPos(0,300,0),new BlockPos(15,305,15))) {
                saved.put(p.immutable(),vale.getBlockState(p));
                if(vale.getBlockEntity(p)!=null) savedEntities.put(p.immutable(),vale.getBlockEntity(p).saveWithFullMetadata());
            }
            removeGateRoots();
            saved.keySet().forEach(p->vale.setBlock(p,p.getY()==300?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),18));
        }
        void unsafe() { for(int x=0;x<16;x++)for(int z=0;z<16;z++)vale.setBlock(new BlockPos(x,300,z),Blocks.MAGMA_BLOCK.defaultBlockState(),18); }
        private void removeGateRoots() {
            // Cleanup roots first, so their frame teardown cannot erase already restored cells.
            for(var p:saved.keySet()) if(vale.getBlockEntity(p) instanceof FamilyGatewayEntity) vale.removeBlock(p,false);
        }
        @Override public void close() {
            removeGateRoots();
            saved.forEach((p,state)->vale.setBlock(p,state,18));
            savedEntities.forEach((p,tag)->{
                if(vale.getBlockEntity(p)!=null) vale.getBlockEntity(p).load(tag);
            });
        }
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_round_trip")
    public static void gatewayExplicitEntryReturnAndSavedDimensionKeepCompanionIdentities(GameTestHelper h) {
        var f=new Fixture(h); f.place(); var family=f.family(f.owner,true); f.homes(family); f.gateway().interact(f.owner);
        f.finish(()->{
            try(var arrival=new Arrival(f.owner)) {
                var before=new HashMap<UUID,CompoundTag>();for(var mob:family)before.put(mob.getUUID(),mob.saveWithoutId(new CompoundTag()));
                var departure=f.owner.blockPosition(); f.gateway().interact(f.owner);
                h.assertTrue(f.owner.serverLevel()==arrival.vale && MushroomVale.safeLanding(arrival.vale,f.owner.blockPosition(),f.owner),
                        "A second deliberate interaction enters safely");
                var savedPlayer=f.owner.saveWithoutId(new CompoundTag());
                h.assertTrue(savedPlayer.getString("Dimension").equals(MushroomVale.ID.toString()), "Vanilla playerdata retains Vale for restart/reconnect");
                var bookmarks=MushroomValeReturns.get(f.owner.server);
                var restored=MushroomValeReturns.load(bookmarks.save(new CompoundTag()));
                h.assertTrue(restored.point(f.owner.getUUID()).pos().equals(departure), "Existing bookmark serialization survives travel");
                var dispatcher=new CommandDispatcher<CommandSourceStack>();
                String dev=System.getProperty(MushroomVale.DEV_PROPERTY);
                try { System.clearProperty(MushroomVale.DEV_PROPERTY); MushroomVale.registerCommands(dispatcher); }
                finally { if(dev==null)System.clearProperty(MushroomVale.DEV_PROPERTY);else System.setProperty(MushroomVale.DEV_PROPERTY,dev); }
                try { h.assertTrue(dispatcher.execute("dudunka return",f.owner.createCommandSourceStack().withPermission(0))==1,
                        "Ordinary Survival player can return without developer opt-in or operator permission"); }
                catch(CommandSyntaxException e) {throw new AssertionError(e);}
                h.assertTrue(f.owner.serverLevel()==f.level && f.owner.blockPosition().distSqr(departure)<=25
                        && bookmarks.point(f.owner.getUUID())==null, "Return reaches departure and consumes only own bookmark");
                for(var mob:family)h.assertTrue(f.level.getEntity(mob.getUUID())==mob && arrival.vale.getEntity(mob.getUUID())==null
                        && before.get(mob.getUUID()).equals(mob.saveWithoutId(new CompoundTag())), "Round trip never transfers/clones or mutates any companion");
                family.forEach(Entity::discard); f.gateway().interact(f.owner);
                h.assertTrue(f.owner.serverLevel()==arrival.vale, "Awakened owner's future visits do not require repeating the family ritual");
                MushroomVale.returnPlayer(f.owner);
            }
        });
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_safe_failure")
    public static void gatewayUnsafeArrivalAndTravelVetoPreservePositionAndBookmarks(GameTestHelper h) {
        var f=new Fixture(h); f.place(); f.family(f.owner,true); f.gateway().interact(f.owner);
        f.finish(()->{
            try(var arrival=new Arrival(f.owner)) {
                var returns=MushroomValeReturns.get(f.owner.server);
                var previous=new MushroomValeReturns.ReturnPoint(Level.NETHER.location(),new BlockPos(8,70,8),10,5);
                returns.remember(f.owner.getUUID(),previous); var position=f.owner.position();
                Consumer<EntityTravelToDimensionEvent> veto=event->{if(event.getEntity()==f.owner)event.setCanceled(true);};
                MinecraftForge.EVENT_BUS.addListener(veto);
                try {
                    f.gateway().interact(f.owner);
                    h.assertTrue(f.owner.serverLevel()==f.level && position.equals(f.owner.position()) && previous.equals(returns.point(f.owner.getUUID())),
                            "Forge veto preserves exact location and existing bookmark");
                } finally {MinecraftForge.EVENT_BUS.unregister(veto);}
                arrival.unsafe(); f.gateway().interact(f.owner);
                h.assertTrue(f.owner.serverLevel()==f.level && position.equals(f.owner.position()) && previous.equals(returns.point(f.owner.getUUID())),
                        "Unsafe arrival neither overwrites bookmark nor moves player");
            }
        });
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_return_failure")
    public static void gatewayUnsafeReturnPreservesBookmarkUntilDepartureIsRepaired(GameTestHelper h) {
        try(var f=new Fixture(h); var arrival=new Arrival(f.owner)) {
            var previousSpawn=f.level.getSharedSpawnPos(); float angle=f.level.getSharedSpawnAngle();
            try {
                h.assertTrue(MushroomVale.enterPlayer(f.owner)==MushroomVale.TravelResult.ENTERED, "Shared travel entry succeeds");
                var returnRoot=MushroomValeReturnGateway.ensure(f.owner);
                h.assertTrue(returnRoot!=null,"Unsafe-return fixture has a physical exit");
                f.owner.teleportTo(arrival.vale,returnRoot.getX()+.5,returnRoot.getY(),returnRoot.getZ()-1.5,37,12);
                var returns=MushroomValeReturns.get(f.owner.server); var point=returns.point(f.owner.getUUID());
                // Same bounded fixture chunk is both departure and fallback; hazardous top precludes every candidate.
                var chunk=new net.minecraft.world.level.ChunkPos(point.pos()); f.level.getChunk(chunk.x,chunk.z);
                for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                    var p=new BlockPos(chunk.getMinBlockX()+x,300,chunk.getMinBlockZ()+z);
                    f.blocks.putIfAbsent(p,f.level.getBlockState(p));f.level.setBlock(p,Blocks.MAGMA_BLOCK.defaultBlockState(),18);
                }
                var blocked=new MushroomValeReturns.ReturnPoint(Level.OVERWORLD.location(),
                        new BlockPos(chunk.getMinBlockX()+8,301,chunk.getMinBlockZ()+8),0,0);
                returns.remember(f.owner.getUUID(),blocked); f.level.setDefaultSpawnPos(blocked.pos(),0);
                var position=f.owner.position();
                clickReturn(f.owner,returnRoot);
                h.assertTrue(f.owner.serverLevel()==arrival.vale && position.equals(f.owner.position())
                        && blocked.equals(returns.point(f.owner.getUUID())),"Unsafe physical return retains position and bookmark for retry");
                h.assertTrue(MushroomVale.returnPlayer(f.owner)==MushroomVale.TravelResult.RETURN_UNSAFE
                        && position.equals(f.owner.position()) && blocked.equals(returns.point(f.owner.getUUID())),
                        "Unsafe departure and fallback keep player and bookmark available for retry");
                returns.remember(f.owner.getUUID(),point);
                h.assertTrue(MushroomVale.returnPlayer(f.owner)==MushroomVale.TravelResult.RETURNED, "Repaired saved departure allows retry");
            } finally {f.level.setDefaultSpawnPos(previousSpawn,angle);}
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_return_isolation")
    public static void gatewayTwoPlayerReturnBookmarksAndOtherDimensionsStayIndependent(GameTestHelper h) {
        try(var f=new Fixture(h);var arrival=new Arrival(f.owner)) {
            var second=f.player();
            h.assertTrue(f.owner.server.getLevel(Level.NETHER)!=null && f.owner.server.getLevel(Level.END)!=null,
                    "Vanilla dimensions coexist with unchanged Vale ID");
            h.assertTrue(MushroomVale.enterPlayer(f.owner)==MushroomVale.TravelResult.ENTERED
                    && MushroomVale.enterPlayer(second)==MushroomVale.TravelResult.ENTERED, "Two explicit trips arrive at separate safe locations");
            var returns=MushroomValeReturns.get(f.owner.server);var secondPoint=returns.point(second.getUUID());
            h.assertTrue(MushroomVale.returnPlayer(f.owner)==MushroomVale.TravelResult.RETURNED
                    && secondPoint.equals(returns.point(second.getUUID())) && second.serverLevel()==arrival.vale,
                    "One return cannot consume another player's bookmark or move them");
            h.assertTrue(MushroomVale.returnPlayer(second)==MushroomVale.TravelResult.RETURNED, "Second player returns independently");
            h.assertTrue(MushroomVale.returnPlayer(second)==MushroomVale.TravelResult.WRONG_DIMENSION, "Public return cannot affect other dimensions");
        }
        h.succeed();
    }

    private static void clickReturn(ServerPlayer player, BlockPos root) {
        var pos=FamilyGatewayBlock.cell(root,7); // The visible veil is selectable even though it has no collision.
        var level=player.serverLevel(); var state=level.getBlockState(pos);
        state.getBlock().use(state,level,pos,player,InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
    }

    private static long returnGateCount(ServerLevel level) {
        return level.getChunk(0,0).getBlockEntities().values().stream()
                .filter(entity->entity instanceof FamilyGatewayEntity).count();
    }

    private static boolean isGatewayCell(BlockPos root, BlockPos pos) {
        for(int part=0;part<12;part++) if(FamilyGatewayBlock.cell(root,part).equals(pos)) return true;
        return false;
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_natural_arrival")
    public static void previouslyActivatedPhysicalGatewayCreatesExitOnNaturalValeArrival(GameTestHelper h) {
        try(var f=new Fixture(h)) {
            savedUnlock(f);
            h.assertTrue(MushroomVale.safeLanding(f.level,f.owner.blockPosition(),f.owner),
                    "Natural-arrival fixture needs a safe departure; feet="+f.level.getBlockState(f.owner.blockPosition())
                            +", floor="+f.level.getBlockState(f.owner.blockPosition().below()));
            var vale=f.owner.server.getLevel(MushroomVale.LEVEL);
            vale.getChunk(0,0);
            h.assertTrue(MushroomValeReturnGateway.find(vale)==null,"Fresh natural arrival chunk starts without a return gateway");
            BlockPos root=null;
            try {
                f.gateway().interact(f.owner);
                root=MushroomValeReturnGateway.find(vale);
                h.assertTrue(f.owner.serverLevel()==vale && root!=null && returnGateCount(vale)==1,
                        "An owner with a previously activated physical gateway receives one exit on first natural arrival");
                var north=vale.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,root.north(2));
                var south=vale.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,root.south(2));
                h.assertTrue(new net.minecraft.world.level.ChunkPos(root).equals(new net.minecraft.world.level.ChunkPos(0,0))
                        && (MushroomVale.safeLanding(vale,north,f.owner)||MushroomVale.safeLanding(vale,south,f.owner)),
                        "Natural-terrain return gate is visible in the loaded arrival chunk with a safe approach");
                var support=vale.getBlockState(root.below());
                h.assertTrue(support.isFaceSturdy(vale,root.below(),Direction.UP),"Natural terrain provides the gateway's sturdy support");
                for(int part=0;part<12;part++) h.assertTrue(vale.getBlockState(FamilyGatewayBlock.cell(root,part))
                        .getValue(FamilyGatewayBlock.ACTIVE),"The complete return frame is visibly active on natural terrain");
                h.assertTrue(MushroomValeReturns.get(f.owner.server).point(f.owner.getUUID())!=null,
                        "Natural placement preserves the visitor's player-specific return bookmark");
            } finally {
                // Placement requires air in every frame cell. Remove only this test's newly created frame.
                // Otherwise later flat-arrival fixtures would discover a gate down on the natural surface.
                if(root!=null) vale.removeBlock(root,false);
            }
        }
        h.succeed();
    }

    /** Focused travel fixtures start from a valid, previously persisted owner unlock. */
    private static void savedUnlock(Fixture f) {
        f.h.assertTrue(f.place()==InteractionResult.CONSUME,"Departure gateway is placed normally");
        var tag=f.gateway().saveWithFullMetadata(); var owners=new ListTag(); var owner=new CompoundTag();
        owner.putUUID("Owner",f.owner.getUUID()); owners.add(owner); tag.put("AwakenedOwners",owners);
        f.gateway().load(tag);
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_physical_round_trip")
    public static void returnGatewaySupportsRepeatedPhysicalRoundTripsWithoutMovingFamily(GameTestHelper h) {
        var f=new Fixture(h); f.place(); var family=f.family(f.owner,true); f.homes(family); f.gateway().interact(f.owner);
        f.finish(()->{
            try(var arrival=new Arrival(f.owner)) {
                var snapshots=new HashMap<UUID,CompoundTag>();
                family.forEach(mob->snapshots.put(mob.getUUID(),mob.saveWithoutId(new CompoundTag())));
                var departure=f.owner.blockPosition(); f.gateway().interact(f.owner);
                var root=MushroomValeReturnGateway.find(arrival.vale);
                h.assertTrue(root!=null && returnGateCount(arrival.vale)==1 && root.distSqr(f.owner.blockPosition())<=81,
                        "Successful family gateway entry provides one nearby persistent return gateway");
                for(int part=0;part<12;part++) h.assertTrue(arrival.vale.getBlockState(FamilyGatewayBlock.cell(root,part)).getValue(FamilyGatewayBlock.ACTIVE),
                        "The whole return gateway is visibly active without another ritual");
                var exit=(FamilyGatewayEntity)arrival.vale.getBlockEntity(root);
                h.assertTrue(!exit.awakened(f.owner.getUUID()) && !exit.awakening(),"Return needs neither an owner unlock nor companions");
                // An ordinary player clicks the actual block, without developer commands or operator permission.
                f.owner.teleportTo(arrival.vale,root.getX()+.5,root.getY(),root.getZ()-1.5,37,12);
                clickReturn(f.owner,root);
                h.assertTrue(f.owner.serverLevel()==f.level && f.owner.blockPosition().equals(departure),"Physical return reaches the personal departure");
                family.forEach(mob->h.assertTrue(f.level.getEntity(mob.getUUID())==mob && arrival.vale.getEntity(mob.getUUID())==null
                        && snapshots.get(mob.getUUID()).equals(mob.saveWithoutId(new CompoundTag())),
                        "Both gateways preserve each companion instance and complete NBT"));
                // Move the family away, then revisit through the previously awakened gate.
                family.forEach(mob->mob.moveTo(f.root.getX()+30,f.root.getY(),f.root.getZ()));
                f.gateway().interact(f.owner);
                h.assertTrue(f.owner.serverLevel()==arrival.vale && root.equals(MushroomValeReturnGateway.find(arrival.vale))
                        && returnGateCount(arrival.vale)==1 && f.owner.blockPosition().getY()==root.getY()
                        && MushroomVale.safeLanding(arrival.vale,f.owner.blockPosition(),f.owner),
                        "Repeat entry reuses the same gate and lands beside it, without a roof landing or another ritual");
                clickReturn(f.owner,root);
                h.assertTrue(f.owner.serverLevel()==f.level,"A second complete round trip uses only block interactions");
            }
        });
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_return_reload")
    public static void returnGatewayAndTwoPersonalBookmarksSurviveSerializationTogether(GameTestHelper h) {
        try(var f=new Fixture(h);var arrival=new Arrival(f.owner)) {
            var second=f.player(); var firstDeparture=f.owner.blockPosition(); var secondDeparture=second.blockPosition();
            h.assertTrue(!firstDeparture.equals(secondDeparture),"Players have distinct departure points");
            h.assertTrue(MushroomVale.enterPlayer(f.owner)==MushroomVale.TravelResult.ENTERED,"First entry succeeds");
            var root=MushroomValeReturnGateway.ensure(f.owner);
            h.assertTrue(root!=null,"First visitor creates a return gate");
            h.assertTrue(MushroomVale.enterPlayer(second)==MushroomVale.TravelResult.ENTERED
                    && root.equals(MushroomValeReturnGateway.ensure(second)) && returnGateCount(arrival.vale)==1,
                    "Second visitor reuses the shared gate and has a separate safe approach");
            var firstPosition=f.owner.blockPosition(); var secondPosition=second.blockPosition();
            h.assertTrue(!firstPosition.equals(secondPosition),"Visitors cannot land inside each other");
            var liveReturns=MushroomValeReturns.get(f.owner.server);
            var firstSavedPoint=liveReturns.point(f.owner.getUUID());
            var secondSavedPoint=liveReturns.point(second.getUUID());
            var bookmarkTag=liveReturns.save(new CompoundTag());
            var reloaded=MushroomValeReturns.load(bookmarkTag);
            h.assertTrue(firstSavedPoint.equals(reloaded.point(f.owner.getUUID()))
                    && secondSavedPoint.equals(reloaded.point(second.getUUID())),
                    "Serialization reload preserves both independent player bookmarks");
            var exit=(FamilyGatewayEntity)arrival.vale.getBlockEntity(root); var blockTag=exit.saveWithFullMetadata();
            arrival.vale.removeBlockEntity(root);
            arrival.vale.getChunkAt(root).setBlockEntityNbt(blockTag);
            h.assertTrue(root.equals(MushroomValeReturnGateway.find(arrival.vale))
                    && root.equals(MushroomValeReturnGateway.ensure(second)),
                    "Deferred chunk NBT prevents duplicate placement before a live block entity is instantiated");
            var restored=net.minecraft.world.level.block.entity.BlockEntity.loadStatic(root,arrival.vale.getBlockState(root),blockTag);
            h.assertTrue(restored instanceof FamilyGatewayEntity,"Registered chunk NBT reconstructs the same gateway type");
            arrival.vale.setBlockEntity(restored);
            h.assertTrue(root.equals(MushroomValeReturnGateway.ensure(second)) && returnGateCount(arrival.vale)==1,
                    "Reloaded chunk state prevents duplicate placement without a new global save record");
            h.assertTrue(f.owner.saveWithoutId(new CompoundTag()).getString("Dimension").equals(MushroomVale.ID.toString())
                    && second.saveWithoutId(new CompoundTag()).getString("Dimension").equals(MushroomVale.ID.toString()),
                    "Both vanilla player saves retain Vale independently");
            var firstPoint=liveReturns.point(f.owner.getUUID());
            clickReturn(second,root);
            h.assertTrue(second.serverLevel()==f.level && second.blockPosition().equals(secondDeparture)
                    && firstPoint.equals(liveReturns.point(f.owner.getUUID())) && f.owner.blockPosition().equals(firstPosition),
                    "Second player's physical return cannot consume or use the first player's bookmark");
            f.owner.teleportTo(arrival.vale,root.getX()+.5,root.getY(),root.getZ()-1.5,37,12);
            clickReturn(f.owner,root);
            h.assertTrue(f.owner.serverLevel()==f.level && f.owner.blockPosition().equals(firstDeparture)
                    && f.owner.getYRot()==37 && f.owner.getXRot()==12 && liveReturns.point(f.owner.getUUID())==null
                    && liveReturns.point(second.getUUID())==null,"Each live bookmark returns its owner and is consumed separately");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_return_placement_safety")
    public static void returnGatewayFitsThroughNaturalFoliageWithoutChangingNearbyTerrain(GameTestHelper h) {
        try(var f=new Fixture(h);var arrival=new Arrival(f.owner)) {
            savedUnlock(f);
            // Exact return requires an interior departure: landingNear deliberately skips chunk edges.
            // GameTest layout put the original departure at (288,-56,11), where local X is zero.
            var departure=f.owner.blockPosition(); int localX=departure.getX()&15, localZ=departure.getZ()&15;
            departure=departure.offset(localX==0?1:localX==15?-1:0,0,localZ==0?1:localZ==15?-1:0);
            f.owner.moveTo(departure,37,12);
            h.assertTrue((departure.getX()&15)>0 && (departure.getX()&15)<15
                    && (departure.getZ()&15)>0 && (departure.getZ()&15)<15
                    && MushroomVale.safeLanding(f.level,departure,f.owner),
                    "Exact-return fixture starts at a safe interior departure: "+departure);
            // Natural decoration fills unused side clearance around the first safe flat site;
            // keep the actual frame plane empty so this tests the permitted foliage case.
            for(int x=1;x<=3;x++) {
                var grass=new BlockPos(x,301,2);
                arrival.vale.setBlock(grass.below(),Blocks.DIRT.defaultBlockState(),18);
                arrival.vale.setBlock(grass,Blocks.GRASS.defaultBlockState(),18);
                arrival.vale.setBlock(new BlockPos(x,301,4),Blocks.MOSS_CARPET.defaultBlockState(),18);
                h.assertTrue(arrival.vale.getBlockState(grass).canSurvive(arrival.vale,grass),
                        "Side foliage fixture has valid soil and survives normal neighbor updates");
            }
            var chestPos=new BlockPos(8,302,8); arrival.vale.setBlock(chestPos,Blocks.CHEST.defaultBlockState(),18);
            var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)arrival.vale.getBlockEntity(chestPos);
            chest.setItem(0,new ItemStack(Items.DIAMOND,7)); var chestTag=chest.saveWithFullMetadata();
            arrival.vale.setBlock(new BlockPos(8,302,7),Blocks.WATER.defaultBlockState(),18);
            var before=new HashMap<BlockPos,BlockState>(); arrival.saved.keySet().forEach(p->before.put(p,arrival.vale.getBlockState(p)));
            f.gateway().interact(f.owner);
            var point=MushroomValeReturns.get(f.owner.server).point(f.owner.getUUID());
            var root=MushroomValeReturnGateway.find(arrival.vale);
            h.assertTrue(f.owner.serverLevel()==arrival.vale && point!=null && root!=null
                    && FamilyGatewayBlock.complete(arrival.vale,root) && returnGateCount(arrival.vale)==1,
                    "Previously activated physical entry places a complete return gateway through natural foliage");
            boolean usedFoliage=false,usedGroundcover=false;
            for(int x=-1;x<=1;x++)for(int z:new int[]{-1,1}) {
                var state=arrival.vale.getBlockState(root.offset(x,0,z));
                usedFoliage|=state.is(Blocks.GRASS); usedGroundcover|=state.is(Blocks.MOSS_CARPET);
            }
            h.assertTrue(usedFoliage && usedGroundcover,
                    "The selected return site preserves both replaceable foliage and floor-level moss carpet in side clearance");
            h.assertTrue(point.dimension().equals(f.level.dimension().location()) && point.pos().equals(departure)
                    && MushroomVale.safeLanding(f.level,departure,f.owner),
                    "Entry retains the exact personal bookmark and its departure remains safe");
            before.forEach((p,state)->{
                if(!isGatewayCell(root,p)) h.assertTrue(arrival.vale.getBlockState(p).equals(state),"Return placement preserves all terrain and vegetation");
            });
            h.assertTrue(chestTag.equals(chest.saveWithFullMetadata()),"Valuable container and its items remain untouched");
            h.assertTrue(arrival.vale.getBlockState(new BlockPos(8,302,7)).is(Blocks.WATER),"Nearby water is preserved");
            f.owner.teleportTo(arrival.vale,root.getX()+.5,root.getY(),root.getZ()-1.5,37,12); clickReturn(f.owner,root);
            h.assertTrue(f.owner.serverLevel()==f.level && f.owner.blockPosition().equals(point.pos()),
                    "The generated return gate uses the visitor's exact personal departure bookmark");
            h.assertTrue(MushroomValeReturns.get(f.owner.server).point(f.owner.getUUID())==null,"Successful physical return consumes only this bookmark");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_return_placement_veto")
    public static void returnGatewayProtectionVetoRollsBackWithoutLosingTravelOrItems(GameTestHelper h) {
        try(var f=new Fixture(h);var arrival=new Arrival(f.owner)) {
            savedUnlock(f); f.owner.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND,7));
            var vetoed=new ArrayList<BlockPos>();
            Consumer<BlockEvent.EntityMultiPlaceEvent> veto=event->{
                if(event.getEntity()==f.owner && event.getLevel()==arrival.vale) {
                    vetoed.add(event.getPos().immutable()); event.setCanceled(true);
                }
            };
            MinecraftForge.EVENT_BUS.addListener(veto);
            try {f.gateway().interact(f.owner);} finally {MinecraftForge.EVENT_BUS.unregister(veto);}
            h.assertTrue(vetoed.size()==1 && f.owner.serverLevel()==arrival.vale
                    && MushroomValeReturns.get(f.owner.server).point(f.owner.getUUID())!=null && returnGateCount(arrival.vale)==0,
                    "One protected placement attempt rolls back its block entity while preserving successful entry/bookmark");
            boolean explained=f.outbound.get(f.owner.getUUID()).stream()
                    .filter(net.minecraft.network.protocol.game.ClientboundSystemChatPacket.class::isInstance)
                    .map(net.minecraft.network.protocol.game.ClientboundSystemChatPacket.class::cast)
                    .map(net.minecraft.network.protocol.game.ClientboundSystemChatPacket::content)
                    .map(component->component.getContents()).filter(TranslatableContents.class::isInstance)
                    .map(TranslatableContents.class::cast)
                    .anyMatch(content->content.getKey().equals("message.dudunka.gateway.return_unavailable"));
            h.assertTrue(explained,"A failed safe-placement attempt explicitly explains the saved /dudunka return fallback");
            for(var p:arrival.saved.keySet()) h.assertTrue(arrival.vale.getBlockState(p).is(p.getY()==300?Blocks.STONE:Blocks.AIR),
                    "The vetoed attempt rolls back every automatic placement snapshot");
            var dispatcher=new CommandDispatcher<CommandSourceStack>(); MushroomVale.registerCommands(dispatcher);
            try { h.assertTrue(dispatcher.execute("dudunka return",f.owner.createCommandSourceStack().withPermission(0))==1,
                    "The public return command remains usable after placement failure"); }
            catch(CommandSyntaxException e) { throw new AssertionError(e); }
            h.assertTrue(f.owner.serverLevel()==f.level && MushroomValeReturns.get(f.owner.server).point(f.owner.getUUID())==null,
                    "The fallback command consumes the preserved bookmark only after returning successfully");
            f.gateway().interact(f.owner);
            h.assertTrue(f.owner.serverLevel()==arrival.vale,"A later successful physical entry retries automatic placement");
            h.assertTrue(f.owner.getMainHandItem().is(Items.DIAMOND) && f.owner.getMainHandItem().getCount()==7,
                    "Automatic placement never consumes or replaces the held item");
            var root=MushroomValeReturnGateway.ensure(f.owner);
            h.assertTrue(root!=null && returnGateCount(arrival.vale)==1,"A later unprotected attempt may create exactly one gate");
            f.owner.teleportTo(arrival.vale,root.getX()+.5,root.getY(),root.getZ()-1.5,37,12); clickReturn(f.owner,root);
            h.assertTrue(f.owner.serverLevel()==f.level,"Preserved bookmark works after a placement veto");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_return_travel_veto")
    public static void returnGatewayTravelVetoRetainsBookmarkForPhysicalRetry(GameTestHelper h) {
        try(var f=new Fixture(h);var arrival=new Arrival(f.owner)) {
            savedUnlock(f); var source=f.owner.position();
            Consumer<EntityTravelToDimensionEvent> veto=event->{if(event.getEntity()==f.owner)event.setCanceled(true);};
            MinecraftForge.EVENT_BUS.addListener(veto);
            try {f.gateway().interact(f.owner);} finally {MinecraftForge.EVENT_BUS.unregister(veto);}
            h.assertTrue(source.equals(f.owner.position()) && f.owner.serverLevel()==f.level && returnGateCount(arrival.vale)==0,
                    "Denied physical entry creates no return gateway");
            f.gateway().interact(f.owner); var root=MushroomValeReturnGateway.find(arrival.vale);
            h.assertTrue(root!=null,"Successful retry creates the return gateway");
            f.owner.teleportTo(arrival.vale,root.getX()+.5,root.getY(),root.getZ()-1.5,37,12);
            var pos=f.owner.position(); var point=MushroomValeReturns.get(f.owner.server).point(f.owner.getUUID());
            MinecraftForge.EVENT_BUS.addListener(veto);
            try {clickReturn(f.owner,root);} finally {MinecraftForge.EVENT_BUS.unregister(veto);}
            h.assertTrue(f.owner.serverLevel()==arrival.vale && f.owner.position().equals(pos)
                    && point.equals(MushroomValeReturns.get(f.owner.server).point(f.owner.getUUID())),
                    "Denied physical return retains the exact position and personal bookmark");
            clickReturn(f.owner,root);
            h.assertTrue(f.owner.serverLevel()==f.level && MushroomValeReturns.get(f.owner.server).point(f.owner.getUUID())==null,
                    "A deliberate physical retry succeeds and only then consumes the bookmark");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_return_no_loads")
    public static void returnGatewayPlacementAndReuseNeverLoadOrForceAdditionalChunks(GameTestHelper h) {
        try(var f=new Fixture(h);var arrival=new Arrival(f.owner)) {
            h.assertTrue(MushroomVale.enterPlayer(f.owner)==MushroomVale.TravelResult.ENTERED,"Travel loads its ordinary target chunk");
            var remote=new BlockPos(2000000,301,2000000);
            // Keep the probe detached: moving a live ServerPlayer can legitimately add its own chunk ticket.
            var remotePlayer=new ServerPlayer(f.owner.server,arrival.vale,new GameProfile(UUID.randomUUID(),"RemoteGateTest"));
            // ServerPlayer movement updates its listener position even when the player is detached.
            var connection=new Connection(PacketFlow.SERVERBOUND) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet,PacketSendListener listener) {}
            };
            remotePlayer.connection=new ServerGamePacketListenerImpl(f.owner.server,connection,remotePlayer) {
                @Override public void send(Packet<?> packet) {}
                @Override public void send(Packet<?> packet,PacketSendListener listener) {}
            };
            remotePlayer.moveTo(remote,0,0);
            h.assertTrue(!arrival.vale.hasChunkAt(remote) && arrival.vale.getEntity(remotePlayer.getUUID())==null,
                    "Remote probe starts detached in an unloaded chunk");
            var forced=new HashSet<>(arrival.vale.getForcedChunks());
            // Constructor spawn checks may load chunks. Measure only the synchronous gate operations after setup.
            int loaded=arrival.vale.getChunkSource().getLoadedChunksCount();
            var root=MushroomValeReturnGateway.ensure(f.owner);
            h.assertTrue(root!=null && root.equals(MushroomValeReturnGateway.ensure(f.owner)),"Repeated placement reuses one complete gate");
            for(int part=0;part<12;part++)h.assertTrue(new net.minecraft.world.level.ChunkPos(FamilyGatewayBlock.cell(root,part))
                    .equals(new net.minecraft.world.level.ChunkPos(0,0)),"Every frame cell stays in the original arrival chunk");
            h.assertTrue(MushroomValeReturnGateway.ensure(remotePlayer)==null,"Out-of-arrival placement is refused");
            h.assertTrue(!arrival.vale.hasChunkAt(remote),"Out-of-arrival refusal leaves the remote chunk unloaded");
            h.assertTrue(loaded==arrival.vale.getChunkSource().getLoadedChunksCount(),
                    "Placement, reuse and out-of-arrival refusal introduce no loaded chunks; before="+loaded
                            +" after="+arrival.vale.getChunkSource().getLoadedChunksCount());
            h.assertTrue(forced.equals(new HashSet<>(arrival.vale.getForcedChunks())),
                    "Placement, reuse and out-of-arrival refusal introduce no force-loads");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_return_broken")
    public static void returnGatewayIncompleteStructureCannotDuplicateAndCommandStillWorks(GameTestHelper h) {
        try(var f=new Fixture(h);var arrival=new Arrival(f.owner)) {
            h.assertTrue(MushroomVale.enterPlayer(f.owner)==MushroomVale.TravelResult.ENTERED,"Travel succeeds");
            var root=MushroomValeReturnGateway.ensure(f.owner); h.assertTrue(root!=null,"Return gateway exists");
            var part=FamilyGatewayBlock.cell(root,7);
            arrival.vale.setBlockAndUpdate(part,arrival.vale.getBlockState(part).setValue(FamilyGatewayBlock.PART,4));
            var point=MushroomValeReturns.get(f.owner.server).point(f.owner.getUUID());
            h.assertTrue(MushroomValeReturnGateway.ensure(f.owner)==null && returnGateCount(arrival.vale)==1,
                    "A malformed existing frame cannot cause another gateway to be placed beside it");
            f.owner.teleportTo(arrival.vale,root.getX()+.5,root.getY(),root.getZ()-1.5,37,12);
            ((FamilyGatewayEntity)arrival.vale.getBlockEntity(root)).interact(f.owner);
            h.assertTrue(f.owner.serverLevel()==arrival.vale && point.equals(MushroomValeReturns.get(f.owner.server).point(f.owner.getUUID())),
                    "Broken-gate interaction retains the bookmark");
            h.assertTrue(MushroomVale.returnPlayer(f.owner)==MushroomVale.TravelResult.RETURNED,"Shared command travel remains available despite a broken exit");
        }
        h.succeed();
    }

    @GameTest(template="empty", timeoutTicks=200, batch="isolated_gateway_return_entities_and_hazards")
    public static void returnGatewayAvoidsOccupiedSitesAndHazardousGround(GameTestHelper h) {
        try(var f=new Fixture(h);var arrival=new Arrival(f.owner)) {
            h.assertTrue(MushroomVale.enterPlayer(f.owner)==MushroomVale.TravelResult.ENTERED,"Safe entry succeeds");
            var firstRoot=MushroomValeReturnGateway.ensure(f.owner); h.assertTrue(firstRoot!=null,"Baseline site is available");
            arrival.vale.removeBlock(firstRoot,false);
            var resident=DudunkaMod.PUFIK.get().create(arrival.vale); resident.setNoAi(true);
            resident.moveTo(Vec3.atBottomCenterOf(firstRoot));
            h.assertTrue(arrival.vale.addFreshEntity(resident),"Canonical live resident occupies the former frame site");
            try {
                var before=resident.saveWithoutId(new CompoundTag());
                var alternate=MushroomValeReturnGateway.ensure(f.owner);
                h.assertTrue(alternate!=null && !alternate.equals(firstRoot) && returnGateCount(arrival.vale)==1
                        && arrival.vale.getEntity(resident.getUUID())==resident && before.equals(resident.saveWithoutId(new CompoundTag())),
                        "Automatic placement avoids a living resident without moving, replacing or modifying it");
                arrival.vale.removeBlock(alternate,false);
                arrival.unsafe();
                h.assertTrue(MushroomValeReturnGateway.ensure(f.owner)==null && returnGateCount(arrival.vale)==0
                        && MushroomValeReturns.get(f.owner.server).point(f.owner.getUUID())!=null,
                        "Sturdy but hazardous ground cannot support a new return gate or consume the bookmark");
                h.assertTrue(MushroomVale.returnPlayer(f.owner)==MushroomVale.TravelResult.RETURNED,"Return command remains available on placement failure");
            } finally {resident.discard();}
        }
        h.succeed();
    }
}
