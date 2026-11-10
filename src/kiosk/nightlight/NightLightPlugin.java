// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import android.content.Context;
import android.view.View;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import me.jxl.kiosk.plugins.KioskPlugin;
import me.jxl.kiosk.plugins.KsTheme;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.PluginHost;

/**
 * A night light for a child's room, drawn as a Kiosk Satellite screensaver. It opens on the night light, a dim warm
 * light. A button on it starts the soother: sleepy music or sounds while the screen drifts through soft colours,
 * which fades back to the night light when it is over or when End soother is pressed. KS still decides when the screensaver starts and ends; this
 * plugin only draws it and plays the music. A double tap closes it.
 */
public final class NightLightPlugin implements KioskPlugin, GlowView.Source, AudioOut.Source, SleepButton.Source {
    static final String ID = "night-light";
    static final String KEY = "nightlight";
    static final String MODE = "plugin:" + ID + ":" + KEY;
    static final String READY = "Ready. Choose Night Light under Screensaver mode, or run the Start night light action.";
    static final String SOUND_ONLY = "Night sound playing.";
    /** The Home Assistant switch for the sound. */
    static final String SOUND_KEY = "sound";
    /** The Home Assistant number for the night light volume, the nightVolume setting. */
    static final String VOLUME_KEY = "volume";
    static final String FADE_KEY = "fade";
    static final double FADE_STEP = 0.5;
    static final int VOLUME_STEP = 5;
    /** KS can rebuild the view (rotation, UI scale), so wait a moment before treating it as dismissed. */
    static final long END_GRACE_MS = 2500;
    /** The tap that runs a corner gesture also dismisses the screensaver, so look again after it has gone. */
    static final long TAP_SETTLE_MS = 800;
    static final String LIGHT_EVENT = "device.light";
    /** KS builds the view a moment before it counts it as on screen, so a tint asked for too soon is tried again. */
    static final long TINT_RETRY_MS = 1000;
    static final int TINT_TRIES = 10;

    private volatile PluginHost host;
    private volatile Config config = new Config(Collections.emptyMap());
    /** The settings behind config, so a change from Home Assistant can build the next one. */
    private Map<String, Object> settings = new HashMap<>();
    private final Session session;
    private ScheduledExecutorService timer;
    private ScheduledFuture<?> pendingEnd;
    private Sound audio;
    /** True while the sound plays on its own: started by Play sound, or kept by Keep sound when closed. */
    private boolean soundOnly;
    /** The soundOn value last saved, so only a change the user makes in settings plays or stops the sound. */
    private boolean savedSoundOn;
    /** False on a KS without plugin entities. */
    private boolean entities = true;
    /** False on a KS without plugin numbers. */
    private boolean numbers = true;
    private int views;
    /** True while this plugin, not the user's Screensaver mode, chose the night light. */
    private boolean overriding;
    /** True when KS passes taps to the screensaver, so it can have its Soother button. */
    private volatile boolean touch;
    /** The room is dark by the light sensor, for Night mode. */
    private volatile boolean dark;
    /** The colour KS's widgets are tinted, or null while they keep their own. */
    private String tint;
    private int tintTries;

    /** Makes the speaker. Tests swap it for a fake, since the JVM's Android stubs cannot build an AudioTrack. */
    Function<AudioOut.Source, Sound> soundFactory = source -> {
        AudioOut out = new AudioOut(source);
        out.start();
        return out;
    };
    /** The screensaver's grace period. Tests set it to 0. */
    long endGraceMs = END_GRACE_MS;

    public NightLightPlugin() {
        this(System::currentTimeMillis);
    }

    NightLightPlugin(Session.Clock clock) {
        session = new Session(clock);
    }

