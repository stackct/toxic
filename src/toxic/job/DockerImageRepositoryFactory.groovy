package toxic.job

import toxic.ToxicProperties
import org.apache.log4j.*

public class DockerImageRepositoryFactory {
  private static Logger log = Logger.getLogger(DockerImageRepositoryFactory.class)

  public static DockerImageRepository make(String type, String name, ToxicProperties properties) {
    try {
      Class.forName(type).newInstance(name, properties)
    }
    catch (ClassNotFoundException cnf) {
      throw new DockerImageRepositoryException("Unsupported repository type ++++ type=${type}")
    }    
  }
}

class DockerImageRepositoryException extends Exception {
  public DockerImageRepositoryException(String msg) {
    super(msg)
  }
}