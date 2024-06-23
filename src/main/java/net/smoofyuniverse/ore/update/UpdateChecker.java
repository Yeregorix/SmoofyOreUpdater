/*
 * Copyright (c) 2021-2024 Hugo Dupanloup (Yeregorix)
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

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.smoofyuniverse.ore.OreAPI;
import net.smoofyuniverse.ore.project.OreProject;
import net.smoofyuniverse.ore.project.OreVersion;
import org.apache.logging.log4j.Logger;
import org.spongepowered.api.Server;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.lifecycle.RefreshGameEvent;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.scheduler.ScheduledTask;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.loader.ConfigurationLoader;
import org.spongepowered.plugin.PluginContainer;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class UpdateChecker {
	private final Logger logger;
	private final PluginContainer plugin;
	private final ConfigurationLoader<? extends ConfigurationNode> loader;
	private final OreProject project;
	private final Predicate<OreVersion> predicate;
	private final String permission;

	private final OreAPI api = new OreAPI();
	private ScheduledTask checkTask;
	private UpdateCheckConfig config;
	private Component[] messages = new Component[0];

	public UpdateChecker(Logger logger, PluginContainer plugin, ConfigurationLoader<? extends ConfigurationNode> loader,
						 String owner, String name) {
		this(logger, plugin, loader, owner, name, "spongeapi");
	}

	public UpdateChecker(Logger logger, PluginContainer plugin, ConfigurationLoader<? extends ConfigurationNode> loader,
						 String owner, String name, String spongeDependency) {
		this(logger, plugin, loader,
				new OreProject(plugin.metadata().id()),
				v -> v.dependencies.get(spongeDependency).startsWith("11."),
				plugin.metadata().id() + ".update.notify");
		if (owner == null || owner.isEmpty())
			throw new IllegalArgumentException("owner");
		if (name == null || name.isEmpty())
			throw new IllegalArgumentException("name");

		this.project.owner = owner;
		this.project.name = name;
	}

	public UpdateChecker(Logger logger, PluginContainer plugin, ConfigurationLoader<? extends ConfigurationNode> loader,
						 OreProject project, Predicate<OreVersion> predicate, String permission) {
		if (logger == null)
			throw new IllegalArgumentException("logger");
		if (plugin == null)
			throw new IllegalArgumentException("plugin");
		if (loader == null)
			throw new IllegalArgumentException("loader");
		if (project == null)
			throw new IllegalArgumentException("project");
		if (predicate == null)
			throw new IllegalArgumentException("predicate");
		if (permission == null || permission.isEmpty())
			throw new IllegalArgumentException("permission");

		this.logger = logger;
		this.loader = loader;
		this.plugin = plugin;
		this.project = project;
		this.predicate = predicate;
		this.permission = permission;
	}

	@Listener(order = Order.LATE)
	public void onServerStarted(StartedEngineEvent<Server> e) {
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
			this.config = root.get(UpdateCheckConfig.class);

			if (this.config == null)
				this.config = new UpdateCheckConfig();
			else
				this.config.normalize();

			root.set(this.config);
			this.loader.save(root);
		} catch (Exception ex) {
			this.logger.error("Failed to load update check configuration", ex);
			return;
		}

		if (this.config.enabled) {
			this.checkTask = Sponge.asyncScheduler().submit(
					Task.builder().interval(this.config.repetitionInterval, TimeUnit.HOURS).execute(this::check).plugin(this.plugin).build());
		}
	}

	private void check() {
		this.logger.debug("Checking for update ...");

		OreVersion latestVersion = null;
		try {
			latestVersion = OreVersion.getLatest(this.project.getVersions(this.api), this.predicate).orElse(null);
		} catch (Exception e) {
			this.logger.info("Failed to check for update", e);
		}

		String version = this.plugin.metadata().version().toString();
		if (latestVersion != null && !latestVersion.name.equals(version)) {
			Component msg1 = Component.join(JoinConfiguration.noSeparators(),
					Component.text("A new version of " + this.project.name + " is available: "),
					Component.text(latestVersion.name, NamedTextColor.AQUA),
					Component.text(". You're currently using version: "),
					Component.text(version, NamedTextColor.AQUA),
					Component.text("."));

			if (this.config.consoleDelay != -1) {
				Sponge.server().scheduler().submit(Task.builder().delay(this.config.consoleDelay, TimeUnit.MILLISECONDS)
						.execute(() -> Sponge.game().systemSubject().sendMessage(msg1)).plugin(this.plugin).build());
			}

			if (this.config.playerDelay != -1) {
				String page = latestVersion.getPage().orElse(null);
				if (page == null) {
					this.messages = new Component[]{msg1};
				} else {
					this.messages = new Component[]{msg1,
							Component.text().content("Click here to open the download page.")
									.color(NamedTextColor.GOLD).clickEvent(ClickEvent.openUrl(page)).build()
					};
				}
			}
		}
	}

	@Listener(order = Order.LATE)
	public void onRefreshGame(RefreshGameEvent e) {
		load();
	}

	@Listener(order = Order.LATE)
	public void onPlayerJoin(ServerSideConnectionEvent.Join e) {
		if (this.messages.length != 0) {
			ServerPlayer p = e.player();
			if (p.hasPermission(this.permission)) {
				Sponge.server().scheduler().submit(Task.builder().delay(this.config.playerDelay, TimeUnit.MILLISECONDS)
						.execute(() -> {
							for (Component msg : this.messages)
								p.sendMessage(msg);
						}).plugin(this.plugin).build());
			}
		}
	}
}
