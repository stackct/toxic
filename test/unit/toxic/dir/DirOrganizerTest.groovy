package toxic.dir

import log.Log
import toxic.MissingFileTask
import toxic.ToxicProperties
import toxic.xml.XmlTask
import org.apache.log4j.Level
import org.junit.*
import static org.junit.Assert.fail

public class DirOrganizerTest {
  @After
  void after() {
    DirItem.metaClass = null
  }
  
  @Test
  void should_get_next_file() {
    def tempDir1
    def tempDir2
    def tempDir3
    try {
      tempDir1 = File.createTempDir()
      new File(tempDir1, 'test.properties').text = 'k=v'
      tempDir2 = File.createTempDir()
      new File(tempDir2, 'test.properties').text = 'k=v'
      tempDir3 = File.createTempDir()
      new File(tempDir3, 'test.properties').text = 'k=v'

      def props = new ToxicProperties()
      props["doDir"] = tempDir1.absolutePath
      props["doDir1"] = tempDir2.absolutePath
      props["doDir2"] = tempDir3.absolutePath

      def org = new DirOrganizer(runRemoteFunctionsFile: new File('doNotLoadRemoteFunctionsFile'))
      def ff = new File("fake")
      def nf
      DirItem.metaClass.exists = { return true }
      DirItem.metaClass.nextFile = { p -> nf = nf ? null : ff; return nf }
      org.init(props)

      // doDir test
      assert org.nextFile()
      assert org.dirItem.file.name == tempDir1.name
      assert org.nextFile()
      assert org.dirItem.file.name == tempDir2.name
      assert org.nextFile()
      assert org.dirItem.file.name == tempDir3.name
      assert !org.nextFile()

      assert org.dirItem == null
      assert [tempDir1.absolutePath, tempDir2.absolutePath, tempDir3.absolutePath] == org.doDirs
    }
    finally {
      tempDir1?.deleteDir()
      tempDir2?.deleteDir()
      tempDir3?.deleteDir()
    }
  }

  @Test
  void should_get_next_file_when_dir_item_is_empty() {
    def tempDir1
    def tempDir2
    def tempDir3
    try {
      tempDir1 = File.createTempDir()
      new File(tempDir1, 'test.properties').text = 'k=v'
      tempDir2 = File.createTempDir()
      new File(tempDir2, 'test.properties').text = 'k=v'
      tempDir3 = File.createTempDir()
      new File(tempDir3, 'test.properties').text = 'k=v'

      def props = new ToxicProperties()
      props["doDir"] = tempDir1.absolutePath
      props["doDir1"] = tempDir2.absolutePath
      props["doDir2"] = tempDir3.absolutePath

      def org = new DirOrganizer(runRemoteFunctionsFile: new File('doNotLoadRemoteFunctionsFile'))
      DirItem.metaClass.exists = { return true }
      int nextFileIndex = 0
      DirItem.metaClass.nextFile = { p ->
        File file = null
        /**
         * Index 0 will return a fake file for doDir 'test'
         * Index 1 will return null to move to the next dir item, doDir1 'test1'
         * Index 2 will return null to move to the next dir item, doDir2 'test2'
         * Index 3 will return a fake file for doDir2 'test2'
         */
        if (nextFileIndex == 0 || nextFileIndex == 3) {
          file = new File('fake')
        }
        nextFileIndex++
        file
      }
      org.init(props)
      assert [tempDir1.absolutePath, tempDir2.absolutePath, tempDir3.absolutePath] == org.doDirs

      assert org.nextFile()
      assert tempDir1.name == org.dirItem.file.name

      assert org.nextFile()
      assert tempDir3.name == org.dirItem.file.name

      assert !org.nextFile()
      assert null == org.dirItem
    }
    finally {
      tempDir1?.deleteDir()
      tempDir2?.deleteDir()
      tempDir3?.deleteDir()
    }
  }

