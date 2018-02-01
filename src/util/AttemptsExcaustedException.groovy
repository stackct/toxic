package util

class AttemptsExhaustedException extends Exception {
  int attempts

  public AttemptsExhaustedException(int attempts) {
    super("Failed after ${attempts} attempts")
    this.attempts = attempts
  }
}