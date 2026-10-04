package io.github.golovinss.dudunka.client;

import io.github.golovinss.dudunka.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Procedural animation for the existing draft models; no additional runtime library. */
public final class AnimationPoses {
    private AnimationPoses() {}
    public static void apply(ModelPart root, Kind kind, Activity activity, float age) {
        if(kind==Kind.DUDUNKA && root.getChild("arm1").hasChild("drawing_sheet"))root.getChild("arm1").getChild("drawing_sheet").visible=activity==Activity.SHOW_DRAWING;
        if (kind == Kind.MARUSYA) root.getChild("tail").yRot = Mth.sin(age * .05f) * .2f;
        if (kind == Kind.SYUSYA) {
            root.getChild("head").getChild("stalk0").zRot = Mth.sin(age * .04f) * .08f;
            root.getChild("head").getChild("stalk1").zRot = -Mth.sin(age * .04f) * .08f;
        }
        if (activity == Activity.IDLE) root.y += Mth.sin(age * .06f) * .05f;
        if (activity == Activity.SIT || activity == Activity.CAMP_REST || activity == Activity.WAIT_FOR_SYUSYA || activity == Activity.SLEEP || activity == Activity.CURL) {
            if (kind != Kind.SYUSYA) {
                root.y += kind == Kind.DUDUNKA ? 1.5f : .8f;
                for (int i = 0; i < (kind == Kind.MARUSYA ? 4 : 2); i++)
                    root.getChild("leg" + i).xRot = kind == Kind.DUDUNKA ? -1.2f : (i < 2 ? -.3f : -1.1f);
            }
            if (activity == Activity.SLEEP || activity == Activity.CURL) {
                root.getChild("head").xRot = .25f;
                root.getChild("head").yRot = .35f;
            }
        }
        if (kind == Kind.DUDUNKA) {
            if (activity == Activity.WAVE) root.getChild("arm0").zRot = 2.6f + Mth.sin(age * .35f) * .25f;
            if (activity == Activity.ADJUST_GLASSES) {
                root.getChild("arm1").xRot = -2.1f;
                root.getChild("arm1").zRot = -.35f;
            }
        }
        if(kind==Kind.DUDUNKA && (activity==Activity.DRAW || activity==Activity.SHOW_DRAWING)){
            root.getChild("head").xRot=activity==Activity.DRAW?.5f:0;
            root.getChild("arm0").xRot=-1.3f+Mth.sin(age*.28f)*.12f;
            root.getChild("arm1").xRot=activity==Activity.DRAW?-1.1f:-1.7f;
            root.getChild("arm1").zRot=activity==Activity.SHOW_DRAWING?-.18f:0;
        }
        if(kind==Kind.MARUSYA && activity==Activity.STRETCH){
            root.getChild("body").xRot=.12f;root.getChild("head").xRot=-.15f;
            root.getChild("leg0").xRot=-.55f;root.getChild("leg1").xRot=-.55f;
            root.getChild("tail").xRot=-.8f;
        }
        if(kind==Kind.MARUSYA && activity==Activity.SCRATCH){
            root.getChild("leg0").xRot=-1.25f+Mth.sin(age*.5f)*.3f;
            root.getChild("leg1").xRot=-1.25f-Mth.sin(age*.5f)*.3f;root.getChild("head").xRot=-.2f;
        }
        if(kind==Kind.MARUSYA && activity==Activity.CURL){
            root.getChild("head").yRot=.6f;root.getChild("head").xRot=.25f;root.getChild("tail").yRot=1.2f;
        }
        if(kind==Kind.SYUSYA && (activity==Activity.RETREAT || activity==Activity.SLEEP)){
            root.getChild("head").z+=1.2f;root.getChild("head").getChild("stalk0").xRot=.8f;root.getChild("head").getChild("stalk1").xRot=.8f;
        }
        if(kind==Kind.SYUSYA && activity==Activity.PEEK){root.getChild("head").z+=.5f+Mth.sin(age*.08f)*.3f;root.getChild("head").yRot=Mth.sin(age*.06f)*.15f;}
        if(kind==Kind.SYUSYA && activity==Activity.NIBBLE){root.getChild("head").xRot=.25f+Mth.sin(age*.2f)*.1f;root.getChild("head").z-=.2f;}
        if (activity == Activity.PURR && kind == Kind.MARUSYA) {
            root.getChild("head").zRot=Mth.sin(age*.08f)*.12f;
            root.getChild("tail").yRot=Mth.sin(age*.08f)*.35f;
        }
        if (activity == Activity.ALERT && kind == Kind.MARUSYA) {
            root.getChild("tail").xRot = root.getChild("tail").hasChild("tip") ? .25f : -1.1f;
            root.getChild("head").getChild("ear0").xRot = -.15f;
            root.getChild("head").getChild("ear1").xRot = -.15f;
        }
    }
}
