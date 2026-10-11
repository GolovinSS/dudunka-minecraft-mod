import io.github.golovinss.dudunka.client.*;
import io.github.golovinss.dudunka.ValeSpider;
import com.mojang.blaze3d.vertex.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Bake actual client geometry, validate UVs and bounded rigid animation, export review poses. */
public class CheckValeSpider {
    public static void main(String[] args) throws Exception {
        var root=ValeSpiderModel.create().bakeRoot();var model=new ValeSpiderModel(root);
        var head=root.getChild("head");
        for(String part:new String[]{"body","round_sides","round_back","soft_brow","left_eye","right_eye","left_cheek","right_cheek","smile","cap"})
            if(!head.hasChild(part))throw new AssertionError("Incomplete approved silhouette: "+part);
        for(int i=0;i<8;i++)if(!root.getChild("leg"+i).hasChild("paw"))throw new AssertionError("Missing articulated paw");
        var texture=ImageIO.read(Path.of("src/main/resources/assets/dudunka",ValeSpiderRenderer.TEXTURE.getPath()).toFile());
        if(texture.getWidth()!=128||texture.getHeight()!=64)throw new AssertionError("Original atlas dimensions");
        var colors=new HashSet<Integer>();
        for(int y=0;y<64;y++)for(int x=0;x<128;x++) {int color=texture.getRGB(x,y);if((color>>>24)!=255)throw new AssertionError("Opaque atlas");colors.add(color);}
        if(colors.size()<15)throw new AssertionError("Missing detailed pixel palette");
        for(String locale:new String[]{"en_us","ru_ru"}) {
            var json=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/dudunka/lang/"+locale+".json"))).getAsJsonObject();
            if(!json.has("entity.dudunka.vale_spider")||!json.has("message.dudunka.vale_spider_greeting"))throw new AssertionError("Missing localized greeting");
        }
        var events=Files.readString(Path.of("src/main/java/io/github/golovinss/dudunka/client/ClientEvents.java"));
        if(!events.contains("ValeSpiderModel.LAYER,ValeSpiderModel::create")||!events.contains("DudunkaMod.VALE_SPIDER.get(),ValeSpiderRenderer::new"))throw new AssertionError("Unregistered client references");
        for(int emote=0;emote<=2;emote++)for(int t=0;t<320;t++) {
            model.animate(t*.1f,.25f,t,30,-80,1,emote,(float)Math.sin(t*Math.PI/320));
            root.getAllParts().forEach(p->{
                if(!Float.isFinite(p.xRot+p.yRot+p.zRot+p.x+p.y+p.z)||p.xScale!=1||p.yScale!=1||p.zScale!=1
                        ||Math.abs(p.xRot)>1.2||Math.abs(p.zRot)>1)throw new AssertionError("Distorted animation");
            });
            root.render(new PoseStack(),new Geometry(),0,0);
        }
        model.animate(0,0,0,0,0,0,0,0);float rest=head.y;
        model.animate(0,0,0,0,0,0,ValeSpider.SHY,1);
        if(head.y<=rest)throw new AssertionError("Shy must lower body");
        model.animate(0,0,0,0,-80,1,0,0);
        if(head.xRot>=-.2f)throw new AssertionError("Curious must lift face toward player");
        model.animate(.7f,.3f,0,0,0,0,0,0);
        for(int i=0;i<8;i++)if(Math.abs(root.getChild("leg"+i).xRot)<.01f)throw new AssertionError("Walking must coordinate all eight legs");
        model.animate(0,0,20,0,0,0,0,0);
        if(head.y==rest)throw new AssertionError("Idle breathing must move gently");
        var previews=new StringBuilder("[");
        for(String pose:new String[]{"front","side","back","three-quarter","idle","walk","curious","greeting","shy"}) {
            model.animate(pose.equals("walk")?.6f:0,pose.equals("walk")?.3f:0,0,0,pose.equals("curious")?-80:0,
                    pose.equals("curious")?1:0,pose.equals("greeting")?1:pose.equals("shy")?2:0,1);
            var sink=new Geometry();root.render(new PoseStack(),sink,0,0);
            if(previews.length()>1)previews.append(',');previews.append("{\"pose\":\"").append(pose).append("\",\"vertices\":[");
            boolean first=true;for(var v:sink.vertices){if(!first)previews.append(',');first=false;previews.append(Arrays.toString(v));}previews.append("]}");
        }
        Files.createDirectories(Path.of("build"));Files.writeString(Path.of("build/vale-spider-preview.json"),previews.append(']').toString());
        System.out.println("PASS spider: "+root.getAllParts().count()+" baked parts, eight paws, original atlas, UVs, locales, registered client references, 960 rigid animation poses");
    }
    static class Geometry implements VertexConsumer {
        final List<double[]> vertices=new ArrayList<>();double x,y,z;float u,v;
        public VertexConsumer vertex(double x,double y,double z){if(!Double.isFinite(x+y+z))throw new AssertionError("Nonfinite geometry");this.x=x;this.y=y;this.z=z;return this;}
        public VertexConsumer uv(float u,float v){if(u<0||u>1||v<0||v>1)throw new AssertionError("UV outside atlas");this.u=u;this.v=v;return this;}
        public VertexConsumer color(int r,int g,int b,int a){return this;}
        public VertexConsumer overlayCoords(int a,int b){return this;}
        public VertexConsumer uv2(int a,int b){return this;}
        public VertexConsumer normal(float a,float b,float c){return this;}
        public void endVertex(){vertices.add(new double[]{x,y,z,u,v});}
        public void defaultColor(int r,int g,int b,int a){}
        public void unsetDefaultColor(){}
    }
}
