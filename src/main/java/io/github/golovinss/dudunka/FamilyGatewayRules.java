package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.*;

/** Stage 3.7 tuning. Reads established friendship/care data without awarding or migrating it. */
public final class FamilyGatewayRules {
    public static final int TRUST = 20, FRIENDSHIP = 10, RADIUS = 8, RITUAL_TICKS = 60;
    public record Eligibility(List<Companion> friends, Component missing) {
        public boolean ready() { return missing == null; }
    }
    private FamilyGatewayRules() {}
    private static Component name(Kind kind) { return Component.translatable("entity.dudunka." + kind.id); }
    private static Eligibility missing(String key, Object... args) {
        return new Eligibility(List.of(), Component.translatable("message.dudunka.gateway." + key, args));
    }
    public static Eligibility inspect(ServerLevel level, BlockPos root, UUID owner) {
        // Entity section lookup inspects loaded entities only. Registry/recovery positions never load chunks.
        return evaluate(level, root, owner, level.getEntitiesOfClass(Companion.class, new AABB(root).inflate(RADIUS)));
    }
    public static Eligibility evaluate(ServerLevel level, BlockPos root, UUID owner, List<Companion> candidates) {
        var selected = new EnumMap<Kind, Companion>(Kind.class);
        var ids = new HashSet<UUID>();
        var carrier = CarrierLedger.get(level.getServer());
        var registry = FamilyRegistry.get(level.getServer());
        for (var mob : candidates) {
            if (!owner.equals(mob.ownerId()) || !mob.isAlive() || mob.isRemoved() || mob.level() != level
                    || mob.distanceToSqr(Vec3.atCenterOf(root)) > RADIUS * RADIUS || carrier.carried(mob.getUUID())) continue;
            if (!ids.add(mob.getUUID())) return missing("duplicate", name(mob.kind));
            if (level.getEntity(mob.getUUID()) != mob) return missing("duplicate", name(mob.kind));
            for (var world : level.getServer().getAllLevels()) {
                var other = world.getEntity(mob.getUUID());
                if (other != null && other != mob) return missing("duplicate", name(mob.kind));
            }
            var indexed = registry.member(mob.getUUID());
            if (indexed != null && (!indexed.owner().equals(owner) || indexed.kind() != mob.kind))
                return missing("duplicate", name(mob.kind));
            if (selected.putIfAbsent(mob.kind, mob) != null) return missing("duplicate_kind", name(mob.kind));
        }
        for (var kind : Kind.values()) {
            var mob = selected.get(kind);
            if (mob == null) return missing("missing", name(kind), RADIUS);
            if (mob.stage() != 2) return missing("young", name(kind));
            if (mob.trust() < TRUST) return missing("trust", name(kind), mob.trust(), TRUST);
            if (mob.isPassenger() || mob.isVehicle() || mob.isLeashed() || mob.isInWaterOrBubble() || mob.isInLava())
                return missing("settle", name(kind));
            if (mob.portalPosing() && !root.equals(mob.portalFocus())) return missing("friend_busy", name(kind));
        }
        var friends = List.copyOf(selected.values());
        var bonds = FamilyFriendships.get(level.getServer());
        for (int a = 0; a < friends.size(); a++) for (int b = a + 1; b < friends.size(); b++) {
            var first = friends.get(a); var second = friends.get(b);
            int score = bonds.score(owner, first.getUUID(), second.getUUID());
            if (score < FRIENDSHIP) return missing("friendship", name(first.kind), name(second.kind), score, FRIENDSHIP);
        }
        return new Eligibility(friends, null);
    }
}
