<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
IRES_HOME="/home/giagulei/git/IReS-Platform"
=======
ES_HOME="/home/bill/PhD/projects/asap/asap4all/IReS-Platform"
>>>>>>> temp
=======
IRES_HOME="/home/giagulei/git/IReS-Platform"
>>>>>>> 679b7257e992f967a6c90fdd205f40a21e7f2014
MASTER_JAR_LOCATION = IRES_HOME .. "/cloudera-kitten/java/master/target/kitten-master-0.2.0-jar-with-dependencies.jar"

YARN_CLASSPATH="/home/hadoop/yarn/etc/hadoop:/home/hadoop/yarn/share/hadoop/common/lib/*:/home/hadoop/yarn/share/hadoop/common/*:/home/hadoop/yarn/share/hadoop/hdfs:/home/hadoop/yarn/share/hadoop/hdfs/lib/*:/home/hadoop/yarn/share/hadoop/hdfs/*:/home/hadoop/yarn/share/hadoop/yarn/lib/*:/home/hadoop/yarn/share/hadoop/yarn/*:/home/hadoop/yarn/share/hadoop/mapreduce/lib/*:/home/hadoop/yarn/share/hadoop/mapreduce/*:/contrib/capacity-scheduler/*.jar"
=======
ES_HOME="/home/bill/PhD/projects/asap/asap4all/IReS-Platform"
MASTER_JAR_LOCATION = IRES_HOME .. "/cloudera-kitten/java/master/target/kitten-master-0.2.0-jar-with-dependencies.jar"

YARN_CLASSPATH="/home/bill/PhD/projects/yarn/etc/hadoop:/home/bill/PhD/projects/yarn/share/hadoop/common/lib/*:/home/bill/PhD/projects/yarn/share/hadoop/common/*:/home/bill/PhD/projects/yarn/share/hadoop/hdfs:/home/bill/PhD/projects/yarn/share/hadoop/hdfs/lib/*:/home/bill/PhD/projects/yarn/share/hadoop/hdfs/*:/home/bill/PhD/projects/yarn/share/hadoop/yarn/lib/*:/home/bill/PhD/projects/yarn/share/hadoop/yarn/*:/home/bill/PhD/projects/yarn/share/hadoop/mapreduce/lib/*:/home/bill/PhD/projects/yarn/share/hadoop/mapreduce/*:/contrib/capacity-scheduler/*.jar"
>>>>>>> aaf0326d5b991e2f0542b947052b252e382efa05

-- Resource and environment setup.
base_resources = {
  ["master.jar"] = { file = MASTER_JAR_LOCATION }
}
base_env = {
  CLASSPATH = table.concat({"${CLASSPATH}", YARN_CLASSPATH, "./master.jar"}, ":"),
}

-- The actual distributed shell job.
operator = yarn {
  name = "Asap master",
  timeout = 1000000000,
  memory = 1024,
  cores = 1,
  master = {
    name = "Asap master",
    env = base_env,
    resources = base_resources,
    command = {
      base = "${JAVA_HOME}/bin/java -Xms64m -Xmx128m com.cloudera.kitten.appmaster.ApplicationMaster",
      args = { "-conf job.xml" },
    }
  }
}
