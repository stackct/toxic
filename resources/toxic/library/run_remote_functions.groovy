def securePropsFile = "/opt/toxic/conf/toxic-secure.properties"

memory.loadRemoteSettings = {
  if (memory['toxic.remote.environment']) return

  /* Constant Properties (Do not change these) */
  memory['toxic.remote.environment']           = "toxic-remote-${ memory.job?.id ?: memory.generateUniqueNumber()}"
  memory['toxic.remote.terraform.resources']   = new File(getClass().getResource('/terraform').toURI()).absolutePath

  /* Optional Properties */
  memory['toxic.remote.sshUser']               = memory['toxic.remote.sshUser'] ?: "ec2-user"
  memory['toxic.remote.aws.region']            = memory['toxic.remote.aws.region'] ?: "us-east-1"
  memory['toxic.remote.aws.vpc']               = memory['toxic.remote.aws.vpc'] ?: null
  memory['toxic.remote.aws.subnet']            = memory['toxic.remote.aws.subnet'] ?: null

  // This image should be an HVM Instance-Store image in the eastern region.
  memory['toxic.remote.aws.ec2.image']         = memory['toxic.remote.aws.ec2.image'] ?: "ami-a518e6df"

  memory['toxic.remote.aws.ec2.instancetype']  = memory['toxic.remote.aws.ec2.instancetype'] ?: "c3.2xlarge"
  memory['toxic.remote.sshKey']                = memory['toxic.remote.sshKey'] ?: "${System.getenv().get('HOME')}/.ssh/id_rsa_aws"
  memory['toxic.remote.tunnel.sourcehost']     = memory['toxic.remote.tunnel.sourcehost'] ?: "172.17.0.1"
  memory['toxic.remote.tunnel.sourceport']     = memory['toxic.remote.tunnel.sourceport'] ?: 8880
  memory['toxic.remote.tunnel.targethost']     = memory['toxic.remote.tunnel.targethost'] ?: "localhost"
  memory['toxic.remote.tunnel.targetport']     = memory['toxic.remote.tunnel.targetport'] ?: 8001

  /* ----------------------------------------------------------------------------
    TODO: This profile should be set to one that has permissions to manage
          the following:
            - ec2-instance
            - ec2-security-group
            - ec2-key-pair
            - instance-profile
            - route53-record
  ----------------------------------------------------------------------------- */
  memory['toxic.remote.aws.ec2.profile'] = memory['toxic.remote.aws.ec2.profile'] ?: "toxic-admin-dev"

  /* ----------------------------------------------------------------------------
    TODO: Replace this with an instance-profile that will be passed in to the
          Terraform configuration when launching the instance. In the meantime,
          we will dig out the credentials for the given profile to pass along
          into the instance.
  ----------------------------------------------------------------------------- */
  memory['toxic.remote.aws.ecr.profile'] = memory['toxic.remote.aws.ecr.profile'] ?: null

  def awsCreds = new File(System.getenv().get("HOME"), ".aws/credentials")?.text
  def lines = []
  awsCreds.eachLine { line -> lines << line }

  if (memory['toxic.remote.aws.ecr.profile']) {
    for (i=0; i<lines.size(); i++) {
      if (lines[i].contains("[${memory['toxic.remote.aws.ecr.profile']}]")) {
        memory['toxic.remote.aws.access_key_id']     = lines[i+1].tokenize("=")[1].trim()
        memory['toxic.remote.aws.secret_access_key'] = lines[i+2].tokenize("=")[1].trim()
        break;
      }
    }
  }

  if (! (memory['toxic.remote.aws.access_key_id'] && memory['toxic.remote.aws.secret_access_key'])) {
    log.warn("Could not locate AWS credentials; profile=${memory['toxic.remote.aws.ecr.profile']}")
  }
  /* --------------------------------------------------------------------------- */
}

