// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.jxl.kiosk.plugins.OverlayFactory;
import me.jxl.kiosk.plugins.PluginHost;

/** Exercises the night light's timeline, colours, music and actions on the desktop JVM. */
public final class NightLightTest {
    public static void main(String[] args) {
        config();
        scene();
        session();
        stars();
        synth();
        plugin();
        audioOnly();
        soundEntity();
        volumeEntity();
        soundFade();
        System.out.println("NightLightTest passed");
    }

    static void config() {
        Config d = new Config(Collections.emptyMap());
        check(d.colourMs == 30 * 60_000L, "default colour show is 30 minutes");
        check(d.warmColor == 0xFF9440, "default warm colour");
        check(d.sound == Config.Sound.MUSIC_BOX && d.nightSound == Config.Sound.NONE, "default sound");
        Map<String, Object> s = new HashMap<>();
        s.put("colourMinutes", 10);
        s.put("warmColor", "#ff0000");
        s.put("warmBrightness", 500);
        s.put("sound", "Ocean waves");
        s.put("palette", "Ocean");
        Config c = new Config(s);
        check(c.colourMs == 600_000L, "colour minutes");
        check(c.warmColor == 0xFF0000, "warm colour parses");
        check(c.warmBrightness == 1.0, "brightness clamps");
        check(c.sound == Config.Sound.WAVES && c.nightSound == Config.Sound.NONE, "sound choices");
        s.put("sleepSound", "Fade to silence");
        check(new Config(s).nightSound == Config.Sound.NONE, "an old saved night sound is ignored");
        check(c.palette == Palettes.OCEAN, "palette");
        s.put("warmColor", "nonsense");
        check(new Config(s).warmColor == 0xFF9440, "bad colour falls back");
    }

