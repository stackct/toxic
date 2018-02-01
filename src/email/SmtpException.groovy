package email

class SmtpException extends Exception {
  public SmtpException(String str) {
    super(str)
  }

  public SmtpException(String str, Throwable t) {
    super(str, t)
  }
}