// ... önceki kodların devamına şunları ekle ...
@Inject(method = "tick", at = @At("HEAD"))
private void onTick(CallbackInfo ci) {
    if (mc.player == null) return;

    Mbest700.movement.velocity(); // Anti-Knockback
    Mbest700.movement.autoEat();  // Otomatik Yemek
    
    // Combat hileleri zaten buradaydı
    Mbest700.combat.autoCrystal();
}