    @Override public synchronized void start(PluginHost host, Map<String, Object> settings) {
        this.host = host;
        setSettings(settings);
        timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "night-light-timer");
            thread.setDaemon(true);
            return thread;
        });
        try {
            try {
                host.publishScreensaverView(KEY, "Night Light", new Factory(), true);
                touch = true;
            } catch (UnsupportedOperationException | LinkageError noTouch) {
                // An older KS: the screensaver works, but every tap closes it, so it has no button.
                host.publishScreensaverView(KEY, "Night Light", new Factory());
                touch = false;
            }
            host.status(READY, false);
            watchLight(host);
        } catch (UnsupportedOperationException | LinkageError olderHost) {
            host.status("This Kiosk Satellite is too old for native screensavers. Update Kiosk Satellite.", true);
        }
        savedSoundOn = config.soundOn;
        if (config.resumeSound && config.soundOn) startSound(host);
        else soundChanged();
        publishVolume();
    }

    @Override public void configure(Map<String, Object> settings) {
        setSettings(settings);
        updateTint();
        publishVolume();
        PluginHost h = host;
        boolean changed;
        synchronized (this) { changed = config.soundOn != savedSoundOn; }
        if (h == null || !changed) return;
        if (config.soundOn) startSound(h);
        else stopSound(h);
    }

    /** Follows the room's light level for Night mode, the way the Clock screensaver does. */
    private void watchLight(PluginHost h) {
        try {
            h.subscribe(LIGHT_EVENT);
            h.executeCommand("getLightLevel", Collections.emptyMap(), (ok, data, error) -> {
                if (ok && data instanceof Map) light(((Map<?, ?>) data).get("lux"));
            });
        } catch (UnsupportedOperationException | SecurityException | LinkageError noSensor) {
            h.log("No light level: " + noSensor);
        }
    }

    private void light(Object lux) {
        if (lux instanceof Number) dark = dark(dark, ((Number) lux).doubleValue(), config.nightLux);
        updateTint();
    }

    /** Dark at or below the threshold, and light again only a quarter above it, so it never flickers. */
    static boolean dark(boolean wasDark, double lux, double threshold) {
        return lux <= (wasDark ? threshold * 1.25 : threshold);
    }

    /** In Night mode KS's corner widgets and At a Glance row take the Night color, like the rest of the screen. */
    static String tint(boolean night, boolean onScreen, int nightColor) {
        return night && onScreen ? String.format("#%06X", nightColor & 0xFFFFFF) : null;
    }

    /** Tints KS's widgets while Night mode is on, and gives them their own colours back when it ends. */
    private synchronized void updateTint() {
        PluginHost h = host;
        if (h == null) return;
        String want = tint(night(), views > 0, config.nightColor);
        if (want == null ? tint == null : want.equals(tint)) return;
        tint = want;
        try {
            if (want != null) {
                h.executeCommand("setScreensaverTint", Collections.singletonMap("color", want), (ok, d, e) -> {
                    if (!ok) tintFailed(h, want, e);
                });
            } else {
                h.executeCommand("clearScreensaverTint", Collections.emptyMap(), (ok, d, e) -> {});
            }
        } catch (RuntimeException | LinkageError olderHost) {
            // A KS without widget tints: the widgets keep their own colours.
            h.log("No widget tint: " + olderHost);
        }
    }

    /** Forgets a tint KS refused, and tries again shortly while the screensaver is still up. */
    private synchronized void tintFailed(PluginHost h, String colour, String error) {
        if (!colour.equals(tint)) return;
        tint = null;
        if (++tintTries >= TINT_TRIES || views == 0 || timer == null) {
            h.log("Could not tint the widgets: " + error);
            return;
        }
        try {
            timer.schedule(this::updateTint, TINT_RETRY_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopping) {
            // The plugin is stopping.
        }
    }

    @Override public void execute(String command, Map<String, Object> arguments) {
        PluginHost h = host;
        if (h == null) throw new IllegalStateException("Night light is not running");
        switch (command) {
            case "startNightLight": startNightLight(h); break;
            case "goToSleep": goToSleep(h); break;
            case "stopNightLight": stopNightLight(h); break;
            case "sleepNow": endShow(h); break;
            case "toggleSleep": toggleShow(); break;
            case "startSound": startSound(h); break;
            case "stopSound": stopSound(h); break;
            default: throw new IllegalArgumentException("Unknown action: " + command);
        }
    }

    private void startNightLight(PluginHost h) {
        session.endShow(config.colourMs);
        ensureShown(h);
    }

    private void goToSleep(PluginHost h) {
        session.startShow();
        h.status("Soother playing.", false);
        ensureShown(h);
    }

    private void endShow(PluginHost h) {
        session.endShow(config.colourMs);
        h.status("Fading back to the night light.", false);
    }

    /** Shows the night light unless it is on screen, checking again once a gesture's tap has dismissed it. */
    private synchronized void ensureShown(PluginHost h) {
        if (!onScreen()) { showScreensaver(h); return; }
        if (timer == null) return;
        try {
            timer.schedule(() -> { if (!onScreen()) showScreensaver(h); }, TAP_SETTLE_MS, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopping) {
            // The plugin is stopping.
        }
    }

    /** True while the screensaver view is up and not on its way out. */
    private synchronized boolean onScreen() {
        return session.live() && views > 0;
    }

    private void showScreensaver(PluginHost h) {
        synchronized (this) { overriding = true; }
        h.executeCommand("setScreensaverMode", Collections.singletonMap("mode", MODE), (ok, data, error) -> {
            if (!ok) { h.status("Could not switch to the night light: " + error, true); return; }
            h.executeCommand("startScreensaver", Collections.emptyMap(), (started, d, e) -> {
                if (!started) h.status("Could not start the screensaver: " + e, true);
            });
        });
    }

    /** Plays the night light sound with no screensaver, until Stop sound or Close Night Light. */
    private synchronized void startSound(PluginHost h) {
        soundOnly = true;
        ensureAudio();
        h.status(views > 0 ? "Night light on." : SOUND_ONLY, false);
        soundChanged();
    }

    /** Stops the sound, whatever started it. A screensaver that shows again starts it again. */
    private synchronized void stopSound(PluginHost h) {
        soundOnly = false;
        stopAudio();
        h.status(views > 0 ? "Night light on, sound stopped." : READY, false);
        soundChanged();
    }

    private void ensureAudio() {
        if (audio == null || !audio.alive()) audio = soundFactory.apply(this);
    }

    private void stopAudio() {
        if (audio != null) audio.stop();
        audio = null;
    }

    private void stopNightLight(PluginHost h) {
        boolean wasOverriding;
        synchronized (this) {
            wasOverriding = overriding;
            overriding = false;
            soundOnly = false;
            stopAudio();
            soundChanged();
        }
        h.status(READY, false);
        if (wasOverriding) h.executeCommand("clearScreensaverMode", Collections.emptyMap(), (ok, d, e) -> {});
        h.executeCommand("stopScreensaver", Collections.emptyMap(), (ok, d, e) -> {
            if (!ok) h.status("Could not stop the screensaver: " + e, true);
        });
    }

    private synchronized void setSettings(Map<String, Object> values) {
        settings = values == null ? new HashMap<>() : new HashMap<>(values);
        config = new Config(settings);
    }

    /** The night light volume as a whole percent on the setting's step, 0 to 100. */
    static int volumePercent(double value) {
        double clamped = Math.max(0, Math.min(100, value));
        return (int) (Math.round(clamped / VOLUME_STEP) * VOLUME_STEP);
    }

    /** The sound fade in seconds on the setting's step, 0 to 60. */
    static double fadeSeconds(double value) {
        double clamped = Math.max(0, Math.min(60, value));
        return Math.round(clamped / FADE_STEP) * FADE_STEP;
    }

    /** Sets the sound fade from Home Assistant, saves it, and confirms the applied value. */
    private void setFade(PluginHost h, Object value) {
        if (!(value instanceof Number)) return;
        double requested = ((Number) value).doubleValue();
        if (Double.isNaN(requested) || Double.isInfinite(requested)) return;
        Double applied = fadeSeconds(requested);
        synchronized (this) {
            settings.put("soundFade", applied);
            config = new Config(settings);
        }
        try {
            h.updateSettings(Collections.singletonMap("soundFade", (Object) applied));
        } catch (RuntimeException | LinkageError olderHost) {
            h.log("Could not save the sound fade: " + olderHost);
        }
        publishVolume();
    }

    /** Sets the night light volume from Home Assistant, saves it, and confirms the applied value. */
    private void setVolume(PluginHost h, Object value) {
        if (!(value instanceof Number)) return;
        double requested = ((Number) value).doubleValue();
        if (Double.isNaN(requested) || Double.isInfinite(requested)) return;
        Integer applied = volumePercent(requested);
        synchronized (this) {
            settings.put("nightVolume", applied);
            config = new Config(settings);
        }
        try {
            h.updateSettings(Collections.singletonMap("nightVolume", (Object) applied));
        } catch (RuntimeException | LinkageError olderHost) {
            h.log("Could not save the volume: " + olderHost);
        }
        publishVolume();
    }

    /** Tells Home Assistant the night light volume and the sound fade, as sliders. */
    private synchronized void publishVolume() {
        PluginHost h = host;
        if (h == null || !numbers) return;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("min", 0);
        metadata.put("max", 100);
        metadata.put("step", VOLUME_STEP);
        metadata.put("unit", "%");
        metadata.put("mode", "slider");
        try {
            h.publishNumber(VOLUME_KEY, "Night light volume", metadata, (double) volumePercent(config.nightVolume * 100));
            Map<String, Object> fade = new LinkedHashMap<>();
            fade.put("min", 0);
            fade.put("max", 60);
            fade.put("step", FADE_STEP);
            fade.put("unit", "s");
            fade.put("mode", "slider");
            h.publishNumber(FADE_KEY, "Sound fade", fade, fadeSeconds(config.soundFade));
        } catch (UnsupportedOperationException | LinkageError olderHost) {
            numbers = false;
        }
    }

    @Override public void onEvent(String event, Map<String, Object> payload) {
        if (("ks." + LIGHT_EVENT).equals(event) && payload != null) light(payload.get("lux"));
        if (("number." + VOLUME_KEY).equals(event) && payload != null) {
            PluginHost h = host;
            if (h != null) setVolume(h, payload.get("value"));
        }
        if (("number." + FADE_KEY).equals(event) && payload != null) {
            PluginHost h = host;
            if (h != null) setFade(h, payload.get("value"));
        }
        if (("switch." + SOUND_KEY).equals(event) && payload != null) {
            PluginHost h = host;
            if (h == null) return;
            if (Boolean.TRUE.equals(payload.get("on"))) startSound(h);
            else stopSound(h);
        }
    }

    /** Tells Home Assistant whether the sound plays, and saves whether it plays on its own for a restart. */
    private synchronized void soundChanged() {
        PluginHost h = host;
        if (h == null) return;
        if (entities) {
            try {
                h.publishSwitch(SOUND_KEY, "Night light sound", audio != null && audio.alive());
            } catch (UnsupportedOperationException | LinkageError olderHost) {
                entities = false;
            }
        }
        if (soundOnly == savedSoundOn) return;
        savedSoundOn = soundOnly;
        try {
            h.updateSettings(Collections.singletonMap("soundOn", (Object) soundOnly));
        } catch (RuntimeException | LinkageError olderHost) {
            h.log("Could not save the sound state: " + olderHost);
        }
    }

    @Override public synchronized void stop() {
        if (audio != null) audio.stopNow();
        audio = null;
        soundOnly = false;
        if (timer != null) timer.shutdownNow();
        timer = null;
        session.ended();
        host = null;
    }

    // ---- Screensaver lifecycle --------------------------------------------------------------------------------

    /** Called on the main thread when KS builds the screensaver view. */
    synchronized void viewShown() {
        views++;
        if (pendingEnd != null) { pendingEnd.cancel(false); pendingEnd = null; }
        session.shown();
        ensureAudio();
        soundChanged();
        PluginHost h = host;
        if (h == null) return;
        h.status(session.showing(config.colourMs) ? "Soother playing." : "Night light on.", false);
        // KS drops the tint whenever the screensaver leaves the screen, so set it again each time it shows.
        tint = null;
        tintTries = 0;
        updateTint();
    }

    /** Called on the main thread when KS removes the view. */
    synchronized void viewGone() {
        views = Math.max(0, views - 1);
        if (views > 0 || timer == null) return;
        pendingEnd = timer.schedule(this::ended, endGraceMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void ended() {
        pendingEnd = null;
        if (views > 0) return;
        tint = null;
        session.ended();
        if (audio != null && (soundOnly || config.keepSound)) soundOnly = true;
        else stopAudio();
        PluginHost h = host;
        if (h == null) return;
        // The Start action only borrows the screensaver for the night. Give the usual one back in the morning.
        if (overriding) {
            overriding = false;
            h.executeCommand("clearScreensaverMode", Collections.emptyMap(), (ok, d, e) -> {});
        }
        h.status(soundOnly ? SOUND_ONLY : READY, false);
        soundChanged();
    }

    /** True while the screensaver's grace timer is waiting to run. */
    synchronized boolean endPending() {
        return pendingEnd != null;
    }

    synchronized boolean audioRunning() {
        return audio != null && audio.alive();
    }

    // ---- Sources for the view and the audio thread -------------------------------------------------------------

    @Override public Config config() {
        return config;
    }

    @Override public long elapsed() {
        return session.elapsed();
    }

    @Override public double fade() {
        return session.fade(config);
    }

    @Override public boolean night() {
        return dark && config.nightMode;
    }

    @Override public void log(String message) {
        PluginHost h = host;
        if (h != null) h.log(message);
    }

    @Override public boolean showing() {
        return session.showing(config.colourMs);
    }

    @Override public void toggleShow() {
        PluginHost h = host;
        if (h == null) return;
        if (showing()) endShow(h);
        else goToSleep(h);
    }

    Session session() {
        return session;
    }

    private final class Factory implements OverlayFactory {
        @Override public View create(Context context, KsTheme theme) {
            GlowView glow = new GlowView(context, NightLightPlugin.this);
            View view = touch ? new NightLightView(context, theme, glow, NightLightPlugin.this) : glow;
            viewShown();
            return view;
        }

        @Override public void onDestroy(View view) {
            viewGone();
        }
    }
}
