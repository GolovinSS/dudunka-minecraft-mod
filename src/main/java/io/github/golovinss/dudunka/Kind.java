package io.github.golovinss.dudunka;

import net.minecraft.world.item.*;

public enum Kind {
    DUDUNKA("dudunka", .70f, .24), MARUSYA("marusya", .65f, .28), SYUSYA("syusya", .28f, .08);
    public final String id;
    public final float height;
    public final double speed;
    Kind(String id, float height, double speed) { this.id=id; this.height=height; this.speed=speed; }
    public boolean likes(ItemStack s) {
        return switch(this) {
            case DUDUNKA -> s.is(Items.APPLE)||s.is(Items.BREAD)||s.is(Items.COOKIE);
            case MARUSYA -> s.is(Items.COD)||s.is(Items.SALMON);
            case SYUSYA -> s.is(Items.APPLE)||s.is(Items.BROWN_MUSHROOM)||s.is(Items.RED_MUSHROOM)||s.is(net.minecraft.tags.ItemTags.LEAVES);
        };
    }
    public int offering(ItemStack s) {
        return switch(this) {
            case DUDUNKA -> s.is(Items.BREAD)?1:s.is(Items.APPLE)?2:s.is(Items.COOKIE)?4:0;
            case MARUSYA -> s.is(Items.COD)||s.is(Items.SALMON)?1:0;
            case SYUSYA -> s.is(net.minecraft.tags.ItemTags.LEAVES)?1:s.is(Items.BROWN_MUSHROOM)||s.is(Items.RED_MUSHROOM)?2:0;
        };
    }
    public int requiredOfferings() { return this==DUDUNKA?7:this==SYUSYA?3:1; }
}
