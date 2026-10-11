import io.github.golovinss.dudunka.client.FluffyJumperModel;
import com.mojang.blaze3d.vertex.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.model.geom.ModelPart;

/** Bake the actual client geometry, render finite poses and inspect resource wiring offline. */
public class CheckFluffyJumper {
    public static void main(String[] args) throws Exception {
        var root=FluffyJumperModel.create().bakeRoot();var model=new FluffyJumperModel(root);var fluff=root.getChild("fluff");
        for(String part:new String[]{"core","crown","forelock","belly","left_eye","right_eye","smile","cheek-1","cheek1"})
            if(!fluff.hasChild(part))throw new AssertionError("Missing distinctive anatomy: "+part);
        for(int i=0;i<12;i++)if(!fluff.hasChild("ruff"+i)||!fluff.hasChild("top_tuft"+i))throw new AssertionError("All-round cuboid fluff");
        for(int i=0;i<4;i++)if(!fluff.hasChild("paw"+i))throw new AssertionError("Tiny paws");
        var palettes=new HashSet<Integer>();
        for(int i=0;i<3;i++) {
            var atlas=ImageIO.read(Path.of("src/main/resources/assets/dudunka/textures/entity/fluffy_jumper_"+i+".png").toFile());
            if(atlas.getWidth()!=128||atlas.getHeight()!=64)throw new AssertionError("Atlas dimensions");
            var colors=new HashSet<Integer>();
            for(int y=0;y<64;y++)for(int x=0;x<128;x++) {int c=atlas.getRGB(x,y);if((c>>>24)!=255)throw new AssertionError("Opaque pixel atlas");colors.add(c);}
            if(colors.size()<25)throw new AssertionError("Detailed shaded palette");palettes.add(atlas.getRGB(0,0));
        }
        if(palettes.size()!=3)throw new AssertionError("Three distinct pastel variants");
        for(String lang:new String[]{"en_us","ru_ru"}) {
            var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/dudunka/lang/"+lang+".json"))).getAsJsonObject();
            if(!json.has("entity.dudunka.fluffy_jumper"))throw new AssertionError("Localized entity name");
            for(int i=0;i<3;i++)if(!json.has("message.dudunka.fluffy_jumper."+i))throw new AssertionError("Localized greeting");
        }
        var events=Files.readString(Path.of("src/main/java/io/github/golovinss/dudunka/client/ClientEvents.java"));
        if(!events.contains("FluffyJumperModel.LAYER,FluffyJumperModel::create")||!events.contains("DudunkaMod.FLUFFY_JUMPER.get(),FluffyJumperRenderer::new"))throw new AssertionError("Client wiring");
        var renderer=Files.readString(Path.of("src/main/java/io/github/golovinss/dudunka/client/FluffyJumperRenderer.java"));
        if(!renderer.contains("textures/entity/fluffy_jumper_"))throw new AssertionError("Variant renderer texture path");
        for(int t=0;t<300;t++) {
            model.animate(t,-70+t%140,-80+t%130,t%20,(float)Math.sin(t)*.42f,t%7/6f,t%31/30f);
            root.getAllParts().forEach(p->{if(!Float.isFinite(p.x+p.y+p.z+p.xRot+p.yRot+p.zRot+p.xScale+p.yScale+p.zScale)
                    ||p.xScale<.85||p.xScale>1.15||p.yScale<.85||p.yScale>1.15||p.zScale<.85||p.zScale>1.15)throw new AssertionError("Finite bounded animation");});
            root.render(new PoseStack(),new Geometry(),0,0);
        }
        model.animate(0,0,0,100,0,1,0);if(fluff.yScale>=.9||fluff.xScale<=1.1)throw new AssertionError("Landing squash");
        model.animate(0,0,0,3,.3f,0,0);if(fluff.yScale<=1.05||fluff.getChild("paw0").xRot>=-.1)throw new AssertionError("Airborne stretch and tucked paws");
        model.animate(0,0,0,100,0,0,0);if(fluff.yScale!=1||fluff.xScale!=1||fluff.zRot!=0)throw new AssertionError("Pose reset leaks");
        float idle=fluff.y;model.animate(10,0,0,100,0,0,0);if(fluff.y==idle)throw new AssertionError("Idle breathing");
        model.animate(2,0,0,100,0,0,1);if(Math.abs(fluff.zRot)<.05)throw new AssertionError("Cheerful greeting");
        model.animate(0,0,0,100,0,0,0);var rest=new Geometry();root.render(new PoseStack(),rest,0,0);
        double min=rest.vertices.stream().mapToDouble(v->v[1]).min().orElseThrow(),max=rest.vertices.stream().mapToDouble(v->v[1]).max().orElseThrow();
        if(max-min<.48||max-min>.55)throw new AssertionError("Chibi height "+(max-min));
        var previews=new StringBuilder("[");
        for(String pose:new String[]{"front","side","back","three-quarter","idle","hop","landing","greeting","look-up"}) {
            model.animate(10,0,pose.equals("look-up")?-55:0,pose.equals("hop")?3:100,pose.equals("hop")?.3f:0,pose.equals("landing")?1:0,pose.equals("greeting")?1:0);
            var sink=new Geometry();root.render(new PoseStack(),sink,0,0);
            if(previews.length()>1)previews.append(',');previews.append("{\"pose\":\"").append(pose).append("\",\"vertices\":[");
            boolean first=true;for(var v:sink.vertices){if(!first)previews.append(',');first=false;previews.append(Arrays.toString(v));}previews.append("]}");
        }
        Files.writeString(Path.of("build/fluffy-jumper-preview.json"),previews.append(']').toString());
        System.out.println("PASS Fluffy Jumper: "+root.getAllParts().count()+" original parts, 3 atlases, UVs/locales/wiring, 300 bounded poses, 9 preview poses; height="+(max-min));
    }
    private static class Geometry implements VertexConsumer {
        final List<double[]> vertices=new ArrayList<>();double x,y,z;float u,v;
        public VertexConsumer vertex(double a,double b,double c){x=a;y=b;z=c;return this;}
        public VertexConsumer uv(float a,float b){u=a;v=b;return this;}
        public void endVertex(){if(!Double.isFinite(x+y+z)||u<0||v<0||u>1||v>1)throw new AssertionError("Finite geometry and atlas UVs");vertices.add(new double[]{x,y,z,u,v});}
        public VertexConsumer color(int a,int b,int c,int d){return this;}
        public VertexConsumer overlayCoords(int a,int b){return this;}
        public VertexConsumer uv2(int a,int b){return this;}
        public VertexConsumer normal(float a,float b,float c){return this;}
        public void defaultColor(int a,int b,int c,int d){}
        public void unsetDefaultColor(){}
    }
}
