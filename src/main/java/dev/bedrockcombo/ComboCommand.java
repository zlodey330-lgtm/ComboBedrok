package dev.bedrockcombo;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.util.function.Predicate;

/**
 * /combo                      — показать все настройки
 * /combo set <опция> <знач.>  — изменить настройку (сохраняется в config/bedrockcombo.json)
 * /combo reload               — перечитать файл
 * /combo reset                — вернуть значения по умолчанию
 */
public final class ComboCommand {
	private ComboCommand() {
	}

	private static final Predicate<ServerCommandSource> CAN_EDIT =
			src -> src.hasPermissionLevel(2) || src.getServer().isSingleplayer();

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("combo")
						.executes(ComboCommand::show)
						.then(CommandManager.literal("set")
								.requires(CAN_EDIT)
								.then(CommandManager.argument("option", StringArgumentType.word())
										.suggests((ctx, builder) -> CommandSource.suggestMatching(ComboConfig.options().keySet(), builder))
										.then(CommandManager.argument("value", StringArgumentType.word())
												.suggests((ctx, builder) -> {
													String name = StringArgumentType.getString(ctx, "option");
													Field f = ComboConfig.options().get(name);
													if (f != null && f.getType() == boolean.class) {
														return CommandSource.suggestMatching(new String[]{"true", "false"}, builder);
													}
													return builder.buildFuture();
												})
												.executes(ComboCommand::set))))
						.then(CommandManager.literal("reload")
								.requires(CAN_EDIT)
								.executes(ctx -> {
									BedrockCombo.CONFIG = ComboConfig.load();
									ctx.getSource().sendFeedback(() -> Text.literal("Настройки комбо перечитаны"), true);
									return 1;
								}))
						.then(CommandManager.literal("reset")
								.requires(CAN_EDIT)
								.executes(ctx -> {
									ComboConfig fresh = new ComboConfig();
									BedrockCombo.CONFIG.copyFrom(fresh);
									BedrockCombo.CONFIG.save();
									ctx.getSource().sendFeedback(() -> Text.literal("Настройки комбо сброшены"), true);
									return 1;
								}))));
	}

	private static int show(CommandContext<ServerCommandSource> ctx) {
		ComboConfig cfg = BedrockCombo.CONFIG;
		ServerCommandSource src = ctx.getSource();
		src.sendFeedback(() -> Text.literal("== Bedrock Combo: настройки (/combo set <опция> <значение>) =="), false);
		for (var e : ComboConfig.options().entrySet()) {
			ComboConfig.Info info = e.getValue().getAnnotation(ComboConfig.Info.class);
			String line = e.getKey() + " = " + cfg.get(e.getKey()) + "  — " + info.value();
			src.sendFeedback(() -> Text.literal(line), false);
		}
		return 1;
	}

	private static int set(CommandContext<ServerCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "option");
		String value = StringArgumentType.getString(ctx, "value");
		try {
			BedrockCombo.CONFIG.set(name, value);
		} catch (IllegalArgumentException ex) {
			ctx.getSource().sendError(Text.literal(ex.getMessage()));
			return 0;
		}
		BedrockCombo.CONFIG.save();
		String now = BedrockCombo.CONFIG.get(name);
		ctx.getSource().sendFeedback(() -> Text.literal(name + " = " + now), true);
		return 1;
	}
}
