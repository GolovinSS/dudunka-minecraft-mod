package io.github.golovinss.dudunka.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.golovinss.dudunka.DudunkaMod;
import io.github.golovinss.dudunka.Pufik;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import java.util.Map;
import java.util.WeakHashMap;

public class PufikModel extends EntityModel<Pufik> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(new ResourceLocation(DudunkaMod.ID, "pufik"), "main");
    private final ModelPart root, head, body, face, cap, leftLeg, rightLeg, leftArm, rightArm;
    // Entity renderers share one model: never share interpolation state between Pufiks.
    private final Map<Pufik, LookState> looks = new WeakHashMap<>();

    public PufikModel(ModelPart root) {
        this.root = root;
        head = root.getChild("head");
        body = head.getChild("body");
        face = body.getChild("face");
        cap = head.getChild("cap");
        leftLeg = root.getChild("left_leg");
        rightLeg = root.getChild("right_leg");
        leftArm = root.getChild("left_arm");
        rightArm = root.getChild("right_arm");
    }

    private static void cube(PartDefinition parent, String name, int u, int v,
                             float x, float y, float z, float width, float height, float depth) {
        parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v)
                .addBox(x, y, z, width, height, depth), PartPose.ZERO);
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // The torso narrows at the shoulders and feet, with a broad, soft middle.
        // A shared stem-base pivot carries the cream stem, facial details and cap as one rigid pose.
        // Rest geometry is identical to the former independent body/cap hierarchy.
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.offset(0, 20.5f, 0));
        PartDefinition body = head.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        cube(body, "shoulders", 24, 96, -1.75f, -4.65f, -1.7f, 3.5f, 1.95f, 3.4f);
        cube(body, "torso", 0, 96, -2.15f, -3.25f, -2.0f, 4.3f, 3.55f, 4.0f);
        cube(body, "lower_torso", 0, 112, -1.75f, -.75f, -1.75f, 3.5f, 1.55f, 3.5f);
        cube(body, "belly", 24, 108, -1.55f, -1.5f, -2.12f, 3.1f, 2.2f, .24f);

        // Facial details remain attached to their cream backing when the shared head lifts.
        PartDefinition face = body.addOrReplaceChild("face", CubeListBuilder.create(), PartPose.offset(0, -1.9f, -1.8f));
        // Oversized oval eyes have a warm brown rim, deep pupils and two catchlights.
        cube(face, "left_eye_rim", 32, 80, -1.62f, -.85f, -.40f, 1.28f, 1.72f, .22f);
        cube(face, "right_eye_rim", 32, 80, .34f, -.85f, -.40f, 1.28f, 1.72f, .22f);
        cube(face, "left_eye", 42, 80, -1.48f, -.73f, -.59f, 1.0f, 1.48f, .18f);
        cube(face, "right_eye", 42, 80, .48f, -.73f, -.59f, 1.0f, 1.48f, .18f);
        cube(face, "left_glint", 50, 80, -1.24f, -.48f, -.77f, .28f, .38f, .08f);
        cube(face, "right_glint", 50, 80, .72f, -.48f, -.77f, .28f, .38f, .08f);
        cube(face, "left_glint_small", 50, 80, -1.02f, -.96f, -.77f, .12f, .16f, .08f);
        cube(face, "right_glint_small", 50, 80, .94f, -.96f, -.77f, .12f, .16f, .08f);
        cube(face, "left_cheek", 56, 80, -2.02f, 1.05f, -.50f, .92f, .56f, .2f);
        cube(face, "right_cheek", 56, 80, 1.1f, 1.05f, -.50f, .92f, .56f, .2f);
        cube(face, "smile", 68, 80, -.53f, 1.22f, -.53f, 1.06f, .42f, .2f);
        cube(face, "tongue", 76, 80, -.22f, 1.28f, -.71f, .44f, .16f, .08f);

        // Five receding tiers form a broad domed canopy; the cream underside shows at the brim.
        PartDefinition cap = head.addOrReplaceChild("cap", CubeListBuilder.create(), PartPose.offset(0, -3.9f, 0));
        cube(cap, "gills", 80, 0, -4.641f, .38f, -4.0495f, 9.282f, .72f, 8.099f);
        cube(cap, "outer_brim", 0, 0, -5.5055f, -.78f, -4.914f, 11.011f, 1.35f, 9.828f);
        cube(cap, "lower_dome", 0, 16, -5.096f, -1.72f, -4.368f, 10.192f, 1.25f, 8.736f);
        cube(cap, "middle_dome", 0, 32, -4.186f, -2.72f, -3.64f, 8.372f, 1.25f, 7.28f);
        cube(cap, "upper_dome", 0, 48, -3.185f, -3.7f, -2.73f, 6.37f, 1.25f, 5.46f);
        cube(cap, "crown", 0, 64, -1.9565f, -4.55f, -1.82f, 3.913f, 1.1f, 3.64f);

        // Cream spots sit on the curved terraces and vary in size like the approved concept.
        float[][] spots = {
                {-1.95f,-4.82f,-1.25f,1.45f,.26f,1.05f}, {.55f,-4.82f,.2f,1.35f,.26f,1.1f},
                {-3.25f,-3.98f,1.1f,1.25f,.26f,1.05f}, {2.35f,-3.98f,-2.0f,1.0f,.26f,1.2f},
                {-4.55f,-2.98f,-2.8f,1.1f,.26f,1.1f}, {3.45f,-2.98f,2.15f,1.05f,.26f,1.15f},
                {-5.45f,-1.98f,1.25f,.85f,.26f,1.0f}, {4.75f,-1.98f,-3.7f,.9f,.26f,1.0f},
                {-1.05f,-3.98f,-3.0f,.8f,.26f,.7f}, {.45f,-3.98f,2.35f,.8f,.26f,.8f}
        };
        for (int i = 0; i < spots.length; i++) {
            float[] s = spots[i];
            s[0] *= .91f;
            s[2] *= .91f;
            s[3] *= .91f;
            s[5] *= .91f;
            cube(cap, "spot" + i, 80, 16, s[0], s[1], s[2], s[3], s[4], s[5]);
        }

        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(80, 40).addBox(-.45f, 0, -.65f, .95f, 2.45f, 1.3f), PartPose.offset(-2.15f, 19.0f, 0));
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(80, 40).addBox(-.5f, 0, -.65f, .95f, 2.45f, 1.3f), PartPose.offset(2.15f, 19.0f, 0));
        cube(leftArm, "mitten", 80, 52, -.48f, 1.8f, -.82f, 1.08f, .68f, 1.55f);
        cube(rightArm, "mitten", 80, 52, -.57f, 1.8f, -.82f, 1.08f, .68f, 1.55f);

        // Feet are darker, wider than the little shins, and stepped forward so they read in front view.
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(80, 72).addBox(-.48f, 0, -.45f, .96f, 1.42f, .95f), PartPose.offset(-1.45f, 22.25f, .08f));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(80, 72).addBox(-.48f, 0, -.45f, .96f, 1.42f, .95f), PartPose.offset(1.45f, 22.25f, .08f));
        cube(leftLeg, "foot", 80, 88, -.78f, .68f, -.82f, 1.42f, .55f, 1.45f);
        cube(rightLeg, "foot", 80, 88, -.78f, .68f, -.82f, 1.42f, .55f, 1.45f);
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override public void setupAnim(Pufik entity, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
        PufikPosePreview.Mode preview = PufikPosePreview.mode();
        if (preview != PufikPosePreview.Mode.OFF) {
            // Deterministic full-pose fixture: no AI, distance, idle, walking or emote inputs.
            animatePose(0, 0, 0, 0, 0, 0, 0, preview == PufikPosePreview.Mode.LOOK_UP ? 1 : 0);
            return;
        }
        float partial = Mth.clamp(age - entity.tickCount, 0, 1);
        Player player = entity.level().getNearestPlayer(entity, 3.5);
        float targetWeight = 0;
        float targetPitch = pitch;
        float targetYaw = yaw;
        if (player != null) {
            var delta = player.getEyePosition(partial).subtract(entity.getPosition(partial).add(0, .35, 0));
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            targetWeight = closeRangeWeight(horizontal);
            // Visual tracking reads interpolated player eyes, independent of occasional AI look goals.
            float eyePitch = (float)(-Math.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG);
            targetPitch = Mth.lerp(targetWeight, pitch, eyePitch);
            float bodyYaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
            targetYaw = Mth.wrapDegrees((float)(Math.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90 - bodyYaw);
            targetYaw = Mth.lerp(targetWeight, yaw, Mth.clamp(targetYaw, -35, 35));
        }
        LookState look = looks.computeIfAbsent(entity, ignored -> new LookState(age));
        look.update(age, targetWeight, targetPitch, targetYaw);
        animatePose(limbSwing, limbAmount, age, look.yaw, look.pitch, entity.emote(),
                entity.emoteStrength(partial), look.weight);
    }

    /** Shared render path for live tracking and deterministic developer/geometry previews. */
    public void animatePose(float limbSwing, float limbAmount, float age, float yaw, float pitch,
                            int emote, float strength, float closeWeight) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float walk = Mth.clamp(limbAmount * 2.2f, 0, 1);
        float step = Mth.sin(limbSwing * 1.55f) * walk;
        leftLeg.xRot = step * .55f;
        rightLeg.xRot = -step * .55f;
        leftArm.xRot = -step * .25f;
        rightArm.xRot = step * .25f;
        root.y = -Math.abs(Mth.sin(limbSwing * 1.55f)) * walk * .35f;
        body.xScale = body.zScale = 1 + Mth.sin(age * .12f) * .012f;
        body.yScale = 1 + Mth.sin(age * .12f) * .02f;
        cap.y = -3.9f + Mth.sin(age * .12f + .5f) * .09f;
        cap.zRot = Mth.sin(age * .07f) * .025f;
        head.yRot = Mth.clamp(yaw * Mth.DEG_TO_RAD, -.35f, .35f);
        // Negative X lifts the front (-Z) in Minecraft's downward-Y model coordinates.
        head.xRot = Mth.lerp(closeWeight, faceLookPitch(pitch), -.25f);
        head.y -= closeWeight * .12f;
        cap.xRot = -closeWeight * .06f;
        // Open the camera-to-eyes sightline: rotation alone leaves the front brim in its way.
        cap.z += closeWeight * .75f;
        cap.y -= closeWeight * .45f;
        if (emote == Pufik.HAPPY) {
            root.y -= strength * 1.6f;
            leftArm.zRot = -strength * .5f;
            rightArm.zRot = strength * .5f;
            cap.y -= strength * .16f;
        } else if (emote == Pufik.SHY) {
            cap.xRot += strength * .28f;
            cap.zRot += strength * .15f;
            cap.y += strength * .45f;
            body.xRot = strength * .08f;
        }
    }

    public static float faceLookPitch(float pitchDegrees) {
        return Mth.clamp(pitchDegrees * Mth.DEG_TO_RAD * .35f, -.16f, .06f);
    }

    /** Engage before the brim hides the face: full by 2.5 blocks, easing out at 3.5 blocks. */
    public static float closeRangeWeight(double distance) {
        float t = Mth.clamp((float)(3.5 - distance), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static final class LookState {
        private float age, weight, pitch, yaw;
        private LookState(float age) { this.age = age; }
        private void update(float age, float weight, float pitch, float yaw) {
            // A four-tick time constant; cap delta after culling to avoid a sudden jump on re-entry.
            float dt = Mth.clamp(age - this.age, 0, 1);
            float alpha = 1 - (float)Math.exp(-dt / 4);
            this.weight = Mth.lerp(alpha, this.weight, weight);
            this.pitch = Mth.lerp(alpha, this.pitch, pitch);
            this.yaw += Mth.wrapDegrees(yaw - this.yaw) * alpha;
            this.age = age;
        }
    }

    @Override public void renderToBuffer(PoseStack pose, VertexConsumer consumer, int light, int overlay,
                                          float red, float green, float blue, float alpha) {
        root.render(pose, consumer, light, overlay, red, green, blue, alpha);
    }
}
