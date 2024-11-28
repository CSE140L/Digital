/*
 * Copyright (c) 2018 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.testing.parser.functions;

import de.neemann.digital.testing.parser.Context;
import de.neemann.digital.testing.parser.Expression;
import de.neemann.digital.testing.parser.ParserException;

import java.util.ArrayList;

/**
 * Generates a random number.
 * Useful to create regression tests.
 */
public class Get2421 extends Function {
    private static final char[] ENCODING = new char[]{0b0000, 0b0001, 0b0010, 0b0011, 0b0100, 0b1011, 0b1100, 0b1101, 0b1110, 0b1111};

    /**
     * Creates a new function
     */
    public Get2421() {
        super(1);
    }

    /**
     * Convert a decimal number to a BCD number
     * @param bcd a decimal value
     * @return 2421 value
     */
    public static long decimalTo2421(long bcd) {
        long result = 0;
        int i = 0;

        while (bcd != 0) {
            char digit = (char) (bcd % 10);
            result |= (long) ENCODING[digit] << (i*4);
            bcd /= 10;
            i++;
        }

        return result;
    }
    @Override
    public long calcValue(Context c, ArrayList<Expression> args) throws ParserException {
        long binary = args.get(0).value(c);
        return decimalTo2421(binary);
    }
}
