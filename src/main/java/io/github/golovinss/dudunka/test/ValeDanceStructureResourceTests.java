package io.github.golovinss.dudunka.test;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.gametest.*;

/** Separate second phase: absence of the newly generated resource is a failure, never a skipped PASS. */
@GameTestHolder("dudunka_structure_resource") @PrefixGameTestTemplate(false)
public final class ValeDanceStructureResourceTests {
    @GameTest(template = "empty", timeoutTicks = 100, batch = "isolated_dance_structure_resource")
    public static void packagedResourceResolvesAndPlacesExactly(GameTestHelper h) throws Exception {
        try (var site = new ValeDanceStructureExportTests.Site(h)) {
            var plan = site.generate();
            var resource = site.level.getServer().getResourceManager().getResourceOrThrow(ValeDanceStructureExportTests.RESOURCE);
            byte[] bytes;
            try (var input = resource.open()) { bytes = input.readAllBytes(); }
            String exportDir = System.getenv("DUDUNKA_DANCE_EXPORT_DIR");
            if (exportDir != null) h.assertTrue(Arrays.equals(bytes, Files.readAllBytes(Path.of(exportDir).resolve("jumper_dance_43a5a.nbt"))),
                    "Resolved mod resource is exactly the validated candidate bytes");
            var manager = site.level.getStructureManager();
            var id = ValeDanceStructureExportTests.ID;
            h.assertTrue(!Files.exists(manager.getPathToGeneratedStructure(id, ".nbt")), "No world-generated template shadows the mod resource");
            manager.remove(id);
            var template = manager.get(id).orElseThrow(() -> new AssertionError("Template ID cannot resolve from packaged mod resources"));
            var nbt = NbtIo.readCompressed(new ByteArrayInputStream(bytes));
            h.assertTrue(template.save(new CompoundTag()).equals(manager.readStructure(nbt.copy()).save(new CompoundTag())),
                    "Native manager resolves the same sparse template as resource bytes");
            ValeDanceStructureExportTests.verify(h, site, nbt, ValeDanceStructureExportTests.local(plan, site.source));
        }
        h.succeed();
    }
}
