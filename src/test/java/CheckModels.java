import io.github.golovinss.dudunka.Kind;
import io.github.golovinss.dudunka.client.FamilyModel;
import com.mojang.blaze3d.vertex.*;
import java.nio.file.*;
public class CheckModels {
 public static void main(String[] args) throws Exception {
  var exports=new StringBuilder("[");
  for (Kind k:Kind.values()) for(int stage=0;stage<(k==Kind.DUDUNKA?3:1);stage++) {
   var root=FamilyModel.create(k,stage).bakeRoot();
   if(!root.hasChild("head"))throw new AssertionError("Missing head: "+k);
   long parts=root.getAllParts().count();if(parts<5)throw new AssertionError("Incomplete model: "+k);
   if(k==Kind.DUDUNKA && stage>0){
    if(!root.getChild("head").hasChild("bun") || !root.getChild("head").hasChild("hair_flower_center") || !root.hasChild("pendant_center"))throw new AssertionError("Missing reference details");
    if(root.getChild("backpack").hasChild("side_pocket0")!=(stage==2))throw new AssertionError("Backpack age detail");
   }
   for (io.github.golovinss.dudunka.Activity activity : io.github.golovinss.dudunka.Activity.values()) {
    root.getAllParts().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);
    io.github.golovinss.dudunka.client.AnimationPoses.apply(root, k, activity, 100);
    root.getAllParts().forEach(part -> {
     if (!Float.isFinite(part.xRot) || !Float.isFinite(part.yRot) || !Float.isFinite(part.zRot) || !Float.isFinite(part.y)) throw new AssertionError("Invalid animation transform: " + activity);
    });
   }
   if (k == Kind.DUDUNKA && !root.getChild("arm1").hasChild("ring")) throw new AssertionError("Ring must move with the hand");
   if(k==Kind.DUDUNKA){
    root.getAllParts().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);
    var sink=new Geometry();root.render(new PoseStack(),sink,0,0);float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;
    for(double[] v:sink.vertices){min=Math.min(min,(float)v[1]);max=Math.max(max,(float)v[1]);}
    if(Math.abs((max-min)*16-10.7f)>.002f)throw new AssertionError("Age models must share normalized height: "+stage+" "+(max-min)*16);
    if(exports.length()>1)exports.append(',');exports.append("{\"stage\":").append(stage).append(",\"vertices\":[");
    boolean first=true;for(double[] v:sink.vertices){if(!first)exports.append(',');first=false;exports.append(java.util.Arrays.toString(v));}exports.append("]}");
   }
   System.out.println("PASS model bake "+k+" stage "+stage+": "+parts+" parts");
  }
  exports.append(']');Files.createDirectories(Path.of("build"));Files.writeString(Path.of("build/model-preview.json"),exports);
 }
 private static class Geometry implements VertexConsumer {
  java.util.List<double[]> vertices=new java.util.ArrayList<>();double x,y,z,u,v;
  public VertexConsumer vertex(double a,double b,double c){x=a;y=b;z=c;return this;}
  public VertexConsumer uv(float a,float b){u=a;v=b;return this;}
  public void endVertex(){if(u<0 || v<0 || u>1 || v>1)throw new AssertionError("UV outside atlas");vertices.add(new double[]{x,y,z,u,v});}
  public VertexConsumer color(int a,int b,int c,int d){return this;}
  public VertexConsumer overlayCoords(int a,int b){return this;}
  public VertexConsumer uv2(int a,int b){return this;}
  public VertexConsumer normal(float a,float b,float c){return this;}
  public void defaultColor(int a,int b,int c,int d){}
  public void unsetDefaultColor(){}
 }
}
