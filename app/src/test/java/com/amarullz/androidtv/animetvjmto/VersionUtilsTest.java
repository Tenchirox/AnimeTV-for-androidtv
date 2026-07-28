package com.amarullz.androidtv.animetvjmto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VersionUtilsTest {

  @Test
  public void versionParts_semver() {
    assertArrayEquals(new int[]{6, 6, 7}, VersionUtils.versionParts("6.6.7"));
  }

  @Test
  public void versionParts_withVPrefix() {
    assertArrayEquals(new int[]{6, 6, 8}, VersionUtils.versionParts("v6.6.8"));
  }

  @Test
  public void versionParts_withSuffix() {
    assertArrayEquals(new int[]{6, 6, 7}, VersionUtils.versionParts("6.6.7-Nightly"));
    assertArrayEquals(new int[]{6, 6, 7}, VersionUtils.versionParts("v6.6.7-Nightly"));
  }

  @Test
  public void versionParts_notVersion() {
    assertArrayEquals(new int[]{0}, VersionUtils.versionParts("nightly"));
  }

  @Test
  public void isNewer_newerMinor() {
    assertTrue(VersionUtils.isNewerVersion("v6.6.8", "6.6.7-Nightly"));
  }

  @Test
  public void isNewer_newerMajor() {
    assertTrue(VersionUtils.isNewerVersion("v7.0.0", "6.6.7-Nightly"));
  }

  @Test
  public void isNewer_sameVersion() {
    assertFalse(VersionUtils.isNewerVersion("v6.6.7-Nightly", "6.6.7-Nightly"));
    assertFalse(VersionUtils.isNewerVersion("6.6.7", "6.6.7-Nightly"));
  }

  @Test
  public void isNewer_older() {
    assertFalse(VersionUtils.isNewerVersion("v6.6.6", "6.6.7-Nightly"));
    assertFalse(VersionUtils.isNewerVersion("v5.4.2", "6.6.7-Nightly"));
  }

  @Test
  public void isNewer_differentLengths() {
    assertTrue(VersionUtils.isNewerVersion("6.7", "6.6.7"));
    assertFalse(VersionUtils.isNewerVersion("6.6", "6.6.7"));
    assertTrue(VersionUtils.isNewerVersion("6.6.7.1", "6.6.7"));
  }

  @Test
  public void isNewer_garbageInput() {
    assertFalse(VersionUtils.isNewerVersion("nightly", "6.6.7-Nightly"));
    assertFalse(VersionUtils.isNewerVersion("", "6.6.7-Nightly"));
  }

  @Test
  public void formatSize_megabytes() {
    assertEquals("6.3 MB", VersionUtils.formatSize(6606028));
    assertEquals("0.5 MB", VersionUtils.formatSize(524288));
  }

  @Test
  public void formatSize_invalid() {
    assertEquals("? MB", VersionUtils.formatSize(0));
    assertEquals("? MB", VersionUtils.formatSize(-42));
  }

  @Test
  public void parseSha256Digest_valid() {
    assertEquals("ab12cd",
        VersionUtils.parseSha256Digest("sha256:ab12cd"));
  }

  @Test
  public void parseSha256Digest_otherAlgo() {
    assertEquals(null, VersionUtils.parseSha256Digest("md5:ab12cd"));
    assertEquals(null, VersionUtils.parseSha256Digest(""));
    assertEquals(null, VersionUtils.parseSha256Digest(null));
  }
}
