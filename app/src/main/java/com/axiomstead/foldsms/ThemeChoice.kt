package com.axiomstead.foldsms

enum class ThemeChoice(val label: String) {
    DEVICE("Device"), LIGHT("Light"), DARK("Dark");

    fun usesDarkColors(deviceDark: Boolean): Boolean = when (this) {
        DEVICE -> deviceDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStored(value: String?): ThemeChoice = entries.firstOrNull { it.name == value } ?: DEVICE
    }
}
