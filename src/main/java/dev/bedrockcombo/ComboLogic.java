package dev.bedrockcombo;

import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ComboLogic {
	private ComboLogic() {
	}

	private static final class ComboState {
		UUID target;
		long lastTick;
		int count;
	}

	/** Серверный счётчик комбо по игрокам (доступ только из серверного потока). */
	private static final Map<UUID, ComboState> STATES = new HashMap<>();

	private static boolean affects(ComboConfig c, LivingEntity target) {
		return target instanceof PlayerEntity ? c.affectPlayers : c.affectMobs;
	}

	/**
	 * Вызывается вместо ванильного LivingEntity.takeKnockback.
	 * @return true — ванильное отбрасывание нужно отменить (мы всё сделали сами)
	 */
	public static boolean onKnockback(LivingEntity target, double strength, double x, double z) {
		ComboContext ctx = ComboContext.current();
		if (ctx == null) return false;

		ComboConfig c = BedrockCombo.CONFIG;
		if (!c.enabled || !affects(c, target)) return false;

		double resist = c.respectKnockbackResistance
				? target.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE)
				: 0.0;
		double factor = Math.max(0.0, 1.0 - resist);
		if (factor <= 0.0) return true; // полное сопротивление — как в ванили, ничего не происходит

		Vec3d dir = new Vec3d(x, 0.0, z).normalize(); // направление «от цели к атакующему»
		Vec3d vel = target.getVelocity();

		// Повторный вызов для той же цели в рамках одного удара = доп. отбрасывание (бег / Отбрасывание)
		if (ctx.lastTarget == target) {
			double s = strength * c.extraKnockbackMultiplier * factor;
			target.setVelocity(vel.x - dir.x * s, vel.y, vel.z - dir.z * s);
			return true;
		}
		ctx.lastTarget = target;

		boolean air = !target.isOnGround();

		// Горизонталь
		double hs = strength * (air ? c.airHorizontal : c.groundHorizontal) * factor;
		double vx = vel.x / 2.0 - dir.x * hs;
		double vz = vel.z / 2.0 - dir.z * hs;

		// Вертикаль: каждый удар ЗАДАЁТ скорость вверх — именно это держит цель в воздухе
		double vy = (air ? c.airVelocity : c.launchVelocity) * factor;
		if (c.maxComboHeight > 0 && heightAboveGround(target, c.maxComboHeight) >= c.maxComboHeight) {
			// слишком высоко — просто «зависает», дальше не поднимается
			vy = Math.max(0.0, Math.min(vel.y, 0.1));
		}

		target.setVelocity(vx, vy, vz);
		return true;
	}

	/** Вызывается после успешного LivingEntity.damage(...). */
	public static void onDamaged(LivingEntity target, DamageSource source) {
		ComboContext ctx = ComboContext.current();
		if (ctx == null || source.getAttacker() != ctx.attacker) return;

		ComboConfig c = BedrockCombo.CONFIG;
		if (!c.enabled || !affects(c, target)) return;

		if (c.hitInvulnerabilityTicks < 20) {
			target.timeUntilRegen = c.hitInvulnerabilityTicks;
		}

		if (c.showComboCounter && ctx.attacker instanceof ServerPlayerEntity player) {
			updateCounter(c, player, target);
		}
	}

	private static void updateCounter(ComboConfig c, ServerPlayerEntity player, LivingEntity target) {
		long now = target.getWorld().getTime();
		if (STATES.size() > 256) {
			STATES.values().removeIf(s -> now - s.lastTick > 1200 || s.lastTick > now);
		}
		ComboState s = STATES.computeIfAbsent(player.getUuid(), k -> new ComboState());
		// цель, по которой уже били, в момент нового удара всё ещё в воздухе (оторвалась от земли)
		boolean continues = s.count > 0
				&& target.getUuid().equals(s.target)
				&& now - s.lastTick <= c.comboTimeoutTicks
				&& !target.isOnGround();
		s.count = continues ? s.count + 1 : 1;
		s.target = target.getUuid();
		s.lastTick = now;
		if (s.count >= 2) {
			player.sendMessage(Text.literal("Комбо x" + s.count), true);
		}
	}

	/** Высота цели над ближайшим твёрдым блоком/жидкостью (но не дальше max+1 блоков вниз). */
	private static double heightAboveGround(LivingEntity e, double max) {
		World w = e.getWorld();
		int bx = e.getBlockX();
		int bz = e.getBlockZ();
		int startY = MathHelper.floor(e.getY() - 0.01);
		int steps = (int) Math.ceil(max) + 1;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int i = 0; i <= steps; i++) {
			int y = startY - i;
			if (y < w.getBottomY()) return Double.MAX_VALUE;
			pos.set(bx, y, bz);
			BlockState st = w.getBlockState(pos);
			if (!st.getCollisionShape(w, pos).isEmpty() || !st.getFluidState().isEmpty()) {
				return e.getY() - (y + 1);
			}
		}
		return Double.MAX_VALUE;
	}
}
