package com.toshif.shorturl.hashing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Base62Test {

    @Test
    void encodesZeroAsFirstAlphabetCharacter() {
        assertEquals("0", Base62.encode(0L));
    }

    @Test
    void roundTripsArbitraryValues() {
        long[] values = {1, 61, 62, 123, 999_999, 4_611_686_018_427_387L};
        for (long v : values) {
            assertEquals(v, Base62.decode(Base62.encode(v)));
        }
    }

    @Test
    void producesUrlSafeCharactersOnly() {
        String code = Base62.encode(9_223_372_036_854L);
        assertEquals(code, code.replaceAll("[^0-9A-Za-z]", ""));
    }
}
