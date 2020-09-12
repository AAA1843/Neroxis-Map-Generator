package generator;

import map.AIMarker;
import map.BinaryMask;
import map.SCMap;
import util.Vector2f;
import util.Vector3f;

import java.util.Random;

import static util.Placement.placeOnHeightmap;

public strictfp class MexGenerator {
    private final SCMap map;
    private final Random random;
    private final int mexSpacing;
    private final int spawnSize;

    public MexGenerator(SCMap map, long seed, int spawnSize, int mexSpacing) {
        this.map = map;
        this.spawnSize = spawnSize;
        this.mexSpacing = mexSpacing;
        random = new Random(seed);
    }

    public void generateMexes(BinaryMask spawnable, BinaryMask spawnablePlateau, BinaryMask spawnableWater) {
        BinaryMask spawnableLand = new BinaryMask(spawnable, random.nextLong());
        spawnable.fillHalf(false);
        int mexSpawnDistance = 32;
        BinaryMask spawnableNoSpawns = new BinaryMask(spawnable, random.nextLong());
        int spawnCount = map.getSpawnCount();
        int totalMexCount = map.getMexCountInit();
        int numBaseMexes = (random.nextInt(2) + 3) * 2;
        int numNearMexes = (random.nextInt(2) + 5 - numBaseMexes / 2) * 2;
        int iMex = 0;
        for (int i = 0; i < map.getSpawnCount(); i += 2) {
            BinaryMask baseMexes = new BinaryMask(spawnable.getSize(), random.nextLong(), spawnable.getSymmetryHierarchy());
            baseMexes.fillCircle(map.getSpawn(i + 1), 15, true).fillCircle(map.getSpawn(i + 1), 5, false).intersect(spawnable);
            for (int j = 0; j < numBaseMexes; j += 2) {
                Vector2f location = baseMexes.getRandomPosition();
                if (location == null) {
                    break;
                }
                Vector2f symLocation = baseMexes.getSymmetryPoint(location);
                map.addMex(new Vector3f(location));
                map.addMex(new Vector3f(symLocation));
                baseMexes.fillCircle(location, 10, false);
                spawnable.fillCircle(location, 10, false);
                iMex += 2;
            }
            BinaryMask nearMexes = new BinaryMask(spawnable.getSize(), random.nextLong(), spawnable.getSymmetryHierarchy());
            nearMexes.fillCircle(map.getSpawn(i + 1), spawnSize * 3, true).fillCircle(map.getSpawn(i + 1), spawnSize, false).intersect(spawnable);
            for (int j = 0; j < map.getSpawnCount(); j += 2) {
                nearMexes.fillCircle(map.getSpawn(j + 1), spawnSize, false);
            }
            for (int j = 0; j < numNearMexes; j += 2) {
                Vector2f location = nearMexes.getRandomPosition();
                if (location == null) {
                    break;
                }
                Vector2f symLocation = nearMexes.getSymmetryPoint(location);
                map.addMex(new Vector3f(location));
                map.addMex(new Vector3f(symLocation));
                nearMexes.fillCircle(location, mexSpacing, false);
                spawnable.fillCircle(location, mexSpacing, false);
                iMex += 2;
            }
        }
        for (int i = 0; i < map.getSpawnCount(); i += 2) {
            spawnable.fillCircle(map.getSpawn(i + 1), 24, false);
            spawnableNoSpawns.fillCircle(map.getSpawn(i + 1), mexSpawnDistance, false);
        }

        int numMexesLeft;
        int actualExpMexCount;
        int baseMexCount = iMex;
        int nonBaseMexCount = totalMexCount - baseMexCount;

        for (int i = 0; i < baseMexCount; i++) {
            spawnable.fillCircle(map.getMex(i), mexSpacing, false);
        }

        if (nonBaseMexCount / 2 > 10) {
            int possibleExpMexCount = (random.nextInt(nonBaseMexCount / 2 / spawnCount) + nonBaseMexCount / 2 / spawnCount) * 2;
            actualExpMexCount = generateMexExpansions(spawnable, baseMexCount, possibleExpMexCount);
            numMexesLeft = nonBaseMexCount - actualExpMexCount;
        } else {
            actualExpMexCount = 0;
            numMexesLeft = nonBaseMexCount;
        }

        spawnableNoSpawns.intersect(spawnable);
        spawnablePlateau.intersect(spawnableNoSpawns);
        spawnableLand.intersect(spawnableNoSpawns);
        spawnableLand.minus(spawnablePlateau);

        float plateauDensity = (float) spawnablePlateau.getCount() / spawnableNoSpawns.getCount();
        int plateauMexCount = (int) (plateauDensity * numMexesLeft / 2) * 2;

        for (int i = 0; i < plateauMexCount; i += 2) {
            Vector2f mexLocation = spawnablePlateau.getRandomPosition();

            if (mexLocation == null) {
                break;
            }

            numMexesLeft -= 2;
            Vector2f mexSymLocation = spawnablePlateau.getSymmetryPoint(mexLocation);

            map.addMex(new Vector3f(mexLocation));
            map.addMex(new Vector3f(mexSymLocation));

            spawnablePlateau.fillCircle(mexLocation, mexSpacing, false);
        }

        int numLandMexes = numMexesLeft;
        for (int i = 0; i < numLandMexes; i += 2) {
            Vector2f mexLocation = spawnableLand.getRandomPosition();

            if (mexLocation == null) {
                break;
            }
            numMexesLeft -= 2;

            Vector2f mexSymLocation = spawnableLand.getSymmetryPoint(mexLocation);

            map.addMex(new Vector3f(mexLocation));
            map.addMex(new Vector3f(mexSymLocation));

            spawnableLand.fillCircle(mexLocation, mexSpacing, false);
        }
        spawnable.intersect(spawnableLand.combine(spawnablePlateau));

        int numNearSpawnMexes = numMexesLeft;
        for (int i = 0; i < numNearSpawnMexes; i += 2) {
            Vector2f mexLocation = spawnable.getRandomPosition();

            if (mexLocation == null) {
                break;
            }
            numMexesLeft -= 2;

            Vector2f mexSymLocation = spawnable.getSymmetryPoint(mexLocation);

            map.addMex(new Vector3f(mexLocation));
            map.addMex(new Vector3f(mexSymLocation));

            spawnable.fillCircle(mexLocation, mexSpacing, false);
            spawnable.fillCircle(mexSymLocation, mexSpacing, false);
        }

        for (int i = 0; i < numMexesLeft; i += 2) {
            Vector2f mexLocation = spawnableWater.getRandomPosition();

            if (mexLocation == null) {
                break;
            }

            Vector2f mexSymLocation = spawnableWater.getSymmetryPoint(mexLocation);

            map.addMex(new Vector3f(mexLocation));
            map.addMex(new Vector3f(mexSymLocation));

            spawnableWater.fillCircle(mexLocation, mexSpacing, false);
            spawnableWater.fillCircle(mexSymLocation, mexSpacing, false);
        }
    }

    public int generateMexExpansions(BinaryMask spawnable, int baseMexCount, int possibleExpMexCount) {
        Vector2f expLocation;
        Vector2f mexLocation;
        Vector2f mexSymLocation;
        int actualExpMexCount = possibleExpMexCount;
        int expMexCount;
        int expMexCountLeft = possibleExpMexCount;
        int iMex = baseMexCount;
        int expMexSpacing = 10;
        int expSize = 10;
        int expSpacing = 96;

        BinaryMask expansionSpawnable = new BinaryMask(spawnable.getSize(), random.nextLong(), spawnable.getSymmetryHierarchy());
        BinaryMask expansion = new BinaryMask(spawnable.getSize(), random.nextLong(), spawnable.getSymmetryHierarchy());

        expansionSpawnable.fillCircle(map.getSize() / 2f, map.getSize() / 2f, map.getSize() / 2f, true).fillCenter(96, false).intersect(spawnable);

        for (int i = 0; i < map.getSpawnCount(); i++) {
            expansionSpawnable.fillCircle(map.getSpawn(i), map.getSize() / 4f, false);
        }

        while (expMexCountLeft > 1) {
            expLocation = expansionSpawnable.getRandomPosition();

            while (expLocation != null && !isMexExpValid(expLocation, expSize, .5f, spawnable)) {
                expansionSpawnable.fillRect(expLocation, 1, 1, false);
                expLocation = expansionSpawnable.getRandomPosition();
            }

            if (expLocation == null) {
                actualExpMexCount = possibleExpMexCount - expMexCountLeft;
                break;
            }

            expansion.fillRect((int) expLocation.x - expSize, (int) expLocation.y - expSize, expSize * 2, expSize * 2, true);
            expansion.intersect(spawnable);

            expMexCount = StrictMath.min((random.nextInt(3) + 2) * 2, expMexCountLeft);
            if (expMexCount >= 6) {
                map.addLargeExpansionMarker(new AIMarker(map.getLargeExpansionMarkerCount(), expLocation, null));
                map.addLargeExpansionMarker(new AIMarker(map.getLargeExpansionMarkerCount(), expansionSpawnable.getSymmetryPoint(expLocation), null));
            } else {
                map.addExpansionMarker(new AIMarker(map.getExpansionMarkerCount(), expLocation, null));
                map.addExpansionMarker(new AIMarker(map.getExpansionMarkerCount(), expansionSpawnable.getSymmetryPoint(expLocation), null));
            }

            expansionSpawnable.fillCircle(expLocation, expSpacing, false);
            expansionSpawnable.fillCircle(expansionSpawnable.getSymmetryPoint(expLocation), expSpacing, false);

            for (int i = iMex; i < iMex + expMexCount; i += 2) {
                mexLocation = expansion.getRandomPosition();
                if (mexLocation == null) {
                    expMexCount -= i - iMex;
                    break;
                }
                mexSymLocation = expansion.getSymmetryPoint(mexLocation);

                map.addMex(new Vector3f(mexLocation));
                map.addMex(new Vector3f(mexSymLocation));

                expansion.fillCircle(mexLocation, expMexSpacing, false);
                expansion.fillCircle(mexSymLocation, expMexSpacing, false);

                spawnable.fillCircle(mexLocation, mexSpacing * 2, false);
                spawnable.fillCircle(mexSymLocation, mexSpacing * 2, false);
            }

            iMex += expMexCount;
            expansion.fillCircle(expLocation, expSize + 1, false);
            expMexCountLeft -= expMexCount;
        }
        return actualExpMexCount;
    }

    private boolean isMexExpValid(Vector2f location, float size, float density, BinaryMask spawnable) {
        boolean valid = true;
        float count = 0;

        for (int dx = 0; dx < size / 2; dx++) {
            for (int dy = 0; dy < size / 2; dy++) {
                if (spawnable.get(StrictMath.min((int) location.x + dx, map.getSize() - 1), StrictMath.min((int) location.y + dy, map.getSize() - 1))) {
                    count++;
                }
                if (spawnable.get(StrictMath.min((int) location.x + dx, map.getSize() - 1), StrictMath.max((int) location.y - dy, 0))) {
                    count++;
                }
                if (spawnable.get(StrictMath.max((int) location.x - dx, 0), StrictMath.min((int) location.y + dy, map.getSize() - 1))) {
                    count++;
                }
                if (spawnable.get(StrictMath.max((int) location.x - dx, 0), StrictMath.max((int) location.y - dy, 0))) {
                    count++;
                }
            }
        }
        if (count / (size * size) < density) {
            valid = false;
        }
        return valid;
    }

    public void setMarkerHeights() {
        for (int i = 0; i < map.getMexCount(); i++) {
            map.getMexes().set(i, placeOnHeightmap(map, map.getMex(i)));
        }
    }
}
