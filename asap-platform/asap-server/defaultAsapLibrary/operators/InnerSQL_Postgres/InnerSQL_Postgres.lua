-- Specific configuration of operator
ENGINE = "Postgres"
OPERATOR = "InnerSQL_" .. ENGINE
SCRIPT = OPERATOR .. ".sh"
SHELL_COMMAND = "./" .. SCRIPT
-- Home directory of operator
OPERATOR_LIBRARY = "asapLibrary/operators"
OPERATOR_HOME = OPERATOR_LIBRARY .. "/" .. OPERATOR

-- The actual distributed shell job.
operator = yarn {
	name = "Execute " .. OPERATOR .. " Operator",
 	labels = "postgres",
      	nodes = "slave5",
	container = {
    		instances = CONTAINER_INSTANCES,
    		env = base_env,
    		command = {
				base = SHELL_COMMAND
			},
    		resources = {
    			[ "InnerSQL_Postgres.sh"] = {
					file = OPERATOR_HOME .. "/" .. SCRIPT,
      				type = "file",               -- other value: 'archive'
      				visibility = "application",  -- other values: 'private', 'public'
    			},
    			[ "tpchQuery17Inner" .. ENGINE .. ".sql"] = {
					file = OPERATOR_HOME .. "/tpchQuery17Inner" .. ENGINE .. ".sql",
      				type = "file",               -- other value: 'archive'
      				visibility = "application",  -- other values: 'private', 'public'
    			},
    			[ "tpchQuery18Inner" .. ENGINE .. ".sql"] = {
					file = OPERATOR_HOME .. "/tpchQuery18Inner" .. ENGINE .. ".sql",
      				type = "file",               -- other value: 'archive'
      				visibility = "application",  -- other values: 'private', 'public'
    			}
  			}		
 	}
}
