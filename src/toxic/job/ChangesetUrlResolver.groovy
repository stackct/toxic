package toxic.job

public class ChangesetUrlResolver {
  public String resolve(String template, String repo, String changeset) {
    if (!template) return ""
    template.replace('@@repo@@', repo).replace('@@changeset@@', changeset)
  }
}