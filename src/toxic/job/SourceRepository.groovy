package toxic.job

interface SourceRepository {
  /**
   * Returns true if the repository has changes that need to be pulled locally.
   */
  public boolean hasChanges()
  
  /**
   * Updates the local repository, and returns the changes since the previous update.
   *
   * @see collectChanges
   */
  public List update()
  
  /**
   * Each list item should be a map containing the following keys and values, if
   * available:
   *  changeset - id or key of the changeset
   *  changesetUrl - link to the website containing the change details/diff
   *  user - author's username, typically the network login
   *  name - author's name, typically first and last if available
   *  email - author's email, if available
   *  date - date or time string representation of the change
   *  dateObject - Date object for the time of the change
   */
  public List collectChanges()


  /**
   * Returns information about specific commit
   */
  public String getDiff(String changeset)
}