  @Test
  void should_return_MissingFileTask_on_missing_files() {
    def org = new DirOrganizer()
    def ff = new File("fake2.xml")
    def nf
    DirItem.metaClass.exists = { return true }
    DirItem.metaClass.nextFile = { p -> nf = nf ? null : ff; return nf }
    def props = new ToxicProperties()
    props["doDir"] = "test"
    props["doTaskClass.xml"]=XmlTask.class.getName()
    org.init(props)

    assert org.hasNext()
    assert org.nextTask instanceof MissingFileTask
  }

  @Test
  void should_return_MissingFileTask_on_missing_directories() {
    def org = new DirOrganizer()
    def ff = new File("fake_dir")
    def nf
    DirItem.metaClass.exists = { return true }
    DirItem.metaClass.nextFile = { p -> nf = nf ? null : ff; return nf }
    def props = new ToxicProperties()
    props["doDir"] = "test"
    props["doTaskClass.xml"]=XmlTask.class.getName()
    org.init(props)

    assert org.hasNext()
    assert org.nextTask instanceof MissingFileTask
  }

  @Test
  void should_return_MissingFileTask_on_missing_file_of_unknown_extension() {
    def org = new DirOrganizer()
    def ff = new File("fake_file.bandicoot")
    def nf
    DirItem.metaClass.exists = { return true }
    DirItem.metaClass.nextFile = { p -> nf = nf ? null : ff; return nf }
    def props = new ToxicProperties()
    props["doDir"] = "test"
    props["doTaskClass.xml"]=XmlTask.class.getName()
    org.init(props)

    assert org.hasNext()
    assert org.nextTask instanceof MissingFileTask
  }

  @Test
  void should_init_with_default_properties() {
    ToxicProperties props = new ToxicProperties()
    props.doDir = 'test'
    props.homePath = '/home'
    DirOrganizer dirOrganizer = new DirOrganizer()
    dirOrganizer.init(props)
    assert '/home/toxic/library' == props.libPath
  }

  @Test
  void should_init_with_override_properties() {
    ToxicProperties props = new ToxicProperties()
    props.doDir = 'test'
    props.homePath = '/home'
    props.libPath = '/library'
    DirOrganizer dirOrganizer = new DirOrganizer()
    dirOrganizer.init(props)
    assert '/library' == props.libPath
  }

  @Test
  void should_init_fn_dir_when_configured() {
    def tempDir
    try {
      tempDir = File.createTempDir()
      new File(tempDir, 'test.fn').text = 'test'
      ToxicProperties props = new ToxicProperties()
      props.doDir = 'test'
      props.homePath = '/home'
      props.fnDir = tempDir.absolutePath
      DirOrganizer dirOrganizer = new DirOrganizer()
      dirOrganizer.init(props)
      assert [tempDir.absolutePath, dirOrganizer.runRemoteFunctionsFile.absolutePath, new File('test').absolutePath] == dirOrganizer.doDirs
    }
    finally {
      tempDir?.deleteDir()
    }
  }

  @Test
  void should_default_fn_dir_when_not_configured() {
    def tempDir
    try {
      tempDir = File.createTempDir()
      File fnDir = new File(tempDir, 'toxic/functions')
      fnDir.mkdirs()
      new File(fnDir, 'test.fn').text = 'test'
      ToxicProperties props = new ToxicProperties()
      props.doDir = 'test'
      props.homePath = tempDir.absolutePath
      DirOrganizer dirOrganizer = new DirOrganizer()
      dirOrganizer.init(props)
      assert [fnDir.absolutePath, dirOrganizer.runRemoteFunctionsFile.absolutePath, new File('test').absolutePath] == dirOrganizer.doDirs
    }
    finally {
      tempDir?.deleteDir()
    }
  }

  @Test
  void should_init_deps_dir_when_configured() {
    def tempDir
    try {
      tempDir = File.createTempDir()
      new File(tempDir, 'test.dep').text = 'test'
      ToxicProperties props = new ToxicProperties()
      props.doDir = 'test'
      props.homePath = '/home'
      props.depsDir = tempDir.absolutePath
      DirOrganizer dirOrganizer = new DirOrganizer()
      dirOrganizer.init(props)
      assert [tempDir.absolutePath, dirOrganizer.runRemoteFunctionsFile.absolutePath, new File('test').absolutePath] == dirOrganizer.doDirs
    }
    finally {
      tempDir?.deleteDir()
    }
  }

