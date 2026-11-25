package com.datn.chatp2p.p2p.ice;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bo loc dia chi quang ba cho ben kia. Tu 25/11 cho ca IPv6 cong khai (do: 4G Vinaphone va 4G Viettel cap IPv6
 * cong khai, trong khi IPv4 la NAT doi xung).
 */
class IceAdvertisableAddressTest {

    @Test
    void routableIpv4AndPublicIpv6AreAdvertised() throws Exception {
        assertTrue(advertisable("172.20.10.2"), "IPv4 noi bo van quang ba (cung LAN di thang)");
        assertTrue(advertisable("113.185.44.11"));
        assertTrue(advertisable("2001:ee0:22c:e3c8:95c7:99d8:2234:e46c"), "IPv6 cong khai 4G Vinaphone");
        assertTrue(advertisable("2401:d800:cbd:e244:551d:10d3:4d7d:d0e8"), "IPv6 cong khai 4G Viettel");
    }

    @Test
    void unusableAddressesAreFiltered() throws Exception {
        assertFalse(advertisable("127.0.0.1"));
        assertFalse(advertisable("169.254.10.20"), "IPv4 link-local");
        assertFalse(advertisable("224.0.0.1"), "multicast");
        assertFalse(advertisable("::1"), "IPv6 loopback");
        assertFalse(advertisable("fe80::b96d:50b3:ca4f:94d8"), "IPv6 link-local");
        assertFalse(advertisable("fd12:3456:789a::1"), "IPv6 noi bo ULA");
        assertFalse(advertisable("2001:0:14c9:d700:34be:8ec:53eb:f5fd"), "Teredo (duong ham cu tren may Huy)");
        assertFalse(advertisable("2002:c000:204::1"), "6to4");
        assertFalse(advertisable("ff02::1"), "IPv6 multicast");
        assertFalse(advertisable("::"), "dia chi rong");
    }

    private static boolean advertisable(String literal) throws Exception {
        return IceP2pConnectionEstablisher.isAdvertisable(InetAddress.getByName(literal));
    }
}