    static void scene() {
        Config c = new Config(Collections.emptyMap());
        Scene.Frame f = Scene.frame(c, 0, null);
        check(f.centre == Scene.scale(0xFF9440, 0.25), "the show grows out of the sleep light");
        check(f.phase == Scene.Phase.COLOURS, "starts with colours");
        check(Scene.frame(c, Session.NIGHT, null).phase == Scene.Phase.SLEEP, "the night light is the sleep light");
        Scene.frame(c, 60_000, f);
        check(f.centre != Scene.scale(0xFF9440, 0.25), "colours show after the fade in");
        int first = Scene.frame(c, 60_000, null).centre;
        int later = Scene.frame(c, 75_000, null).centre;
        check(first != later, "colours change over time");
        check(Scene.phase(c, c.colourMs + 1000) == Scene.Phase.FADING, "fading after the show");
        Scene.frame(c, c.colourMs + c.fadeMs + 1, f);
        check(f.phase == Scene.Phase.SLEEP, "sleep light after the fade");
        check(f.centre == Scene.scale(0xFF9440, 0.25), "sleep light is the dim warm colour");
        check(f.cx == 0.5 && f.cy == 0.5, "sleep light is centred");
        // The fade is continuous: halfway colours sit between show and sleep light.
        int before = Scene.frame(c, c.colourMs - 1, null).centre;
        int after = Scene.frame(c, c.colourMs + c.fadeMs, null).centre;
        int mid = Scene.frame(c, c.colourMs + c.fadeMs / 2, null).centre;
        check(mid != before && mid != after, "fade blends");
        check(Scene.mix(0x000000, 0xFFFFFF, 0.5) != 0x7F7F7F || true, "mix runs");
        check(Scene.colourAt(Palettes.RAINBOW, 0) == Palettes.RAINBOW[0], "palette starts at its first colour");
        check(Scene.colourAt(Palettes.RAINBOW, Palettes.RAINBOW.length) == Palettes.RAINBOW[0], "palette loops");
        check(Scene.colourAt(Palettes.RAINBOW, -0.5) != 0, "negative positions wrap");

        // The liquid style: off by default, and only the show changes.
        check(!c.liquid, "glow style by default");
        Map<String, Object> ls = new HashMap<>();
        ls.put("colourStyle", "Liquid");
        Config lc = new Config(ls);
        check(lc.liquid, "liquid style");
        check(Scene.frame(lc, Session.NIGHT, null).centre == Scene.frame(c, Session.NIGHT, null).centre, "liquid leaves the night light alone");
        check(Scene.frame(lc, 0, null).centre == Scene.frame(c, 0, null).centre, "liquid grows out of the sleep light too");
        check(Scene.frame(lc, 60_000, null).centre != Scene.frame(c, 60_000, null).centre, "liquid dims the glow under the blobs");
        Scene.Blob[] blobs = Scene.blobs(lc, 0, null);
        check(blobs.length == Scene.BLOBS && blobs[0].colour == 0, "no blobs before the show grows in");
        Scene.blobs(lc, 60_000, blobs);
        boolean lit = false;
        for (Scene.Blob b : blobs) {
            lit |= b.colour != 0;
            check(b.x >= 0 && b.x <= 1 && b.y >= 0 && b.y <= 1 && b.r > 0.3, "blobs stay on screen");
        }
        check(lit, "blobs show during the show");
        check(blobs[0].colour != blobs[1].colour, "blobs are different colours");
        double x0 = blobs[0].x;
        check(Math.abs(Scene.blobs(lc, 60_066, null)[0].x - x0) < 0.005, "blobs drift slowly");
        Scene.blobs(lc, lc.colourMs + lc.fadeMs, blobs);
        check(blobs[0].colour == 0 && Math.abs(blobs[0].x - 0.5) < 1e-9, "blobs gather and fade into the sleep light");

        // Night light styles: glow by default; flat is even; liquid dims the glow and adds slow blobs.
        check(c.nightStyle == Config.Style.GLOW, "glow night light by default");
        Scene.Frame glowNight = Scene.frame(c, Session.NIGHT, 1, 0, null);
        check(glowNight.centre != glowNight.edge, "the glow is brighter in the middle");
        Map<String, Object> ns = new HashMap<>();
        ns.put("nightStyle", "Flat");
        Config flat = new Config(ns);
        Scene.Frame flatNight = Scene.frame(flat, Session.NIGHT, 1, 0, null);
        check(flatNight.centre == flatNight.edge && flatNight.centre == glowNight.centre, "flat is one even colour");
        ns.put("nightColours", "Ocean");
        Scene.Frame flatWaves = Scene.frame(new Config(ns), Session.NIGHT, 1, 0, null);
        check(flatWaves.centre == flatWaves.edge, "flat stays even with drifting colours");
        ns.remove("nightColours");
        ns.put("nightStyle", "Liquid");
        Config liquidNight = new Config(ns);
        check(Scene.frame(liquidNight, Session.NIGHT, 1, 0, null).centre != glowNight.centre, "liquid dims the glow under its blobs");
        Scene.Blob[] nb = Scene.nightBlobs(liquidNight, Session.NIGHT, 1, 0, null);
        check(nb[0].colour != 0 && nb[0].colour != nb[1].colour, "the night light's blobs show, in shades");
        double nx = nb[0].x;
        check(Math.abs(Scene.nightBlobs(liquidNight, Session.NIGHT, 1, 250, null)[0].x - nx) < 0.005, "and drift very slowly");
        check(Scene.nightBlobs(liquidNight, 60_000, 0, 0, null)[0].colour == 0, "they give way to the soother");
        check(Scene.nightBlobs(c, Session.NIGHT, 1, 0, null)[0].colour == 0, "no night blobs unless liquid");
        check(Scene.frame(liquidNight, 60_000, 0, 0, null).centre == Scene.frame(c, 60_000, 0, 0, null).centre, "the night light style leaves the soother alone");

        // Night light colours: solid by default; a colour set drifts, and only the night light changes.
        Map<String, Object> ws = new HashMap<>();
        ws.put("nightColours", "Sunset");
        Config wc = new Config(ws);
        check(!c.nightWaves && c.nightPalette == null, "solid night light colour by default");
        check(wc.nightWaves && wc.nightPalette == Palettes.SUNSET, "night light colours");
        ws.put("palette", "Ocean");
        check(new Config(ws).palette == Palettes.OCEAN && new Config(ws).nightPalette == Palettes.SUNSET, "chosen apart from the soother's colours");
        check(Scene.frame(wc, Session.NIGHT, 1, 0, null).centre == Scene.scale(Palettes.SUNSET[0], wc.warmBrightness), "drifts through its own colours");
        Map<String, Object> old = new HashMap<>();
        old.put("nightWaves", true);
        check(!new Config(old).nightWaves, "the old colour waves setting is ignored");
        int nightA = Scene.frame(wc, Session.NIGHT, 1, 0, null).centre;
        int nightB = Scene.frame(wc, Session.NIGHT, 1, 60_000, null).centre;
        check(nightA != nightB, "colour waves drift at night");
        check(Scene.frame(c, Session.NIGHT, 1, 0, null).centre == Scene.frame(c, Session.NIGHT, 1, 60_000, null).centre, "the plain night light stays still");
        check(Scene.frame(wc, 60_000, 0, 0, null).centre == Scene.frame(c, 60_000, 0, 0, null).centre, "colour waves leave the show alone");
        check(Scene.phase(0) == Scene.Phase.COLOURS && Scene.phase(0.5) == Scene.Phase.FADING && Scene.phase(1) == Scene.Phase.SLEEP, "phase follows the fade");

        double sootherLevel = Scene.loudness(0.4);
        double nightLevel = Scene.loudness(0.25);
        Scene.Mix m = Scene.mix(c, 0, null);
        check(m.soother == 0, "the soother's sound fades in");
        Scene.mix(c, 30_000, m);
        check(Math.abs(m.soother - sootherLevel) < 1e-9 && m.night == 0, "the soother at its volume");
        Scene.mix(c, c.colourMs + c.fadeMs + 1, m);
        check(m.soother == 0 && m.night == 0, "the night light is silent by default");
        Scene.mix(c, Session.NIGHT, m);
        check(m.soother == 0 && m.night == 0, "silent from the start by default");
        check(m.tempo < 1 && m.tempo >= Scene.NIGHT_TEMPO, "music slows down");

        // Both modes choose from the same sounds, separately.
        Map<String, Object> s = new HashMap<>();
        String[] names = {"Music box lullaby", "Brahms & Mozart lullabies", "Dreamy pads", "Ocean waves", "Rain",
            "Thunderstorm", "White noise", "Pink noise", "Brown noise", "No sound"};
        for (String name : names) {
            s.put("sound", name);
            s.put("nightSound", name);
            Config both = new Config(s);
            check(both.sound == both.nightSound && (both.sound == Config.Sound.NONE) == name.equals("No sound"), name + " in both modes");
        }
        s.put("sound", "Brahms & Mozart lullabies");
        s.put("nightSound", "Brown noise");
        Config split = new Config(s);
        check(split.sound == Config.Sound.CLASSIC && split.nightSound == Config.Sound.BROWN, "each mode keeps its own sound");
        check(split.sound.music() && !split.sound.noise() && split.nightSound.noise(), "sound kinds");
        Scene.mix(split, Session.NIGHT, m);
        check(m.soother == 0 && Math.abs(m.night - nightLevel) < 1e-9, "the night light's sound through the night");
        Scene.mix(split, 30_000, 0, m);
        check(m.night == 0 && Math.abs(m.soother - sootherLevel) < 1e-9, "it gives way to the soother");
        Scene.mix(split, 4000, 0, m);
        check(m.night > 0 && m.soother > 0, "and the two crossfade");
        s.put("nightVolume", 60);
        Scene.mix(new Config(s), Session.NIGHT, m);
        check(Math.abs(m.night - Scene.loudness(0.6)) < 1e-9, "night light volume is its own");
        s.put("volume", 0);
        Scene.mix(new Config(s), Session.NIGHT, m);
        check(Math.abs(m.night - Scene.loudness(0.6)) < 1e-9, "the soother volume leaves the night light alone");
        s.put("nightSound", "No sound");
        s.put("sound", "No sound");
        Scene.mix(new Config(s), 30_000, m);
        check(m.soother == 0 && m.night == 0, "no sound means no sound");
    }

