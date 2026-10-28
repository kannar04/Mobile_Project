package com.gmail.manhle240406.project.wiring;
import org.junit.Test;
import static org.junit.Assert.*;
import com.gmail.manhle240406.project.adoption.DiscoveryQuery;
public final class DiscoveryNormalizationTest {
 @Test public void vietnameseAndUnicodeSpacingHaveTheSameAsciiPrefix(){
  assertEquals("meo do",DiscoveryQuery.normalize("  M\u00e8o\u00a0\u2003\u0110\u1ecf\t ",32));
  assertEquals("a b",DiscoveryQuery.normalize("a\u0085b",32));
  assertEquals("",DiscoveryQuery.normalize(" \t\u00a0",32));
 }
 @Test public void unsupportedPunctuationStillRejects(){try{DiscoveryQuery.normalize("cat*",32);fail();}catch(IllegalArgumentException expected){assertEquals("unsupported_prefix",expected.getMessage());}}
}
