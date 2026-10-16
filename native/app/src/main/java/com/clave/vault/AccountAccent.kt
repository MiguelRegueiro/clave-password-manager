package com.clave.vault

/** Curated container/text pairs keep custom accents readable in both app themes. */
enum class AccountAccent(val label: String, val lightContainer: Long, val lightText: Long,
                         val darkContainer: Long, val darkText: Long) {
    BLUE("Azul", 0xFFE4EEFF, 0xFF244B85, 0xFF20344E, 0xFFBDD6FF),
    TEAL("Turquesa", 0xFFDDF3EE, 0xFF155D50, 0xFF193D36, 0xFFA5E3D4),
    VIOLET("Violeta", 0xFFEEE6FF, 0xFF59358C, 0xFF38294D, 0xFFD9C0FF),
    ROSE("Rosa", 0xFFFBE4EF, 0xFF853653, 0xFF4A2938, 0xFFF5BCD4),
    AMBER("Ámbar", 0xFFFFEECB, 0xFF755014, 0xFF44371F, 0xFFF1D18F),
    CORAL("Coral", 0xFFFFE5DE, 0xFF883E2D, 0xFF4B3028, 0xFFF9C2AF),
    INDIGO("Índigo", 0xFFE7E9FF, 0xFF3D438B, 0xFF2B2F4F, 0xFFC4CAFF),
    SLATE("Gris", 0xFFE8E9EB, 0xFF44484D, 0xFF303438, 0xFFE0E2E4);

    companion object {
        fun fromStored(value: String?) = entries.firstOrNull { it.name == value }
        // Color is a user choice, not inferred from a profile's name.
        fun forProfile(@Suppress("UNUSED_PARAMETER") name: String) = SLATE
    }
}
