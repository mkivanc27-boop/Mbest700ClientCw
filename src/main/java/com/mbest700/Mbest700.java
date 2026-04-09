package com.mbest700;

import com.mbest700.modules.CombatModules;
import net.fabricmc.api.ClientModInitializer;

public class Mbest700 implements ClientModInitializer {
    public static CombatModules combat = new CombatModules();
    @Override
    public void onInitializeClient() {}
}

