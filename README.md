Ever been tired of your server lagging an awful lot when you open a chest that may contain an explorer map?
Or when you level up a cartographer villager?

Even more with a lot of mods, so much that you can get up to multiple explorer maps in a single chest,
or as villager trades, which would cause massive lag upon generation?

You don't want your server to crash anymore because of that?


Then look no further! I have a simple yet effective mod right here for you.

# Maps of the Unknown

**Maps of the Unknown** *(or BetterMaps)* only tweaks it so that the structure location logic is processed at a later
point, upon request of the player, by replacing the map item by what would _look like_ an empty map,
but would become the regular explorer map upon use.

This mod doesn't come with any config file, for it doesn't need it, but adds two gamerules:
* `doBetterMaps`, which acts as a pure toggle for the mod (`true` by default)
* `doBetterMapsLoot`, which serves as a toggle only for the chest loot map generation (`true` by default)
* `doBetterMapsTrades`, which serves as a toggle only for the villager trade map generation (`true` by default)
* `doBetterMapFromPlayerPos`, which sets the mod to start locating the structure from the player (`false` by default). <br>
  Beware, despite it being on `false` by default, keeping it as such can alter the location algorithm's behavior,
  thus locating a different instance of the wanted structure than expected, although still trying to keep it as
  close as possible from the location the map was generated at (default behavior).
* `doBetterMapsDynamicLocating`, which toggles whether the map locating tasks should be run in a dynamic way
  (when `true`, default value) or vanilla. Enabling it makes it so the world isn't frozen until the task is finished, 
  and solves some crash issues with maps creation. <br>
  In order to prevent map duping with starting multiple tasks before the first one is finished, it is required for
  the player to keep the map in hand until they get the result map, so that the mod can effectively consume the
  initial map (except in creative of course)

What's even better with this mod you may ask, is that it is *entirely server-sided*! There is no need for the client to install it, or you can just install it on a server with another modpack, for the pure sake of improving the map creation logic!

---

**Q&A**

My world still crashes when I use a map, why?
* While improving the loot-table processing, this mod doesn't change the structure location logic.
  It only prevents the game from trying to locate multiple structures during a single tick,
  which can easily end up crashing even the most powerful servers.

Why do you use the vanilla empty map?
* To make the mod able to work purely server-side.

Why doesn't it do anything when I use the map?
* I added one more thing to the map creation, that is dimension specification.
  You may see what dimension is a map for by hovering it in your inventory, as a tooltip.
  If you are not in the matching dimension when using the map, look forward to seeing a red "x"
  temporarily appear over your hotbar, and your action not doing anything.
* Additionally, a blue "x" means that the structure couldn't be found for some other reason.
  Thus meaning that there either is no such structure in that dimension, or none in search range.

Why didn't you just name it "BetterMaps"?
* Because it sounds cool.

What happens if I remove the mod?
* This mod works hand in hand with vanilla Minecraft, but only change the latter's behavior when loaded.
  Thus, if you remove the mod, using a special "empty map" created by the mod would only make the game behave
  as normal with a regular empty map.

Is this mod incompatible with _<insert name here>_?
* The only real response I can give you is [maybe](https://tryitands.ee).
* [Async Locator](https://modrinth.com/mod/async-locator): Yes and no. This mod cannot work when enabled with
  Async Locator if its exploration map and villager trade features are enabled, or you may need to toggle at least
  the corresponding feature of this here mod. And considering my plans for the future of this mod,
  they will most probably not be compatible at all on the long term. But until further notice, you may use both
  while validating the aforementioned conditions.