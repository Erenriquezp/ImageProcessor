package io.github.erenriquezp.rora.core.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.erenriquezp.rora.core.internal.ImgAccess;
import org.junit.jupiter.api.Test;

class ImgTest {

  @Test
  void createIsTransparentBlack() {
    Img img = Img.create(3, 2);
    assertThat(img.width()).isEqualTo(3);
    assertThat(img.height()).isEqualTo(2);
    assertThat(img.pixelCount()).isEqualTo(6);
    assertThat(img.toArray()).containsOnly(0);
    assertThat(img).hasToString("Img[3x2]");
  }

  @Test
  void copyOfCopiesAndAdoptDoesNot() {
    int[] px = {1, 2, 3, 4};
    Img copied = Img.copyOf(2, 2, px);
    Img adopted = Img.adopt(2, 2, px);
    px[0] = 99;
    assertThat(copied.argb(0, 0)).isEqualTo(1);
    assertThat(adopted.argb(0, 0)).isEqualTo(99);
    assertThat(ImgAccess.pixels(adopted)).isSameAs(px);
    assertThat(ImgAccess.pixels(copied)).isNotSameAs(px);
  }

  @Test
  void rowMajorAccessAndCopies() {
    Img img = Img.copyOf(3, 2, new int[] {10, 11, 12, 20, 21, 22});
    assertThat(img.argb(2, 1)).isEqualTo(22);
    int[] row = new int[5];
    img.copyRow(1, row, 1);
    assertThat(row).containsExactly(0, 20, 21, 22, 0);
    int[] all = img.toArray();
    all[0] = -1;
    assertThat(img.argb(0, 0)).isEqualTo(10);
    int[] dst = new int[6];
    img.copyTo(dst);
    assertThat(dst).containsExactly(10, 11, 12, 20, 21, 22);
  }

  @Test
  void boundsAreChecked() {
    Img img = Img.create(2, 2);
    assertThatThrownBy(() -> img.argb(2, 0)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> img.argb(0, -1)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> img.copyRow(2, new int[2], 0))
        .isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> img.copyTo(new int[3])).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidDimensionsAreRejected() {
    assertThatThrownBy(() -> Img.create(0, 1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Img.create(1, -1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Img.create(65536, 65536))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("grande");
    assertThatThrownBy(() -> Img.copyOf(2, 2, new int[3]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Img.adopt(2, 2, null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void sameContentComparesDimensionsAndPixels() {
    Img a = Img.copyOf(2, 1, new int[] {1, 2});
    assertThat(a.sameContent(Img.copyOf(2, 1, new int[] {1, 2}))).isTrue();
    assertThat(a.sameContent(Img.copyOf(1, 2, new int[] {1, 2}))).isFalse();
    assertThat(a.sameContent(Img.copyOf(2, 1, new int[] {1, 3}))).isFalse();
    assertThat(a.sameContent(null)).isFalse();
  }

  @Test
  void accessorCanOnlyBeRegisteredOnce() {
    Img.create(1, 1);
    assertThatThrownBy(() -> ImgAccess.setAccessor(i -> null))
        .isInstanceOf(IllegalStateException.class);
  }
}
