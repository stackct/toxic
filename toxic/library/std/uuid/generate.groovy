def startsWith = memory['startsWith'];

if (startsWith)
{
    def uuid = startsWith.concat(UUID.randomUUID().toString().substring(startsWith.size()));
    def uuidRegex = /^[0-9a-fA-F]{8}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{12}$/;
    def matcher = (uuid =~ uuidRegex)

    if(!matcher.matches()) {
        println "Invalid UUID starting value '${startsWith}' produced '${uuid}'"
        memory['value'] = null;
        return;
    }

    memory['value'] = uuid;
}
else
{
    memory['value'] = UUID.randomUUID().toString();
}
