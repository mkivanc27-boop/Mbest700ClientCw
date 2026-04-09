package com.mbest700.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

public class MovementModules {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    // Velocity: Hasar aldığında geriye savrulmanı engeller
    public void velocity() {
        if (mc.player.hurtTime > 0) {
            mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
        }
    }

    // AutoEat: Açlığın düştüğünde elindeki yemeği otomatik yer
    public void autoEat() {
        if (mc.player.getHungerManager().getFoodLevel() <= 14) {
            for (int i = 0; i < 9; i++) {
                if (mc.player.getInventory().getStack(i).isFood()) {
                    mc.player.getInventory().selectedSlot = i;
                    mc.options.useKey.setPressed(true);
                    return;
                }
            }
        } else {
            // Açlık dolunca basmayı bırak
            if (!mc.options.useKey.isDefault()) {
                mc.options.useKey.setPressed(false);
            }
        }
    }
}