memory.withSsh = { Closure c ->
  memory.loadRemoteSettings()

  /* Read back dynamic outputs */
  def props = [:]
  props.sshKey = memory['toxic.remote.sshKey']
  props.sshHost = memory.terraformOutput('public_ip', memory['toxic.remote.terraform.resources'])
  props.sshUser = memory['toxic.remote.sshUser']
  c(props)
}

memory.collectRemoteArtifacts = {
  memory.withSsh { props ->
    memory.runRemoteTests(props.sshHost, props.sshUser, props.sshKey, "toxic/library/cluster/extra/collect_artifacts", [:])
  }
}

memory.downloadRemoteArtifacts = {
  memory.withSsh { props ->
    assert 0 == memory.getRemoteArtifacts(props.sshHost, props.sshUser, props.sshKey)
  }
}

memory.getRemoteArtifacts = { sshHost, sshUser, sshKey ->

  int result = 0
  def tar = 'toxic.artifacts.tar'
  def localArtifactDir = memory.artifactsDirectory

  /* Package the artifacts */
  def execs = []
  execs << "sudo rm -f ~${sshUser}/${tar}"
  execs << "sudo rm -f ~${sshUser}/${tar}.gz"
  execs << "sudo tar cf ~${sshUser}/${tar} -C ~toxic/gen/artifacts ."
  execs << "sudo tar rf ~${sshUser}/${tar} -C /var/log ."
  execs << "sudo chown ${sshUser} ~${sshUser}/${tar}"
  execs << "sudo gzip ~${sshUser}/${tar}"

  tar += ".gz"

  execs.each { e -> result &= memory.execRemote(sshHost, e, sshUser, sshKey) }

  /* Retrieve the tarball */
  def cmds = []
  cmds << 'scp'
  cmds << '-o'
  cmds << 'StrictHostKeyChecking=no'
  cmds << '-o'
  cmds << 'UserKnownHostsFile=/dev/null'
  cmds << '-i'
  cmds << sshKey
  cmds << "${sshUser}@${sshHost}:~/${tar}"
  cmds << localArtifactDir

  result &= execWithEnv(cmds, [:], 60, memory.homePath)

  /* Unpack the tarball */
  cmds = []
  cmds << 'tar'
  cmds << 'xzf'
  cmds << localArtifactDir + '/' + tar
  cmds << '-C'
  cmds << localArtifactDir
  cmds << '.'

  result &= execWithEnv(cmds, [:], 60, memory.homePath)

  result
}

memory.teardownRemoteEnvironment = { preserve ->
  memory.downloadRemoteArtifacts()

  if (!preserve) {
    /* Teardown remote instance and clean up */
    assert 0 == memory.terraformDestroy(memory['toxic.remote.terraform.resources'])
    /* Terraform Cleanup */
    memory.terraformCleanup()
  }
}

memory.runRemoteTests = { sshHost, sshUser, sshKey, doDir, args ->
  def expandedArgs = args.collect { k,v -> "-${k}=${v}" }.join(" ")
  expandedArgs += " -jobId=${memory.job?.id ?: 'unknown.job'}"

  def cmds = []
  cmds << "sudo"
  cmds << "su"
  cmds << "-"
  cmds << "toxic"
  cmds << "-c"
  cmds << "\"toxic -doDir=${doDir} ${expandedArgs}\"".toString()

  if (new File(securePropsFile).isFile()) {
    exec("scp -i ${sshKey} ${securePropsFile} ${sshUser}@${sshHost}:/tmp/tmp.properties")
    exec("ssh -i ${sshKey} ${sshUser}@${sshHost} sudo chown toxic:toxic /tmp/tmp.properties")
    exec("ssh -i ${sshKey} ${sshUser}@${sshHost} sudo mv /tmp/tmp.properties ${securePropsFile}")
  }

  memory.execRemote(sshHost, cmds.join(" "), sshUser, sshKey)
}

