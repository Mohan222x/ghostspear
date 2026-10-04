package com.example.ghostspear.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class GhostSpearClient implements ClientModInitializer {
    private static final double REACH = 3.0;          // distance where we sync and hit
    private static final double SEARCH_RANGE = 40.0;  // how far we look for targets
    private static final double SPEED = 1.5;          // blocks per tick while ghosting

    @Override
    public void onInitializeClient() {
        // Left click with the ghost spear starts the desync
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (!holdingGhostSpear(player)) return false;
            if (GhostState.desynced) return true;
            startGhost(client, player);
            return true; // cancels the normal attack
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private static boolean holdingGhostSpear(ClientPlayerEntity p) {
        return p.getMainHandStack().getName().getString().toLowerCase().contains("ghost");
    }

    private static void startGhost(MinecraftClient mc, ClientPlayerEntity p) {
        GhostState.desynced = true;

        // Clone of yourself left where you started (your screen only)
        OtherClientPlayerEntity clone = new OtherClientPlayerEntity(mc.world, p.getGameProfile());
        clone.copyPositionAndRotation(p);
        clone.setHeadYaw(p.getHeadYaw());
        clone.setBodyYaw(p.getBodyYaw());
        mc.world.addEntity(clone);
        GhostState.clone = clone;
    }

    private void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            GhostState.desynced = false;
            GhostState.clone = null;
            return;
        }
        if (!GhostState.desynced) return;

        // Switched away from the spear: end safely
        if (!holdingGhostSpear(p)) {
            endGhost(mc, p, null);
            return;
        }

        // Only fly while right click is held
        if (!mc.options.useKey.isPressed()) return;

        Entity target = findTarget(mc, p);
        if (target == null) return;

        Vec3d aim = new Vec3d(target.getX(), target.getY() + target.getHeight() / 2, target.getZ());
        Vec3d toTarget = aim.subtract(p.getEyePos());

        if (toTarget.length() <= REACH) {
            endGhost(mc, p, target); // sync and hit
        } else {
            p.setVelocity(toTarget.normalize().multiply(SPEED));
        }
    }

    private static Entity findTarget(MinecraftClient mc, ClientPlayerEntity p) {
        Entity best = null;
        double bestDist = SEARCH_RANGE * SEARCH_RANGE;
        for (Entity e : mc.world.getEntities()) {
            if (e == p || e == GhostState.clone || !(e instanceof LivingEntity) || !e.isAlive()) continue;
            double d = e.squaredDistanceTo(p);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }

    private static void endGhost(MinecraftClient mc, ClientPlayerEntity p, Entity target) {
        GhostState.desynced = false; // packets flow again

        // Tell the server where you really are now
        p.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch(),
                p.isOnGround(), p.horizontalCollision));

        if (target != null) {
            mc.interactionManager.attackEntity(p, target);
            p.swingHand(Hand.MAIN_HAND);
        }

        if (GhostState.clone != null) {
            GhostState.clone.discard();
            GhostState.clone = null;
        }
    }
}
