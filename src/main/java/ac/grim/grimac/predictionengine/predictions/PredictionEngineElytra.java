package ac.grim.grimac.predictionengine.predictions;

import ac.grim.grimac.player.GrimPlayer;
import ac.grim.grimac.predictionengine.movementtick.MovementTickerPlayer;
import ac.grim.grimac.utils.data.VectorData;
import ac.grim.grimac.utils.nmsutil.ReachUtils;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.bukkit.Bukkit;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PredictionEngineElytra extends PredictionEngine {
    // Inputs have no effect on movement
    @Override
    public List<VectorData> applyInputsToVelocityPossibilities(GrimPlayer player, Set<VectorData> possibleVectors, float speed) {
        List<VectorData> results = new ArrayList<>();
        Vector currentLook = ReachUtils.getLook(player, player.xRot, player.yRot);

        for (VectorData data : possibleVectors) {
            Vector elytraResult = getElytraMovement(player, data.vector.clone(), currentLook)
                    .multiply(player.stuckSpeedMultiplier)
                    .multiply(new Vector(0.99F, 0.98F, 0.99F));
            results.add(data.returnNewModified(elytraResult, VectorData.VectorType.InputResult));

            // Handle Optifine trigonometry differences
            player.trigHandler.toggleShitMath();
            elytraResult = getElytraMovement(player, data.vector.clone(), ReachUtils.getLook(player, player.xRot, player.yRot))
                    .multiply(player.stuckSpeedMultiplier)
                    .multiply(new Vector(0.99F, 0.98F, 0.99F));
            player.trigHandler.toggleShitMath();
            results.add(data.returnNewModified(elytraResult, VectorData.VectorType.InputResult));
        }

        return results;
    }

    public static Vector getElytraMovement(GrimPlayer player, Vector vector, Vector lookVector) {
        float yRotRadians = player.yRot * 0.017453292F;
        double horizontalSqrt = Math.sqrt(lookVector.getX() * lookVector.getX() + lookVector.getZ() * lookVector.getZ());
        double horizontalLength = vector.clone().setY(0).length();
        double length = lookVector.length();

        // Mojang changed trigonometry calculations in 1.18.2
        double vertCosRotation = player.getClientVersion().isNewerThanOrEquals(ClientVersion.V_1_18_2)
                ? Math.cos(yRotRadians)
                : player.trigHandler.cos(yRotRadians);
        vertCosRotation = vertCosRotation * vertCosRotation * Math.min(1.0D, length / 0.4D);

        // Recalculate gravity based on slow falling status
        double recalculatedGravity = player.compensatedEntities.getSelf().getAttributeValue(Attributes.GENERIC_GRAVITY);
        if (player.clientVelocity.getY() <= 0 && player.compensatedEntities.getSlowFallingAmplifier().isPresent()) {
            recalculatedGravity = player.getClientVersion().isOlderThan(ClientVersion.V_1_20_5)
                    ? 0.01
                    : Math.min(recalculatedGravity, 0.01);
        }

        vector.add(new Vector(0.0D, recalculatedGravity * (-1.0D + vertCosRotation * 0.75D), 0.0D));
        double d5;

        // Handle slowing the player down when falling
        if (vector.getY() < 0.0D && horizontalSqrt > 0.0D) {
            d5 = vector.getY() * -0.1D * vertCosRotation;
            vector.add(new Vector(lookVector.getX() * d5 / horizontalSqrt, d5, lookVector.getZ() * d5 / horizontalSqrt));
        }

        // Handle accelerating the player when they are looking down
        if (yRotRadians < 0.0F && horizontalSqrt > 0.0D) {
            d5 = horizontalLength * (-player.trigHandler.sin(yRotRadians)) * 0.04D;
            vector.add(new Vector(-lookVector.getX() * d5 / horizontalSqrt, d5 * 3.2D, -lookVector.getZ() * d5 / horizontalSqrt));
        }

        // Handle accelerating the player sideways
        if (horizontalSqrt > 0) {
            vector.add(new Vector(
                    (lookVector.getX() / horizontalSqrt * horizontalLength - vector.getX()) * 0.1D,
                    0.0D,
                    (lookVector.getZ() / horizontalSqrt * horizontalLength - vector.getZ()) * 0.1D));
        }

        return vector;
    }

    // Players can jump while using an elytra if they are on the ground
    @Override
    public void addJumpsToPossibilities(GrimPlayer player, Set<VectorData> existingVelocities) {
        new PredictionEngineNormal().addJumpsToPossibilities(player, existingVelocities);
    }

    @Override
    public void guessBestMovement(float speed, GrimPlayer player) {
        // Allow all movement with vectors less than length 2.0
        if (player.actualMovement.length() < 1.2) {
            // Accept the movement without prediction
            // Bukkit.broadcastMessage("movement: "+ player.actualMovement.length());
            player.clientVelocity = player.actualMovement.clone();
            // Set predictedVelocity for consistency
            player.predictedVelocity = new VectorData(player.actualMovement.clone(), VectorData.VectorType.BestVelPicked);
            // Update player's position and velocity
            new MovementTickerPlayer(player).move(player.clientVelocity.clone(), player.predictedVelocity.vector);
            // Perform end-of-tick processing
            endOfTick(player, player.gravity);
        } else {
            // For vectors with length >= 2.5, use normal prediction
            super.guessBestMovement(speed, player);
        }
    }
}