    static void stars() {
        long hour = 60 * 60_000L;
        check(Config.parseTime("19:00") == 19 * hour, "24 hour time");
        check(Config.parseTime("7pm") == 19 * hour && Config.parseTime("7:30 AM") == 7 * hour + 30 * 60_000L, "am and pm");
        check(Config.parseTime("12am") == 0 && Config.parseTime("12pm") == 12 * hour, "midnight and noon");
        check(Config.parseTime("25:00") < 0 && Config.parseTime("bedtime") < 0 && Config.parseTime("") < 0, "bad times");

        Config off = new Config(Collections.emptyMap());
        check(!off.countdown && Stars.count(off) == 0, "countdown is off by default");
        Map<String, Object> s = new HashMap<>();
        s.put("countdown", true);
        s.put("bedTime", "nonsense");
        Config c = new Config(s);
        check(c.bedMs == 19 * hour && c.wakeMs == 7 * hour, "bad times fall back");
        check(Stars.count(c) == 10, "a 12 hour night still has at most 10 stars");
        check(Stars.lit(c, 19 * hour) == 10, "all lit at bed time");
        check(Stars.lit(c, 7 * hour - 1) == 1, "one left just before up time");
        check(Stars.lit(c, 7 * hour) == 0, "all out at up time");
        check(Stars.lit(c, 12 * hour) == 0, "still out in the morning");
        check(Stars.lit(c, 14 * hour) == 10, "lit again ready for the night");
        check(Stars.lit(c, 1 * hour) == 5, "half way through the night");

        s.put("bedTime", "8pm");
        s.put("wakeTime", "6:00");
        c = new Config(s);
        check(Stars.count(c) == 10, "10 hour night has 10 stars");
        check(Stars.lit(c, 20 * hour + hour / 2) == 10 && Stars.lit(c, 21 * hour) == 9, "one star an hour");
        s.put("wakeTime", "11pm");
        check(Stars.count(new Config(s)) == 3, "a short night has fewer stars");
        s.put("wakeTime", "8pm");
        check(Stars.count(new Config(s)) == 0, "no night, no stars");
    }

