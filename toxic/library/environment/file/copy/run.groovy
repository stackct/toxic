execWithEnv(memory['file.cmds'][memory.runtime].mkParentDir)
memory.success = (0 == execWithEnv(memory['file.cmds'][memory.runtime].copy))