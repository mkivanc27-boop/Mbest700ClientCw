package com.mbest700.modules.impl;

import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

public class AutoAnchor {
    private final net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();

    public void onTick() {
        if (!(mc.crosshairTarget instanceof BlockHitResult blockHit)) return;

        int anchor = find(Items.RESPAWN_ANCHOR);
        int glow = find(Items.GLOWSTONE);

        if (anchor == -1) {
            mc.player.sendMessage(Text.literal("§cAnchor Yok!"), true);
            return;
        }

        // Anchor seç ve koy
        mc.player.getInventory().selectedSlot = anchor;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
        
        if (glow != -1) {
            // Glowstone seç ve doldur
            mc.player.getInventory().selectedSlot = glow;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit); 
            // Patlat
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
        } else {
            // Totem kontrolü
            if (find(Items.TOTEM_OF_UNDYING) == -1) {
                mc.player.getInventory().selectedSlot = anchor;
            }
        }
    }

    private int find(net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }
}

