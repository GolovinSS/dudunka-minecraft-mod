import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.golovinss.dudunka.client.PufikModel;
import net.minecraft.client.model.geom.ModelPart;
import io.github.golovinss.dudunka.Pufik;
import io.github.golovinss.dudunka.MushroomVale;
import io.github.golovinss.dudunka.client.PufikPosePreview;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import java.util.Map;
import java.util.LinkedHashMap;
import org.joml.Vector3d;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class CheckPufikModel {
    public static void main(String[] args) throws Exception {
        var root = PufikModel.create().bakeRoot();
        for (String part : new String[]{"head", "left_leg", "right_leg", "left_arm", "right_arm"})
            if (!root.hasChild(part)) throw new AssertionError("Missing Pufik part: " + part);
        var head = root.getChild("head");
        var body = head.getChild("body");
        var face = body.getChild("face");
        var cap = head.getChild("cap");
        if (!face.hasChild("left_eye") || !face.hasChild("right_cheek")
                || !cap.hasChild("crown") || !cap.hasChild("spot7"))
            throw new AssertionError("Pufik face or tiered spotted cap is incomplete");
        if (PufikModel.closeRangeWeight(2.5) != 1 || PufikModel.closeRangeWeight(3.5) != 0
                || Math.abs(PufikModel.closeRangeWeight(3) - .5f) > .0001f)
            throw new AssertionError("Proximity must fully engage before entity overlap");
        var model = new PufikModel(root);
        model.animatePose(0, 0, 0, 0, 0, 0, 0, 0);
        int hiddenAtRest = occludedFeatures(root, 1);
        checkPreviewCommand(model, head);
        model.animatePose(0, 0, 0, 0, 0, 0, 0, 1);
        if (head.xRot != -.25f || cap.xRot != -.06f || face.xRot != 0)
            throw new AssertionError("Shared head must lift the stem, face and cap together");
        for (double distance : new double[]{1, 1.5, 2, 2.5, 2.75, 3}) {
            int hidden = occludedFeatures(root, distance);
            if (hidden != 0) throw new AssertionError("Look-up pose hides " + hidden
                    + " eye/smile front surfaces from a standing camera at " + distance + " blocks");
        }
        for (double distance : new double[]{1, 1.5, 2, 2.5, 2.75, 3}) {
            float weight = PufikModel.closeRangeWeight(distance);
            float eyePitch = (float)-Math.toDegrees(Math.atan2(1.62 - .35, distance));
            model.animatePose(0, 0, 0, 0, eyePitch * weight, 0, 0, weight);
            if (occludedFeatures(root, distance) != 0)
                throw new AssertionError("Settled live-tracking pose occludes the face at " + distance + " blocks");
        }
        if (hiddenAtRest == 0) throw new AssertionError("Camera fixture must reproduce resting brim occlusion");
        // Reproduce the previous full-strength pose: decorative face rotation with a fixed stem.
        model.animatePose(0, 0, 0, 0, 0, 0, 0, 0);
        face.xRot = -.30f;
        body.y -= .12f;
        cap.xRot = -.06f;
        if (occludedFeatures(root, 1) == 0)
            throw new AssertionError("Camera fixture must reproduce the previous ineffective face-only pose");
        for (int emote : new int[]{Pufik.HAPPY, Pufik.SHY}) {
            model.animatePose(1, 1, 12, 0, 0, emote, 1, 1);
            if (head.xRot != -.25f || root.getChild("left_leg").xRot == 0)
                throw new AssertionError("Emotes must preserve the head pose and walking");
            if (emote == Pufik.HAPPY && root.y > -1.6f || emote == Pufik.SHY && cap.xRot <= 0)
                throw new AssertionError("Happy hop and shy cap tilt must remain active");
        }
        // Shared renderer model must reset cleanly for the next entity.
        model.animatePose(0, 0, 0, 0, 0, 0, 0, 0);
        if (head.xRot != 0 || face.xRot != 0 || cap.xRot != 0 || head.y != 20.5f)
            throw new AssertionError("Look-up pose leaked into the next entity/rest pose");
        root.getAllParts().forEach(ModelPart::resetPose);
        long parts = root.getAllParts().count();
        if (parts < 25) throw new AssertionError("Pufik silhouette has too few parts: " + parts);
        var sink = new Geometry();
        root.render(new PoseStack(), sink, 0, 0);
        double minY=Double.POSITIVE_INFINITY, maxY=Double.NEGATIVE_INFINITY, minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;
        for (double[] vertex : sink.vertices) {
            minY=Math.min(minY,vertex[1]); maxY=Math.max(maxY,vertex[1]);
            minX=Math.min(minX,vertex[0]); maxX=Math.max(maxX,vertex[0]);
        }
        double height=(maxY-minY)*16, width=(maxX-minX)*16;
        if (height < 10 || height > 12 || width < 10.5 || width > 12.5 || sink.vertices.size() < 500)
            throw new AssertionError("Pufik must have a rounded, slightly taller 0.6-block silhouette: " + width + "×" + height);
        var image=ImageIO.read(Path.of("src/main/resources/assets/dudunka/textures/entity/pufik.png").toFile());
        if (image == null || image.getWidth()!=128 || image.getHeight()!=128) throw new AssertionError("Pufik atlas must be a 128×128 PNG");
        for (String lang : new String[]{"en_us","ru_ru"}) {
            var path=Path.of("src/main/resources/assets/dudunka/lang/"+lang+".json");
            var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (!json.has("entity.dudunka.pufik") || !json.has("message.dudunka.pufik_greeting"))
                throw new AssertionError("Missing localized Pufik name or greeting in " + lang);
        }
        System.out.println("PASS Pufik model: " + parts + " parts, " + sink.vertices.size() + " vertices, " + width + "×" + height + " pixels, atlas/locales, posed camera clearance and emote composition");
    }
    private static void checkPreviewCommand(PufikModel model, ModelPart head) throws Exception {
        String original = System.getProperty(MushroomVale.DEV_PROPERTY);
        var source = new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, 4,
                "pose-check", Component.literal("pose-check"), null, null);
        try {
            System.setProperty(MushroomVale.DEV_PROPERTY, "false");
            var disabled = new CommandDispatcher<CommandSourceStack>();
            PufikPosePreview.register(new RegisterClientCommandsEvent(disabled, null));
            if (disabled.getRoot().getChild("dudunka_pufik_pose") != null)
                throw new AssertionError("Pose preview must be developer-only");
            System.setProperty(MushroomVale.DEV_PROPERTY, "true");
            var enabled = new CommandDispatcher<CommandSourceStack>();
            PufikPosePreview.register(new RegisterClientCommandsEvent(enabled, null));
            enabled.execute("dudunka_pufik_pose look_up", source);
            // Null entity proves the real setupAnim preview does not read AI or distance.
            model.setupAnim(null, 1, 1, 100, 90, 60);
            if (head.xRot != -.25f || head.yRot != 0)
                throw new AssertionError("Developer look-up must bypass live animation inputs");
            enabled.execute("dudunka_pufik_pose rest", source);
            model.setupAnim(null, 10, 1, 200, -90, -60);
            if (head.xRot != 0 || head.yRot != 0)
                throw new AssertionError("Developer rest must be deterministic");
            enabled.execute("dudunka_pufik_pose off", source);
            if (PufikPosePreview.mode() != PufikPosePreview.Mode.OFF)
                throw new AssertionError("Preview off must restore live tracking");
        } finally {
            if (original == null) System.clearProperty(MushroomVale.DEV_PROPERTY);
            else System.setProperty(MushroomVale.DEV_PROPERTY, original);
        }
    }

    /** Cast actual baked triangles toward eye/smile front surfaces from a 1.62-block camera. */
    private static int occludedFeatures(ModelPart root, double distance) {
        Map<String, Geometry> geometry = new LinkedHashMap<>();
        root.visit(new PoseStack(), (pose, path, index, cube) -> {
            var sink = new Geometry();
            cube.compile(pose, sink, 0, 0, 1, 1, 1, 1);
            geometry.put(path, sink);
        });
        Vector3d camera = new Vector3d(0, 1.501 - 1.62, -distance);
        int occluded = 0;
        for (String feature : new String[]{"left_eye", "right_eye", "smile"}) {
            var target = new Vector3d();
            int count = 0;
            for (double[] v : geometry.get("/head/body/face/" + feature).vertices) {
                if (v[7] < -.8) { target.add(v[0], v[1], v[2]); count++; }
            }
            if (count != 4) throw new AssertionError("Expected a forward-facing feature quad");
            target.div(count);
            var samples = new ArrayList<Vector3d>();
            samples.add(target);
            // Check most of each feature's front area, not only its center.
            for (double[] v : geometry.get("/head/body/face/" + feature).vertices)
                if (v[7] < -.8) samples.add(new Vector3d(v[0], v[1], v[2]).lerp(target, .2));
            boolean blocked = false;
            for (var sample : samples) for (var entry : geometry.entrySet()) {
                if (entry.getKey().startsWith("/head/body/face/")) continue;
                var vertices = entry.getValue().vertices;
                for (int i = 0; i < vertices.size(); i += 4) {
                    if (intersects(camera, sample, vertices.get(i), vertices.get(i+1), vertices.get(i+2))
                            || intersects(camera, sample, vertices.get(i), vertices.get(i+2), vertices.get(i+3))) {
                        blocked = true;
                    }
                }
            }
            if (blocked) occluded++;
        }
        return occluded;
    }

    private static boolean intersects(Vector3d camera, Vector3d target, double[] a, double[] b, double[] c) {
        var origin = new Vector3d(a[0], a[1], a[2]);
        var edge1 = new Vector3d(b[0], b[1], b[2]).sub(origin);
        var edge2 = new Vector3d(c[0], c[1], c[2]).sub(origin);
        var direction = new Vector3d(target).sub(camera);
        var h = new Vector3d(direction).cross(edge2);
        double det = edge1.dot(h);
        if (Math.abs(det) < 1e-10) return false;
        var s = new Vector3d(camera).sub(origin);
        double u = s.dot(h) / det;
        if (u < 0 || u > 1) return false;
        var q = s.cross(edge1);
        double v = direction.dot(q) / det;
        double t = edge2.dot(q) / det;
        return v >= 0 && u + v <= 1 && t > .00001 && t < .99999;
    }

    private static class Geometry implements VertexConsumer {
        final java.util.List<double[]> vertices=new ArrayList<>();
        double x,y,z; float u,v,nx,ny,nz;
        public VertexConsumer vertex(double a,double b,double c){x=a;y=b;z=c;return this;}
        public VertexConsumer uv(float a,float b){u=a;v=b;return this;}
        public void endVertex(){if(u<0 || v<0 || u>1 || v>1)throw new AssertionError("Pufik UV outside atlas");vertices.add(new double[]{x,y,z,u,v,nx,ny,nz});}
        public VertexConsumer color(int a,int b,int c,int d){return this;}
        public VertexConsumer overlayCoords(int a,int b){return this;}
        public VertexConsumer uv2(int a,int b){return this;}
        public VertexConsumer normal(float a,float b,float c){nx=a;ny=b;nz=c;return this;}
        public void defaultColor(int a,int b,int c,int d){}
        public void unsetDefaultColor(){}
    }
}
