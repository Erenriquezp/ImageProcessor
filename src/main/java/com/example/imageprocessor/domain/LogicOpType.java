package com.example.imageprocessor.domain;

/** Operaciones lógicas bit a bit sobre canales RGB (OpenGL Logic Op). */
public enum LogicOpType {
    AND("AND"),
    OR("OR"),
    XOR("XOR"),
    NAND("NAND"),
    NOR("NOR"),
    INVERT("INVERT"),
    COPY_INVERTED("COPY_INVERTED");

    private final String label;

    LogicOpType(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
