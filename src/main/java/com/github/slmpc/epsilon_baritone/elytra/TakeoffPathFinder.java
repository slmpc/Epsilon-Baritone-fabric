package com.github.slmpc.epsilon_baritone.elytra;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TakeoffPathFinder {

    private TakeoffPathFinder() {
    }

    public static final class TakeoffSolution {
        public final float pitch;
        public final float yaw;
        public final BlockPos end;

        public TakeoffSolution(float pitch, float yaw, BlockPos end) {
            this.pitch = pitch;
            this.yaw = yaw;
            this.end = end;
        }
    }

    public static @Nullable TakeoffSolution getTakeoffDirection(@NotNull Minecraft minecraft, double maxDistance, double surroundDistance, double maxYRatio, double yOffset) {
        Level level = minecraft.level;
        Player player = minecraft.player;
        if (level == null || player == null) {
            return null;
        }

        Vec3 start = player.position().add(0.0, yOffset, 0.0);
        Vec3 startVelocity = player.getDeltaMovement();
        List<Vec3> baseDirections = generateDirections(maxYRatio);
        List<DirectionScore> candidates = new ArrayList<>();

        for (Vec3 direction : baseDirections) {
            BlockPos end = hasNoObstacleInLine(level, start, startVelocity, direction, maxDistance);
            if (end != null) {
                double score = computeOpennessScore(level, start, direction, maxDistance, surroundDistance, minecraft.player);
                if (score > 0.0) {
                    candidates.add(new DirectionScore(direction, direction.y < 0.0 ? score * 0.5 : score, end));
                }
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort((a, b) -> Double.compare(b.score, a.score));
        DirectionScore best = refineDirections(level, start, startVelocity, candidates.subList(0, Math.min(3, candidates.size())), maxDistance, surroundDistance, minecraft);
        return new TakeoffSolution(getPitchFromDirection(best.dir), getYawFromDirection(best.dir), best.end);
    }

    private static List<Vec3> generateDirections(double maxYRatio) {
        List<Vec3> list = new ArrayList<>(512);
        double phi = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < 512; i++) {
            double y = 1 - (i / (double) (512 - 1)) * maxYRatio;
            double radius = Math.sqrt(1 - y * y);
            double theta = phi * i;
            list.add(new Vec3(Math.cos(theta) * radius, y, Math.sin(theta) * radius));
        }
        return list;
    }

    public static @Nullable BlockPos hasNoObstacleInLine(@NotNull Level level, Vec3 start, Vec3 startVelocity, @NotNull Vec3 direction, double maxDistance) {
        Vec3 look = direction.normalize();
        double traveled = 0.0;
        Vec3 nextPos = start;
        List<Vec3> prediction = FlightPredictor.predictPath(40, start, startVelocity, look);

        for (int tick = 1; tick < prediction.size(); tick++) {
            nextPos = prediction.get(tick);
            if (!level.isLoaded(BlockPos.containing(nextPos))) {
                return null;
            }

            AABB box = new AABB(
                    nextPos.x - 0.3,
                    nextPos.y,
                    nextPos.z - 0.3,
                    nextPos.x + 0.3,
                    nextPos.y + 0.6,
                    nextPos.z + 0.3
            );

            Iterable<VoxelShape> collisions = level.getBlockCollisions(null, box);
            if (collisions.iterator().hasNext()) {
                return null;
            }

            traveled += prediction.get(tick - 1).distanceTo(nextPos);
            if (traveled >= maxDistance) {
                break;
            }
        }

        return BlockPos.containing(nextPos);
    }

    private static double computeOpennessScore(@NotNull Level level, @NotNull Vec3 start, @NotNull Vec3 direction, double maxDistance, double surroundDistance, @Nullable LocalPlayer player) {
        double[][] sampleDistances = {{10, 0.15}, {20, 0.35}, {30, 0.35}, {40, 0.15}};
        double score = 0.0;

        for (double[] sample : sampleDistances) {
            double distance = sample[0];
            double weight = sample[1];
            if (distance > maxDistance) {
                continue;
            }

            Vec3 point = start.add(direction.scale(distance));
            if (!level.getBlockState(BlockPos.containing(point)).isAir()) {
                return 0.0;
            }

            Vec3[] axes = {
                    new Vec3(1, 0, 0), new Vec3(-1, 0, 0),
                    new Vec3(0, 1, 0), new Vec3(0, -1, 0),
                    new Vec3(0, 0, 1), new Vec3(0, 0, -1)
            };

            double pointMin = Double.MAX_VALUE;
            for (Vec3 axis : axes) {
                Vec3 end = point.add(axis.scale(surroundDistance));
                BlockHitResult hit = level.clip(new ClipContext(point, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                double hitDistance = hit == null || hit.getType() == HitResult.Type.MISS ? surroundDistance : hit.getLocation().distanceTo(point);
                pointMin = Math.min(pointMin, hitDistance);
            }

            if (pointMin <= 1.5) {
                break;
            }
            score += pointMin * weight;
        }

        return score;
    }

    private static DirectionScore refineDirections(@NotNull Level level, @NotNull Vec3 start, Vec3 startVelocity, @NotNull List<DirectionScore> topCandidates, double maxDistance, double surroundDistance, @NotNull Minecraft minecraft) {
        final double angleRange = 15.0;
        final int steps = 5;
        DirectionScore best = null;
        double bestScore = -1.0;

        for (DirectionScore candidate : topCandidates) {
            float baseYaw = getYawFromDirection(candidate.dir);
            float basePitch = getPitchFromDirection(candidate.dir);

            for (int i = 0; i < steps; i++) {
                for (int j = 0; j < steps; j++) {
                    float yawOffset = (float) (-angleRange + 2 * angleRange * i / (steps - 1));
                    float pitchOffset = (float) (-angleRange + 2 * angleRange * j / (steps - 1));
                    float testYaw = baseYaw + yawOffset;
                    float testPitch = Math.max(-90.0F, Math.min(90.0F, basePitch + pitchOffset));
                    Vec3 testDirection = getDirectionFromYawPitch(testYaw, testPitch);
                    BlockPos end = hasNoObstacleInLine(level, start, startVelocity, testDirection, maxDistance);
                    if (end != null) {
                        double score = computeOpennessScore(level, start, testDirection, maxDistance, surroundDistance, minecraft.player);
                        if (score > bestScore) {
                            bestScore = score;
                            best = new DirectionScore(testDirection, score, end);
                        }
                    }
                }
            }
        }

        return best != null ? best : topCandidates.getFirst();
    }

    private static float getYawFromDirection(@NotNull Vec3 direction) {
        double yaw = Math.toDegrees(Math.atan2(-direction.x, direction.z));
        return (float) ((yaw + 360.0) % 360.0);
    }

    private static float getPitchFromDirection(@NotNull Vec3 direction) {
        return (float) Math.toDegrees(-Math.asin(direction.y));
    }

    private static Vec3 getDirectionFromYawPitch(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new Vec3(x, y, z);
    }

    private static final class DirectionScore {
        private final Vec3 dir;
        private final double score;
        private final BlockPos end;

        private DirectionScore(Vec3 dir, double score, BlockPos end) {
            this.dir = dir;
            this.score = score;
            this.end = end;
        }
    }
}

