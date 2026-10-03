package io.github.golovinss.dudunka;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

public final class FamilyAchievements {
    private FamilyAchievements() {}
    public static void award(Companion mob, String id) {
        if (!(mob.ownerPlayer() instanceof ServerPlayer player)) return;
        var advancement = player.server.getAdvancements().getAdvancement(new ResourceLocation(DudunkaMod.ID, id));
        if (advancement != null) player.getAdvancements().award(advancement, "event");
    }
    public static void checkFamily(Companion mob) {
        var nearby = mob.level().getEntitiesOfClass(Companion.class, mob.getBoundingBox().inflate(8), mob::sameFamily);
        var kinds = java.util.EnumSet.noneOf(Kind.class);
        kinds.add(mob.kind);
        nearby.forEach(other -> kinds.add(other.kind));
        if (kinds.size() == Kind.values().length) award(mob, "all_home");
    }
}
