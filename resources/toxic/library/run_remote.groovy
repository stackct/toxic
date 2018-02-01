/* ----------------------------------------------------------------------------
  Run Toxic tasks on a remote EC2 instance.
    
    Required:
      - toxic.remote.doDir (String)
      - toxic.remote.args  (Map)
----------------------------------------------------------------------------- */

memory.loadRemoteSettings()

def tfvars = [
  environment             : memory['toxic.remote.environment'],
  external                : memory['toxic.remote.external'] == "true" ? 1 : 0,
  aws_region              : memory['toxic.remote.aws.region'],
  aws_profile             : memory['toxic.remote.aws.ec2.profile'],
  aws_access_key_id       : memory['toxic.remote.aws.access_key_id'],     // TODO: Replace with instance-profile
  aws_secret_access_key   : memory['toxic.remote.aws.secret_access_key'], // TODO: Replace with instance-profile
  vpc_id                  : memory['toxic.remote.aws.vpc'],
  subnet_id               : memory['toxic.remote.aws.subnet'],
  image_id                : memory['toxic.remote.aws.ec2.image'],
  instance_type           : memory['toxic.remote.aws.ec2.instancetype'],
  ssh_user                : memory['toxic.remote.sshUser'],
  private_key             : memory['toxic.remote.sshKey'],
  public_key              : memory['toxic.remote.sshKey'] + '.pub',
  allowed_cidr            : memory['toxic.remote.allowed_cidr'] ?: "http://ipv4.icanhazip.com".toURL().text.trim() + "/32",
  purpose                 : memory['toxic.remote.purpose'] ?: "integration-testing",
  root_device_size_gb     : "50",
  root_device_iops        : "2500",
  dns_name                : memory['toxic.remote.aws.dns'] ?: memory['toxic.remote.environment'], 
  remote_state_bucket     : memory['toxic.remote.state.bucket'] ?: "toxic-terraform-states" + memory['toxic.remote.environment'],
  remote_state_key_prefix : memory['toxic.remote.state.key_prefix'] ?: "site-static",
  certificate_domain      : memory['toxic.remote.aws.certificate_domain'] ?: '*.mycompany.invalid'
]

/* Clean up from any previous run */
if (memory['toxic.remote.terraform.reset'] == 'true') {
  assert 0 == memory.terraformDestroy(memory['toxic.remote.terraform.resources'])
}

/* Write Terraform vars */
memory.writeTerraformVars(tfvars)

/* Create remote instance */
assert 0 == memory.terraformApply(memory['toxic.remote.terraform.resources'])

/* Read back dynamic outputs */
def sshKey  = memory['toxic.remote.sshKey']
def sshHost = memory.terraformOutput('public_ip', memory['toxic.remote.terraform.resources'])
def sshUser = memory['toxic.remote.sshUser']
def instanceId = memory.terraformOutput('instance_id', memory['toxic.remote.terraform.resources'])

/* Deploy test files to remote instance */
assert 0 == memory.deployTestSuite(sshHost, sshUser, sshKey)

/* Authenticate with ECR */
assert 0 == memory.execRemote(sshHost, "sudo su - toxic -c 'eval \$(aws ecr get-login --no-include-email|sed s^https:.*^https://docker.mycompany.invalid^)'", sshUser, sshKey)

/* Execute tests on remote instance */
memory.sshTimeoutSecs=7200

/* Set up SSH tunnel */
def tunnel = [
  memory['toxic.remote.tunnel.sourcehost'],
  memory['toxic.remote.tunnel.sourceport'], 
  memory['toxic.remote.tunnel.targethost'],
  memory['toxic.remote.tunnel.targetport']
].join(":") 

assert 0 == execWithEnv(["ssh", "-oStrictHostKeyChecking=no", "-oUserKnownHostsFile=/dev/null", "-f", "-R", tunnel, "-i", sshKey, "${sshUser}@${sshHost}", "sleep ${memory.sshTimeoutSecs}"], [:], 60, memory.homePath)

/* Run Tests */
assert 0 == memory.initRemoteTests(sshHost, sshUser, sshKey)

def testResult = memory.runRemoteTests(sshHost, sshUser, sshKey, memory['toxic.remote.doDir'], memory['toxic.remote.args'])
def testResponse = memory.lastResponse?.toString()

memory.teardownRemoteEnvironment(memory['toxic.remote.args']['docker.preserveContainers'] == 'true')

assert 0 == testResult
assert testResponse?.contains("; fail=0")