package dev.bedrockcombo;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Живёт только пока игрок выполняет PlayerEntity.attack(...).
 * По нему мы понимаем, что отбрасывание вызвано ближним ударом игрока.
 */
public final class ComboContext {
	private static final ThreadLocal<ComboContext> CURRENT = new ThreadLocal<>();

	public final PlayerEntity attacker;
	/** Цель, к которой основное отбрасывание уже применено (остальные вызовы — «доп. отбрасывание»). */
	public Entity lastTarget;

	private ComboContext(PlayerEntity attacker) {
		this.attacker = attacker;
	}

	public static void begin(PlayerEntity attacker) {
		CURRENT.set(new ComboContext(attacker));
	}

	public static void end() {
		CURRENT.remove();
	}

	public static ComboContext current() {
		return CURRENT.get();
	}
}
