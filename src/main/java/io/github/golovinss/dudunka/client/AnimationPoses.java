package io.github.golovinss.dudunka.client;

import io.github.golovinss.dudunka.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Procedural animation for the existing draft models; no additional runtime library. */
public final class AnimationPoses {
    private AnimationPoses() {}
    public static void apply(ModelPart root, Kind kind, Activity activity, float age) {
        if (kind == Kind.MARUSYA) root.getChild("tail").yRot = Mth.sin(age * .05f) * .2f;
        if (kind == Kind.SYUSYA) {
            root.getChild("head").getChild("stalk0").zRot = Mth.sin(age * .04f) * .08f;
            root.getChild("head").getChild("stalk1").zRot = -Mth.sin(age * .04f) * .08f;
        }
        if (activity == Activity.IDLE) root.y += Mth.sin(age * .06f) * .05f;
        if (activity == Activity.SIT || activity == Activity.CAMP_REST || activity == Activity.WAIT_FOR_SYUSYA || activity == Activity.SLEEP) {
            if (kind != Kind.SYUSYA) {
                root.y += kind == Kind.DUDUNKA ? 1.5f : .8f;
                for (int i = 0; i < (kind == Kind.MARUSYA ? 4 : 2); i++)
                    root.getChild("leg" + i).xRot = kind == Kind.DUDUNKA ? -1.2f : (i < 2 ? -.3f : -1.1f);
            }
            if (activity == Activity.SLEEP) {
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
