# Night Light

A bedtime night light for a child's room, built as a [Kiosk Satellite](https://github.com/jxlarrea/kiosk-satellite) screensaver.

Night Light has two modes:

- **Night light**: a dim, warm light that stays on all night, with optional quiet sound. The screensaver always opens on it.
- **Soother**: gentle sleepy music or sounds while the screen drifts slowly through soft colours. Press the **Soother** button in the corner to start it. Press **End soother**, or let it finish (30 minutes by default), and it fades back to the night light.

| Soother | Night light with the bedtime countdown |
| --- | --- |
| ![The soother in the Liquid style: soft colours drifting, then End soother fades back to the night light](docs/soother.gif) | ![The night light's stars going out one by one between bed time and up time](docs/night-light-stars.gif) |
| The Liquid style, sped up 20 times, then End soother | A night from 7 pm to 7 am, sped up |

What it offers:

- **Sound made on the device.** A music box lullaby, dreamy pads or ocean waves, composed as it plays so it never repeats exactly. Or Brahms' Lullaby and Mozart's Twinkle, Twinkle on the music box. It slows down a little as the soother goes on. There are no audio files and nothing to download.
- **Soft colours.** The soother is a slow glow drifting through Rainbow, Ocean, Sunset, Aurora or Candy colours. Or pick the **Liquid** style: a few soft blobs of colour that slowly drift and blend into each other, then gather into the night light at the end. It dims gradually, so the night light is not a jump.
- **A warm night light.** Amber by default, with adjustable colour and brightness. Choose its style: **Flat**, one even colour; **Glow**, brighter in the middle; or **Liquid**, a few soft blobs that drift and blend very slowly. Its **colours** are **Solid**, the one night light colour, or any of the soother's colour sets, Rainbow, Ocean, Sunset, Aurora or Candy, to drift through very slowly, just as dim. The night light's set is chosen separately from the soother's.
- **Sounds for each mode.** The soother and the night light choose from the same sounds: a music box lullaby, Brahms & Mozart lullabies, dreamy pads, ocean waves, rain, a thunderstorm, white, pink or brown noise, or no sound. Each has its own choice and volume, so the soother can play a lullaby and the night light brown noise all night. The thunderstorm is rain with distant thunder rolling in every minute or two, never a sudden crack. White noise is a steady hiss, pink noise is softer and brown noise is a deep rumble.
- **Bedtime countdown.** Optionally shows a row of stars on the night light, about one per hour between bed time and up time (at most 10). They go out one by one through the night, so a child can see how long until it is time to get up.
- **Wakes gently.** If the screen is tapped mid-soother and the screensaver comes back within an hour, the soother carries on where it was.

## Using it

Either way works:

1. **As the screensaver.** Choose **Night Light (Night Light)** under **Screensaver > Screensaver mode**, or in a screensaver schedule entry, for example every evening from 7pm to 7am.
2. **On demand.** Run the **Show night light** or **Start soother** action from a gesture, the kiosk drawer or a Home Assistant button. It shows the screensaver right away and gives your usual one back once it is dismissed in the morning.

| Action | What it does |
| --- | --- |
| **Show night light** | Shows the night light, fading out the soother if it is playing |
| **Start soother** | Starts the soother, showing the screensaver first if needed |
| **End soother** | Fades the soother back to the night light over about 20 seconds |
| **Start or end soother** | Starts the soother, or ends it if it is playing. Handy for a single clap gesture |
| **Play night light sound** | Plays the night light sound without the screensaver, for a sound machine with the screen off |
| **Stop night light sound** | Stops the sound, whatever started it |
| **Close Night Light** | Dismisses the screensaver, restores your usual one and stops the sound |

While the screensaver shows, a dim button in the bottom right corner switches between **Soother** and **End soother**. A single tap anywhere else does nothing, so little hands cannot close it by accident. **Double tap** to close the screensaver. A hint saying so shows for a few seconds after each touch.

The button needs a Kiosk Satellite release that passes taps to plugin screensavers. On older releases there is no button and any tap closes the screensaver, so use a gesture such as a clap with the **Start or end soother** action instead.

Each action can also be exposed to Home Assistant from the plugin page. You can then start the soother from a bedtime automation.

The plugin also adds a **Night light sound** switch to Home Assistant. It shows whether the sound is playing, from the screensaver or on its own, and turns the sound on or off. It needs a Kiosk Satellite release with plugin entities.

A **Night light volume** slider (0 to 100%, steps of 5) sets the **Night light volume** setting from Home Assistant, so an automation can turn the sound down without the device volume. It needs Kiosk Satellite 2026.11.14 or later; older releases just leave it out.

### Recommended Kiosk Satellite settings

Kiosk Satellite still controls when the screensaver starts and stops, so check these screensaver settings:

- **Turn off any screen-off timer** for the screensaver, or the night light goes dark when it fires.
- **Screensaver brightness**: set a lowish panel brightness. The night light's own brightness setting dims it further.
- **Motion wake**: turn it off, or someone moving in the room closes the screensaver.
- **Sound with the screen off**: the sound normally stops when the screensaver closes. Turn on **Keep sound when closed**, or use the **Play night light sound** action, to keep it playing.
- **Sound fade**: the sound fades in when it starts and out when it stops, over **Sound fade** seconds (0 for about a second). Home Assistant gets it as a number beside the volume, so an automation can wait for the fade to end.
- **Media volume**: sound plays on the device's media volume. The plugin's **Soother volume** and **Night light volume** settings sit on top of that.

### Night mode

Like the Clock screensaver's Night mode, this uses the device's light sensor. When the room is at or below the **Light level** (5 lx by default), the Soother button, the hint, the stars and Kiosk Satellite's corner widgets and At a Glance row turn the **Night color**, a muted red by default. With **Hide button and stars** on, the button, hint and stars disappear instead, leaving only the night light and the tinted widgets. Night mode ends a quarter above the light level, so the colours never flicker. The night light itself keeps its own colour and brightness.

Tinting the widgets needs a Kiosk Satellite release with screensaver tints. On older releases the widgets keep their usual colours.

## Settings

| Group | Setting | Default |
| --- | --- | --- |
| Soother | Soother length | 30 min |
| Soother | Soother sound | Music box lullaby |
| Soother | Soother volume | 40% |
| Soother | Soother colours | Rainbow |
| Soother | Colour style | Glow |
| Soother | Colour speed | Slow |
| Soother | Colour brightness | 70% |
| Soother | Fade to night light | 3 min |
| Night light | Night light style | Glow |
| Night light | Night light colours | Solid |
| Night light | Night light colour (Solid only) | #FF9440 (warm amber) |
| Night light | Night light brightness | 25% |
| Night light | Night light sound | No sound |
| Night light | Night light volume | 25% |
| Night light | Keep sound when closed | Off |
| Night light | Sound on | Off |
| Night light | Resume sound after restart | Off |
| Night light | Sound fade | 2 s |
| Countdown | Bedtime countdown | Off |
| Countdown | Bed time | 19:00 |
| Countdown | Up time | 07:00 |
| Night mode | Night mode | Off |
| Night mode | Light level | 5 lx |
| Night mode | Night color | #A63A32 (muted red) |
| Night mode | Hide button and stars | Off |

Settings save automatically and apply straight away, even while the night light is showing.

## Installation

### Community Plugins (Recommended)

Night Light is listed in [Community Plugins](https://kiosksatellite.com/plugins/). Use the button below to open it in your Kiosk Satellite and install it.

[![Install in Kiosk Satellite](https://kiosksatellite.com/img/badges/plugin-add.svg)](https://kiosksatellite.com/redirect/plugin/?repository=ostat/kiosk-satellite-night-light-plugin)

Or search for `Night Light` in **Settings > Plugin Manager > Browse plugins** in Kiosk Satellite.

### Manual

1. Open **Settings > Plugin Manager** in Kiosk Satellite and turn on **Enable Plugins**.
2. Choose **Add plugin**, paste `https://github.com/ostat/kiosk-satellite-night-light-plugin` and choose **Preview**.
3. Review the plugin and choose **Trust and install**.

## Permissions

The plugin declares `screensaver` to draw the night light, plus `screensaver.mode` and `host.control` so the actions can switch to it and start or dismiss it, and `host.read` to read the room's light level for **Night mode**. It reads nothing else from the kiosk and makes no network requests.

## Building

Requires Python 3, JDK 17 or newer and an Android SDK with a platform and build-tools.

```sh
python3 tools/test.py
python3 tools/build.py
```

The ZIP lands in `dist/`. Install it for testing with **Plugin Manager > Developer Tools > Install from ZIP**.

To publish, push to `main`. The included workflow tests and builds the plugin, then releases it under the date: `v2026.1104.1` for the first release on 4 November 2026, `v2026.1104.2` for the second that day. Pushes that only change Markdown files, `docs/` or the licence do not release. You can still create a release by hand, and the workflow builds and attaches the plugin to it. Anyone can add the plugin with **Plugin Manager > Add plugin** and the repository URL.

## License

Apache-2.0. The plugin SDK in `sdk/` and the build tools in `tools/` come from the [Kiosk Satellite plugin template](https://github.com/jxlarrea/kiosk-satellite-plugins), also Apache-2.0.
