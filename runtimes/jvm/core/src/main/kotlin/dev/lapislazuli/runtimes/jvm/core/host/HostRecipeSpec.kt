package dev.lapislazuli.runtimes.jvm.core.host

sealed interface HostRecipeIngredient

data class HostMaterialIngredient(
    val type: String,
) : HostRecipeIngredient

data class HostExactItemIngredient(
    val item: HostItemSpec,
) : HostRecipeIngredient

sealed interface HostRecipeSpec {
    val id: String
    val result: HostItemSpec
}

data class HostShapedRecipeSpec(
    override val id: String,
    override val result: HostItemSpec,
    val shape: List<String>,
    val ingredients: Map<Char, HostRecipeIngredient>,
) : HostRecipeSpec

data class HostShapelessRecipeSpec(
    override val id: String,
    override val result: HostItemSpec,
    val ingredients: List<HostRecipeIngredient>,
) : HostRecipeSpec
