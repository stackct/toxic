package toxic.job

import org.apache.log4j.*

public class SourceRepositoryFactory {
  private static Logger log = Logger.getLogger(SourceRepositoryFactory.class)

  public static SourceRepository make(String type, String local, String remote, String changesetUrlTemplate, String branch = null) {
    try {
      Class.forName(type).newInstance(local, remote, changesetUrlTemplate, branch)
    }
    catch (ClassNotFoundException cnf) {
      throw new SourceRepositoryException("Unsupported repository type ++++ type=${type}")
    }    
  }
}

class SourceRepositoryException extends Exception {
  public SourceRepositoryException(String msg) {
    super(msg)
  }
}