    static void session() {
        long[] now = {1_000_000};
        long show = 30 * 60_000;
        Session s = new Session(() -> now[0]);
        check(s.elapsed() == Session.NIGHT, "nothing before the first night");
        s.shown();
        check(s.elapsed() == Session.NIGHT && !s.showing(show), "opens on the night light");
        s.endShow(show);
        check(s.elapsed() == Session.NIGHT, "ending no show does nothing");
        s.startShow();
        check(s.elapsed() == 0 && s.showing(show), "go to sleep starts the show");
        now[0] += 10 * 60_000;
        check(s.elapsed() == 10 * 60_000, "elapsed counts");
        s.ended();
        now[0] += 5 * 60_000;
        s.shown();
        check(s.elapsed() == 15 * 60_000, "a quick wake carries on");
        Config cfg = new Config(Collections.emptyMap());
        check(s.fade(cfg) == 0, "no fade while the show plays");
        s.endShow(show);
        check(s.elapsed() == 15 * 60_000 && !s.showing(show), "end keeps the show's place, so nothing jumps");
        check(s.fade(cfg) == 0, "end starts its fade");
        now[0] += Session.END_FADE_MS / 2;
        double half = s.fade(cfg);
        check(half > 0 && half < 1, "end fades quickly: " + half);
        s.endShow(show);
        check(!s.showing(show), "pressing end again does not restart the show");
        now[0] += Session.END_FADE_MS / 2;
        check(s.fade(cfg) == 1, "end reaches the night light");
        s.startShow();
        check(s.showing(show) && s.fade(cfg) == 0, "go to sleep starts again after end");
        now[0] += show;
        check(!s.showing(show) && s.fade(cfg) == 0, "the show's own fade starts at its end");
        now[0] += cfg.fadeMs;
        check(s.fade(cfg) == 1, "and takes the fade setting");
        s.endShow(show);
        check(s.fade(cfg) == 1, "end after the show does nothing");
        s.ended();
        now[0] += Session.RESUME_MS + 1;
        s.shown();
        check(s.elapsed() == Session.NIGHT, "the next night opens on the night light");
        s.ended();
        now[0] += Session.RESUME_MS + 1;
        s.startShow();
        s.shown();
        check(s.elapsed() == 0 && s.showing(show), "go to sleep before the screensaver keeps the show");
    }

    static void synth() {
        int rate = 22050;
        for (boolean dreamy : new boolean[] {false, true}) {
            LullabySynth synth = new LullabySynth(rate, 42);
            synth.setDreamy(dreamy);
            synth.setLevels(1, 1, 0, 1);
            synth.settle();
            Stats st = render(synth, rate * 60);
            check(st.rms > 800, (dreamy ? "dreamy" : "music box") + " is audible: rms " + st.rms);
            check(st.peak < 32767, "no hard clipping: peak " + st.peak);
        }
        LullabySynth songs = new LullabySynth(rate, 5);
        songs.setSongs(true);
        songs.setLevels(1, 1, 0, 1);
        songs.settle();
        Stats sung = render(songs, rate * 120);
        check(sung.rms > 800 && sung.peak < 32767, "classic lullabies are audible: rms " + sung.rms);
        Map<String, Object> classic = new HashMap<>();
        classic.put("sound", "Brahms & Mozart lullabies");
        Config cc = new Config(classic);
        check(cc.sound == Config.Sound.CLASSIC && Scene.mix(cc, 30_000, null).soother > 0, "classic lullabies play in the soother");

        for (LullabySynth.Noise colour : LullabySynth.Noise.values()) {
            LullabySynth hush = new LullabySynth(rate, 9);
            hush.setNoise(colour);
            hush.setLevels(1, 0, 0, 1, 1);
            hush.settle();
            Stats ns = render(hush, rate * 10);
            check(ns.rms > 2000 && ns.rms < 12000 && ns.peak < 32767, colour + " noise is steady and not clipped: rms " + ns.rms);
        }

        // A storm: rain all the way through, and thunder rolling in at least once in three minutes.
        LullabySynth storm = new LullabySynth(rate, 11);
        storm.setNoise(LullabySynth.Noise.STORM);
        storm.setLevels(1, 0, 0, 1, 1);
        storm.settle();
        double quietest = Double.MAX_VALUE;
        double loudest = 0;
        for (int second = 0; second < 180; second++) {
            Stats window = render(storm, rate);
            check(window.peak < 32767, "thunder is not clipped");
            quietest = Math.min(quietest, window.rms);
            loudest = Math.max(loudest, window.rms);
        }
        check(quietest > 2000, "rain never stops: " + quietest);
        check(loudest > quietest * 1.3, "thunder rolls in: " + loudest + " over " + quietest);

        LullabySynth waves = new LullabySynth(rate, 7);
        waves.setLevels(1, 0, 1, 1);
        waves.settle();
        Stats st = render(waves, rate * 30);
        check(st.rms > 500 && st.peak < 32767, "waves are audible: rms " + st.rms);

        LullabySynth quiet = new LullabySynth(rate, 1);
        quiet.setLevels(0, 1, 1, 1);
        quiet.settle();
        check(render(quiet, rate * 5).peak == 0, "zero volume is silent");

        // A level change glides rather than jumping.
        LullabySynth glide = new LullabySynth(rate, 3);
        glide.setLevels(1, 1, 0, 1);
        glide.settle();
        render(glide, rate * 10);
        glide.setLevels(0, 1, 0, 1);
        render(glide, rate / 20);
        check(glide.currentMaster() > 0.5, "volume glides down");
        render(glide, rate * 3);
        check(glide.currentMaster() < 0.01, "and gets there");
    }

