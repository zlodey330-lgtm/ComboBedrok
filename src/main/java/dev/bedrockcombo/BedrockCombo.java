package dev.bedrockcombo;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BedrockCombo implements ModInitializer {
	public static final String MOD_ID = "bedrockcombo";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Текущие настройки (читаются и сервером, и клиентом). */
	public static volatile ComboConfig CONFIG = new ComboConfig();

	@Override
	public void onInitialize() {
		CONFIG = ComboConfig.load();
		ComboCommand.register();
		LOGGER.info("Bedrock Combo загружен");
	}
}
