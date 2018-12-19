if (!memory.value || !memory.zoneId || !memory.recordName) {
  log.info("Skipping DNS update; zoneId=${memory.zoneId}; record=${memory.recordName}; value=${memory.value}")
  memory.success = false
  return
}

def dnsFile = File.createTempFile("pickle-", "-dns.json")
dnsFile.text = """
{
  "Changes": [
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "${memory.recordName}",
        "Type": "A",
        "TTL": 60,
        "ResourceRecords": [
          {
            "Value": "${memory.value}"
          }
        ]
      }
    }
  ]
}
"""

def cmds = []
cmds << "aws"
cmds << 'route53'
cmds << 'change-resource-record-sets'
cmds << '--hosted-zone-id'
cmds << memory.zoneId
cmds << '--change-batch'
cmds << 'file://' + dnsFile.absolutePath

def env = [:]
if (memory.awsAccessKeyId && memory.awsAccessKeySecret) {
  log.info("Using provided AWS credentials; accessKeyId=${memory.awsAccessKeyId}; region=${memory.awsRegion}")
  env.AWS_ACCESS_KEY_ID = memory.awsAccessKeyId
  env.AWS_SECRET_ACCESS_KEY = memory.awsAccessKeySecret
  env.AWS_DEFAULT_REGION = memory.awsRegion
}

log.info("Performing DNS update; zoneId=${memory.zoneId}; record=${memory.recordName}; value=${memory.value}")
def exitCode = execWithEnv(cmds, env)

dnsFile.delete()

memory.success = (exitCode == 0)