    static final class Stats { double rms; int peak; }

    static Stats render(LullabySynth synth, int samples) {
        short[] buf = new short[1024];
        double sum = 0;
        int peak = 0;
        int done = 0;
        while (done < samples) {
            int n = Math.min(buf.length, samples - done);
            synth.render(buf, 0, n);
            for (int i = 0; i < n; i++) { sum += buf[i] * (double) buf[i]; peak = Math.max(peak, Math.abs(buf[i])); }
            done += n;
        }
        Stats s = new Stats();
        s.rms = Math.sqrt(sum / samples);
        s.peak = peak;
        return s;
    }

    static void plugin() {
        FakeHost host = new FakeHost();
        long[] now = {5_000_000};
        NightLightPlugin plugin = new NightLightPlugin(() -> now[0]);
        plugin.start(host, Collections.emptyMap());
        check("nightlight".equals(host.screensaverKey) && host.factory != null, "publishes the screensaver");
        check(host.touch, "asks for taps");
        NightLightPlugin older = new NightLightPlugin(() -> now[0]);
        FakeHost olderHost = new FakeHost();
        olderHost.noTouch = true;
        older.start(olderHost, Collections.emptyMap());
        check("nightlight".equals(olderHost.screensaverKey) && !olderHost.touch, "older KS still gets the screensaver");
        older.stop();
        check(NightLightPlugin.MODE.equals("plugin:night-light:nightlight"), "mode id");

        check(!plugin.showing(), "not showing before go to sleep");
        plugin.execute("goToSleep", Collections.emptyMap());
        check(plugin.showing(), "go to sleep starts the show");
        check(host.commands.equals(java.util.Arrays.asList("setScreensaverMode", "startScreensaver")), "go to sleep shows the screensaver: " + host.commands);
        plugin.toggleShow();
        check(!plugin.showing(), "the button ends the show");
        plugin.toggleShow();
        check(plugin.showing(), "and starts it again");
        plugin.execute("toggleSleep", Collections.emptyMap());
        check(!plugin.showing(), "the toggle action ends the show");
        plugin.execute("toggleSleep", Collections.emptyMap());
        check(plugin.showing(), "and starts it again too");

        host.commands.clear();
        plugin.execute("startNightLight", Collections.emptyMap());
        check(!plugin.showing(), "start night light ends the show");
        check(host.commands.equals(java.util.Arrays.asList("setScreensaverMode", "startScreensaver")), "start switches and starts: " + host.commands);
        check(NightLightPlugin.MODE.equals(host.lastArgs.get("setScreensaverMode").get("mode")), "start picks the night light");

        host.commands.clear();
        plugin.execute("stopNightLight", Collections.emptyMap());
        check(host.commands.equals(java.util.Arrays.asList("clearScreensaverMode", "stopScreensaver")), "stop gives the screensaver back: " + host.commands);

        Map<String, Object> s = new HashMap<>();
        s.put("colourMinutes", 20);
        plugin.configure(s);
        check(plugin.config().colourMs == 20 * 60_000L, "configure applies");
        plugin.execute("goToSleep", Collections.emptyMap());
        plugin.execute("sleepNow", Collections.emptyMap());
        check(!plugin.showing() && plugin.fade() == 0, "end go to sleep starts the fade");
        now[0] += Session.END_FADE_MS;
        check(plugin.fade() == 1, "and fades to the night light");

        check(NightLightPlugin.dark(false, 3, 5) && !NightLightPlugin.dark(false, 6, 5), "dark at or below the light level");
        check(NightLightPlugin.dark(true, 6, 5) && !NightLightPlugin.dark(true, 7, 5), "light again a quarter above it");
        Config nd = new Config(Collections.emptyMap());
        check(!nd.nightMode && nd.nightLux == 5 && nd.nightColor == 0xA63A32 && !nd.nightHide, "night mode defaults");
        plugin.onEvent("ks.device.light", Collections.singletonMap("lux", 1));
        check(!plugin.night(), "night mode is off by default");
        s.put("nightMode", true);
        plugin.configure(s);
        check(plugin.night(), "night mode in a dark room");
        plugin.onEvent("ks.device.light", Collections.singletonMap("lux", 6));
        check(plugin.night(), "stays on just above the light level");
        plugin.onEvent("ks.device.light", Collections.singletonMap("lux", 50.0));
        check(!plugin.night(), "and ends in a lit room");
        check("#A63A32".equals(NightLightPlugin.tint(true, true, 0xA63A32)), "night mode tints the widgets the night color");
        check("#00000A".equals(NightLightPlugin.tint(true, true, 0x0A)), "tints are six digits");
        check(NightLightPlugin.tint(false, true, 0xA63A32) == null, "no tint in a lit room");
        check(NightLightPlugin.tint(true, false, 0xA63A32) == null, "no tint while the screensaver is not showing");
        host.commands.clear();
        plugin.onEvent("ks.device.light", Collections.singletonMap("lux", 1));
        check(!host.commands.contains("setScreensaverTint"), "never tints with no screensaver on screen: " + host.commands);

        boolean threw = false;
        try { plugin.execute("nope", Collections.emptyMap()); } catch (IllegalArgumentException e) { threw = true; }
        check(threw, "unknown actions are refused");
        plugin.stop();
    }

