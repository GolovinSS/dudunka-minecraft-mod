package io.github.golovinss.dudunka;

import java.util.*;
import net.minecraft.network.chat.Component;

/** Each friend's three notes form an independent story; existing Dudunka bits stay 0..2. */
public final class FriendStories {
    public static final int NOTES=9,MASK=(1<<NOTES)-1;
    private FriendStories(){}
    public static Kind kind(int note){if(note<1 || note>NOTES)throw new IllegalArgumentException("Invalid note");return note<=3?Kind.DUDUNKA:note<=6?Kind.MARUSYA:Kind.SYUSYA;}
    public static int offset(Kind kind){return switch(kind){case DUDUNKA->0;case MARUSYA->3;case SYUSYA->6;};}
    public static int page(int note){kind(note);return (note-1)%3+1;}
    public static int unlocked(int mask,Kind kind){return TrailProgress.unlocked(mask>>offset(kind));}
    public static List<Component> page(Kind kind,int step,int flags,int mask){
        if(kind==Kind.DUDUNKA || step<0 || step>3)throw new IllegalArgumentException("Invalid friend story");
        String prefix="story.dudunka."+kind.id+".";var lines=new ArrayList<Component>();
        lines.add(Component.translatable(prefix+"title",step+1));lines.add(Component.empty());
        if(step>unlocked(mask,kind)){
            lines.add(Component.translatable("trail.dudunka.locked"));
            if((mask&(1<<(offset(kind)+step-1)))!=0)lines.add(Component.translatable("trail.dudunka.saved_ahead"));
            // A useful route remains visible when a player browses a locked page.
            lines.add(Component.translatable(prefix+"places"));
        }else{
            lines.add(Component.translatable(prefix+"step."+step));lines.add(Component.empty());
            lines.add(Component.translatable(prefix+"places"));
            if((flags&EggGuide.MVS)!=0)lines.add(Component.translatable(prefix+"mvs"));
            if((flags&EggGuide.DUNGEONS)!=0)lines.add(Component.translatable(prefix+"dungeons"));
        }
        if((flags&EggGuide.NOTES)==0)lines.add(Component.translatable("trail.dudunka.disabled"));
        lines.add(Component.translatable("story.dudunka.optional"));return List.copyOf(lines);
    }
}
