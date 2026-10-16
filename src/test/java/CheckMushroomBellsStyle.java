import com.google.gson.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

/** Static OBJ/atlas/visibility contracts. Client baking and visual acceptance are separate. */
public final class CheckMushroomBellsStyle {
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path root = Path.of("src/main/resources/assets/dudunka");
        Path dir = root.resolve("models/block/mushroom_bells_styled");
        var m = JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/dudunka/mushroom_bells_styled/layout.json"))).getAsJsonObject();
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(m.get("source").getAsString()))));
        require(hash.equals(m.get("source_sha256").getAsString()), "Approved source preserved");
        String styledHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(m.get("styled_source").getAsString()))));
        require(styledHash.equals(m.get("styled_sha256").getAsString()), "Separate derivative matches its converter");
        var atlas = ImageIO.read(root.resolve("textures/block/mushroom_bells_styled/atlas.png").toFile());
        require(atlas != null && atlas.getWidth() == 256 && atlas.getHeight() == 128, "Power-of-two native pixel atlas");
        var colors = new HashSet<Integer>();
        for (int y = 0; y < atlas.getHeight(); y++) for (int x = 0; x < atlas.getWidth(); x++) {
            int c = atlas.getRGB(x, y); require((c >>> 24) == 255, "Opaque palette without blending"); colors.add(c);
        }
        require(colors.size() > 30 && colors.size() < 128, "Cohesive non-flat handcrafted palette");
        var base = JsonParser.parseString(Files.readString(dir.resolve("base.json"))).getAsJsonObject();
        require(base.get("loader").getAsString().equals("forge:obj") && !base.get("automatic_culling").getAsBoolean(), "Shared built-in OBJ geometry without seam culling");
        var visibility = base.getAsJsonObject("visibility");
        for (var v : visibility.entrySet()) require(!v.getValue().getAsBoolean(), "Parent hides every cell by default");
        var variants = JsonParser.parseString(Files.readString(root.resolve("blockstates/mushroom_bells_styled_part.json"))).getAsJsonObject().getAsJsonObject("variants");
        var counts = new HashMap<String, Integer>();
        var positions = new ArrayList<double[]>(); var uvs = new ArrayList<double[]>(); var normals = new ArrayList<double[]>();
        String group = null;
        for (String line : Files.readAllLines(dir.resolve("scene.obj"))) {
            String[] fields = line.split(" ");
            if (line.startsWith("g ")) { group = fields[1]; require(visibility.has(group), "Known cell group"); counts.put(group, 0); }
            else if (line.startsWith("v ") || line.startsWith("vt ") || line.startsWith("vn ")) {
                double[] values = new double[fields.length - 1];
                for (int i = 1; i < fields.length; i++) { values[i - 1] = Double.parseDouble(fields[i]); require(Double.isFinite(values[i - 1]), "Finite vertex data"); }
                if (fields[0].equals("vn")) normals.add(values);
                else {
                    for (double v : values) require(v >= 0 && v <= 1, "Cell-local positions and normalized UVs");
                    if (fields[0].equals("v")) positions.add(values); else uvs.add(values);
                }
            } else if (line.startsWith("f ")) {
                require(group != null && fields.length >= 4 && fields.length <= 5, "Triangle/quad in a known cell");
                for (int i = 1; i < fields.length; i++) {
                    var refs = fields[i].split("/");
                    require(Integer.parseInt(refs[0]) > 0 && Integer.parseInt(refs[0]) <= positions.size()
                            && Integer.parseInt(refs[1]) > 0 && Integer.parseInt(refs[1]) <= uvs.size()
                            && Integer.parseInt(refs[2]) > 0 && Integer.parseInt(refs[2]) <= normals.size(), "Valid interpolated OBJ references");
                }
                counts.merge(group, 1, Integer::sum);
            } else if (line.startsWith("usemtl ")) require(Set.of("surface", "warm", "glint").contains(fields[1]), "Only three lighting materials");
        }
        int lights = 0, faces = 0;
        for (var entry : m.getAsJsonArray("parts")) {
            var part = entry.getAsJsonObject(); int index = part.get("part").getAsInt();
            String name = String.format(Locale.ROOT, "cell_%03d", index);
            var child = JsonParser.parseString(Files.readString(dir.resolve(name + ".json"))).getAsJsonObject();
            require(child.get("parent").getAsString().equals("dudunka:block/mushroom_bells_styled/base"), "Inherit shared OBJ and visibility defaults");
            var own = child.getAsJsonObject("visibility");
            require(own.size() == 1 && own.get(name).getAsBoolean(), "Exactly one cell visible in each baked state");
            require(variants.getAsJsonObject("part=" + index).get("model").getAsString().endsWith("/" + name), "Exact state/model mapping");
            require(counts.get(name) == part.get("faces").getAsInt(), "Measured face count matches actual OBJ");
            faces += counts.get(name); if (part.get("light").getAsInt() > 0) lights++;
        }
        var metrics = m.getAsJsonObject("optimization");
        require(lights == 4 && faces == metrics.get("styled_faces").getAsInt(), "Four real light cells and exact optimized face budget");
        require(faces < metrics.get("original_faces").getAsInt() && variants.size() == counts.size(), "Measured reduction without missing cells");
        for (var f : m.getAsJsonArray("files")) require(Files.isRegularFile(Path.of(f.getAsString())), "Generated artifact exists");
        String mtl = Files.readString(dir.resolve("palette.mtl"));
        require(mtl.contains("Ka 0.8 0.8 0.8") && mtl.contains("Ka 0.4 0.4 0.4"), "Selective self-lit materials, independent of block lighting");
        System.out.println("PASS styled Mushroom Bells static resource contracts: " + counts.size() + " cells, " + faces + " faces; Minecraft/BMC4 acceptance separate");
    }
}
