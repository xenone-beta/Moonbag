package dev.moonbag.app.game

/** 256 plain words for the game's recovery phrase. Twelve of them make 96 bits. It is not BIP-39. */
object WordList {

    val words: List<String> = """
acorn actor agile album alien alpha amber anchor
angel apple arch arena arrow aster atlas atom
aurora autumn badge bagel baker balm bamboo banjo
barn basil beach beacon berry bison blaze bloom
bolt bonus brave breeze bridge brook bubble cabin
cactus cadet camel candle canoe canyon cape carbon
cargo castle cedar chalk charm cherry chess cider
cinema circle citrus clever cliff clock cloud clover
cobalt cocoa comet coral cosmic cotton crane crater
crown crystal cube curve daisy dance dawn delta
desert diamond dolphin dragon dream drift dune eagle
echo eclipse ember engine epoch falcon fancy fern
ferry field flame flint flute forest fossil fox
frost galaxy garden gecko ginger glacier globe glow
golden granite grape gravity grove guitar harbor hazel
heron honey horizon hunter icon igloo indigo island
ivory jade jaguar jazz jelly jewel jungle karma
kayak kettle kiwi koala lagoon lantern laser lava
lemon lilac lime linen lotus lunar magnet mango
maple marble meadow melody meteor mint mirror mocha
monsoon moon mosaic mountain nebula nectar neon nickel
noble nomad north nova oasis ocean olive onyx
opal orbit orchid otter oxide paddle palm panda
paper pearl pebble pepper phoenix piano pilot pixel
planet plum polar pond poppy prairie prism puzzle
quartz quest quill rabbit radar rain raven reef
ridge river robin rocket rose ruby saddle safari
sage sailor sand sapphire scarlet shadow shell signal
silver sketch sky solar spark spice spiral spruce
star stone storm summer sunset swan tango teal
temple thunder tiger timber topaz tower trail tulip
tundra turtle umbra unity valley velvet violet vortex
""".trim().split(Regex("\\s+"))

    val set: Set<String> = words.toSet()

    init {
        require(words.size == 256 && set.size == 256) { "word list must hold 256 different words" }
    }
}
