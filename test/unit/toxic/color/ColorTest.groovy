package toxic.color

import org.junit.*
import org.junit.jupiter.api.*

public class ColorTest {
  @Test
  public void colorSuccess() {
    isColor("32", {-> Color.success([:] as ConfigObject, "foo")})
    isColor("10", {-> Color.success([colorSuccess:10] as ConfigObject, "foo")})
  }

  @Test
  public void colorFailure() {
    isColor("31", {-> Color.failure([:] as ConfigObject, "foo")})
    isColor("9", {-> Color.failure([colorFailure:9] as ConfigObject, "foo")})
  }

  @Test
  public void colorStep() {
    isColor("32", {-> Color.step([:] as ConfigObject, "foo")})
  }

  @Test
  public void colorHttp() {
    isColor("25", {-> Color.http([:] as ConfigObject, "foo")})
    isColor("48", {-> Color.http([colorHttp:48] as ConfigObject, "foo")})
  }

  @Test
  public void colorSql() {
    isColor("38", {-> Color.sql([:] as ConfigObject, "foo")})
    isColor("8", {-> Color.sql([colorSql:8] as ConfigObject, "foo")})
  }

  @Test
  public void should_default_to_enabled() {
    isColor("38", {-> Color.sql([:] as ConfigObject, "foo")})
  }

  @Test
  public void canEnable() {
    isColor("32", {-> Color.success([colorEnabled:true] as ConfigObject, "foo")})
    isColor("32", {-> Color.success([colorEnabled:"true"] as ConfigObject, "foo")})
  }

  @Test
  public void canDisable() {
    assert "foo" == Color.success([colorEnabled:false] as ConfigObject, "foo")
    assert "foo" == Color.success([colorEnabled:"false"] as ConfigObject, "foo")
  }

  Boolean isColor(String color, def check) {
    assert "\u001B[38;5;${color}mfoo\u001B[0m" == check()
  }

  Boolean hasNoColor(def check) {
    assert "foo" == check()
  }
}
