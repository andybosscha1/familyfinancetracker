package com.timmat.financetracker.common

import com.timmat.financetracker.data.model.AppCurrency
import java.text.NumberFormat
import java.util.Locale

/** Shared helper: return a NumberFormat configured for the given app currency. */
fun currencyFormatter(currency: AppCurrency, locale: Locale = Locale.getDefault()): NumberFormat =
    NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = currency.toJavaCurrency()
    }
