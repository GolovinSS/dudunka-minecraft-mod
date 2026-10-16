import com.google.gson.*;
import java.nio.file.*;

/** Resource contracts only; actual appearance needs day/night Minecraft acceptance. */
public final class CheckValeFantasyVisuals {
    private static void require(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path root=Path.of("src/main/resources/assets/dudunka");
        for(String name:new String[]{"dance_flower_0","dance_flower_1","dance_flower_2","dance_flower_3",
                "turquoise_music_bell_0","turquoise_music_bell_1","violet_music_bell_0","violet_music_bell_1",
                "golden_music_bell_0","golden_music_bell_1"}) {
            var model=JsonParser.parseString(Files.readString(root.resolve("models/block/"+name+".json"))).getAsJsonObject();
            int luminous=0,sloped=0;
            var elements=model.getAsJsonArray("elements");
            require(elements.size()<=24,"Bounded cuboid count: "+name);
            for(var element:elements) {
                var box=element.getAsJsonObject();
                for(String edge:new String[]{"from","to"}) for(var coordinate:box.getAsJsonArray(edge))
                    require(coordinate.getAsDouble()>=-16 && coordinate.getAsDouble()<=32,"Vanilla model coordinate limits: "+name);
                if(box.has("rotation")) sloped++;
                if(box.has("forge_data")) {
                    var data=box.getAsJsonObject("forge_data");
                    require(data.get("block_light").getAsInt()==15 && !data.get("ambient_occlusion").getAsBoolean(),"Fullbright glow face contract");
                    luminous++;
                }
            }
            if(name.startsWith("dance") || name.endsWith("_1"))
                require(luminous>=2 && sloped>=3,"Exposed luminous volumes and individual sloped parts: "+name);
            for(var texture:model.getAsJsonObject("textures").entrySet()) {
                String value=texture.getValue().getAsString();
                if(value.startsWith("dudunka:")) require(Files.isRegularFile(root.resolve("textures/"+value.substring(8)+".png")),"Existing local texture: "+value);
            }
        }
        System.out.println("PASS fantasy resource contracts; graphical acceptance remains separate");
    }
}
