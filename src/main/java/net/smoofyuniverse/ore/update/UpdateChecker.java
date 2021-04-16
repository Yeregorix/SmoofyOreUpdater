/*
 * Copyright (c) 2021 Hugo Dupanloup (Yeregorix)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.smoofyuniverse.ore.update;

import net.smoofyuniverse.ore.OreAPI;
import net.smoofyuniverse.ore.project.OreProject;
import net.smoofyuniverse.ore.project.OreVersion;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;

import java.net.URL;
import java.util.concurrent.TimeUnit;

public class UpdateChecker {
	private final Logger logger;
	private final PluginContainer plugin;
	private final ConfigurationLoader<? extends ConfigurationNode> loader;
	private final OreProject project;
	private final String permission;

	private final OreAPI api = new OreAPI();
	private Task checkTask;
	private UpdateCheckConfig config;
	private Text[] messages = new Text[0];

	public UpdateChecker(Logger logger, PluginContainer plugin, ConfigurationLoader<? extends ConfigurationNode> loader, String owner, String name) {
		this(logger, plugin, loader, new OreProject(plugin.getId()), plugin.getId() + ".update.notify");
		if (owner == null || owner.isEmpty())
			throw new IllegalArgumentException("owner");
		if (name == null || name.isEmpty())
			throw new IllegalArgumentException("name");

		this.project.owner = owner;
		this.project.name = name;
	}

	public UpdateChecker(Logger logger, PluginContainer plugin, ConfigurationLoader<? extends ConfigurationNode> loader, OreProject project, String permission) {
		if (logger == null)
			throw new IllegalArgumentException("logger");
		if (plugin == null)
			throw new IllegalArgumentException("plugin");
		if (loader == null)
			throw new IllegalArgumentException("loader");
		if (project == null)
			throw new IllegalArgumentException("project");
		if (permission == null || permission.isEmpty())
			throw new IllegalArgumentException("permission");

		this.logger = logger;
		this.loader = loader;
		this.plugin = plugin;
		this.project = project;
		this.permission = permission;
	}

	@Listener(order = Order.LATE)
	public void onServerStarted(GameStartedServerEvent e) {
		load();
	}

	private void load() {
		if (this.checkTask != null) {
			this.checkTask.cancel();
			this.checkTask = null;
		}

		this.logger.debug("Loading update check configuration ...");

		try {
			ConfigurationNode root = this.loader.load();
			this.config = root.getValue(UpdateCheckConfig.TOKEN);

			if (this.config == null)
				this.config = new UpdateCheckConfig();
			else
				this.config.normalize();

			root.setValue(UpdateCheckConfig.TOKEN, this.config);
			this.loader.save(root);
		} catch (Exception ex) {
			this.logger.error("Failed to load update check configuration", ex);
			return;
		}

		if (this.config.enabled) {
			this.checkTask = Task.builder().async().interval(this.config.repetitionInterval, TimeUnit.HOURS).execute(this::check).submit(this.plugin);
		}
	}

	@Listener(order = Order.LATE)
	public void onGameReload(GameReloadEvent e) {
		load();
	}

	private void check() {
		String version = this.plugin.getVersion().orElse(null);
		if (version == null)
			return;

		this.logger.debug("Checking for update ..");

		OreVersion latestVersion = null;
		try {
			latestVersion = OreVersion.getLatest(this.project.getVersions(this.api), v -> v.apiVersion.charAt(0) == '7').orElse(null);
		} catch (Exception e) {
			this.logger.info("Failed to check for update", e);
		}

		if (latestVersion != null && !latestVersion.name.equals(version)) {
			Text msg1 = Text.join(Text.of("A new version of " + this.project.name + " is available: "),
					Text.builder(latestVersion.name).color(TextColors.AQUA).build(),
					Text.of(". You're currently using version: "),
					Text.builder(version).color(TextColors.AQUA).build(),
					Text.of("."));

			Text msg2;
			try {
				msg2 = Text.builder("Click here to open the download page.").color(TextColors.GOLD)
						.onClick(TextActions.openUrl(new URL(latestVersion.getPage().get()))).build();
			} catch (Exception e) {
				msg2 = null;
			}

			if (this.config.consoleDelay != -1) {
				Task.builder().delay(this.config.consoleDelay, TimeUnit.MILLISECONDS)
						.execute(() -> Sponge.getServer().getConsole().sendMessage(msg1)).submit(this.plugin);
			}

			if (this.config.playerDelay != -1) {
				this.messages = msg2 == null ? new Text[]{msg1} : new Text[]{msg1, msg2};
			}
		}
	}

	@Listener(order = Order.LATE)
	public void onClientConnection(ClientConnectionEvent.Join e) {
		if (this.messages.length != 0) {
			Player p = e.getTargetEntity();
			if (p.hasPermission(this.permission)) {
				Task.builder().delay(this.config.playerDelay, TimeUnit.MILLISECONDS)
						.execute(() -> p.sendMessages(messages)).submit(this.plugin);
			}
		}
	}
}
