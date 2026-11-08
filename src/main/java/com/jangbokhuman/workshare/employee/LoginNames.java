package com.jangbokhuman.workshare.employee;

import java.text.Normalizer;

/** Canonical identity shared by employee registration and authentication. */
public final class LoginNames {
    private LoginNames() {
    }

    public static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    }
}
