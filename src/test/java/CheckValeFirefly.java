import io.github.golovinss.dudunka.client.*;
import com.mojang.blaze3d.vertex.*;
import javax.imageio.ImageIO;
import java.nio.file.*;
import com.google.gson.JsonParser;

public class CheckValeFirefly {
    public static void main(String[] args) throws Exception {
        var root=ValeFireflyModel.create().bakeRoot();
        for(String name:new String[]{"abdomen","head","left_wing","right_wing","left_eye","right_eye"})
            if(!root.hasChild(name)) throw new AssertionError("Missing part "+name);
        VertexConsumer sink=new VertexConsumer() {
            public VertexConsumer vertex(double x,double y,double z) { if(!Double.isFinite(x+y+z)) throw new AssertionError("Nonfinite geometry"); return this; }
            public VertexConsumer uv(float u,float v) { if(u<0||u>1||v<0||v>1) throw new AssertionError("UV outside atlas"); return this; }
            public VertexConsumer color(int r,int g,int b,int a){return this;}
            public VertexConsumer overlayCoords(int a,int b){return this;}
            public VertexConsumer uv2(int a,int b){return this;}
            public VertexConsumer normal(float a,float b,float c){return this;}
            public void endVertex(){}
            public void defaultColor(int r,int g,int b,int a){}
            public void unsetDefaultColor(){}
        };
        var model=new ValeFireflyModel(root);
        for(int t=0;t<400;t++) {
            model.animate(t,.8f);root.render(new PoseStack(),sink,0,0);
            float glow=ValeFireflyModel.glow(t,123);
            if(glow<.45f||glow>1) throw new AssertionError("Pulse bounds");
        }
        if(ValeFireflyModel.glow(50,123)==ValeFireflyModel.glow(50,456)) throw new AssertionError("Blink phases synchronized");
        for(int i=0;i<3;i++) for(boolean glow:new boolean[]{false,true}) {
            var path=Path.of("src/main/resources/assets/dudunka",ValeFireflyRenderer.texture(i,glow).getPath());
            var img=ImageIO.read(path.toFile());
            if(img.getWidth()!=32||img.getHeight()!=16) throw new AssertionError("Atlas dimensions");
            if(glow && ((img.getRGB(16,1)>>>24)!=0 || (img.getRGB(2,2)>>>24)==0)) throw new AssertionError("Emissive mask must isolate abdomen");
        }
        for(String lang:new String[]{"en_us","ru_ru"})
            if(!JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/dudunka/lang/"+lang+".json"))).getAsJsonObject().has("entity.dudunka.vale_firefly")) throw new AssertionError("Missing locale");
        System.out.println("PASS firefly: baked model/animated UVs, six atlases, emissive masks, asynchronous pulse bounds, locales");
    }
}