memory.terraformApply = { path ->
  def cmds = []

  // This isn't a perfect lock but it should get the job done
  def tfLock = new File("toxic-terraform.lock")
  int lockCheck = 0
  while (tfLock.isFile()) {
    log.info("Toxic Terraform lock file exists, waiting 10 seconds for other job to finish init; lockedBy=${tfLock.text}")
    sleep(10000)
    if (++lockCheck == 30) {
      log.warn("Toxic Terraform lock file still exists after 5 minutes, forcing removal")
      tfLock.delete()
      break
    }
  }
  try {
    log.info("Creating Toxic Terraform lock file")
    tfLock.text = memory.job?.id ?: 'unknown'
    cmds << 'terraform'
    cmds << 'init'
    cmds << '-upgrade=true'
    cmds << path
    execWithEnv(cmds, [:], 200)
  } finally {
    def result = tfLock.delete()
    log.info("Deleted Toxic Terraform lock file; actuallyDeleted=${result}")
  }


  cmds = []
  cmds << 'terraform'
  cmds << 'apply'
  cmds << '-no-color'
  cmds << '-backup=-'
  cmds << '-input=false'
  cmds << '-auto-approve'
  cmds << '-state=' + memory.terraformStatePath()
  cmds << '-var-file=' + memory.terraformVarsPath()
  cmds << path
  execWithEnv(cmds, [:], 600)
}

memory.terraformDestroy = { path ->

  def cmds = []
  cmds << 'terraform'
  cmds << 'destroy'
  cmds << '-no-color'
  cmds << '-backup=-'
  cmds << '-state=' + memory.terraformStatePath()
  cmds << '-force'
  cmds << '-var-file=' + memory.terraformVarsPath()
  cmds << path

  execWithEnv(cmds, [:], 600)
}

memory.terraformOutput = { output, path ->

  def cmds = []
  cmds << 'terraform'
  cmds << 'output'
  cmds << '-no-color'
  cmds << '-state=' + memory.terraformStatePath()
  cmds << output

  execWithEnv(cmds, [:], 600)

  return out.toString().trim()
}

memory.terraformCleanup = { ->
  new File(memory.terraformVarsPath()).delete()
  new File(memory.terraformStatePath()).delete()
}

memory.terraformWithRemoteState = { closure ->
  def exitCode = -1

  def config = [:]
  config['bucket']  = "toxic-terraform-states-" + memory['toxic.remote.environment']
  config['key']     = memory['toxic.remote.context'] + "/terraform.tfstate"
  config['region']  = memory['toxic.remote.aws.region']
  config['profile'] = memory['toxic.remote.aws.profile']

  try {
    memory.terraformConfigRemote(config)
    exitCode = closure()
  } catch (Exception e) {
    log.error("Failed to run Terraform operation; ex=${e.message}")
  } finally {
    memory.terraformDisableRemote()
  }

  return exitCode
}

memory.terraformConfigRemote = { options ->
  def cmds = []
  cmds << 'terraform'
  cmds << 'remote'
  cmds << 'config'
  cmds << '-backend=s3'
  options.each { k,v ->
    cmds << "-backend-config=${k}=${v}"
  }

  execWithEnv(cmds, [:], 600)
}

memory.terraformDisableRemote = { ->
  def cmds = []
  cmds << 'terraform'
  cmds << 'remote'
  cmds << 'config'
  cmds << '-disable'

  execWithEnv(cmds, [:], 600)
}

memory.writeTerraformVars = { vars ->
  def sb = new StringBuilder()
  vars.each { k,v -> sb.append("${k} = \"${v}\"" + '\n') }

  new File(memory.terraformVarsPath()).write(sb.toString())
}

memory.terraformStatePath = { ->
  new File(memory.homePath, 'gen/terraform.tfstate').canonicalPath
}

memory.terraformVarsPath = { ->
  new File(memory.homePath, 'gen/terraform.tfvars').canonicalPath
}

