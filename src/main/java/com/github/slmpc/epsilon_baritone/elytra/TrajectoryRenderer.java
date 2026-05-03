package com.github.slmpc.epsilon_baritone.elytra;

import com.mojang.blaze3d.vertex.PoseStack;
import com.github.epsilon.utils.render.Render3DUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;

import java.util.ArrayList;
import java.util.List;

public final class TrajectoryRenderer {

    private static final List<Vec3> PATH = new ArrayList<>();
    private static final List<BlockPos> MARKED_POSITIONS = new ArrayList<>();
    private TrajectoryRenderer() {
    }

    public static void drawTrajectory(List<Vec3> path) {
        PATH.clear();
        PATH.addAll(path);
    }

    public static void markPos(BlockPos pos) {
        if (!MARKED_POSITIONS.contains(pos)) {
            MARKED_POSITIONS.add(pos);
        }
    }

    public static void clear() {
        PATH.clear();
        MARKED_POSITIONS.clear();
    }

    public static void render(PoseStack poseStack) {
        if (Minecraft.getInstance().player == null || (PATH.isEmpty() && MARKED_POSITIONS.isEmpty())) {
            return;
        }

        for (Vec3 point : PATH) {
            Render3DUtils.drawOutlineBox(poseStack, new AABB(point.x - 0.05, point.y - 0.05, point.z - 0.05, point.x + 0.05, point.y + 0.05, point.z + 0.05), Color.CYAN.getRGB(), 1.0f);
        }

        for (BlockPos pos : MARKED_POSITIONS) {
            Render3DUtils.drawOutlineBox(poseStack, new AABB(pos), Color.RED.getRGB(), 2.0f);
        }
    }
}




