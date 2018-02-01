package toxic.websocket

class UnexpectedResponseException extends Exception {
  UnexpectedResponseException(String msg) {
    super(msg)
  }
}
