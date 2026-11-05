package dev.bedrockcombo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Настройки мода. Файл: config/bedrockcombo.json
 * Менять можно руками в файле (потом /combo reload) или командой /combo set <опция> <значение>.
 */
public class ComboConfig {

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface Info {
		String value();
		double min() default 0;
		double max() default 0;
	}

	// ---------- общие ----------
	@Info("Мод включён")
	public boolean enabled = true;

	@Info("Подбрасывать мобов")
	public boolean affectMobs = true;

	@Info("Подбрасывать игроков")
	public boolean affectPlayers = true;

	// ---------- подброс ----------
	@Info(value = "Вертикальная скорость первого удара (с земли). Больше = выше взлетает", min = 0, max = 3)
	public double launchVelocity = 0.50;

	@Info(value = "Вертикальная скорость каждого удара по цели в воздухе (комба). Больше = быстрее набирает высоту", min = 0, max = 3)
	public double airVelocity = 0.40;

	@Info(value = "Макс. высота над землёй, выше которой цель больше не подбрасывается (0 = без ограничения)", min = 0, max = 64)
	public double maxComboHeight = 6.0;

	// ---------- отбрасывание ----------
	@Info(value = "Множитель горизонтального отбрасывания, когда цель на земле (1 = как в ванили)", min = 0, max = 5)
	public double groundHorizontal = 0.80;

	@Info(value = "Множитель горизонтального отбрасывания, когда цель в воздухе. Меньше = цель висит на месте, проще комбить", min = 0, max = 5)
	public double airHorizontal = 0.25;

	@Info(value = "Множитель доп. отбрасывания от бега/зачарования Отбрасывание (0 = выключить)", min = 0, max = 5)
	public double extraKnockbackMultiplier = 0.50;

	@Info("Учитывать сопротивление отбрасыванию (незеритовая броня, големы и т.п.). false = подбрасывать всех")
	public boolean respectKnockbackResistance = true;

	// ---------- скорость ударов ----------
	@Info("Убрать кулдаун атаки Java: каждый клик бьёт на полную силу, можно тапать как в PE")
	public boolean disableAttackCooldown = true;

	@Info(value = "Тики неуязвимости цели после удара (20 = ваниль, удар раз в 10 тиков; 10 или меньше = бить можно каждым кликом)", min = 0, max = 20)
	public int hitInvulnerabilityTicks = 20;

	// ---------- счётчик ----------
	@Info("Показывать счётчик комбо над хотбаром")
	public boolean showComboCounter = true;

	@Info(value = "Через сколько тиков без удара комбо считается оконченным", min = 5, max = 200)
	public int comboTimeoutTicks = 40;

	// ======================================================================

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Field> OPTIONS = new LinkedHashMap<>();

	static {
		for (Field f : ComboConfig.class.getDeclaredFields()) {
			if (f.isAnnotationPresent(Info.class) && !Modifier.isStatic(f.getModifiers())) {
				OPTIONS.put(f.getName(), f);
			}
		}
	}

	public static Map<String, Field> options() {
		return Collections.unmodifiableMap(OPTIONS);
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("bedrockcombo.json");
	}

	public static ComboConfig load() {
		Path path = file();
		ComboConfig cfg = null;
		if (Files.exists(path)) {
			try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				cfg = GSON.fromJson(r, ComboConfig.class);
			} catch (Exception e) {
				BedrockCombo.LOGGER.error("Не удалось прочитать {} — использую значения по умолчанию", path, e);
			}
		}
		if (cfg == null) cfg = new ComboConfig();
		cfg.clamp();
		cfg.save(); // дописывает новые опции в файл
		return cfg;
	}

	public void save() {
		Path path = file();
		try {
			Files.createDirectories(path.getParent());
			try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
		} catch (IOException e) {
			BedrockCombo.LOGGER.error("Не удалось сохранить {}", path, e);
		}
	}

	public void copyFrom(ComboConfig other) {
		for (Field f : OPTIONS.values()) {
			try {
				f.set(this, f.get(other));
			} catch (IllegalAccessException ignored) {
			}
		}
	}

	/** Приводит значения к допустимым диапазонам. */
	public void clamp() {
		for (Field f : OPTIONS.values()) {
			Info info = f.getAnnotation(Info.class);
			try {
				if (f.getType() == double.class) {
					double v = f.getDouble(this);
					if (Double.isNaN(v) || Double.isInfinite(v)) v = info.min();
					f.setDouble(this, Math.max(info.min(), Math.min(info.max(), v)));
				} else if (f.getType() == int.class) {
					int v = f.getInt(this);
					f.setInt(this, (int) Math.max(info.min(), Math.min(info.max(), v)));
				}
			} catch (IllegalAccessException ignored) {
			}
		}
	}

	public String get(String name) {
		Field f = OPTIONS.get(name);
		if (f == null) return null;
		try {
			return String.valueOf(f.get(this));
		} catch (IllegalAccessException e) {
			return null;
		}
	}

	/** Меняет опцию по имени. Бросает IllegalArgumentException с понятным текстом при ошибке. */
	public void set(String name, String raw) {
		Field f = OPTIONS.get(name);
		if (f == null) throw new IllegalArgumentException("Нет такой настройки: " + name);
		Info info = f.getAnnotation(Info.class);
		try {
			Class<?> t = f.getType();
			if (t == boolean.class) {
				String s = raw.toLowerCase();
				if (s.equals("true") || s.equals("on") || s.equals("1")) f.setBoolean(this, true);
				else if (s.equals("false") || s.equals("off") || s.equals("0")) f.setBoolean(this, false);
				else throw new IllegalArgumentException("Нужно true или false");
			} else if (t == int.class) {
				int v;
				try {
					v = Integer.parseInt(raw);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Нужно целое число");
				}
				f.setInt(this, (int) Math.max(info.min(), Math.min(info.max(), v)));
			} else if (t == double.class) {
				double v;
				try {
					v = Double.parseDouble(raw);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Нужно число, например 0.5");
				}
				if (Double.isNaN(v) || Double.isInfinite(v)) throw new IllegalArgumentException("Некорректное число");
				f.setDouble(this, Math.max(info.min(), Math.min(info.max(), v)));
			}
		} catch (IllegalAccessException e) {
			throw new IllegalArgumentException("Не удалось изменить настройку");
		}
	}
}
