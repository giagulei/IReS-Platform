-- Specific configuration of operator
ENGINE = "Postgres"
OPERATOR = "Move_Hive_" .. ENGINE
SCRIPT = OPERATOR .. ".sh"
SHELL_COMMAND = "./" .. SCRIPT
-- Home directory of operator
OPERATOR_LIBRARY = "asapLibrary/operators"
OPERATOR_HOME = OPERATOR_LIBRARY .. "/" .. OPERATOR

-- The actual distributed shell job.
operator = yarn {
  name = "Execute " .. OPERATOR .. " Operator",
  labels = "hive",
  nodes = "slave5",
  memory = 1024,
  container = {
    instances = 1,
    command = {
		base = SHELL_COMMAND
  	},
    resources = {
		[ SCRIPT] = {
			file = OPERATOR_HOME .. "/" .. SCRIPT,
			type = "file",               -- other value: 'archive'
			visibility = "application",  -- other values: 'private', 'public'
		}
    }
  }
}
