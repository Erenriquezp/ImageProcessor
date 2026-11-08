package com.example.imageprocessor.testsupport;

import java.awt.image.BufferedImage;
import java.util.function.UnaryOperator;

/**
 * Una operación con unos parámetros concretos. Sustituye al record {@code FilterParams} (privado en
 * {@code ImageProcessorApp}, inaccesible desde tests) con un catálogo de casos con nombre.
 *
 * @param op identificador de la operación (carpeta del golden)
 * @param name nombre del caso de parámetros (neutral, typical, extreme...)
 * @param fn aplica la operación sobre una imagen
 * @param alphaPreserved si la operación conserva el alpha de cada píxel de entrada
 * @param identity si el caso es "neutro": la salida debe ser ≈ la entrada
 * @param identityTolerance tolerancia por canal de la identidad
 */
public record OpCase(
    String op,
    String name,
    UnaryOperator<BufferedImage> fn,
    boolean alphaPreserved,
    boolean identity,
    int identityTolerance) {

  public static OpCase of(String op, String name, UnaryOperator<BufferedImage> fn) {
    return new OpCase(op, name, fn, true, false, 0);
  }

  /** La operación modifica el alpha a propósito. */
  public OpCase changesAlpha() {
    return new OpCase(op, name, fn, false, identity, identityTolerance);
  }

  /** Marca el caso como neutro (≈ identidad dentro de la tolerancia). */
  public OpCase neutral(int tolerance) {
    return new OpCase(op, name, fn, alphaPreserved, true, tolerance);
  }

  public BufferedImage apply(BufferedImage in) {
    return fn.apply(in);
  }

  public String id() {
    return op + "/" + name;
  }

  @Override
  public String toString() {
    return id();
  }
}
