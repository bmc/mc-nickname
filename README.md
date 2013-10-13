A simple Minecraft Bukkit server plugin that allows users to change their
display names. Written primarily to play around with Josh Cough's
[Scala Bukkit plugin API](https://github.com/joshcough/MinecraftPlugins).

# Getting the Plugin

You can either download a precompiled version of the plugin or compile it
yourself.

## Downloading the Plugin

You can download the compiled plugin from
<http://download.clapper.org/mc-nickname/>.

## Building the Plugin

To build the plugin, you'll need a Java JDK (preferably Java 7).

* Check out the repo.
* Run: `gradlew zip`
* The resulting distribution is in `build/distributions/mc-nickname-VERSION.zip`,
  where `VERSION` is the current version of the plugin (e.g., "0.1").

# Installing the Plugin

Unpack the zip file you downloaded or built above. Unzipping will result in
an `mc-nickname` subdirectory containing numerous jar files. Assuming the
top-level directory of your Minecraft world is `$WORLD`, issue the following
commands. (These commands are suitable for Unix and Mac systems. If you're
on Windows, either use the appropriate Windows commands or run the following
commands from a [Cygwin](http://www.cygwin.com/) shell.)

    $ cd mc-nickname
    $ mkdir -p $WORLD/lib
    $ cp scala-library-2.10.jar $WORLD/lib
    $ cp mclib*.jar $WORLD/lib/mclib.jar
    $ cp scala-minecraft-plugin-api*.jar mc-nickname.jar $WORLD/plugins

Then, adjust any permissions (see below) and reload or restart your server.

## Permissions

It's not necessary to set permissions, because the plugin assumes
reasonable defaults. However, if you're using a permissions plugin such
as [Essentials Group Manager](http://wiki.ess3.net/wiki/Group_Manager),
you can control which users are permitted to change their nicknames.

The plugin supports the following permission:

- `nickname.canchange`: Set to `true` or `false`, to enable or disable
  the ability to change the nickname. Default, if not set: `true`

## Commands

Supports the following in-game chat commands:

`nickname name` sets a new nickname.
`nickname` (without arguments) shows your nickname.
`nickname -` disables your nickname.
