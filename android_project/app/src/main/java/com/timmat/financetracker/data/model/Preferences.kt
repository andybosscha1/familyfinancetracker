package com.timmat.financetracker.data.model

import java.util.Currency

/** Supported UI languages. English is default/fallback. */
enum class AppLanguage(val tag: String, val displayNameRes: Int) {
    English("en", com.timmat.financetracker.R.string.lang_english),
    Dutch("nl", com.timmat.financetracker.R.string.lang_dutch);

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            values().firstOrNull { it.tag == tag } ?: English
    }
}

/**
 * Supported display currencies. Stored as ISO 4217 code and used to configure
 * `NumberFormat.getCurrencyInstance()` for consistent rendering.
 */
enum class AppCurrency(val code: String, val displayNameRes: Int) {
    EUR("EUR", com.timmat.financetracker.R.string.currency_eur),
    USD("USD", com.timmat.financetracker.R.string.currency_usd),
    GBP("GBP", com.timmat.financetracker.R.string.currency_gbp),
    JPY("JPY", com.timmat.financetracker.R.string.currency_jpy);

    fun toJavaCurrency(): Currency = Currency.getInstance(code)

    companion object {
        fun fromCode(code: String?): AppCurrency =
            values().firstOrNull { it.code == code } ?: EUR
    }
}

/** UI colour scheme choice. Medium = warm sepia between pure-light and pure-dark. */
enum class AppTheme(val displayNameRes: Int) {
    Light(com.timmat.financetracker.R.string.theme_light),
    Sepia(com.timmat.financetracker.R.string.theme_sepia),
    Dark(com.timmat.financetracker.R.string.theme_dark);

    companion object {
        fun fromName(name: String?): AppTheme =
            values().firstOrNull { it.name == name } ?: Light
    }
}

/** App-lock mode on top of Google sign-in. */
enum class AppLockMode(val displayNameRes: Int) {
    None(com.timmat.financetracker.R.string.app_lock_none),
    Pin(com.timmat.financetracker.R.string.app_lock_pin),
    PinAndBiometric(com.timmat.financetracker.R.string.app_lock_pin_biometric);

    companion object {
        fun fromName(name: String?): AppLockMode =
            values().firstOrNull { it.name == name } ?: None
    }
}