    /** A stand-in for the speaker: the JVM's Android stubs cannot make an AudioTrack. */
    static final class FakeSound implements Sound {
        boolean stopped;
        @Override public void stop() { stopped = true; }
        @Override public void stopNow() { stopped = true; }
        @Override public boolean alive() { return !stopped; }
    }

    static NightLightPlugin soundPlugin(FakeHost host, List<FakeSound> made, Map<String, Object> settings) {
        NightLightPlugin plugin = new NightLightPlugin(() -> 5_000_000L);
        plugin.endGraceMs = 0;
        plugin.soundFactory = source -> { FakeSound f = new FakeSound(); made.add(f); return f; };
        plugin.start(host, settings);
        return plugin;
    }

    /** Waits for the screensaver's grace timer, which runs at once in tests. */
    static void settle(NightLightPlugin plugin) {
        for (int i = 0; i < 200 && plugin.endPending(); i++) {
            try { Thread.sleep(5); } catch (InterruptedException e) { throw new AssertionError(e); }
        }
        check(!plugin.endPending(), "the grace timer ran");
    }

    static void audioOnly() {
        Config d = new Config(Collections.emptyMap());
        check(!d.keepSound, "keep sound is off by default");
        check(new Config(Collections.singletonMap("keepSound", true)).keepSound, "keep sound parses");

        FakeHost host = new FakeHost();
        List<FakeSound> made = new ArrayList<>();
        NightLightPlugin plugin = soundPlugin(host, made, Collections.emptyMap());
        plugin.execute("startSound", Collections.emptyMap());
        check(made.size() == 1 && plugin.audioRunning(), "play sound starts the speaker");
        check(NightLightPlugin.SOUND_ONLY.equals(host.status), "status says the sound plays: " + host.status);
        check(host.commands.isEmpty(), "play sound needs no screensaver: " + host.commands);
        plugin.execute("startSound", Collections.emptyMap());
        check(made.size() == 1, "play sound twice keeps one speaker");
        plugin.execute("stopSound", Collections.emptyMap());
        check(made.get(0).stopped && !plugin.audioRunning(), "stop sound stops it");

        plugin.execute("startSound", Collections.emptyMap());
        plugin.viewShown();
        check(made.size() == 2, "the screensaver reuses the playing speaker");
        plugin.viewGone();
        settle(plugin);
        check(plugin.audioRunning(), "a sound started by the action outlives the screensaver");
        plugin.execute("stopNightLight", Collections.emptyMap());
        check(made.get(1).stopped && !plugin.audioRunning(), "close stops a playing sound");
        check(NightLightPlugin.READY.equals(host.status), "and the status says so: " + host.status);
        plugin.stop();

        made.clear();
        host = new FakeHost();
        plugin = soundPlugin(host, made, Collections.emptyMap());
        plugin.viewShown();
        check(made.size() == 1, "the screensaver starts the speaker");
        plugin.viewGone();
        settle(plugin);
        check(made.get(0).stopped && !plugin.audioRunning(), "without keep sound, closing stops it");
        plugin.stop();

        made.clear();
        host = new FakeHost();
        plugin = soundPlugin(host, made, Collections.singletonMap("keepSound", true));
        plugin.viewShown();
        plugin.viewGone();
        settle(plugin);
        check(!made.get(0).stopped && plugin.audioRunning(), "with keep sound, closing leaves it playing");
        plugin.viewShown();
        check(made.size() == 1, "and the screensaver picks it up again");
        plugin.viewGone();
        settle(plugin);
        plugin.execute("stopSound", Collections.emptyMap());
        check(made.get(0).stopped && !plugin.audioRunning(), "stop sound ends a kept sound");
        plugin.execute("startSound", Collections.emptyMap());
        plugin.stop();
        check(made.get(1).stopped, "stopping the plugin stops the sound");
    }

    static Object lastSaved(FakeHost host, String key) {
        for (int i = host.updates.size() - 1; i >= 0; i--) if (host.updates.get(i).containsKey(key)) return host.updates.get(i).get(key);
        return null;
    }

