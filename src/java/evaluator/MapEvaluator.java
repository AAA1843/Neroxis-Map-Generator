package evaluator;

import export.SCMapExporter;
import export.SaveExporter;
import generator.AIMarkerGenerator;
import importer.SCMapImporter;
import importer.SaveImporter;
import map.*;
import util.ArgumentParser;
import util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;

public strictfp class MapEvaluator {

    public static boolean DEBUG = false;

    private Path inMapPath;
    private Path outFolderPath;
    private String mapName;
    private SCMap map;

    //masks used in transformation
    private BinaryMask land;
    private BinaryMask mountains;
    private BinaryMask plateaus;
    private BinaryMask ramps;
    private BinaryMask impassable;
    private BinaryMask passable;
    private BinaryMask passableLand;
    private BinaryMask passableWater;
    private FloatMask heightmapBase;
    private BinaryMask grass;
    private FloatMask grassTexture;
    private BinaryMask rock;
    private FloatMask rockTexture;
    private FloatMask lightGrassTexture;
    private FloatMask lightRockTexture;

    private SymmetryHierarchy symmetryHierarchy;

    public static void main(String[] args) throws IOException {

        Locale.setDefault(Locale.US);
        if (DEBUG) {
            Path debugDir = Paths.get(".", "debug");
            FileUtils.deleteRecursiveIfExists(debugDir);
            Files.createDirectory(debugDir);
        }

        MapEvaluator transformer = new MapEvaluator();

        transformer.interpretArguments(args);

        System.out.println("Transforming map " + transformer.inMapPath);
        transformer.importMap();
        transformer.transform();
        transformer.exportMap();
        System.out.println("Saving map to " + transformer.outFolderPath.toAbsolutePath());
        System.out.println("Terrain Symmetry: " + transformer.symmetryHierarchy.getTerrainSymmetry());
        System.out.println("Team Symmetry: " + transformer.symmetryHierarchy.getTeamSymmetry());
        System.out.println("Spawn Symmetry: " + transformer.symmetryHierarchy.getSpawnSymmetry());
        System.out.println("Done");
    }

    public void interpretArguments(String[] args) {
        interpretArguments(ArgumentParser.parse(args));
    }

    private void interpretArguments(Map<String, String> arguments) {
        if (arguments.containsKey("help")) {
            System.out.println("map-transformer usage:\n" +
                    "--help                 produce help message\n" +
                    "--in-folder-path arg   required, set the input folder for the map\n" +
                    "--out-folder-path arg  required, set the output folder for the transformed map\n" +
                    "--terrain-symmetry arg optional, set the symmetry for the map terrain(Point, X, Y, XY, YX, QUAD, DIAG)\n" +
                    "--team-symmetry arg    optional, set the symmetry for the teams(X, Y, XY, YX)\n" +
                    "--spawn-symmetry arg   optional, set the symmetry for the spawns(Point, X, Y, XY, YX)\n" +
                    "--debug                optional, turn on debugging options");
            System.exit(0);
        }

        if (arguments.containsKey("debug")) {
            DEBUG = true;
        }

        if (!arguments.containsKey("in-folder-path")) {
            System.out.println("Input Folder not Specified");
            System.exit(1);
        }

        if (!arguments.containsKey("out-folder-path")) {
            System.out.println("Output Folder not Specified");
            System.exit(2);
        }

        if (!arguments.containsKey("terrain-symmetry") || !arguments.containsKey("team-symmetry") || !arguments.containsKey("spawn-symmetry")) {
            System.out.println("Symmetries not Specified");
            System.exit(3);
        }

        inMapPath = Paths.get(arguments.get("in-folder-path"));
        outFolderPath = Paths.get(arguments.get("out-folder-path"));
        symmetryHierarchy = new SymmetryHierarchy(Symmetry.valueOf(arguments.get("terrain-symmetry")), Symmetry.valueOf(arguments.get("team-symmetry")));
        symmetryHierarchy.setSpawnSymmetry(Symmetry.valueOf(arguments.get("spawn-symmetry")));
    }

    public void importMap() {
        try {
            File dir = inMapPath.toFile();

            File[] mapFiles = dir.listFiles((dir1, filename) -> filename.endsWith(".scmap"));
            if (mapFiles == null || mapFiles.length == 0) {
                System.out.println("No scmap file in map folder");
                return;
            }
            File scmapFile = mapFiles[0];
            mapName = scmapFile.getName().replace(".scmap", "");

            map = SCMapImporter.loadSCMAP(inMapPath);
            SaveImporter.importSave(inMapPath, map);


        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error while saving the map.");
        }
    }

    public void exportMap() {
        try {
            FileUtils.deleteRecursiveIfExists(outFolderPath.resolve(mapName));

            long startTime = System.currentTimeMillis();
            Files.createDirectories(outFolderPath.resolve(mapName));
//            Files.copy(inMapPath.resolve(mapName + "_scenario.lua"), outFolderPath.resolve(mapName).resolve(mapName + "_scenario.lua"));
//            Files.copy(inMapPath.resolve(mapName + "_script.lua"), outFolderPath.resolve(mapName).resolve(mapName + "_script.lua"));
            SCMapExporter.exportSCMAP(outFolderPath, mapName, map);
            SaveExporter.exportSave(outFolderPath, mapName, map);
            System.out.printf("File export done: %d ms\n", System.currentTimeMillis() - startTime);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error while saving the map.");
        }
    }

    public void transform() {
        boolean waterPresent = map.getBiome().getWaterSettings().isWaterPresent();
        float waterHeight;
        if (waterPresent) {
            waterHeight = map.getBiome().getWaterSettings().getElevation();
        } else {
            waterHeight = 0;
        }
        heightmapBase = map.getHeightMask(symmetryHierarchy);
        land = new BinaryMask(heightmapBase, waterHeight, null);
        FloatMask slope = heightmapBase.copy().gradient();
        passable = new BinaryMask(slope, .75f, null).invert();
        passableLand = new BinaryMask(land, null);
        passableWater = new BinaryMask(land, null).invert();

        passable.deflate(6).trimEdge(8);
        passableLand.deflate(4).intersect(passable);
        passableWater.deflate(16).trimEdge(8);

        AIMarkerGenerator aiMarkerGenerator = new AIMarkerGenerator(map, 0);
        aiMarkerGenerator.generateAIMarkers(passable, passableLand, passableWater, 8, 16, false);

        aiMarkerGenerator.setMarkerHeights();
    }
}
