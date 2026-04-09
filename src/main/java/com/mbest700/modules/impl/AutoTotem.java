package com.mbest700.modules.impl;

import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotem {
    private final net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();

    public void onTick() {
        if (mc.player == null) return;
        
        if (!mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            for (int i = 9; i < 45; i++) {
                if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, 45, 0, SlotActionType.PICKUP, mc.player);
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, i, 0, SlotActionType.PICKUP, mc.player);
                    break;
                }
            }
        }
    }
}

