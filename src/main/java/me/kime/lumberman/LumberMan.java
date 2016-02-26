/* 
 * The MIT License
 *
 * Copyright 2016 Kime.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package me.kime.lumberman;

import com.google.inject.Inject;
import java.util.logging.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingEvent;
import org.spongepowered.api.plugin.Plugin;

/**
 *
 * @author Kime
 */
@Plugin(id = "LimberMan", name = "LimberMan", version = "${project.version}")
public class LumberMan {

    private LMListener eventListener;

    @Inject
    private Logger logger;

    @Listener
    public void onDisable(GameStoppingEvent event) {
        Sponge.getEventManager().unregisterListeners(eventListener);

        logger.info("LumberMan Disabled!");
    }

    @Listener
    public void onServerStart(GameStartedServerEvent event) {
        eventListener = new LMListener(this);

        Sponge.getEventManager().registerListeners(this, eventListener);

        logger.info("LumberMan Enabled!");
    }

    public Logger getLogger() {
        return logger;
    }
}