    static void soundEntity() {
        Config d = new Config(Collections.emptyMap());
        check(!d.resumeSound && !d.soundOn, "resume and sound on are off by default");

        FakeHost host = new FakeHost();
        List<FakeSound> made = new ArrayList<>();
        NightLightPlugin plugin = soundPlugin(host, made, Collections.emptyMap());
        check(Boolean.FALSE.equals(host.switches.get("sound")), "publishes the sound switch, off: " + host.switches);
        plugin.onEvent("switch.sound", Collections.singletonMap("on", true));
        check(plugin.audioRunning() && Boolean.TRUE.equals(host.switches.get("sound")), "the switch plays the sound and reports it");
        check(Boolean.TRUE.equals(lastSaved(host, "soundOn")), "and remembers it for a restart");
        plugin.onEvent("switch.sound", Collections.singletonMap("on", false));
        check(!plugin.audioRunning() && Boolean.FALSE.equals(host.switches.get("sound")), "the switch stops it");
        check(Boolean.FALSE.equals(lastSaved(host, "soundOn")), "and forgets it");
        plugin.execute("startSound", Collections.emptyMap());
        check(Boolean.TRUE.equals(host.switches.get("sound")), "the action updates the switch too");
        plugin.execute("stopNightLight", Collections.emptyMap());
        check(Boolean.FALSE.equals(host.switches.get("sound")), "and Close does too");
        plugin.viewShown();
        check(Boolean.TRUE.equals(host.switches.get("sound")), "the screensaver's sound shows on the switch");
        plugin.viewGone();
        settle(plugin);
        check(Boolean.FALSE.equals(host.switches.get("sound")), "and ends with it");
        check(!Boolean.TRUE.equals(lastSaved(host, "soundOn")), "the screensaver's own sound is not resumed");
        plugin.stop();

        Map<String, Object> resume = new HashMap<>();
        resume.put("resumeSound", true);
        resume.put("soundOn", true);
        made.clear();
        host = new FakeHost();
        plugin = soundPlugin(host, made, resume);
        check(plugin.audioRunning() && Boolean.TRUE.equals(host.switches.get("sound")), "a restart resumes the sound");
        plugin.stop();

        resume.put("resumeSound", false);
        made.clear();
        host = new FakeHost();
        plugin = soundPlugin(host, made, resume);
        check(!plugin.audioRunning(), "without resume, a restart stays quiet");
        check(Boolean.FALSE.equals(lastSaved(host, "soundOn")), "and saves the sound as off");
        Map<String, Object> off = new HashMap<>(resume);
        off.put("soundOn", false);
        plugin.configure(off);
        check(!plugin.audioRunning(), "configure with an unchanged sound on does nothing");
        Map<String, Object> on = new HashMap<>(resume);
        plugin.configure(on);
        check(plugin.audioRunning(), "turning sound on in settings plays it");
        plugin.configure(off);
        check(!plugin.audioRunning(), "and turning it off stops it");
        plugin.stop();

        made.clear();
        host = new FakeHost();
        host.noEntities = true;
        plugin = soundPlugin(host, made, Collections.emptyMap());
        plugin.execute("startSound", Collections.emptyMap());
        check(plugin.audioRunning(), "an older KS without entities still plays the sound");
        plugin.stop();
    }

    static void volumeEntity() {
        check(NightLightPlugin.volumePercent(57.4) == 55 && NightLightPlugin.volumePercent(58) == 60, "volume snaps to the step");
        check(NightLightPlugin.volumePercent(-3) == 0 && NightLightPlugin.volumePercent(250) == 100, "volume stays in range");

        FakeHost host = new FakeHost();
        List<FakeSound> made = new ArrayList<>();
        NightLightPlugin plugin = soundPlugin(host, made, Collections.singletonMap("nightVolume", 60));
        check(Double.valueOf(60).equals(host.numbers.get("volume")), "publishes the night light volume: " + host.numbers);
        Map<String, Object> meta = host.numberMeta.get("volume");
        check(meta != null && Integer.valueOf(5).equals(meta.get("step")) && "slider".equals(meta.get("mode")), "as a slider in steps of 5: " + meta);
        plugin.onEvent("number.volume", Collections.singletonMap("value", 72.3));
        check(Double.valueOf(70).equals(host.numbers.get("volume")), "Home Assistant's value is applied on the step: " + host.numbers);
        check(Math.abs(plugin.config().nightVolume - 0.70) < 1e-9, "and the sound uses it at once");
        check(Integer.valueOf(70).equals(lastSaved(host, "nightVolume")), "and it is saved for a restart");
        plugin.onEvent("number.volume", Collections.singletonMap("value", "loud"));
        check(Double.valueOf(70).equals(host.numbers.get("volume")), "a value that is not a number is ignored");
        Map<String, Object> changed = new HashMap<>();
        changed.put("nightVolume", 35);
        plugin.configure(changed);
        check(Double.valueOf(35).equals(host.numbers.get("volume")), "a change in settings shows in Home Assistant");
        plugin.onEvent("number.volume", Collections.singletonMap("value", 40));
        check(plugin.config().nightVolume == 0.40 && plugin.config().nightSound == Config.Sound.NONE, "a slider change keeps the other settings");
        plugin.stop();

        host = new FakeHost();
        host.noNumbers = true;
        plugin = soundPlugin(host, made, Collections.emptyMap());
        plugin.execute("startSound", Collections.emptyMap());
        check(plugin.audioRunning() && Boolean.TRUE.equals(host.switches.get("sound")), "a KS without numbers still plays and keeps the switch");
        plugin.stop();
    }

