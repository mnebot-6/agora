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
}
