import com.google.gson.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Headless resource contracts, not a claim that a graphical client baked or accepted the models. */
public final class CheckMushroomBellsPreview {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path resources = Path.of("src/main/resources");
        Path assets = resources.resolve("assets/dudunka");
        var layout = JsonParser.parseString(Files.readString(resources.resolve("data/dudunka/mushroom_bells_preview/layout.json"))).getAsJsonObject();
        byte[] sourceBytes = Files.readAllBytes(Path.of(layout.get("source").getAsString()));
        var source = JsonParser.parseString(new String(sourceBytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(sourceBytes));
        require(hash.equals(layout.get("source_sha256").getAsString()), "Assets must track the approved source exactly");
        require(source.getAsJsonArray("elements").size() == layout.get("source_meshes").getAsInt(), "Every mesh accounted for");
        require(source.getAsJsonArray("groups").size() == layout.get("source_groups").getAsInt(), "Every group accounted for");
        require(source.getAsJsonArray("textures").size() == layout.get("source_textures").getAsInt(), "Use actual source palette count");
        require(layout.get("blocks_per_source_unit").getAsDouble() == .375, "Explicit source-unit to world-block conversion");
        double area = layout.get("source_surface_area").getAsDouble();
        require(Math.abs(area - layout.get("clipped_surface_area").getAsDouble()) < area * 1e-7, "Cell clipping preserves surface area");
        for (var value : layout.getAsJsonArray("files")) require(Files.isRegularFile(Path.of(value.getAsString())), "Generated resource exists: " + value);
        for (var value : source.getAsJsonArray("textures")) {
            var t = value.getAsJsonObject();
            byte[] embedded = Base64.getDecoder().decode(t.get("source").getAsString().split(",", 2)[1]);
            require(Arrays.equals(embedded, Files.readAllBytes(assets.resolve("textures/block/mushroom_bells_preview/" + t.get("name").getAsString() + ".png"))),
                    "Preserve embedded texture bytes");
        }
        var states = JsonParser.parseString(Files.readString(assets.resolve("blockstates/mushroom_bells_preview_part.json"))).getAsJsonObject().getAsJsonObject("variants");
        require(states.size() == layout.getAsJsonArray("parts").size(), "Every part has exactly one blockstate model");
        int totalFaces = 0;
        var offsets = new HashSet<String>();
        var seenGroups = new HashSet<String>();
        for (var entry : layout.getAsJsonArray("parts")) {
            var part = entry.getAsJsonObject(); int index = part.get("part").getAsInt();
            require(offsets.add(part.get("offset").toString()), "Unique layout cells");
            for (var g : part.getAsJsonArray("groups")) seenGroups.add(g.getAsString());
            String name = String.format(Locale.ROOT, "cell_%03d", index);
            Path dir = assets.resolve("models/block/mushroom_bells_preview");
            var model = JsonParser.parseString(Files.readString(dir.resolve(name + ".json"))).getAsJsonObject();
            require(model.get("loader").getAsString().equals("forge:obj") && !model.has("elements"), "Use real Forge mesh loading, not vanilla cuboids");
            require(model.get("model").getAsString().equals("dudunka:models/block/mushroom_bells_preview/" + name + ".obj"), "Exact OBJ resource location");
            require(!model.get("automatic_culling").getAsBoolean(), "Never cull seams/ornaments against adjacent partial cells");
            require(states.getAsJsonObject("part=" + index).get("model").getAsString().endsWith("/" + name), "Blockstate resolves exact cell model");
            int vertices = 0, normals = 0, faces = 0;
            for (String line : Files.readAllLines(dir.resolve(name + ".obj"))) {
                if (line.startsWith("v ")) {
                    vertices++;
                    for (String coordinate : line.substring(2).split(" ")) {
                        double v = Double.parseDouble(coordinate);
                        require(Double.isFinite(v) && v >= 0 && v <= 1, "All geometry remains inside its chunk-rendered block cell");
                    }
                } else if (line.startsWith("vn ")) {
                    normals++;
                    double length = 0;
                    for (String c : line.substring(3).split(" ")) length += Math.pow(Double.parseDouble(c), 2);
                    require(Math.abs(length - 1) < 1e-5, "Unit flat-shading normals");
                } else if (line.startsWith("f ")) {
                    faces++;
                    var corners = line.substring(2).split(" ");
                    require(corners.length == 3, "Explicit triangular geometry");
                    for (String corner : corners) {
                        String[] indices = corner.split("/");
                        require(Integer.parseInt(indices[0]) > 0 && Integer.parseInt(indices[0]) <= vertices
                                && indices[1].equals("1") && Integer.parseInt(indices[2]) > 0
                                && Integer.parseInt(indices[2]) <= normals, "Valid OBJ position/UV/normal indices");
                    }
                } else if (line.startsWith("mtllib ")) require(line.equals("mtllib palette.mtl"), "Shared material library");
            }
            require(faces == part.get("faces").getAsInt() && faces > 0, "Exact nonempty baked face budget");
            require(faces < 1500, "Bounded geometry in each cell");
            totalFaces += faces;
        }
        require(seenGroups.size() == 9 && seenGroups.contains("05_Central_Carved_Stone"), "All nine approved object groups converted");
        require(totalFaces < 40000 && states.size() < 512, "Bounded static prototype cost");
        String mtl = Files.readString(assets.resolve("models/block/mushroom_bells_preview/palette.mtl"));
        require(mtl.contains("Ka 1 1 1") && mtl.contains("forge_TintIndex -1"), "Un-tinted source colors and the source's emissive accents");
        System.out.println("PASS Mushroom Bells source, palette, clipped OBJ references and bounds: " + states.size() + " cells, " + totalFaces + " faces; graphical acceptance separate");
    }
}
