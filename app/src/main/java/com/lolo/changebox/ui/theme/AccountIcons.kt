package com.lolo.changebox.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Briefcase
import com.composables.icons.lucide.Car
import com.composables.icons.lucide.CircleDollarSign
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Gem
import com.composables.icons.lucide.Gift
import com.composables.icons.lucide.HandCoins
import com.composables.icons.lucide.Heart
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.ShoppingBag
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Stethoscope
import com.composables.icons.lucide.Store
import com.composables.icons.lucide.Utensils
import com.composables.icons.lucide.Wallet
import com.composables.icons.lucide.Wrench
import com.lolo.changebox.domain.AccountType

// Port 1:1 de fantastic-eureka/src/lib/account-icons.ts. El nombre kebab se
// guarda en Account.icon y viaja INTACTO al servidor (la web usa los mismos
// nombres con lucide-react); null = automático según el tipo de cuenta.

data class AccountIconDef(val name: String, val icon: ImageVector)

val ACCOUNT_ICONS: List<AccountIconDef> = listOf(
    AccountIconDef("wallet", Lucide.Wallet),
    AccountIconDef("banknote", Lucide.Banknote),
    AccountIconDef("landmark", Lucide.Landmark),
    AccountIconDef("credit-card", Lucide.CreditCard),
    AccountIconDef("piggy-bank", Lucide.PiggyBank),
    AccountIconDef("coins", Lucide.Coins),
    AccountIconDef("hand-coins", Lucide.HandCoins),
    AccountIconDef("circle-dollar-sign", Lucide.CircleDollarSign),
    AccountIconDef("smartphone", Lucide.Smartphone),
    AccountIconDef("store", Lucide.Store),
    AccountIconDef("shopping-bag", Lucide.ShoppingBag),
    AccountIconDef("briefcase", Lucide.Briefcase),
    AccountIconDef("house", Lucide.House),
    AccountIconDef("car", Lucide.Car),
    AccountIconDef("plane", Lucide.Plane),
    AccountIconDef("gift", Lucide.Gift),
    AccountIconDef("heart", Lucide.Heart),
    AccountIconDef("star", Lucide.Star),
    AccountIconDef("shield", Lucide.Shield),
    AccountIconDef("gem", Lucide.Gem),
    AccountIconDef("book-open", Lucide.BookOpen),
    AccountIconDef("utensils", Lucide.Utensils),
    AccountIconDef("stethoscope", Lucide.Stethoscope),
    AccountIconDef("wrench", Lucide.Wrench),
)

private val BY_NAME = ACCOUNT_ICONS.associate { it.name to it.icon }

val ACCOUNT_ICON_NAMES: List<String> = ACCOUNT_ICONS.map { it.name }

private val TYPE_FALLBACK: Map<AccountType, ImageVector> = mapOf(
    AccountType.CASH to Lucide.Banknote,
    AccountType.CASH_BOX to Lucide.Coins,
    AccountType.BANK to Lucide.Landmark,
    AccountType.DIGITAL to Lucide.CreditCard,
)

fun getAccountIcon(icon: String?, type: AccountType): ImageVector =
    icon?.let { BY_NAME[it] } ?: TYPE_FALLBACK[type] ?: Lucide.Wallet

