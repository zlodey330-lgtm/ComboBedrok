package dev.bedrockcombo.mixin;

import dev.bedrockcombo.BedrockCombo;
import dev.bedrockcombo.ComboContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

	/** Помечаем начало ближнего удара игрока (только на сервере). */
	@Inject(method = "attack", at = @At("HEAD"))
	private void bedrockcombo$attackStart(Entity target, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (!self.getWorld().isClient) {
			ComboContext.begin(self);
		}
	}

	@Inject(method = "attack", at = @At("RETURN"))
	private void bedrockcombo$attackEnd(Entity target, CallbackInfo ci) {
		ComboContext.end();
	}

	/** Без кулдауна атаки: каждый клик = полный удар, как в Bedrock. */
	@Inject(method = "getAttackCooldownProgress", at = @At("HEAD"), cancellable = true)
	private void bedrockcombo$noCooldown(float baseTime, CallbackInfoReturnable<Float> cir) {
		var c = BedrockCombo.CONFIG;
		if (c.enabled && c.disableAttackCooldown) {
			cir.setReturnValue(1.0F);
		}
	}
}
