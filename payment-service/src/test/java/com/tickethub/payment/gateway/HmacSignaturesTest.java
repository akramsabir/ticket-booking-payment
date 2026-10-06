package com.tickethub.payment.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class HmacSignaturesTest {

    @Test
    void knownVector_matchesRfc4231Style() {
        // Independent reference value computed with:  echo -n "what do ya want for nothing?" | openssl dgst -sha256 -hmac Jefe
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                HmacSignatures.hmacSha256Hex("what do ya want for nothing?", "Jefe"));
    }

    @Test
    void checkoutSignature_isHmacOfOrderPipePayment() {
        String sig = HmacSignatures.hmacSha256Hex("order_A|pay_B", "secret");
        assertTrue(HmacSignatures.matches(sig, sig));
        assertFalse(HmacSignatures.matches(sig, HmacSignatures.hmacSha256Hex("order_A|pay_C", "secret")));
    }

    @Test
    void matches_isCaseInsensitiveForHexAndRejectsNull() {
        String sig = HmacSignatures.hmacSha256Hex("x", "k");
        assertTrue(HmacSignatures.matches(sig, sig.toUpperCase()));
        assertFalse(HmacSignatures.matches(sig, null));
        assertFalse(HmacSignatures.matches(null, sig));
    }

    @Test
    void amountConversion_isExactAndNeverRoundsSilently() {
        assertEquals(50000L, AmountUtil.toMinorUnits(new BigDecimal("500")));
        assertEquals(50050L, AmountUtil.toMinorUnits(new BigDecimal("500.50")));
        assertEquals(new BigDecimal("500.50"), AmountUtil.fromMinorUnits(50050L));
        assertThrows(ArithmeticException.class, () -> AmountUtil.toMinorUnits(new BigDecimal("10.005")));
    }
}
