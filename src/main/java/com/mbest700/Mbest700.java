package com.mbest700;

import com.mbest700.modules.CombatModules;
import com.mbest700.modules.MovementModules;
import net.fabricmc.api.ClientModInitializer;

public class Mbest700 implements ClientModInitializer {
    public static CombatModules combat = new CombatModules();
    public static MovementModules movement = new MovementModules();

    @Override
    public void onInitializeClient() {}
}
