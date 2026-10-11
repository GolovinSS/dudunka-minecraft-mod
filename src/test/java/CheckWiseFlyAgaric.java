import io.github.golovinss.dudunka.client.*;
import io.github.golovinss.dudunka.WiseFlyAgaric;
import com.mojang.blaze3d.vertex.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3d;
public class CheckWiseFlyAgaric {
 public static void main(String[] args) throws Exception {
  var root=WiseFlyAgaricModel.create().bakeRoot();var model=new WiseFlyAgaricModel(root);
  var torso=root.getChild("torso");var head=torso.getChild("head");
  for(String part:new String[]{"face","eye-1","eye1","mouth","beard_tip","cap"})if(!head.hasChild(part))throw new AssertionError(part);
  for(String part:new String[]{"cloak","belt","pouch","book","right_arm"})if(!torso.hasChild(part))throw new AssertionError(part);
  var atlas=ImageIO.read(Path.of("src/main/resources/assets/dudunka/textures/entity/wise_fly_agaric.png").toFile());
  if(atlas.getWidth()!=128||atlas.getHeight()!=64)throw new AssertionError("atlas dimensions");
  var colors=new HashSet<Integer>();for(int y=0;y<64;y++)for(int x=0;x<128;x++){int c=atlas.getRGB(x,y);if((c>>>24)!=255)throw new AssertionError("opaque atlas");colors.add(c);}
  if(colors.size()<25)throw new AssertionError("shaded palette");
  for(String lang:new String[]{"ru_ru","en_us"}) {
   var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/dudunka/lang/"+lang+".json"))).getAsJsonObject();
   if(!json.has("entity.dudunka.wise_fly_agaric"))throw new AssertionError("name");
   for(int i=0;i<6;i++)if(!json.has("message.dudunka.wise_fly_agaric."+i))throw new AssertionError("dialogue");
  }
  var events=Files.readString(Path.of("src/main/java/io/github/golovinss/dudunka/client/ClientEvents.java"));
  if(!events.contains("WiseFlyAgaricModel.LAYER,WiseFlyAgaricModel::create")||!events.contains("DudunkaMod.WISE_FLY_AGARIC.get(),WiseFlyAgaricRenderer::new"))throw new AssertionError("renderer wiring");
  for(int emote=0;emote<=3;emote++)for(int t=0;t<240;t++) {
   model.animate(t*.1f,.3f,t,35,-70,1,emote,(float)Math.sin(t*Math.PI/240));
   root.getAllParts().forEach(p->{if(!Float.isFinite(p.x+p.y+p.z+p.xRot+p.yRot+p.zRot)||p.xScale!=1||p.yScale!=1||p.zScale!=1||Math.abs(p.xRot)>1.2)throw new AssertionError("rigid finite poses");});
   root.render(new PoseStack(),new Geometry(),0,0);
  }
  for(double d:new double[]{1,1.5,2,2.5,2.75,3}) {
   float close=(float)Math.min(1,3.5-d),pitch=(float)-Math.toDegrees(Math.atan2(1.62-.82,d));
   model.animate(0,0,0,0,pitch,close,0,0);
   int hidden=occludedFeatures(root,d);if(hidden>0)throw new AssertionError("Camera at "+d+" hides "+hidden+" features");
  }
  model.animate(0,0,0,0,0,0,0,0);float rest=torso.y;
  model.animate(0,0,20,0,0,0,0,0);if(Math.abs(torso.y-rest)<.01f)throw new AssertionError("breathing");
  model.animate(.7f,.3f,0,0,0,0,0,0);if(Math.abs(root.getChild("left_leg").xRot)<.1f||root.getChild("left_leg").xRot!=-root.getChild("right_leg").xRot)throw new AssertionError("alternating steps");
  model.animate(0,0,0,0,0,0,2,1);if(head.zRot<.15f)throw new AssertionError("thoughtful tilt");
  model.animate(0,0,0,0,0,0,1,1);if(torso.getChild("left_arm").zRot>-.6f)throw new AssertionError("visible greeting");
  model.animate(0,0,0,0,-50,1,0,0);if(head.xRot>-.4f)throw new AssertionError("visible look up");
  model.animate(0,0,0,0,0,0,3,1);if(!torso.getChild("book").visible)throw new AssertionError("reading prop");
  model.animate(0,0,0,0,0,0,0,0);if(torso.getChild("book").visible||head.xRot!=0||head.zRot!=0)throw new AssertionError("state leaks");
  var sink=new Geometry();root.render(new PoseStack(),sink,0,0);
  double min=sink.vertices.stream().mapToDouble(v->v[1]).min().orElseThrow(),max=sink.vertices.stream().mapToDouble(v->v[1]).max().orElseThrow();
  if(max-min<1||max-min>1.2)throw new AssertionError("height "+(max-min));
  var previews=new StringBuilder("[");
  for(String pose:new String[]{"front","side","back","three-quarter","idle","walk","look-up","greeting","think","read"}) {
   model.animate(pose.equals("walk")?.7f:0,pose.equals("walk")?.3f:0,20,0,pose.equals("look-up")?-50:0,pose.equals("look-up")?1:0,pose.equals("greeting")?1:pose.equals("think")?2:pose.equals("read")?3:0,1);
   sink=new Geometry();root.render(new PoseStack(),sink,0,0);
   if(previews.length()>1)previews.append(',');previews.append("{\"pose\":\"").append(pose).append("\",\"vertices\":[");
   boolean first=true;for(var v:sink.vertices){if(!first)previews.append(',');first=false;previews.append(Arrays.toString(Arrays.copyOf(v,5)));}previews.append("]}");
  }
  Files.writeString(Path.of("build/wise-fly-agaric-preview.json"),previews.append(']').toString());
  System.out.println("PASS Wise Fly Agaric: "+root.getAllParts().count()+" parts, "+(max-min)+" block height, atlas/UVs/locales, 960 rigid poses, six close camera distances");
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
        for (String feature : new String[]{"eye-1", "eye1", "mouth"}) {
            var target = new Vector3d();
            int count = 0;
            for (double[] v : geometry.get("/torso/head/" + feature).vertices) {
                if (v[7] < -.7) { target.add(v[0], v[1], v[2]); count++; }
            }
            if (count != 4) throw new AssertionError("Expected a forward-facing feature quad");
            target.div(count);
            var samples = new ArrayList<Vector3d>();
            samples.add(target);
            // Check most of each feature's front area, not only its center.
            for (double[] v : geometry.get("/torso/head/" + feature).vertices)
                if (v[7] < -.7) samples.add(new Vector3d(v[0], v[1], v[2]).lerp(target, .2));
            boolean blocked = false;
            for (var sample : samples) for (var entry : geometry.entrySet()) {
                if (entry.getKey().equals("/torso/head/" + feature) || entry.getKey().contains("glint")) continue;
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
