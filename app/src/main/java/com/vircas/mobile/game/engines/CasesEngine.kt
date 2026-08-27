package com.vircas.mobile.game.engines

import com.vircas.mobile.core.random.RandomProvider

enum class ItemRarity(val weight: Int) {
    COMMON(550), UNCOMMON(250), RARE(120), EPIC(50), LEGENDARY(25), MYTHIC(5)
}

data class CaseItemTemplate(
    val id: String,
    val name: String,
    val weaponCategory: String,
    val rarity: ItemRarity,
    val marketValue: Long,
    val previewKey: String
)

data class CaseDefinition(
    val id: String,
    val title: String,
    val cost: Long,
    val items: List<CaseItemTemplate>
)

data class CaseOpeningResult(
    val caseId: String,
    val item: CaseItemTemplate,
    val reel: List<CaseItemTemplate>,
    val winningIndex: Int
)

class CasesEngine(private val random: RandomProvider) {
    fun open(case: CaseDefinition, reelSize: Int = 36, winningIndex: Int = 30): CaseOpeningResult {
        require(case.cost > 0 && case.items.isNotEmpty())
        require(reelSize >= 10 && winningIndex in 0 until reelSize)
        val won = pick(case.items)
        val reel = MutableList(reelSize) { pick(case.items) }
        reel[winningIndex] = won
        return CaseOpeningResult(case.id, won, reel, winningIndex)
    }

    private fun pick(items: List<CaseItemTemplate>): CaseItemTemplate {
        val availableRarities = ItemRarity.entries.filter { rarity -> items.any { it.rarity == rarity } }
        val total = availableRarities.sumOf { it.weight }
        var ticket = random.nextInt(0, total)
        val rarity = availableRarities.firstOrNull { rarity ->
            if (ticket < rarity.weight) true else {
                ticket -= rarity.weight
                false
            }
        } ?: availableRarities.last()
        val pool = items.filter { it.rarity == rarity }
        return pool[random.nextInt(0, pool.size)]
    }

    companion object DemoCases {
        private val items = listOf(
            CaseItemTemplate("pulse_rifle_ember", "Emberline", "Rifle", ItemRarity.COMMON, 180, "ember"),
            CaseItemTemplate("vector_frost", "Frost Byte", "SMG", ItemRarity.COMMON, 210, "frost"),
            CaseItemTemplate("pistol_orbit", "Orbit Trace", "Pistol", ItemRarity.UNCOMMON, 420, "orbit"),
            CaseItemTemplate("shotgun_aurora", "Aurora Breach", "Shotgun", ItemRarity.UNCOMMON, 520, "aurora"),
            CaseItemTemplate("rifle_neon", "Neon Shard", "Rifle", ItemRarity.RARE, 1_100, "neon"),
            CaseItemTemplate("sniper_void", "Void Signal", "Sniper", ItemRarity.RARE, 1_450, "void"),
            CaseItemTemplate("smg_reactor", "Reactor Bloom", "SMG", ItemRarity.EPIC, 3_200, "reactor"),
            CaseItemTemplate("rifle_eclipse", "Eclipse Crown", "Rifle", ItemRarity.LEGENDARY, 8_500, "eclipse"),
            CaseItemTemplate("knife_prism", "Prism Edge", "Melee", ItemRarity.MYTHIC, 25_000, "prism")
        )

        val Starter = CaseDefinition("starter", "Signal Cache", 500, items)
        val Reactor = CaseDefinition("reactor", "Reactor Cache", 1_500, items.map { if (it.rarity >= ItemRarity.RARE) it.copy(marketValue = it.marketValue * 2) else it })
        val Apex = CaseDefinition("apex", "Apex Cache", 5_000, items.map { it.copy(marketValue = it.marketValue * 3) })
        val All = listOf(Starter, Reactor, Apex)
    }
}
