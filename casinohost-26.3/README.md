# Casino Host HUD - Fabric, Minecraft 26.3

Needs: Java 25, Gradle 9.6.0, Fabric Loader 0.19.5+, Fabric API 0.161.0+26.3.

Build locally:  gradle build      -> build/libs/casinohost-1.0.0.jar
Build with no setup: push this folder to a GitHub repo; the included workflow
(.github/workflows/build.yml) builds it and attaches the jar under Actions > Artifacts.

Use: press R in-game for the host menu.
EDIT the PAID regex in CasinoHostClient.java to match DonutSMP's real payment message.

Not compile-tested. If a build error mentions a renamed class/method, paste it to Claude.
