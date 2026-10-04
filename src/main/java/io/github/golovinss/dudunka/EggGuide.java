package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;

/** Localized pages with optional places enabled by the SERVER's mod/config snapshot. */
public final class EggGuide {
    public static final int MVS=1,DUNGEONS=2,BOP=4,NATURAL=8,LOOT=16,PAGES=6;
    private EggGuide() {}
    public static int flags(){
        var mods=ModList.get();return (mods.isLoaded("mvs")?MVS:0)|(mods.isLoaded("betterdungeons")?DUNGEONS:0)
            |(mods.isLoaded("biomesoplenty")?BOP:0)|(DudunkaMod.NATURAL_EGGS.get()?NATURAL:0)|(DudunkaMod.LOOT_MULTIPLIER.get()>0?LOOT:0);
    }
    public static List<Component> page(int index,int flags){
        var lines=new ArrayList<Component>();String id=switch(index){case 1->"dudunka";case 2->"marusya";case 3->"syusya";case 4->"old_world";case 5->"care";default->"intro";};
        lines.add(Component.translatable("guide.dudunka."+id+".title"));lines.add(Component.empty());
        lines.add(Component.translatable("guide.dudunka."+id+".body"));
        if(index>=1 && index<=3){
            if((flags&LOOT)==0)lines.add(Component.translatable("guide.dudunka.loot_disabled"));
            else {
                if((flags&MVS)!=0)lines.add(Component.translatable("guide.dudunka."+id+".mvs"));
                if((flags&DUNGEONS)!=0 && index>=2)lines.add(Component.translatable("guide.dudunka."+id+".dungeons"));
            }
            if(index==3){
                if((flags&NATURAL)==0)lines.add(Component.translatable("guide.dudunka.natural_disabled"));
                else {lines.add(Component.translatable("guide.dudunka.syusya.natural"));if((flags&BOP)!=0)lines.add(Component.translatable("guide.dudunka.syusya.bop"));}
            }
            lines.add(Component.empty());lines.add(Component.translatable("guide.dudunka.rare"));
        }
        return List.copyOf(lines);
    }
}
