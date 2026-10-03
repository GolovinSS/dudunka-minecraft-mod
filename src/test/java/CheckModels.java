import io.github.golovinss.dudunka.Kind;
import io.github.golovinss.dudunka.client.FamilyModel;
public class CheckModels {
 public static void main(String[] args) {
  for (Kind k:Kind.values()) {
   var root=FamilyModel.create(k).bakeRoot();
   if(!root.hasChild("head"))throw new AssertionError("Missing head: "+k);
   long parts=root.getAllParts().count();if(parts<5)throw new AssertionError("Incomplete model: "+k);
   System.out.println("PASS model bake "+k+": "+parts+" parts");
  }
 }
}
