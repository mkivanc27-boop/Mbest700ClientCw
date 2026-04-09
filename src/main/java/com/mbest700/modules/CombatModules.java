package com.mbest700.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.text.Text;

public class CombatModules {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    public void autoAnchor() {
        if (!(mc.crosshairTarget instanceof BlockHitResult hit)) return;
        int anchor = find(Items.RESPAWN_ANCHOR);
        int glow = find(Items.GLOWSTONE);

        if (anchor == -1) {
            mc.player.sendMessage(Text.literal("§cActionbar: Anchor Yok!"), true);
            return;
        }

        mc.player.getInventory().selectedSlot = anchor;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);

        if (glow != -1) {
            mc.player.getInventory().selectedSlot = glow;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit); 
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        }
    }

    public void autoCrystal() {
        if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL) && mc.options.useKey.isPressed()) {
            mc.world.getEntities().forEach(e -> {
                if (e instanceof EndCrystalEntity crystal && mc.player.distanceTo(crystal) < 5) {
                    mc.interactionManager.attackEntity(mc.player, crystal);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            });
        }
    }

    public void shieldCracker() {
        if (mc.targetedEntity instanceof PlayerEntity target && target.isUsingShield()) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                    mc.player.getInventory().selectedSlot = i;
                    mc.interactionManager.attackEntity(mc.player, target);
                    break;
                }
            }
        }
    }

    private int find(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
}

