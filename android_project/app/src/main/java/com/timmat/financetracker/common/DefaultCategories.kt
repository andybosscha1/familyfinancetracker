package com.timmat.financetracker.common

/**
 * Default categories seeded for every newly created family.
 * Admins can add more later; these should cover 80% of households.
 */
object DefaultCategories {
    val NAMES: List<String> = listOf(
        "Salary",
        "Groceries",
        "Rent/Mortgage",
        "Utilities",
        "Transport",
        "Dining",
        "Entertainment",
        "Healthcare",
    )
}
