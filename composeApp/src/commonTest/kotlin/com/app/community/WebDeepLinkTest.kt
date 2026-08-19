package com.app.community

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebDeepLinkTest {

    @Test
    fun parsesInviteCodeFromQuery() {
        assertEquals(WebDeepLink.Invite("ABC123"), parseWebDeepLink("?c=ABC123"))
    }

    @Test
    fun parsesActivityCodeFromQuery() {
        assertEquals(WebDeepLink.Activity("XYZ789"), parseWebDeepLink("?a=XYZ789"))
    }

    @Test
    fun parsesCodeAmongOtherParams() {
        assertEquals(WebDeepLink.Activity("XYZ"), parseWebDeepLink("?utm_source=share&a=XYZ"))
    }

    @Test
    fun inviteWinsWhenBothParamsPresent() {
        assertEquals(WebDeepLink.Invite("AAA"), parseWebDeepLink("?c=AAA&a=BBB"))
    }

    @Test
    fun decodesPercentEncodedCode() {
        assertEquals(WebDeepLink.Invite("A B"), parseWebDeepLink("?c=A%20B"))
    }

    @Test
    fun returnsNullForEmptyOrIrrelevantQuery() {
        assertNull(parseWebDeepLink(""))
        assertNull(parseWebDeepLink("?"))
        assertNull(parseWebDeepLink("?c="))
        assertNull(parseWebDeepLink("?foo=bar"))
    }

    @Test
    fun parsesPaymentIdFromQuery() {
        assertEquals(
            WebDeepLink.Payment("6b1f0d2e-0000-4000-8000-000000000001"),
            parseWebDeepLink("?pay=6b1f0d2e-0000-4000-8000-000000000001"),
        )
    }

    /**
     * Volver de un cobro es lo mas urgente que puede traer una URL: si se pierde, el usuario
     * se queda mirando una plaza sin confirmar despues de haber pagado.
     */
    @Test
    fun paymentWinsOverInviteAndActivity() {
        assertEquals(WebDeepLink.Payment("P"), parseWebDeepLink("?c=AAA&a=BBB&pay=P"))
        assertEquals(WebDeepLink.Payment("P"), parseWebDeepLink("?pay=P&c=AAA"))
    }

    @Test
    fun parsesConnectReturn() {
        assertEquals(
            WebDeepLink.ConnectReturn("c-123"),
            parseWebDeepLink("?connect=c-123"),
        )
    }

    /** Un pago en curso es mas urgente que volver de configurar cobros. */
    @Test
    fun paymentWinsOverConnectReturn() {
        assertEquals(WebDeepLink.Payment("P"), parseWebDeepLink("?connect=C&pay=P"))
    }

    @Test
    fun emptyConnectIdIsIgnored() {
        assertNull(parseWebDeepLink("?connect="))
    }

    @Test
    fun emptyPaymentIdFallsThroughToTheOtherParams() {
        assertEquals(WebDeepLink.Invite("AAA"), parseWebDeepLink("?pay=&c=AAA"))
        assertNull(parseWebDeepLink("?pay="))
    }
}
