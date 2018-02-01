package toxic.job

class PauseInfo {
  private String name = ""
  private boolean paused = false
  private Date updateDate = new Date()

  public PauseInfo(String name) {
    this.name = name
  }

  public void pause() {
    paused = true
    update()
  }

  public void unpause() {
    paused = false
    update()
  }

  private void update() {
    updateDate = new Date()
  }

  public boolean isPaused() {
    return paused
  }

  public Date getToggleDate() {
    return updateDate
  }
}