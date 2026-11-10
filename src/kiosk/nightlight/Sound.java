// SPDX-License-Identifier: Apache-2.0
package kiosk.nightlight;

/** What plays the night light's sound. A seam so tests run without an AudioTrack. */
interface Sound {
    /** Fades out, then releases the speaker. */
    void stop();

    /** Stops right away, for plugin shutdown. */
    void stopNow();

    boolean alive();
}
