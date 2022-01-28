/*
 * Copyright (c) 2021-2022 Hugo Dupanloup (Yeregorix)
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

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import static java.lang.Math.max;

@ConfigSerializable
public class UpdateCheckConfig {
	@Comment("Enable or disable automatic update checking")
	@Setting("Enabled")
	public boolean enabled = true;

	@Comment("Interval in hours between update checking repetitions, 0 to disable")
	@Setting("RepetitionInterval")
	public int repetitionInterval = 12;

	@Comment("Delay in milliseconds before sending a message to the console, -1 to disable message")
	@Setting("ConsoleDelay")
	public int consoleDelay = 1000;

	@Comment("Delay in milliseconds before sending a message after a player connection, -1 to disable message")
	@Setting("PlayerDelay")
	public int playerDelay = 1000;

	public void normalize() {
		this.repetitionInterval = max(this.repetitionInterval, 0);
		this.consoleDelay = max(this.consoleDelay, -1);
		this.playerDelay = max(this.playerDelay, -1);

		if (this.consoleDelay == -1 && this.playerDelay == -1)
			this.enabled = false;
	}
}
