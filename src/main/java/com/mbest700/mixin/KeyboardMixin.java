package com.mbest700.mixin;

import com.mbest700.Mbest700;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    private void onKey(long win, int key, int sc, int action, int mods, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS) {
            if (key == GLFW.GLFW_KEY_V) Mbest700.combat.autoAnchor(); // Örnek Tuş V
            if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) { /* GUI Aç */ }
        }
    }
}

