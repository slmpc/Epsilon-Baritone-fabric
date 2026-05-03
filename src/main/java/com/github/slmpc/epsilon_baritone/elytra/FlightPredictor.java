package com.github.slmpc.epsilon_baritone.elytra;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class FlightPredictor {

    private FlightPredictor() {
    }

    public static List<Vec3> predictPath(int ticks, Vec3 pos, Vec3 velocity, Vec3 look) {
        List<Vec3> points = new ArrayList<>();

        for (int i = 0; i < ticks; i++) {
            points.add(pos);

            velocity = velocity.add(
                    look.x * 0.1 + (look.x * 1.5 - velocity.x) * 0.5,
                    look.y * 0.1 + (look.y * 1.5 - velocity.y) * 0.5,
                    look.z * 0.1 + (look.z * 1.5 - velocity.z) * 0.5
            );

            double horizontalVelocity = velocity.horizontalDistance();
            float pitchFactor = (float) look.y;
            float liftScale = pitchFactor <= -0.5F ? 2.0F : 1.0F;

            velocity = velocity.add(0.0, -0.08 + pitchFactor * 0.06 * liftScale, 0.0);

            if (velocity.y < 0.0 && horizontalVelocity > 0.0) {
                double lift = velocity.y * -0.1 * liftScale;
                velocity = velocity.add(look.x * lift / horizontalVelocity, lift, look.z * lift / horizontalVelocity);
            }

            velocity = velocity.multiply(0.99, 0.98, 0.99);
            pos = pos.add(velocity);
        }

        return points;
    }
}