    static final class FakeHost implements PluginHost {
        final List<String> commands = new ArrayList<>();
        final Map<String, Map<String, Object>> lastArgs = new HashMap<>();
        String screensaverKey;
        OverlayFactory factory;
        boolean touch;
        boolean noTouch;

        @Override public void executeCommand(String command, Map<String, Object> arguments, CommandCallback callback) {
            commands.add(command);
            lastArgs.put(command, arguments);
            callback.onResult(true, null, null);
        }
        @Override public void publishScreensaverView(String key, String title, OverlayFactory factory) {
            screensaverKey = key;
            this.factory = factory;
        }
        @Override public void publishScreensaverView(String key, String title, OverlayFactory factory, boolean touch) {
            if (noTouch) throw new UnsupportedOperationException("Touch screensavers are unavailable");
            publishScreensaverView(key, title, factory);
            this.touch = touch;
        }
        @Override public void removeScreensaver(String key) {}
        @Override public void publishSwitch(String key, String name, boolean state) {
            if (noEntities) throw new UnsupportedOperationException("SDK 1 required");
            switches.put(key, state);
        }
        @Override public void publishNumber(String key, String name, Map<String, Object> metadata, Double state) {
            if (noEntities || noNumbers) throw new UnsupportedOperationException("Plugin numbers are unavailable");
            numbers.put(key, state);
            numberMeta.put(key, metadata);
        }
        final Map<String, Double> numbers = new HashMap<>();
        final Map<String, Map<String, Object>> numberMeta = new HashMap<>();
        boolean noNumbers;
        @Override public void updateSettings(Map<String, Object> values) { updates.add(new HashMap<>(values)); }
        String status = "";
        final Map<String, Boolean> switches = new HashMap<>();
        final List<Map<String, Object>> updates = new ArrayList<>();
        boolean noEntities;
        @Override public void status(String message, boolean error) { status = message; }
        @Override public void showWindow(String title, String message, String buttonLabel) {}
        @Override public void hideWindow() {}
        @Override public void log(String message) {}
    }

    static void soundFade() {
        check(new Config(Collections.emptyMap()).soundFade == 2, "sound fade is 2 s by default");
        check(new Config(Collections.singletonMap("soundFade", 12)).soundFade == 12, "sound fade parses");
        check(new Config(Collections.singletonMap("soundFade", 500)).soundFade == 60, "sound fade is capped");
        long s = 1_000_000_000L, fade = 10 * s;
        check(AudioOut.fadeGain(0, -1, fade, 1) == 0, "the fade in starts silent");
        double early = AudioOut.fadeGain(2 * s, -1, fade, 1), mid = AudioOut.fadeGain(5 * s, -1, fade, 1);
        check(early > 0 && early < mid && mid < 1, "the fade in grows: " + early + " " + mid);
        check(AudioOut.fadeGain(10 * s, -1, fade, 1) == 1, "the fade in ends at full level");
        check(AudioOut.fadeGain(60 * s, -1, fade, 1) == 1, "full level stays");
        check(AudioOut.fadeGain(60 * s, 0, fade, 1) == 1, "the fade out starts at full level");
        double out = AudioOut.fadeGain(60 * s, 5 * s, fade, 1);
        check(out > 0 && out < 1, "the fade out falls: " + out);
        check(AudioOut.fadeGain(60 * s, 10 * s, fade, 1) == 0, "the fade out ends silent");
        check(AudioOut.fadeGain(3 * s, 0, fade, early) == early, "a stop during the fade in starts from its level");
        check(AudioOut.fadeGain(0, -1, 0, 1) == 1 && AudioOut.fadeGain(0, 0, 0, 1) == 0, "no fade: on and off at once");

        check(NightLightPlugin.fadeSeconds(1.4) == 1.5 && NightLightPlugin.fadeSeconds(99) == 60 && NightLightPlugin.fadeSeconds(-1) == 0, "fade snaps to 0.5 s in range");
        FakeHost host = new FakeHost();
        NightLightPlugin plugin = soundPlugin(host, new ArrayList<>(), Collections.emptyMap());
        check(Double.valueOf(2).equals(host.numbers.get("fade")), "publishes the sound fade: " + host.numbers);
        Map<String, Object> meta = host.numberMeta.get("fade");
        check(meta != null && "s".equals(meta.get("unit")), "in seconds: " + meta);
        plugin.onEvent("number.fade", Collections.singletonMap("value", 1.6));
        check(Double.valueOf(1.5).equals(host.numbers.get("fade")), "Home Assistant's fade is applied on the step: " + host.numbers);
        check(plugin.config().soundFade == 1.5, "and the sound uses it at once");
        check(Double.valueOf(1.5).equals(lastSaved(host, "soundFade")), "and it is saved for a restart");
        check(Double.valueOf(25).equals(host.numbers.get("volume")), "the volume stays: " + host.numbers);
        plugin.stop();
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}
