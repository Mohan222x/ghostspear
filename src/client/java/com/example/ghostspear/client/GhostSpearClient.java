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
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class GhostSpearClient implements ClientModInitializer {
    private static final double REACH = 4.5; // default spear max reach, measured from your ghost

    @Override
    public void onInitializeClient() {
        // Left click with the ghost spear: enter ghost mode, or cancel it if already ghosting
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (!holdingGhostSpear(player)) return false;
            if (GhostState.desynced) {
                endGhost(client, player, null); // cancel: sync back, no attack
            } else {
                startGhost(client, player);
            }
            return true; // cancels the normal attack
        });

        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private static boolean holdingGhostSpear(ClientPlayerEntity p) {
        return p.getMainHandStack().getName().getString().toLowerCase().contains("ghost");
    }

    private static void startGhost(MinecraftClient mc, ClientPlayerEntity p) {
        GhostState.desynced = true;

        // Clone of you left where you started (your screen only)
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

        // You move and aim yourself. Only right click held + crosshair on a target in reach triggers the hit.
        if (!mc.options.useKey.isPressed()) return;

        Entity target = crosshairTargetInReach(mc, p);
        if (target != null) {
            endGhost(mc, p, target);
        }
    }

    private static Entity crosshairTargetInReach(MinecraftClient mc, ClientPlayerEntity p) {
        HitResult hit = mc.crosshairTarget;
        if (hit instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            if (e != p && e != GhostState.clone && e instanceof LivingEntity && e.isAlive()
                    && e.squaredDistanceTo(p) <= REACH * REACH) {
                return e;
            }
        }
        return null;
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