  @Test
  void should_default_deps_dir_when_not_configured() {
    def tempDir
    try {
      tempDir = File.createTempDir()
      File depsDir = new File(tempDir, 'toxic/deps')
      depsDir.mkdirs()
      new File(depsDir, 'test.dep').text = 'test'
      ToxicProperties props = new ToxicProperties()
      props.doDir = 'test'
      props.homePath = tempDir.absolutePath
      DirOrganizer dirOrganizer = new DirOrganizer()
      dirOrganizer.init(props)
      assert [depsDir.absolutePath, dirOrganizer.runRemoteFunctionsFile.absolutePath, new File('test').absolutePath] == dirOrganizer.doDirs
    }
    finally {
      tempDir?.deleteDir()
    }
  }

  @Test
  void should_add_run_remote_functions() {
    ToxicProperties props = new ToxicProperties()
    props.doDir = 'test'
    props.homePath = '/home'
    DirOrganizer dirOrganizer = new DirOrganizer()
    dirOrganizer.init(props)
    assert [dirOrganizer.runRemoteFunctionsFile.absolutePath, new File('test').absolutePath] == dirOrganizer.doDirs
  }

  @Test
  void should_not_reinit_do_dirs() {
    def tempDir
    try {
      tempDir = File.createTempDir()
      new File(tempDir, 'test.fn').text = 'test'
      ToxicProperties props = new ToxicProperties()
      props.doDir = tempDir.absolutePath
      props.homePath = '/home'
      props.fnDir = tempDir.absolutePath
      DirOrganizer dirOrganizer = new DirOrganizer()
      dirOrganizer.init(props)
      assert [tempDir.absolutePath, dirOrganizer.runRemoteFunctionsFile.absolutePath, tempDir.absolutePath] == dirOrganizer.doDirs

      dirOrganizer = new DirOrganizer()
      dirOrganizer.init(props)
      assert [dirOrganizer.runRemoteFunctionsFile.absolutePath, tempDir.absolutePath] == dirOrganizer.doDirs
    }
    finally {
      tempDir?.deleteDir()
    }
  }

  @Test
  void should_run_remote() {
    def tempDir
    try {
      tempDir = File.createTempDir()
      ToxicProperties props = new ToxicProperties()
      props.doDir = 'test'
      props.homePath = tempDir.absolutePath
      props.'toxic.remote' = true
      props.'toxic.remote.arg.docker' = true
      DirOrganizer dirOrganizer = new DirOrganizer()
      dirOrganizer.init(props)
      assert [dirOrganizer.runRemoteFunctionsFile.absolutePath, dirOrganizer.runRemoteFile.absolutePath] == dirOrganizer.doDirs
      assert true == props['toxic.remote.args'].useDepsCache
      assert true == props['toxic.remote.args'].docker
      assert 'test' == props.'toxic.remote.doDir'
    }
    finally {
      tempDir?.deleteDir()
    }
  }

  @Test
  void should_log_fail_when_add_do_dir_does_not_exist() {
    Log log = Log.getLogger(DirOrganizer.class)
    ToxicProperties props = new ToxicProperties()
    props.doDir = 'doesNotExist'
    props.log = log

    DirOrganizer dirOrganizer = new DirOrganizer(runRemoteFunctionsFile: new File('doNotLoadRemoteFunctionsFile'))
    log.track { logger ->
      try {
        dirOrganizer.init(props)
        fail('Expected IllegalArgumentException')
      }
      catch(IllegalArgumentException e) {
        assert 'Invalid directory item name specified; doDirs=[]' == e.message
        assert logger.isLogged("skipping non-existent or empty doDir; doDir=${new File('doesNotExist').absolutePath}", Level.WARN)
      }
    }
  }
}