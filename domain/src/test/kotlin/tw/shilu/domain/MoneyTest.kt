package tw.shilu.domain

import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test fun currencyScalesAndExactRounding() {
        assertEquals(0,Money.scale("JPY"));assertEquals(2,Money.scale("TWD"));assertEquals(3,Money.scale("KWD"))
        assertEquals(21500,Money.convert(1000,"JPY","TWD","0.215"))
        assertEquals(22,Money.convert(1,"JPY","TWD","0.215"))
        assertEquals(1235,Money.convert(100,"TWD","KWD","1.2345"))
        assertEquals(123,Money.parse("1.23","TWD"))
    }
    @Test fun averageRemainderStableAcrossDisplayOrder() {
        val a=Money.equal(10000,listOf("c","a","b"));val b=Money.equal(10000,listOf("b","c","a"))
        assertEquals(a,b);assertEquals(3334L,a["a"]);assertEquals(10000,a.values.sum())
    }
    @Test fun customAndLedgerNetSumToZero() {
        val shares=Money.custom(21500,mapOf("a" to 10000L,"b" to 11500L))
        val result=Money.balances(listOf(Money.LedgerExpense(21500,"c",shares),Money.LedgerExpense(99,"a",Money.equal(99,listOf("a","b","c")))))
        assertEquals(0,result.values.sumOf{it.net});assertEquals(21467,result["c"]!!.net)
    }
    @Test fun rejectBadInputsAndOverflow() {
        listOf<()->Any>({Money.parse("1.2","JPY")},{Money.rate("-1")},{Money.rate("NaN")},{Money.rate("0")},{Money.convert(100,"TWD","TWD","2")},{Money.custom(100,mapOf("a" to 99L))},{Money.equal(100,emptyList())},{Money.parse("999999999999999999999","TWD")}).forEach { op->assertTrue(runCatching(op).isFailure) }
    }
    @Test fun generatedLedgersPreserveEveryMinorUnit() {
        val ids=listOf("a","b","c","d","e","f","g")
        for(total in listOf(0L,1,2,99,100,21500,999999999999L))for(count in 1..7){val split=Money.equal(total,ids.take(count));assertEquals(total,split.values.sum());assertTrue(split.values.max()-split.values.min()<=1);assertEquals(0,Money.balances(listOf(Money.LedgerExpense(total,ids.last(),split))).values.sumOf{it.net})}
    }
}
