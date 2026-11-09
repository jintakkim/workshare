package com.jangbokhuman.workshare.employee;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.text.Normalizer;

/** Canonical identity shared by employee registration and authentication. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LoginNames {

    public static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
    }
}
