package com.stayease.user.util;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility for generating cryptographically secure temporary passwords.
 * Ensures balanced character diversity (uppercase, lowercase, digits, symbols).
 */
public final class PasswordGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"; // excluded easily confused I, O
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz"; // excluded easily confused l
    private static final String DIGITS = "23456789";                  // excluded easily confused 0, 1
    private static final String SPECIAL = "!@#$%^&*()-_=+";
    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

    private PasswordGenerator() {}

    /**
     * Generates a cryptographically strong temporary password of specified length (min 12).
     */
    public static String generatePassword(int length) {
        int targetLength = Math.max(length, 12);
        List<Character> chars = new ArrayList<>();

        // Ensure at least two of each character class
        chars.add(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        chars.add(UPPER.charAt(RANDOM.nextInt(UPPER.length())));
        chars.add(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        chars.add(LOWER.charAt(RANDOM.nextInt(LOWER.length())));
        chars.add(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        chars.add(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        chars.add(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));
        chars.add(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));

        for (int i = chars.size(); i < targetLength; i++) {
            chars.add(ALL.charAt(RANDOM.nextInt(ALL.length())));
        }

        Collections.shuffle(chars, RANDOM);

        StringBuilder sb = new StringBuilder(targetLength);
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }
}
