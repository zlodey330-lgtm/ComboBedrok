package dev.bedrockcombo.mixin;

import dev.bedrockcombo.ComboLogic;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

	@Inject(method = "takeKnockback", at = @At("HEAD"), cancellable = true)
	private void bedrockcombo$takeKnockback(double strength, double x, double z, CallbackInfo ci) {
		if (ComboLogic.onKnockback((LivingEntity) (Object) this, strength, x, z)) {
			ci.cancel();
		}
	}

	@Inject(method = "damage", at = @At("RETURN"))
	private void bedrockcombo$afterDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) {
			ComboLogic.onDamaged((LivingEntity) (Object) this, source);
		}
	}
}
