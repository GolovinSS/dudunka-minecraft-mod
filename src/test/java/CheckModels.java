import io.github.golovinss.dudunka.Kind;
import io.github.golovinss.dudunka.client.FamilyModel;
public class CheckModels {
 public static void main(String[] args) {
  for (Kind k:Kind.values()) {
   var root=FamilyModel.create(k).bakeRoot();
   if(!root.hasChild("head"))throw new AssertionError("Missing head: "+k);
   long parts=root.getAllParts().count();if(parts<5)throw new AssertionError("Incomplete model: "+k);
   for (io.github.golovinss.dudunka.Activity activity : io.github.golovinss.dudunka.Activity.values()) {
    root.getAllParts().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);
    io.github.golovinss.dudunka.client.AnimationPoses.apply(root, k, activity, 100);
    root.getAllParts().forEach(part -> {
     if (!Float.isFinite(part.xRot) || !Float.isFinite(part.yRot) || !Float.isFinite(part.zRot) || !Float.isFinite(part.y)) throw new AssertionError("Invalid animation transform: " + activity);
    });
   }
   if (k == Kind.DUDUNKA && !root.getChild("arm1").hasChild("ring")) throw new AssertionError("Ring must move with the hand");
   System.out.println("PASS model bake "+k+": "+parts+" parts");
  }
 }
}
