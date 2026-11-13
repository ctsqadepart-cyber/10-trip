package tw.shilu.domain

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Currency

/** Amounts use minor-unit integers; no Double participates in accounting. */
object Money {
    fun scale(currency: String): Int = Currency.getInstance(currency).defaultFractionDigits.also {
        require(it in 0..3) { "不支援此幣別的小數位數" }
    }
    fun parse(text: String, currency: String): Long {
        require(text.matches(Regex("\\d+(\\.\\d+)?"))) { "請填寫有效的非負金額" }
        return BigDecimal(text).setScale(scale(currency), RoundingMode.UNNECESSARY)
            .movePointRight(scale(currency)).longValueExact()
    }
    fun format(minor: Long, currency: String): String = BigDecimal.valueOf(minor, scale(currency)).toPlainString()
    fun rate(text: String): BigDecimal = runCatching { BigDecimal(text) }.getOrElse { error("請填寫有效匯率") }.also {
        require(it.signum() > 0 && it.scale() in 0..12 && it.precision() <= 18) { "匯率需大於零，最多 12 位小數" }
    }
    fun convert(originalMinor: Long, original: String, settlement: String, rate: String): Long {
        require(originalMinor >= 0)
        val r = rate(rate)
        require(original != settlement || r.compareTo(BigDecimal.ONE) == 0) { "同幣別匯率必須為 1" }
        return BigDecimal.valueOf(originalMinor, scale(original)).multiply(r)
            .setScale(scale(settlement), RoundingMode.HALF_UP).movePointRight(scale(settlement)).longValueExact()
    }
    fun equal(total: Long, memberIds: List<String>): Map<String, Long> {
        require(total >= 0 && memberIds.isNotEmpty() && memberIds.distinct().size == memberIds.size)
        val stable = memberIds.sorted() // UUID, independent of display order or names.
        return stable.mapIndexed { index, id -> id to total / stable.size + if (index < total % stable.size) 1L else 0L }.toMap()
    }
    fun custom(total: Long, shares: Map<String, Long>): Map<String, Long> {
        require(total >= 0 && shares.isNotEmpty() && shares.values.all { it >= 0 }) { "請選擇分攤成員並填寫非負金額" }
        require(shares.values.fold(BigInteger.ZERO) { a, b -> a + b.toBigInteger() } == total.toBigInteger()) { "每人金額合計必須等於支出金額" }
        return shares
    }
    data class Balance(val paid: Long, val burden: Long) { val net: Long get() = Math.subtractExact(paid, burden) }
    data class LedgerExpense(val total: Long, val payer: String, val shares: Map<String, Long>)
    fun balances(expenses: List<LedgerExpense>): Map<String, Balance> {
        val result = linkedMapOf<String, Balance>()
        expenses.forEach { e ->
            custom(e.total, e.shares)
            val old = result[e.payer] ?: Balance(0, 0)
            result[e.payer] = old.copy(paid = Math.addExact(old.paid, e.total))
            e.shares.forEach { (id, share) -> val b = result[id] ?: Balance(0, 0); result[id] = b.copy(burden = Math.addExact(b.burden, share)) }
        }
        check(result.values.fold(BigInteger.ZERO) { a, b -> a + b.net.toBigInteger() } == BigInteger.ZERO)
        return result
    }
}
