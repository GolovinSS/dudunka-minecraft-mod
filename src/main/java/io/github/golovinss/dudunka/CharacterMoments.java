package io.github.golovinss.dudunka;

/** Small visual variations; no changes to movement, care, rewards or loot RNG. */
public final class CharacterMoments {
    public static final int VARIANTS=3;
    public enum Feelers { CALM, INTERESTED, RETRACTED }
    private CharacterMoments(){}
    public static int bounded(int variant){return Math.max(0,Math.min(VARIANTS-1,variant));}
    public static Feelers feelers(Companion mob){
        if(mob.kind!=Kind.SYUSYA)return Feelers.CALM;
        var activity=mob.activity();
        if(mob.isInWaterOrBubble() || mob.level().isRainingAt(mob.blockPosition()) || activity==Activity.RETREAT || activity==Activity.SLEEP)return Feelers.RETRACTED;
        if(activity==Activity.PEEK || activity==Activity.NIBBLE || activity==Activity.SEEK_PLANT)return Feelers.INTERESTED;
        var owner=mob.ownerPlayer();
        return owner!=null && owner.isAlive() && !owner.isSpectator() && mob.distanceToSqr(owner)<=9 && mob.hasLineOfSight(owner)
            && (mob.kind.likes(owner.getMainHandItem()) || mob.kind.likes(owner.getOffhandItem()))?Feelers.INTERESTED:Feelers.CALM;
    }
}