memory.deployTestSuite = { sshHost, sshUser, sshKey ->
  int result = 0

  def tar = "toxic.tar.gz"
  def cmds = []

  // Create tarball
  log.info("Creating ${tar} from ${memory.homePath}")
  cmds << 'tar'
  cmds << '--exclude=.git'
  cmds << '-czf'
  cmds << tar
  cmds << 'toxic'
  String depsDirPath = 'gen/toxic/deps'
  File depsDir = new File(memory.homePath, depsDirPath)
  if(depsDir.isDirectory()) {
    cmds << depsDirPath
  }
  result &= execWithEnv(cmds, [:], 600, memory.homePath)

  // Copy tarball to remote host
  cmds = []
  cmds << 'scp'
  cmds << '-o'
  cmds << 'StrictHostKeyChecking=no'
  cmds << '-o'
  cmds << 'UserKnownHostsFile=/dev/null'
  cmds << '-i'
  cmds << sshKey
  cmds << tar
  cmds << "${sshUser}@${sshHost}:~/"
  result &= execWithEnv(cmds, [:], 60, memory.homePath)

  // Delete local tarball
  cmds = []
  cmds << 'rm'
  cmds << '-f'
  cmds << tar
  result &= execWithEnv(cmds, [:], 60, memory.homePath)

  // Expand remote tarball
  result &= memory.execRemote(sshHost, "sudo rm -fr ~toxic/toxic && sudo rm -fr ~toxic/${depsDirPath} && sudo mv ${tar} ~toxic/", sshUser, sshKey)
  result &= memory.execRemote(sshHost, "sudo su - toxic -c 'tar xf ${tar}'", sshUser, sshKey)

  result
}

memory.initRemoteTests = { sshHost, sshUser, sshKey ->
  memory.execRemote(sshHost, "sudo su - toxic -c 'mkdir -p ~/gen/test'", sshUser, sshKey)
}

memory.execRemote = { def host, def cmd, def user = null, key = null, timeout = 1800000, boolean bypassProxy=true ->
  if(bypassProxy && memory.remoteExecProxyBypassMappings) {
    host = memory.remoteExecProxyBypassMappings[host] ?: host
  }

  if (memory['remoteExecWithDocker']) {
    int exitCode = 1

    if (!memory['docker.project']) {
      log.error("No project specified - configure memory['docker.project'] to use this method")
      return exitCode
    }

    log.info("Running execRemote() with Docker exec; host=${host}, cmd=${cmd}; user=${user}; project=${memory['docker.project']}")

    exitCode = memory.dockerExec(cmd, "${memory['docker.project']}_${host}", user, [:], timeout)

    memory.lastResponse = memory.dockerOut
    memory.lastError = memory.dockerErr

    log.info("Docker exec process outputs; cmd=${cmd}; exitCode=${exitCode}; out=${memory.dockerOut}; err=${memory.dockerErr}")

    return exitCode
  }

  def cmds = []
  cmds << 'ssh'
  cmds << '-i'
  cmds << (key ?: memory.sshPrivateKeyFile)
  cmds << '-oStrictHostKeyChecking=no'
  cmds << '-oUserKnownHostsFile=/dev/null'
  cmds << '-l'
  cmds << (user ?: memory.sshUser)
  cmds << host?.toString()
  cmds << cmd?.toString()

  exitCode = execWithEnv(cmds,[:],memory.sshTimeoutSecs ? memory.sshTimeoutSecs.toInteger() : 3600)

  memory.lastResponse = out
  memory.lastError = err

  return exitCode
}

memory.dockerExec = { command, container, user, env=[:], timeout=300000 ->
  def cmd = []
  cmd << "docker"
  cmd << "exec"
  cmd << "-i"
  if (user) {
    cmd << "-u"
    cmd << user
  }
  cmd << container
  cmd << "/bin/bash"
  cmd << "--login"
  cmd << "-c"
  cmd << command

  withProcessOutputs {
    execWithEnv(cmd, env, timeout)
  }
}

def withProcessOutputs(closure) {
  int exitCode = closure()
  // Save output streams, replacing backticks to avoid later interpretation by GroovyEvaluator
  memory.dockerOut = out?.toString().replaceAll('`',"'")
  memory.dockerErr = err?.toString().replaceAll('`',"'")
  return exitCode
}