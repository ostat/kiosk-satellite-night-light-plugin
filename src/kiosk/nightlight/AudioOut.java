// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Streams the lullaby to the media volume on its own thread while the night light shows. It reads the settings and
 * the session itself every 50 ms, so the music follows the timeline without any timers.
 */
final class AudioOut implements Runnable, Sound {
    interface Source {
        Config config();
        long elapsed();
        double fade();
        void log(String message);
    }

    private static final int RATE = 22050;
    private static final long STOP_FADE_NS = 1_500_000_000L;

    private final Source source;
    private volatile boolean running = true;
    private volatile long stopRequested;
    private final long startedAt = System.nanoTime();
    /** The fade gain heard when stop() came, so a stop during the fade in fades out from there. */
    private volatile double gainAtStop = 1;
    private final Thread thread;

    AudioOut(Source source) {
        this.source = source;
        thread = new Thread(this, "night-light-audio");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
    }

    void start() {
        thread.start();
    }

    /** Fades out over the Sound fade setting (about a second when it is 0), then releases the speaker. */
    @Override public void stop() {
        if (!running) return;
        long now = System.nanoTime();
        gainAtStop = fadeGain(now - startedAt, -1, fadeNs(source.config()), 1);
        stopRequested = now;
        running = false;
    }

    /** Stops right away, for plugin shutdown. */
    @Override public void stopNow() {
        stop();
        thread.interrupt();
    }

    @Override public boolean alive() {
        return running && thread.isAlive();
    }

    @Override public void run() {
        AudioTrack track = null;
        try {
            int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bytes = Math.max(min, RATE / 4 * 2);
            track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(new AudioFormat.Builder()
                    .setSampleRate(RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();
            loop(track);
        } catch (InterruptedException ignored) {
            // Plugin shutdown.
        } catch (Throwable error) {
            source.log("Night light audio stopped: " + error);
        } finally {
            if (track != null) {
                try { track.pause(); track.flush(); } catch (Throwable ignored) { }
                track.release();
            }
        }
    }

    /** Sets a synth up to play one sound at a level. */
    private static void play(LullabySynth synth, Config.Sound sound, double level, double tempo) {
        synth.setDreamy(sound == Config.Sound.DREAMY);
        synth.setSongs(sound == Config.Sound.CLASSIC);
        if (sound == Config.Sound.WHITE) synth.setNoise(LullabySynth.Noise.WHITE);
        else if (sound == Config.Sound.PINK) synth.setNoise(LullabySynth.Noise.PINK);
        else if (sound == Config.Sound.BROWN) synth.setNoise(LullabySynth.Noise.BROWN);
        else if (sound == Config.Sound.RAIN) synth.setNoise(LullabySynth.Noise.RAIN);
        else if (sound == Config.Sound.STORM) synth.setNoise(LullabySynth.Noise.STORM);
        synth.setLevels(level, sound.music() ? 1 : 0, sound == Config.Sound.WAVES ? 1 : 0, sound.noise() ? 1 : 0, tempo);
    }

    static long fadeNs(Config c) {
        return Math.round(c.soundFade * 1e9);
    }

    /**
     * The gain of the sound fade, from 0 to 1. sinceStart is how long the sound has played; sinceStop is how long
     * ago stop() came, or negative while it plays. The curve is eased and squared, so the level grows and falls
     * evenly to the ear. A fade of 0 gives 1 while playing and 0 once stopped.
     */
    static double fadeGain(long sinceStart, long sinceStop, long fade, double atStop) {
        if (fade <= 0) return sinceStop < 0 ? 1 : 0;
        if (sinceStop < 0) return ease((double) sinceStart / fade);
        return atStop * ease(1 - (double) sinceStop / fade);
    }

    private static double ease(double t) {
        t = Math.max(0, Math.min(1, t));
        double s = t * t * (3 - 2 * t);
        return s * s;
    }

    /** True while a synth is heard or on its way in or out. */
    private static boolean audible(LullabySynth synth, double level) {
        return level > 1e-4 || synth.currentMaster() > 1e-4;
    }

    private void loop(AudioTrack track) throws InterruptedException {
        // The soother and the night light each have a synth, so their sounds can differ and still crossfade.
        long seed = System.nanoTime();
        LullabySynth soother = new LullabySynth(RATE, seed);
        LullabySynth night = new LullabySynth(RATE, seed + 1);
        Scene.Mix mix = new Scene.Mix();
        short[] chunk = new short[RATE / 20];
        short[] other = new short[chunk.length];
        boolean playing = false;
        while (!Thread.currentThread().isInterrupted()) {
            Config c = source.config();
            Scene.mix(c, source.elapsed(), source.fade(), mix);
            boolean stopping = !running;
            long now = System.nanoTime();
            long fade = fadeNs(c);
            double gain = fadeGain(now - startedAt, stopping ? now - stopRequested : -1, fade, gainAtStop);
            double sootherLevel = mix.soother * gain;
            double nightLevel = mix.night * gain;
            play(soother, c.sound, sootherLevel, mix.tempo);
            play(night, c.nightSound, nightLevel, Scene.NIGHT_TEMPO);
            boolean sootherOn = audible(soother, sootherLevel);
            boolean nightOn = audible(night, nightLevel);

            if (stopping && ((gain <= 0 && !sootherOn && !nightOn) || now - stopRequested > fade + STOP_FADE_NS)) return;
            if (!sootherOn && !nightOn) {
                // Nothing to hear, for example a silent night light: rest the speaker and check again shortly.
                if (playing) { track.pause(); track.flush(); playing = false; }
                Thread.sleep(1000);
                continue;
            }
            if (!playing) { track.play(); playing = true; }
            if (sootherOn) soother.render(chunk, 0, chunk.length);
            else java.util.Arrays.fill(chunk, (short) 0);
            if (nightOn) {
                night.render(other, 0, other.length);
                for (int i = 0; i < chunk.length; i++) {
                    chunk[i] = (short) Math.max(-32767, Math.min(32767, chunk[i] + other[i]));
                }
            }
            int offset = 0;
            while (offset < chunk.length) {
                int wrote = track.write(chunk, offset, chunk.length - offset);
                if (wrote < 0) throw new IllegalStateException("AudioTrack write failed: " + wrote);
                offset += wrote;
            }
        }
    }
}
