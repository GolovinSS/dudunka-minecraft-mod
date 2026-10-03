import io.github.golovinss.dudunka.Kind;
import io.github.golovinss.dudunka.client.FamilyModel;
import com.mojang.blaze3d.vertex.*;
import java.nio.file.*;
public class CheckModels {
 public static void main(String[] args) throws Exception {
  var exports=new StringBuilder("[");
  for (Kind k:Kind.values()) for(int stage=0;stage<(FamilyModel.hasAgeModels(k)?3:1);stage++) {
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
   if(k==Kind.MARUSYA){
    if(!root.hasChild("chest_fur") || !root.hasChild("tail"))throw new AssertionError("Missing cat joints");
    for(int i=0;i<4;i++)if(!root.hasChild("leg"+i))throw new AssertionError("Missing cat leg");
    if(stage>0){
     if(!root.getChild("head").getChild("ear0").hasChild("tuft"))throw new AssertionError("Missing ear tuft");
     if(!root.getChild("leg0").hasChild("paw"))throw new AssertionError("Missing broad paw");
     var random=net.minecraft.util.RandomSource.create(42);var tail=root.getChild("tail");
     if(!overlap(tail.getRandomCube(random),tail.getChild("middle").getRandomCube(random))
       || !overlap(tail.getChild("middle").getRandomCube(random),tail.getChild("upper").getRandomCube(random))
       || !overlap(tail.getChild("upper").getRandomCube(random),tail.getChild("tip").getRandomCube(random)))throw new AssertionError("Disconnected fluffy tail");
    }
    var body=root.getChild("body").getRandomCube(net.minecraft.util.RandomSource.create(42));
    double expected=6*(stage==0?1:stage==1?1.35:1.8);
    if(Math.abs(body.maxZ-body.minZ-expected)>.002)throw new AssertionError("Cat body length ratio");
   }
   if(k==Kind.SYUSYA){
    if(!root.getChild("head").hasChild("stalk0") || !root.getChild("head").hasChild("stalk1"))throw new AssertionError("Missing animated feelers");
    if(stage>0 && !root.hasChild("tail_tip"))throw new AssertionError("Missing rear body tip");
    if(stage==2 && !root.getChild("shell").hasChild("cone4"))throw new AssertionError("Missing adult Achatina apex");
    if(stage>0){
     var shell=root.getChild("shell");var random=net.minecraft.util.RandomSource.create(42);
     var firstCone=shell.getChild("cone0").getRandomCube(random);boolean connected=false;
     for(int i=0;i<(stage==2?5:4);i++)connected|=overlap(firstCone,shell.getChild("whorl"+i).getRandomCube(random));
     if(!connected)throw new AssertionError("Rear cone disconnected from main shell");
     for(int i=1;i<(stage==2?5:2);i++)if(!overlap(shell.getChild("cone"+(i-1)).getRandomCube(random),shell.getChild("cone"+i).getRandomCube(random)))throw new AssertionError("Gap between cone whorls");
    }
   }
   if(FamilyModel.hasAgeModels(k)){
    root.getAllParts().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);
    var sink=new Geometry();root.render(new PoseStack(),sink,0,0);float min=Float.MAX_VALUE,max=-Float.MAX_VALUE;
    for(double[] v:sink.vertices){min=Math.min(min,(float)v[1]);max=Math.max(max,(float)v[1]);}
    if(k==Kind.DUDUNKA && Math.abs((max-min)*16-10.7f)>.002f)throw new AssertionError("Age models must share normalized height: "+stage+" "+(max-min)*16);
    if(k==Kind.SYUSYA){
     double minZ=Double.POSITIVE_INFINITY,maxZ=Double.NEGATIVE_INFINITY;
     for(double[] v:sink.vertices){minZ=Math.min(minZ,v[2]);maxZ=Math.max(maxZ,v[2]);}
     double expected=8.8*(stage==0?1:stage==1?1.3:1.7);
     if(Math.abs((maxZ-minZ)*16-expected)>.002)throw new AssertionError("Snail length ratio: "+stage+" "+(maxZ-minZ)*16);
    }
    if(exports.length()>1)exports.append(',');exports.append("{\"kind\":\"").append(k.id).append("\",\"stage\":").append(stage).append(",\"vertices\":[");
    boolean first=true;for(double[] v:sink.vertices){if(!first)exports.append(',');first=false;exports.append(java.util.Arrays.toString(v));}exports.append("]}");
   }
   System.out.println("PASS model bake "+k+" stage "+stage+": "+parts+" parts");
  }
  exports.append(']');Files.createDirectories(Path.of("build"));Files.writeString(Path.of("build/model-preview.json"),exports);
 }
 private static boolean overlap(net.minecraft.client.model.geom.ModelPart.Cube a,net.minecraft.client.model.geom.ModelPart.Cube b){
  return a.minX<b.maxX && a.maxX>b.minX && a.minY<b.maxY && a.maxY>b.minY && a.minZ<b.maxZ && a.maxZ>b.minZ;
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
