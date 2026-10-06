package com.tickethub.payment.gateway;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Razorpay wants money as a WHOLE NUMBER of the smallest currency unit: Rs 500.00 = 50000 paise.
 * We keep BigDecimal everywhere inside our service (no rounding bugs) and convert only here, at the border.
 *
 * Works for 2-decimal currencies such as INR/USD. The service only accepts currencies listed in
 * tickethub.payment.allowed-currencies (default INR), so this assumption is enforced, not hoped for.
 */
public final class AmountUtil {

    private AmountUtil() {
    }

    /** 500.00 -> 50000. Throws ArithmeticException if the amount has more than 2 decimal places (never silently rounds money). */
    public static long toMinorUnits(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    /** 50000 -> 500.00 */
    public static BigDecimal fromMinorUnits(long minorUnits) {
        return BigDecimal.valueOf(minorUnits, 2);
    }
}
