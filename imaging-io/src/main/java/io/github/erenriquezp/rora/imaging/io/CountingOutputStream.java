package io.github.erenriquezp.rora.imaging.io;

import java.io.OutputStream;

/** Cuenta los bytes sin guardarlos. */
final class CountingOutputStream extends OutputStream {

  private long count;

  long count() {
    return count;
  }

  @Override
  public void write(int b) {
    count++;
  }

  @Override
  public void write(byte[] b, int off, int len) {
    count += len;
  }
}
