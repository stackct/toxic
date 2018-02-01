
package toxic

public interface Validator {
  public void init(def props)
  public void validate(def actual, def expected, def memory)
}